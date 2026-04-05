package com.cloudsentinel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request to apply an action in the simulation.
 */
public class ActionRequestDTO {
    @Min(0)
    @Max(8)
    private int action;

    public ActionRequestDTO() {}

    public ActionRequestDTO(int action) {
        this.action = action;
    }

    public int getAction() { return action; }
    public void setAction(int action) { this.action = action; }
}
