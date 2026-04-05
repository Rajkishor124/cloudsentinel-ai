"""SafeExecutionWrapper — catch, classify, fix, retry loop.

Wraps the training pipeline with self-debugging capabilities.
When an error occurs, it:
  1. Captures the exception and traceback
  2. Classifies the error category
  3. Checks memory for past successful fixes
  4. Applies targeted fixes via FixEngine
  5. Retries execution
  6. Logs the outcome for future learning
"""

import logging
import time
import traceback
from dataclasses import asdict
from typing import Any, Callable

from src.utils.error_classifier import ErrorCategory, ErrorClassifier
from src.utils.debug_memory import DebugEntry, DebugMemory
from src.utils.fix_engine import FixEngine

logger = logging.getLogger(__name__)


class SafeExecutionWrapper:
    """Wraps a callable with error detection, classification, auto-fix, and retry."""

    def __init__(
        self,
        max_retries: int = 3,
        retry_delay: float = 2.0,
        log_dir: str = "logs/debug",
        enable_auto_fix: bool = True,
    ):
        self.max_retries = max_retries
        self.retry_delay = retry_delay
        self.enable_auto_fix = enable_auto_fix
        self.memory = DebugMemory(log_dir=log_dir)

    def execute(self, fn: Callable, *args, context: dict | None = None, **kwargs) -> Any:
        """Execute a callable with auto-retry and error recovery.

        Args:
            fn: The callable to execute.
            *args: Positional arguments for fn.
            context: Additional context for fix engine (env, last_observation, etc.).
            **kwargs: Keyword arguments for fn.

        Returns:
            The result of fn() if successful.

        Raises:
            The original exception if all retries exhausted.
        """
        context = context or {}
        last_error = None

        for attempt in range(1, self.max_retries + 1):
            try:
                return fn(*args, **kwargs)

            except Exception as e:
                last_error = e
                tb_str = traceback.format_exc()
                classification = ErrorClassifier.classify(e, tb_str)

                logger.error(
                    f"[Attempt {attempt}/{self.max_retries}] "
                    f"Error: {classification.category.value} — {e}"
                )

                if attempt >= self.max_retries:
                    logger.error(
                        f"All {self.max_retries} attempts failed. Last error: {e}")
                    break

                if self.enable_auto_fix:
                    fixes = FixEngine.apply_fixes(
                        classification.category, context)

                    if fixes:
                        logger.info(f"Fixes applied: {fixes}")
                    else:
                        logger.info(
                            f"No automatic fix available. "
                            f"Suggestion: {classification.suggested_fix}"
                        )

                    # Record in memory
                    entry = DebugEntry(
                        timestamp=time.strftime("%Y-%m-%dT%H:%M:%S"),
                        error_type=type(e).__name__,
                        error_message=str(e)[:500],
                        category=classification.category.value,
                        fix_applied="; ".join(
                            fixes) if fixes else classification.suggested_fix,
                        retry_success=False,  # Will be updated on next attempt
                        retry_count=attempt,
                    )
                    self.memory.record(entry)

                # Exponential backoff
                delay = self.retry_delay * (2 ** (attempt - 1))
                logger.info(f"Retrying in {delay:.1f}s...")
                time.sleep(delay)

        raise last_error

    def execute_training(self, train_fn: Callable, env=None, context: dict | None = None):
        """Specialized wrapper for training loops with env-aware fixes.

        Passes the environment as context for observation-shape fixes.
        """
        ctx = context or {}
        if env is not None:
            ctx["env"] = env

        def _run():
            return train_fn()

        return self.execute(_run, context=ctx)

    def print_summary(self):
        """Print debugging history summary."""
        summary = self.memory.summary()
        if summary["total_errors"] == 0:
            print("\n=== Self-Debug Summary: No errors recorded ===")
            return

        print("\n=== Self-Debug Summary ===")
        print(f"  Total errors encountered: {summary['total_errors']}")
        print(
            f"  Overall success rate: {summary.get('overall_success_rate', 0):.0%}")
        print(f"  By category:")
        for cat, stats in summary.get("by_category", {}).items():
            print(
                f"    {cat}: {stats['count']} errors, {stats['successes']} resolved")
        print("=" * 40)
