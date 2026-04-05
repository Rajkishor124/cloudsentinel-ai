"""ErrorClassifier — maps runtime exceptions to fixable error categories.

Each error type has an associated fix strategy in FixEngine.
Unknown errors are logged and passed through for manual resolution.
"""

import re
from dataclasses import dataclass
from enum import Enum


class ErrorCategory(Enum):
    SERIALIZATION_ERROR = "SERIALIZATION_ERROR"
    OBSERVATION_SHAPE_ERROR = "OBSERVATION_SHAPE_ERROR"
    IMPORT_ERROR = "IMPORT_ERROR"
    API_CONNECTION_ERROR = "API_CONNECTION_ERROR"
    MODEL_LOAD_ERROR = "MODEL_LOAD_ERROR"
    ENV_RESET_ERROR = "ENV_RESET_ERROR"
    UNKNOWN = "UNKNOWN"


@dataclass
class ClassificationResult:
    category: ErrorCategory
    confidence: float
    description: str
    suggested_fix: str
    trace_excerpt: str


class ErrorClassifier:
    """Classifies exceptions into actionable categories with fix suggestions."""

    # Pattern -> (category, description, suggested_fix)
    PATTERNS = [
        # NumPy type serialization
        (
            re.compile(
                r"Object of type (int64|float64|bool_).*not JSON serializable", re.IGNORECASE),
            ErrorCategory.SERIALIZATION_ERROR,
            "NumPy type in JSON payload",
            "Convert numpy types to native Python types via _numpy_to_python() before HTTP send",
        ),
        # Array shape mismatch
        (
            re.compile(
                r"could not broadcast input array from shape \((\d+),\) into shape \((\d+),\)", re.IGNORECASE),
            ErrorCategory.OBSERVATION_SHAPE_ERROR,
            "Observation size mismatch between env and observation_space",
            "Ensure backend returns fixed-size observation and observation_space matches stacked output",
        ),
        (
            re.compile(r"shape.*mismatch", re.IGNORECASE),
            ErrorCategory.OBSERVATION_SHAPE_ERROR,
            "Shape mismatch in observation",
            "Verify OBS_SIZE constant matches backend ObservationDTO.OBS_SIZE",
        ),
        # Module import failures
        (
            re.compile(
                r"ModuleNotFoundError: No module named '(\w+)'", re.IGNORECASE),
            ErrorCategory.IMPORT_ERROR,
            "Module import failed",
            "Check sys.path, verify package installed, or use module execution (py -m scripts.X)",
        ),
        # Connection refused
        (
            re.compile(
                r"ConnectionRefusedError|Connection refused|Max retries exceeded", re.IGNORECASE),
            ErrorCategory.API_CONNECTION_ERROR,
            "Cannot connect to Spring Boot backend",
            "Ensure backend is running: ./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev",
        ),
        # HTTP errors from API
        (
            re.compile(
                r"HTTPError.*500|HTTPError.*404|HTTPError.*401", re.IGNORECASE),
            ErrorCategory.API_CONNECTION_ERROR,
            "Backend returned HTTP error",
            "Check backend logs, verify API endpoint exists, check auth token",
        ),
        # Keyword-based catch-all
        (
            re.compile(r"multiple values for keyword", re.IGNORECASE),
            ErrorCategory.MODEL_LOAD_ERROR,
            "Duplicate keyword argument in SB3 constructor",
            "Remove duplicate kwargs — SB3 passes some buffer args internally",
        ),
    ]

    @classmethod
    def classify(cls, error: Exception, traceback_str: str = "") -> ClassificationResult:
        """Classify an exception into a known error category."""
        error_repr = f"{type(error).__name__}: {error}"
        search_text = f"{error_repr}\n{traceback_str}"

        for pattern, category, description, suggested_fix in cls.PATTERNS:
            match = pattern.search(search_text)
            if match:
                return ClassificationResult(
                    category=category,
                    confidence=0.9,
                    description=description,
                    suggested_fix=suggested_fix,
                    trace_excerpt=error_repr[:200],
                )

        return ClassificationResult(
            category=ErrorCategory.UNKNOWN,
            confidence=0.5,
            description=f"Unclassified error: {type(error).__name__}",
            suggested_fix="Review traceback and apply manual fix",
            trace_excerpt=error_repr[:200],
        )
