package com.heronix.teacher.service;

import com.heronix.teacher.model.domain.Club;
import com.heronix.teacher.model.domain.Student;
import com.heronix.teacher.repository.ClubRepository;
import com.heronix.teacher.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Club Service
 *
 * Business logic for clubs and extracurricular activities
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClubService {

    private final ClubRepository clubRepository;
    private final StudentRepository studentRepository;

    // ==================== Club CRUD Operations ====================

    /**
     * Get all active clubs
     */
    public List<Club> getAllActiveClubs() {
        return clubRepository.findByActiveTrueOrderByNameAsc();
    }

    /**
     * Get club by ID
     */
    public Optional<Club> getClubById(Long id) {
        return clubRepository.findById(id);
    }

    /**
     * Get club by name
     */
    public Optional<Club> getClubByName(String name) {
        return clubRepository.findByName(name);
    }

    /**
     * Create new club
     */
    @Transactional
    public Club createClub(Club club) {
        // Check for duplicate name
        if (clubRepository.findByName(club.getName()).isPresent()) {
            throw new RuntimeException("Club with this name already exists");
        }

        Club saved = clubRepository.save(club);
        log.info("Created new club: {}", club.getName());
        return saved;
    }

    /**
     * Update existing club
     */
    @Transactional
    public Club updateClub(Long id, Club updatedClub) {
        Club existing = clubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        existing.setName(updatedClub.getName());
        existing.setDescription(updatedClub.getDescription());
        existing.setCategory(updatedClub.getCategory());
        existing.setAdvisorName(updatedClub.getAdvisorName());
        existing.setMeetingDay(updatedClub.getMeetingDay());
        existing.setMeetingTime(updatedClub.getMeetingTime());
        existing.setDurationMinutes(updatedClub.getDurationMinutes());
        existing.setLocation(updatedClub.getLocation());
        existing.setMaxCapacity(updatedClub.getMaxCapacity());
        existing.setRequiresApproval(updatedClub.getRequiresApproval());
        existing.setStartDate(updatedClub.getStartDate());
        existing.setEndDate(updatedClub.getEndDate());
        existing.setNotes(updatedClub.getNotes());

        Club saved = clubRepository.save(existing);
        log.info("Updated club: {}", existing.getName());
        return saved;
    }

    /**
     * Delete club (soft delete - mark inactive)
     */
    @Transactional
    public void deleteClub(Long id) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        club.setActive(false);
        clubRepository.save(club);
        log.info("Deactivated club: {}", club.getName());
    }

    // ==================== Membership Management ====================

    /**
     * Add student to club
     */
    @Transactional
    public void addStudentToClub(Long clubId, Long studentId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (club.isAtCapacity()) {
            throw new RuntimeException("Club is at maximum capacity");
        }

        club.addMember(student);
        clubRepository.save(club);
        log.info("Added student {} to club {}", student.getFullName(), club.getName());
    }

    /**
     * Remove student from club
     */
    @Transactional
    public void removeStudentFromClub(Long clubId, Long studentId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        club.removeMember(student);
        clubRepository.save(club);
        log.info("Removed student {} from club {}", student.getFullName(), club.getName());
    }

    /**
     * Get club members
     */
    public Set<Student> getClubMembers(Long clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        return club.getMembers();
    }

    /**
     * Get clubs for a student
     */
    public List<Club> getStudentClubs(Long studentId) {
        return clubRepository.findByStudentId(studentId);
    }

    /**
     * Check if student is in club
     */
    public boolean isStudentInClub(Long clubId, Long studentId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        return club.getMembers().stream()
                .anyMatch(member -> member.getId().equals(studentId));
    }

    // ==================== Filtering and Search ====================

    /**
     * Get clubs by category
     */
    public List<Club> getClubsByCategory(String category) {
        return clubRepository.findByCategoryAndActiveTrueOrderByNameAsc(category);
    }

    /**
     * Get clubs by advisor
     */
    public List<Club> getClubsByAdvisor(String advisorName) {
        return clubRepository.findByAdvisorNameOrderByNameAsc(advisorName);
    }

    /**
     * Get clubs by meeting day
     */
    public List<Club> getClubsByMeetingDay(String meetingDay) {
        return clubRepository.findByMeetingDayAndActiveTrueOrderByMeetingTimeAsc(meetingDay);
    }

    /**
     * Get clubs with available spots
     */
    public List<Club> getClubsWithAvailability() {
        return clubRepository.findClubsWithAvailability();
    }

    /**
     * Get clubs at capacity
     */
    public List<Club> getClubsAtCapacity() {
        return clubRepository.findClubsAtCapacity();
    }

    /**
     * Search clubs by name or description
     */
    public List<Club> searchClubs(String searchTerm) {
        return clubRepository.searchClubs(searchTerm);
    }

    /**
     * Get clubs requiring approval
     */
    public List<Club> getClubsRequiringApproval() {
        return clubRepository.findByRequiresApprovalTrueAndActiveTrueOrderByNameAsc();
    }

    /**
     * Get active clubs for a date (within seasonal range)
     */
    public List<Club> getActiveClubsForDate(LocalDate date) {
        return clubRepository.findActiveForDate(date);
    }

    /**
     * Get clubs with upcoming meetings
     */
    public List<Club> getClubsWithUpcomingMeetings() {
        return clubRepository.findWithUpcomingMeetings(LocalDate.now());
    }

    // ==================== Statistics and Analytics ====================

    /**
     * Get distinct categories
     */
    public List<String> getAllCategories() {
        return clubRepository.findDistinctCategories();
    }

    /**
     * Get distinct meeting days
     */
    public List<String> getAllMeetingDays() {
        return clubRepository.findDistinctMeetingDays();
    }

    /**
     * Count active clubs
     */
    public long countActiveClubs() {
        return clubRepository.countByActiveTrue();
    }

    /**
     * Count clubs by category
     */
    public long countClubsByCategory(String category) {
        return clubRepository.countByCategoryAndActiveTrue(category);
    }

    /**
     * Get club statistics
     */
    public Map<String, Object> getClubStatistics() {
        List<Club> allClubs = clubRepository.findByActiveTrueOrderByNameAsc();

        long totalClubs = allClubs.size();
        long totalMembers = allClubs.stream()
                .mapToLong(club -> club.getCurrentEnrollment() != null ? club.getCurrentEnrollment() : 0)
                .sum();

        long clubsAtCapacity = clubRepository.findClubsAtCapacity().size();
        long clubsWithSpace = clubRepository.findClubsWithAvailability().size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClubs", totalClubs);
        stats.put("totalMembers", totalMembers);
        stats.put("clubsAtCapacity", clubsAtCapacity);
        stats.put("clubsWithSpace", clubsWithSpace);
        stats.put("averageMembership", totalClubs > 0 ? (double) totalMembers / totalClubs : 0.0);

        return stats;
    }

    /**
     * Get category distribution
     */
    public Map<String, Long> getCategoryDistribution() {
        List<Club> allClubs = clubRepository.findByActiveTrueOrderByNameAsc();

        return allClubs.stream()
                .filter(club -> club.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Club::getCategory,
                        Collectors.counting()
                ));
    }

    /**
     * Get most popular clubs (by membership count)
     */
    public List<Club> getMostPopularClubs(int limit) {
        List<Club> allClubs = clubRepository.findByActiveTrueOrderByNameAsc();

        return allClubs.stream()
                .sorted((c1, c2) -> {
                    int count1 = c1.getCurrentEnrollment() != null ? c1.getCurrentEnrollment() : 0;
                    int count2 = c2.getCurrentEnrollment() != null ? c2.getCurrentEnrollment() : 0;
                    return Integer.compare(count2, count1);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get student's club count
     */
    public long getStudentClubCount(Long studentId) {
        return clubRepository.countStudentMemberships(studentId);
    }

    // ==================== Club Attendance ====================

    /**
     * Save club attendance record
     *
     * @param clubId Club ID
     * @param studentId Student ID
     * @param date Attendance date
     * @param status Attendance status (PRESENT, ABSENT, EXCUSED)
     * @param notes Optional notes
     */
    @Transactional
    public void saveClubAttendance(Long clubId, Long studentId, LocalDate date,
                                   String status, String notes) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found: " + clubId));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        // Verify student is a member of the club
        if (!isStudentInClub(clubId, studentId)) {
            throw new RuntimeException("Student is not a member of this club");
        }

        // Store attendance in club notes/metadata for now
        // In a full implementation, this would use a dedicated ClubAttendance entity
        String attendanceNote = String.format("[%s] %s - %s: %s%s",
                date,
                student.getFullName(),
                status,
                notes != null && !notes.isEmpty() ? notes : "No notes",
                System.lineSeparator());

        String currentNotes = club.getNotes() != null ? club.getNotes() : "";
        club.setNotes(currentNotes + attendanceNote);
        clubRepository.save(club);

        log.debug("Saved club attendance: club={}, student={}, date={}, status={}",
                club.getName(), student.getFullName(), date, status);
    }

    // ==================== Sync Support ====================

    /**
     * Get clubs needing sync
     */
    public List<Club> getClubsNeedingSync() {
        return clubRepository.findNeedingSync();
    }

    /**
     * Mark club as synced
     */
    @Transactional
    public void markSynced(Long id) {
        clubRepository.findById(id).ifPresent(club -> {
            club.setSyncStatus("synced");
            clubRepository.save(club);
        });
    }
}
