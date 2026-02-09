package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.Assignment;
import com.heronix.teacher.model.domain.AssignmentCategory;
import com.heronix.teacher.model.domain.Grade;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.model.dto.ClassRosterDTO;
import com.heronix.teacher.repository.AssignmentCategoryRepository;
import com.heronix.teacher.repository.AssignmentRepository;
import com.heronix.teacher.repository.GradeRepository;
import com.heronix.teacher.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gradebook service for managing students, assignments, and grades
 *
 * Provides business logic for:
 * - Student roster management
 * - Assignment creation and management
 * - Grade entry and calculations
 * - Statistics and reports
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradebookService {

    private final StudentRepository studentRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentCategoryRepository categoryRepository;
    private final GradeRepository gradeRepository;
    private final StudentEnrollmentCache studentEnrollmentCache;

    // ==================== Student Management ====================

    /**
     * Get all active students
     */
    public List<Student> getAllActiveStudents() {
        log.debug("Fetching all active students");
        return studentRepository.findByActiveTrue();
    }

    /**
     * Get student by ID
     */
    public Optional<Student> getStudentById(Long id) {
        return studentRepository.findById(id);
    }

    /**
     * Search students by name
     */
    public List<Student> searchStudents(String searchTerm) {
        log.debug("Searching students with term: {}", searchTerm);
        return studentRepository.searchByName(searchTerm);
    }

    /**
     * Get students by grade level
     */
    public List<Student> getStudentsByGradeLevel(Integer gradeLevel) {
        return studentRepository.findByGradeLevelAndActiveTrue(gradeLevel);
    }

    /**
     * Get students with IEP
     */
    public List<Student> getStudentsWithIEP() {
        return studentRepository.findByHasIepTrueAndActiveTrue();
    }

    /**
     * Get students with 504 plan
     */
    public List<Student> getStudentsWith504() {
        return studentRepository.findByHas504TrueAndActiveTrue();
    }

    /**
     * Count active students
     */
    public long countActiveStudents() {
        return studentRepository.countByActiveTrue();
    }

    // ==================== Assignment Management ====================

    /**
     * Create new assignment
     */
    @Transactional
    public Assignment createAssignment(Assignment assignment) {
        log.info("Creating new assignment: {}", assignment.getName());
        assignment.setSyncStatus("pending");
        return assignmentRepository.save(assignment);
    }

    /**
     * Update assignment
     */
    @Transactional
    public Assignment updateAssignment(Assignment assignment) {
        log.info("Updating assignment: {}", assignment.getName());
        return assignmentRepository.save(assignment);
    }

    /**
     * Delete assignment
     */
    @Transactional
    public void deleteAssignment(Long assignmentId) {
        log.info("Deleting assignment ID: {}", assignmentId);
        assignmentRepository.deleteById(assignmentId);
    }

    /**
     * Get all active assignments
     */
    public List<Assignment> getAllActiveAssignments() {
        return assignmentRepository.findByActiveTrue();
    }

    /**
     * Get assignment by ID
     */
    public Optional<Assignment> getAssignmentById(Long id) {
        return assignmentRepository.findById(id);
    }

    /**
     * Get assignments by course
     */
    public List<Assignment> getAssignmentsByCourse(Long courseId) {
        return assignmentRepository.findByCourseIdAndActiveTrue(courseId);
    }

    /**
     * Get assignments by category
     */
    public List<Assignment> getAssignmentsByCategory(AssignmentCategory category) {
        return assignmentRepository.findByCategoryAndActiveTrue(category);
    }

    /**
     * Get overdue assignments
     */
    public List<Assignment> getOverdueAssignments() {
        return assignmentRepository.findOverdue(LocalDate.now());
    }

    // ==================== Grade Management ====================

    /**
     * Enter or update grade
     */
    @Transactional
    public Grade saveGrade(Grade grade) {
        log.info("Saving grade for student {} on assignment {}",
                 grade.getStudent().getFullName(),
                 grade.getAssignment().getName());

        grade.setSyncStatus("pending");
        grade.calculateLetterGrade();

        return gradeRepository.save(grade);
    }

    /**
     * Enter or update grade by IDs
     */
    @Transactional
    public Grade enterGrade(Long studentId, Long assignmentId, Double score, String notes) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentId));

        // Check if grade already exists
        Optional<Grade> existingGrade = gradeRepository.findByStudentAndAssignment(student, assignment);

        Grade grade;
        if (existingGrade.isPresent()) {
            grade = existingGrade.get();
            grade.setScore(score);
            grade.setNotes(notes);
        } else {
            grade = Grade.builder()
                .student(student)
                .assignment(assignment)
                .score(score)
                .notes(notes)
                .excused(false)
                .late(false)
                .missing(false)
                .syncStatus("pending")
                .build();
        }

        return saveGrade(grade);
    }

    /**
     * Mark assignment as missing
     */
    @Transactional
    public Grade markMissing(Long studentId, Long assignmentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));

        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));

        Optional<Grade> existingGrade = gradeRepository.findByStudentAndAssignment(student, assignment);

        Grade grade;
        if (existingGrade.isPresent()) {
            grade = existingGrade.get();
        } else {
            grade = Grade.builder()
                .student(student)
                .assignment(assignment)
                .build();
        }

        grade.setMissing(true);
        grade.setScore(0.0);

        return saveGrade(grade);
    }

    /**
     * Mark assignment as excused
     */
    @Transactional
    public Grade markExcused(Long studentId, Long assignmentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));

        Assignment assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new RuntimeException("Assignment not found"));

        Optional<Grade> existingGrade = gradeRepository.findByStudentAndAssignment(student, assignment);

        Grade grade;
        if (existingGrade.isPresent()) {
            grade = existingGrade.get();
        } else {
            grade = Grade.builder()
                .student(student)
                .assignment(assignment)
                .build();
        }

        grade.setExcused(true);
        grade.setScore(null);

        return saveGrade(grade);
    }

    /**
     * Get grade for student and assignment
     */
    public Optional<Grade> getGrade(Long studentId, Long assignmentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);

        if (student == null || assignment == null) {
            return Optional.empty();
        }

        return gradeRepository.findByStudentAndAssignment(student, assignment);
    }

    /**
     * Get all grades for student
     */
    public List<Grade> getGradesForStudent(Long studentId) {
        return gradeRepository.findByStudentId(studentId);
    }

    /**
     * Get all grades for assignment
     */
    public List<Grade> getGradesForAssignment(Long assignmentId) {
        return gradeRepository.findByAssignmentId(assignmentId);
    }

    /**
     * Delete grade
     */
    @Transactional
    public void deleteGrade(Long gradeId) {
        log.info("Deleting grade ID: {}", gradeId);
        gradeRepository.deleteById(gradeId);
    }

    // ==================== Statistics & Reports ====================

    /**
     * Calculate student GPA
     */
    public Double calculateStudentGPA(Long studentId) {
        return gradeRepository.calculateGpaByStudentId(studentId);
    }

    /**
     * Get assignment average
     */
    public Double getAssignmentAverage(Long assignmentId) {
        return gradeRepository.getAverageScoreByAssignmentId(assignmentId);
    }

    /**
     * Count missing assignments for student
     */
    public long countMissingAssignments(Long studentId) {
        List<Grade> missingGrades = gradeRepository.findMissingByStudentId(studentId);
        return missingGrades.size();
    }

    /**
     * Count failing grades for student
     */
    public long countFailingGrades(Long studentId) {
        List<Grade> failingGrades = gradeRepository.findFailingByStudentId(studentId);
        return failingGrades.size();
    }

    /**
     * Get students at risk (GPA < 2.0)
     */
    public List<Student> getStudentsAtRisk() {
        return studentRepository.findByGpaBelowThreshold(2.0);
    }

    // ==================== Sync Management ====================

    /**
     * Get all items needing sync
     */
    public long countItemsNeedingSync() {
        long students = studentRepository.findNeedingSync().size();
        long assignments = assignmentRepository.findNeedingSync().size();
        long grades = gradeRepository.findNeedingSync().size();

        return students + assignments + grades;
    }

    /**
     * Mark student as synced
     */
    @Transactional
    public void markStudentSynced(Long studentId) {
        studentRepository.findById(studentId).ifPresent(student -> {
            student.setSyncStatus("synced");
            studentRepository.save(student);
        });
    }

    /**
     * Mark assignment as synced
     */
    @Transactional
    public void markAssignmentSynced(Long assignmentId) {
        assignmentRepository.findById(assignmentId).ifPresent(assignment -> {
            assignment.setSyncStatus("synced");
            assignmentRepository.save(assignment);
        });
    }

    /**
     * Mark grade as synced
     */
    @Transactional
    public void markGradeSynced(Long gradeId) {
        gradeRepository.findById(gradeId).ifPresent(grade -> {
            grade.setSyncStatus("synced");
            gradeRepository.save(grade);
        });
    }

    // ==================== Category Management ====================

    /**
     * Create new assignment category
     */
    @Transactional
    public AssignmentCategory createCategory(AssignmentCategory category) {
        log.info("Creating new assignment category: {}", category.getName());
        category.setSyncStatus("pending");
        return categoryRepository.save(category);
    }

    /**
     * Update assignment category
     */
    @Transactional
    public AssignmentCategory updateCategory(AssignmentCategory category) {
        log.info("Updating assignment category: {}", category.getName());
        return categoryRepository.save(category);
    }

    /**
     * Delete assignment category
     */
    @Transactional
    public void deleteCategory(Long categoryId) {
        log.info("Deleting assignment category ID: {}", categoryId);
        categoryRepository.deleteById(categoryId);
    }

    /**
     * Get all active categories
     */
    public List<AssignmentCategory> getAllActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    /**
     * Get categories by course
     */
    public List<AssignmentCategory> getCategoriesByCourse(Long courseId) {
        return categoryRepository.findByCourseIdAndActiveTrueOrderByDisplayOrderAsc(courseId);
    }

    /**
     * Get category by ID
     */
    public Optional<AssignmentCategory> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    /**
     * Get global categories (apply to all courses)
     */
    public List<AssignmentCategory> getGlobalCategories() {
        return categoryRepository.findGlobalCategories();
    }

    /**
     * Mark category as synced
     */
    @Transactional
    public void markCategorySynced(Long categoryId) {
        categoryRepository.findById(categoryId).ifPresent(category -> {
            category.setSyncStatus("synced");
            categoryRepository.save(category);
        });
    }

    // ==================== Teacher Dashboard Support ====================

    /**
     * Get list of courses taught by current teacher.
     * Includes period-course labels from enrollment cache (e.g., "Period 1 - Algebra I (MATH101)")
     * plus any distinct course names from existing assignments.
     */
    public List<String> getTeacherCourses() {
        List<String> courses = new ArrayList<>();

        // Add period-course labels from enrollment cache
        List<String> periodLabels = studentEnrollmentCache.getPeriodCourseLabels();
        if (periodLabels != null && !periodLabels.isEmpty()) {
            courses.addAll(periodLabels);
        }

        // Also add distinct course names from assignments (legacy support)
        List<String> assignmentCourses = assignmentRepository.findAll().stream()
            .map(Assignment::getCourseName)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        for (String course : assignmentCourses) {
            if (!courses.contains(course)) {
                courses.add(course);
            }
        }

        return courses;
    }

    /**
     * Get students enrolled in a specific course
     * Based on students who have grades in assignments for that course
     */
    public List<Student> getStudentsByCourse(String courseName) {
        if (courseName == null || courseName.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Get all assignments for this course
        List<Assignment> courseAssignments = assignmentRepository.findAll().stream()
            .filter(a -> courseName.equals(a.getCourseName()))
            .collect(Collectors.toList());

        if (courseAssignments.isEmpty()) {
            return new ArrayList<>();
        }

        // Get students who have grades in these assignments
        Set<Student> students = new HashSet<>();
        for (Assignment assignment : courseAssignments) {
            List<Grade> grades = gradeRepository.findByAssignmentId(assignment.getId());
            for (Grade grade : grades) {
                if (grade.getStudent() != null) {
                    students.add(grade.getStudent());
                }
            }
        }

        return new ArrayList<>(students);
    }

    /**
     * Get students for a specific period from the enrollment cache.
     * Looks up local persisted students by studentId string.
     */
    public List<Student> getStudentsForPeriod(int period) {
        ClassRosterDTO roster = studentEnrollmentCache.getRoster(period);
        if (roster == null || roster.getStudents() == null) {
            return new ArrayList<>();
        }

        List<Student> students = new ArrayList<>();
        for (ClassRosterDTO.RosterStudentDTO rosterStudent : roster.getStudents()) {
            String studentNumber = rosterStudent.getStudentNumber();
            if (studentNumber != null) {
                studentRepository.findByStudentId(studentNumber).ifPresent(students::add);
            }
        }
        return students;
    }

    /**
     * Get active assignments for a specific period.
     */
    public List<Assignment> getAssignmentsForPeriod(int period) {
        return assignmentRepository.findByPeriodNumberAndActiveTrue(period);
    }

    /**
     * Count pending grades (assignments without grades)
     */
    public long getPendingGradesCount() {
        long totalAssignments = assignmentRepository.findByActiveTrue().size();
        long totalStudents = studentRepository.countByActiveTrue();
        long totalGrades = gradeRepository.count();

        // Expected grades = assignments * students
        long expectedGrades = totalAssignments * totalStudents;

        // Pending = expected - actual
        return Math.max(0, expectedGrades - totalGrades);
    }

    /**
     * Get recent grade entries
     * Returns formatted strings for display
     */
    public List<String> getRecentGrades(int limit) {
        List<Grade> recentGrades = gradeRepository.findAll().stream()
            .filter(g -> g.getScore() != null && !g.getExcused())
            .sorted((g1, g2) -> {
                // Sort by ID descending (most recent first)
                return Long.compare(g2.getId(), g1.getId());
            })
            .limit(limit)
            .collect(Collectors.toList());

        return recentGrades.stream()
            .map(g -> String.format("  %s: %.1f/%s on %s",
                g.getStudent() != null ? g.getStudent().getFullName() : "Unknown",
                g.getScore(),
                g.getAssignment() != null ? g.getAssignment().getMaxPoints() : "?",
                g.getAssignment() != null ? g.getAssignment().getName() : "Unknown Assignment"))
            .collect(Collectors.toList());
    }

    // ==================== Weighted Grade Calculations ====================

    /**
     * Calculate weighted final grade for a student in a course
     *
     * This method implements Canvas-like weighted grading:
     * 1. Groups assignments by category
     * 2. Applies drop lowest/highest rules per category
     * 3. Calculates category averages
     * 4. Applies category weights to compute final grade
     * 5. Handles extra credit categories
     *
     * @param studentId the student ID
     * @param courseId the course ID
     * @return WeightedGradeResult containing final grade and breakdown
     */
    public WeightedGradeResult calculateWeightedGrade(Long studentId, Long courseId) {
        log.debug("Calculating weighted grade for student {} in course {}", studentId, courseId);

        // Get all grades for student in this course
        List<Grade> allGrades = gradeRepository.findByStudentId(studentId).stream()
            .filter(g -> g.getAssignment() != null
                && g.getAssignment().getCourseId() != null
                && g.getAssignment().getCourseId().equals(courseId))
            .collect(Collectors.toList());

        if (allGrades.isEmpty()) {
            return WeightedGradeResult.empty();
        }

        // Get categories for this course
        List<AssignmentCategory> categories = getCategoriesByCourse(courseId);

        // Group grades by category
        Map<AssignmentCategory, List<Grade>> gradesByCategory = new HashMap<>();
        List<Grade> uncategorizedGrades = new ArrayList<>();

        for (Grade grade : allGrades) {
            Assignment assignment = grade.getAssignment();
            if (assignment.getCategory() != null) {
                gradesByCategory
                    .computeIfAbsent(assignment.getCategory(), k -> new ArrayList<>())
                    .add(grade);
            } else {
                uncategorizedGrades.add(grade);
            }
        }

        // Calculate category averages
        Map<AssignmentCategory, Double> categoryAverages = new HashMap<>();
        Map<AssignmentCategory, Integer> categoryPointsEarned = new HashMap<>();
        Map<AssignmentCategory, Integer> categoryPointsPossible = new HashMap<>();

        for (Map.Entry<AssignmentCategory, List<Grade>> entry : gradesByCategory.entrySet()) {
            AssignmentCategory category = entry.getKey();
            List<Grade> categoryGrades = entry.getValue();

            // Apply drop lowest/highest logic
            List<Grade> gradesToCount = applyDropRules(categoryGrades, category);

            // Calculate category average
            double totalPoints = 0.0;
            double totalPossible = 0.0;

            for (Grade grade : gradesToCount) {
                if (grade.getScore() != null && !grade.getExcused() && !grade.getMissing()) {
                    totalPoints += grade.getScore();
                    totalPossible += grade.getAssignment().getMaxPoints();
                } else if (grade.getMissing()) {
                    // Missing assignments count as 0
                    totalPossible += grade.getAssignment().getMaxPoints();
                }
            }

            if (totalPossible > 0) {
                double categoryAverage = (totalPoints / totalPossible) * 100.0;
                categoryAverages.put(category, categoryAverage);
                categoryPointsEarned.put(category, (int) totalPoints);
                categoryPointsPossible.put(category, (int) totalPossible);
            }
        }

        // Calculate weighted final grade
        double finalGrade = 0.0;
        double totalWeight = 0.0;
        double extraCreditPoints = 0.0;

        for (Map.Entry<AssignmentCategory, Double> entry : categoryAverages.entrySet()) {
            AssignmentCategory category = entry.getKey();
            Double categoryAverage = entry.getValue();

            if (category.getIsExtraCredit()) {
                // Extra credit adds bonus points
                extraCreditPoints += categoryAverage * (category.getWeight() != null ? category.getWeight() : 0.0);
            } else {
                // Regular category contributes to weighted average
                double weight = category.getWeight() != null ? category.getWeight() : 0.0;
                finalGrade += categoryAverage * weight;
                totalWeight += weight;
            }
        }

        // If total weight < 1.0, normalize the grade
        if (totalWeight > 0 && totalWeight < 1.0) {
            finalGrade = finalGrade / totalWeight;
        }

        // Add extra credit
        finalGrade += extraCreditPoints;

        // Cap at 100% unless extra credit pushes it higher
        if (extraCreditPoints == 0) {
            finalGrade = Math.min(finalGrade, 100.0);
        }

        return WeightedGradeResult.builder()
            .finalGrade(finalGrade)
            .categoryAverages(categoryAverages)
            .categoryPointsEarned(categoryPointsEarned)
            .categoryPointsPossible(categoryPointsPossible)
            .totalWeight(totalWeight)
            .extraCreditPoints(extraCreditPoints)
            .build();
    }

    /**
     * Apply drop lowest/highest rules to grades in a category
     */
    private List<Grade> applyDropRules(List<Grade> grades, AssignmentCategory category) {
        if (grades.isEmpty()) {
            return grades;
        }

        // Filter out excused grades - they don't participate in drop rules
        List<Grade> validGrades = grades.stream()
            .filter(g -> !g.getExcused())
            .collect(Collectors.toList());

        if (validGrades.size() <= category.getTotalScoresToDrop()) {
            // Can't drop all grades - return all valid grades
            return validGrades;
        }

        // Sort by percentage score
        List<Grade> sorted = new ArrayList<>(validGrades);
        sorted.sort((g1, g2) -> {
            Double pct1 = g1.getPercentage() != null ? g1.getPercentage() : 0.0;
            Double pct2 = g2.getPercentage() != null ? g2.getPercentage() : 0.0;
            return Double.compare(pct1, pct2);
        });

        // Drop lowest N
        int dropLowest = category.getDropLowest() != null ? category.getDropLowest() : 0;
        if (dropLowest > 0) {
            sorted = sorted.subList(dropLowest, sorted.size());
        }

        // Drop highest N
        int dropHighest = category.getDropHighest() != null ? category.getDropHighest() : 0;
        if (dropHighest > 0 && sorted.size() > dropHighest) {
            sorted = sorted.subList(0, sorted.size() - dropHighest);
        }

        return sorted;
    }

    /**
     * Result object for weighted grade calculation
     */
    @lombok.Data
    @lombok.Builder
    public static class WeightedGradeResult {
        private Double finalGrade;
        private Map<AssignmentCategory, Double> categoryAverages;
        private Map<AssignmentCategory, Integer> categoryPointsEarned;
        private Map<AssignmentCategory, Integer> categoryPointsPossible;
        private Double totalWeight;
        private Double extraCreditPoints;

        public static WeightedGradeResult empty() {
            return WeightedGradeResult.builder()
                .finalGrade(0.0)
                .categoryAverages(new HashMap<>())
                .categoryPointsEarned(new HashMap<>())
                .categoryPointsPossible(new HashMap<>())
                .totalWeight(0.0)
                .extraCreditPoints(0.0)
                .build();
        }

        public String getLetterGrade() {
            if (finalGrade == null) return "N/A";
            if (finalGrade >= 90) return "A";
            if (finalGrade >= 80) return "B";
            if (finalGrade >= 70) return "C";
            if (finalGrade >= 60) return "D";
            return "F";
        }

        public String getFormattedGrade() {
            if (finalGrade == null) return "N/A";
            return String.format("%.2f%% (%s)", finalGrade, getLetterGrade());
        }
    }
}
