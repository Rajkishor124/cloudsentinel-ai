"""DebugMemory — persistent log of errors, fixes, and outcomes.

Stores each error-fix cycle so the system learns from past failures
and can prioritize fixes that have worked before.
"""

import json
import os
from dataclasses import dataclass, asdict
from datetime import datetime
from pathlib import Path
from typing import Optional


@dataclass
class DebugEntry:
    timestamp: str
    error_type: str
    error_message: str
    category: str
    fix_applied: str
    retry_success: bool
    retry_count: int


class DebugMemory:
    """Persistent memory of error-fix cycles."""

    def __init__(self, log_dir: str = "logs/debug"):
        self.log_dir = Path(log_dir)
        self.log_dir.mkdir(parents=True, exist_ok=True)
        self.log_path = self.log_dir / "debug_history.json"
        self.entries: list[DebugEntry] = self._load()

    def _load(self) -> list[DebugEntry]:
        """Load existing debug history from disk."""
        if self.log_path.exists():
            with open(self.log_path, "r") as f:
                data = json.load(f)
            return [DebugEntry(**entry) for entry in data]
        return []

    def record(self, entry: DebugEntry):
        """Record a new error-fix cycle."""
        self.entries.append(entry)
        self._save()

    def _save(self):
        """Persist debug history to disk."""
        with open(self.log_path, "w") as f:
            json.dump([asdict(e) for e in self.entries], f, indent=2)

    def get_fix_history(self, error_type: str) -> list[DebugEntry]:
        """Get all past fixes for a given error type."""
        return [e for e in self.entries if e.error_type == error_type]

    def get_successful_fixes(self, error_type: str) -> list[str]:
        """Get fixes that have successfully resolved a given error."""
        return [
            e.fix_applied
            for e in self.entries
            if e.error_type == error_type and e.retry_success
        ]

    def get_most_common_fix(self, error_type: str) -> Optional[str]:
        """Get the most frequently successful fix for an error type."""
        successful = self.get_successful_fixes(error_type)
        if not successful:
            return None
        from collections import Counter
        return Counter(successful).most_common(1)[0][0]

    def summary(self) -> dict:
        """Get a summary of all debugging activity."""
        if not self.entries:
            return {"total_errors": 0}

        by_category = {}
        for e in self.entries:
            by_category.setdefault(e.category, {"count": 0, "successes": 0})
            by_category[e.category]["count"] += 1
            if e.retry_success:
                by_category[e.category]["successes"] += 1

        return {
            "total_errors": len(self.entries),
            "by_category": by_category,
            "overall_success_rate": (
                sum(1 for e in self.entries if e.retry_success) /
                len(self.entries)
            ),
        }

    def __repr__(self) -> str:
        summary = self.summary()
        return f"DebugMemory(total={summary['total_errors']}, success_rate={summary.get('overall_success_rate', 0):.0%})"
