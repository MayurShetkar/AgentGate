package com.agentgate.ai;

import com.agentgate.entity.PaymentIntent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AiRiskReviewer {

    public RiskAssessment review(PaymentIntent paymentIntent) {

        BigDecimal amount = paymentIntent.getAmount();

        // High-value transactions are considered high risk
        if (amount.compareTo(new BigDecimal("300.00")) > 0) {

            return new RiskAssessment(
                    RiskDecision.BLOCK,
                    "AI detected a high-risk transaction because the amount is unusually large."
            );
        }

        // Unknown vendors require additional verification
        if (!isKnownVendor(paymentIntent.getVendor())) {

            return new RiskAssessment(
                    RiskDecision.ESCALATE,
                    "AI recommends human verification because the vendor is unknown."
            );
        }

        // Otherwise, the transaction is considered acceptable
        return new RiskAssessment(
                RiskDecision.APPROVE,
                "AI found no significant additional risk."
        );
    }

    private boolean isKnownVendor(String vendor) {

        return vendor.equalsIgnoreCase("Amazon")
                || vendor.equalsIgnoreCase("Walmart")
                || vendor.equalsIgnoreCase("BestBuy");
    }
}
