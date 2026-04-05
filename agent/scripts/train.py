"""Main training script for the CloudSentinel RL agent.

Usage:
    py -m scripts.train --difficulty SIMPLE --timesteps 500000
    py -m scripts.train --eval --model-path checkpoints/dqn_best
"""

import argparse
import logging
import sys

from src.agents.dqn_agent import DQNTrainer
from src.env.cloud_sentinel_env import CloudSentinelEnv
from src.utils.safe_execution import SafeExecutionWrapper

# Configure logging for self-debug system
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("train")


def run_training(args):
    """Core training logic — wrapped by SafeExecutionWrapper for auto-recovery."""
    # Create environment
    env = CloudSentinelEnv(
        api_url=args.api_url,
        difficulty=args.difficulty,
        render_mode="human" if args.render else None,
    )

    # Wrap in vector env for SB3
    from stable_baselines3.common.vec_env import DummyVecEnv
    vec_env = DummyVecEnv([lambda: env])

    # Create trainer
    trainer = DQNTrainer(
        env=vec_env,
        checkpoint_dir="checkpoints",
        log_dir="logs",
        total_timesteps=args.timesteps,
    )

    # Train
    print("\nStarting training...")
    print("-" * 60)

    model = trainer.train()

    # Save final model
    trainer.save("dqn_final")

    # Evaluate
    print("\n" + "-" * 60)
    print("Final Evaluation:")
    trainer.evaluate(n_eval_episodes=10)


def main():
    parser = argparse.ArgumentParser(
        description="Train CloudSentinel RL Agent")
    parser.add_argument(
        "--episodes", type=int, default=100,
        help="Number of training episodes (used if --timesteps not set)",
    )
    parser.add_argument(
        "--timesteps", type=int, default=500_000,
        help="Total training timesteps",
    )
    parser.add_argument(
        "--difficulty", type=str, default="SIMPLE",
        choices=["SIMPLE", "MEDIUM", "COMPLEX", "ADVERSARIAL"],
        help="Difficulty level for training",
    )
    parser.add_argument(
        "--api-url", type=str, default="http://localhost:8080",
        help="URL of the Spring Boot simulation API",
    )
    parser.add_argument(
        "--eval", action="store_true",
        help="Run evaluation instead of training",
    )
    parser.add_argument(
        "--model-path", type=str, default=None,
        help="Path to a saved model for evaluation or continued training",
    )
    parser.add_argument(
        "--render", action="store_true",
        help="Render episode progress to console",
    )
    parser.add_argument(
        "--no-auto-fix", action="store_true",
        help="Disable automatic error recovery",
    )

    args = parser.parse_args()

    print("=" * 60)
    print("CloudSentinel AI - RL Agent Training")
    print("=" * 60)
    print(f"Difficulty: {args.difficulty}")
    print(f"API URL: {args.api_url}")
    print(f"Timesteps: {args.timesteps:,}")
    print(f"Auto-fix: {'disabled' if args.no_auto_fix else 'enabled'}")
    print("=" * 60)

    if args.eval:
        if args.model_path is None:
            print("Error: --model-path is required for evaluation")
            sys.exit(1)

        print(f"\nEvaluating model: {args.model_path}")
        from stable_baselines3 import DQN
        from stable_baselines3.common.vec_env import DummyVecEnv
        env = CloudSentinelEnv(api_url=args.api_url,
                               difficulty=args.difficulty)
        vec_env = DummyVecEnv([lambda: env])
        model = DQN.load(args.model_path, env=vec_env)
        from stable_baselines3.common.evaluation import evaluate_policy
        mean_reward, std_reward = evaluate_policy(
            model, vec_env, n_eval_episodes=10)
        print(f"\nEvaluation Result: {mean_reward:.4f} +/- {std_reward:.4f}")
        return

    # Wrap training in self-debugging execution
    wrapper = SafeExecutionWrapper(
        max_retries=3,
        retry_delay=3.0,
        enable_auto_fix=not args.no_auto_fix,
    )

    try:
        wrapper.execute(run_training, args)
    except Exception as e:
        logger.error(f"Training failed after all retries: {e}")
        wrapper.print_summary()
        sys.exit(1)
    else:
        wrapper.print_summary()
        logger.info("Training completed successfully!")


if __name__ == "__main__":
    main()
