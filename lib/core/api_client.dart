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
/// 本应用**只直连酷狗**：所有请求都交给 `lib/core/kugou/` 里的内置实现，
/// 不再存在任何外部 / 自建服务器路径，也没有「回退」逻辑。
///
/// 内置尚未实现的路由会抛 [ApiException]（消息里带上路由名），由上层提示用户。
class ApiClient {
  ApiClient();

  final KugouClient _builtIn = KugouClient();

  String? token;
  String? t1;
  String? sessionId;

  /// 内置客户端（供上层同步登录态）。
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

  /// 分发到内置实现。
  ///
  /// 内置实现只接收一个参数 Map，因此 POST body 会被合并进 query。
  Future<dynamic> _dispatch(
    String method,
    String path,
    Map<String, Object?> query, {
    Map<String, Object?>? body,
  }) async {
    _builtIn.setSession(token: token, t1: t1, userId: sessionId);
    try {
      return await _builtIn.handle(method, path, {...query, ...?body});
    } on KugouUnsupportedRoute catch (error) {
      throw ApiException('该功能暂未内置（${error.route}），请等待后续版本');
    } on KugouApiException catch (error) {
      throw ApiException(error.message, statusCode: error.statusCode);
    }
  }

  void close() => _builtIn.close();
}
