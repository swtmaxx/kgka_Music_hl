import 'package:flutter/material.dart';
import '../design_tokens.dart';
import 'package:flutter/services.dart';

import '../../controllers/player_controller.dart';

class AudioInterruptionSettingsPage extends StatefulWidget {
  const AudioInterruptionSettingsPage({super.key, required this.player});

  final PlayerController player;

  @override
  State<AudioInterruptionSettingsPage> createState() =>
      _AudioInterruptionSettingsPageState();
}

class _AudioInterruptionSettingsPageState
    extends State<AudioInterruptionSettingsPage> {
  // 本页只关心这两个开关。原实现直接 AnimatedBuilder(player)，会随播放进度
  // 每次 tick（~200ms）整页重建——在手表上纯属浪费。改为本地快照 + 变化才 setState。
  late bool _interruptionEnabled;
  late bool _autoResume;

  @override
  void initState() {
    super.initState();
    _interruptionEnabled = widget.player.audioInterruptionEnabled;
    _autoResume = widget.player.autoResumeAfterInterruption;
    widget.player.addListener(_onPlayerChanged);
  }

  @override
  void dispose() {
    widget.player.removeListener(_onPlayerChanged);
    super.dispose();
  }

  void _onPlayerChanged() {
    final interruption = widget.player.audioInterruptionEnabled;
    final resume = widget.player.autoResumeAfterInterruption;
    if (interruption == _interruptionEnabled && resume == _autoResume) return;
    setState(() {
      _interruptionEnabled = interruption;
      _autoResume = resume;
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final player = widget.player;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: colorScheme.brightness == Brightness.dark
            ? Brightness.light
            : Brightness.dark,
        statusBarBrightness: colorScheme.brightness == Brightness.dark
            ? Brightness.dark
            : Brightness.light,
      ),
      child: Scaffold(
        appBar: AppBar(title: const Text('后台打断机制')),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(10, 12, 16, 32),
          children: [
            // Info banner
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: colorScheme.primaryContainer.withValues(alpha: .35),
                borderRadius: BorderRadius.circular(AppRadius.lg),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.info_outline_rounded,
                    size: 20,
                    color: colorScheme.primary,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      '当你在听歌时，其他 App（如短视频、游戏）可能会抢占音频焦点导致音乐暂停。'
                      '你可以在下方调整打断行为。',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                        height: 1.5,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
            // Settings card
            DecoratedBox(
              decoration: BoxDecoration(
                color: colorScheme.surfaceContainer,
                borderRadius: BorderRadius.circular(AppRadius.lg),
              ),
              child: Column(
                children: [
                  SwitchListTile(
                    value: !_interruptionEnabled,
                    onChanged: (value) =>
                        player.setAudioInterruptionEnabled(!value),
                    secondary: Icon(
                      Icons.block_rounded,
                      color: colorScheme.primary,
                    ),
                    title: const Text('阻止后台打断'),
                    subtitle: const Text('其他 App 播放音频时不会暂停当前音乐'),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 14),
                  ),
                  Divider(
                    height: 1,
                    indent: 58,
                    color: colorScheme.outlineVariant.withValues(alpha: .4),
                  ),
                  SwitchListTile(
                    value: _autoResume,
                    onChanged: player.setAutoResumeAfterInterruption,
                    secondary: Icon(
                      Icons.play_circle_outline_rounded,
                      color: colorScheme.primary,
                    ),
                    title: const Text('自动恢复播放'),
                    subtitle: const Text('被打断后自动继续播放'),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 14),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
            // Compatibility notice
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: colorScheme.tertiaryContainer.withValues(alpha: .3),
                borderRadius: BorderRadius.circular(AppRadius.lg),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.phonelink_setup_rounded,
                    size: 20,
                    color: colorScheme.onSurfaceVariant,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      '兼容性提示：vivo / iQOO 等 OriginOS 系统对安卓音频框架做了深度定制，'
                      '本功能在这些设备上可能无法正常生效。目前暂无适配方案，'
                      '如遇到问题建议在系统设置中关闭相关后台音频优化。',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                        height: 1.5,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
