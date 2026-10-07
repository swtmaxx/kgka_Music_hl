import 'package:shared_preferences/shared_preferences.dart';

/// 全局静态配置。
///
/// 默认走**外置（自建）API 服务器**（`API_BASE_URL` 指向的地址）。
/// 也可以在设置里开启「内置 API」，改为直连酷狗（`lib/core/kugou/`），
/// 此时内置尚未实现的路由仍会回退到外置服务器。
class AppConfig {
  const AppConfig._();

  static const appName = 'KA Music';
  static const appVersion = '3.1.0';
  static const appVersionCode = '310';

  static const _defaultApiBaseUrl = 'https://music.api.hoilai.cn';
  static const _customBaseUrlKey = 'settings.custom_api_base_url';
  static const _useBuiltInApiKey = 'settings.use_built_in_api';

  /// 默认 API 服务器地址（可用 `--dart-define=KA_MUSIC_API_BASE_URL=...` 覆盖）。
  static const apiBaseUrl = String.fromEnvironment(
    'KA_MUSIC_API_BASE_URL',
    defaultValue: _defaultApiBaseUrl,
  );

  static const debugLyrics = bool.fromEnvironment(
    'KA_MUSIC_DEBUG_LYRICS',
    defaultValue: true,
  );

  // ===== 缓存与下载配置 =====
  /// 数据缓存目录名 / 下载目录名 / 播放缓存目录名
  static const cacheDirName = 'ka_music_cache';
  static const downloadDirName = 'KA Music';
  static const playCacheDirName = 'ka_music_play_cache';

  /// 数据缓存 TTL（分级）
  static const homeCacheTtl = Duration(minutes: 30); // 首页推荐
  static const playlistDetailTtl = Duration(hours: 24); // 歌单/专辑详情
  static const userProfileTtl = Duration(hours: 24); // 用户信息+歌单列表

  /// 播放缓存大小上限（超过则按 LRU 清理），下载不设上限（用户主动管理）
  static const playCacheMaxBytes = 300 * 1024 * 1024; // 300MB

  /// 下载并发数
  static const maxConcurrentDownloads = 3;

  /// 用户自定义的 API 服务器地址；非空时优先于编译期 [apiBaseUrl]。
  static String? _customBaseUrl;

  /// 实际生效的 API 服务器地址（自定义优先，否则用默认）。
  static String get effectiveBaseUrl => _customBaseUrl ?? apiBaseUrl;

  /// 用户是否设置了自定义地址。
  static bool get hasCustomBaseUrl => _customBaseUrl != null;

  /// 自定义地址（未设置时为 null）。
  static String? get customBaseUrl => _customBaseUrl;

  /// 默认地址。
  static String get defaultApiBaseUrl => apiBaseUrl;

  /// 是否使用**内置酷狗 API**（直连酷狗，不依赖外部服务器）。
  ///
  /// 默认 **false**：走外置服务器。可在设置里开启。
  static bool _useBuiltInApi = false;

  static bool get useBuiltInApi => _useBuiltInApi;

  /// 加载持久化设置（自定义 API 地址 + 内置 API 开关）。
  static Future<void> loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getString(_customBaseUrlKey);
    if (stored != null && stored.trim().isNotEmpty) {
      _customBaseUrl = stored.trim();
    }
    _useBuiltInApi = prefs.getBool(_useBuiltInApiKey) ?? false;
  }

  /// 保存内置 API 开关。
  static Future<void> saveUseBuiltInApi(bool enabled) async {
    _useBuiltInApi = enabled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_useBuiltInApiKey, enabled);
  }

  /// 保存自定义 API 地址。传 `null` 或空串则恢复默认。
  static Future<void> saveCustomBaseUrl(String? url) async {
    final prefs = await SharedPreferences.getInstance();
    final trimmed = url?.trim();
    if (trimmed == null || trimmed.isEmpty || trimmed == apiBaseUrl) {
      _customBaseUrl = null;
      await prefs.remove(_customBaseUrlKey);
    } else {
      _customBaseUrl = trimmed;
      await prefs.setString(_customBaseUrlKey, trimmed);
    }
  }

  static Uri apiUri(String path, [Map<String, Object?> query = const {}]) {
    final base = Uri.parse(effectiveBaseUrl);
    final cleanPath = path.startsWith('/') ? path.substring(1) : path;
    final normalizedBasePath =
        base.path.endsWith('/') ? base.path : '${base.path}/';

    return base.replace(
      path: '$normalizedBasePath$cleanPath',
      queryParameters: {
        for (final entry in query.entries)
          if (entry.value != null && entry.value.toString().isNotEmpty)
            entry.key: entry.value.toString(),
      },
    );
  }
}
