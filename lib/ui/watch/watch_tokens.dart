/// 手表专用设计 Token。
///
/// 目标机：S100 240×284（DPR 1.0）、Android 8.1、1GB RAM。
/// 所有数值按小屏压缩，触控目标不小于 [WatchSize.minTouch]。
/// 新代码请优先使用这里的常量，避免散落魔法数值。
library;

abstract final class WatchSpacing {
  static const double xxs = 2;
  static const double xs = 4;
  static const double sm = 6;
  static const double md = 8;
  static const double lg = 12;
  static const double xl = 16;
}

abstract final class WatchRadius {
  static const double sm = 6;
  static const double md = 10;
  static const double lg = 14;
  static const double pill = 999;
}

abstract final class WatchSize {
  /// 最小触控目标（手表上再小就点不准了）。
  static const double minTouch = 40;
  static const double appBar = 40;
  static const double icon = 18;
  static const double iconSmall = 14;
  static const double listRow = 44;
  static const double coverSmall = 36;
  static const double coverMedium = 64;
  static const double coverLarge = 108;
  static const double progressTrack = 3;
}

abstract final class WatchText {
  static const double caption = 9;
  static const double small = 10;
  static const double body = 11;
  static const double title = 12;
  static const double heading = 14;
  static const double display = 16;
}
