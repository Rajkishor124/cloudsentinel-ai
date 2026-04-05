"""Comprehensive evaluation script for trained models.

Evaluates across multiple episodes, tracks detailed metrics,
and generates a summary report.

Usage:
    py -m scripts.evaluate checkpoints/dqn_final.zip
    py -m scripts.evaluate checkpoints/dqn_final.zip --episodes 50 --difficulty MEDIUM
"""

import argparse
import json
from pathlib import Path

import numpy as np
from stable_baselines3 import DQN
from stable_baselines3.common.evaluation import evaluate_policy
from stable_baselines3.common.vec_env import DummyVecEnv
from src.env.cloud_sentinel_env import CloudSentinelEnv


def run_detailed_evaluation(model, env, n_episodes: int) -> dict:
    """Run detailed evaluation with per-episode metrics."""
    results = {
        "episode_rewards": [],
        "episode_lengths": [],
        "slo_scores": [],
        "failure_modes": [],
        "action_distribution": {},
        "cumulative_costs": [],
    }

    for ep in range(n_episodes):
        obs, info = env.reset()
        episode_reward = 0.0
        done = False
        actions_taken = []

        while not done:
            action, _ = model.predict(obs, deterministic=True)
            obs, reward, terminated, truncated, info = env.step(int(action))
            episode_reward += reward
            done = terminated or truncated
            actions_taken.append(int(action))

            if done:
                results["episode_rewards"].append(episode_reward)
                results["episode_lengths"].append(info.get("tick", 0))
                results["slo_scores"].append(info.get("slo_score", 0.0))
                results["failure_modes"].append(
                    info.get("failure_mode", "N/A"))
                results["cumulative_costs"].append(
                    info.get("cumulative_cost", 0.0))

        # Track action distribution
        for action in actions_taken:
            action_name = f"action_{action}"
            results["action_distribution"][action_name] = \
                results["action_distribution"].get(action_name, 0) + 1

    # Convert to numpy for stats
    rewards = np.array(results["episode_rewards"])
    results["summary"] = {
        "n_episodes": n_episodes,
        "mean_reward": float(np.mean(rewards)),
        "std_reward": float(np.std(rewards)),
        "min_reward": float(np.min(rewards)),
        "max_reward": float(np.max(rewards)),
        "median_reward": float(np.median(rewards)),
        "mean_slo": float(np.mean(results["slo_scores"])),
        "mean_episode_length": float(np.mean(results["episode_lengths"])),
        "total_actions": sum(results["action_distribution"].values()),
    }

    return results


def print_report(results: dict):
    """Print a formatted evaluation report."""
    summary = results["summary"]
    print("\n" + "=" * 60)
    print("EVALUATION REPORT")
    print("=" * 60)
    print(f"Episodes Evaluated: {summary['n_episodes']}")
    print(
        f"Mean Reward:        {summary['mean_reward']:.4f} +/- {summary['std_reward']:.4f}")
    print(f"Median Reward:      {summary['median_reward']:.4f}")
    print(f"Min Reward:         {summary['min_reward']:.4f}")
    print(f"Max Reward:         {summary['max_reward']:.4f}")
    print(f"Mean SLO Score:     {summary['mean_slo']:.4f}")
    print(f"Mean Episode Length:{summary['mean_episode_length']:.1f} ticks")
    print("-" * 60)

    # Action distribution
    print("\nAction Distribution:")
    total = summary["total_actions"]
    for action_name, count in sorted(results["action_distribution"].items()):
        pct = count / total * 100
        print(f"  {action_name:20s}: {count:5d} ({pct:5.1f}%)")

    print("-" * 60)

    # Failure mode breakdown
    print("\nFailure Mode Breakdown:")
    from collections import Counter
    failure_counts = Counter(results["failure_modes"])
    for mode, count in failure_counts.most_common():
        pct = count / summary["n_episodes"] * 100
        print(f"  {mode:25s}: {count:3d} ({pct:5.1f}%)")

    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="Evaluate CloudSentinel RL Agent")
    parser.add_argument("model_path", help="Path to saved model (.zip)")
    parser.add_argument("--api-url", default="http://localhost:8080")
    parser.add_argument("--difficulty", default="SIMPLE",
                        choices=["SIMPLE", "MEDIUM", "COMPLEX", "ADVERSARIAL"])
    parser.add_argument("--episodes", type=int, default=20)
    parser.add_argument("--output-report", type=str, default=None,
                        help="Path to save JSON report")
    args = parser.parse_args()

    print(f"Evaluating model: {args.model_path}")
    print(f"Difficulty: {args.difficulty}, Episodes: {args.episodes}")

    env = CloudSentinelEnv(api_url=args.api_url, difficulty=args.difficulty)
    vec_env = DummyVecEnv([lambda: env])

    model = DQN.load(args.model_path, env=vec_env)

    # Run detailed evaluation
    results = run_detailed_evaluation(model, env, n_episodes=args.episodes)

    # Print report
    print_report(results)

    # Save report if requested
    if args.output_report:
        report_path = Path(args.output_report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        with open(report_path, "w") as f:
            json.dump(results, f, indent=2)
        print(f"\nReport saved to: {report_path}")


if __name__ == "__main__":
    main()
