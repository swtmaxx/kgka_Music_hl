import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'design_tokens.dart';

class AppTheme {
  static const blue = Color(0xFF1478FF);
  static const musicRed = Color(0xFFFF2D55);

  static ThemeData light({Color? seedColor, bool transparentBackground = false}) {
    return _theme(Brightness.light,
        seedColor: seedColor ?? blue,
        transparentBackground: transparentBackground);
  }

  static ThemeData dark({Color? seedColor, bool transparentBackground = false}) {
    return _theme(Brightness.dark,
        seedColor: seedColor ?? blue,
        transparentBackground: transparentBackground);
  }

  static ThemeData _theme(
    Brightness brightness, {
    Color seedColor = blue,
    bool transparentBackground = false,
  }) {
    final isDark = brightness == Brightness.dark;
    final scheme = ColorScheme.fromSeed(seedColor: seedColor, brightness: brightness)
        .copyWith(
          primary: isDark ? _lighten(seedColor, 0.18) : seedColor,
          secondary: musicRed,
          tertiary: const Color(0xFF24C768),
          surface: isDark ? const Color(0xFF0B0C10) : Colors.white,
          surfaceContainerLowest: isDark
              ? const Color(0xFF06070A)
              : Colors.white,
          surfaceContainer: isDark
              ? const Color(0xFF151820)
              : const Color(0xFFF4F7FB),
          surfaceContainerHighest: isDark
              ? const Color(0xFF202430)
              : const Color(0xFFEFF5FF),
          onSurface: isDark ? Colors.white : const Color(0xFF080B12),
          onSurfaceVariant: isDark
              ? const Color(0xFFB0B8C6)
              : const Color(0xFF6F7785),
          outline: isDark ? const Color(0xFF4D5668) : const Color(0xFFD2DAE7),
          outlineVariant: isDark
              ? const Color(0xFF303747)
              : const Color(0xFFE7EDF7),
        );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      // 手表专用：全局紧凑密度，减小控件高度与内边距。
      visualDensity: VisualDensity.compact,
      scaffoldBackgroundColor: transparentBackground
          ? Colors.transparent
          : (isDark ? const Color(0xFF06070A) : Colors.white),
      textTheme: const TextTheme(
        labelSmall: TextStyle(fontSize: 10, height: 1.2),
        bodySmall: TextStyle(fontSize: 11, height: 1.25),
        labelMedium: TextStyle(fontSize: 11, height: 1.25),
        bodyMedium: TextStyle(fontSize: 13, height: 1.3),
        labelLarge: TextStyle(fontSize: 13, height: 1.3),
        bodyLarge: TextStyle(fontSize: 14, height: 1.3),
        titleSmall: TextStyle(fontSize: 14, height: 1.3, fontWeight: FontWeight.w600),
        titleMedium: TextStyle(fontSize: 15, height: 1.3, fontWeight: FontWeight.w700),
        titleLarge: TextStyle(fontSize: 16, height: 1.3, fontWeight: FontWeight.w700),
        headlineSmall: TextStyle(fontSize: 16, height: 1.3, fontWeight: FontWeight.w700),
        headlineMedium: TextStyle(fontSize: 18, height: 1.3, fontWeight: FontWeight.w800),
        displaySmall: TextStyle(fontSize: 18, height: 1.3, fontWeight: FontWeight.w800),
      ),
      iconTheme: const IconThemeData(size: 18),
      listTileTheme: const ListTileThemeData(
        dense: true,
        minVerticalPadding: 4,
        contentPadding: EdgeInsets.symmetric(horizontal: 8),
      ),
      dividerTheme: const DividerThemeData(thickness: 0.6, space: 1),
      fontFamilyFallback: const [
        'SF Pro Display',
        'SF Pro Text',
        'Roboto',
        'Arial',
      ],
      appBarTheme: AppBarTheme(
        centerTitle: false,
        elevation: 0,
        toolbarHeight: 40,
        titleTextStyle: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w700,
          color: scheme.onSurface,
        ),
        backgroundColor: transparentBackground
            ? Colors.transparent
            : Colors.transparent,
        surfaceTintColor: Colors.transparent,
        foregroundColor: scheme.onSurface,
        // AppBar 会创建自己的 AnnotatedRegion 并覆盖页面级状态栏设置；
        // backgroundColor 为 transparent 时 Flutter 会推断为深色背景而强制白字，
        // 导致浅色页面状态栏文字（白字）不可见。必须显式指定。
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          // iOS：描述状态栏【背景】明暗 → 浅色背景用 Brightness.light（黑字）
          statusBarBrightness: isDark ? Brightness.dark : Brightness.light,
          // Android：描述【前景】图标/文字明暗（与上者相反）
          statusBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
          systemNavigationBarColor: scheme.surface,
          systemNavigationBarIconBrightness:
              isDark ? Brightness.light : Brightness.dark,
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          highlightColor: scheme.primary.withValues(alpha: .08),
        ),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        margin: EdgeInsets.zero,
        color: scheme.surface,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.lg)),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(44, 34),
          textStyle: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800),
          shape: const StadiumBorder(),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark ? const Color(0xFF171A22) : Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
          borderSide: BorderSide(
            color: scheme.outlineVariant.withValues(alpha: .72),
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
          borderSide: BorderSide(color: scheme.primary, width: 1.3),
        ),
      ),
    );
  }

  /// 将颜色向白色方向提亮。
  static Color _lighten(Color color, [double amount = 0.2]) {
    return Color.lerp(color, Colors.white, amount) ?? color;
  }
}
