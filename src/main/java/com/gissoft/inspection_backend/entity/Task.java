package com.gissoft.inspection_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entity_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private EntityMaster entity;

    /**
     * INSPECTION | REINSPECTION | FOLLOW_UP
     */
    @Column(name = "task_type", nullable = false, length = 40)
    private String taskType;

    /**
     * Phase name or operational sub-type e.g. Routine, Foundation, Traffic …
     */
    @Column(name = "phase", nullable = false, length = 80)
    private String phase;

    @Column(name = "subtype", nullable = false, length = 80)
    private String subtype;

    @Column(name = "assigned_to", length = 80)
    private String assignedTo;

    /**
     * PENDING | IN_PROGRESS | COMPLETED | CANCELLED
     */
    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    /**
     * LOW | MEDIUM | HIGH | CRITICAL
     */
    @Column(length = 20)
    private String priority;

    @Column(name = "work_plan_id")
    private UUID workPlanId;

    /**
     * ORACLE | INTERNAL
     */
    @Column(name = "source_system", length = 20)
    private String sourceSystem;

    @Column(name = "oracle_ref", length = 80)
    private String oracleRef;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_by", length = 80)
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}