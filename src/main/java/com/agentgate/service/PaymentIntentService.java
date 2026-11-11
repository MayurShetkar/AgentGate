
package com.agentgate.service;

import com.agentgate.ai.AiRiskReviewer;
import com.agentgate.ai.RiskAssessment;
import com.agentgate.ai.RiskDecision;
import com.agentgate.dto.CreatePaymentRequest;
import com.agentgate.entity.PaymentIntent;
import com.agentgate.paypal.PayPalCaptureResult;
import com.agentgate.paypal.PayPalOrderResult;
import com.agentgate.paypal.PayPalService;
import com.agentgate.policy.PolicyDecision;
import com.agentgate.policy.PolicyEngine;
import com.agentgate.policy.PolicyResult;
import com.agentgate.repository.PaymentIntentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentIntentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final PolicyEngine policyEngine;
    private final AiRiskReviewer aiRiskReviewer;
    private final PayPalService payPalService;

    public PaymentIntentService(
            PaymentIntentRepository paymentIntentRepository,
            PolicyEngine policyEngine,
            AiRiskReviewer aiRiskReviewer,
            PayPalService payPalService) {

        this.paymentIntentRepository = paymentIntentRepository;
        this.policyEngine = policyEngine;
        this.aiRiskReviewer = aiRiskReviewer;
        this.payPalService = payPalService;
    }

    // GET ALL PAYMENTS - USED BY DASHBOARD
    public List<PaymentIntent> getAllPayments() {
        return paymentIntentRepository.findAll();
    }

    // CREATE PAYMENT
    @Transactional
    public PaymentIntent createPayment(CreatePaymentRequest request) {

        PaymentIntent paymentIntent = new PaymentIntent();

        paymentIntent.setAgentId(request.getAgentId());
        paymentIntent.setVendor(request.getVendor());
        paymentIntent.setAmount(request.getAmount());
        paymentIntent.setCurrency(request.getCurrency());
        paymentIntent.setDescription(request.getDescription());
        paymentIntent.setCreatedAt(LocalDateTime.now());

        // Step 1: Evaluate payment using Policy Engine
        PolicyResult policyResult = policyEngine.evaluate(paymentIntent);

        paymentIntent.setPolicyDecision(policyResult.getDecision().name());
        paymentIntent.setPolicyReason(policyResult.getReason());

        // Step 2: Hard BLOCK by policy
        if (policyResult.getDecision() == PolicyDecision.BLOCK) {

            paymentIntent.setStatus("BLOCKED");

            return paymentIntentRepository.save(paymentIntent);
        }

        // Step 3: Automatic approval
        if (policyResult.getDecision() == PolicyDecision.ALLOW) {

            PaymentIntent savedPayment =
                    paymentIntentRepository.save(paymentIntent);

            PayPalOrderResult orderResult =
                    payPalService.createOrder(
                            savedPayment.getCurrency(),
                            savedPayment.getAmount().toString(),
                            savedPayment.getDescription()
                    );

            savedPayment.setPaypalOrderId(orderResult.getOrderId());
            savedPayment.setPaypalApprovalUrl(orderResult.getApprovalUrl());

            // Order created; buyer still needs to complete checkout
            savedPayment.setStatus("APPROVED");

            return paymentIntentRepository.save(savedPayment);
        }

        // Step 4: AI Risk Review for escalated payments
        RiskAssessment riskAssessment = aiRiskReviewer.review(paymentIntent);

        paymentIntent.setAiDecision(riskAssessment.getDecision().name());
        paymentIntent.setAiReason(riskAssessment.getReason());

        if (riskAssessment.getDecision() == RiskDecision.BLOCK) {
            paymentIntent.setStatus("BLOCKED");
        } else {
            paymentIntent.setStatus("ESCALATED");
        }

        return paymentIntentRepository.save(paymentIntent);
    }

    // APPROVE PAYMENT
    @Transactional
    public PaymentIntent approvePayment(Long paymentId) {

        PaymentIntent paymentIntent =
                paymentIntentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Payment not found"
                                )
                        );

        // Only escalated payments can be approved
        if (!"ESCALATED".equals(paymentIntent.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only escalated payments can be approved. Current status: "
                            + paymentIntent.getStatus()
            );
        }

        // AI BLOCK must never be overridden
        if ("BLOCK".equals(paymentIntent.getAiDecision())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment was blocked by AI risk review and cannot be approved"
            );
        }

        // Human approval: create PayPal order
        PayPalOrderResult orderResult =
                payPalService.createOrder(
                        paymentIntent.getCurrency(),
                        paymentIntent.getAmount().toString(),
                        paymentIntent.getDescription()
                );

        paymentIntent.setPaypalOrderId(orderResult.getOrderId());
        paymentIntent.setPaypalApprovalUrl(orderResult.getApprovalUrl());
        paymentIntent.setStatus("APPROVED");

        return paymentIntentRepository.save(paymentIntent);
    }

    // REJECT PAYMENT
    @Transactional
    public PaymentIntent rejectPayment(Long paymentId) {

        PaymentIntent paymentIntent =
                paymentIntentRepository.findById(paymentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Payment not found"
                                )
                        );

        if (!"ESCALATED".equals(paymentIntent.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only escalated payments can be rejected. Current status: "
                            + paymentIntent.getStatus()
            );
        }

        paymentIntent.setStatus("REJECTED");

        return paymentIntentRepository.save(paymentIntent);
    }

    // CAPTURE PAYPAL PAYMENT
    @Transactional
    public PaymentIntent capturePayment(String orderId) {

        if (orderId == null || orderId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PayPal order ID cannot be empty"
            );
        }

        PaymentIntent paymentIntent =
                paymentIntentRepository.findByPaypalOrderId(orderId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No AgentGate payment found for PayPal order: "
                                                + orderId
                                )
                        );

        // Prevent duplicate capture
        if ("PAID".equals(paymentIntent.getStatus())) {
            return paymentIntent;
        }

        if (!"APPROVED".equals(paymentIntent.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment must be APPROVED before capture. Current status: "
                            + paymentIntent.getStatus()
            );
        }

        // Capture payment through PayPal
        PayPalCaptureResult captureResult =
                payPalService.captureOrder(orderId);

        if (captureResult == null ||
                !"COMPLETED".equalsIgnoreCase(captureResult.status())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayPal capture did not complete"
            );
        }

        if (captureResult.captureId() == null ||
                captureResult.captureId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayPal did not return a valid capture ID"
            );
        }

        // Save successful payment details
        paymentIntent.setPaypalCaptureId(captureResult.captureId());
        paymentIntent.setCapturedAt(LocalDateTime.now());
        paymentIntent.setStatus("PAID");

        return paymentIntentRepository.save(paymentIntent);
    }
}
