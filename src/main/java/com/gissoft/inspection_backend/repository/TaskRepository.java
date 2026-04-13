package com.gissoft.inspection_backend.repository;

import com.gissoft.inspection_backend.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Mobile: inspector's tasks for a date window, excluding terminal statuses. */
    @Query("""
        SELECT t FROM Task t
        WHERE t.assignedTo = :username
          AND t.dueAt BETWEEN :from AND :to
          AND t.status NOT IN ('CANCELLED','COMPLETED')
        ORDER BY t.dueAt ASC
        """)
    List<Task> findMyTasks(@Param("username") String username,
                            @Param("from")     OffsetDateTime from,
                            @Param("to")       OffsetDateTime to);

    /** Ops: multi-filter paged list. */
    @Query("""
        SELECT t FROM Task t
        WHERE (:dg         IS NULL OR t.entity.directorate = :dg)
          AND (:status     IS NULL OR t.status             = :status)
          AND (:assignedTo IS NULL OR t.assignedTo         = :assignedTo)
          AND (:workPlanId IS NULL OR t.workPlanId         = :workPlanId)
          AND (:priority   IS NULL OR t.priority           = :priority)
        """)
    Page<Task> findByFilters(@Param("dg")         String dg,
                              @Param("status")     String status,
                              @Param("assignedTo") String assignedTo,
                              @Param("workPlanId") UUID workPlanId,
                              @Param("priority")   String priority,
                              Pageable pageable);

    List<Task> findByEntityIdOrderByDueAtAsc(UUID entityId);
    List<Task> findByEntityIdOrderByCreatedAtAsc(UUID entityId);

    long countByAssignedToAndStatusAndDueAtBefore(String assignedTo,
                                                   String status,
                                                   OffsetDateTime before);
}
