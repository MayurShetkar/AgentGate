package com.agentgate.policy;

public class PolicyResult {

    private final PolicyDecision decision;
    private final String reason;

    public PolicyResult(PolicyDecision decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public PolicyDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }
}
