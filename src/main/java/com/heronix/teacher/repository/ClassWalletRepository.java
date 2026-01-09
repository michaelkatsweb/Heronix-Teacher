package com.heronix.teacher.repository;

import com.heronix.teacher.model.domain.ClassWallet;
import com.heronix.teacher.model.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ClassWallet Repository
 *
 * Data access for classroom economy transactions
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Repository
public interface ClassWalletRepository extends JpaRepository<ClassWallet, Long> {

    /**
     * Find all transactions for a student
     */
    List<ClassWallet> findByStudentOrderByTransactionDateDesc(Student student);

    /**
     * Find transactions by student ID
     */
    @Query("SELECT cw FROM ClassWallet cw WHERE cw.student.id = :studentId ORDER BY cw.transactionDate DESC, cw.createdAt DESC")
    List<ClassWallet> findByStudentId(@Param("studentId") Long studentId);

    /**
     * Find transactions by type
     */
    List<ClassWallet> findByTransactionTypeOrderByTransactionDateDesc(String transactionType);

    /**
     * Find transactions by date range
     */
    @Query("SELECT cw FROM ClassWallet cw WHERE cw.transactionDate BETWEEN :startDate AND :endDate ORDER BY cw.transactionDate DESC")
    List<ClassWallet> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find transactions by category
     */
    List<ClassWallet> findByCategoryOrderByTransactionDateDesc(String category);

    /**
     * Find pending transactions (unapproved)
     */
    @Query("SELECT cw FROM ClassWallet cw WHERE cw.approved = false ORDER BY cw.transactionDate DESC")
    List<ClassWallet> findPendingTransactions();

    /**
     * Get current balance for a student
     */
    @Query("SELECT cw FROM ClassWallet cw WHERE cw.student.id = :studentId ORDER BY cw.createdAt DESC")
    List<ClassWallet> findMostRecentByStudentId(@Param("studentId") Long studentId);

    /**
     * Calculate total earned by student
     */
    @Query("SELECT COALESCE(SUM(cw.amount), 0) FROM ClassWallet cw WHERE cw.student.id = :studentId AND cw.amount > 0")
    BigDecimal getTotalEarnedByStudent(@Param("studentId") Long studentId);

    /**
     * Calculate total spent by student
     */
    @Query("SELECT COALESCE(SUM(cw.amount), 0) FROM ClassWallet cw WHERE cw.student.id = :studentId AND cw.amount < 0")
    BigDecimal getTotalSpentByStudent(@Param("studentId") Long studentId);

    /**
     * Get recent transactions
     */
    @Query("SELECT cw FROM ClassWallet cw WHERE cw.transactionDate >= :date ORDER BY cw.transactionDate DESC, cw.createdAt DESC")
    List<ClassWallet> findRecentTransactions(@Param("date") LocalDate date);

    /**
     * Find transactions needing sync
     */
    @Query("SELECT cw FROM ClassWallet cw WHERE cw.syncStatus = 'pending'")
    List<ClassWallet> findNeedingSync();

    /**
     * Count transactions by type for a date range
     */
    @Query("SELECT COUNT(cw) FROM ClassWallet cw WHERE cw.transactionType = :type AND cw.transactionDate BETWEEN :startDate AND :endDate")
    long countByTypeAndDateRange(@Param("type") String type, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get top earners
     */
    @Query("SELECT cw.student, SUM(cw.amount) as total FROM ClassWallet cw WHERE cw.amount > 0 GROUP BY cw.student ORDER BY total DESC")
    List<Object[]> getTopEarners();
}
