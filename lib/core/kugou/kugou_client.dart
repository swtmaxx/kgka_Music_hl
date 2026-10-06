import 'dart:convert';

import 'kugou_request.dart';
import 'kugou_signature.dart';

/// 内置酷狗 API 的**路由调度器**。
///
/// 目标是**复刻外部服务（music.api.hoilai.cn）的响应结构**，从而让上层
/// `MusicApi` / 数据模型**零改动**。多数端点的响应就是酷狗原生 JSON
/// （`ApiClient.unwrapData` 会自动解包 `data`），个别端点需要轻量整形。
class KugouClient {
  KugouClient({KugouRequest? request}) : _request = request ?? KugouRequest();

  final KugouRequest _request;

  KugouRequest get request => _request;

  /// 与 `MusicApi.setSession` 同步登录态。
  void setSession({String? token, String? t1, String? userId}) {
    _request.token = token;
    _request.t1 = t1;
    _request.userId = userId;
  }

  /// 处理一次 API 调用；[path] 形如 `/search`。
  ///
  /// 抛 [KugouUnsupportedRoute] 表示该路由尚未内置，调用方可回退到外部服务器。
  Future<Object?> handle(
    String method,
    String path, [
    Map<String, Object?> query = const {},
  ]) async {
    final route = path.startsWith('/') ? path : '/$path';
    switch (route) {
      case '/search':
        return _search(query);
      case '/song/url':
        return _songUrl(query);
      case '/search/lyric':
        return _searchLyric(query);
      case '/lyric':
        return _lyric(query);
      default:
        throw KugouUnsupportedRoute(route);
    }
  }

  // ===== /search =====

  /// 歌曲/专辑搜索（`module/search.js`）。
  Future<Object?> _search(Map<String, Object?> q) async {
    final type = const {
      'special',
      'lyric',
      'song',
      'album',
      'author',
      'mv',
    }.contains(q['type'])
        ? q['type']!.toString()
        : 'song';
    final keyword = (q['keywords'] ?? q['keyword'] ?? '').toString();
    final page = q['page'] ?? 1;
    final pageSize = q['pagesize'] ?? 30;

    if (type == 'album') {
      final raw = await _request.send(
        method: 'GET',
        url: '/v1/search/album',
        baseURL: KugouConfig.gateway,
        params: {
          'keyword': keyword,
          'page': page,
          'pagesize': pageSize,
          'platform': 'AndroidFilter',
          'iscorrection': q['iscorrection'] ?? 1,
          'category': q['category'] ?? '1',
          'sorttype': q['sorttype'] == 1 ? 1 : 0,
          'searchsong': q['searchsong'] == 1 ? 1 : 0,
          'clientver': 201,
        },
        headers: const {'x-router': 'complexsearch.kugou.com'},
      );
      return _flattenList(raw, const ['albums', 'album', 'lists']);
    }

    final raw = await _request.send(
      method: 'GET',
      url: '/v2/search/song',
      baseURL: KugouConfig.gateway,
      params: {
        'keyword': keyword,
        'page': page,
        'pagesize': pageSize,
        'platform': 'AndroidFilter',
        'iscorrection': q['iscorrection'] ?? 1,
        'privilegefilter': q['privilegefilter'] ?? 0,
        'area_code': q['area_code'] ?? 1,
        'dopicfull': 1,
        // 概念版(Young) versionCode 为 201
        'clientver': 201,
      },
      headers: const {'x-router': 'complexsearch.kugou.com'},
    );
    return _flattenList(raw, const ['songs', 'song', 'lists']);
  }

  // ===== /song/url =====

  /// 获取播放地址（`module/song_url.js`）。
  Future<Object?> _songUrl(Map<String, Object?> q) async {
    const magicQualities = {
      'piano',
      'acappella',
      'subwoofer',
      'ancient',
      'dj',
      'surnay',
    };
    final rawQuality = q['quality']?.toString() ?? '128';
    final quality =
        magicQualities.contains(rawQuality) ? 'magic_$rawQuality' : rawQuality;

    final raw = await _request.send(
      method: 'GET',
      url: '/v5/url',
      baseURL: KugouConfig.gateway,
      params: {
        'album_id': _int(q['album_id']),
        'area_code': 1,
        'hash': (q['hash']?.toString() ?? '').toLowerCase(),
        'ssa_flag': 'is_fromtrack',
        'version': 11430,
        'page_id': 967177915,
        'quality': quality,
        'album_audio_id': _int(q['album_audio_id']),
        'behavior': 'play',
        'pid': 411,
        'cmd': 26,
        'pidversion': 3001,
        'IsFreePart': q['free_part'] == true ? 1 : 0,
        'ppage_id': q['ppage_id'] ?? '356753938,823673182,967485191',
        'cdnBackup': 1,
        'module': '',
        'clientver': 11430,
      },
      headers: const {'x-router': 'trackercdn.kugou.com'},
      encryptKey: true,
    );
    return _normalizeSongUrl(raw);
  }

  // ===== /search/lyric =====

  /// 歌词候选搜索（`module/search_lyric.js`）。
  Future<Object?> _searchLyric(Map<String, Object?> q) async {
    final raw = await _request.send(
      method: 'GET',
      url: '/v1/search',
      baseURL: 'https://lyrics.kugou.com',
      params: {
        'album_audio_id': _int(q['album_audio_id']),
        'appid': KugouConfig.appId,
        'clientver': KugouConfig.clientVer,
        'duration': _int(q['duration']),
        'hash': q['hash'] ?? '',
        'keyword': q['keywords'] ?? '',
        'lrctxt': 1,
        'man': q['man'] ?? 'no',
      },
      clearDefaultParams: true,
      notSignature: true,
    );
    return raw;
  }

  // ===== /lyric =====

  /// 下载歌词（`module/lyric.js`）。返回体带 `decodedContent`/`rawContent`，
  /// 与外部服务的字段对齐。
  Future<Object?> _lyric(Map<String, Object?> q) async {
    final fmt = q['fmt']?.toString() ?? 'krc';
    final raw = await _request.send(
      method: 'GET',
      url: '/download',
      baseURL: 'https://lyrics.kugou.com',
      params: {
        'ver': 1,
        'client': q['client'] ?? 'android',
        'id': q['id'],
        'accesskey': q['accesskey'],
        'fmt': fmt,
        'charset': 'utf8',
      },
    );
    return _decodeLyricBody(raw, fmt);
  }

  // ===== 整形工具 =====

  /// 把 `{data:{lists:[...]}}` 之类的嵌套列表拍平为数组；
  /// 已经是数组时原样返回。
  Object? _flattenList(Object? raw, List<String> keys) {
    if (raw is List) return raw;
    if (raw is! Map) return raw;

    final data = raw['data'];
    final candidates = <Object?>[
      if (data is Map) ...keys.map((k) => data[k]),
      if (data is List) data,
      ...keys.map((k) => raw[k]),
    ];
    for (final candidate in candidates) {
      if (candidate is List) return candidate;
    }
    return raw;
  }

  /// `/v5/url` 响应整形：保证 `{url: [...], hash: '...'}` 结构（与外部服务一致）。
  Object? _normalizeSongUrl(Object? raw) {
    if (raw is! Map) return raw;
    final data = raw['data'];
    final source = data is Map ? data : raw;

    final url = source['url'];
    final hash = source['hash'] ?? '';
    if (url is List || url is String) {
      return <String, Object?>{
        ...source,
        'url': url is String ? [url] : url,
        'hash': hash,
      };
    }
    // 上游返回失败（如需要验证）：透传原始错误体
    return raw;
  }

  /// 歌词内容解码：KRC 为加密格式，此处仅解 Base64（明文 LRC）。
  ///
  /// KRC 的二次解码由上层 `parseLyrics` 的 `[language:]` 分支处理；
  /// 与外部服务一致，同时给出 `decodedContent` 与 `rawContent`。
  Object? _decodeLyricBody(Object? raw, String fmt) {
    if (raw is! Map) return raw;
    final body = raw['data'] is Map
        ? Map<String, Object?>.of(raw['data'] as Map)
        : Map<String, Object?>.of(raw);

    final content = body['content']?.toString();
    if (content == null || content.isEmpty) return raw;

    final contentType = body['contenttype'];
    final isPlain =
        fmt == 'lrc' || (contentType != null && int.tryParse('$contentType') != 0);

    String? decoded;
    try {
      if (isPlain) {
        decoded = utf8.decode(base64.decode(content), allowMalformed: true);
      }
    } catch (_) {
      decoded = null;
    }

    if (decoded != null && decoded.isNotEmpty) {
      body['decodedContent'] = decoded;
    }
    body['rawContent'] = content;
    return body;
  }

  int _int(Object? value) {
    if (value == null) return 0;
    if (value is int) return value;
    return int.tryParse(value.toString()) ?? 0;
  }

  void close() => _request.close();
}

/// 该路由尚未内置（调用方可回退到外部服务器）。
class KugouUnsupportedRoute implements Exception {
  KugouUnsupportedRoute(this.route);

  final String route;

  @override
  String toString() => '内置 API 暂不支持路由：$route';
}
