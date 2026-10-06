import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

import 'kugou_crypto.dart';
import 'kugou_util.dart';

/// 酷狗 API 设备身份（`device_config.js` / `device.rs` 的 Dart 移植）。
///
/// 设备标识必须**跨启动持久化**：`mid` 由 `guid` 派生，若每次冷启动都重新随机
/// `guid`，账号会被上游持续判定为「新设备」并反复注册。
class KugouDevice {
  KugouDevice._();

  static final KugouDevice instance = KugouDevice._();

  static const String _fileName = 'kugou_device_info.json';

  String? _dfid;
  String? _mid;
  String? _uuid;
  String? _guid;
  String? _serverDev;
  String? _mac;

  String? _dataDir;
  bool _loaded = false;

  String get dfid => _dfid ?? '-';
  String get mid => _mid ?? '-';
  String get uuid => _uuid ?? '-';
  String get guid => _guid ?? '-';
  String get serverDev => _serverDev ?? '-';
  String get mac => _mac ?? '02:00:00:00:00:00';

  /// 是否已完成设备注册（拿到有效 dfid）。
  bool get hasDfid {
    final value = _dfid;
    return value != null && value.isNotEmpty && value != '-';
  }

  Map<String, String> toMap() => {
        'dfid': dfid,
        'mid': mid,
        'uuid': uuid,
        'guid': guid,
        'serverDev': serverDev,
        'mac': mac,
      };

  /// 初始化设备标识：优先从磁盘恢复，否则生成新的并落盘。
  ///
  /// [dataDir] 为持久化目录；不传时使用应用文档目录。
  Future<void> ensureLoaded({String? dataDir}) async {
    if (_loaded) return;
    _loaded = true;

    try {
      _dataDir = dataDir ?? (await getApplicationDocumentsDirectory()).path;
    } catch (_) {
      _dataDir = null;
    }

    if (_dataDir != null && await _loadCached()) {
      _ensureDefaults();
      return;
    }

    _guid = KugouUtil.getGuid();
    _mid = KugouUtil.calculateMid(_guid!);
    _uuid = '-';
    _serverDev = KugouUtil.randomString(10);
    _mac = '02:00:00:00:00:00';
    await _persist();
  }

  /// 由 `/register/dev` 返回的 dfid 更新设备标识。
  Future<void> setDfid(String newDfid) async {
    _dfid = newDfid;
    final currentMid = _mid ?? '-';
    _uuid = KugouCrypto.md5Hex('$newDfid$currentMid');
    await _persist();
  }

  Future<void> setMid(String newMid) async {
    _mid = newMid;
    _uuid = KugouCrypto.md5Hex('${dfid}$newMid');
    await _persist();
  }

  /// 恢复历史 guid，并重算派生的 mid/uuid。
  Future<void> setGuid(String newGuid) async {
    _guid = newGuid;
    _mid = KugouUtil.calculateMid(newGuid);
    _uuid = KugouCrypto.md5Hex('${dfid}${_mid!}');
    await _persist();
  }

  void _ensureDefaults() {
    _guid ??= KugouUtil.getGuid();
    _mid ??= KugouUtil.calculateMid(_guid!);
    _uuid ??= '-';
    _serverDev ??= KugouUtil.randomString(10);
    _mac ??= '02:00:00:00:00:00';
  }

  Future<bool> _loadCached() async {
    final path = '$_dataDir/$_fileName';
    final file = File(path);
    if (!await file.exists()) return false;
    try {
      final data = jsonDecode(await file.readAsString());
      if (data is! Map) return false;
      final dfid = data['dfid']?.toString() ?? '';
      if (dfid.isEmpty) return false;
      _dfid = dfid;
      final mid = data['mid']?.toString();
      if (mid != null && mid.isNotEmpty) _mid = mid;
      final guid = data['guid']?.toString();
      if (guid != null && guid.isNotEmpty) {
        _guid = guid;
        _mid = KugouUtil.calculateMid(guid);
      }
      final serverDev = data['serverDev']?.toString();
      if (serverDev != null && serverDev.isNotEmpty) _serverDev = serverDev;
      final mac = data['mac']?.toString();
      if (mac != null && mac.isNotEmpty) _mac = mac;
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> _persist() async {
    final dir = _dataDir;
    if (dir == null) return;
    try {
      await Directory(dir).create(recursive: true);
      await File('$dir/$_fileName').writeAsString(
        const JsonEncoder.withIndent('  ').convert(toMap()),
      );
    } catch (_) {
      // 落盘失败不阻塞请求；下次启动会重新生成。
    }
  }

  /// 测试/重置用：清空内存状态。
  void reset() {
    _dfid = null;
    _mid = null;
    _uuid = null;
    _guid = null;
    _serverDev = null;
    _mac = null;
    _loaded = false;
  }
}
