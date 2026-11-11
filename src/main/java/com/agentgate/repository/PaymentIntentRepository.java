
package com.agentgate.repository;

import com.agentgate.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentIntentRepository
        extends JpaRepository<PaymentIntent, Long> {

    Optional<PaymentIntent> findByPaypalOrderId(String paypalOrderId);
}
