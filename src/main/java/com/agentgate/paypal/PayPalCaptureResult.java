
package com.agentgate.paypal;

public record PayPalCaptureResult(
        String status,
        String captureId
) {

}


