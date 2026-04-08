#!/bin/bash
# NekoPlayer AutoDevQueue 快速安装脚本

set -e

echo "🚀 NekoPlayer AutoDevQueue 安装器"
echo "=================================="
echo ""

# 检查依赖
echo "📋 检查依赖..."

# Python 3.8+
if ! command -v python3 &> /dev/null; then
    echo "❌ 错误: 需要 Python 3.8+"
    exit 1
fi

PYTHON_VERSION=$(python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
echo "✅ Python: $PYTHON_VERSION"

# Git
if ! command -v git &> /dev/null; then
    echo "❌ 错误: 需要 Git"
    exit 1
fi
echo "✅ Git: $(git --version)"

# PyYAML
if ! python3 -c "import yaml" 2>/dev/null; then
    echo "📦 安装 PyYAML..."
    pip3 install pyyaml
fi
echo "✅ PyYAML: 已安装"

# 可选: GitHub CLI
if command -v gh &> /dev/null; then
    echo "✅ GitHub CLI: $(gh --version | head -1)"
else
    echo "⚠️  GitHub CLI: 未安装 (用于自动创建PR，可选)"
    echo "    安装: https://cli.github.com/"
fi

echo ""
echo "🔧 初始化队列系统..."

# 创建目录结构
mkdir -p .neko/scripts
mkdir -p .neko/reports
mkdir -p .neko/scripts/git-hooks

# 复制脚本 (如果它们不存在在当前目录)
if [ -f ".neko/scripts/neko-queue" ]; then
    chmod +x .neko/scripts/neko-queue
    chmod +x .neko/scripts/scheduler.py
    echo "✅ 脚本已就绪"
else
    echo "⚠️  请将脚本文件放入 .neko/scripts/ 目录"
fi

# 初始化数据库
if [ -f ".neko/scripts/scheduler.py" ] && [ -f ".neko/neko-tasks.yml" ]; then
    python3 .neko/scripts/scheduler.py load --yaml .neko/neko-tasks.yml
    echo "✅ 数据库已初始化"
fi

# 安装 Git hooks
if [ -d ".git" ] && [ -f ".neko/scripts/git-hooks/post-commit" ]; then
    cp .neko/scripts/git-hooks/post-commit .git/hooks/
    chmod +x .git/hooks/post-commit
    echo "✅ Git hooks 已安装"
fi

# 添加到 PATH (可选)
echo ""
echo "📝 配置建议:"
echo ""
echo "1. 创建全局快捷命令:"
echo "   echo 'export PATH=\"\$PATH:$(pwd)/.neko/scripts\"' >> ~/.bashrc"
echo "   source ~/.bashrc"
echo ""
echo "2. 或使用 Makefile:"
echo "   make status"
echo "   make start"
echo "   make commit MSG='提交信息'"
echo ""
echo "3. 首次使用:"
echo "   neko-queue status    # 查看任务队列"
echo "   neko-queue start     # 开始第一个任务"
echo ""

echo "🎉 安装完成!"
echo ""

# 显示当前状态
if [ -f ".neko/scripts/neko-queue" ]; then
    echo "📊 当前任务队列状态:"
    echo "===================="
    ./.neko/scripts/neko-queue status 2>/dev/null || echo "请先加载任务定义文件"
fi
