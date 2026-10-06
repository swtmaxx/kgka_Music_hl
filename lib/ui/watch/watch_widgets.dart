import 'package:flutter/material.dart';

import 'watch_tokens.dart';

/// 手表扁平背景。
///
/// 用纯色 + 一层极轻的主题色渐变替代原来的 BackdropFilter 极光背景，
/// 在 1GB RAM / 弱 GPU 上避免离屏合成开销。
class WatchBackground extends StatelessWidget {
  const WatchBackground({super.key, required this.child});

  final Widget child;

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

/// 手表扁平卡片容器。
class WatchCard extends StatelessWidget {
  const WatchCard({
    super.key,
    required this.child,
    this.borderRadius = WatchRadius.md,
    this.padding,
    this.margin,
    this.width,
    this.height,
    this.onTap,
    this.color,
    this.borderColor,
  });

  final Widget child;
  final double borderRadius;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double? width;
  final double? height;
  final VoidCallback? onTap;
  final Color? color;
  final Color? borderColor;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = color ?? (isDark ? scheme.surfaceContainerHigh : scheme.surfaceContainerLowest);
    final border = borderColor ??
        (isDark ? Colors.white.withValues(alpha: 0.06) : scheme.outlineVariant.withValues(alpha: 0.5));

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

/// 手表列表行。
class WatchListTile extends StatelessWidget {
  const WatchListTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onTap,
    this.dense = false,
  });

  final Widget title;
  final Widget? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return InkWell(
      onTap: onTap,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: WatchSize.listRow),
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: WatchSpacing.md,
            vertical: dense ? WatchSpacing.xs : WatchSpacing.sm,
          ),
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
                    DefaultTextStyle.merge(
                      style: TextStyle(
                        fontSize: WatchText.body,
                        color: scheme.onSurface,
                        fontWeight: FontWeight.w500,
                      ),
                      child: title,
                    ),
                    if (subtitle != null) ...[
                      const SizedBox(height: 1),
                      DefaultTextStyle.merge(
                        style: TextStyle(
                          fontSize: WatchText.caption,
                          color: scheme.onSurfaceVariant,
                        ),
                        child: subtitle!,
                      ),
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
        ),
      ),
    );
  }
}

/// 分组标题。
class WatchSectionHeader extends StatelessWidget {
  const WatchSectionHeader(this.title, {super.key, this.trailing});

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        WatchSpacing.md,
        WatchSpacing.md,
        WatchSpacing.md,
        WatchSpacing.xs,
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                fontSize: WatchText.small,
                fontWeight: FontWeight.w700,
                color: scheme.onSurfaceVariant,
                letterSpacing: 0.3,
              ),
            ),
          ),
          if (trailing != null) trailing!,
        ],
      ),
    );
  }
}

/// 开关设置行。
class WatchSwitchTile extends StatelessWidget {
  const WatchSwitchTile({
    super.key,
    required this.title,
    required this.value,
    required this.onChanged,
    this.subtitle,
  });

  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    return WatchListTile(
      title: Text(title),
      subtitle: subtitle == null ? null : Text(subtitle!),
      onTap: onChanged == null ? null : () => onChanged!(!value),
      trailing: Transform.scale(
        scale: 0.75,
        child: Switch(
          value: value,
          onChanged: onChanged,
          materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
        ),
      ),
    );
  }
}

/// 胶囊按钮。
class WatchCapsule extends StatelessWidget {
  const WatchCapsule({
    super.key,
    required this.child,
    this.onTap,
    this.isActive = false,
    this.activeColor,
    this.padding = const EdgeInsets.symmetric(
      horizontal: WatchSpacing.md,
      vertical: WatchSpacing.xs,
    ),
    this.margin,
  });

  final Widget child;
  final VoidCallback? onTap;
  final bool isActive;
  final Color? activeColor;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;

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

/// 主操作按钮。
class WatchButton extends StatelessWidget {
  const WatchButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
    this.filled = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(fontSize: WatchText.body, fontWeight: FontWeight.w700);
    final child = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[
          Icon(icon, size: WatchSize.iconSmall),
          const SizedBox(width: WatchSpacing.xs),
        ],
        Text(label, style: style),
      ],
    );
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(WatchRadius.pill),
    );
    return SizedBox(
      height: WatchSize.minTouch,
      child: filled
          ? FilledButton(
              onPressed: onPressed,
              style: FilledButton.styleFrom(
                shape: shape,
                padding: const EdgeInsets.symmetric(horizontal: WatchSpacing.lg),
              ),
              child: child,
            )
          : OutlinedButton(
              onPressed: onPressed,
              style: OutlinedButton.styleFrom(
                shape: shape,
                padding: const EdgeInsets.symmetric(horizontal: WatchSpacing.lg),
              ),
              child: child,
            ),
    );
  }
}

/// 紧凑 AppBar。
class WatchAppBar extends StatelessWidget implements PreferredSizeWidget {
  const WatchAppBar({
    super.key,
    required this.title,
    this.leading,
    this.actions,
  });

  final Widget title;
  final Widget? leading;
  final List<Widget>? actions;

  @override
  Size get preferredSize => const Size.fromHeight(WatchSize.appBar);

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      height: WatchSize.appBar,
      child: Row(
        children: [
          if (leading != null) leading!,
          Expanded(
            child: DefaultTextStyle.merge(
              style: TextStyle(
                fontSize: WatchText.title,
                fontWeight: FontWeight.w700,
                color: scheme.onSurface,
              ),
              child: title,
            ),
          ),
          if (actions != null) ...actions!,
        ],
      ),
    );
  }
}

/// 页面脚手架：统一背景 + 紧凑内边距。
class WatchScaffold extends StatelessWidget {
  const WatchScaffold({
    super.key,
    this.appBar,
    required this.body,
    this.floatingActionButton,
    this.bottomBar,
  });

  final PreferredSizeWidget? appBar;
  final Widget body;
  final Widget? floatingActionButton;
  final Widget? bottomBar;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: appBar,
      floatingActionButton: floatingActionButton,
      body: WatchBackground(
        child: SafeArea(
          bottom: false,
          child: body,
        ),
      ),
      bottomNavigationBar: bottomBar,
    );
  }
}

/// 空状态占位。
class WatchEmptyState extends StatelessWidget {
  const WatchEmptyState({super.key, required this.message, this.icon});

  final String message;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(WatchSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 28, color: scheme.onSurfaceVariant),
              const SizedBox(height: WatchSpacing.sm),
            ],
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: WatchText.small, color: scheme.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }
}
