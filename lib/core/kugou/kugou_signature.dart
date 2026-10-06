import 'dart:convert';

import 'kugou_crypto.dart';

/// 内置酷狗 API 的平台配置（概念版 lite）。
///
/// 移植自 KuGouMusicApi（MIT）的 `util/config.json` 与 `util/helper.js`。
class KugouConfig {
  KugouConfig._();

  /// 概念版 appid / clientver（`platform=lite`）。
  static const int liteAppId = 3116;
  static const int liteClientVer = 11440;

  /// 标准版（部分接口如「刷刷」必须用标准版签名）。
  static const int appId = 1005;
  static const int clientVer = 20489;

  /// 默认网关。
  static const String gateway = 'https://gateway.kugou.com';

  // ===== 签名盐值 =====
  /// Android 版 signature 盐（概念版）。
  static const String route = 'LnT6xpN3khm36zse0QzvmgTZ3waWdRSA';

  /// Android 版 signature 盐（标准版）。
  static const String routeStandard = 'OIlwieks28dk2k092lksi2UIkp';

  /// Web 版 signature 盐。
  static const String routeWeb = 'NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt';

  /// signKey 盐（概念版）。
  static const String signKeyStr = '185672dd44712f60bb1736df5a377e82';

  /// signCloudKey 盐。
  static const String signCloudStr = 'ebd1ac3134c880bda6a2194537843caa0162e2e7';

  /// signParams 盐。
  static const String signParamsStr = 'R6snCXJgbCaj9WFRJKefTMIFp0ey6Gza';

  /// 注册接口盐。
  static const String signRegisterStr = '1014';

  // ===== RSA 公钥（概念版 / 标准版）=====
  static const String publicLiteRsaKey =
      '-----BEGIN PUBLIC KEY-----\n'
      'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO0'
      '1rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTX'
      'LgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ0'
      '8/q6ifSioBszvb3QiwIDAQAB\n'
      '-----END PUBLIC KEY-----';

  static const String publicRsaKey =
      '-----BEGIN PUBLIC KEY-----\n'
      'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/g'
      'bjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+'
      'UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE'
      '5E221wf/4WLFxwAtRQIDAQAB\n'
      '-----END PUBLIC KEY-----';

  /// 云歌单 appkey（概念版）。
  static const String cloudListAppKey = 'LnT6xpN3khm36zse0QzvmgTZ3waWdRSA';
}

/// 酷狗 API 请求签名工具（`util/helper.js` 的 Dart 移植）。
class KugouSignature {
  KugouSignature._();

  /// 把参数按 `key=value` 拼接并按 key 排序；对象值先 JSON 序列化。
  static String paramsJoinedSorted(Map<String, Object?> params) {
    final keys = params.keys.toList()..sort();
    return keys
        .map((k) => '$k=${_coerce(params[k])}')
        .join();
  }

  /// 把参数按 `keyvalue`（无等号）拼接并按 key 排序。
  static String paramsKeyValueSorted(Map<String, Object?> params) {
    final keys = params.keys.toList()..sort();
    return keys
        .map((k) => '$k${_coerce(params[k])}')
        .join();
  }

  /// 仅取值、排序后拼接（注册接口用）。
  static String valuesJoinedSorted(Map<String, Object?> params) {
    final values = params.values.map(_coerce).toList()..sort();
    return values.join();
  }

  /// Web 版 signature。
  static String signatureWebParams(
    Map<String, Object?> params, [
    String? data,
  ]) {
    final s = paramsJoinedSorted(params);
    return KugouCrypto.md5Hex(
      '${KugouConfig.routeWeb}$s${data ?? ''}${KugouConfig.routeWeb}',
    );
  }

  /// Android 版 signature（概念版）。
  static String signatureAndroidParams(
    Map<String, Object?> params, [
    Object? data,
  ]) {
    final s = paramsJoinedSorted(params);
    if (data is List<int>) {
      return KugouCrypto.md5Hex4(
        utf8.encode(KugouConfig.route),
        utf8.encode(s),
        data,
        utf8.encode(KugouConfig.route),
      );
    }
    return KugouCrypto.md5Hex(
      '${KugouConfig.route}$s${data ?? ''}${KugouConfig.route}',
    );
  }

  /// Android 版 signature（标准版，部分接口必需）。
  static String signatureAndroidParamsStandard(
    Map<String, Object?> params, [
    Object? data,
  ]) {
    final s = paramsJoinedSorted(params);
    return KugouCrypto.md5Hex(
      '${KugouConfig.routeStandard}$s${data ?? ''}${KugouConfig.routeStandard}',
    );
  }

  /// 设备注册接口 signature。
  static String signatureRegisterParams(Map<String, Object?> params) {
    final s = valuesJoinedSorted(params);
    return KugouCrypto.md5Hex(
      '${KugouConfig.signRegisterStr}$s${KugouConfig.signRegisterStr}',
    );
  }

  /// 通用 sign。
  static String signParams(Map<String, Object?> params, [String? data]) {
    final s = paramsKeyValueSorted(params);
    return KugouCrypto.md5Hex('$s${data ?? ''}${KugouConfig.signParamsStr}');
  }

  /// signKey。
  static String signKey(
    String hash,
    String mid, {
    Object? userId,
    Object? appId,
  }) {
    final uid = userId ?? 0;
    final aid = appId ?? KugouConfig.liteAppId;
    return KugouCrypto.md5Hex(
      '$hash${KugouConfig.signKeyStr}$aid$mid$uid',
    );
  }

  /// 云盘 signCloudKey。
  static String signCloudKey(String hash, String pid) {
    return KugouCrypto.md5Hex(
      'musicclound$hash$pid${KugouConfig.signCloudStr}',
    );
  }

  /// signParamsKey。
  static String signParamsKey(
    String data, {
    Object? appId,
    Object? clientVer,
  }) {
    final aid = appId ?? KugouConfig.liteAppId;
    final cv = clientVer ?? KugouConfig.liteClientVer;
    return KugouCrypto.md5Hex(
      '$aid${KugouConfig.route}$cv$data',
    );
  }

  /// JS 模板字符串强制转换。
  static String _coerce(Object? v) {
    if (v == null) return 'null';
    if (v is String) return v;
    if (v is num || v is bool) return v.toString();
    return jsonEncode(v);
  }
}
