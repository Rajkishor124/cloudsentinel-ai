"""DQN agent with Dueling architecture and optimized training configuration.

Uses Stable-Baselines3 for the training loop with custom callbacks.
"""

import os
from pathlib import Path

import numpy as np
from stable_baselines3 import DQN
from stable_baselines3.common.callbacks import BaseCallback, EvalCallback, CheckpointCallback
from stable_baselines3.common.vec_env import DummyVecEnv
from stable_baselines3.dqn.policies import DQNPolicy
from stable_baselines3.common.evaluation import evaluate_policy


class DQNTrainer:
    """Manages DQN training lifecycle with optimized hyperparameters."""

    def __init__(
        self,
        env,
        checkpoint_dir: str = "checkpoints",
        log_dir: str = "logs",
        total_timesteps: int = 500_000,
        learning_rate: float = 1e-4,
        buffer_size: int = 200_000,       # Increased: more diverse experience
        batch_size: int = 128,             # Increased: better gradient estimates
        gamma: float = 0.99,
        exploration_fraction: float = 0.15,  # Increased: more exploration
        exploration_final_eps: float = 0.02,  # Decreased: less random at end
        train_freq: int = 4,
        gradient_steps: int = 8,           # Increased: more updates per step
        target_update_interval: int = 2000,  # Increased: more stable targets
        learning_starts: int = 10_000,     # Wait before training
    ):
        self.env = env
        self.checkpoint_dir = Path(checkpoint_dir)
        self.log_dir = Path(log_dir)
        self.total_timesteps = total_timesteps

        # Ensure directories exist
        self.checkpoint_dir.mkdir(parents=True, exist_ok=True)
        self.log_dir.mkdir(parents=True, exist_ok=True)

        # Create DQN model with optimized hyperparameters
        self.model = DQN(
            "MlpPolicy",
            env,
            learning_rate=learning_rate,
            buffer_size=buffer_size,
            batch_size=batch_size,
            gamma=gamma,
            exploration_fraction=exploration_fraction,
            exploration_final_eps=exploration_final_eps,
            exploration_initial_eps=1.0,
            train_freq=train_freq,
            gradient_steps=gradient_steps,
            target_update_interval=target_update_interval,
            learning_starts=learning_starts,
            verbose=1,
            tensorboard_log=str(self.log_dir),
        )

    def train(self) -> DQN:
        """Run the training loop with callbacks."""
        # Checkpoint every 50k steps
        checkpoint_callback = CheckpointCallback(
            save_freq=50_000,
            save_path=str(self.checkpoint_dir),
            name_prefix="dqn_checkpoint",
            verbose=1,
        )

        # Training progress callback
        progress_callback = TrainingProgressCallback()

        # Evaluation callback - runs every 25k steps
        eval_callback = EvalCallback(
            self.env,
            best_model_save_path=str(self.checkpoint_dir / "best_model"),
            log_path=str(self.log_dir / "eval"),
            eval_freq=25_000,
            n_eval_episodes=5,
            deterministic=True,
            verbose=1,
        )

        self.model.learn(
            total_timesteps=self.total_timesteps,
            callback=[checkpoint_callback, progress_callback, eval_callback],
            reset_num_timesteps=False,
        )

        return self.model

    def save(self, path: str):
        """Save the trained model."""
        save_path = self.checkpoint_dir / path
        self.model.save(str(save_path))
        print(f"Model saved to {save_path}")

    def load(cls, path: str, env):
        """Load a trained model."""
        model = DQN.load(str(path), env=env)
        return model

    def evaluate(self, n_eval_episodes: int = 10) -> tuple[float, float]:
        """Evaluate the current policy."""
        mean_reward, std_reward = evaluate_policy(
            self.model, self.env, n_eval_episodes=n_eval_episodes
        )
        print(f"Mean Reward: {mean_reward:.4f} (+/- {std_reward:.4f})")
        return mean_reward, std_reward


class TrainingProgressCallback(BaseCallback):
    """Custom callback for logging training progress with comprehensive metrics."""

    def __init__(self, log_interval: int = 1000):
        super().__init__()
        self.log_interval = log_interval
        self.episode_rewards: list[float] = []
        self.current_episode_reward = 0.0
        self.best_mean_reward = -float("inf")
        self.episode_count = 0
        self.last_log_step = 0

    def _on_step(self) -> bool:
        # Accumulate episode reward from info
        if len(self.locals.get("infos", [])) > 0:
            info = self.locals["infos"][0]
            if "cumulative_reward" in info:
                self.current_episode_reward = info["cumulative_reward"]

            # Track episode completion
            if info.get("done", False):
                self.episode_count += 1
                self.episode_rewards.append(self.current_episode_reward)
                print(f"\n[Episode {self.episode_count}] Reward: {self.current_episode_reward:.4f} | "
                      f"Steps: {self.n_calls:,} | "
                      f"SLO: {info.get('slo_score', 0.0):.3f} | "
                      f"Failure: {info.get('failure_mode', 'N/A')}")

        # Log periodically
        if self.n_calls % self.log_interval == 0 and self.n_calls != self.last_log_step:
            self.last_log_step = self.n_calls
            self.logger.record("training/n_calls", self.n_calls)
            self.logger.record("training/n_episodes", self.episode_count)
            if self.current_episode_reward != 0:
                self.logger.record("episode/current_reward",
                                   self.current_episode_reward)
            if len(self.episode_rewards) > 0:
                recent = self.episode_rewards[-10:]
                mean_recent = np.mean(recent)
                self.logger.record("episode/mean_reward_10ep", mean_recent)
                print(f"\n[Training] Step {self.n_calls:,} | "
                      f"Episodes: {self.episode_count} | "
                      f"Mean Reward (last 10): {mean_recent:.4f}")
            self.logger.dump(self.n_calls)

        return True

    def _on_rollout_end(self):
        """Called at the end of each rollout."""
        if len(self.episode_rewards) >= 10:
            recent = self.episode_rewards[-10:]
            mean_reward = np.mean(recent)
            if mean_reward > self.best_mean_reward:
                self.best_mean_reward = mean_reward
                self.logger.record(
                    "training/best_mean_reward_10ep", mean_reward)
                print(f"\n[New Best] Mean Reward (last 10): {mean_reward:.4f}")
