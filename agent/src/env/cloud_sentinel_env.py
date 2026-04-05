"""Gymnasium environment wrapper for the CloudSentinel simulation.

Bridges the Spring Boot simulator (via HTTP) with the RL training loop.
Observation size is FIXED at 31 (7 services * 4 metrics + 3 extras)
regardless of active topology size.
"""

from collections import deque
from typing import Optional

import gymnasium as gym
import numpy as np
from gymnasium import spaces

from src.env.http_client import SimulatorAPIClient

# Fixed constants matching backend ObservationDTO
MAX_SERVICES = 7
METRICS_PER_SERVICE = 4  # cpu, memory, error_rate, latency
EXTRA_FEATURES = 3       # tick_ratio, max_cascade_depth, has_failure
OBS_SIZE = MAX_SERVICES * METRICS_PER_SERVICE + EXTRA_FEATURES  # 31
HISTORY_LEN = 4


class CloudSentinelEnv(gym.Env):
    """Cloud incident response RL environment backed by Spring Boot simulator."""

    metadata = {"render_modes": ["human"]}

    def __init__(
        self,
        api_url: str = "http://localhost:8080",
        difficulty: str = "SIMPLE",
        render_mode: Optional[str] = None,
    ):
        super().__init__()
        self.api = SimulatorAPIClient(base_url=api_url)
        self.difficulty = difficulty
        self.render_mode = render_mode

        # FIXED observation space — stacked across HISTORY_LEN frames
        # Single frame: OBS_SIZE = 31
        # Stacked: HISTORY_LEN * OBS_SIZE = 124
        self.stacked_obs_size = HISTORY_LEN * OBS_SIZE
        self.observation_space = spaces.Box(
            low=0.0, high=1.0, shape=(self.stacked_obs_size,), dtype=np.float32
        )

        # Action space: 9 discrete actions (matches ActionType enum)
        self.action_space = spaces.Discrete(9)

        # State buffer for temporal context
        self._state_buffer: deque[np.ndarray] = deque(maxlen=HISTORY_LEN)

        # Episode tracking
        self._tick = 0
        self._episode_reward = 0.0

    def reset(
        self,
        *,
        seed: Optional[int] = None,
        options: Optional[dict] = None,
    ) -> tuple[np.ndarray, dict]:
        """Reset the environment to initial state."""
        super().reset(seed=seed)

        difficulty = options.get(
            "difficulty", self.difficulty) if options else self.difficulty
        resp = self.api.reset(difficulty=difficulty)

        obs = np.array(resp["observation"], dtype=np.float32)
        assert obs.shape == (OBS_SIZE,), (
            f"Backend returned obs shape {obs.shape}, expected ({OBS_SIZE},). "
            "Check ObservationDTO.flatten() in backend."
        )

        # Reset state buffer with fixed-size zero vectors
        self._state_buffer.clear()
        for _ in range(HISTORY_LEN - 1):
            self._state_buffer.append(np.zeros(OBS_SIZE, dtype=np.float32))
        self._state_buffer.append(obs)

        self._tick = 0
        self._episode_reward = 0.0

        return self._build_stacked_obs(), {}

    def step(
        self, action: int
    ) -> tuple[np.ndarray, float, bool, bool, dict]:
        """Apply action and advance simulation by one tick."""
        resp = self.api.step(action)

        obs = np.array(resp["observation"], dtype=np.float32)
        assert obs.shape == (OBS_SIZE,), (
            f"Step returned obs shape {obs.shape}, expected ({OBS_SIZE},)"
        )

        reward = float(resp["reward"])
        done = bool(resp["done"])
        truncated = False

        self._state_buffer.append(obs)
        self._tick += 1
        self._episode_reward += reward

        info = {
            "action_name": resp.get("action_name", ""),
            "slo_score": resp.get("slo_score", 0.0),
            "cumulative_cost": resp.get("cumulative_cost", 0.0),
            "cumulative_reward": resp.get("cumulative_reward", 0.0),
            "failure_mode": resp.get("failure_mode", "HEALTHY"),
            "tick": resp.get("tick", 0),
        }

        return self._build_stacked_obs(), reward, done, truncated, info

    def _build_stacked_obs(self) -> np.ndarray:
        """Stack last K observations for temporal context."""
        while len(self._state_buffer) < HISTORY_LEN:
            self._state_buffer.appendleft(np.zeros(OBS_SIZE, dtype=np.float32))

        stacked = np.concatenate(list(self._state_buffer))
        return stacked.astype(np.float32)

    def render(self):
        if self.render_mode == "human":
            print(
                f"Tick: {self._tick}, Episode Reward: {self._episode_reward:.4f}")
