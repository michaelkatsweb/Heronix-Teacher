package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.ClassWallet;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.ClassWalletRepository;
import com.heronix.teacher.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ClassWallet Service
 *
 * Business logic for classroom economy system
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClassWalletService {

    private final ClassWalletRepository walletRepository;
    private final StudentRepository studentRepository;

    // ==================== Transaction Management ====================

    /**
     * Award points to a student
     */
    @Transactional
    public ClassWallet awardPoints(Long studentId, BigDecimal amount, String category, String description) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        BigDecimal currentBalance = getCurrentBalance(studentId);
        BigDecimal newBalance = currentBalance.add(amount);

        ClassWallet transaction = ClassWallet.builder()
                .student(student)
                .transactionType("REWARD")
                .amount(amount)
                .balanceAfter(newBalance)
                .category(category)
                .description(description)
                .transactionDate(LocalDate.now())
                .approved(true)
                .syncStatus("pending")
                .build();

        ClassWallet saved = walletRepository.save(transaction);
        log.info("Awarded {} points to student {}: {}", amount, student.getFullName(), description);
        return saved;
    }

    /**
     * Deduct points from a student (fine)
     */
    @Transactional
    public ClassWallet deductPoints(Long studentId, BigDecimal amount, String category, String description) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        BigDecimal currentBalance = getCurrentBalance(studentId);
        BigDecimal newBalance = currentBalance.subtract(amount);

        ClassWallet transaction = ClassWallet.builder()
                .student(student)
                .transactionType("FINE")
                .amount(amount.negate()) // Store as negative
                .balanceAfter(newBalance)
                .category(category)
                .description(description)
                .transactionDate(LocalDate.now())
                .approved(true)
                .syncStatus("pending")
                .build();

        ClassWallet saved = walletRepository.save(transaction);
        log.info("Deducted {} points from student {}: {}", amount, student.getFullName(), description);
        return saved;
    }

    /**
     * Record a purchase
     */
    @Transactional
    public ClassWallet recordPurchase(Long studentId, BigDecimal amount, String item, String notes) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        BigDecimal currentBalance = getCurrentBalance(studentId);

        if (currentBalance.compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        BigDecimal newBalance = currentBalance.subtract(amount);

        ClassWallet transaction = ClassWallet.builder()
                .student(student)
                .transactionType("PURCHASE")
                .amount(amount.negate()) // Store as negative
                .balanceAfter(newBalance)
                .category("Store")
                .description(item)
                .notes(notes)
                .transactionDate(LocalDate.now())
                .approved(true)
                .syncStatus("pending")
                .build();

        ClassWallet saved = walletRepository.save(transaction);
        log.info("Recorded purchase for student {}: {} for {}", student.getFullName(), item, amount);
        return saved;
    }

    /**
     * Create a manual adjustment
     */
    @Transactional
    public ClassWallet createAdjustment(Long studentId, BigDecimal amount, String reason, String teacherName) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        BigDecimal currentBalance = getCurrentBalance(studentId);
        BigDecimal newBalance = currentBalance.add(amount);

        ClassWallet transaction = ClassWallet.builder()
                .student(student)
                .transactionType("ADJUSTMENT")
                .amount(amount)
                .balanceAfter(newBalance)
                .category("Manual")
                .description(reason)
                .teacherName(teacherName)
                .transactionDate(LocalDate.now())
                .approved(true)
                .syncStatus("pending")
                .build();

        ClassWallet saved = walletRepository.save(transaction);
        log.info("Created adjustment for student {}: {} by {}", student.getFullName(), amount, teacherName);
        return saved;
    }

    // ==================== Balance Queries ====================

    /**
     * Get current balance for a student
     */
    public BigDecimal getCurrentBalance(Long studentId) {
        List<ClassWallet> transactions = walletRepository.findMostRecentByStudentId(studentId);

        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Return the most recent balance_after
        return transactions.get(0).getBalanceAfter();
    }

    /**
     * Get all student balances
     */
    public Map<Student, BigDecimal> getAllStudentBalances() {
        List<Student> activeStudents = studentRepository.findByActiveTrue();
        Map<Student, BigDecimal> balances = new LinkedHashMap<>();

        for (Student student : activeStudents) {
            balances.put(student, getCurrentBalance(student.getId()));
        }

        return balances;
    }

    /**
     * Get top earners (students with highest balances)
     */
    public List<Map<String, Object>> getTopEarners(int limit) {
        Map<Student, BigDecimal> balances = getAllStudentBalances();

        return balances.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("student", entry.getKey());
                    result.put("balance", entry.getValue());
                    return result;
                })
                .collect(Collectors.toList());
    }

    // ==================== Transaction History ====================

    /**
     * Get transaction history for a student
     */
    public List<ClassWallet> getStudentTransactions(Long studentId) {
        return walletRepository.findByStudentId(studentId);
    }

    /**
     * Get recent transactions for all students
     */
    public List<ClassWallet> getRecentTransactions(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        return walletRepository.findRecentTransactions(startDate);
    }

    /**
     * Get transactions by type
     */
    public List<ClassWallet> getTransactionsByType(String type) {
        return walletRepository.findByTransactionTypeOrderByTransactionDateDesc(type);
    }

    /**
     * Get transactions by category
     */
    public List<ClassWallet> getTransactionsByCategory(String category) {
        return walletRepository.findByCategoryOrderByTransactionDateDesc(category);
    }

    /**
     * Get transactions by date range
     */
    public List<ClassWallet> getTransactionsByDateRange(LocalDate startDate, LocalDate endDate) {
        return walletRepository.findByDateRange(startDate, endDate);
    }

    // ==================== Statistics ====================

    /**
     * Get total earned by student
     */
    public BigDecimal getTotalEarned(Long studentId) {
        return walletRepository.getTotalEarnedByStudent(studentId);
    }

    /**
     * Get total spent by student
     */
    public BigDecimal getTotalSpent(Long studentId) {
        BigDecimal total = walletRepository.getTotalSpentByStudent(studentId);
        return total.abs(); // Return as positive number
    }

    /**
     * Get class statistics
     */
    public Map<String, Object> getClassStatistics() {
        List<ClassWallet> allTransactions = walletRepository.findAll();

        BigDecimal totalAwarded = allTransactions.stream()
                .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(ClassWallet::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent = allTransactions.stream()
                .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(ClassWallet::getAmount)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalTransactions = allTransactions.size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAwarded", totalAwarded);
        stats.put("totalSpent", totalSpent);
        stats.put("totalTransactions", totalTransactions);
        stats.put("activeStudents", studentRepository.countByActiveTrue());

        return stats;
    }

    // ==================== CRUD Operations ====================

    /**
     * Get all transactions
     */
    public List<ClassWallet> getAllTransactions() {
        return walletRepository.findAll();
    }

    /**
     * Get transaction by ID
     */
    public Optional<ClassWallet> getTransactionById(Long id) {
        return walletRepository.findById(id);
    }

    /**
     * Update transaction
     */
    @Transactional
    public ClassWallet updateTransaction(Long id, ClassWallet updatedTransaction) {
        ClassWallet existing = walletRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        existing.setDescription(updatedTransaction.getDescription());
        existing.setCategory(updatedTransaction.getCategory());
        existing.setNotes(updatedTransaction.getNotes());
        existing.setApproved(updatedTransaction.getApproved());

        return walletRepository.save(existing);
    }

    /**
     * Delete transaction
     */
    @Transactional
    public void deleteTransaction(Long id) {
        walletRepository.deleteById(id);
        log.info("Deleted transaction: {}", id);
    }

    // ==================== Sync Support ====================

    /**
     * Get transactions needing sync
     */
    public List<ClassWallet> getTransactionsNeedingSync() {
        return walletRepository.findNeedingSync();
    }

    /**
     * Mark transaction as synced
     */
    @Transactional
    public void markSynced(Long id) {
        walletRepository.findById(id).ifPresent(transaction -> {
            transaction.setSyncStatus("synced");
            walletRepository.save(transaction);
        });
    }
}
