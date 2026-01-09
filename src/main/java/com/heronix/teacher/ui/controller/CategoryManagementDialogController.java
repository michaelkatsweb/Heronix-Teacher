package com.heronix.teacher.ui.controller;

import com.heronix.teacher.model.domain.AssignmentCategory;
import com.heronix.teacher.service.GradebookService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Category Management Dialog Controller
 *
 * Handles creation and editing of assignment categories for weighted grading
 * Features:
 * - Create/edit/delete categories
 * - Set category weights
 * - Configure drop lowest/highest scores
 * - Extra credit categories
 * - Real-time weight calculation
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
public class CategoryManagementDialogController {

    private GradebookService gradebookService;
    private AssignmentCategory selectedCategory;
    private ObservableList<AssignmentCategory> categories = FXCollections.observableArrayList();

    // FXML Controls
    @FXML private TableView<AssignmentCategory> categoriesTable;
    @FXML private TableColumn<AssignmentCategory, String> nameColumn;
    @FXML private TableColumn<AssignmentCategory, String> weightColumn;
    @FXML private TableColumn<AssignmentCategory, String> dropColumn;

    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private TextField weightField;
    @FXML private Spinner<Integer> dropLowestSpinner;
    @FXML private Spinner<Integer> dropHighestSpinner;
    @FXML private CheckBox extraCreditCheckbox;
    @FXML private Spinner<Integer> displayOrderSpinner;
    @FXML private CheckBox activeCheckbox;
    @FXML private Button saveButton;
    @FXML private Label totalWeightLabel;

    /**
     * Initialize the dialog
     */
    public void initialize() {
        log.info("Initializing Category Management Dialog Controller");

        setupTableColumns();
        setupSpinners();
        setupListeners();

        log.info("Category Management Dialog Controller initialized");
    }

    /**
     * Setup table columns
     */
    private void setupTableColumns() {
        nameColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getName()));

        weightColumn.setCellValueFactory(cellData -> {
            AssignmentCategory category = cellData.getValue();
            if (category.getWeight() != null) {
                return new SimpleStringProperty(String.format("%.0f%%", category.getWeight() * 100));
            }
            return new SimpleStringProperty("Auto");
        });

        dropColumn.setCellValueFactory(cellData -> {
            AssignmentCategory category = cellData.getValue();
            int dropLowest = category.getDropLowest() != null ? category.getDropLowest() : 0;
            int dropHighest = category.getDropHighest() != null ? category.getDropHighest() : 0;
            if (dropLowest > 0 || dropHighest > 0) {
                return new SimpleStringProperty(String.format("L:%d H:%d", dropLowest, dropHighest));
            }
            return new SimpleStringProperty("-");
        });

        categoriesTable.setItems(categories);
    }

    /**
     * Setup spinners
     */
    private void setupSpinners() {
        dropLowestSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10, 0));
        dropHighestSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10, 0));
        displayOrderSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 0));
    }

    /**
     * Setup listeners
     */
    private void setupListeners() {
        // Selection listener
        categoriesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadCategory(newVal);
            }
        });

        // Weight change listener
        weightField.textProperty().addListener((obs, oldVal, newVal) -> updateTotalWeight());
    }

    /**
     * Set the gradebook service
     */
    public void setGradebookService(GradebookService service) {
        this.gradebookService = service;
        loadCategories();
    }

    /**
     * Load all categories
     */
    private void loadCategories() {
        if (gradebookService != null) {
            List<AssignmentCategory> allCategories = gradebookService.getAllActiveCategories();
            categories.clear();
            categories.addAll(allCategories);
            updateTotalWeight();
        }
    }

    /**
     * Load category into form
     */
    private void loadCategory(AssignmentCategory category) {
        selectedCategory = category;

        nameField.setText(category.getName());
        descriptionField.setText(category.getDescription());

        if (category.getWeight() != null) {
            weightField.setText(String.format("%.0f", category.getWeight() * 100));
        } else {
            weightField.clear();
        }

        dropLowestSpinner.getValueFactory().setValue(
            category.getDropLowest() != null ? category.getDropLowest() : 0);
        dropHighestSpinner.getValueFactory().setValue(
            category.getDropHighest() != null ? category.getDropHighest() : 0);
        extraCreditCheckbox.setSelected(
            category.getIsExtraCredit() != null && category.getIsExtraCredit());
        displayOrderSpinner.getValueFactory().setValue(
            category.getDisplayOrder() != null ? category.getDisplayOrder() : 0);
        activeCheckbox.setSelected(
            category.getActive() != null && category.getActive());

        saveButton.setText("Update Category");
    }

    /**
     * Create new category
     */
    @FXML
    public void newCategory() {
        selectedCategory = null;
        clearForm();
        saveButton.setText("Save Category");
    }

    /**
     * Clear form
     */
    @FXML
    public void clearForm() {
        selectedCategory = null;
        nameField.clear();
        descriptionField.clear();
        weightField.clear();
        dropLowestSpinner.getValueFactory().setValue(0);
        dropHighestSpinner.getValueFactory().setValue(0);
        extraCreditCheckbox.setSelected(false);
        displayOrderSpinner.getValueFactory().setValue(0);
        activeCheckbox.setSelected(true);
        categoriesTable.getSelectionModel().clearSelection();
        saveButton.setText("Save Category");
    }

    /**
     * Validate form
     */
    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            errors.append("• Category name is required\n");
        }

        if (weightField.getText() != null && !weightField.getText().trim().isEmpty()) {
            try {
                double weight = Double.parseDouble(weightField.getText());
                if (weight < 0 || weight > 100) {
                    errors.append("• Weight must be between 0 and 100\n");
                }
            } catch (NumberFormatException e) {
                errors.append("• Weight must be a valid number\n");
            }
        }

        if (errors.length() > 0) {
            showError("Validation Error", errors.toString());
            return false;
        }

        return true;
    }

    /**
     * Save category
     */
    @FXML
    public void saveCategory() {
        if (!validateForm()) {
            return;
        }

        try {
            if (selectedCategory == null) {
                selectedCategory = new AssignmentCategory();
            }

            selectedCategory.setName(nameField.getText().trim());
            selectedCategory.setDescription(descriptionField.getText());

            if (weightField.getText() != null && !weightField.getText().trim().isEmpty()) {
                double weight = Double.parseDouble(weightField.getText()) / 100.0;
                selectedCategory.setWeight(weight);
            } else {
                selectedCategory.setWeight(null);
            }

            selectedCategory.setDropLowest(dropLowestSpinner.getValue());
            selectedCategory.setDropHighest(dropHighestSpinner.getValue());
            selectedCategory.setIsExtraCredit(extraCreditCheckbox.isSelected());
            selectedCategory.setDisplayOrder(displayOrderSpinner.getValue());
            selectedCategory.setActive(activeCheckbox.isSelected());

            if (selectedCategory.getId() == null) {
                gradebookService.createCategory(selectedCategory);
                log.info("Created new category: {}", selectedCategory.getName());
            } else {
                gradebookService.updateCategory(selectedCategory);
                log.info("Updated category: {}", selectedCategory.getName());
            }

            loadCategories();
            clearForm();

        } catch (Exception e) {
            log.error("Error saving category", e);
            showError("Save Error", "Failed to save category: " + e.getMessage());
        }
    }

    /**
     * Delete category
     */
    @FXML
    public void deleteCategory() {
        AssignmentCategory selected = categoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Selection", "Please select a category to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Category: " + selected.getName());
        confirm.setContentText("Are you sure you want to delete this category?\n\n" +
                               "Assignments in this category will not be deleted,\n" +
                               "but they will no longer be associated with a category.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                gradebookService.deleteCategory(selected.getId());
                log.info("Deleted category: {}", selected.getName());
                loadCategories();
                clearForm();
            } catch (Exception e) {
                log.error("Error deleting category", e);
                showError("Delete Error", "Failed to delete category: " + e.getMessage());
            }
        }
    }

    /**
     * Update total weight display
     */
    private void updateTotalWeight() {
        double totalWeight = 0.0;
        int categoriesWithWeight = 0;

        for (AssignmentCategory category : categories) {
            if (category.getWeight() != null && !category.getIsExtraCredit()) {
                totalWeight += category.getWeight();
                categoriesWithWeight++;
            }
        }

        // Check if current form has weight set
        if (weightField.getText() != null && !weightField.getText().trim().isEmpty()) {
            try {
                double currentWeight = Double.parseDouble(weightField.getText()) / 100.0;
                if (selectedCategory == null || selectedCategory.getWeight() == null) {
                    totalWeight += currentWeight;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        String display = String.format("%.0f%%", totalWeight * 100);
        if (Math.abs(totalWeight - 1.0) < 0.01) {
            display += " ✓ (Perfect!)";
            totalWeightLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else if (totalWeight > 1.0) {
            display += " ⚠ (Over 100%)";
            totalWeightLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        } else if (totalWeight > 0 && totalWeight < 1.0) {
            display += " (Will be normalized)";
            totalWeightLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        } else {
            totalWeightLabel.setStyle("-fx-text-fill: gray;");
        }

        totalWeightLabel.setText(display);
    }

    /**
     * Close dialog
     */
    @FXML
    public void close() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Show error alert
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show warning alert
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
