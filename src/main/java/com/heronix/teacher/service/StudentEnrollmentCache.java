package com.heronix.teacher.service;

import com.heronix.teacher.model.dto.ClassRosterDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Shared in-memory cache for period -> course -> students mapping.
 * Populated from AttendanceController after roster sync, consumed by
 * GradebookController and AssignmentDialogController.
 */
@Service
@Slf4j
public class StudentEnrollmentCache {

    private final Map<Integer, ClassRosterDTO> periodRosters = new ConcurrentHashMap<>();

    public void updateRosters(Map<Integer, ClassRosterDTO> rosters) {
        periodRosters.clear();
        periodRosters.putAll(rosters);
        log.info("Enrollment cache updated with {} period rosters", rosters.size());
    }

    public Map<Integer, ClassRosterDTO> getAllRosters() {
        return Collections.unmodifiableMap(periodRosters);
    }

    public ClassRosterDTO getRoster(int period) {
        return periodRosters.get(period);
    }

    /**
     * Returns labels like "Period 1 - Algebra I (MATH101)" for UI combo boxes.
     */
    public List<String> getPeriodCourseLabels() {
        return periodRosters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int period = entry.getKey();
                    ClassRosterDTO roster = entry.getValue();
                    String periodDisplay = period == 0 ? "Homeroom" : "Period " + period;
                    String courseName = roster.getCourseName() != null ? roster.getCourseName() : "Unknown";
                    String courseCode = roster.getCourseCode() != null ? " (" + roster.getCourseCode() + ")" : "";
                    return periodDisplay + " - " + courseName + courseCode;
                })
                .collect(Collectors.toList());
    }

    /**
     * Parse period number from a label like "Period 1 - Algebra I (MATH101)".
     * Returns null if parsing fails.
     */
    public static Integer parsePeriodFromLabel(String label) {
        if (label == null) return null;
        if (label.startsWith("Homeroom")) return 0;
        if (label.startsWith("Period ")) {
            try {
                String numStr = label.substring("Period ".length(), label.indexOf(" - "));
                return Integer.parseInt(numStr.trim());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
