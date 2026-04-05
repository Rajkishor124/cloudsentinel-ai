"""HTTP client for communicating with the Spring Boot simulation API."""

import json
from typing import Any

import numpy as np
import requests


def _numpy_to_python(val: Any) -> Any:
    """Convert NumPy types to native Python types for JSON serialization."""
    if isinstance(val, np.integer):
        return int(val)
    if isinstance(val, np.floating):
        return float(val)
    if isinstance(val, np.ndarray):
        return val.tolist()
    if isinstance(val, np.bool_):
        return bool(val)
    return val


class _NumpyEncoder(json.JSONEncoder):
    """JSON encoder that handles NumPy types."""

    def default(self, obj: Any) -> Any:
        return _numpy_to_python(obj)


class SimulatorAPIClient:
    """Client for the CloudSentinel simulation REST API."""

    def __init__(self, base_url: str = "http://localhost:8080", timeout: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def reset(self, difficulty: str = "SIMPLE") -> dict:
        """Reset the simulation environment."""
        resp = self.session.post(
            f"{self.base_url}/api/v1/simulation/reset",
            params={"difficulty": difficulty},
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def step(self, action: int) -> dict:
        """Apply an action and advance the simulation."""
        resp = self.session.post(
            f"{self.base_url}/api/v1/simulation/step",
            data=json.dumps(
                {"action": _numpy_to_python(action)}, cls=_NumpyEncoder),
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def get_state(self) -> dict:
        """Get current state without advancing."""
        resp = self.session.get(
            f"{self.base_url}/api/v1/simulation/state",
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def get_topology(self) -> dict:
        """Get the service dependency topology."""
        resp = self.session.get(
            f"{self.base_url}/api/v1/simulation/topology",
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def get_metadata(self) -> dict:
        """Get environment metadata (action count, names, etc.)."""
        resp = self.session.get(
            f"{self.base_url}/api/v1/simulation/metadata",
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def inject_failure(self, mode: str = "CPU_SPIKE") -> dict:
        """Manually inject a failure for testing."""
        resp = self.session.post(
            f"{self.base_url}/api/v1/simulation/inject-failure",
            params={"mode": mode},
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()
