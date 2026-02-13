package com.heronix.teacher.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pre-built discipline referral templates with proper K-12 legal/educational terminology.
 *
 * Each template pre-fills category, severity, description text (with [placeholder] markers
 * for teacher-specific details), and suggested intervention. Templates align with PBIS
 * frameworks, CRDC reporting categories, and standard Student Code of Conduct language.
 */
@Data
@Builder
@AllArgsConstructor
public class DisciplinePromptTemplate {

    private String id;
    private String displayName;
    private String category;
    private String defaultSeverity;
    private String descriptionTemplate;
    private String suggestedIntervention;
    private boolean requiresAdminReferral;

    private static final List<DisciplinePromptTemplate> ALL_TEMPLATES;

    static {
        List<DisciplinePromptTemplate> templates = new ArrayList<>();

        templates.add(DisciplinePromptTemplate.builder()
                .id("CLASSROOM_DISRUPTION")
                .displayName("Classroom Disruption")
                .category("DISRUPTION")
                .defaultSeverity("MINOR")
                .descriptionTemplate("Student engaged in behavior that materially and substantially disrupted " +
                        "the educational process, including [specific behavior]. This behavior interfered with the " +
                        "learning environment and the rights of other students to receive instruction.")
                .suggestedIntervention("Verbal warning; student conference; seat reassignment; parent contact if repeated.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("HORSEPLAY")
                .displayName("Horseplay / Unsafe Physical Contact")
                .category("FIGHTING")
                .defaultSeverity("MINOR")
                .descriptionTemplate("Student engaged in physical contact not intended to cause bodily harm but " +
                        "which posed a safety risk, including rough or boisterous play. This behavior violated school " +
                        "safety expectations and could have resulted in injury.")
                .suggestedIntervention("Verbal warning; loss of privilege; parent notification; behavior contract if repeated.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("PROFANE_LANGUAGE")
                .displayName("Inappropriate / Profane Language")
                .category("INAPPROPRIATE_LANGUAGE")
                .defaultSeverity("MODERATE")
                .descriptionTemplate("Student used language that was profane, vulgar, obscene, or otherwise " +
                        "inappropriate for the school setting, directed toward [peers/staff/general use]. This " +
                        "constitutes a violation of the Student Code of Conduct regarding respectful communication.")
                .suggestedIntervention("Student conference; written reflection; parent contact; referral if directed at staff.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("DEFIANCE_INSUBORDINATION")
                .displayName("Defiance / Insubordination")
                .category("DEFIANCE")
                .defaultSeverity("MODERATE")
                .descriptionTemplate("Student willfully refused to comply with a reasonable and lawful directive " +
                        "issued by authorized school personnel. Specific directive given: [directive]. Student's " +
                        "response: [response]. This constitutes insubordination under the Student Code of Conduct.")
                .suggestedIntervention("Student conference; parent contact; behavior contract; progressive discipline referral.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("PERSISTENT_NON_COMPLIANCE")
                .displayName("Persistent Non-Compliance")
                .category("NON_COMPLIANCE")
                .defaultSeverity("MINOR")
                .descriptionTemplate("Student repeatedly failed to follow established classroom rules and " +
                        "expectations despite verbal redirection and documented interventions. Specific " +
                        "expectations not met: [expectations].")
                .suggestedIntervention("Documented verbal warnings; parent contact; behavior improvement plan; team conference.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("BULLYING_INTIMIDATION")
                .displayName("Bullying / Intimidation")
                .category("BULLYING")
                .defaultSeverity("MAJOR")
                .descriptionTemplate("Student engaged in behavior constituting bullying as defined under state " +
                        "education code: repeated or severe conduct directed at another student that exploits an " +
                        "imbalance of power and creates a hostile educational environment. Specific behavior: " +
                        "[behavior]. Impact on target student: [impact].")
                .suggestedIntervention("Immediate admin referral; separation of students; counselor involvement; parent conference; safety plan.")
                .requiresAdminReferral(true)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("PHYSICAL_AGGRESSION")
                .displayName("Physical Aggression / Fighting")
                .category("FIGHTING")
                .defaultSeverity("MAJOR")
                .descriptionTemplate("Student initiated or engaged in physical aggression toward another individual " +
                        "on school grounds/at a school-sponsored event. This incident is documented in accordance " +
                        "with district policy and applicable state law. Description of physical contact: [description].")
                .suggestedIntervention("Immediate admin referral; separation of involved parties; nurse check if injury; parent notification; possible SRO involvement.")
                .requiresAdminReferral(true)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("HARASSMENT_GENERAL")
                .displayName("Harassment (General)")
                .category("HARASSMENT")
                .defaultSeverity("MAJOR")
                .descriptionTemplate("Student engaged in unwelcome conduct that was sufficiently severe, pervasive, " +
                        "or persistent so as to create a hostile educational environment for the affected " +
                        "individual(s). Nature of conduct: [description]. This may constitute a violation of " +
                        "federal civil rights protections.")
                .suggestedIntervention("Immediate admin referral; Title IX coordinator notification if applicable; separation of parties; counselor support for target.")
                .requiresAdminReferral(true)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("ACADEMIC_DISHONESTY")
                .displayName("Academic Dishonesty")
                .category("OTHER")
                .defaultSeverity("MODERATE")
                .descriptionTemplate("Student engaged in academic dishonesty including but not limited to: " +
                        "[plagiarism/unauthorized collaboration/use of prohibited materials/submission of work " +
                        "not their own] during [assignment/assessment]. Evidence: [evidence]. This violates the " +
                        "Academic Integrity Policy.")
                .suggestedIntervention("Zero on assignment; parent contact; academic integrity conference; notation in student record.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("TECHNOLOGY_AUP_VIOLATION")
                .displayName("Technology / AUP Violation")
                .category("TECHNOLOGY_MISUSE")
                .defaultSeverity("MINOR")
                .descriptionTemplate("Student violated the district Acceptable Use Policy (AUP) by: " +
                        "[specific violation]. Device involved: [school-issued/personal]. This constitutes " +
                        "misuse of technology resources as defined in the signed AUP agreement.")
                .suggestedIntervention("Device confiscation for remainder of period; parent notification; technology privilege restriction if repeated.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("DRESS_CODE_VIOLATION")
                .displayName("Dress Code Violation")
                .category("DRESS_CODE_VIOLATION")
                .defaultSeverity("MINOR")
                .descriptionTemplate("Student was found in violation of the district dress code as outlined " +
                        "in the Student Handbook. Specific violation: [description]. Student was given the " +
                        "opportunity to correct the violation.")
                .suggestedIntervention("Opportunity to change; loaner clothing if available; parent contact if unresolved.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("TARDINESS")
                .displayName("Tardiness")
                .category("TARDINESS")
                .defaultSeverity("MINOR")
                .descriptionTemplate("Student arrived to [class/school] at [time], which is [X] minutes after " +
                        "the designated start time, without an authorized excuse. This is the student's [Nth] " +
                        "documented tardy this [term/semester].")
                .suggestedIntervention("Documented warning; parent notification after 3rd occurrence; detention after 5th; admin referral after repeated pattern.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("VANDALISM")
                .displayName("Vandalism / Property Damage")
                .category("VANDALISM")
                .defaultSeverity("MODERATE")
                .descriptionTemplate("Student willfully damaged, defaced, or destroyed [school property/personal " +
                        "property of another]. Description of damage: [description]. Estimated value: [if applicable]. " +
                        "Restitution may be pursued in accordance with district policy and state law.")
                .suggestedIntervention("Admin referral; restitution assessment; parent conference; possible law enforcement referral for significant damage.")
                .requiresAdminReferral(false)
                .build());

        templates.add(DisciplinePromptTemplate.builder()
                .id("THEFT")
                .displayName("Theft")
                .category("THEFT")
                .defaultSeverity("MAJOR")
                .descriptionTemplate("Student is alleged to have taken or attempted to take property belonging to " +
                        "[another student/staff member/the school district] without authorization. Item(s): " +
                        "[description]. Circumstances: [description]. This matter may be referred to law " +
                        "enforcement per district policy.")
                .suggestedIntervention("Immediate admin referral; secure any recovered property; document all witness statements; parent notification; possible SRO involvement.")
                .requiresAdminReferral(true)
                .build());

        ALL_TEMPLATES = Collections.unmodifiableList(templates);
    }

    /**
     * Returns all 14 discipline prompt templates.
     */
    public static List<DisciplinePromptTemplate> getAll() {
        return ALL_TEMPLATES;
    }

    /**
     * Returns templates filtered by behavior category.
     */
    public static List<DisciplinePromptTemplate> getByCategory(String category) {
        return ALL_TEMPLATES.stream()
                .filter(t -> t.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    /**
     * Returns a template by its ID, or null if not found.
     */
    public static DisciplinePromptTemplate getById(String id) {
        return ALL_TEMPLATES.stream()
                .filter(t -> t.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }
}
