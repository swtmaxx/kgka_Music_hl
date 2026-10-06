import 'dart:convert';
import 'dart:typed_data';

import 'kugou_crypto.dart';
import 'kugou_device.dart';
import 'kugou_register.dart';
import 'kugou_request.dart';
import 'kugou_signature.dart';
import 'kugou_util.dart';

/// 内置酷狗 API 的**路由调度器**。
///
/// 目标是**复刻外部服务（music.api.hoilai.cn）的响应结构**，从而让上层
/// `MusicApi` / 数据模型**零改动**。多数端点的响应就是酷狗原生 JSON
/// （`ApiClient.unwrapData` 会自动解包 `data`），个别端点需要轻量整形。
class KugouClient {
  KugouClient({KugouRequest? request}) : _request = request ?? KugouRequest();

  final KugouRequest _request;

  KugouRequest get request => _request;

  bool _registerAttempted = false;

  /// 首次使用前完成设备注册（拿到 dfid），否则播放地址等接口会被上游要求安全验证。
  Future<void> _ensureRegistered() async {
    if (_registerAttempted) return;
    _registerAttempted = true;
    final device = KugouDevice.instance;
    await device.ensureLoaded();
    if (device.hasDfid) return;
    try {
      await KugouRegister.register(_request);
    } catch (_) {
      // 注册失败不阻塞；后续请求会以未注册身份发出，必要时回退外部服务器。
    }
  }

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
    // 除歌词/版本查询外，统一先确保设备已注册。
    if (route != '/search/lyric' && route != '/lyric') {
      await _ensureRegistered();
    }

    switch (route) {
      // ===== 搜索 / 歌曲 / 歌词 =====
      case '/search':
        return _search(query);
      case '/search/hot':
        return _forward(
          method: 'GET',
          url: '/api/v3/search/hot_tab',
          params: {'navid': 1, 'plat': 2},
          headers: const {'x-router': 'msearch.kugou.com'},
        );
      case '/search/suggest':
        return _forward(
          method: 'GET',
          url: '/v2/getSearchTip',
          params: {
            'keyword': query['keywords'] ?? '',
            'AlbumTipCount': query['albumTipCount'] ?? 10,
            'CorrectTipCount': query['correctTipCount'] ?? 10,
            'MVTipCount': query['mvTipCount'] ?? 10,
            'MusicTipCount': query['musicTipCount'] ?? 10,
            'radiotip': 1,
          },
          headers: const {'x-router': 'searchtip.kugou.com'},
        );
      case '/song/url':
        return _songUrl(query);
      case '/song/climax':
        return _forward(
          method: 'GET',
          url: '/v1/audio_climax/audio',
          baseURL: 'https://expendablekmrcdn.kugou.com',
          params: {
            'data': jsonEncode(
              (query['hash']?.toString() ?? '')
                  .split(',')
                  .map((h) => {'hash': h})
                  .toList(),
            ),
          },
        );
      case '/search/lyric':
        return _searchLyric(query);
      case '/lyric':
        return _lyric(query);

      // ===== 用户 =====
      case '/user/detail':
        return _userDetail();
      case '/user/playlist':
        return _forward(
          method: 'POST',
          url: '/v7/get_all_list',
          params: {'plat': 1, 'userid': _userId(), 'token': _token()},
          data: {
            'userid': _userId(),
            'token': _token(),
            'total_ver': 979,
            'type': 2,
            'page': query['page'] ?? 1,
            'pagesize': query['pagesize'] ?? 30,
          },
          headers: const {'x-router': 'cloudlist.service.kugou.com'},
        );
      case '/user/vip/detail':
        return _forward(
          method: 'GET',
          url: '/v1/get_union_vip',
          params: {'busi_type': 'all'},
          headers: const {'x-router': 'kugouvip.kugou.com'},
        );

      // ===== 歌单 =====
      case '/playlist/detail':
        return _forward(
          method: 'POST',
          url: '/v3/get_list_info',
          data: {
            'data': (query['ids']?.toString() ?? '')
                .split(',')
                .map((s) => {'global_collection_id': s})
                .toList(),
            'userid': _userId(),
            'token': _token(),
          },
          headers: const {'x-router': 'pubsongs.kugou.com'},
        );
      case '/playlist/track/all':
        return _forward(
          method: 'GET',
          url: '/pubsongs/v2/get_other_list_file_nofilt',
          params: {
            'area_code': 1,
            'begin_idx':
                ((_int(query['page'], 1) - 1) * _int(query['pagesize'], 80)),
            'plat': 1,
            'type': 1,
            'mode': 1,
            'personal_switch': 1,
            'extend_fields': 'abtags,hot_cmt,popularization',
            'pagesize': _int(query['pagesize'], 80),
            'global_collection_id': query['id'],
          },
        );
      case '/playlist/similar':
        return _forward(
          method: 'POST',
          url: '/pubsongs/v1/kmr_get_similar_lists',
          data: {
            'appid': KugouConfig.liteAppId,
            'clientver': KugouConfig.liteClientVer,
            'clienttime': _nowMs(),
            'key': _signParamsKey(_nowMs().toString()),
            'userid': _userId(),
            'ugc': 1,
            'show_list': 1,
            'need_songs': 1,
            'data': (query['ids']?.toString() ?? '')
                .split(',')
                .map((s) => {'global_collection_id': s})
                .toList(),
          },
        );

      // ===== 推荐 / 榜单 =====
      case '/recommend/songs':
        return _forward(
          method: 'POST',
          url: '/everyday_song_recommend',
          data: {
            'platform': query['platform'] ?? 'android',
            'userid': _userIdString(),
          },
          headers: const {'x-router': 'everydayrec.service.kugou.com'},
        );
      case '/top/song':
        return _forward(
          method: 'POST',
          url: '/musicadservice/container/v1/newsong_publish',
          data: {
            'rank_id': query['type'] ?? 21608,
            'userid': _userId(),
            'page': query['page'] ?? 1,
            'pagesize': query['pagesize'] ?? 30,
            'tags': <Object?>[],
          },
        );
      case '/top/album':
        return _forward(
          method: 'POST',
          url: '/musicadservice/v1/mobile_newalbum_sp',
          data: {
            'apiver': 20,
            'token': _token(),
            'page': query['page'] ?? 1,
            'pagesize': query['pagesize'] ?? 30,
            'withpriv': 1,
          },
        );
      case '/top/playlist':
        return _forward(
          method: 'POST',
          url: '/v2/special_recommend',
          data: {
            'appid': KugouConfig.liteAppId,
            'mid': KugouDevice.instance.mid,
            'clientver': KugouConfig.liteClientVer,
            'platform': 'android',
            'clienttime': _nowMs(),
            'userid': _userId(),
            'module_id': query['module_id'] ?? 1,
            'page': query['page'] ?? 1,
            'pagesize': query['pagesize'] ?? 30,
            'key': _signParamsKey(_nowMs().toString()),
            'special_recommend': {'withtag': 1, 'withsong': 1, 'sort': 1},
            'req_multi': 1,
            'retrun_min': 5,
            'return_special_falg': 1,
          },
          headers: const {'x-router': 'specialrec.service.kugou.com'},
        );
      case '/top/card':
        return _forward(
          method: 'POST',
          url: '/singlecardrec.service/v1/single_card_recommend',
          data: {
            'appid': KugouConfig.liteAppId,
            'clientver': KugouConfig.liteClientVer,
            'platform': 'android',
            'clienttime': _nowMs(),
            'userid': _userId(),
            'key': _signParamsKey(_nowMs().toString()),
            'fakem': 'ca981cfc583a4c37f28d2d49000013c16a0a',
            'area_code': 1,
            'mid': KugouDevice.instance.mid,
            'uuid': '-',
            'client_playlist': <Object?>[],
            'u_info': 'a0c35cd40af564444b5584c2754dedec',
          },
          params: {
            'card_id': query['card_id'] ?? 1,
            'fakem': 'ca981cfc583a4c37f28d2d49000013c16a0a',
            'area_code': 1,
            'platform': 'ios',
          },
        );
      case '/personal/fm':
        return _personalFm(query);
      case '/album/shop':
        return _forward(
          method: 'GET',
          url: '/zhuanjidata/v3/album_shop_v2/get_classify_data',
        );

      // ===== 专辑 / 歌手 =====
      case '/album/songs':
        return _forward(
          method: 'POST',
          url: '/v1/album_audio/lite',
          baseURL: 'https://openapi.kugou.com',
          data: {
            'album_id': query['id'],
            'is_buy': query['is_buy'] ?? '',
            'page': query['page'] ?? 1,
            'pagesize': query['pagesize'] ?? 30,
          },
          headers: const {'x-router': 'openapi.kugou.com', 'kg-tid': '255'},
        );
      case '/artist/detail':
        return _forward(
          method: 'POST',
          url: '/kmr/v3/author',
          baseURL: 'https://openapi.kugou.com',
          data: {'author_id': query['id']},
          headers: const {'x-router': 'openapi.kugou.com', 'kg-tid': '36'},
        );
      case '/artist/albums':
        return _forward(
          method: 'POST',
          url: '/kmr/v1/author/albums',
          baseURL: 'https://openapi.kugou.com',
          data: {
            'author_id': query['id'],
            'pagesize': query['pagesize'] ?? 30,
            'page': query['page'] ?? 1,
            'sort': query['sort'] == 'hot' ? 3 : 1,
            'category': 1,
            'area_code': 'all',
          },
          headers: const {'x-router': 'openapi.kugou.com', 'kg-tid': '36'},
        );
      case '/artist/audios':
        return _forward(
          method: 'POST',
          url: '/kmr/v1/audio_group/author',
          baseURL: 'https://openapi.kugou.com',
          data: {
            'appid': KugouConfig.liteAppId,
            'clientver': KugouConfig.liteClientVer,
            'mid': KugouDevice.instance.mid,
            'clienttime': _nowMs(),
            'key': _signParamsKey(_nowMs().toString()),
            'author_id': query['id'],
            'pagesize': query['pagesize'] ?? 30,
            'page': query['page'] ?? 1,
            'sort': query['sort'] == 'hot' ? 1 : 2,
            'area_code': 'all',
          },
          headers: const {'x-router': 'openapi.kugou.com', 'kg-tid': '220'},
        );

      // ===== 电台 =====
      case '/fm/recommend':
        return _forward(
          method: 'POST',
          url: '/v1/rcmd_list',
          data: {
            'appid': KugouConfig.liteAppId,
            'clientver': KugouConfig.liteClientVer,
            'clienttime': _nowMs(),
            'mid': KugouDevice.instance.mid,
            'key': _signParamsKey(_nowMs().toString()),
            'rcmdsongcount': 1,
            'level': 0,
            'area_code': 1,
            'get_tracker': 1,
            'uid': 0,
          },
          headers: const {'x-router': 'fm.service.kugou.com'},
        );
      case '/fm/class':
        return _forward(
          method: 'POST',
          url: '/v1/class_fm_song',
          data: {
            'kguid': _userId(),
            'clienttime': _nowMs(),
            'mid': KugouDevice.instance.mid,
            'platform': 'android',
            'clientver': KugouConfig.liteClientVer,
            'uid': _userId(),
            'get_tracker': 1,
            'key': _signParamsKey(_nowMs().toString()),
            'appid': KugouConfig.liteAppId,
          },
          headers: const {'x-router': 'fm.service.kugou.com'},
        );
      case '/fm/songs':
        return _forward(
          method: 'POST',
          url: '/v1/app_song_list_offset',
          data: {
            'appid': KugouConfig.liteAppId,
            'area_code': 1,
            'clienttime': _nowMs(),
            'clientver': KugouConfig.liteClientVer,
            'data': (query['fmid']?.toString() ?? '')
                .split(',')
                .map((s) => {
                      'fmid': s,
                      'fmtype': query['type'] ?? 2,
                      'offset': query['offset'] ?? -1,
                      'size': query['size'] ?? 20,
                      'singername': '',
                    })
                .toList(),
            'get_tracker': 1,
            'key': _signParamsKey(_nowMs().toString()),
            'mid': KugouDevice.instance.mid,
            'uid': _userId(),
          },
          headers: const {
            'x-router': 'fm.service.kugou.com',
            'Content-Type': 'application/json',
          },
        );
      case '/fm/image':
        return _forward(
          method: 'POST',
          url: '/v1/fm_info',
          data: {
            'appid': KugouConfig.liteAppId,
            'clienttime': _nowMs(),
            'clientver': KugouConfig.liteClientVer,
            'data': (query['fmid']?.toString() ?? '')
                .split(',')
                .map((s) => {
                      'fields': 'imgUrl100,imgUrl50',
                      'fmid': s,
                      'fmtype': 2,
                    })
                .toList(),
            'dfid': KugouDevice.instance.dfid,
            'key': _signParamsKey(_nowMs().toString()),
            'mid': KugouDevice.instance.mid,
          },
          headers: const {
            'x-router': 'fm.service.kugou.com',
            'Content-Type': 'application/json',
          },
        );

      // ===== 评论 =====
      case '/comment/music':
        return _forward(
          method: 'POST',
          url: '/mcomment/v1/cmtlist',
          params: {
            'ver': 6,
            'mixsongid': query['mixsongid'],
            'need_show_image': 1,
            'p': query['page'] ?? 1,
            'pagesize': query['pagesize'] ?? 30,
            'show_classify': query['show_classify'] ?? 1,
            'show_hotword_list': query['show_hotword_list'] ?? 1,
            'extdata': '0',
            'code': 'fc4be23b4e972707f36b8a828a93ba8a',
          },
        );

      // ===== 云盘 =====
      case '/user/cloud':
        return _userCloud(query);
      case '/user/cloud/url':
        return _forward(
          method: 'GET',
          url: '/bsstrackercdngz/v2/query_musicclound_url',
          params: {
            'hash': (query['hash']?.toString() ?? '').toLowerCase(),
            'ssa_flag': 'is_fromtrack',
            'version': '20102',
            'ssl': 0,
            'album_audio_id': query['album_audio_id'] ?? 0,
            'pid': 20026,
            'audio_id': query['audio_id'] ?? 0,
            'kv_id': 2,
            'key': KugouSignature.signCloudKey(
              (query['hash']?.toString() ?? '').toLowerCase(),
              '20026',
            ),
            'bucket': 'musicclound',
            'name': query['name'] ?? '',
            'with_res_tag': 0,
          },
        );

      // ===== 登录 =====
      case '/login/qr/key':
        return _forward(
          method: 'GET',
          url: '/v2/qrcode',
          baseURL: 'https://login-user.kugou.com',
          params: {
            'appid': query['type'] == 'web' ? 1014 : 1001,
            'type': 1,
            'plat': 4,
            'qrcode_txt':
                'https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=${KugouConfig.appId}&',
            'srcappid': 2919,
          },
          encryptType: KugouEncryptType.web,
        );
      case '/login/qr/check':
        return _forward(
          method: 'GET',
          url: '/v2/get_userinfo_qrcode',
          baseURL: 'https://login-user.kugou.com',
          params: {
            'plat': 4,
            'appid': KugouConfig.appId,
            'srcappid': 2919,
            'qrcode': query['key'],
            'dev': KugouDevice.instance.serverDev,
          },
          encryptType: KugouEncryptType.web,
        );
      case '/login/logout':
        return _forward(
          method: 'POST',
          url: '/v1/logout',
          baseURL: 'https://login.user.kugou.com',
        );
      case '/captcha/sent':
        return _forward(
          method: 'POST',
          url: '/v7/send_mobile_code',
          baseURL: 'http://login.user.kugou.com',
          data: {
            'businessid': 5,
            'mobile': query['mobile']?.toString() ?? '',
            'plat': 3,
          },
        );
      case '/login/token':
        return _loginByToken();
      case '/login/cellphone':
        return _loginByVerifyCode(query);

      // ===== 杂项 =====
      case '/listen/timeadd':
        return _forward(
          method: 'POST',
          url: '/v1/listen_time',
          baseURL: 'https://listenservice.kugou.com',
          params: {'userid': _userId()},
        );

      default:
        throw KugouUnsupportedRoute(route);
    }
  }

  // ===== 端点实现 =====

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
      url: '/song_search_v2',
      params: {
        'keyword': keyword,
        'page': page,
        'pagesize': pageSize,
        'platform': 'WebFilter',
        'iscorrection': 1,
        'albumhide': 0,
        'nocollect': 0,
      },
      headers: const {'x-router': 'songsearch.kugou.com'},
    );
    return _flattenList(raw, const ['songs', 'song', 'lists']);
  }

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
      params: {
        'album_id': _int(q['album_id'], 0),
        'area_code': 1,
        'hash': (q['hash']?.toString() ?? '').toLowerCase(),
        'ssa_flag': 'is_fromtrack',
        'version': 11430,
        'page_id': 967177915,
        'quality': quality,
        'album_audio_id': _int(q['album_audio_id'], 0),
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

  /// 歌词候选搜索（`module/search_lyric.js`）。
  ///
  /// 上游实际端点为 `krcs.kugou.com/search`（`/v1/search` 已失效），
  /// 参数必须**按 key 排序**，否则 CDN 报 `cdn paramters must be sorted`。
  Future<Object?> _searchLyric(Map<String, Object?> q) async {
    return _request.send(
      method: 'GET',
      url: '/search',
      baseURL: 'https://krcs.kugou.com',
      params: {
        'ver': 1,
        'man': q['man'] ?? 'yes',
        'client': q['client'] ?? 'mobi',
        'keyword': q['keywords'] ?? '',
        'hash': q['hash'] ?? '',
        'album_audio_id': _int(q['album_audio_id'], 0),
        'duration': _int(q['duration'], 0),
      },
      clearDefaultParams: true,
      notSignature: true,
      sortQuery: true,
    );
  }

  /// 下载歌词（`module/lyric.js`）。
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

  /// 用户资料（`module/user_detail.js`）：`p` 为无填充 RSA 加密。
  Future<Object?> _userDetail() async {
    final clientTimeSec = _nowSec();
    final pk = KugouCrypto.rsaEncryptRaw(
      utf8.encode(jsonEncode({'token': _token(), 'clienttime': clientTimeSec})),
      KugouCrypto.parseRsaPublicKey(KugouConfig.publicLiteRsaKey),
    );
    return _request.send(
      method: 'POST',
      url: '/v3/get_my_info',
      params: {'plat': 1},
      data: {
        'visit_time': clientTimeSec,
        'usertype': 1,
        'p': pk,
        'userid': _userId(),
      },
      headers: const {'x-router': 'usercenter.kugou.com'},
    );
  }

  /// 私人 FM（`module/personal_fm.js`）。
  Future<Object?> _personalFm(Map<String, Object?> q) async {
    final data = <String, Object?>{
      'appid': KugouConfig.liteAppId,
      'clienttime': _nowMs(),
      'mid': KugouDevice.instance.mid,
      'action': q['action'] ?? 'play',
      'recommend_source_locked': 0,
      'song_pool_id': _int(q['song_pool_id'], 0),
      'callerid': 0,
      'm_type': 1,
      'platform': q['platform'] ?? 'ios',
      'area_code': 1,
      'remain_songcnt': _int(q['remain_songcnt'], 0),
      'clientver': KugouConfig.liteClientVer,
      'is_overplay': q['isOverplay'] == true ? 1 : 0,
      'mode': q['mode'] ?? 'normal',
      'fakem': 'ca981cfc583a4c37f28d2d49000013c16a0a',
      'key': _signParamsKey(_nowMs().toString()),
    };
    if (_userId() != 0) {
      data['userid'] = _userId();
      data['kguid'] = _userId();
    }
    if (_token().isNotEmpty) data['token'] = _token();
    for (final key in const ['hash', 'songid', 'playtime', 'cur_mark']) {
      final value = q[key] ?? q[key == 'songid' ? 'songId' : key];
      if (value != null) data[key] = value;
    }
    return _request.send(
      method: 'POST',
      url: '/v2/personal_recommend',
      data: data,
      headers: const {'x-router': 'persnfm.service.kugou.com'},
    );
  }

  /// 云盘列表（`module/user_cloud.js`）：AES 请求体 + RSA 会话串。
  Future<Object?> _userCloud(Map<String, Object?> q) async {
    final clientTimeSec = _nowSec();
    final aesKeyRaw = KugouUtil.randomString(6).toLowerCase();
    final digest = KugouCrypto.md5Hex(aesKeyRaw);
    final aesKey = digest.substring(0, 16);
    final aesIv = digest.substring(16, 32);

    final bodyBase64 = KugouCrypto.aesCbcEncryptBase64(
      utf8.encode(
        jsonEncode({
          'page': q['page'] ?? 1,
          'pagesize': q['pagesize'] ?? 30,
          'getkmr': 1,
        }),
      ),
      aesKey,
      aesIv,
    );

    final portrait = KugouCrypto.rsaEncryptPkcs1(
      utf8.encode(
        jsonEncode({'aes': aesKeyRaw, 'uid': _userId(), 'token': _token()}),
      ),
      KugouCrypto.parseRsaPublicKey(KugouConfig.publicLiteRsaKey),
    ).toUpperCase();

    final raw = await _request.send(
      method: 'POST',
      url: '/v1/get_list',
      baseURL: 'https://mcloudservice.kugou.com',
      params: {
        'clienttime': clientTimeSec,
        'mid': KugouDevice.instance.mid,
        'key': KugouSignature.signParamsKey('$clientTimeSec'),
        'clientver': KugouConfig.liteClientVer,
        'appid': KugouConfig.liteAppId,
        'p': portrait,
      },
      data: bodyBase64,
      clearDefaultParams: true,
      notSignature: true,
      rawResponse: true,
    );
    if (raw is! Uint8List || raw.isEmpty) return raw;

    final text = KugouCrypto.aesCbcDecryptHex(_hex(raw), aesKey, aesIv);
    try {
      return jsonDecode(text);
    } catch (_) {
      return text;
    }
  }

  // ===== 通用转发 =====

  Future<Object?> _forward({
    required String method,
    required String url,
    String? baseURL,
    Map<String, Object?>? params,
    Object? data,
    Map<String, String> headers = const {},
    bool encryptKey = false,
    bool sortQuery = false,
    KugouEncryptType encryptType = KugouEncryptType.android,
  }) {
    return _request.send(
      method: method,
      url: url,
      baseURL: baseURL,
      params: params ?? const {},
      data: data,
      headers: headers,
      encryptKey: encryptKey,
      sortQuery: sortQuery,
      encryptType: encryptType,
    );
  }

  // ===== 登录实现 =====

  /// 固定 AES key/iv（概念版，`login_token.js` / `login_cellphone.js`）。
  static const _liteLoginKey = 'c24f74ca2820225badc01946dba4fdf7';
  static const _liteLoginIv = 'adc01946dba4fdf7';
  static const _liteT2Key = 'fd14b35e3f81af3817a20ae7adae7020';
  static const _liteT2Iv = '17a20ae7adae7020';
  static const _liteT1Key = '5e4ef500e9597fe004bd09a46d8add98';
  static const _liteT1Iv = '04bd09a46d8add98';

  /// 用 token 刷新登录态（`module/login_token.js`）。
  Future<Object?> _loginByToken() async {
    final nowMs = _nowMs();
    final device = KugouDevice.instance;

    // AES(clienttime + token)，固定 key/iv
    final encrypt = KugouCrypto.aesCbcEncryptHex(
      utf8.encode(
        jsonEncode({'clienttime': nowMs ~/ 1000, 'token': _token()}),
      ),
      _liteLoginKey,
      _liteLoginIv,
    );
    // AES({}) 生成随机会话 key
    final sessionKey = KugouUtil.randomString(16).toLowerCase();
    final encryptParams = KugouCrypto.aesCbcEncryptHex(
      utf8.encode(jsonEncode(<String, Object?>{})),
      KugouCrypto.md5Hex(sessionKey).substring(0, 32),
      KugouCrypto.md5Hex(sessionKey).substring(16, 32),
    );
    final pk = KugouCrypto.rsaEncryptRaw(
      utf8.encode(jsonEncode({'clienttime_ms': nowMs, 'key': sessionKey})),
      KugouCrypto.parseRsaPublicKey(KugouConfig.publicLiteRsaKey),
    );
    final t2 = KugouCrypto.aesCbcEncryptHex(
      utf8.encode(
        '${device.guid}|0f607264fc6318a92b9e13c65db7cd3c|${device.mac}|${device.serverDev}|$nowMs',
      ),
      _liteT2Key,
      _liteT2Iv,
    );
    final t1 = KugouCrypto.aesCbcEncryptHex(
      utf8.encode('${_request.t1 ?? ''}|$nowMs'),
      _liteT1Key,
      _liteT1Iv,
    );

    final raw = await _request.send(
      method: 'POST',
      url: '/v5/login_by_token',
      baseURL: 'http://login.user.kugou.com',
      data: {
        'dfid': device.dfid,
        'p3': encrypt,
        'plat': 1,
        't1': t1,
        't2': t2,
        't3': 'MCwwLDAsMCwwLDAsMCwwLDA=',
        'pk': pk,
        'params': encryptParams,
        'userid': _userIdString(),
        'clienttime_ms': nowMs,
        'dev': device.serverDev,
      },
    );
    return _extractSecuParams(raw, sessionKey);
  }

  /// 手机验证码登录（`module/login_cellphone.js`）。
  Future<Object?> _loginByVerifyCode(Map<String, Object?> q) async {
    final nowMs = _nowMs();
    final device = KugouDevice.instance;
    final mobile = q['mobile']?.toString() ?? '';
    final masked = mobile.length >= 11
        ? '${mobile.substring(0, 2)}*****${mobile.substring(10, 11)}'
        : mobile;

    final sessionKey = KugouUtil.randomString(16).toLowerCase();
    final sessionDigest = KugouCrypto.md5Hex(sessionKey);
    final encrypt = KugouCrypto.aesCbcEncryptHex(
      utf8.encode(jsonEncode({'mobile': mobile, 'code': q['code'] ?? ''})),
      sessionDigest.substring(0, 32),
      sessionDigest.substring(16, 32),
    );
    final t2 = KugouCrypto.aesCbcEncryptHex(
      utf8.encode(
        '${device.guid}|0f607264fc6318a92b9e13c65db7cd3c|${device.mac}|${device.serverDev}|$nowMs',
      ),
      _liteT2Key,
      _liteT2Iv,
    );
    final t1 = KugouCrypto.aesCbcEncryptHex(
      utf8.encode('|$nowMs'),
      _liteT1Key,
      _liteT1Iv,
    );
    final pk = KugouCrypto.rsaEncryptRaw(
      utf8.encode(jsonEncode({'clienttime_ms': nowMs, 'key': sessionKey})),
      KugouCrypto.parseRsaPublicKey(KugouConfig.publicLiteRsaKey),
    );

    final data = <String, Object?>{
      'plat': 1,
      'support_multi': 1,
      't1': t1,
      't2': t2,
      'clienttime_ms': nowMs,
      'mobile': masked,
      'key': _signParamsKey('$nowMs'),
      'pk': pk,
      'params': encrypt,
      'dfid': device.dfid,
      'dev': device.serverDev,
      'gitversion': '5f0b7c4',
    };
    if (q['userId'] != null) data['userid'] = q['userId'];

    final raw = await _request.send(
      method: 'POST',
      url: '/v7/login_by_verifycode',
      baseURL: 'https://loginserviceretry.kugou.com',
      data: data,
      headers: const {
        'support-calm': '1',
        'User-Agent': 'Android16-1070-11440-130-0-LOGIN-wifi',
      },
    );
    return _extractSecuParams(raw, sessionKey);
  }

  /// 解密登录响应中的 `secu_params` 并合并回 `data`。
  Object? _extractSecuParams(Object? raw, String sessionKey) {
    if (raw is! Map) return raw;
    final body = Map<String, Object?>.from(raw);
    final data = body['data'];
    if (data is! Map) return raw;
    final secu = data['secu_params']?.toString();
    if (secu == null || secu.isEmpty) return raw;

    final digest = KugouCrypto.md5Hex(sessionKey);
    try {
      final text = KugouCrypto.aesCbcDecryptHex(
        secu,
        digest.substring(0, 32),
        digest.substring(16, 32),
      );
      final decoded = jsonDecode(text);
      if (decoded is Map) {
        body['data'] = {...Map<String, Object?>.from(data), ...decoded};
      } else if (decoded is String) {
        body['data'] = {...Map<String, Object?>.from(data), 'token': decoded};
      }
    } catch (_) {
      // 解密失败保留原始响应
    }
    return body;
  }

  // ===== 整形 / 工具 =====

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

  Object? _normalizeSongUrl(Object? raw) {
    if (raw is! Map) return raw;
    final data = raw['data'];
    final source = Map<String, Object?>.from(data is Map ? data : raw);

    final url = source['url'];
    final hash = source['hash'] ?? '';
    if (url is List || url is String) {
      return <String, Object?>{
        ...source,
        'url': url is String ? [url] : url,
        'hash': hash,
      };
    }
    return raw;
  }

  Object? _decodeLyricBody(Object? raw, String fmt) {
    if (raw is! Map) return raw;
    final rawData = raw['data'];
    final body = rawData is Map
        ? Map<String, Object?>.from(rawData)
        : Map<String, Object?>.from(raw);

    final content = body['content']?.toString();
    if (content == null || content.isEmpty) return raw;

    final contentType = body['contenttype'];
    final isPlain = fmt == 'lrc' ||
        (contentType != null && int.tryParse('$contentType') != 0);

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

  String _signParamsKey(String data) =>
      KugouSignature.signParamsKey(data);

  int _nowMs() => DateTime.now().millisecondsSinceEpoch;
  int _nowSec() => DateTime.now().millisecondsSinceEpoch ~/ 1000;

  int _userId() => int.tryParse(_request.userId ?? '0') ?? 0;
  String _userIdString() => _request.userId ?? '0';
  String _token() => _request.token ?? '';

  int _int(Object? value, int fallback) {
    if (value == null) return fallback;
    if (value is int) return value;
    return int.tryParse(value.toString()) ?? fallback;
  }

  String _hex(Uint8List bytes) =>
      bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();

  void close() => _request.close();
}

/// 该路由尚未内置（调用方可回退到外部服务器）。
class KugouUnsupportedRoute implements Exception {
  KugouUnsupportedRoute(this.route);

  final String route;

  @override
  String toString() => '内置 API 暂不支持路由：$route';
}
