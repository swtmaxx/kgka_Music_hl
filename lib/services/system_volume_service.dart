import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class SystemVolumeService {
  static const _channel = MethodChannel('kgka_music_hl/volume');

  bool get isSupported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  Future<double?> getVolume() async {
    if (!isSupported) return null;

    try {
      final percent = await _channel.invokeMethod<num>('getVolume');
      if (percent == null) return null;
      return (percent.toDouble() / 100).clamp(0.0, 1.0).toDouble();
    } on PlatformException catch (error) {
      debugPrint('[KA Music][system-volume] Read failed: $error');
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  Future<double?> setVolume(double volume) async {
    if (!isSupported) return null;

    final targetPercent = (volume.clamp(0.0, 1.0) * 100).round();
    try {
      final actualPercent = await _channel.invokeMethod<num>(
        'setVolume',
        {'value': targetPercent},
      );
      return ((actualPercent ?? targetPercent).toDouble() / 100)
          .clamp(0.0, 1.0)
          .toDouble();
    } on PlatformException catch (error) {
      debugPrint('[KA Music][system-volume] Write failed: $error');
      return null;
    } on MissingPluginException {
      return null;
    }
  }
}
