# NekoPlayer v1.1.0 & v1.2.0 需求规格说明书

> 版本: v1.1.0 (P0需求) / v1.2.0 (P1需求)  
> 日期: 2026-04-08  
> 状态: 概要设计阶段

---

## 1. 版本规划

### 1.1 v1.1.0 目标
**主题**: 核心体验补齐  
**周期**: 4-6周  
**核心交付**: 歌词系统 + 本地音乐 + 播放历史 + 睡眠定时

### 1.2 v1.2.0 目标
**主题**: 体验深化 + 数据能力  
**周期**: 4-6周  
**核心交付**: 详情页 + 歌词动效 + 播放统计 + 歌单导入导出

---

## 2. P0 需求详细规格

### 2.1 歌词系统

#### 需求描述
在播放页面显示同步滚动的歌词，支持拖拽跳转。

#### 功能点
| 功能 | 描述 | 优先级 |
|------|------|--------|
| LRC解析 | 解析标准LRC格式歌词文件 | P0 |
| 歌词获取 | 从音源获取歌词，失败时尝试第三方 | P0 |
| 同步滚动 | 根据播放进度高亮当前歌词行 | P0 |
| 点击跳转 | 点击歌词行跳转到对应时间 | P0 |
| 无歌词处理 | 显示"暂无歌词"，提供搜索入口 | P1 |

#### 数据结构
```kotlin
// 歌词行
data class LyricLine(
    val timeMs: Long,      // 时间戳 (毫秒)
    val content: String,   // 歌词内容
    val translation: String? = null  // 翻译
)

// 歌词数据
data class Lyrics(
    val songId: String,
    val source: String,    // "bilibili" | "migu"
    val lines: List<LyricLine>,
    val hasTranslation: Boolean
)
```

#### UI/UX
- 位置: NowPlayingScreen 下半部分
- 样式: 当前行高亮，过往行变暗，未来行灰色
- 交互: 上下滑动浏览，点击跳转，松手后3秒恢复自动滚动

---

### 2.2 本地音乐扫描

#### 需求描述
扫描设备本地音频文件，支持添加到播放队列和歌单。

#### 功能点
| 功能 | 描述 | 优先级 |
|------|------|--------|
| 权限申请 | 存储权限 (Android) / 媒体库权限 (iOS) | P0 |
| 自动扫描 | 扫描常见音乐目录 | P0 |
| 手动添加 | 支持选择文件夹扫描 | P1 |
| 元数据读取 | 读取ID3标签 (标题/艺人/专辑/封面) | P0 |
| 添加到歌单 | 本地歌曲可添加到歌单 | P0 |
| 本地歌单 | 创建纯本地歌曲的歌单 | P1 |

#### 支持格式
- Android: MP3, AAC, FLAC, WAV, OGG
- iOS: MP3, AAC, ALAC, WAV

#### 数据结构
```kotlin
data class LocalSong(
    val id: String,        // 文件路径哈希或URI
    val uri: String,       // 文件URI
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val coverPath: String?, // 内嵌封面缓存路径
    val filePath: String,
    val addedAt: Long
)
```

#### UI/UX
- 新页面: LocalMusicScreen
- 列表显示: 封面 + 标题 + 艺人 + 时长
- 顶部: 扫描按钮 + 排序选项 (名称/时间/艺人)
- 长按: 添加到歌单 / 下一首播放

---

### 2.3 播放历史

#### 需求描述
自动记录播放过的歌曲，生成"最近播放"列表。

#### 功能点
| 功能 | 描述 | 优先级 |
|------|------|--------|
| 自动记录 | 歌曲播放超30%计入历史 | P0 |
| 历史列表 | 按时间倒序排列 | P0 |
| 去重 | 同一歌曲只保留最近一条 | P0 |
| 数量限制 | 保留最近500首 | P1 |
| 清空历史 | 支持一键清空 | P1 |

#### 数据结构
```kotlin
// 播放记录表
CREATE TABLE play_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    song_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    cover_url TEXT,
    source TEXT NOT NULL,  -- "bilibili" | "migu" | "local"
    played_at INTEGER NOT NULL,
    duration INTEGER,      -- 歌曲总时长
    played_duration INTEGER, -- 实际播放时长
    UNIQUE(song_id, source) ON CONFLICT REPLACE
);
```

#### UI/UX
- 虚拟歌单: "最近播放" (系统生成，不可删除)
- 入口: PlaylistListScreen 顶部
- 显示: 与普通歌单一致，但添加时间标签

---

### 2.4 睡眠定时

#### 需求描述
设置倒计时，到时自动暂停播放。

#### 功能点
| 功能 | 描述 | 优先级 |
|------|------|--------|
| 时间选择 | 15/30/45/60分钟或自定义 | P0 |
| 倒计时显示 | NowPlaying显示剩余时间 | P0 |
| 自动暂停 | 到时自动暂停 | P0 |
| 渐入渐出 | 最后30秒音量渐变 | P1 |
| 取消定时 | 可随时取消 | P0 |

#### UI/UX
- 入口: NowPlayingScreen底部 ⋮ 菜单
- 选择器: BottomSheet 时间选项
- 显示: 开启后在MiniPlayer显示⏲️图标

---

## 3. P1 需求详细规格

### 3.1 歌单/歌曲详情页

#### 需求描述
为歌单和歌曲提供更丰富的详情展示。

#### 歌单详情增强
| 功能 | 描述 | 优先级 |
|------|------|--------|
| 编辑信息 | 修改歌单名/封面 | P1 |
| 排序 | 按添加时间/歌名/艺人排序 | P1 |
| 批量操作 | 多选删除/移动 | P2 |
| 简介字段 | 歌单描述文字 | P2 |

#### 歌曲详情页 (新增)
| 功能 | 描述 | 优先级 |
|------|------|--------|
| 歌曲信息 | 封面/标题/艺人/专辑/时长 | P1 |
| 相关推荐 | 相似歌曲推荐 (基于艺人/专辑) | P2 |
| 添加到歌单 | 快捷入口 | P1 |
| 分享 | 生成分享卡片 | P2 |

#### UI/UX
- 入口: 歌曲项长按菜单 → "查看详情"
- 页面: SongDetailScreen
- 布局: 大封面 + 信息卡片 + 操作按钮 + 相关推荐列表

---

### 3.2 歌词动效

#### 需求描述
歌词显示添加视觉效果，提升沉浸感。

#### 动效类型
| 效果 | 描述 | 优先级 |
|------|------|--------|
| 逐字高亮 | 当前歌词逐字高亮显示 | P1 |
| 字体缩放 | 当前行放大，其他行缩小 | P1 |
| 渐变过渡 | 行与行之间透明度渐变 | P1 |
| 背景模糊 | 动态模糊当前歌词行 | P2 |
| 粒子效果 | 背景跟随音乐跳动的粒子 | P2 |

#### 技术要点
- 使用 Compose Canvas 自定义绘制
- 逐字高亮需要支持逐字时间戳 (Enhanced LRC)
- 性能: 60fps，避免过度绘制

---

### 3.3 播放统计

#### 需求描述
统计用户的听歌数据，生成个人音乐报告。

#### 统计维度
| 维度 | 描述 | 优先级 |
|------|------|--------|
| 听歌时长 | 总时长/今日/本周/本月 | P1 |
| 听歌数量 | 总歌曲数/不重复歌曲数 | P1 |
| 艺人排行 | 听得最多的艺人Top10 | P1 |
| 时段分布 | 听歌时段热力图 | P2 |
| 音乐品味 | 标签云/曲风分布 | P2 |

#### 数据结构
```kotlin
data class PlaybackStats(
    val totalDurationMs: Long,
    val totalSongs: Int,
    val uniqueSongs: Int,
    val topArtists: List<ArtistStat>,
    val dailyStats: List<DailyStat>
)

data class ArtistStat(
    val name: String,
    val playCount: Int,
    val durationMs: Long
)

data class DailyStat(
    val date: String,
    val durationMs: Long,
    val songCount: Int
)
```

#### UI/UX
- 新页面: StatsScreen
- 图表: 使用 Compose 图表库
- 分享: 生成统计卡片可分享

---

### 3.4 歌单导入导出

#### 需求描述
支持歌单的备份与恢复，支持外部格式导入。

#### 功能点
| 功能 | 描述 | 优先级 |
|------|------|--------|
| 导出JSON | 导出歌单为JSON文件 | P1 |
| 导入JSON | 从JSON恢复歌单 | P1 |
| 网易云导入 | 支持网易云分享链接解析 | P2 |
| 批量导出 | 一键导出所有歌单 | P2 |

#### 导出格式
```json
{
  "version": "1.0",
  "exportTime": "2026-04-08T12:00:00Z",
  "playlists": [
    {
      "name": "我的歌单",
      "createdAt": 1234567890,
      "songs": [
        {
          "title": "歌曲名",
          "artist": "艺人",
          "source": "bilibili",
          "songId": "..."
        }
      ]
    }
  ]
}
```

#### UI/UX
- 入口: PlaylistListScreen ⋮ 菜单
- 选项: 导入 / 导出全部
- 文件选择: 系统文件选择器

---

## 4. 技术选型

### 4.1 歌词系统

| 方案 | 描述 | 优点 | 缺点 | 选型 |
|------|------|------|------|------|
| **A. 纯自研** | 自己解析LRC，自绘UI | 可控性强 | 开发量大 | ✅ 选 |
| B. 开源库 | 使用现有歌词库 | 快速 | 可能不符合UI需求 | - |

**决策**: 自研，LRC格式简单，Compose绘制灵活。

### 4.2 本地音乐扫描

| 方案 | 描述 | 优点 | 缺点 | 选型 |
|------|------|------|------|------|
| **A. 平台API** | MediaStore (Android) / MPMediaLibrary (iOS) | 官方支持 | 需要平台代码 | ✅ 选 |
| B. 文件遍历 | 直接扫描文件系统 | 可控 | 权限复杂、性能差 | - |

**决策**: 使用平台原生API，性能更好，权限管理规范。

### 4.3 播放统计

| 方案 | 描述 | 优点 | 缺点 | 选型 |
|------|------|------|------|------|
| **A. SQL聚合** | 数据库查询统计 | 准确 | 大数据量时慢 | ✅ 选 |
| B. 内存缓存 | 运行时统计 | 快速 | 丢失风险 | - |

**决策**: SQL聚合 + 简单缓存，定时刷新。

### 4.4 图表库

| 库 | 支持 | 特点 | 选型 |
|---|------|------|------|
| **Compose Charts** | KMP | 纯Compose，跨平台 | ✅ 选 |
| MPAndroidChart | Android only | 功能丰富 | - |

**决策**: Compose Charts (假设有KMP支持) 或自研简单图表。

---

## 5. 数据库变更

### 5.1 v1.1.0 新增表

```sql
-- 播放历史表
CREATE TABLE play_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    song_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    cover_url TEXT,
    source TEXT NOT NULL,
    played_at INTEGER NOT NULL,
    duration INTEGER,
    played_duration INTEGER
);

CREATE INDEX idx_history_played_at ON play_history(played_at DESC);
CREATE INDEX idx_history_song ON play_history(song_id, source);

-- 本地歌曲表 (虚拟表，实际通过平台API获取)
-- 本地歌曲缓存 (可选，用于快速显示)
CREATE TABLE local_song_cache (
    id TEXT PRIMARY KEY,
    uri TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT,
    duration INTEGER,
    cover_path TEXT,
    file_path TEXT NOT NULL,
    scanned_at INTEGER NOT NULL
);
```

### 5.2 v1.2.0 表变更

```sql
-- 歌单表扩展
ALTER TABLE playlist ADD COLUMN description TEXT DEFAULT '';
ALTER TABLE playlist ADD COLUMN sort_type TEXT DEFAULT 'manual'; -- manual/added_time/title/artist

-- 播放统计表 (按日汇总，自动维护)
CREATE TABLE play_stats_daily (
    date TEXT PRIMARY KEY,  -- YYYY-MM-DD
    duration_ms INTEGER DEFAULT 0,
    song_count INTEGER DEFAULT 0,
    unique_songs INTEGER DEFAULT 0
);

-- 艺人统计表
CREATE TABLE play_stats_artist (
    name TEXT PRIMARY KEY,
    play_count INTEGER DEFAULT 0,
    duration_ms INTEGER DEFAULT 0
);
```

---

## 6. 接口设计

### 6.1 歌词API

```kotlin
interface LyricsApi {
    // 获取歌词
    suspend fun getLyrics(songId: String, source: String): Result<Lyrics>
    
    // 备用：从第三方获取
    suspend fun getLyricsFromThirdParty(title: String, artist: String): Result<Lyrics>
}
```

### 6.2 本地音乐Repository

```kotlin
interface LocalMusicRepository {
    // 扫描音乐
    suspend fun scanMusic(): List<LocalSong>
    
    // 获取已扫描的音乐
    fun getLocalSongs(): Flow<List<LocalSong>>
    
    // 刷新扫描
    suspend fun refresh()
}
```

### 6.3 播放历史Repository

```kotlin
interface PlayHistoryRepository {
    // 记录播放
    suspend fun recordPlay(song: Song, playedDurationMs: Long)
    
    // 获取历史
    fun getHistory(limit: Int = 500): Flow<List<PlayHistoryItem>>
    
    // 清空历史
    suspend fun clearHistory()
}
```

### 6.4 统计Repository

```kotlin
interface StatsRepository {
    // 获取总统计
    suspend fun getTotalStats(): PlaybackStats
    
    // 获取时段分布
    suspend fun getHourlyDistribution(): List<Int> // 24小时播放时长
    
    // 获取艺人排行
    suspend fun getTopArtists(limit: Int = 10): List<ArtistStat>
}
```

---

## 7. UI 架构

### 7.1 新增页面

```
- LocalMusicScreen        # 本地音乐
- SongDetailScreen        # 歌曲详情 (v1.2.0)
- StatsScreen             # 播放统计 (v1.2.0)
- LyricsFullScreen        # 全屏歌词 (可选)
```

### 7.2 组件改造

```
- NowPlayingScreen
  ├── AlbumCover         # 现有
  ├── ProgressBar        # 现有
  ├── ControlButtons     # 现有
  └── LyricsPanel        # 新增 (可展开)

- MiniPlayer
  └── SleepTimerIcon     # 新增指示器

- PlaylistListScreen
  ├── RecentPlaylistCard # 新增 "最近播放" 入口
  └── ImportExportMenu   # 新增 ⋮ 菜单选项
```

---

## 8. 依赖引入

### 8.1 v1.1.0 新增依赖

```kotlin
// ID3标签解析 (本地音乐)
commonMain.dependencies {
    implementation("com.mpatric:mp3agic:0.9.1") // 或 KMP替代方案
}

androidMain.dependencies {
    // MediaStore 原生支持，无需额外依赖
}

iosMain.dependencies {
    // MPMediaLibrary 原生支持
}
```

### 8.2 v1.2.0 新增依赖

```kotlin
// 图表库 (待选型，需KMP支持)
// 方案1: 自研简单图表
// 方案2: 寻找KMP图表库

// JSON序列化 (已有)
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
```

---

## 9. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| iOS本地音乐权限收紧 | 高 | 提前调研iOS17+权限变化 |
| 歌词API不稳定 | 中 | 多源备选，允许用户手动导入 |
| 大数据量统计慢 | 中 | 预聚合 + 分页 + 异步 |
| Compose性能问题 | 中 | 歌词组件做性能测试 |

---

## 10. 里程碑

### v1.1.0 Milestone
- [ ] Week 1: 数据库设计 + 歌词解析器
- [ ] Week 2: 歌词UI + API对接
- [ ] Week 3: 本地音乐扫描 (Android)
- [ ] Week 4: 本地音乐 (iOS) + 播放历史
- [ ] Week 5: 睡眠定时 + 测试优化
- [ ] Week 6: 集成测试 + Bug修复

### v1.2.0 Milestone
- [ ] Week 1: 详情页设计 + 基础实现
- [ ] Week 2: 歌词动效技术预研
- [ ] Week 3: 歌词动效实现
- [ ] Week 4: 播放统计 + 图表
- [ ] Week 5: 导入导出功能
- [ ] Week 6: 集成测试 + 发布

---

*文档结束*
