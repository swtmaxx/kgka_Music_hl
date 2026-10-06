import 'dart:convert';
import 'dart:math';

import 'kugou_crypto.dart';

/// 酷狗 API 通用工具（`util/util.js` 的 Dart 移植）。
class KugouUtil {
  KugouUtil._();

  static final Random _rnd = Random();

  /// 随机字符串，字符池 `1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ`。
  ///
  /// 与 JS 一致：索引 = `ceil((36 - 1) * random)`，即 0..35。
  static String randomString([int len = 16]) {
    const keyString = '1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    final buffer = StringBuffer();
    for (var i = 0; i < len; i++) {
      final index = (35 * _rnd.nextDouble()).ceil().clamp(0, 35);
      buffer.write(keyString[index]);
    }
    return buffer.toString();
  }

  /// 随机数字字符串，字符池 `1234567890`。
  static String randomNumber([int len = 16]) {
    const keyString = '1234567890';
    final buffer = StringBuffer();
    for (var i = 0; i < len; i++) {
      final index = (9 * _rnd.nextDouble()).ceil().clamp(0, 9);
      buffer.write(keyString[index]);
    }
    return buffer.toString();
  }

  /// 由字符串计算设备 MID：MD5 的十六进制按大整数转十进制字符串。
  static String calculateMid(String input) {
    return KugouCrypto.md5ToDecimalString(utf8.encode(input));
  }

  /// 生成 UUID v4 形态的 GUID。
  ///
  /// `e() = ((65536 * (1 + random)) | 0).toString(16).substring(1)` → 4 位 hex。
  static String getGuid() {
    String e() {
      final value = (65536 * (1 + _rnd.nextDouble())).floor();
      final hex = value.toRadixString(16);
      return hex.length > 1 ? hex.substring(1) : hex;
    }

    return '${e()}${e()}-${e()}-${e()}-${e()}-${e()}${e()}${e()}';
  }

  /// `encodeURIComponent` 兼容实现（保留 unreserved 字符）。
  static String encodeUriComponent(String value) {
    const unreserved =
        'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*\'()';
    final buffer = StringBuffer();
    for (final b in utf8.encode(value)) {
      if (b < 128 && unreserved.contains(String.fromCharCode(b))) {
        buffer.write(String.fromCharCode(b));
      } else {
        buffer.write('%${b.toRadixString(16).padLeft(2, '0').toUpperCase()}');
      }
    }
    return buffer.toString();
  }

  /// 解析 Cookie 字符串为 Map（与 `server.js` 的 cookie 中间件一致）。
  static Map<String, String> parseCookieHeader(String? header) {
    final result = <String, String>{};
    if (header == null || header.isEmpty) return result;
    for (final pair in header.split(RegExp(r';\s+'))) {
      final index = pair.indexOf('=');
      if (index < 1 || index == pair.length - 1) continue;
      final key = Uri.decodeComponent(pair.substring(0, index).trim());
      final value = Uri.decodeComponent(pair.substring(index + 1).trim());
      result[key] = value;
    }
    return result;
  }

  /// 从响应 Set-Cookie 提取 `key=value`。
  static String parseCookieString(String setCookie) {
    final index = setCookie.indexOf(';');
    return index >= 0 ? setCookie.substring(0, index) : setCookie;
  }

  /// 判断是否为 UUID v4 格式。
  static bool isUuidV4(String? value) {
    if (value == null) return false;
    return RegExp(
      r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
      caseSensitive: false,
    ).hasMatch(value);
  }
}
