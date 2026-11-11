
package com.agentgate.controller;

import com.agentgate.dto.CreatePaymentRequest;
import com.agentgate.entity.PaymentIntent;
import com.agentgate.service.PaymentIntentService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentIntentService paymentIntentService;

    // Constructor injection
    public PaymentController(
            PaymentIntentService paymentIntentService) {
        this.paymentIntentService = paymentIntentService;
    }

    // GET ALL PAYMENT REQUESTS
    // Used by the AgentGate dashboard
    @GetMapping
    public List<PaymentIntent> getAllPayments() {
        return paymentIntentService.getAllPayments();
    }

    // CREATE A NEW PAYMENT REQUEST
    @PostMapping
    public PaymentIntent createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {

        return paymentIntentService.createPayment(request);
    }

    // APPROVE AN ESCALATED PAYMENT
    @PostMapping("/{id}/approve")
    public PaymentIntent approvePayment(
            @PathVariable Long id) {

        return paymentIntentService.approvePayment(id);
    }

    // REJECT AN ESCALATED PAYMENT
    @PostMapping("/{id}/reject")
    public PaymentIntent rejectPayment(
            @PathVariable Long id) {

        return paymentIntentService.rejectPayment(id);
    }
}
