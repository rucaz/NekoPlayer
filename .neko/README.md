# NekoPlayer AutoDevQueue 全自动开发任务队列

一个声明式、智能化的开发任务管理系统。定义任务，自动调度，一键执行。

---

## 🚀 快速开始

### 1. 初始化系统

```bash
neko-queue init
```

这会:
- 初始化 SQLite 数据库
- 安装 Git hooks
- 加载任务定义

### 2. 查看任务状态

```bash
neko-queue status
```

输出示例:
```
📊 NekoPlayer 任务队列状态
==================================================
总任务数: 19

按状态统计:
  pending      :  15 个
  in_progress  :   1 个
  complete     :   3 个

接下来5个任务:
1. [10] LYRICS-001 - 歌词解析器核心实现... (8h)
2. [ 9] LOCAL-001 - Android本地音乐扫描... (12h)
3. [ 9] LOCAL-002 - iOS本地音乐扫描... (12h)
...
```

### 3. 开始执行任务

自动选择最高优先级任务:
```bash
neko-queue start
```

或指定任务:
```bash
neko-queue start LYRICS-001
```

这会:
- 自动创建功能分支 `feature/lyrics-lyrics-001`
- 更新任务状态为 `in_progress`
- 显示任务详情和验收标准

### 4. 提交工作

```bash
# 常规提交
neko-queue commit "实现LRC时间标签解析"

# 实际执行: git commit -m "lyrics(lyrics-001): 实现LRC时间标签解析"
```

### 5. 完成任务

```bash
neko-queue complete
```

这会:
- 推送分支到远程
- 创建 Pull Request
- 更新任务状态为 `complete`
- 自动触发后续依赖任务

---

## 📋 任务定义

任务定义在 `.neko/neko-tasks.yml`:

```yaml
tasks:
  - id: LYRICS-001
    title: "歌词解析器核心实现"
    module: lyrics
    priority: 10
    estimate: 8h
    milestone: v1.1.0
    content:
      type: code
      specs:
        - "实现LrcParser.kt"
        - "单元测试覆盖率 > 90%"
    dependencies: []
    triggers:
      on_complete: [LYRICS-002]
```

### 字段说明

| 字段 | 说明 | 示例 |
|------|------|------|
| `id` | 唯一标识 | `LYRICS-001` |
| `title` | 任务标题 | 歌词解析器核心实现 |
| `module` | 所属模块 | lyrics, local_music, player |
| `priority` | 优先级 1-100 | 10 (越高越优先) |
| `estimate` | 预估时间 | 8h, 2d |
| `milestone` | 里程碑 | v1.1.0 |
| `dependencies` | 依赖任务 | `[TASK-001, TASK-002]` |
| `triggers` | 完成后触发 | `on_complete: [TASK-003]` |

---

## 🎯 工作流

### 典型工作流

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  queue  │ -> │  start  │ -> │  commit │ -> │complete │ -> │  next   │
│ status  │    │  task   │    │  work   │    │   PR    │    │  task   │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
     |              |              |              |              |
     v              v              v              v              v
 查看任务       自动创建      自动格式化      自动创建       自动触发
 队列状态       功能分支      提交信息        Pull Request    后续任务
```

### 自动化规则

系统内置以下自动化规则:

1. **智能分支命名**
   - 格式: `feature/{module}-{task-id}`
   - 示例: `feature/lyrics-lyrics-001`

2. **提交信息格式化**
   - 格式: `{module}({task-id}): {message}`
   - 示例: `lyrics(lyrics-001): 实现LRC解析器`

3. **PR自动生成**
   - 标题: `{task-id}: {title}`
   - 正文: 自动包含任务验收清单

4. **任务依赖自动触发**
   - 任务A完成后，自动标记依赖A的任务为 `ready`

5. **每日报告**
   - 定时生成开发日报 (可配置发送到飞书)

---

## 📊 调度算法

### 优先级计算

任务按以下因子排序:

1. **优先级数值** (priority): 越高越优先
2. **里程碑顺序**: v1.1.0 > v1.2.0
3. **依赖满足**: 所有依赖完成后才进入 `ready` 状态

### 状态流转

```
[PENDING] --依赖满足--> [READY] --start--> [IN_PROGRESS] 
                                                      |
                                                      v
[BLOCKED] <---阻塞-------- [REVIEW] <---提交PR---- [COMPLETE]
    |                       ^
    |--解决阻塞-------------|
```

---

## 🔧 命令参考

| 命令 | 说明 |
|------|------|
| `neko-queue init` | 初始化队列系统 |
| `neko-queue status` | 查看任务队列状态 |
| `neko-queue next` | 显示接下来可执行的任务 |
| `neko-queue start [id]` | 开始任务 (不传则自动选择) |
| `neko-queue commit [msg]` | 提交更改 |
| `neko-queue complete` | 完成任务并创建PR |
| `neko-queue block [reason]` | 标记任务阻塞 |
| `neko-queue report` | 生成开发报告 |
| `neko-queue gantt` | 导出甘特图数据 |

---

## 🗄️ 数据存储

### SQLite 数据库

- 路径: `.neko/queue.db`
- 表: `tasks`, `task_logs`

### 文件结构

```
.neko/
├── neko-tasks.yml          # 任务定义
├── queue.db                # SQLite数据库
├── .current_task           # 当前进行中的任务ID
├── scripts/
│   ├── neko-queue          # 主命令脚本
│   ├── scheduler.py        # Python调度引擎
│   └── git-hooks/
│       └── post-commit     # Git钩子
└── reports/
    └── daily-YYYY-MM-DD.md # 日报
```

---

## 🎨 扩展开发

### 添加自定义规则

编辑 `.neko/neko-tasks.yml` 的 `rules` 部分:

```yaml
rules:
  - name: notify_on_complete
    trigger: task_complete
    action: |
      curl -X POST "你的webhook地址" -d "任务完成"
```

### 集成到 CI/CD

GitHub Actions 示例:

```yaml
name: AutoDevQueue
on:
  pull_request:
    types: [closed]
    
jobs:
  update-queue:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Update task status
        run: |
          pip install pyyaml
          ./.neko/scripts/neko-queue complete --task-id ${{ github.event.pull_request.head.ref }}
```

---

## 📝 最佳实践

1. **任务粒度**: 单个任务控制在 4-16 小时
2. **依赖管理**: 尽量减少依赖，并行开发
3. **及时提交**: 每完成一个检查点就 `neko-queue commit`
4. **阻塞上报**: 遇到问题立即 `neko-queue block`，避免时间浪费
5. **每日查看**: 早上先 `neko-queue status` 了解当日工作

---

*Powered by NekoPlayer AutoDevQueue v1.0*
