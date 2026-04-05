package com.cloudsentinel.action.model;

/**
 * Defines all available actions the RL agent can take.
 * Each action has a cost multiplier, cooldown, and precondition requirements.
 */
public enum ActionType {
    DO_NOTHING(0,             0.0,  0,   new String[0]),
    RESTART_SERVICE(1,        0.05, 60,  new String[0]),
    SCALE_UP(2,               0.10, 30,  new String[0]),
    SCALE_DOWN(3,             0.0,  20,  new String[]{"cpu_below_40_for_5_ticks"}),
    CLEAR_CACHE(4,            0.01, 0,   new String[0]),
    RESTART_DATABASE(5,       0.15, 120, new String[]{"db_not_recently_restarted"}),
    REROUTE_TRAFFIC(6,        0.08, 10,  new String[]{"at_least_2_healthy_replicas"}),
    ROLLBACK_DEPLOYMENT(7,    0.12, 90,  new String[]{"recent_deploy_flag"}),
    TRIGGER_CIRCUIT_BREAKER(8, 0.02, 5,  new String[0]);

    private final int code;
    private final double costMultiplier;
    private final int cooldownTicks;
    private final String[] preconditions;

    ActionType(int code, double costMultiplier, int cooldownTicks, String[] preconditions) {
        this.code = code;
        this.costMultiplier = costMultiplier;
        this.cooldownTicks = cooldownTicks;
        this.preconditions = preconditions;
    }

    public int getCode() { return code; }
    public double getCostMultiplier() { return costMultiplier; }
    public int getCooldownTicks() { return cooldownTicks; }
    public String[] getPreconditions() { return preconditions; }

    public static ActionType fromCode(int code) {
        for (ActionType action : values()) {
            if (action.code == code) return action;
        }
        throw new IllegalArgumentException("Unknown action code: " + code);
    }

    public static int size() { return values().length; }
}
