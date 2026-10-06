import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../controllers/auth_controller.dart';
import '../../controllers/download_controller.dart';
import '../../controllers/local_music_controller.dart';
import '../../controllers/player_controller.dart';
import '../../controllers/theme_controller.dart';
import '../../services/cache_service.dart';
import '../../services/music_api.dart';
import '../pages/home_page.dart';
import '../pages/library_page.dart';
import '../pages/player_page.dart';
import '../pages/search_page.dart';
import '../pages/settings_page.dart';
import 'watch_feature_menu.dart';
import 'watch_tokens.dart';
import 'watch_widgets.dart';

/// 手表专用外壳：播放器优先 + 横向滑动切换主页面。
///
/// 主页面顺序：播放器(0) → 首页(1) → 我的(2) → 搜索(3) → 设置(4)。
/// 其余功能通过右下角「全部功能」网格进入。
class WatchShell extends StatefulWidget {
  const WatchShell({
    super.key,
    required this.api,
    required this.auth,
    required this.player,
    required this.cache,
    required this.downloads,
    required this.theme,
    required this.localMusic,
  });

  final MusicApi api;
  final AuthController auth;
  final PlayerController player;
  final CacheService cache;
  final DownloadController downloads;
  final ThemeController theme;
  final LocalMusicController localMusic;

  @override
  State<WatchShell> createState() => _WatchShellState();
}

class _WatchShellState extends State<WatchShell> {
  static const _pageCount = 5;

  final _pageController = PageController();
  final _navigatorKey = GlobalKey<NavigatorState>();
  int _index = 0;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _goTo(int index) {
    if (index < 0 || index >= _pageCount) return;
    _pageController.animateToPage(
      index,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
    );
  }

  void _openFeatureMenu() {
    final nav = Navigator.of(context);
    nav.push(
      MaterialPageRoute<void>(
        builder: (_) => WatchFeatureMenu(
          api: widget.api,
          auth: widget.auth,
          player: widget.player,
          theme: widget.theme,
          cache: widget.cache,
          downloads: widget.downloads,
          localMusic: widget.localMusic,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        final nav = _navigatorKey.currentState;
        if (nav != null && nav.canPop()) {
          nav.pop();
        } else if (_index != 0) {
          _goTo(0);
        } else {
          SystemNavigator.pop();
        }
      },
      child: Navigator(
        key: _navigatorKey,
        onGenerateRoute: (settings) {
          return MaterialPageRoute<void>(
            settings: settings,
            builder: (navContext) => _buildShell(navContext),
          );
        },
      ),
    );
  }

  Widget _buildShell(BuildContext context) {
    final pages = <Widget>[
      PlayerPage(player: widget.player, auth: widget.auth),
      HomePage(
        api: widget.api,
        auth: widget.auth,
        player: widget.player,
        cache: widget.cache,
        theme: widget.theme,
        downloads: widget.downloads,
        localMusic: widget.localMusic,
      ),
      LibraryPage(
        api: widget.api,
        auth: widget.auth,
        player: widget.player,
        downloads: widget.downloads,
        theme: widget.theme,
        localMusic: widget.localMusic,
      ),
      SearchPage(
        api: widget.api,
        auth: widget.auth,
        player: widget.player,
      ),
      SettingsPage(
        api: widget.api,
        auth: widget.auth,
        player: widget.player,
        theme: widget.theme,
        localMusic: widget.localMusic,
        cache: widget.cache,
        downloads: widget.downloads,
      ),
    ];

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: WatchBackground(
        child: SafeArea(
          bottom: false,
          child: Stack(
            children: [
              PageView(
                controller: _pageController,
                onPageChanged: (i) => setState(() => _index = i),
                children: pages,
              ),
              // 底部：页码圆点 + 「全部功能」入口
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: SafeArea(
                  top: false,
                  child: SizedBox(
                    height: 30,
                    child: Row(
                      children: [
                        const SizedBox(width: WatchSize.minTouch),
                        Expanded(
                          child: Center(
                            child: _PageDots(count: _pageCount, index: _index),
                          ),
                        ),
                        SizedBox(
                          width: WatchSize.minTouch,
                          height: WatchSize.minTouch,
                          child: IconButton(
                            padding: EdgeInsets.zero,
                            iconSize: WatchSize.icon,
                            tooltip: '全部功能',
                            onPressed: _openFeatureMenu,
                            icon: const Icon(Icons.apps_rounded),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PageDots extends StatelessWidget {
  const _PageDots({required this.count, required this.index});

  final int count;
  final int index;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: List.generate(count, (i) {
        final active = i == index;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          margin: const EdgeInsets.symmetric(horizontal: 2),
          width: active ? 12 : 5,
          height: 5,
          decoration: BoxDecoration(
            color: active
                ? scheme.primary
                : scheme.onSurfaceVariant.withValues(alpha: 0.35),
            borderRadius: BorderRadius.circular(WatchRadius.pill),
          ),
        );
      }),
    );
  }
}
