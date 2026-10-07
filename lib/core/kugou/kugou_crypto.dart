import 'dart:convert';
import 'dart:io' show ZLibDecoder;
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart' as c;
import 'package:pointycastle/export.dart' as pc;

/// 内置酷狗 API 的加密原语。
///
/// 移植自 KuGouMusicApi（MIT）的 `util/crypto.js`：
/// - MD5 / SHA1
/// - AES-128-CBC（PKCS7）
/// - RSA PKCS#1 v1.5 加密（1024-bit 公钥）
/// - 云歌单的 AES(Base64) 加解密
class KugouCrypto {
  KugouCrypto._();

  // ===== MD5 / SHA1 =====

  /// MD5，返回 32 位小写 hex。对象会先 JSON 序列化（与 JS `cryptoMd5` 一致）。
  static String md5Hex(Object data) {
    final text = data is String ? data : jsonEncode(data);
    return c.md5.convert(utf8.encode(text)).toString();
  }

  static String sha1Hex(Object data) {
    final text = data is String ? data : jsonEncode(data);
    return c.sha1.convert(utf8.encode(text)).toString();
  }

  /// 分段 MD5：md5(a || b || c || d)，用于带二进制请求体的签名。
  static String md5Hex4(
    List<int> a,
    List<int> b,
    List<int> cBytes,
    List<int> d,
  ) {
    return c.md5.convert(<int>[...a, ...b, ...cBytes, ...d]).toString();
  }

  /// MD5 的十进制表示（`calculateMid` 用：把 hex 当大整数转十进制字符串）。
  static String md5ToDecimalString(List<int> data) {
    final hex = c.md5.convert(data).toString();
    return _hexToDecimal(hex);
  }

  // ===== AES-128-CBC =====

  /// AES-CBC 加密，返回 hex。
  ///
  /// key 支持 16/24/32 字符（AES-128/192/256）；iv 必须 16 字符。
  static String aesCbcEncryptHex(List<int> data, String key, String iv) {
    final engine = pc.CBCBlockCipher(pc.AESEngine())
      ..init(
        true,
        pc.ParametersWithIV(
          pc.KeyParameter(_keyBytes(key)),
          _ivBytes(iv),
        ),
      );
    final input = _pkcs7Pad(Uint8List.fromList(data), 16);
    final out = Uint8List(input.length);
    for (var off = 0; off < input.length; off += 16) {
      engine.processBlock(input, off, out, off);
    }
    return _hex(out);
  }

  /// AES-CBC 解密 hex，返回 UTF-8 字符串（自动去 PKCS7 padding）。
  static String aesCbcDecryptHex(String hexCipher, String key, String iv) {
    final engine = pc.CBCBlockCipher(pc.AESEngine())
      ..init(
        false,
        pc.ParametersWithIV(
          pc.KeyParameter(_keyBytes(key)),
          _ivBytes(iv),
        ),
      );
    final input = _fromHex(hexCipher);
    final out = Uint8List(input.length);
    for (var off = 0; off < input.length; off += 16) {
      engine.processBlock(input, off, out, off);
    }
    return utf8.decode(_pkcs7Unpad(out), allowMalformed: true);
  }

  /// 云歌单 AES：Base64 输出。
  static String aesCbcEncryptBase64(List<int> data, String key, String iv) {
    final hexCipher = aesCbcEncryptHex(data, key, iv);
    return base64.encode(_fromHex(hexCipher));
  }

  // ===== RSA =====

  /// RSA PKCS#1 v1.5 加密，返回小写 hex（与 JS `rsaEncrypt2` 一致）。
  static String rsaEncryptPkcs1(List<int> data, pc.RSAPublicKey publicKey) {
    final engine = pc.PKCS1Encoding(pc.RSAEngine())
      ..init(
        true,
        pc.ParametersWithRandom(
          pc.PublicKeyParameter<pc.RSAPublicKey>(publicKey),
          _fortuna(),
        ),
      );
    return _hex(engine.process(Uint8List.fromList(data)));
  }

  /// 无填充 RSA（裸模幂），返回定长大写 hex（与 JS `cryptoRSAEncrypt` 一致）。
  ///
  /// `user_detail` / `login` 的 `p`/`pk` 参数使用这种方式（非 PKCS#1）。
  /// 明文必须放在缓冲区开头、尾部补零到密钥长度（对应 JS 参考实现
  /// `crypto.js` 的 `padded.set(buffer)`）；补零方向不可颠倒，否则服务端
  /// 解出的明文错位，登录 / 资料接口会返回 error_code 20006。
  static String rsaEncryptRaw(List<int> data, pc.RSAPublicKey publicKey) {
    final keyLength = (publicKey.modulus!.bitLength + 7) ~/ 8;
    if (data.length > keyLength) {
      throw ArgumentError('数据长度超过 RSA 密钥长度');
    }
    // 明文放缓冲区开头、尾部补零到密钥长度（对应 JS 参考实现
    // crypto.js 的 padded.set(buffer)），再整体当作大整数做模幂。
    // ⚠️ 补零方向必须与参考实现一致，否则服务端解出的明文错位，
    // 登录 / 资料接口会返回 error_code 20006。
    final padded = Uint8List(keyLength);
    padded.setRange(0, data.length, data);
    final value = _bytesToBigInt(padded);
    final encrypted = value.modPow(publicKey.exponent!, publicKey.modulus!);
    return encrypted
        .toRadixString(16)
        .padLeft(keyLength * 2, '0')
        .toUpperCase();
  }

  /// 从 PEM（SubjectPublicKeyInfo）解析 1024-bit RSA 公钥。
  static pc.RSAPublicKey parseRsaPublicKey(String pem) {
    final b64 = pem
        .replaceAll(RegExp(r'-----[A-Z ]+-----'), '')
        .replaceAll(RegExp(r'\s'), '');
    final der = base64.decode(b64);
    final reader = _DerReader(der);

    // SubjectPublicKeyInfo ::= SEQUENCE { AlgorithmIdentifier, BIT STRING }
    reader.expect(0x30);
    reader.readLength();
    reader.expect(0x30); // AlgorithmIdentifier
    final algLen = reader.readLength();
    reader.skip(algLen); // OID + NULL
    reader.expect(0x03); // BIT STRING
    final bitLen = reader.readLength();
    reader.skip(1); // unused-bits byte
    final inner = _DerReader(reader.take(bitLen - 1));

    // RSAPublicKey ::= SEQUENCE { modulus INTEGER, publicExponent INTEGER }
    inner.expect(0x30);
    inner.readLength();
    inner.expect(0x02);
    final modLen = inner.readLength();
    final modulus = inner.take(modLen);
    inner.expect(0x02);
    final expLen = inner.readLength();
    final exponent = inner.take(expLen);

    return pc.RSAPublicKey(
      _bytesToBigInt(modulus),
      _bytesToBigInt(exponent),
    );
  }

  /// 生成随机字符串（小写字母+数字），与 JS `randomString` 一致。
  static String randomString(int length, {String? alphabet}) {
    const defaultAlphabet =
        '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    final chars = alphabet ?? defaultAlphabet;
    final rnd = Random.secure();
    return List.generate(
      length,
      (_) => chars[rnd.nextInt(chars.length)],
    ).join();
  }

  // ===== KRC 歌词解码 =====

  /// KRC 内容的 16 字节循环 XOR 密钥。
  static const List<int> _krcXorKey = [
    64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105,
  ];

  /// 解码 KRC：base64 → 丢弃前 4 字节文件头 → 循环 XOR → zlib inflate。
  ///
  /// 对应 JS 参考实现的 `decodeLyrics`。若上游内容并非 KRC 加密格式
  /// （例如已是明文），解密会得到乱码，此时调用方应回退到其它候选内容。
  static String? decodeKrc(String base64Content) {
    try {
      final raw = base64.decode(base64Content);
      if (raw.length <= 4) return null;
      final body = Uint8List.fromList(raw.sublist(4));
      for (var i = 0; i < body.length; i++) {
        body[i] = body[i] ^ _krcXorKey[i % _krcXorKey.length];
      }
      final inflated = ZLibDecoder().convert(body);
      return utf8.decode(inflated, allowMalformed: true);
    } catch (_) {
      return null;
    }
  }

  // ===== 内部工具 =====

  static Uint8List _keyBytes(String s) {
    final b = utf8.encode(s);
    if (b.length == 16 || b.length == 24 || b.length == 32) {
      return Uint8List.fromList(b);
    }
    // 非常规长度：截断/补零到 16 字节
    final out = Uint8List(16);
    for (var i = 0; i < 16 && i < b.length; i++) {
      out[i] = b[i];
    }
    return out;
  }

  static Uint8List _ivBytes(String s) {
    final b = utf8.encode(s);
    final out = Uint8List(16);
    for (var i = 0; i < 16 && i < b.length; i++) {
      out[i] = b[i];
    }
    return out;
  }

  static Uint8List _pkcs7Pad(Uint8List data, int block) {
    final pad = block - (data.length % block);
    final out = Uint8List(data.length + pad);
    out.setRange(0, data.length, data);
    for (var i = data.length; i < out.length; i++) {
      out[i] = pad;
    }
    return out;
  }

  static Uint8List _pkcs7Unpad(Uint8List data) {
    if (data.isEmpty) return data;
    final pad = data.last;
    if (pad < 1 || pad > 16 || pad > data.length) return data;
    return Uint8List.sublistView(data, 0, data.length - pad);
  }

  static String _hex(List<int> bytes) =>
      bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();

  static Uint8List _fromHex(String hex) {
    final clean = hex.length.isOdd ? '0$hex' : hex;
    final out = Uint8List(clean.length ~/ 2);
    for (var i = 0; i < out.length; i++) {
      out[i] = int.parse(clean.substring(i * 2, i * 2 + 2), radix: 16);
    }
    return out;
  }

  static BigInt _bytesToBigInt(Uint8List bytes) {
    var result = BigInt.zero;
    for (final b in bytes) {
      result = (result << 8) | BigInt.from(b);
    }
    return result;
  }

  /// hex 字符串按大整数转十进制字符串（`calculateMid`）。
  static String _hexToDecimal(String hex) {
    var value = BigInt.zero;
    for (final ch in hex.split('')) {
      value = value * BigInt.from(16) + BigInt.parse(ch, radix: 16);
    }
    return value.toString();
  }

  static pc.FortunaRandom _fortuna() {
    final random = pc.FortunaRandom();
    final seed = Uint8List(32);
    final rnd = Random.secure();
    for (var i = 0; i < seed.length; i++) {
      seed[i] = rnd.nextInt(256);
    }
    random.seed(pc.KeyParameter(seed));
    return random;
  }
}

/// 极简 DER 读取器（仅覆盖 SPKI 结构）。
class _DerReader {
  _DerReader(this._data);

  final Uint8List _data;
  int _offset = 0;

  void expect(int tag) {
    if (_data[_offset] != tag) {
      throw FormatException(
        'DER: expected 0x${tag.toRadixString(16)}, '
        'got 0x${_data[_offset].toRadixString(16)}',
      );
    }
    _offset++;
  }

  int readLength() {
    var len = _data[_offset++];
    if (len & 0x80 == 0) return len;
    final count = len & 0x7f;
    var value = 0;
    for (var i = 0; i < count; i++) {
      value = (value << 8) | _data[_offset++];
    }
    return value;
  }

  Uint8List take(int length) {
    final out = Uint8List.sublistView(_data, _offset, _offset + length);
    _offset += length;
    return out;
  }

  void skip(int length) => _offset += length;
}
