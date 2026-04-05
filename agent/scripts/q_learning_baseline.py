"""Q-Learning baseline test for the CloudSentinel environment.

Run this to verify the reward signal before training full DQN.
Usage: py -m scripts.q_learning_baseline
"""

import numpy as np
from collections import defaultdict

from src.env.cloud_sentinel_env import CloudSentinelEnv


def discretize_obs(obs: np.ndarray, bins: int = 3) -> tuple:
    """Discretize continuous observation for tabular Q-learning."""
    # Only use the most recent observation (last 4 values), not the full stacked obs
    recent = obs[-4:]  # cpu, memory, error_rate, latency of primary service
    return tuple(np.digitize(recent, np.linspace(0, 1, bins)))


def run_q_learning(episodes: int = 100, epsilon: float = 0.3):
    """Run tabular Q-Learning baseline."""
    env = CloudSentinelEnv(
        api_url="http://localhost:8080", difficulty="SIMPLE")

    # Q-table
    Q: dict[tuple, np.ndarray] = defaultdict(lambda: np.zeros(9))

    # Hyperparameters
    alpha = 0.1
    gamma = 0.99

    rewards_per_episode = []

    print(f"Running {episodes} episodes of tabular Q-Learning...")
    print("-" * 60)

    for ep in range(episodes):
        obs, _ = env.reset()
        state = discretize_obs(obs)
        total_reward = 0.0

        for step in range(200):
            # Epsilon-greedy action selection
            if np.random.random() < epsilon:
                action = env.action_space.sample()
            else:
                action = int(np.argmax(Q[state]))

            next_obs, reward, done, truncated, info = env.step(action)
            next_state = discretize_obs(next_obs)

            # Q-learning update
            best_next = np.max(Q[next_state])
            Q[state][action] += alpha * \
                (reward + gamma * best_next - Q[state][action])

            state = next_state
            total_reward += reward

            if done:
                break

        rewards_per_episode.append(total_reward)

        # Decay epsilon
        epsilon = max(0.05, epsilon * 0.99)

        if (ep + 1) % 10 == 0:
            avg = np.mean(rewards_per_episode[-10:])
            print(
                f"Episode {ep+1:4d}: avg_reward={avg:8.4f}, epsilon={epsilon:.3f}")

    # Summary
    print("\n" + "=" * 60)
    print("Q-Learning Baseline Results:")
    print(f"  First 10 episodes avg:  {np.mean(rewards_per_episode[:10]):.4f}")
    print(
        f"  Last 10 episodes avg:   {np.mean(rewards_per_episode[-10:]):.4f}")
    print(f"  Best episode reward:    {max(rewards_per_episode):.4f}")
    print(f"  Worst episode reward:   {min(rewards_per_episode):.4f}")
    print("=" * 60)

    if np.mean(rewards_per_episode[-10:]) > np.mean(rewards_per_episode[:10]):
        print("SUCCESS: Agent is learning (reward increasing)")
    else:
        print("WARNING: Agent may not be learning. Check reward function.")

    return Q, rewards_per_episode


def main():
    """CLI entry point for Q-Learning baseline."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Q-Learning baseline for CloudSentinel")
    parser.add_argument(
        "--episodes", type=int, default=100,
        help="Number of training episodes")
    parser.add_argument(
        "--api-url", type=str, default="http://localhost:8080",
        help="URL of the Spring Boot simulation API")
    args = parser.parse_args()

    run_q_learning(episodes=args.episodes)


if __name__ == "__main__":
    main()
