
package com.agentgate.paypal;

public class PayPalOrderResult {

    private final String orderId;
    private final String approvalUrl;

    public PayPalOrderResult(String orderId, String approvalUrl) {
        this.orderId = orderId;
        this.approvalUrl = approvalUrl;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getApprovalUrl() {
        return approvalUrl;
    }
}

