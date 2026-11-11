
const API_URL = "/api/payments";

let allPayments = [];

const table = document.getElementById("paymentsTable");
const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");

// Create Payment modal elements
const paymentModal = document.getElementById("paymentModal");
const openPaymentModal = document.getElementById("openPaymentModal");
const closePaymentModal = document.getElementById("closePaymentModal");
const cancelPayment = document.getElementById("cancelPayment");
const createPaymentForm = document.getElementById("createPaymentForm");
const submitPaymentBtn = document.getElementById("submitPaymentBtn");

// Open modal
openPaymentModal.addEventListener("click", () => {
    paymentModal.style.display = "flex";
});

// Close modal
function closeModal() {
    paymentModal.style.display = "none";
}

closePaymentModal.addEventListener("click", closeModal);
cancelPayment.addEventListener("click", closeModal);

// Close when clicking outside the form
paymentModal.addEventListener("click", (event) => {
    if (event.target === paymentModal) {
        closeModal();
    }
});

// Close with Escape key
document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
        closeModal();
    }
});

// Load payments from backend
async function loadPayments() {

    table.innerHTML = `
        <tr>
            <td colspan="7" class="loading">
                Loading payments...
            </td>
        </tr>
    `;

    try {
        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error("Could not load payments");
        }

        allPayments = await response.json();

        updateStatistics();
        renderPayments();

    } catch (error) {

        table.innerHTML = `
            <tr>
                <td colspan="7" class="empty">
                    Failed to load payments. Check whether Spring Boot is running.
                </td>
            </tr>
        `;

        showToast(error.message);
    }
}

// Update dashboard statistics
function updateStatistics() {

    document.getElementById("totalCount").textContent =
        allPayments.length;

    document.getElementById("paidCount").textContent =
        countStatus("PAID");

    document.getElementById("escalatedCount").textContent =
        countStatus("ESCALATED");

    document.getElementById("blockedCount").textContent =
        countStatus("BLOCKED");
}

function countStatus(status) {

    return allPayments.filter(
        payment => payment.status === status
    ).length;
}

// Display payment records
function renderPayments() {

    const search = searchInput.value.toLowerCase();
    const selectedStatus = statusFilter.value;

    const filteredPayments = allPayments.filter(payment => {

        const matchesSearch =
            (payment.vendor || "").toLowerCase().includes(search) ||
            (payment.agentId || "").toLowerCase().includes(search);

        const matchesStatus =
            selectedStatus === "ALL" ||
            payment.status === selectedStatus;

        return matchesSearch && matchesStatus;
    });

    document.getElementById("recordCount").textContent =
        `${filteredPayments.length} records`;

    if (filteredPayments.length === 0) {

        table.innerHTML = `
            <tr>
                <td colspan="7" class="empty">
                    No matching payment requests found.
                </td>
            </tr>
        `;

        return;
    }

    table.innerHTML = "";

    filteredPayments.forEach(payment => {

        const status = payment.status || "UNKNOWN";
        const aiDecision = payment.aiDecision || "N/A";

        const row = document.createElement("tr");

        const values = [
            `#${payment.id}`,
            payment.vendor || "Unknown",
            payment.agentId || "N/A",
            `${payment.currency || "USD"} ${Number(payment.amount || 0).toFixed(2)}`,
            aiDecision,
            status
        ];

        values.forEach((value, index) => {

            const cell = document.createElement("td");
            cell.textContent = value;

            if (index === 4) {
                cell.className = "ai-decision";
            }

            if (index === 5) {

                const badge = document.createElement("span");

                badge.className = `status ${status}`;
                badge.textContent = status;

                cell.textContent = "";
                cell.appendChild(badge);
            }

            row.appendChild(cell);
        });

        // Action buttons
        const actionCell = document.createElement("td");
        const actions = document.createElement("div");

        actions.className = "action-buttons";

        if (status === "ESCALATED") {

            const approveButton = document.createElement("button");

            approveButton.className = "approve-btn";
            approveButton.textContent = "Approve";

            approveButton.addEventListener("click", () => {
                updatePayment(payment.id, "approve");
            });

            const rejectButton = document.createElement("button");

            rejectButton.className = "reject-btn";
            rejectButton.textContent = "Reject";

            rejectButton.addEventListener("click", () => {
                updatePayment(payment.id, "reject");
            });

            actions.append(approveButton, rejectButton);

        } else if (payment.paypalApprovalUrl && status === "APPROVED") {

            const paypalLink = document.createElement("a");

            paypalLink.className = "paypal-btn";
            paypalLink.textContent = "Pay with PayPal";
            paypalLink.href = payment.paypalApprovalUrl;
            paypalLink.target = "_blank";
            paypalLink.rel = "noopener noreferrer";

            actions.appendChild(paypalLink);

        } else {

            actions.textContent = "—";
        }

        actionCell.appendChild(actions);
        row.appendChild(actionCell);

        table.appendChild(row);
    });
}

// Create a new payment request
createPaymentForm.addEventListener("submit", async (event) => {

    event.preventDefault();

    const paymentData = {

        agentId: document.getElementById("agentId").value.trim(),

        vendor: document.getElementById("vendor").value.trim(),

        amount: Number(document.getElementById("amount").value),

        currency: "USD",

        description: document.getElementById("description").value.trim()
    };

    if (
        !paymentData.agentId ||
        !paymentData.vendor ||
        !paymentData.description ||
        !Number.isFinite(paymentData.amount) ||
        paymentData.amount <= 0
    ) {
        showToast("Please enter valid payment details.");
        return;
    }

    submitPaymentBtn.disabled = true;
    submitPaymentBtn.textContent = "Submitting...";

    try {

        const response = await fetch(API_URL, {

            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify(paymentData)
        });

        const responseText = await response.text();

        let result = {};

        try {
            result = responseText ? JSON.parse(responseText) : {};
        } catch {
            result = {};
        }

        if (!response.ok) {

            throw new Error(
                result.detail ||
                result.message ||
                result.error ||
                `Payment creation failed (${response.status})`
            );
        }

        showToast(
            `Payment request #${result.id ?? ""} submitted successfully!`
        );

        createPaymentForm.reset();

        document.getElementById("agentId").value =
            "shopping-agent-01";

        closeModal();

        // Reload latest payments and statistics
        await loadPayments();

    } catch (error) {

        showToast(error.message);

    } finally {

        submitPaymentBtn.disabled = false;
        submitPaymentBtn.textContent = "Submit Payment";
    }
});

// Approve or reject an escalated payment
async function updatePayment(id, action) {

    const confirmed = confirm(
        `Are you sure you want to ${action} payment #${id}?`
    );

    if (!confirmed) return;

    try {

        const response = await fetch(
            `${API_URL}/${id}/${action}`,
            {
                method: "POST"
            }
        );

        const responseText = await response.text();

        let errorData = {};

        try {
            errorData = responseText ? JSON.parse(responseText) : {};
        } catch {
            errorData = {};
        }

        if (!response.ok) {

            throw new Error(
                errorData.detail ||
                errorData.message ||
                errorData.error ||
                `Request failed (${response.status})`
            );
        }

        showToast(`Payment #${id} ${action} successful.`);

        await loadPayments();

    } catch (error) {

        showToast(error.message);
    }
}

// Toast notification
function showToast(message) {

    const toast = document.getElementById("toast");

    toast.textContent = message;
    toast.style.display = "block";

    setTimeout(() => {
        toast.style.display = "none";
    }, 3000);
}

// Refresh button
document.getElementById("refreshBtn").addEventListener(
    "click",
    loadPayments
);

// Search and filter
searchInput.addEventListener("input", renderPayments);

statusFilter.addEventListener("change", renderPayments);

// Initial loading
loadPayments();
