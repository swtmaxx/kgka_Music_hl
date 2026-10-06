import 'dart:convert';
import 'dart:typed_data';

import 'kugou_crypto.dart';
import 'kugou_device.dart';
import 'kugou_request.dart';
import 'kugou_signature.dart';
import 'kugou_util.dart';

/// 设备注册（`module/register_dev.js` 的 Dart 移植）。
///
/// 未注册的设备在请求播放地址等接口时会被上游以 `errcode 20028`（需要安全验证）
/// 拒绝。注册一次拿到 `dfid` 并持久化后，后续请求即正常。
class KugouRegister {
  KugouRegister._();

  static const String _baseUrl = 'https://userservice.kugou.com';
  static const String _path = '/risk/v2/r_register_dev';

  /// 注册设备并把 `dfid` 写入 [KugouDevice]。
  ///
  /// 返回是否成功。失败不抛异常（调用方继续用未注册身份请求）。
  static Future<bool> register(KugouRequest request) async {
    final device = KugouDevice.instance;
    await device.ensureLoaded();

    // 1) AES 加密设备指纹
    final aesKeyRaw = KugouUtil.randomString(6).toLowerCase();
    final digest = KugouCrypto.md5Hex(aesKeyRaw);
    final aesKey = digest.substring(0, 16);
    final aesIv = digest.substring(16, 32);

    final fingerprint = <String, Object?>{
      'availableRamSize': 4983533568,
      'availableRomSize': 48114719,
      'availableSDSize': 48114717,
      'basebandVer': '',
      'batteryLevel': 100,
      'batteryStatus': 3,
      'brand': 'Redmi',
      'buildSerial': 'unknown',
      'device': 'marble',
      'imei': device.guid,
      'imsi': '',
      'manufacturer': 'Xiaomi',
      'uuid': device.guid,
      'accelerometer': false,
      'accelerometerValue': '',
      'gravity': false,
      'gravityValue': '',
      'gyroscope': false,
      'gyroscopeValue': '',
      'light': false,
      'lightValue': '',
      'magnetic': false,
      'magneticValue': '',
      'orientation': false,
      'orientationValue': '',
      'pressure': false,
      'pressureValue': '',
      'step_counter': false,
      'step_counterValue': '',
      'temperature': false,
      'temperatureValue': '',
    };
    final bodyBase64 = KugouCrypto.aesCbcEncryptBase64(
      utf8.encode(jsonEncode(fingerprint)),
      aesKey,
      aesIv,
    );

    // 2) RSA 加密会话串 p
    final portrait = KugouCrypto.rsaEncryptPkcs1(
      utf8.encode(
        jsonEncode({'aes': aesKeyRaw, 'uid': 0, 'token': ''}),
      ),
      KugouCrypto.parseRsaPublicKey(KugouConfig.publicLiteRsaKey),
    );

    // 3) POST 注册
    final raw = await request.send(
      method: 'POST',
      url: _path,
      baseURL: _baseUrl,
      params: {'part': 1, 'platid': 1, 'p': portrait},
      data: bodyBase64,
      rawResponse: true,
    );
    if (raw is! Uint8List || raw.isEmpty) return false;

    // 4) AES 解密响应
    final text = KugouCrypto.aesCbcDecryptHex(
      _hex(raw),
      aesKey,
      aesIv,
    );
    final decoded = jsonDecode(text);
    if (decoded is! Map) return false;
    if (decoded['status'] != 1) return false;
    final data = decoded['data'];
    if (data is! Map) return false;
    final dfid = data['dfid']?.toString();
    if (dfid == null || dfid.isEmpty) return false;

    await device.setDfid(dfid);
    return true;
  }

  static String _hex(Uint8List bytes) =>
      bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
}
