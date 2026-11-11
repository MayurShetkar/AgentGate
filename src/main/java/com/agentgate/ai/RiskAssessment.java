package com.agentgate.ai;

public class RiskAssessment {

    private final RiskDecision decision;
    private final String reason;

    public RiskAssessment(RiskDecision decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public RiskDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }
}
