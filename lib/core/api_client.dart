import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import 'kugou/kugou_client.dart';
import 'kugou/kugou_request.dart';

class ApiException implements Exception {
  ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => 'ApiException($statusCode): $message';
}

/// 酷狗 API 客户端。
///
/// 默认请求**外置（自建）API 服务器**：`AppConfig.effectiveBaseUrl`，
/// 通过 `X-Kg-Session-Id` / `t1` 头携带登录态，响应体统一解包 `data`。
///
/// 在设置里开启「内置 API」后改为直连酷狗（`lib/core/kugou/`）；
/// 此时内置尚未实现的路由、以及内置报错时，仍会回退到外置服务器。
class ApiClient {
  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;
  final KugouClient _builtIn = KugouClient();

  String? token;
  String? t1;

  /// 后端会话 key（响应头 `X-Kg-Session-Id`），仅外置服务器使用。
  String? sessionId;

  /// 当前登录用户的**酷狗 userid**（内置 API 需要它，缺失会以 userid=0 请求
  /// 而被服务端拒绝，报 20006 / 20010）。
  String? userId;

  /// 是否使用**内置酷狗 API**（直连酷狗，无需外部服务器）。
  ///
  /// 默认 **false**：走外置服务器。应用启动时由
  /// `ApiClient.useBuiltInApi = AppConfig.useBuiltInApi` 同步。
  static bool useBuiltInApi = false;

  /// 内置 API 失败时是否回退外置服务器（避免内置实现有 bug 时完全不可用）。
  static bool fallbackOnBuiltInError = true;

  /// 内置客户端（供上层同步登录态 / 预热）。
  KugouClient get builtIn => _builtIn;

  Future<dynamic> get(String path, [Map<String, Object?> query = const {}]) {
    return _dispatch('GET', path, query);
  }

  Future<dynamic> post(
    String path, {
    Map<String, Object?> query = const {},
    Map<String, Object?>? body,
  }) {
    return _dispatch('POST', path, query, body: body);
  }

  /// 统一分发：默认走外置服务器；开启内置 API 时先走内置，
  /// 未内置或内置报错则回退外置服务器。
  ///
  /// 内置实现只接收一个参数 Map，因此 POST body 会被合并进 query；
  /// 外置服务器则严格区分 query 与 JSON body。
  Future<dynamic> _dispatch(
    String method,
    String path,
    Map<String, Object?> query, {
    Map<String, Object?>? body,
  }) async {
    if (useBuiltInApi) {
      _builtIn.setSession(token: token, t1: t1, userId: userId);
      try {
        // 内置实现已按路由把响应整形成与外部服务器一致的结构
        // （外部服务器是**逐路由**决定是否解包 `data` 的，不能统一处理）。
        return await _builtIn.handle(method, path, {...query, ...?body});
      } on KugouUnsupportedRoute {
        // 路由未内置：回退到外置服务器
      } on KugouApiException catch (error) {
        if (!fallbackOnBuiltInError) {
          throw ApiException(error.message, statusCode: error.statusCode);
        }
      }
    }

    if (method == 'POST') {
      return _sendWithRetry(
        () => _client.post(
          AppConfig.apiUri(path, query),
          headers: _headers,
          body: body == null ? null : jsonEncode(body),
        ),
      );
    }
    return _sendWithRetry(
      () => _client.get(AppConfig.apiUri(path, query), headers: _headers),
    );
  }

  Map<String, String> get _headers {
    final headers = <String, String>{
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    };

    // 后端会话 key：sessionId 优先（它才是服务端下发的真实 session），
    // token 作为扫码/登录早期的兼容值。
    final session = sessionId?.isNotEmpty == true
        ? sessionId
        : (token?.isNotEmpty == true ? token : null);
    if (session != null) {
      headers['X-Kg-Session-Id'] = session;
    }
    if (t1 case final value?) {
      headers['t1'] = value;
    }

    return headers;
  }

  /// 带自动重试的请求发送。
  ///
  /// 对连接超时、5xx 服务器错误自动重试，指数退避（500ms、1s）。
  /// 其他错误（如 4xx、格式异常）不重试，直接抛出。
  Future<dynamic> _sendWithRetry(
    Future<http.Response> Function() request, {
    int maxRetries = 2,
  }) async {
    for (var attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        final response = await request().timeout(
          const Duration(seconds: 20),
          onTimeout: () => throw http.ClientException('请求超时'),
        );
        // 5xx 服务器错误可重试
        if (response.statusCode >= 500 && attempt < maxRetries) {
          await Future.delayed(Duration(milliseconds: 500 * (1 << attempt)));
          continue;
        }
        return _processResponse(response);
      } on http.ClientException {
        // 网络连接异常（连接超时、断网等），重试
        if (attempt < maxRetries) {
          await Future.delayed(Duration(milliseconds: 500 * (1 << attempt)));
          continue;
        }
        rethrow;
      } on FormatException {
        // 非 5xx 的格式异常不重试
        rethrow;
      }
    }
    throw ApiException('请求失败，已重试 $maxRetries 次');
  }

  /// 处理响应：更新 sessionId、校验状态码、解码 JSON。
  Future<dynamic> _processResponse(http.Response response) {
    final responseSessionId = response.headers['x-kg-session-id'];
    if (responseSessionId != null && responseSessionId.isNotEmpty) {
      sessionId = responseSessionId;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(response.body, statusCode: response.statusCode);
    }
    if (response.body.trim().isEmpty) {
      return Future.value(null);
    }
    try {
      final decoded = jsonDecode(response.body);
      return Future.value(unwrapData(decoded));
    } on FormatException {
      return Future.value(response.body);
    }
  }

  void close() {
    _builtIn.close();
    _client.close();
  }
}

dynamic unwrapData(dynamic json) {
  // 兼容 `Map<String, dynamic>`（jsonDecode 产物）与内置实现构造的 `Map<String, Object?>`。
  if (json is Map) {
    final data = json['data'];
    // 只在 data 是**结构化载荷**（Map/List）时解包。
    // 部分路由的 data 是提示文案，例如登录失败时服务端返回：
    //   {"userid":null,"token":null,"t1":null,"data":"验证码过期",
    //    "status":0,"error_code":20020}
    // 此时若把 data 当结果返回，就会丢掉 status / error_code，
    // 让上层把「验证码过期」误报成「登录失败」且拿不到错误码。
    if (data is Map || data is List) return data;
  }
  return json;
}
