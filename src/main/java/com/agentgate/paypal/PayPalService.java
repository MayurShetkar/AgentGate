
package com.agentgate.paypal;

import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.controllers.OrdersController;
import com.paypal.sdk.exceptions.ApiException;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.GetOrderInput;
import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderApplicationContext;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.PurchaseUnitRequest;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;

@Service
public class PayPalService {

    private final OrdersController ordersController;

    public PayPalService(PaypalServerSdkClient paypalClient) {
        this.ordersController = paypalClient.getOrdersController();
    }

    // 1. Create a PayPal checkout order
    public PayPalOrderResult createOrder(
            String currency,
            String amount,
            String description) {

        CreateOrderInput input = new CreateOrderInput.Builder(
                null,
                new OrderRequest.Builder(
                        CheckoutPaymentIntent.CAPTURE,
                        Arrays.asList(
                                new PurchaseUnitRequest.Builder(
                                        new AmountWithBreakdown.Builder(
                                                currency,
                                                amount
                                        ).build()
                                )
                                        .description(description)
                                        .build()
                        )
                )
                        .applicationContext(
                                new OrderApplicationContext.Builder()
                                        .returnUrl(
                                                "http://localhost:8080/api/paypal/return"
                                        )
                                        .cancelUrl(
                                                "http://localhost:8080/api/paypal/cancel"
                                        )
                                        .build()
                        )
                        .build()
        )
                .prefer("return=representation")
                .build();

        try {
            ApiResponse<Order> response =
                    ordersController.createOrder(input);

            Order order = response.getResult();

            if (order == null) {
                throw new RuntimeException(
                        "PayPal returned an empty order response."
                );
            }

            String approvalUrl = null;

            if (order.getLinks() != null) {
                approvalUrl = order.getLinks().stream()
                        .filter(link -> "approve".equals(link.getRel()))
                        .map(link -> link.getHref())
                        .findFirst()
                        .orElse(null);
            }

            if (approvalUrl == null) {
                throw new RuntimeException(
                        "PayPal did not return an approval URL."
                );
            }

            return new PayPalOrderResult(
                    order.getId(),
                    approvalUrl
            );

        } catch (ApiException | IOException exception) {
            throw new RuntimeException(
                    "PayPal order creation failed: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // 2. Retrieve order details directly from PayPal
    public Order getOrderDetails(String orderId) {

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException(
                    "PayPal order ID cannot be empty."
            );
        }

        GetOrderInput input = new GetOrderInput(
                orderId,
                null,
                null,
                null
        );

        try {
            ApiResponse<Order> response =
                    ordersController.getOrder(input);

            Order order = response.getResult();

            if (order == null) {
                throw new RuntimeException(
                        "PayPal returned an empty order-details response."
                );
            }

            return order;

        } catch (ApiException | IOException exception) {
            throw new RuntimeException(
                    "PayPal order retrieval failed: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // 3. Capture an approved PayPal order
    public PayPalCaptureResult captureOrder(String orderId) {

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException(
                    "PayPal order ID cannot be empty."
            );
        }

        CaptureOrderInput input =
                new CaptureOrderInput.Builder(
                        orderId,
                        "application/json"
                )
                        .prefer("return=representation")
                        .build();

        try {
            ApiResponse<Order> response =
                    ordersController.captureOrder(input);

            Order order = response.getResult();

            if (order == null || order.getStatus() == null) {
                throw new RuntimeException(
                        "PayPal returned an empty capture response."
                );
            }

            String status = order.getStatus().toString();

            if (!"COMPLETED".equalsIgnoreCase(status)) {
                throw new RuntimeException(
                        "PayPal capture is not completed. Status: "
                                + status
                );
            }

            if (order.getPurchaseUnits() == null
                    || order.getPurchaseUnits().isEmpty()) {
                throw new RuntimeException(
                        "PayPal response contains no purchase units."
                );
            }

            var purchaseUnit = order.getPurchaseUnits().get(0);

            if (purchaseUnit.getPayments() == null
                    || purchaseUnit.getPayments().getCaptures() == null
                    || purchaseUnit.getPayments().getCaptures().isEmpty()) {
                throw new RuntimeException(
                        "PayPal completed the order but returned no capture ID."
                );
            }

            var capture =
                    purchaseUnit.getPayments().getCaptures().get(0);

            if (capture.getId() == null || capture.getId().isBlank()) {
                throw new RuntimeException(
                        "PayPal returned an empty capture ID."
                );
            }

            return new PayPalCaptureResult(
                    status,
                    capture.getId()
            );

        } catch (ApiException | IOException exception) {
            throw new RuntimeException(
                    "PayPal payment capture failed: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
}
