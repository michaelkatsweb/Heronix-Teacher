package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ClassWallet Transaction Entity
 *
 * Tracks student rewards, fines, and purchases in a classroom economy system
 *
 * Features:
 * - Student balance tracking
 * - Transaction history
 * - Categories (Reward, Fine, Purchase, Refund)
 * - Teacher notes and approval
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "class_wallet_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Student associated with transaction
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /**
     * Transaction type: REWARD, FINE, PURCHASE, REFUND, ADJUSTMENT
     */
    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    /**
     * Amount (positive for credits, negative for debits)
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Balance after this transaction
     */
    @Column(name = "balance_after", precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * Category (Behavior, Homework, Participation, Purchase, etc.)
     */
    @Column(name = "category")
    private String category;

    /**
     * Description of transaction
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Transaction date
     */
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /**
     * Teacher who recorded the transaction
     */
    @Column(name = "teacher_name")
    private String teacherName;

    /**
     * Approval status (for purchases)
     */
    @Column(name = "approved")
    private Boolean approved = true;

    /**
     * Notes for the transaction
     */
    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * Sync status for admin system
     */
    @Column(name = "sync_status")
    private String syncStatus = "pending";

    /**
     * Created timestamp
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Updated timestamp
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (transactionDate == null) {
            transactionDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if transaction is a credit (adds to balance)
     */
    public boolean isCredit() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if transaction is a debit (subtracts from balance)
     */
    public boolean isDebit() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Get formatted amount string
     */
    public String getFormattedAmount() {
        if (amount == null) return "$0.00";
        return String.format("$%.2f", amount.abs());
    }

    /**
     * Get display string for transaction
     */
    public String getDisplayString() {
        String type = isCredit() ? "+" : "-";
        return String.format("%s%s: %s", type, getFormattedAmount(), description != null ? description : "No description");
    }
}
