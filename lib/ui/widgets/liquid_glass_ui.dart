import 'package:flutter/material.dart';

import '../watch/watch_tokens.dart';

/// 兼容层：原液态玻璃组件已全部替换为手表扁平实现。
///
/// 保留类名以避免大规模改动页面代码，但不再使用任何
/// `BackdropFilter` / `liquid_glass_easy`，在 1GB RAM 的手表上
/// 避免离屏模糊合成。新代码请直接使用 `lib/ui/watch/watch_widgets.dart`。

/// 扁平背景（原极光背景）。
class LiquidGlassBackground extends StatelessWidget {
  const LiquidGlassBackground({
    super.key,
    required this.child,
    this.showOrbs = true,
  });

  final Widget child;
  final bool showOrbs;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final base = isDark ? const Color(0xFF090B10) : const Color(0xFFF3F7FC);
    final tint = scheme.primary.withValues(alpha: isDark ? 0.14 : 0.07);
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color.alphaBlend(tint, base), base],
        ),
      ),
      child: child,
    );
  }
}

/// 扁平卡片（原液态玻璃卡片）。
class LiquidGlassCard extends StatelessWidget {
  const LiquidGlassCard({
    super.key,
    required this.child,
    this.borderRadius = WatchRadius.md,
    this.padding,
    this.margin,
    this.width,
    this.height,
    this.onTap,
    this.enableTouchFlex = true,
    this.backgroundColor,
    this.borderColor,
    this.blurSigma = 0.0,
  });

  final Widget child;
  final double borderRadius;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double? width;
  final double? height;
  final VoidCallback? onTap;
  final bool enableTouchFlex;
  final Color? backgroundColor;
  final Color? borderColor;
  final double blurSigma;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = backgroundColor ??
        (isDark ? scheme.surfaceContainerHigh : scheme.surfaceContainerLowest);
    final border = borderColor ??
        (isDark
            ? Colors.white.withValues(alpha: 0.06)
            : scheme.outlineVariant.withValues(alpha: 0.5));

    Widget body = Container(
      width: width,
      height: height,
      padding: padding ?? const EdgeInsets.all(WatchSpacing.md),
      child: child,
    );

    if (onTap != null) {
      body = Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(borderRadius),
          onTap: onTap,
          splashColor: scheme.primary.withValues(alpha: 0.12),
          highlightColor: scheme.primary.withValues(alpha: 0.06),
          child: body,
        ),
      );
    }

    return Container(
      margin: margin,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(borderRadius),
        border: Border.all(color: border, width: 1),
      ),
      clipBehavior: Clip.antiAlias,
      child: body,
    );
  }
}

/// 扁平胶囊（原液态玻璃胶囊）。
class LiquidGlassCapsule extends StatelessWidget {
  const LiquidGlassCapsule({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.symmetric(
      horizontal: WatchSpacing.md,
      vertical: WatchSpacing.xs,
    ),
    this.margin,
    this.onTap,
    this.isActive = false,
    this.activeColor,
    this.blurSigma = 0.0,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final VoidCallback? onTap;
  final bool isActive;
  final Color? activeColor;
  final double blurSigma;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final accent = activeColor ?? scheme.primary;
    final bg = isActive
        ? accent.withValues(alpha: 0.22)
        : scheme.surfaceContainerHigh;
    final border = isActive ? accent.withValues(alpha: 0.7) : Colors.transparent;

    Widget body = Padding(padding: padding, child: child);
    if (onTap != null) {
      body = Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(WatchRadius.pill),
          onTap: onTap,
          child: body,
        ),
      );
    }

    return Container(
      margin: margin,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(WatchRadius.pill),
        border: Border.all(color: border),
      ),
      clipBehavior: Clip.antiAlias,
      child: body,
    );
  }
}

/// 扁平列表行（原液态玻璃列表行）。
class LiquidGlassTile extends StatelessWidget {
  const LiquidGlassTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onTap,
    this.borderRadius = WatchRadius.md,
    this.padding = const EdgeInsets.symmetric(
      horizontal: WatchSpacing.md,
      vertical: WatchSpacing.sm,
    ),
  });

  final Widget title;
  final Widget? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onTap;
  final double borderRadius;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return LiquidGlassCard(
      borderRadius: borderRadius,
      padding: padding,
      onTap: onTap,
      child: Row(
        children: [
          if (leading != null) ...[
            leading!,
            const SizedBox(width: WatchSpacing.sm),
          ],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                title,
                if (subtitle != null) ...[
                  const SizedBox(height: 2),
                  subtitle!,
                ],
              ],
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: WatchSpacing.xs),
            trailing!,
          ],
        ],
      ),
    );
  }
}

/// 扁平底部面板（原液态玻璃底板）。
class LiquidGlassSheetContainer extends StatelessWidget {
  const LiquidGlassSheetContainer({
    super.key,
    required this.child,
    this.borderRadius = WatchRadius.lg,
    this.padding,
    this.constraints,
  });

  final Widget child;
  final double borderRadius;
  final EdgeInsetsGeometry? padding;
  final BoxConstraints? constraints;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      constraints: constraints,
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHigh,
        borderRadius: BorderRadius.vertical(top: Radius.circular(borderRadius)),
        border: Border(
          top: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.5)),
        ),
      ),
      child: Padding(
        padding: padding ?? const EdgeInsets.all(WatchSpacing.lg),
        child: child,
      ),
    );
  }
}
