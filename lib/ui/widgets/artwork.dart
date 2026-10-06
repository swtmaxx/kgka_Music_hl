import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class Artwork extends StatelessWidget {
  const Artwork({
    super.key,
    this.url,
    required this.size,
    this.borderRadius = 8,
    this.icon = Icons.music_note_rounded,
    this.cacheSize,
  });

  final String? url;
  final double size;
  final double borderRadius;
  final IconData icon;

  /// 解码缓存的目标边长（逻辑像素）。
  //
  /// 用于限制封面图的解码分辨率——即使显示尺寸很小，若不限制
  /// 解码尺寸，1024x1024 的原图会全分辨率解码进内存，在低内存
  /// 手表上极易触发 OOM。
  //
  /// 传 null 时自动按显示尺寸推导：`size * 3`（3 倍像素密度余量），
  /// 并 clamp 到 [64, 480]。这样列表缩略图（44/48/50dp）只会解码
  /// 约 132-150px，不会把大图全量驻留内存。
  final double? cacheSize;

  double get _effectiveCacheSize {
    if (cacheSize != null) return cacheSize!;
    if (!size.isFinite) return 480;
    return (size * 3).clamp(64.0, 480.0).toDouble();
  }

  @override
  Widget build(BuildContext context) {
    final imageUrl = url;
    final child = imageUrl == null
        ? _Fallback(icon: icon)
        : imageUrl.startsWith('content://')
            ? _ContentUriImage(
                uri: imageUrl,
                size: size,
                borderRadius: borderRadius,
                icon: icon,
              )
            : Image.network(
                imageUrl,
                fit: BoxFit.cover,
                cacheWidth: _effectiveCacheSize.round(),
                cacheHeight: _effectiveCacheSize.round(),
                errorBuilder: (context, error, stackTrace) =>
                    _Fallback(icon: icon),
                loadingBuilder: (context, child, progress) {
                  if (progress == null) {
                    return child;
                  }
                  return _ShimmerBox(
                    size: size,
                    borderRadius: borderRadius,
                  );
                },
              );

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: size.isFinite
          ? SizedBox.square(dimension: size, child: child)
          : SizedBox.expand(child: child),
    );
  }
}

/// 加载 content:// URI 的图片（用于本地音乐专辑封面）。
class _ContentUriImage extends StatefulWidget {
  const _ContentUriImage({
    required this.uri,
    required this.size,
    required this.borderRadius,
    required this.icon,
  });

  final String uri;
  final double size;
  final double borderRadius;
  final IconData icon;

  @override
  State<_ContentUriImage> createState() => _ContentUriImageState();
}

class _ContentUriImageState extends State<_ContentUriImage> {
  static const _channel = MethodChannel('kgka_music_hl/local_music');
  Uint8List? _bytes;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadImage();
  }

  @override
  void didUpdateWidget(covariant _ContentUriImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 列表刷新/重排时同一位置会复用 State，uri 变化需要重新加载封面，
    // 否则会显示上一次的旧封面（封面错位）。
    if (oldWidget.uri != widget.uri) {
      _bytes = null;
      _loading = true;
      _loadImage();
    }
  }

  Future<void> _loadImage() async {
    // 记录发起加载时的 uri，用于丢弃过期结果，避免快速刷新时的竞态。
    final uri = widget.uri;
    try {
      // 从 content URI 中提取 albumId
      final albumId = int.tryParse(uri.split('/').last);
      if (albumId == null || albumId <= 0) {
        if (mounted) setState(() => _loading = false);
        return;
      }
      final bytes = await _channel.invokeMethod<Uint8List>(
        'getAlbumArt',
        {'albumId': albumId},
      );
      if (!mounted || widget.uri != uri) return;
      setState(() {
        _bytes = bytes;
        _loading = false;
      });
    } catch (e) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return _ShimmerBox(size: widget.size, borderRadius: widget.borderRadius);
    }
    if (_bytes == null) {
      return _Fallback(icon: widget.icon);
    }
    // 本地专辑封面同样限制解码分辨率，避免大图全分辨率驻留内存。
    final cache = widget.size.isFinite
        ? widget.size.clamp(64.0, 480.0).toDouble()
        : 480.0;
    return Image.memory(
      _bytes!,
      fit: BoxFit.cover,
      cacheWidth: cache.round(),
      cacheHeight: cache.round(),
    );
  }
}

class _Fallback extends StatelessWidget {
  const _Fallback({required this.icon});

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            colorScheme.primary.withValues(alpha: .88),
            const Color(0xFF70D6FF),
            colorScheme.secondary.withValues(alpha: .72),
          ],
        ),
      ),
      child: Icon(icon, color: Colors.white, size: 28),
    );
  }
}

/// 图片加载时的占位块。
///
/// 手表专用：已移除 shimmer 动画（持续 ticker + 渐变重绘），
/// 改为静态色块，图片加载期间不再产生任何 GPU 动画开销。
class _ShimmerBox extends StatelessWidget {
  const _ShimmerBox({required this.size, required this.borderRadius});

  final double size;
  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final baseColor = isDark
        ? colorScheme.surfaceContainerHighest
        : colorScheme.surfaceContainer;
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: SizedBox(
        width: size.isFinite ? size : null,
        height: size.isFinite ? size : null,
        child: ColoredBox(color: baseColor),
      ),
    );
  }
}
