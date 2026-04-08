#!/usr/bin/env python3
"""
NekoPlayer AutoDevQueue - 智能调度引擎
负责: 任务排序、依赖解析、批次调度
"""

import yaml
import json
import sqlite3
from datetime import datetime
from typing import List, Dict, Optional, Set
from dataclasses import dataclass, asdict
from enum import Enum
import argparse
import os

class TaskStatus(Enum):
    PENDING = "pending"
    READY = "ready"        # 依赖已满足
    IN_PROGRESS = "in_progress"
    REVIEW = "review"      # 代码完成，等待review
    COMPLETE = "complete"
    BLOCKED = "blocked"
    FAILED = "failed"

class TaskType(Enum):
    CODE = "code"
    DESIGN = "design"
    DATABASE = "database"
    RESEARCH = "research"
    DOC = "doc"

@dataclass
class Task:
    id: str
    title: str
    module: str
    priority: int
    estimate: str
    assignee: str
    milestone: str
    content: Dict
    dependencies: List[str]
    criteria: List[Dict]
    triggers: Optional[Dict] = None
    platform: Optional[List[str]] = None
    status: TaskStatus = TaskStatus.PENDING
    started_at: Optional[str] = None
    completed_at: Optional[str] = None
    blocked_hours: int = 0
    attempt: int = 0

class TaskScheduler:
    def __init__(self, db_path: str = ".neko/queue.db"):
        self.db_path = db_path
        self.tasks: Dict[str, Task] = {}
        self.config = {}
        self.init_db()
    
    def init_db(self):
        """初始化SQLite数据库"""
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS tasks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                module TEXT NOT NULL,
                priority INTEGER NOT NULL,
                estimate TEXT,
                assignee TEXT,
                milestone TEXT,
                content TEXT,  -- JSON
                dependencies TEXT,  -- JSON
                criteria TEXT,  -- JSON
                triggers TEXT,  -- JSON
                platform TEXT,  -- JSON
                status TEXT NOT NULL,
                started_at TEXT,
                completed_at TEXT,
                blocked_hours INTEGER DEFAULT 0,
                attempt INTEGER DEFAULT 0
            )
        ''')
        
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS task_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id TEXT NOT NULL,
                action TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                details TEXT
            )
        ''')
        
        conn.commit()
        conn.close()
    
    def load_from_yaml(self, yaml_path: str = ".neko/neko-tasks.yml"):
        """从YAML加载任务定义"""
        with open(yaml_path, 'r', encoding='utf-8') as f:
            data = yaml.safe_load(f)
        
        self.config = data.get('config', {})
        
        for task_data in data.get('tasks', []):
            task = Task(
                id=task_data['id'],
                title=task_data['title'],
                module=task_data['module'],
                priority=task_data['priority'],
                estimate=task_data.get('estimate', '0h'),
                assignee=task_data.get('assignee', 'auto'),
                milestone=task_data.get('milestone', ''),
                content=task_data.get('content', {}),
                dependencies=task_data.get('dependencies', []),
                criteria=task_data.get('criteria', []),
                triggers=task_data.get('triggers'),
                platform=task_data.get('platform'),
            )
            self.tasks[task.id] = task
        
        self.sync_to_db()
        print(f"✅ 已加载 {len(self.tasks)} 个任务")
    
    def sync_to_db(self):
        """同步任务到数据库"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        for task in self.tasks.values():
            cursor.execute('''
                INSERT OR REPLACE INTO tasks VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
            ''', (
                task.id, task.title, task.module, task.priority,
                task.estimate, task.assignee, task.milestone,
                json.dumps(task.content),
                json.dumps(task.dependencies),
                json.dumps(task.criteria),
                json.dumps(task.triggers) if task.triggers else None,
                json.dumps(task.platform) if task.platform else None,
                task.status.value,
                task.started_at, task.completed_at,
                task.blocked_hours, task.attempt
            ))
        
        conn.commit()
        conn.close()
    
    def get_ready_tasks(self) -> List[Task]:
        """获取所有依赖已满足的任务 (READY状态)"""
        ready = []
        for task in self.tasks.values():
            if task.status == TaskStatus.PENDING:
                # 检查依赖是否全部完成
                deps_satisfied = all(
                    self.tasks.get(dep_id, Task(id=dep_id, title="", module="", priority=0, estimate="", assignee="", milestone="", content={}, dependencies=[], criteria=[])).status == TaskStatus.COMPLETE
                    for dep_id in task.dependencies
                )
                if deps_satisfied:
                    ready.append(task)
        return ready
    
    def get_next_tasks(self, limit: int = 5) -> List[Task]:
        """获取接下来应该执行的任务 (按优先级排序)"""
        ready = self.get_ready_tasks()
        # 按优先级降序，相同优先级按里程碑
        ready.sort(key=lambda t: (-t.priority, t.milestone))
        return ready[:limit]
    
    def start_task(self, task_id: str) -> bool:
        """开始执行任务"""
        if task_id not in self.tasks:
            print(f"❌ 任务 {task_id} 不存在")
            return False
        
        task = self.tasks[task_id]
        if task.status != TaskStatus.PENDING:
            print(f"⚠️ 任务 {task_id} 状态为 {task.status.value}，无法开始")
            return False
        
        task.status = TaskStatus.IN_PROGRESS
        task.started_at = datetime.now().isoformat()
        task.attempt += 1
        
        self.sync_to_db()
        self._log(task_id, "start", f"第{task.attempt}次尝试")
        
        print(f"🚀 任务 {task_id} 已开始: {task.title}")
        return True
    
    def complete_task(self, task_id: str) -> bool:
        """完成任务"""
        if task_id not in self.tasks:
            print(f"❌ 任务 {task_id} 不存在")
            return False
        
        task = self.tasks[task_id]
        task.status = TaskStatus.COMPLETE
        task.completed_at = datetime.now().isoformat()
        
        self.sync_to_db()
        self._log(task_id, "complete", "任务完成")
        
        # 触发后续任务
        self._trigger_next_tasks(task)
        
        print(f"✅ 任务 {task_id} 已完成: {task.title}")
        return True
    
    def block_task(self, task_id: str, reason: str = ""):
        """标记任务阻塞"""
        if task_id in self.tasks:
            self.tasks[task_id].status = TaskStatus.BLOCKED
            self.sync_to_db()
            self._log(task_id, "block", reason)
            print(f"🚫 任务 {task_id} 已阻塞: {reason}")
    
    def _trigger_next_tasks(self, completed_task: Task):
        """任务完成后触发后续任务"""
        if not completed_task.triggers:
            return
        
        next_ids = completed_task.triggers.get('on_complete', [])
        for next_id in next_ids:
            if next_id in self.tasks:
                print(f"📌 触发后续任务: {next_id}")
                # 这里可以集成通知或自动创建分支
    
    def _log(self, task_id: str, action: str, details: str = ""):
        """记录日志"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute('''
            INSERT INTO task_logs (task_id, action, timestamp, details)
            VALUES (?, ?, ?, ?)
        ''', (task_id, action, datetime.now().isoformat(), details))
        conn.commit()
        conn.close()
    
    def status_report(self) -> str:
        """生成状态报告"""
        status_counts = {}
        for task in self.tasks.values():
            status_counts[task.status.value] = status_counts.get(task.status.value, 0) + 1
        
        lines = [
            "📊 NekoPlayer 任务队列状态",
            "=" * 50,
            f"总任务数: {len(self.tasks)}",
            "",
            "按状态统计:",
        ]
        for status, count in sorted(status_counts.items()):
            lines.append(f"  {status:12} : {count:3} 个")
        
        lines.extend([
            "",
            "接下来5个任务:",
            "-" * 50,
        ])
        
        for i, task in enumerate(self.get_next_tasks(5), 1):
            lines.append(f"{i}. [{task.priority:2}] {task.id} - {task.title[:30]}... ({task.estimate})")
        
        lines.extend([
            "",
            "按里程碑统计:",
            "-" * 50,
        ])
        
        milestone_stats = {}
        for task in self.tasks.values():
            ms = task.milestone
            if ms not in milestone_stats:
                milestone_stats[ms] = {'total': 0, 'complete': 0}
            milestone_stats[ms]['total'] += 1
            if task.status == TaskStatus.COMPLETE:
                milestone_stats[ms]['complete'] += 1
        
        for ms, stats in sorted(milestone_stats.items()):
            pct = stats['complete'] / stats['total'] * 100 if stats['total'] > 0 else 0
            bar = '█' * int(pct / 10) + '░' * (10 - int(pct / 10))
            lines.append(f"  {ms}: {bar} {pct:.0f}% ({stats['complete']}/{stats['total']})")
        
        return '\n'.join(lines)
    
    def export_gantt_data(self, output: str = ".neko/gantt.json"):
        """导出甘特图数据"""
        data = []
        for task in self.tasks.values():
            data.append({
                'id': task.id,
                'name': task.title,
                'module': task.module,
                'start': task.started_at or '',
                'end': task.completed_at or '',
                'progress': 100 if task.status == TaskStatus.COMPLETE else (
                    50 if task.status == TaskStatus.IN_PROGRESS else 0
                ),
                'dependencies': task.dependencies,
                'milestone': task.milestone,
            })
        
        with open(output, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        
        print(f"📈 甘特图数据已导出: {output}")

def main():
    parser = argparse.ArgumentParser(description='NekoPlayer AutoDevQueue 调度引擎')
    parser.add_argument('action', choices=['load', 'status', 'next', 'start', 'complete', 'block', 'gantt'])
    parser.add_argument('--task-id', help='任务ID')
    parser.add_argument('--reason', help='阻塞原因')
    parser.add_argument('--yaml', default='.neko/neko-tasks.yml', help='任务定义文件')
    
    args = parser.parse_args()
    
    scheduler = TaskScheduler()
    
    if args.action == 'load':
        scheduler.load_from_yaml(args.yaml)
    
    elif args.action == 'status':
        print(scheduler.status_report())
    
    elif args.action == 'next':
        tasks = scheduler.get_next_tasks()
        print("🎯 接下来可执行的任务:")
        for task in tasks:
            deps = f" (依赖: {', '.join(task.dependencies)})" if task.dependencies else ""
            print(f"  - [{task.priority}] {task.id}: {task.title}{deps}")
    
    elif args.action == 'start':
        if not args.task_id:
            print("❌ 请指定 --task-id")
            return
        scheduler.start_task(args.task_id)
    
    elif args.action == 'complete':
        if not args.task_id:
            print("❌ 请指定 --task-id")
            return
        scheduler.complete_task(args.task_id)
    
    elif args.action == 'block':
        if not args.task_id:
            print("❌ 请指定 --task-id")
            return
        scheduler.block_task(args.task_id, args.reason or "未知原因")
    
    elif args.action == 'gantt':
        scheduler.export_gantt_data()

if __name__ == '__main__':
    main()
