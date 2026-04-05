package com.cloudsentinel.detector.model;

/**
 * Severity levels for detected anomalies.
 */
public enum SeverityLevel {
    INFO(0, "Informational - no action required"),
    LOW(1, "Minor deviation, monitor closely"),
    MEDIUM(2, "Moderate issue, should investigate"),
    HIGH(3, "Serious issue, requires immediate action"),
    CRITICAL(4, "Critical failure, emergency response needed");

    private final int level;
    private final String description;

    SeverityLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() { return level; }
    public String getDescription() { return description; }
}
