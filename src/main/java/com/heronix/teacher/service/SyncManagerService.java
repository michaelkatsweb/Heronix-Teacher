package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.*;
import com.heronix.teacher.model.dto.*;
import com.heronix.teacher.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sync Manager Service
 * Orchestrates bidirectional sync between Heronix-Teacher and EduScheduler-Pro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncManagerService {

    private final AdminApiClient adminApiClient;
    private final StudentRepository studentRepository;
    private final AssignmentRepository assignmentRepository;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final SessionManager sessionManager;

    // ========================================================================
    // FULL SYNC WORKFLOW
    // ========================================================================

    /**
     * Perform full bidirectional sync
     * 1. Pull latest data from server (students, assignments)
     * 2. Push local changes to server (grades, attendance)
     * 3. Handle conflicts
     */
    @Transactional
    public SyncResult performFullSync() {
        log.info("Starting full sync operation");
        SyncResult result = new SyncResult();

        try {
            // Check if authenticated
            if (!adminApiClient.isAuthenticated()) {
                result.setSuccess(false);
                result.setMessage("Not authenticated - please log in");
                return result;
            }

            // Check server connectivity
            if (!adminApiClient.isServerReachable()) {
                result.setSuccess(false);
                result.setMessage("Server not reachable");
                return result;
            }

            // Step 1: Pull students from server
            log.info("Pulling students from server");
            int studentsUpdated = pullStudents();
            result.setStudentsSynced(studentsUpdated);

            // Step 2: Pull assignments from server
            log.info("Pulling assignments from server");
            int assignmentsUpdated = pullAssignments();
            result.setAssignmentsSynced(assignmentsUpdated);

            // Step 3: Push pending grades to server
            log.info("Pushing pending grades to server");
            int gradesPushed = pushPendingGrades();
            result.setGradesSynced(gradesPushed);

            // Step 4: Push pending attendance to server
            log.info("Pushing pending attendance to server");
            int attendancePushed = pushPendingAttendance();
            result.setAttendanceSynced(attendancePushed);

            // Step 5: Check for conflicts
            log.info("Checking for conflicts");
            List<ConflictDTO> conflicts = adminApiClient.getGradeConflicts();
            result.setConflicts(conflicts);
            result.setConflictCount(conflicts.size());

            result.setSuccess(true);
            result.setMessage(String.format(
                    "Sync complete: %d students, %d assignments, %d grades, %d attendance",
                    studentsUpdated, assignmentsUpdated, gradesPushed, attendancePushed));
            result.setLastSyncTime(LocalDateTime.now());

            log.info("Full sync completed successfully");

        } catch (Exception e) {
            log.error("Error during sync", e);
            result.setSuccess(false);
            result.setMessage("Sync failed: " + e.getMessage());
        }

        return result;
    }

    // ========================================================================
    // PULL OPERATIONS (From Server)
    // ========================================================================

    /**
     * Pull students from server and merge with local data
     */
    private int pullStudents() throws Exception {
        List<StudentDTO> serverStudents = adminApiClient.getStudents();
        int updated = 0;

        for (StudentDTO dto : serverStudents) {
            Optional<Student> existingOpt = studentRepository.findByStudentId(dto.getStudentId());

            if (existingOpt.isPresent()) {
                // Update existing student
                Student existing = existingOpt.get();

                // Only update if server data is newer
                if (dto.getLastModified() != null &&
                    (existing.getLastModified() == null ||
                     dto.getLastModified().isAfter(existing.getLastModified()))) {

                    updateStudentFromDTO(existing, dto);
                    existing.setSyncStatus("synced");
                    studentRepository.save(existing);
                    updated++;
                }

            } else {
                // Create new student
                Student newStudent = createStudentFromDTO(dto);
                newStudent.setSyncStatus("synced");
                studentRepository.save(newStudent);
                updated++;
            }
        }

        return updated;
    }

    /**
     * Pull assignments from server
     */
    private int pullAssignments() throws Exception {
        List<AssignmentDTO> serverAssignments = adminApiClient.getAssignments();
        int updated = 0;

        for (AssignmentDTO dto : serverAssignments) {
            Optional<Assignment> existingOpt = assignmentRepository.findById(dto.getId());

            if (existingOpt.isPresent()) {
                // Update existing assignment
                Assignment existing = existingOpt.get();

                if (dto.getModifiedDate() != null &&
                    (existing.getModifiedDate() == null ||
                     dto.getModifiedDate().isAfter(existing.getModifiedDate()))) {

                    updateAssignmentFromDTO(existing, dto);
                    existing.setSyncStatus("synced");
                    assignmentRepository.save(existing);
                    updated++;
                }

            } else {
                // Create new assignment
                Assignment newAssignment = createAssignmentFromDTO(dto);
                newAssignment.setSyncStatus("synced");
                assignmentRepository.save(newAssignment);
                updated++;
            }
        }

        return updated;
    }

    // ========================================================================
    // PUSH OPERATIONS (To Server)
    // ========================================================================

    /**
     * Push pending grades to server
     */
    private int pushPendingGrades() throws Exception {
        List<Grade> pendingGrades = gradeRepository.findNeedingSync();

        if (pendingGrades.isEmpty()) {
            return 0;
        }

        // Convert to DTOs
        List<GradeDTO> gradeDTOs = pendingGrades.stream()
                .map(this::convertGradeToDTO)
                .collect(Collectors.toList());

        // Submit to server
        adminApiClient.submitGrades(gradeDTOs);

        // Mark as synced locally
        List<Long> syncedIds = new ArrayList<>();
        for (Grade grade : pendingGrades) {
            grade.setSyncStatus("synced");
            gradeRepository.save(grade);
            syncedIds.add(grade.getId());
        }

        // Notify server of successful sync
        adminApiClient.markSynced(syncedIds, new ArrayList<>());

        return pendingGrades.size();
    }

    /**
     * Push pending attendance to server
     */
    private int pushPendingAttendance() throws Exception {
        List<Attendance> pendingAttendance = attendanceRepository.findNeedingSync();

        if (pendingAttendance.isEmpty()) {
            return 0;
        }

        // Convert to DTOs
        List<AttendanceDTO> attendanceDTOs = pendingAttendance.stream()
                .map(this::convertAttendanceToDTO)
                .collect(Collectors.toList());

        // Submit to server
        adminApiClient.submitAttendance(attendanceDTOs);

        // Mark as synced locally
        List<Long> syncedIds = new ArrayList<>();
        for (Attendance attendance : pendingAttendance) {
            attendance.setSyncStatus("synced");
            attendanceRepository.save(attendance);
            syncedIds.add(attendance.getId());
        }

        // Notify server of successful sync
        adminApiClient.markSynced(new ArrayList<>(), syncedIds);

        return pendingAttendance.size();
    }

    // ========================================================================
    // CONVERSION METHODS (Entity <-> DTO)
    // ========================================================================

    private void updateStudentFromDTO(Student student, StudentDTO dto) {
        student.setFirstName(dto.getFirstName());
        student.setLastName(dto.getLastName());
        student.setGradeLevel(dto.getGradeLevel());
        student.setEmail(dto.getEmail());
        student.setDateOfBirth(dto.getDateOfBirth());
        student.setCurrentGpa(dto.getCurrentGpa());
        student.setActive(dto.getActive());
        student.setHasIep(dto.getHasIep());
        student.setHas504(dto.getHas504());
        if (dto.getNotes() != null) {
            student.setNotes(dto.getNotes());
        }
        student.setLastModified(dto.getLastModified());
    }

    private Student createStudentFromDTO(StudentDTO dto) {
        return Student.builder()
                .studentId(dto.getStudentId())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .gradeLevel(dto.getGradeLevel())
                .email(dto.getEmail())
                .dateOfBirth(dto.getDateOfBirth())
                .currentGpa(dto.getCurrentGpa())
                .active(dto.getActive())
                .hasIep(dto.getHasIep())
                .has504(dto.getHas504())
                .notes(dto.getNotes())
                .lastModified(dto.getLastModified())
                .createdDate(LocalDateTime.now())
                .build();
    }

    private void updateAssignmentFromDTO(Assignment assignment, AssignmentDTO dto) {
        assignment.setName(dto.getName());
        assignment.setDescription(dto.getDescription());
        // Set category name (not object - will be resolved later)
        assignment.setCategoryName(dto.getCategory());
        assignment.setMaxPoints(dto.getMaxPoints());
        assignment.setWeight(dto.getWeight());
        assignment.setDueDate(dto.getDueDate());
        assignment.setActive(dto.getActive());
        assignment.setModifiedDate(dto.getModifiedDate());
    }

    private Assignment createAssignmentFromDTO(AssignmentDTO dto) {
        return Assignment.builder()
                .courseId(dto.getCourseId())
                .courseName(dto.getCourseName())
                .name(dto.getName())
                .description(dto.getDescription())
                .categoryName(dto.getCategory()) // Set category name
                .maxPoints(dto.getMaxPoints())
                .weight(dto.getWeight())
                .dueDate(dto.getDueDate())
                .active(dto.getActive())
                .createdDate(LocalDateTime.now())
                .modifiedDate(dto.getModifiedDate())
                .build();
    }

    private GradeDTO convertGradeToDTO(Grade grade) {
        return GradeDTO.builder()
                .id(grade.getId())
                .studentId(grade.getStudent().getId())
                .assignmentId(grade.getAssignment().getId())
                .score(grade.getScore())
                .letterGrade(grade.getLetterGrade())
                .gpaPoints(grade.getGpaPoints())
                .notes(grade.getNotes())
                .excused(grade.getExcused())
                .late(grade.getLate())
                .missing(grade.getMissing())
                .dateEntered(grade.getDateEntered())
                .modifiedDate(grade.getModifiedDate())
                .build();
    }

    private AttendanceDTO convertAttendanceToDTO(Attendance attendance) {
        return AttendanceDTO.builder()
                .id(attendance.getId())
                .studentId(attendance.getStudent().getId())
                .courseId(null) // Not in current model - could add later
                .date(attendance.getAttendanceDate())
                .status(attendance.getStatus())
                .notes(attendance.getNotes())
                .timeIn(attendance.getArrivalTime())
                .timeOut(null) // Not in current model
                .createdDate(LocalDateTime.now()) // Use current timestamp
                .build();
    }

    // ========================================================================
    // SYNC STATUS CHECKS
    // ========================================================================

    /**
     * Get count of items needing sync
     */
    public SyncStatusInfo getLocalSyncStatus() {
        int pendingGrades = gradeRepository.findNeedingSync().size();
        int pendingAttendance = attendanceRepository.findNeedingSync().size();

        return new SyncStatusInfo(pendingGrades, pendingAttendance);
    }

    /**
     * Check if sync is needed
     */
    public boolean isSyncNeeded() {
        SyncStatusInfo status = getLocalSyncStatus();
        return status.pendingGrades > 0 || status.pendingAttendance > 0;
    }

    // ========================================================================
    // RESULT CLASSES
    // ========================================================================

    public static class SyncResult {
        private boolean success;
        private String message;
        private int studentsSynced;
        private int assignmentsSynced;
        private int gradesSynced;
        private int attendanceSynced;
        private int conflictCount;
        private List<ConflictDTO> conflicts;
        private LocalDateTime lastSyncTime;

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public int getStudentsSynced() { return studentsSynced; }
        public void setStudentsSynced(int studentsSynced) { this.studentsSynced = studentsSynced; }

        public int getAssignmentsSynced() { return assignmentsSynced; }
        public void setAssignmentsSynced(int assignmentsSynced) { this.assignmentsSynced = assignmentsSynced; }

        public int getGradesSynced() { return gradesSynced; }
        public void setGradesSynced(int gradesSynced) { this.gradesSynced = gradesSynced; }

        public int getAttendanceSynced() { return attendanceSynced; }
        public void setAttendanceSynced(int attendanceSynced) { this.attendanceSynced = attendanceSynced; }

        public int getConflictCount() { return conflictCount; }
        public void setConflictCount(int conflictCount) { this.conflictCount = conflictCount; }

        public List<ConflictDTO> getConflicts() { return conflicts; }
        public void setConflicts(List<ConflictDTO> conflicts) { this.conflicts = conflicts; }

        public LocalDateTime getLastSyncTime() { return lastSyncTime; }
        public void setLastSyncTime(LocalDateTime lastSyncTime) { this.lastSyncTime = lastSyncTime; }
    }

    public static class SyncStatusInfo {
        public final int pendingGrades;
        public final int pendingAttendance;

        public SyncStatusInfo(int pendingGrades, int pendingAttendance) {
            this.pendingGrades = pendingGrades;
            this.pendingAttendance = pendingAttendance;
        }

        public int getTotal() {
            return pendingGrades + pendingAttendance;
        }
    }
}
