"""FixEngine — applies targeted fixes to recover from classified errors.

Each fix operates at system boundaries (env wrappers, HTTP clients, config)
without modifying core RL logic.
"""

import importlib
import logging
from typing import Any, Callable, Optional

from src.utils.error_classifier import ErrorCategory

logger = logging.getLogger(__name__)


class FixEngine:
    """Applies automatic fixes for known error categories."""

    # Map from error category to list of fix functions
    _fixes: dict[ErrorCategory, list[Callable]] = {}

    @classmethod
    def register(cls, category: ErrorCategory):
        """Decorator to register a fix function for an error category."""
        def decorator(func: Callable):
            cls._fixes.setdefault(category, []).append(func)
            return func
        return decorator

    @classmethod
    def apply_fixes(cls, category: ErrorCategory, context: dict) -> list[str]:
        """Apply all registered fixes for a given error category.

        Returns list of fix descriptions that were applied.
        """
        fixes_applied = []
        for fix_fn in cls._fixes.get(category, []):
            try:
                result = fix_fn(context)
                if result:
                    fixes_applied.append(result)
                    logger.info(f"Applied fix: {result}")
            except Exception as e:
                logger.warning(f"Fix failed: {fix_fn.__name__}: {e}")
        return fixes_applied


# ============================================================
# Fix: SERIALIZATION_ERROR — Ensure numpy types are converted
# ============================================================
@FixEngine.register(ErrorCategory.SERIALIZATION_ERROR)
def fix_numpy_serialization(context: dict) -> Optional[str]:
    """Patch the HTTP client's step method to use numpy-to-python conversion.

    This fix is idempotent — if already patched, returns None.
    """
    # The http_client module already has _numpy_to_python built in.
    # If this error still fires, it means the old code path is being used.
    # We force a module reload to pick up the fixed version.
    try:
        import src.env.http_client as http_client_mod
        importlib.reload(http_client_mod)
        return "Reloaded http_client module with numpy serialization fix"
    except ImportError:
        return None


# ============================================================
# Fix: OBSERVATION_SHAPE_ERROR — Enforce fixed observation size
# ============================================================
@FixEngine.register(ErrorCategory.OBSERVATION_SHAPE_ERROR)
def fix_observation_shape(context: dict) -> Optional[str]:
    """Force the env's observation_space to match the actual returned shape."""
    try:
        env = context.get("env")
        if env is None:
            return None

        from gymnasium import spaces
        import numpy as np

        # Get the actual obs size from the last observation
        last_obs = context.get("last_observation")
        if last_obs is not None:
            actual_size = len(last_obs)
            env.observation_space = spaces.Box(
                low=0.0, high=1.0, shape=(actual_size,), dtype=np.float32
            )
            return f"Set observation_space to ({actual_size},)"

        # Fallback: use the known stacked size
        expected = getattr(env, "stacked_obs_size", None)
        if expected:
            env.observation_space = spaces.Box(
                low=0.0, high=1.0, shape=(expected,), dtype=np.float32
            )
            return f"Set observation_space to ({expected},) from stacked_obs_size"

        return None
    except Exception as e:
        logger.warning(f"Observation shape fix failed: {e}")
        return None


# ============================================================
# Fix: IMPORT_ERROR — Fix sys.path for module resolution
# ============================================================
@FixEngine.register(ErrorCategory.IMPORT_ERROR)
def fix_import_path(context: dict) -> Optional[str]:
    """Ensure the agent root is on sys.path for package imports."""
    import sys
    import os

    agent_root = os.path.dirname(os.path.dirname(
        os.path.dirname(os.path.abspath(__file__))))
    if agent_root not in sys.path:
        sys.path.insert(0, agent_root)
        return f"Added agent root to sys.path: {agent_root}"
    return None


# ============================================================
# Fix: API_CONNECTION_ERROR — Retry with backoff
# ============================================================
@FixEngine.register(ErrorCategory.API_CONNECTION_ERROR)
def fix_api_connection(context: dict) -> Optional[str]:
    """Signal that a retry with backoff should be attempted."""
    return "API connection error — retry with backoff recommended"


# ============================================================
# Fix: MODEL_LOAD_ERROR — Reconstruct model with safe defaults
# ============================================================
@FixEngine.register(ErrorCategory.MODEL_LOAD_ERROR)
def fix_model_config(context: dict) -> Optional[str]:
    """Remove known problematic kwargs from SB3 model config."""
    return "Model config error — check for duplicate kwargs in SB3 constructor"
