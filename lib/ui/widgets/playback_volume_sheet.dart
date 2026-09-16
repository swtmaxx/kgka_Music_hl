import 'package:flutter/material.dart';

import '../../controllers/player_controller.dart';

/// 弹出音量调节面板。
///
/// 手表的实体音量键往往缺失或不便操作，这个面板提供屏内音量调节，
/// 并即时作用于正在播放的音频。
Future<double?> showPlaybackVolumeSheet({
  required BuildContext context,
  required PlayerController player,
}) {
  return showModalBottomSheet<double>(
    context: context,
    showDragHandle: true,
    backgroundColor: Theme.of(context).colorScheme.surface,
    builder: (sheetContext) {
      return _PlaybackVolumeSheet(player: player);
    },
  );
}

class _PlaybackVolumeSheet extends StatefulWidget {
  const _PlaybackVolumeSheet({required this.player});

  final PlayerController player;

  @override
  State<_PlaybackVolumeSheet> createState() => _PlaybackVolumeSheetState();
}

class _PlaybackVolumeSheetState extends State<_PlaybackVolumeSheet> {
  static const _min = 0.0;
  static const _max = 1.0;
  static const _steps = [0.0, 0.25, 0.5, 0.75, 1.0];

  late double _volume;

  @override
  void initState() {
    super.initState();
    _volume = widget.player.playbackVolume;
  }

  double _snapToNearest(double value) {
    var closest = _steps.first;
    var minDist = (value - closest).abs();
    for (final step in _steps.skip(1)) {
      final dist = (value - step).abs();
      if (dist < minDist) {
        minDist = dist;
        closest = step;
      }
    }
    // 仅当非常接近时吸附到整档，否则允许自由滑动（保留 1% 精度）。
    if (minDist < 0.02) return closest;
    return (value * 100).roundToDouble() / 100;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '音量',
              style: Theme.of(
                context,
              ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 4),
            Text(
              '调整播放音量',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 20),
            Center(
              child: Text(
                '${(_volume * 100).round()}%',
                style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.w900,
                  color: _volume == 1.0
                      ? colorScheme.onSurface
                      : colorScheme.primary,
                ),
              ),
            ),
            const SizedBox(height: 8),
            SliderTheme(
              data: SliderTheme.of(context).copyWith(
                trackHeight: 6,
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10),
                overlayShape: const RoundSliderOverlayShape(overlayRadius: 18),
                activeTrackColor: colorScheme.primary,
                inactiveTrackColor: colorScheme.surfaceContainerHighest,
                thumbColor: colorScheme.primary,
              ),
              child: Slider(
                value: _volume.clamp(_min, _max),
                min: _min,
                max: _max,
                onChanged: (value) {
                  setState(() => _volume = _snapToNearest(value));
                },
                onChangeEnd: (value) {
                  final snapped = _snapToNearest(value);
                  widget.player.setPlaybackVolume(snapped);
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('0%', style: _stepLabelStyle(context, _volume == 0.0)),
                  Text('50%', style: _stepLabelStyle(context, _volume == 0.5)),
                  Text('100%', style: _stepLabelStyle(context, _volume == 1.0)),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Center(
              child: TextButton.icon(
                onPressed: _volume == 1.0
                    ? null
                    : () {
                        setState(() => _volume = 1.0);
                        widget.player.setPlaybackVolume(1.0);
                      },
                icon: const Icon(Icons.restart_alt_rounded, size: 18),
                label: const Text('恢复默认'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  TextStyle? _stepLabelStyle(BuildContext context, bool active) {
    final colorScheme = Theme.of(context).colorScheme;
    return Theme.of(context).textTheme.bodySmall?.copyWith(
      color: active ? colorScheme.primary : colorScheme.onSurfaceVariant,
      fontWeight: active ? FontWeight.w800 : FontWeight.w500,
    );
  }
}
