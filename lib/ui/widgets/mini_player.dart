import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../controllers/auth_controller.dart';
import '../../controllers/player_controller.dart';
import '../pages/player_page.dart';
import '../watch/watch_tokens.dart';
import 'artwork.dart';

/// 手表扁平迷你播放器（悬浮圆形唱片）。
///
/// 替代原液态玻璃版本：去掉 BackdropFilter，改用纯色圆盘 + 环形进度，
/// 在 1GB RAM 设备上避免离屏模糊合成。
class MiniPlayer extends StatefulWidget {
  const MiniPlayer({
    super.key,
    required this.player,
    required this.auth,
    this.onOpenPlayer,
  });

  final PlayerController player;
  final AuthController auth;
  final VoidCallback? onOpenPlayer;

  @override
  State<MiniPlayer> createState() => _MiniPlayerState();
}

class _MiniPlayerState extends State<MiniPlayer>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _rotationController;
  bool _appInBackground = false;

  @override
  void initState() {
    super.initState();
    _rotationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 24),
    );
    WidgetsBinding.instance.addObserver(this);
    _syncRotation();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _appInBackground = state != AppLifecycleState.resumed;
    _syncRotation();
  }

  @override
  void didUpdateWidget(covariant MiniPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncRotation();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _rotationController.dispose();
    super.dispose();
  }

  void _syncRotation() {
    if (widget.player.isPlaying && !_appInBackground) {
      if (!_rotationController.isAnimating) {
        _rotationController.repeat();
      }
    } else if (_rotationController.isAnimating) {
      _rotationController.stop(canceled: false);
    }
  }

  void _openPlayerByRoute(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PlayerPage(player: widget.player, auth: widget.auth),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    final isLandscape = size.width > size.height;
    // 手表专用版为竖屏，这里保留判断仅作防御。
    if (isLandscape && false) {
      return const SizedBox.shrink();
    }

    final scheme = Theme.of(context).colorScheme;

    return RepaintBoundary(
      child: AnimatedBuilder(
        animation: widget.player,
        builder: (context, _) {
          _syncRotation();
          final song = widget.player.currentSong;
          if (song == null) return const SizedBox.shrink();

          final durationMs = widget.player.duration.inMilliseconds;
          final positionMs = widget.player.position.inMilliseconds;
          final progress =
              (durationMs > 0 ? (positionMs / durationMs) : 0.0).clamp(0.0, 1.0);

          return Align(
            alignment: Alignment.bottomCenter,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: widget.onOpenPlayer ??
                  () => _openPlayerByRoute(context),
              onLongPress: () {
                HapticFeedback.lightImpact();
                widget.player.togglePlay();
              },
              child: Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: scheme.surfaceContainerHigh,
                  border: Border.all(
                    color: scheme.outlineVariant.withValues(alpha: 0.6),
                  ),
                ),
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    SizedBox.square(
                      dimension: 48,
                      child: CircularProgressIndicator(
                        value: progress,
                        strokeWidth: 2,
                        strokeCap: StrokeCap.round,
                        valueColor: AlwaysStoppedAnimation<Color>(scheme.primary),
                        backgroundColor: Colors.transparent,
                      ),
                    ),
                    ClipOval(
                      child: RotationTransition(
                        turns: _rotationController,
                        child: Artwork(
                          url: song.coverUrl,
                          size: 36,
                          borderRadius: 36,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
