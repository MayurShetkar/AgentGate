package com.agentgate.policy;

import com.agentgate.entity.PaymentIntent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class PolicyEngine {

    private static final BigDecimal AUTO_APPROVAL_LIMIT =
            new BigDecimal("100.00");

    private static final BigDecimal HARD_BLOCK_LIMIT =
            new BigDecimal("500.00");

    private static final Set<String> ALLOWED_VENDORS = Set.of(
            "Amazon",
            "Walmart",
            "BestBuy"
    );

    public PolicyResult evaluate(PaymentIntent paymentIntent) {

        BigDecimal amount = paymentIntent.getAmount();

        // Rule 1: Hard maximum amount
        if (amount.compareTo(HARD_BLOCK_LIMIT) > 0) {
            return new PolicyResult(
                    PolicyDecision.BLOCK,
                    "Transaction exceeds the maximum allowed amount."
            );
        }

        // Rule 2: Vendor check
        if (!ALLOWED_VENDORS.contains(paymentIntent.getVendor())) {
            return new PolicyResult(
                    PolicyDecision.ESCALATE,
                    "Vendor is not on the approved vendor list."
            );
        }

        // Rule 3: Automatic approval limit
        if (amount.compareTo(AUTO_APPROVAL_LIMIT) <= 0) {
            return new PolicyResult(
                    PolicyDecision.ALLOW,
                    "Transaction is within the automatic approval limit."
            );
        }

        // Amount between $100 and $500
        return new PolicyResult(
                PolicyDecision.ESCALATE,
                "Transaction requires additional approval."
        );
    }
}