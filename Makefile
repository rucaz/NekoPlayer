# NekoPlayer AutoDevQueue Makefile
# 提供便捷的快捷命令

.PHONY: help init status start commit complete next report gantt

# 默认显示帮助
help:
	@echo "NekoPlayer AutoDevQueue - 快捷命令"
	@echo ""
	@echo "  make init      - 初始化队列系统"
	@echo "  make status    - 查看任务状态"
	@echo "  make next      - 查看接下来任务"
	@echo "  make start     - 开始最高优先级任务"
	@echo "  make commit    - 提交更改 (需传 MSG=...)"
	@echo "  make complete  - 完成任务并创建PR"
	@echo "  make report    - 生成开发报告"
	@echo "  make gantt     - 导出甘特图"
	@echo ""

# 初始化
init:
	@.neko/scripts/neko-queue init

# 查看状态
status:
	@.neko/scripts/neko-queue status

# 接下来任务
next:
	@.neko/scripts/neko-queue next

# 开始任务
start:
	@.neko/scripts/neko-queue start

# 提交更改 (使用: make commit MSG="你的提交信息")
commit:
	@if [ -z "$(MSG)" ]; then \
		echo "用法: make commit MSG='你的提交信息'"; \
		exit 1; \
	fi
	@.neko/scripts/neko-queue commit "$(MSG)"

# 完成任务
complete:
	@.neko/scripts/neko-queue complete

# 标记阻塞 (使用: make block REASON="原因")
block:
	@if [ -z "$(REASON)" ]; then \
		echo "用法: make block REASON='阻塞原因'"; \
		exit 1; \
	fi
	@.neko/scripts/neko-queue block "$(REASON)"

# 生成报告
report:
	@.neko/scripts/neko-queue report

# 导出甘特图
gantt:
	@.neko/scripts/neko-queue gantt

# 快捷: 开始歌词任务
start-lyrics:
	@.neko/scripts/neko-queue start LYRICS-001

# 快捷: 开始本地音乐任务
start-local:
	@.neko/scripts/neko-queue start LOCAL-001
