package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.dto.CreateTaskOnlyRequest;
import com.gissoft.inspection_backend.entity.EntityMaster;
import com.gissoft.inspection_backend.entity.PhaseConfig;
import com.gissoft.inspection_backend.entity.Task;
import com.gissoft.inspection_backend.repository.EntityMasterRepository;
import com.gissoft.inspection_backend.repository.PhaseConfigRepository;
import com.gissoft.inspection_backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskCreationService {

    private final EntityMasterRepository entityRepo;
    private final TaskRepository taskRepo;
    private final AuditService auditService;
    private final PhaseConfigRepository phaseRepo;

    public Task createTaskOnly(CreateTaskOnlyRequest req, String actor) {

        // 1. Create Entity
        EntityMaster entity = EntityMaster.builder()
                .externalRef("INT-" + System.currentTimeMillis())
                .directorate(req.directorate())
                .category(req.category())
                .sourceSystem("INTERNAL")
                .name(req.entityName())
                .ownerName(req.ownerName())
                .ownerPhone(req.ownerPhone())
                .lat(req.lat())
                .lon(req.lon())
                .complianceFlag("ACTIVE")
                .build();

        entity = entityRepo.save(entity);

        // ✅ AUDIT (Entity creation)
        auditService.log(actor, "CREATE", "EntityMaster", entity.getId().toString());

        PhaseConfig firstPhase = phaseRepo
                .findByDirectorateAndCategoryAndActiveTrueOrderBySortOrderAsc(
                        req.directorate(),
                        req.category()
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No phase config found"));

        // 2. Create Task (NO ASSIGNEE)
        Task task = Task.builder()
                .entity(entity)
                .taskType("INSPECTION")
                .phase(firstPhase.getPhaseType())
                .subtype("GENERAL")
                .assignedTo(null)
                .status("PENDING")
                .priority(req.priority() != null ? req.priority() : "MEDIUM")
                .dueAt(req.dueAt())
                .sourceSystem("INTERNAL")
                .build();




        task = taskRepo.save(task);

        // ✅ AUDIT (Task creation)
        auditService.log(actor, "CREATE", "Task", task.getId().toString());

        return task;
    }
}