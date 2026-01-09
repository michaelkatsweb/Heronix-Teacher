package com.heronix.teacher.model.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Club Entity
 *
 * Manages after-school clubs, activities, and extracurricular organizations
 *
 * Features:
 * - Club information and schedule
 * - Student membership tracking
 * - Meeting management
 * - Capacity limits
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
@Entity
@Table(name = "clubs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Club name
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Club description
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Club category (Academic, Sports, Arts, Service, etc.)
     */
    @Column(name = "category")
    private String category;

    /**
     * Teacher/Advisor name
     */
    @Column(name = "advisor_name")
    private String advisorName;

    /**
     * Meeting day of week (MONDAY, TUESDAY, etc.)
     */
    @Column(name = "meeting_day")
    private String meetingDay;

    /**
     * Meeting start time
     */
    @Column(name = "meeting_time")
    private LocalTime meetingTime;

    /**
     * Meeting duration in minutes
     */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /**
     * Meeting location/room
     */
    @Column(name = "location")
    private String location;

    /**
     * Maximum capacity
     */
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    /**
     * Current enrollment count
     */
    @Column(name = "current_enrollment")
    private Integer currentEnrollment = 0;

    /**
     * Active status
     */
    @Column(name = "active")
    private Boolean active = true;

    /**
     * Requires application/approval
     */
    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;

    /**
     * Club start date (for seasonal clubs)
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * Club end date (for seasonal clubs)
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Next meeting date
     */
    @Column(name = "next_meeting_date")
    private LocalDate nextMeetingDate;

    /**
     * Notes about the club
     */
    @Column(name = "notes", length = 2000)
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

    /**
     * Student members (many-to-many relationship)
     * Note: Using Set to avoid duplicates
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "club_memberships",
        joinColumns = @JoinColumn(name = "club_id"),
        inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    @Builder.Default
    private Set<Student> members = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currentEnrollment == null) {
            currentEnrollment = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if club is at capacity
     */
    public boolean isAtCapacity() {
        if (maxCapacity == null) return false;
        return currentEnrollment != null && currentEnrollment >= maxCapacity;
    }

    /**
     * Check if club has available spots
     */
    public int getAvailableSpots() {
        if (maxCapacity == null) return Integer.MAX_VALUE;
        return Math.max(0, maxCapacity - (currentEnrollment != null ? currentEnrollment : 0));
    }

    /**
     * Get formatted meeting schedule
     */
    public String getMeetingSchedule() {
        if (meetingDay == null || meetingTime == null) {
            return "Schedule TBD";
        }
        return String.format("%ss at %s", meetingDay, meetingTime);
    }

    /**
     * Check if club is currently active (within start/end dates)
     */
    public boolean isCurrentlyActive() {
        if (!active) return false;

        LocalDate today = LocalDate.now();
        if (startDate != null && today.isBefore(startDate)) return false;
        if (endDate != null && today.isAfter(endDate)) return false;

        return true;
    }

    /**
     * Get status display string
     */
    public String getStatusDisplay() {
        if (!active) return "Inactive";
        if (isAtCapacity()) return "Full";
        if (startDate != null && LocalDate.now().isBefore(startDate)) return "Upcoming";
        if (endDate != null && LocalDate.now().isAfter(endDate)) return "Completed";
        return "Active";
    }

    /**
     * Add member to club
     */
    public void addMember(Student student) {
        members.add(student);
        currentEnrollment = members.size();
    }

    /**
     * Remove member from club
     */
    public void removeMember(Student student) {
        members.remove(student);
        currentEnrollment = members.size();
    }
}
