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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

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

        // Create dialog
        Dialog<ClassWallet> dialog = new Dialog<>();
        dialog.setTitle("New Transaction");
        dialog.setHeaderText("Create a new wallet transaction");

        // Add buttons
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Create form
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(15);
        form.setPadding(new Insets(20));

        // Student selector
        Label studentLabel = new Label("Student:");
        studentLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        ComboBox<Student> studentCombo = new ComboBox<>();
        studentCombo.setItems(FXCollections.observableArrayList(allStudents));
        studentCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Student student) {
                return student != null ? student.getFullName() + " (" + student.getStudentId() + ")" : "";
            }
            @Override
            public Student fromString(String string) { return null; }
        });
        studentCombo.setPrefWidth(300);

        // Transaction type
        Label typeLabel = new Label("Type:");
        typeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.setItems(FXCollections.observableArrayList("REWARD", "FINE", "PURCHASE", "REFUND", "ADJUSTMENT"));
        typeCombo.setValue("REWARD");
        typeCombo.setPrefWidth(300);

        // Category
        Label categoryLabel = new Label("Category:");
        categoryLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.setItems(FXCollections.observableArrayList(
                "Participation", "Homework", "Behavior", "Achievement", "Attendance",
                "Store Purchase", "Penalty", "Bonus", "Other"));
        categoryCombo.setValue("Participation");
        categoryCombo.setEditable(true);
        categoryCombo.setPrefWidth(300);

        // Amount
        Label amountLabel = new Label("Amount:");
        amountLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        TextField amountField = new TextField();
        amountField.setPromptText("Enter amount (e.g., 10.00)");
        amountField.setPrefWidth(300);

        // Description
        Label descLabel = new Label("Description:");
        descLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        TextArea descArea = new TextArea();
        descArea.setPromptText("Enter description (optional)");
        descArea.setPrefRowCount(2);
        descArea.setPrefWidth(300);

        // Add to form
        form.add(studentLabel, 0, 0);
        form.add(studentCombo, 1, 0);
        form.add(typeLabel, 0, 1);
        form.add(typeCombo, 1, 1);
        form.add(categoryLabel, 0, 2);
        form.add(categoryCombo, 1, 2);
        form.add(amountLabel, 0, 3);
        form.add(amountField, 1, 3);
        form.add(descLabel, 0, 4);
        form.add(descArea, 1, 4);

        dialog.getDialogPane().setContent(form);

        // Enable/disable create button based on validation
        javafx.scene.Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        // Validation listener
        Runnable validate = () -> {
            boolean valid = studentCombo.getValue() != null &&
                    typeCombo.getValue() != null &&
                    !amountField.getText().trim().isEmpty();
            if (valid) {
                try {
                    new BigDecimal(amountField.getText().trim());
                } catch (NumberFormatException e) {
                    valid = false;
                }
            }
            createButton.setDisable(!valid);
        };

        studentCombo.valueProperty().addListener((obs, o, n) -> validate.run());
        typeCombo.valueProperty().addListener((obs, o, n) -> validate.run());
        amountField.textProperty().addListener((obs, o, n) -> validate.run());

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                try {
                    Student student = studentCombo.getValue();
                    String type = typeCombo.getValue();
                    String category = categoryCombo.getValue();
                    BigDecimal amount = new BigDecimal(amountField.getText().trim());
                    String description = descArea.getText();

                    // Adjust amount sign based on type
                    if (type.equals("FINE") || type.equals("PURCHASE")) {
                        amount = amount.abs().negate();
                    } else {
                        amount = amount.abs();
                    }

                    return walletService.createTransaction(student.getId(), type, category, amount, description);
                } catch (Exception e) {
                    log.error("Error creating transaction", e);
                    showError("Failed to create transaction: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<ClassWallet> result = dialog.showAndWait();
        result.ifPresent(transaction -> {
            log.info("Transaction created: {} {} for {}", transaction.getTransactionType(),
                    transaction.getAmount(), transaction.getStudent().getFullName());
            loadData();
            updateStatistics();
            showSuccess("Transaction created successfully!");
        });
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

        // Create dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Wallet Reports");
        dialog.setHeaderText("Class Wallet Statistics & Reports");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.setResizable(true);

        // Main layout
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setPrefWidth(600);
        mainContent.setPrefHeight(450);

        // Summary statistics panel
        GridPane statsPane = new GridPane();
        statsPane.setHgap(30);
        statsPane.setVgap(10);
        statsPane.setStyle("-fx-background-color: #e3f2fd; -fx-padding: 15; -fx-background-radius: 5;");

        Label statsTitle = new Label("Summary Statistics");
        statsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        statsPane.add(statsTitle, 0, 0, 4, 1);

        try {
            Map<String, Object> stats = walletService.getClassStatistics();

            BigDecimal totalAwarded = (BigDecimal) stats.get("totalAwarded");
            BigDecimal totalSpent = (BigDecimal) stats.get("totalSpent");
            Long activeStudents = (Long) stats.get("activeStudents");
            BigDecimal netBalance = totalAwarded.subtract(totalSpent);

            addStatLabel(statsPane, "Total Awarded:", String.format("%.2f pts", totalAwarded), "#4caf50", 0, 1);
            addStatLabel(statsPane, "Total Spent:", String.format("%.2f pts", totalSpent), "#f44336", 2, 1);
            addStatLabel(statsPane, "Net Balance:", String.format("%.2f pts", netBalance), "#2196f3", 0, 2);
            addStatLabel(statsPane, "Active Students:", String.valueOf(activeStudents), "#9c27b0", 2, 2);

        } catch (Exception e) {
            log.error("Error loading statistics", e);
            statsPane.add(new Label("Error loading statistics"), 0, 1);
        }

        // Transaction breakdown by type
        VBox typeBreakdown = new VBox(10);
        Label typeTitle = new Label("Transactions by Type");
        typeTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        GridPane typeGrid = new GridPane();
        typeGrid.setHgap(20);
        typeGrid.setVgap(5);
        typeGrid.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10; -fx-background-radius: 5;");

        Map<String, Long> typeCounts = transactions.stream()
                .collect(Collectors.groupingBy(ClassWallet::getTransactionType, Collectors.counting()));

        int row = 0;
        for (Map.Entry<String, Long> entry : typeCounts.entrySet()) {
            Label typeLabel = new Label(entry.getKey() + ":");
            Label countLabel = new Label(entry.getValue() + " transactions");
            countLabel.setStyle("-fx-font-weight: bold;");
            typeGrid.add(typeLabel, 0, row);
            typeGrid.add(countLabel, 1, row);
            row++;
        }

        typeBreakdown.getChildren().addAll(typeTitle, typeGrid);

        // Category breakdown
        VBox categoryBreakdown = new VBox(10);
        Label categoryTitle = new Label("Transactions by Category");
        categoryTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        GridPane categoryGrid = new GridPane();
        categoryGrid.setHgap(20);
        categoryGrid.setVgap(5);
        categoryGrid.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10; -fx-background-radius: 5;");

        Map<String, Long> categoryCounts = transactions.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(ClassWallet::getCategory, Collectors.counting()));

        row = 0;
        for (Map.Entry<String, Long> entry : categoryCounts.entrySet()) {
            if (row >= 8) break; // Limit to top 8 categories
            Label catLabel = new Label(entry.getKey() + ":");
            Label countLabel = new Label(entry.getValue() + " transactions");
            countLabel.setStyle("-fx-font-weight: bold;");
            categoryGrid.add(catLabel, 0, row);
            categoryGrid.add(countLabel, 1, row);
            row++;
        }

        categoryBreakdown.getChildren().addAll(categoryTitle, categoryGrid);

        // Export button
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button exportReportBtn = new Button("Export Report to CSV");
        exportReportBtn.setOnAction(e -> handleExport());
        buttonBox.getChildren().add(exportReportBtn);

        mainContent.getChildren().addAll(statsPane, typeBreakdown, categoryBreakdown, buttonBox);
        dialog.getDialogPane().setContent(mainContent);

        dialog.showAndWait();
    }

    /**
     * Helper to add stat label to grid
     */
    private void addStatLabel(GridPane pane, String label, String value, String color, int col, int row) {
        Label lbl = new Label(label);
        Label val = new Label(value);
        val.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: " + color + ";");
        pane.add(lbl, col, row);
        pane.add(val, col + 1, row);
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
     * Handle export button - Export transactions to CSV
     */
    @FXML
    public void handleExport() {
        log.info("Exporting wallet data (encrypted)");

        if (transactions.isEmpty()) {
            showError("No transactions to export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Wallet Transactions");
        fileChooser.setInitialFileName("class_wallet_export_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".heronix");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Encrypted Files", "*.heronix")
        );

        File file = fileChooser.showSaveDialog(transactionsTable.getScene().getWindow());

        if (file != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                try (PrintWriter writer = new PrintWriter(sw)) {
                    writer.write('\ufeff');
                    writer.println("Date,Student Name,Student ID,Type,Category,Description,Amount,Balance After,Status");

                    for (ClassWallet t : transactions) {
                        writer.println(String.format("%s,%s,%s,%s,%s,%s,%.2f,%.2f,%s",
                                t.getTransactionDate() != null ? t.getTransactionDate().format(DATE_FORMATTER) : "",
                                escapeCSV(t.getStudent() != null ? t.getStudent().getFullName() : "Unknown"),
                                escapeCSV(t.getStudent() != null ? t.getStudent().getStudentId() : ""),
                                t.getTransactionType(),
                                escapeCSV(t.getCategory()),
                                escapeCSV(t.getDescription()),
                                t.getAmount(),
                                t.getBalanceAfter() != null ? t.getBalanceAfter() : BigDecimal.ZERO,
                                t.getApproved() ? "Approved" : "Pending"
                        ));
                    }
                }
                String originalName = file.getName().replace(".heronix", ".csv");
                byte[] encrypted = com.heronix.teacher.security.HeronixEncryptionService.getInstance()
                        .encryptFile(sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), originalName);
                java.nio.file.Files.write(file.toPath(), encrypted);

                log.info("Wallet transactions exported (encrypted) to {}", file.getAbsolutePath());
                showSuccess("Exported " + transactions.size() + " transactions to:\n" + file.getName());

            } catch (Exception e) {
                log.error("Error exporting wallet data", e);
                showError("Failed to export: " + e.getMessage());
            }
        }
    }

    /**
     * Escape CSV value
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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
