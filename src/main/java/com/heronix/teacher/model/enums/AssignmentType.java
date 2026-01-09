package com.heronix.teacher.model.enums;

/**
 * Assignment type enumeration
 *
 * Defines different types of assignments used in education:
 * - Daily lessons, quizzes, tests, exams, formative/summative assessments, etc.
 * Similar to Canvas LMS assignment types
 *
 * @author EduScheduler Team
 * @version 1.0.0
 */
public enum AssignmentType {

    /**
     * Daily homework assignments
     */
    HOMEWORK("Homework"),

    /**
     * Quiz (short assessment)
     */
    QUIZ("Quiz"),

    /**
     * Test (formal assessment)
     */
    TEST("Test"),

    /**
     * Exam (major assessment)
     */
    EXAM("Exam"),

    /**
     * Long-term project
     */
    PROJECT("Project"),

    /**
     * Lab work (science, computer science, etc.)
     */
    LAB("Lab"),

    /**
     * Class participation
     */
    PARTICIPATION("Participation"),

    /**
     * Formative assessment (ongoing, informal)
     */
    FORMATIVE("Formative Assessment"),

    /**
     * Summative assessment (end of unit/term evaluation)
     */
    SUMMATIVE("Summative Assessment"),

    /**
     * Essay or written assignment
     */
    ESSAY("Essay"),

    /**
     * Presentation or oral assignment
     */
    PRESENTATION("Presentation"),

    /**
     * Discussion board/forum participation
     */
    DISCUSSION("Discussion"),

    /**
     * Practice/drill assignment
     */
    PRACTICE("Practice"),

    /**
     * In-class work/classwork (common K-12)
     */
    CLASSWORK("Classwork"),

    /**
     * Journal or notebook entries (common elementary/middle school)
     */
    JOURNAL("Journal/Notebook"),

    /**
     * Performance assessment (music, PE, drama, art)
     */
    PERFORMANCE("Performance"),

    /**
     * Portfolio assessment (collection of work over time)
     */
    PORTFOLIO("Portfolio"),

    /**
     * Benchmark assessment (district/state standardized)
     */
    BENCHMARK("Benchmark Assessment"),

    /**
     * Checkpoint/progress check (mid-unit assessment)
     */
    CHECKPOINT("Checkpoint"),

    /**
     * Review assignment (test prep, study guide)
     */
    REVIEW("Review"),

    /**
     * Group project/collaborative work
     */
    GROUP_PROJECT("Group Project"),

    /**
     * Reading assignment (logs, comprehension, book reports)
     */
    READING("Reading"),

    /**
     * Bell ringer/warm-up (start of class activity)
     */
    BELL_RINGER("Bell Ringer/Warm-up"),

    /**
     * Exit ticket (end of class check for understanding)
     */
    EXIT_TICKET("Exit Ticket"),

    /**
     * Research paper (common high school/middle school)
     */
    RESEARCH_PAPER("Research Paper"),

    /**
     * Peer review/evaluation
     */
    PEER_REVIEW("Peer Review"),

    /**
     * Self-assessment/reflection
     */
    SELF_ASSESSMENT("Self-Assessment"),

    /**
     * Extra credit assignment
     */
    EXTRA_CREDIT("Extra Credit"),

    /**
     * Other/custom assignment type
     */
    OTHER("Other");

    private final String displayName;

    AssignmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Check if assignment type is a major assessment
     */
    public boolean isMajorAssessment() {
        return this == TEST || this == EXAM || this == PROJECT ||
               this == SUMMATIVE || this == BENCHMARK || this == PORTFOLIO ||
               this == RESEARCH_PAPER || this == GROUP_PROJECT;
    }

    /**
     * Check if assignment type is formative (ongoing feedback)
     */
    public boolean isFormative() {
        return this == FORMATIVE || this == HOMEWORK || this == PRACTICE ||
               this == QUIZ || this == PARTICIPATION || this == CLASSWORK ||
               this == BELL_RINGER || this == EXIT_TICKET || this == CHECKPOINT ||
               this == REVIEW || this == JOURNAL;
    }

    /**
     * Get default weight for assignment type
     */
    public double getDefaultWeight() {
        return switch (this) {
            case EXAM -> 0.30;                  // 30% of final grade
            case TEST -> 0.20;                  // 20% of final grade
            case BENCHMARK -> 0.25;             // 25% (standardized assessments)
            case PROJECT -> 0.15;               // 15% of final grade
            case GROUP_PROJECT -> 0.15;         // 15% of final grade
            case RESEARCH_PAPER -> 0.15;        // 15% of final grade
            case PORTFOLIO -> 0.20;             // 20% (cumulative assessment)
            case HOMEWORK -> 0.15;              // 15% of final grade
            case QUIZ -> 0.10;                  // 10% of final grade
            case CHECKPOINT -> 0.10;            // 10% of final grade
            case PARTICIPATION -> 0.10;         // 10% of final grade
            case CLASSWORK -> 0.10;             // 10% of final grade
            case READING -> 0.10;               // 10% of final grade
            case JOURNAL -> 0.08;               // 8% of final grade
            case PERFORMANCE -> 0.12;           // 12% (arts/PE assessments)
            case PRESENTATION -> 0.12;          // 12% of final grade
            case BELL_RINGER -> 0.05;           // 5% (daily formative)
            case EXIT_TICKET -> 0.05;           // 5% (daily formative)
            case REVIEW -> 0.05;                // 5% (test prep)
            case PRACTICE -> 0.05;              // 5% (drill work)
            case PEER_REVIEW -> 0.05;           // 5% (peer feedback)
            case SELF_ASSESSMENT -> 0.03;       // 3% (reflection)
            default -> 0.05;                    // 5% for other types
        };
    }
}
