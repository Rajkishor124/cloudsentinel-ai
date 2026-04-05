"""Self-Healing Cloud AI System - Autonomous Operation Client.

This client connects to the Spring Boot backend and runs the self-healing loop:
1. Monitor: Continuously scan for anomalies
2. Decide: Hybrid decision engine (RL agent + rule-based safety)
3. Execute: Apply remediation actions
4. Learn: Record feedback and build healing memory

Usage:
    py -m scripts.self_healing --api-url http://localhost:8080 --cycles 100
    py -m scripts.self_healing --monitor-only
"""

import argparse
import json
import logging
import sys
import time
from datetime import datetime
from pathlib import Path

import numpy as np
import requests

logger = logging.getLogger("self-healing")


class SelfHealingClient:
    """Autonomous self-healing client for CloudSentinel AI."""

    def __init__(self, api_url: str, rl_model_path: str = None):
        self.api_url = api_url
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

        # RL model (optional - falls back to rule-based if not provided)
        self.rl_model = None
        if rl_model_path:
            try:
                from stable_baselines3 import DQN
                from stable_baselines3.common.vec_env import DummyVecEnv
                from src.env.cloud_sentinel_env import CloudSentinelEnv

                env = CloudSentinelEnv(api_url=api_url)
                vec_env = DummyVecEnv([lambda: env])
                self.rl_model = DQN.load(rl_model_path, env=vec_env)
                logger.info("RL model loaded: %s", rl_model_path)
            except Exception as e:
                logger.warning(
                    "Failed to load RL model, using rule-based only: %s", e)

        # Tracking
        self.cycle_count = 0
        self.successful_cycles = 0
        self.total_rewards = 0.0
        self.healing_log = []

    def reset_environment(self, difficulty: str = "SIMPLE") -> dict:
        """Reset the simulation environment."""
        resp = self.session.post(
            f"{self.api_url}/api/v1/simulation/reset",
            params={"difficulty": difficulty},
        )
        resp.raise_for_status()
        return resp.json()

    def get_state(self) -> dict:
        """Get current system state."""
        resp = self.session.get(f"{self.api_url}/api/v1/simulation/state")
        resp.raise_for_status()
        return resp.json()

    def get_alerts(self) -> list:
        """Get current anomaly alerts."""
        resp = self.session.get(f"{self.api_url}/api/v1/healing/alerts")
        if resp.status_code == 200:
            return resp.json()
        return []

    def get_healing_status(self) -> dict:
        """Get orchestrator status."""
        resp = self.session.get(f"{self.api_url}/api/v1/healing/status")
        if resp.status_code == 200:
            return resp.json()
        return {}

    def execute_healing_cycle(self, rl_action: int = 0) -> dict:
        """Execute one self-healing cycle via the orchestrator."""
        resp = self.session.post(
            f"{self.api_url}/api/v1/healing/cycle",
            params={"rlAction": rl_action},
        )
        if resp.status_code == 200:
            return resp.json()
        return {"error": resp.text}

    def predict_rl_action(self, observation) -> int:
        """Get action prediction from RL model."""
        if self.rl_model is None:
            return 0  # DO_NOTHING

        try:
            action, _ = self.rl_model.predict(observation, deterministic=True)
            return int(action)
        except Exception as e:
            logger.warning("RL prediction failed: %s", e)
            return 0

    def run_healing_loop(self, num_cycles: int = 100, difficulty: str = "SIMPLE"):
        """Run the autonomous self-healing loop."""
        logger.info("=" * 60)
        logger.info("Starting Self-Healing Loop")
        logger.info("Cycles: %d, Difficulty: %s", num_cycles, difficulty)
        logger.info("=" * 60)

        # Reset environment
        self.reset_environment(difficulty)

        for cycle in range(num_cycles):
            self.cycle_count += 1
            cycle_start = time.time()

            try:
                # Step 1: Monitor
                alerts = self.get_alerts()
                alert_count = len(alerts)

                # Step 2: Get current state
                state = self.get_state()
                observation = np.array(
                    state.get("observation", []), dtype=np.float32)

                # Step 3: Decide
                if self.rl_model is not None and len(observation) > 0:
                    rl_action = self.predict_rl_action(observation)
                    decision_source = "HYBRID"
                else:
                    rl_action = 0
                    decision_source = "RULE"

                # Step 4: Execute healing cycle
                result = self.execute_healing_cycle(rl_action)

                # Step 5: Record results
                cycle_duration = time.time() - cycle_start
                effective = result.get("successful", False)
                reward = result.get("feedback", {}).get("reward", 0.0)

                if effective:
                    self.successful_cycles += 1
                self.total_rewards += reward

                # Log cycle
                log_entry = {
                    "cycle": self.cycle_count,
                    "timestamp": datetime.now().isoformat(),
                    "alerts_detected": alert_count,
                    "decision_source": decision_source,
                    "action_taken": result.get("decision", {}).get("recommendedAction", "UNKNOWN"),
                    "effective": effective,
                    "reward": reward,
                    "duration_ms": int(cycle_duration * 1000),
                }
                self.healing_log.append(log_entry)

                # Print summary
                status_icon = "[OK]" if effective else "[!!]"
                print(f"\n{status_icon} Cycle #{self.cycle_count:3d} | "
                      f"Alerts: {alert_count:2d} | "
                      f"Action: {log_entry['action_taken']:25s} | "
                      f"Reward: {reward:6.3f} | "
                      f"Duration: {cycle_duration*1000:5.0f}ms")

                if alert_count > 0:
                    print(f"     Detected: {alerts[0].get('anomalyType', 'UNKNOWN')} on "
                          f"{alerts[0].get('serviceName', 'unknown')}")

                # Brief pause
                time.sleep(0.1)

            except Exception as e:
                logger.error("Cycle #%d failed: %s", self.cycle_count, e)
                print(f"\n[ERROR] Cycle #{self.cycle_count} failed: {e}")

        # Print final summary
        self.print_summary()

    def print_summary(self):
        """Print healing session summary."""
        print("\n" + "=" * 60)
        print("SELF-HEALING SESSION SUMMARY")
        print("=" * 60)
        print(f"Total Cycles:        {self.cycle_count}")
        print(f"Successful Cycles:   {self.successful_cycles}")
        print(
            f"Success Rate:        {(self.successful_cycles / max(self.cycle_count, 1) * 100):.1f}%")
        print(f"Total Reward:        {self.total_rewards:.3f}")
        print(
            f"Average Reward:      {(self.total_rewards / max(self.cycle_count, 1)):.3f}")
        print("=" * 60)

        # Action distribution
        if self.healing_log:
            action_counts = {}
            for entry in self.healing_log:
                action = entry["action_taken"]
                action_counts[action] = action_counts.get(action, 0) + 1

            print("\nAction Distribution:")
            for action, count in sorted(action_counts.items(), key=lambda x: -x[1]):
                pct = count / self.cycle_count * 100
                print(f"  {action:25s}: {count:3d} ({pct:5.1f}%)")

        # Save log
        log_path = Path("logs/healing_session.json")
        log_path.parent.mkdir(parents=True, exist_ok=True)
        with open(log_path, "w") as f:
            json.dump({
                "summary": {
                    "total_cycles": self.cycle_count,
                    "successful_cycles": self.successful_cycles,
                    "success_rate": self.successful_cycles / max(self.cycle_count, 1),
                    "total_reward": self.total_rewards,
                },
                "cycles": self.healing_log,
            }, f, indent=2)
        print(f"\nSession log saved to: {log_path}")


def main():
    parser = argparse.ArgumentParser(
        description="CloudSentinel Self-Healing AI")
    parser.add_argument("--api-url", default="http://localhost:8080",
                        help="Backend API URL")
    parser.add_argument("--cycles", type=int, default=100,
                        help="Number of healing cycles to run")
    parser.add_argument("--difficulty", default="SIMPLE",
                        choices=["SIMPLE", "MEDIUM", "COMPLEX", "ADVERSARIAL"],
                        help="Difficulty level")
    parser.add_argument("--rl-model", type=str, default=None,
                        help="Path to trained RL model (.zip)")
    parser.add_argument("--monitor-only", action="store_true",
                        help="Only monitor, don't execute actions")
    parser.add_argument("--log-level", default="INFO",
                        choices=["DEBUG", "INFO", "WARNING", "ERROR"])

    args = parser.parse_args()

    logging.basicConfig(
        level=getattr(logging, args.log_level),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    client = SelfHealingClient(args.api_url, args.rl_model)

    if args.monitor_only:
        print("Monitor-only mode. Press Ctrl+C to stop.")
        try:
            while True:
                alerts = client.get_alerts()
                status = client.get_healing_status()
                print(f"\n[{datetime.now().strftime('%H:%M:%S')}] "
                      f"Alerts: {len(alerts)}, "
                      f"Healing cycles: {status.get('totalCycles', 0)}")
                time.sleep(5)
        except KeyboardInterrupt:
            print("\nMonitoring stopped.")
    else:
        client.run_healing_loop(args.cycles, args.difficulty)


if __name__ == "__main__":
    main()
