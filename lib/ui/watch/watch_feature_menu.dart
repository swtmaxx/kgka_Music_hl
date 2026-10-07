import 'package:flutter/material.dart';

import '../../controllers/auth_controller.dart';
import '../../controllers/download_controller.dart';
import '../../controllers/local_music_controller.dart';
import '../../controllers/player_controller.dart';
import '../../controllers/theme_controller.dart';
import '../../services/cache_service.dart';
import '../../services/music_api.dart';
import '../pages/about_page.dart';
import '../pages/audio_interruption_settings_page.dart';
import '../pages/cloud_drive_page.dart';
import '../pages/downloaded_songs_page.dart';
import '../pages/local_songs_page.dart';
import '../pages/personalization_settings_page.dart';
import '../pages/playback_history_page.dart';
import '../pages/playback_stats_page.dart';
import '../pages/vip_info_page.dart';
import 'watch_layout.dart';
import 'watch_tokens.dart';
import 'watch_widgets.dart';

/// 手表「全部功能」网格入口。
///
/// 主页面只放最常用的 5 个（播放器/首页/我的/搜索/设置），其余功能
/// 全部收敛到这里，保证全功能可达又不挤占小屏。
class WatchFeatureMenu extends StatelessWidget {
  const WatchFeatureMenu({
    super.key,
    required this.api,
    required this.auth,
    required this.player,
    required this.theme,
    required this.cache,
    required this.downloads,
    required this.localMusic,
  });

  final MusicApi api;
  final AuthController auth;
  final PlayerController player;
  final ThemeController theme;
  final CacheService cache;
  final DownloadController downloads;
  final LocalMusicController localMusic;

  void _push(BuildContext context, Widget page) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => page),
    );
  }

  @override
  Widget build(BuildContext context) {
    final entries = <_MenuEntry>[
      _MenuEntry(Icons.workspace_premium_rounded, 'VIP 会员',
          (c) => _push(c, VipInfoPage(api: api, auth: auth))),
      _MenuEntry(Icons.bar_chart_rounded, '播放统计',
          (c) => _push(c, PlaybackStatsPage(player: player))),
      _MenuEntry(Icons.history_rounded, '播放历史',
          (c) => _push(c, PlaybackHistoryPage(api: api, auth: auth, player: player))),
      _MenuEntry(Icons.download_rounded, '下载管理',
          (c) => _push(c, DownloadedSongsPage(api: api, auth: auth, player: player, downloads: downloads))),
      _MenuEntry(Icons.cloud_rounded, '云盘',
          (c) => _push(c, CloudDrivePage(api: api, auth: auth, player: player))),
      _MenuEntry(Icons.folder_rounded, '本地歌曲',
          (c) => _push(c, LocalSongsPage(player: player, localMusic: localMusic))),
      _MenuEntry(Icons.palette_rounded, '个性化',
          (c) => _push(c, PersonalizationSettingsPage(themeController: theme))),
      _MenuEntry(Icons.headphones_rounded, '音频中断',
          (c) => _push(c, AudioInterruptionSettingsPage(player: player))),
      _MenuEntry(Icons.info_outline_rounded, '关于',
          (c) => _push(c, AboutPage(api: api))),
    ];

    return WatchScaffold(
      appBar: WatchAppBar(
        leading: IconButton(
          padding: EdgeInsets.zero,
          iconSize: WatchSize.icon,
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: const Text('全部功能'),
      ),
      body: GridView.builder(
        padding: const EdgeInsets.fromLTRB(
          WatchLayout.contentPadding,
          WatchSpacing.xs,
          WatchLayout.contentPadding,
          WatchSpacing.lg,
        ),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          mainAxisSpacing: WatchSpacing.sm,
          crossAxisSpacing: WatchSpacing.sm,
          childAspectRatio: 1.7,
        ),
        itemCount: entries.length,
        itemBuilder: (context, i) {
          final entry = entries[i];
          final scheme = Theme.of(context).colorScheme;
          return WatchCard(
            onTap: () => entry.onTap(context),
            padding: const EdgeInsets.all(WatchSpacing.sm),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(entry.icon, size: 22, color: scheme.primary),
                const SizedBox(height: WatchSpacing.xs),
                Text(
                  entry.label,
                  style: TextStyle(
                    fontSize: WatchText.small,
                    fontWeight: FontWeight.w600,
                    color: scheme.onSurface,
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _MenuEntry {
  const _MenuEntry(this.icon, this.label, this.onTap);

  final IconData icon;
  final String label;
  final void Function(BuildContext) onTap;
}
