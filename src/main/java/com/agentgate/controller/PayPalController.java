
package com.agentgate.controller;

import com.agentgate.entity.PaymentIntent;
import com.agentgate.paypal.PayPalOrderResult;
import com.agentgate.paypal.PayPalService;
import com.agentgate.service.PaymentIntentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/paypal")
public class PayPalController {

    private final PayPalService payPalService;
    private final PaymentIntentService paymentIntentService;

    public PayPalController(
            PayPalService payPalService,
            PaymentIntentService paymentIntentService) {
        this.payPalService = payPalService;
        this.paymentIntentService = paymentIntentService;
    }

    // Create a PayPal Sandbox test order
    @PostMapping("/test-order")
    public PayPalOrderResult createTestOrder() {
        return payPalService.createOrder(
                "USD",
                "10.00",
                "AgentGate PayPal Sandbox Test"
        );
    }

    // Capture payment and update the AgentGate database
    @GetMapping("/return")
    public Object handleReturn(
            @RequestParam(required = false) String token,
            @RequestParam(name = "PayerID", required = false)
            String payerId) {

        if (token == null || token.isBlank()) {
            return "PayPal returned without an order token.";
        }

        try {
            PaymentIntent payment =
                    paymentIntentService.capturePayment(token);

            return payment;

        } catch (Exception exception) {
            return "Payment capture failed for order "
                    + token + ": " + exception.getMessage();
        }
    }

    // Handle buyer cancelling checkout
    @GetMapping("/cancel")
    public String handleCancel(
            @RequestParam(required = false) String token) {

        return "PayPal checkout was cancelled. Order ID: "
                + (token == null ? "not provided" : token);
    }
    @GetMapping("/order/{orderId}")
    public Object getOrderDetails(@PathVariable String orderId) {
        return payPalService.getOrderDetails(orderId);
    }
}

