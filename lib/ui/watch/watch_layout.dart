import 'package:flutter/material.dart';

import 'watch_tokens.dart';

/// 手表布局工具。
///
/// 本项目已改为手表专用：所有设备都按手表密度渲染，宽屏仅做轻微放宽。
/// 设计基准为 S100 的 240×284 逻辑像素（DPR 1.0）。
class WatchLayout {
  const WatchLayout._();

  /// 设计基准宽度（S100 逻辑宽）。
  static const double designWidth = 240;

  /// 设计基准高度（S100 逻辑高）。
  static const double designHeight = 284;

  /// 超过该宽度才视为"宽表"，可放宽间距与网格列数。
  static const double wideBreakpoint = 300;

  /// 页面内容统一内边距。
  static const double contentPadding = WatchSpacing.md;

  /// 当前是否为宽表（如方屏手表）。
  static bool isWide(BuildContext context) {
    return MediaQuery.sizeOf(context).width > wideBreakpoint;
  }

  /// 依据可用宽度计算网格列数（手表默认 2 列，宽表 3 列）。
  static int gridColumns(BuildContext context, {double minItemWidth = 96}) {
    final width = MediaQuery.sizeOf(context).width;
    final columns = (width / minItemWidth).floor();
    return columns.clamp(2, 3);
  }

  /// 列表底部安全留白，避免被迷你播放条遮挡。
  static double listBottomInset(BuildContext context, {bool hasMiniPlayer = true}) {
    final safe = MediaQuery.paddingOf(context).bottom;
    return safe + (hasMiniPlayer ? WatchSize.listRow + WatchSpacing.md : WatchSpacing.md);
  }
}
