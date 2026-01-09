package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.ClassWallet;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.StudentRepository;
import com.heronix.teacher.service.ClassWalletService;
import com.heronix.teacher.service.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class Wallet Controller
 *
 * Manages classroom economy transactions
 * - Award/deduct points
 * - Track student balances
 * - Transaction history
 * - Reports and statistics
 *
 * @author Heronix-Teacher Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassWalletController {

    private final ClassWalletService walletService;
    private final StudentRepository studentRepository;
    private final SessionManager sessionManager;

    // Filter controls
    @FXML private ComboBox<String> studentFilterCombo;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TextField searchField;

    // Summary labels
    @FXML private Label totalBalanceLabel;
    @FXML private Label totalStudentsLabel;
    @FXML private Label totalEarnedLabel;
    @FXML private Label totalSpentLabel;
    @FXML private Label transactionCountLabel;
    @FXML private Label transactionTotalLabel;

    // Buttons
    @FXML private Button newTransactionBtn;
    @FXML private Button syncBtn;

    // Table
    @FXML private TableView<ClassWallet> transactionsTable;
    @FXML private TableColumn<ClassWallet, String> dateColumn;
    @FXML private TableColumn<ClassWallet, String> studentColumn;
    @FXML private TableColumn<ClassWallet, String> typeColumn;
    @FXML private TableColumn<ClassWallet, String> categoryColumn;
    @FXML private TableColumn<ClassWallet, String> descriptionColumn;
    @FXML private TableColumn<ClassWallet, String> amountColumn;
    @FXML private TableColumn<ClassWallet, String> balanceColumn;
    @FXML private TableColumn<ClassWallet, String> statusColumn;

    private ObservableList<ClassWallet> transactions = FXCollections.observableArrayList();
    private List<Student> allStudents;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    /**
     * Initialize controller
     */
    @FXML
    public void initialize() {
        log.info("Initializing Class Wallet Controller");

        setupTableColumns();
        setupFilters();
        loadData();
        updateStatistics();

        log.info("Class Wallet Controller initialized successfully");
    }

    /**
     * Setup table columns
     */
    private void setupTableColumns() {
        // Date column
        dateColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData.getValue().getTransactionDate() != null
                    ? cellData.getValue().getTransactionDate().format(DATE_FORMATTER)
                    : ""
            ));

        // Student column
        studentColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData.getValue().getStudent() != null
                    ? cellData.getValue().getStudent().getFullName()
                    : "Unknown"
            ));

        // Type column with color coding
        typeColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getTransactionType()));
        typeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "REWARD":
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                            break;
                        case "FINE":
                            setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                            break;
                        case "PURCHASE":
                            setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                            break;
                        case "REFUND":
                            setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("-fx-text-fill: gray;");
                    }
                }
            }
        });

        // Category column
        categoryColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getCategory()));

        // Description column
        descriptionColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getDescription()));

        // Amount column with formatting
        amountColumn.setCellValueFactory(cellData -> {
            BigDecimal amount = cellData.getValue().getAmount();
            String formatted = amount.compareTo(BigDecimal.ZERO) > 0
                ? "+" + String.format("%.2f", amount)
                : String.format("%.2f", amount);
            return new SimpleStringProperty(formatted);
        });
        amountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Balance column
        balanceColumn.setCellValueFactory(cellData -> {
            BigDecimal balance = cellData.getValue().getBalanceAfter();
            return new SimpleStringProperty(
                balance != null ? String.format("%.2f", balance) : "0.00"
            );
        });

        // Status column
        statusColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData.getValue().getApproved() ? "✓" : "⏳"
            ));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("✓")) {
                        setStyle("-fx-text-fill: green; -fx-font-size: 14px;");
                    } else {
                        setStyle("-fx-text-fill: orange; -fx-font-size: 14px;");
                    }
                }
            }
        });

        transactionsTable.setItems(transactions);
    }

    /**
     * Setup filter controls
     */
    private void setupFilters() {
        // Transaction type filter
        typeFilterCombo.setItems(FXCollections.observableArrayList(
            "All Types", "REWARD", "FINE", "PURCHASE", "REFUND", "ADJUSTMENT"
        ));
        typeFilterCombo.setValue("All Types");
        typeFilterCombo.setOnAction(e -> applyFilters());

        // Date pickers
        fromDatePicker.setOnAction(e -> applyFilters());
        toDatePicker.setOnAction(e -> applyFilters());

        // Search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    /**
     * Load data
     */
    private void loadData() {
        try {
            // Load all students for filter
            allStudents = studentRepository.findByActiveTrue();

            ObservableList<String> studentNames = FXCollections.observableArrayList("All Students");
            studentNames.addAll(allStudents.stream()
                .map(Student::getFullName)
                .sorted()
                .collect(Collectors.toList()));
            studentFilterCombo.setItems(studentNames);
            studentFilterCombo.setValue("All Students");
            studentFilterCombo.setOnAction(e -> applyFilters());

            // Load all transactions
            List<ClassWallet> allTransactions = walletService.getAllTransactions();
            transactions.setAll(allTransactions);

            log.info("Loaded {} transactions", allTransactions.size());

        } catch (Exception e) {
            log.error("Error loading wallet data", e);
            showError("Failed to load wallet data: " + e.getMessage());
        }
    }

    /**
     * Apply filters to transaction list
     */
    private void applyFilters() {
        try {
            List<ClassWallet> filtered = walletService.getAllTransactions();

            // Filter by student
            String selectedStudent = studentFilterCombo.getValue();
            if (selectedStudent != null && !selectedStudent.equals("All Students")) {
                filtered = filtered.stream()
                    .filter(t -> t.getStudent() != null &&
                                 t.getStudent().getFullName().equals(selectedStudent))
                    .collect(Collectors.toList());
            }

            // Filter by type
            String selectedType = typeFilterCombo.getValue();
            if (selectedType != null && !selectedType.equals("All Types")) {
                filtered = filtered.stream()
                    .filter(t -> t.getTransactionType().equals(selectedType))
                    .collect(Collectors.toList());
            }

            // Filter by date range
            LocalDate fromDate = fromDatePicker.getValue();
            LocalDate toDate = toDatePicker.getValue();
            if (fromDate != null) {
                filtered = filtered.stream()
                    .filter(t -> !t.getTransactionDate().isBefore(fromDate))
                    .collect(Collectors.toList());
            }
            if (toDate != null) {
                filtered = filtered.stream()
                    .filter(t -> !t.getTransactionDate().isAfter(toDate))
                    .collect(Collectors.toList());
            }

            // Search filter
            String searchText = searchField.getText();
            if (searchText != null && !searchText.trim().isEmpty()) {
                String search = searchText.toLowerCase();
                filtered = filtered.stream()
                    .filter(t ->
                        (t.getDescription() != null && t.getDescription().toLowerCase().contains(search)) ||
                        (t.getCategory() != null && t.getCategory().toLowerCase().contains(search)) ||
                        (t.getStudent() != null && t.getStudent().getFullName().toLowerCase().contains(search))
                    )
                    .collect(Collectors.toList());
            }

            transactions.setAll(filtered);
            updateStatistics();

        } catch (Exception e) {
            log.error("Error applying filters", e);
        }
    }

    /**
     * Clear all filters
     */
    @FXML
    public void handleClearFilters() {
        studentFilterCombo.setValue("All Students");
        typeFilterCombo.setValue("All Types");
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        searchField.clear();
        applyFilters();
    }

    /**
     * Update statistics
     */
    private void updateStatistics() {
        try {
            Map<String, Object> stats = walletService.getClassStatistics();

            BigDecimal totalAwarded = (BigDecimal) stats.get("totalAwarded");
            BigDecimal totalSpent = (BigDecimal) stats.get("totalSpent");
            Long activeStudents = (Long) stats.get("activeStudents");

            // Calculate total balance (awarded - spent)
            BigDecimal totalBalance = totalAwarded.subtract(totalSpent);

            totalBalanceLabel.setText(String.format("%.2f", totalBalance));
            totalEarnedLabel.setText(String.format("%.2f", totalAwarded));
            totalSpentLabel.setText(String.format("%.2f", totalSpent));
            totalStudentsLabel.setText(activeStudents + " students");
            transactionCountLabel.setText(String.valueOf(transactions.size()));
            transactionTotalLabel.setText(transactions.size() + " transactions");

        } catch (Exception e) {
            log.error("Error updating statistics", e);
        }
    }

    /**
     * Handle new transaction button
     */
    @FXML
    public void handleNewTransaction() {
        log.info("Opening new transaction dialog");
        showInfo("Transaction Dialog", "New transaction dialog will be implemented");
        // FUTURE ENHANCEMENT: Transaction Dialog - Framework placeholder, dialog UI pending
    }

    /**
     * Handle sync button
     */
    @FXML
    public void handleSync() {
        log.info("Syncing wallet data");
        try {
            // Sync is handled by TeacherSyncService
            loadData();
            updateStatistics();
            showSuccess("Wallet data refreshed successfully");
        } catch (Exception e) {
            log.error("Error syncing wallet data", e);
            showError("Failed to refresh: " + e.getMessage());
        }
    }

    /**
     * Handle view reports button
     */
    @FXML
    public void handleViewReports() {
        log.info("Viewing wallet reports");
        showInfo("Reports", "Wallet reports will be implemented");
        // FUTURE ENHANCEMENT: Reports Dialog - Framework placeholder, dialog UI pending
    }

    /**
     * Handle top earners button
     */
    @FXML
    public void handleTopEarners() {
        log.info("Viewing top earners");
        try {
            List<Map<String, Object>> topEarners = walletService.getTopEarners(10);

            StringBuilder message = new StringBuilder("Top 10 Earners:\n\n");
            int rank = 1;
            for (Map<String, Object> entry : topEarners) {
                Student student = (Student) entry.get("student");
                BigDecimal balance = (BigDecimal) entry.get("balance");
                message.append(String.format("%d. %s - %.2f pts\n", rank++, student.getFullName(), balance));
            }

            showInfo("Top Earners", message.toString());
        } catch (Exception e) {
            log.error("Error getting top earners", e);
            showError("Failed to load top earners: " + e.getMessage());
        }
    }

    /**
     * Handle export button
     */
    @FXML
    public void handleExport() {
        log.info("Exporting wallet data");
        showInfo("Export", "CSV export will be implemented");
        // FUTURE ENHANCEMENT: CSV Export - Framework placeholder, export feature pending
    }

    /**
     * Show error alert
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show info alert
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show success alert
     */
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
