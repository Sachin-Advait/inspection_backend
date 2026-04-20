package com.gissoft.inspection_backend.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "checklist_rule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChecklistRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    private ChecklistQuestion question;

    /** MINOR | MAJOR | CRITICAL */
    @Column(name = "fail_severity", nullable = false, length = 20)
    private String failSeverity;

    @Type(JsonBinaryType.class)
    @Column(name = "evidence_policy_json", columnDefinition = "jsonb")
    private Map<String, Object> evidencePolicyJson;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "violation_code", referencedColumnName = "code")
    private ViolationCode violationCode;

    /** WARNING | FINE | CLOSURE */
    @Column(name = "default_action", length = 30)
    private String defaultAction;

    /** SUPERVISOR | MANAGER */
    @Column(name = "force_approval_level", length = 20)
    private String forceApprovalLevel;

    @Type(JsonBinaryType.class)
    @Column(name = "reinspection_suggestion_json", columnDefinition = "jsonb")
    private Map<String, Object> reinspectionSuggestionJson;
}
