import 'package:flutter/material.dart';

class NowPlayingBadge extends StatelessWidget {
  const NowPlayingBadge({
    super.key,
    required this.active,
    required this.playing,
    required this.color,
    this.size = 18,
  });

  final bool active;
  final bool playing;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    if (!active) {
      return SizedBox.square(dimension: size);
    }
    // 手表专用：静态均衡器图标，不做动画（省 GPU/电量）。
    return SizedBox.square(
      dimension: size,
      child: CustomPaint(
        painter: _NowPlayingPainter(progress: .42, color: color),
      ),
    );
  }
}

class _NowPlayingPainter extends CustomPainter {
  const _NowPlayingPainter({required this.progress, required this.color});

  final double progress;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    final barWidth = size.width / 5;
    final gap = barWidth / 2;
    final values = [
      .42 + .36 * progress,
      .72 - .28 * progress,
      .48 + .44 * (1 - (progress - .5).abs() * 2),
    ];

    for (var i = 0; i < values.length; i++) {
      final height = size.height * values[i].clamp(.28, .92);
      final left = i * (barWidth + gap) + gap / 2;
      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(left, size.height - height, barWidth, height),
        Radius.circular(barWidth),
      );
      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _NowPlayingPainter oldDelegate) {
    return oldDelegate.progress != progress || oldDelegate.color != color;
  }
}
