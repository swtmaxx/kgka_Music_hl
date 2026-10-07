import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import 'kugou_device.dart';
import 'kugou_signature.dart';
import 'kugou_util.dart';

/// 签名加密类型。
enum KugouEncryptType { android, web, register }

/// 内置 API 请求异常。
class KugouApiException implements Exception {
  KugouApiException(this.message, {this.statusCode, this.body});

  final String message;
  final int? statusCode;
  final Object? body;

  @override
  String toString() => 'KugouApiException($statusCode): $message';
}

/// 内置酷狗 API 的底层请求发送器（`util/request.js` 的 Dart 移植）。
///
/// 负责：注入设备标识与默认参数 → 生成签名 → 配置请求头 → 发送 → 解析响应。
class KugouRequest {
  KugouRequest({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  /// 当前登录态（由 `MusicApi.setSession` 同步）。
  String? token;
  String? t1;
  String? userId;

  static const _userAgent =
      'Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi';

  /// 发送请求并返回解析后的 JSON（Map 或 List）。
  ///
  /// [rawResponse] 为 true 时返回原始字节（`Uint8List`），用于返回体是二进制
  /// 或加密内容的接口（如设备注册）。
  Future<Object?> send({
    required String method,
    required String url,
    Map<String, Object?> params = const {},
    Object? data,
    String? baseURL,
    Map<String, String> headers = const {},
    KugouEncryptType encryptType = KugouEncryptType.android,
    bool encryptKey = false,
    bool notSignature = false,
    bool clearDefaultParams = false,
    bool clearDefaultHeaders = false,
    bool rawResponse = false,
    bool sortQuery = false,
  }) async {
    final device = KugouDevice.instance;
    await device.ensureLoaded();

    final dfid = device.dfid;
    final mid = device.mid;
    final uuid = device.uuid;
    final clientTime = DateTime.now().millisecondsSinceEpoch ~/ 1000;

    // ===== 默认参数 =====
    final defaultParams = <String, Object?>{
      'dfid': dfid,
      'mid': mid,
      'uuid': uuid,
      'appid': KugouConfig.liteAppId,
      'clientver': KugouConfig.liteClientVer,
      'clienttime': clientTime,
    };
    if (token != null && token!.isNotEmpty) defaultParams['token'] = token;
    if (userId != null && userId!.isNotEmpty && userId != '0') {
      defaultParams['userid'] = userId;
    }

    final merged = clearDefaultParams
        ? Map<String, Object?>.of(params)
        : <String, Object?>{...defaultParams, ...params};

    // ===== 请求头 =====
    final baseHeaders = <String, String>{
      'dfid': dfid,
      'clienttime': '$clientTime',
      'mid': mid,
      'kg-rc': '1',
      'kg-thash': '5d816a0',
      'kg-rec': '1',
      'kg-rf': 'B9EDA08A64250DEFFBCADDEE00F8F25F',
    };

    // ===== signKey =====
    if (encryptKey) {
      merged['key'] = KugouSignature.signKey(
        merged['hash']?.toString() ?? '',
        merged['mid']?.toString() ?? mid,
        userId: merged['userid'],
        appId: merged['appid'],
      );
    }

    // ===== 请求体序列化 =====
    // 支持三种形态：String（原样）/ Uint8List（二进制原样）/ 其它（JSON 编码）。
    final Object bodyPayload;
    if (data == null) {
      bodyPayload = '';
    } else if (data is String || data is Uint8List) {
      bodyPayload = data;
    } else {
      bodyPayload = jsonEncode(data);
    }

    // ===== 签名 =====
    if (merged['signature'] == null && !notSignature) {
      switch (encryptType) {
        case KugouEncryptType.register:
          merged['signature'] = KugouSignature.signatureRegisterParams(merged);
        case KugouEncryptType.web:
          merged['signature'] = KugouSignature.signatureWebParams(
            merged,
            bodyPayload is String ? bodyPayload : '',
          );
        case KugouEncryptType.android:
          merged['signature'] = KugouSignature.signatureAndroidParams(
            merged,
            bodyPayload is Uint8List ? bodyPayload : bodyPayload as String,
          );
      }
    }

    // ===== 组装请求 =====
    final query = <String, String>{};
    for (final entry in merged.entries) {
      final value = entry.value;
      if (value == null) continue;
      query[entry.key] = value is String ? value : jsonEncode(value);
    }

    // 部分 CDN 接口要求 query 参数按 key 升序，否则报 "cdn paramters must be sorted"。
    final orderedQuery = <String, String>{};
    if (sortQuery) {
      final keys = query.keys.toList()..sort();
      for (final key in keys) {
        orderedQuery[key] = query[key]!;
      }
    } else {
      orderedQuery.addAll(query);
    }

    final uri = Uri.parse(baseURL ?? KugouConfig.gateway)
        .replace(path: url, queryParameters: orderedQuery);

    final requestHeaders = <String, String>{
      if (!clearDefaultHeaders) 'User-Agent': _userAgent,
      ...headers,
      if (!clearDefaultHeaders) ...baseHeaders,
    };
    if (method.toUpperCase() == 'POST') {
      requestHeaders.putIfAbsent(
        'Content-Type',
        () => 'application/json;charset=utf-8',
      );
    }

    final http.Response response;
    try {
      response = method.toUpperCase() == 'POST'
          ? await _client
              .post(uri, headers: requestHeaders, body: bodyPayload)
              .timeout(const Duration(seconds: 20))
          : await _client
              .get(uri, headers: requestHeaders)
              .timeout(const Duration(seconds: 20));
    } catch (error) {
      throw KugouApiException('网络请求失败：$error', statusCode: 502);
    }

    // ===== 捕获设备标识（部分接口通过 Set-Cookie 下发 dfid/token）=====
    final setCookie = response.headers['set-cookie'];
    if (setCookie != null) {
      final cookies = KugouUtil.parseCookieHeader(
        setCookie.replaceAll(RegExp(r',\s*(?=[A-Za-z_])'), '; '),
      );
      final newDfid = cookies['dfid'];
      if (newDfid != null && newDfid.isNotEmpty && newDfid != device.dfid) {
        await device.setDfid(newDfid);
      }
    }

    // ===== 解析响应 =====
    if (rawResponse) {
      if (response.statusCode >= 400) {
        throw KugouApiException(
          '请求失败（HTTP ${response.statusCode}）',
          statusCode: response.statusCode,
        );
      }
      return response.bodyBytes;
    }

    final text = utf8.decode(response.bodyBytes, allowMalformed: true);
    Object? parsed;
    if (text.trim().isEmpty) {
      parsed = null;
    } else {
      try {
        parsed = jsonDecode(text);
      } catch (_) {
        parsed = text;
      }
    }

    if (response.statusCode >= 400) {
      throw KugouApiException(
        _errorMessage(parsed) ?? '请求失败（HTTP ${response.statusCode}）',
        statusCode: response.statusCode,
        body: parsed,
      );
    }

    // 上游业务错误：status==0 或 error_code != 0
    if (parsed is Map) {
      final status = parsed['status'];
      final errorCode = parsed['error_code'] ?? parsed['err_code'];
      final hasError = errorCode != null && errorCode != 0;
      if (status == 0 || hasError) {
        throw KugouApiException(
          _errorMessage(parsed) ?? '请求被拒绝',
          statusCode: 502,
          body: parsed,
        );
      }
    }

    return parsed;
  }

  String? _errorMessage(Object? parsed) {
    if (parsed is! Map) return null;
    for (final key in const ['error', 'msg', 'errmsg', 'message', 'error_msg']) {
      final value = parsed[key];
      if (value is String && value.isNotEmpty) return value;
    }
    final code = parsed['errcode'] ?? parsed['error_code'] ?? parsed['err_code'];
    if (code != null) return '错误码 $code';
    return null;
  }

  void close() => _client.close();
}
