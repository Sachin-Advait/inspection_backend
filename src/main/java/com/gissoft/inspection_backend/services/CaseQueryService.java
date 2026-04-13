package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.dto.TimelineDto;
import com.gissoft.inspection_backend.entity.EntityMaster;
import com.gissoft.inspection_backend.entity.Task;
import com.gissoft.inspection_backend.repository.EntityMasterRepository;
import com.gissoft.inspection_backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all non-Flowable query operations that the controller previously
 * tried to call on TaskQueryService (which is a pure Flowable service).
 */
@Service
@RequiredArgsConstructor
public class CaseQueryService {

    private final EntityMasterRepository entityRepo;
    private final TaskRepository taskRepo;

    // ── Case / entity search (O03 + O05) ─────────────────────────────────────

    public Page<EntityMaster> searchCases(String query, String dg,
                                          String category, Pageable pageable) {
        return entityRepo.search(dg, category, query, pageable);
    }

    public EntityMaster getCaseDetail(UUID entityId) {
        return entityRepo.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entity not found: " + entityId));
    }

    // ── Task docs linked to a task (Oracle pull refs) ─────────────────────────

    public Map<String, Object> getTaskDocs(UUID taskId) {
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task not found: " + taskId));
        return Map.of(
                "taskId", taskId,
                "entityRef", task.getEntity().getExternalRef(),
                "sourceSystem", task.getEntity().getSourceSystem(),
                "docs", List.of()   // populate from oracle_doc_cache when available
        );
    }

    // ── Health due list (O06) ─────────────────────────────────────────────────

    public List<EntityMaster> getHealthDueList(String category, int limit) {
        return entityRepo.findHealthDueList(category, PageRequest.of(0, limit));
    }

    // ── Navigation deep-link ──────────────────────────────────────────────────

    public Map<String, Object> navigationLink(double lat, double lon) {
        String url = "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon;
        return Map.of("url", url, "lat", lat, "lon", lon);
    }

    public List<TimelineDto> getTimeline(UUID entityId) {
        return taskRepo.findByEntityIdOrderByCreatedAtAsc(entityId)
                .stream()
                .map(t -> new TimelineDto(
                        t.getPhase(),
                        t.getStatus(),
                        t.getAssignedTo(),
                        t.getCompletedBy(),
                        t.getCompletedAt(),
                        t.getCreatedAt()
                ))
                .toList();
    }
}
