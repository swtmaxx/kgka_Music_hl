import 'dart:async';
import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// 缓存读取结果。
class CacheResult<T> {
  const CacheResult({required this.data, required this.isStale});

  final T data;

  /// 是否已超过 TTL（过期）。
  final bool isStale;
}

/// 统一数据缓存服务。
///
/// 缓存载体为 SharedPreferences + JSON（决策1）。每条缓存存储为：
/// `{ "savedAt": <毫秒时间戳>, "payload": <任意 JSON> }`
///
/// 核心方法 [swr] 封装 stale-while-revalidate 模式：先返回缓存立即显示，
/// 后台静默刷新，失败时降级到缓存。该模式推广自 AuthController 已有的
/// 「先缓存后刷新 + 失败降级」逻辑。
class CacheService {
  CacheService();

  static const _savedAtKey = 'savedAt';
  static const _payloadKey = 'payload';

  /// 数据缓存条目数上限。
  ///
  /// `cache_playlist_{id}` / `cache_album_{id}` / `cache_artist_{id}` 是一实体一键，
  /// 浏览越多、条目越多。SharedPreferences 启动时全量读入内存、每次写入重写整个
  /// XML，无上限时长期使用会拖慢启动并抬高内存占用（1GB 手表尤其明显）。
  /// 超出时按 `savedAt` 淘汰最旧的条目（近似 LRU）。
  static const _maxEntries = 200;

  // ===== key 命名规范 =====
  // 首页（匿名可访问，登出不清理）：cache_home
  // 歌单详情：cache_playlist_{playlistId}
  // 专辑详情：cache_album_{albumId}
  // 歌手详情：cache_artist_{artistId}
  // 用户信息：cache_user_{userId}
  // 用户歌单列表：cache_user_playlists_{userId}

  /// 用户相关缓存 key 前缀，登出时按前缀清理。
  static const _userCachePrefixes = <String>[
    'cache_user_',
    'cache_playlist_',
    'cache_album_',
    'cache_artist_',
  ];

  /// 读取缓存。无缓存返回 null。
  ///
  /// 注意：TTL 仅通过 [CacheResult.isStale] 上报，**不会**阻止返回数据。
  /// 调用方如需“过期就不算命中”，必须自行检查 isStale。
  Future<CacheResult<T>?> read<T>(
    String key, {
    required T Function(Map<String, dynamic> json) decode,
    Duration ttl = const Duration(hours: 24),
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(key);
    if (raw == null) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) return null;
      final payload = decoded[_payloadKey];
      if (payload is! Map<String, dynamic>) return null;
      final savedAt = decoded[_savedAtKey];
      final isStale = savedAt is! num ||
          DateTime.now().millisecondsSinceEpoch - savedAt.toInt() >
              ttl.inMilliseconds;
      return CacheResult<T>(data: decode(payload), isStale: isStale);
    } catch (_) {
      return null;
    }
  }

  /// 写入缓存（记录 savedAt = 当前时间）。
  Future<void> write(String key, Map<String, dynamic> payload) async {
    final prefs = await SharedPreferences.getInstance();
    final wrapper = jsonEncode({
      _savedAtKey: DateTime.now().millisecondsSinceEpoch,
      _payloadKey: payload,
    });
    await prefs.setString(key, wrapper);
    await _evictIfNeeded(prefs);
  }

  /// 超出 [_maxEntries] 时按 `savedAt` 升序淘汰最旧的缓存条目。
  Future<void> _evictIfNeeded(SharedPreferences prefs) async {
    final keys = prefs
        .getKeys()
        .where((key) => key.startsWith('cache_'))
        .toList();
    if (keys.length <= _maxEntries) return;

    final ages = <String, int>{};
    for (final key in keys) {
      var savedAt = 0;
      final raw = prefs.getString(key);
      if (raw != null) {
        try {
          final decoded = jsonDecode(raw);
          if (decoded is Map && decoded[_savedAtKey] is num) {
            savedAt = (decoded[_savedAtKey] as num).toInt();
          }
        } catch (_) {}
      }
      ages[key] = savedAt;
    }

    final ordered = ages.keys.toList()
      ..sort((a, b) => (ages[a] ?? 0).compareTo(ages[b] ?? 0));
    final removeCount = keys.length - _maxEntries;
    for (var i = 0; i < removeCount && i < ordered.length; i++) {
      await prefs.remove(ordered[i]);
    }
  }

  /// 移除单条缓存。
  Future<void> remove(String key) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(key);
  }

  /// 登出清理：清除用户相关缓存，保留匿名可访问内容（首页 cache_home）。
  Future<void> clearUserCache(String? userId) async {
    final prefs = await SharedPreferences.getInstance();
    final keys = prefs.getKeys().toList();
    for (final key in keys) {
      for (final prefix in _userCachePrefixes) {
        if (key.startsWith(prefix)) {
          await prefs.remove(key);
          break;
        }
      }
    }
  }

  /// 获取所有数据缓存的总大小（字节）。
  ///
  /// 遍历 SharedPreferences 中的所有 key，计算以 `cache_` 开头或
  /// 歌单缓存相关 key 的字符串大小（UTF-16 每字符约 2 字节）。
  Future<int> getCacheSize() async {
    final prefs = await SharedPreferences.getInstance();
    var total = 0;
    for (final key in prefs.getKeys()) {
      if (key.startsWith('cache_') ||
          key.startsWith('ka_music_cached_playlists')) {
        final value = prefs.getString(key);
        if (value != null) {
          total += value.length * 2; // UTF-16 每字符约 2 字节
        }
      }
    }
    return total;
  }

  /// 获取缓存条目数量。
  Future<int> getCacheCount() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs
        .getKeys()
        .where((key) => key.startsWith('cache_'))
        .length;
  }

  /// 清空所有数据缓存（保留用户歌单索引等必要数据）。
  ///
  /// 同时移除 CacheService 自己的索引键，避免清理后旧索引残留。
  Future<void> clearAllCache() async {
    final prefs = await SharedPreferences.getInstance();
    final keys = prefs.getKeys().toList();
    for (final key in keys) {
      if (key.startsWith('cache_')) {
        await prefs.remove(key);
      }
    }
  }

  /// stale-while-revalidate 封装。
  ///
  /// 流程：
  /// 1. 读取缓存 → 若命中，立即 [onData](cached)（首屏优先显示，无感）。
  /// 2. 后台执行 [fetch]：
  ///    - 成功 → [write] 缓存 → [onData](fresh)（静默刷新界面）。
  ///    - 失败 → 若缓存存在（哪怕过期）则保持不报错（降级）；
  ///      若完全无缓存则调用 [onError]。
  /// 3. [forceRefresh] = true（下拉刷新）时跳过第 1 步的立即返回，
  ///    优先走 fetch，失败再回退缓存。
  Future<void> swr<T>({
    required String key,
    required Duration ttl,
    required Future<T> Function() fetch,
    required T Function(Map<String, dynamic> json) decode,
    required Map<String, dynamic> Function(T data) encode,
    required void Function(T data) onData,
    void Function(Object error)? onError,
    bool forceRefresh = false,
  }) async {
    // 1. 先读缓存（非强制刷新时立即返回显示）
    CacheResult<T>? cached;
    if (!forceRefresh) {
      cached = await read<T>(key, decode: decode, ttl: ttl);
      if (cached != null) {
        onData(cached.data);
      }
    } else {
      // 强制刷新也先读缓存作为降级兜底，但不立即显示
      cached = await read<T>(key, decode: decode, ttl: ttl);
    }

    // 2. 后台静默刷新
    try {
      final fresh = await fetch();
      await write(key, encode(fresh));
      onData(fresh);
    } catch (error) {
      if (cached != null) {
        // 有缓存（哪怕过期）则降级，不报错
        if (forceRefresh) {
          // 强制刷新模式下前面没有立即显示，这里补显示缓存
          onData(cached.data);
        }
      } else {
        // 完全无缓存，回调错误
        onError?.call(error);
      }
    }
  }
}
