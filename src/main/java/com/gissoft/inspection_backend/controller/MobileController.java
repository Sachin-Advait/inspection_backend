package com.gissoft.inspection_backend.controller;

import com.gissoft.inspection_backend.dto.CreateTaskRequest;
import com.gissoft.inspection_backend.dto.InspectionDto.AnswerBatch;
import com.gissoft.inspection_backend.dto.InspectionDto.InspectionResponse;
import com.gissoft.inspection_backend.dto.InspectionDto.StartRequest;
import com.gissoft.inspection_backend.dto.InspectionDto.SubmitRequest;
import com.gissoft.inspection_backend.dto.ReassignRequest;
import com.gissoft.inspection_backend.dto.RescheduleRequest;
import com.gissoft.inspection_backend.entity.ChecklistTemplate;
import com.gissoft.inspection_backend.entity.EvidenceFile;
import com.gissoft.inspection_backend.entity.InspectionRun;
import com.gissoft.inspection_backend.entity.Task;
import com.gissoft.inspection_backend.services.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
public class MobileController {

    private final TaskService taskService;
    private final ChecklistService checklistService;
    private final InspectionService inspectionService;
    private final EvidenceService evidenceService;
    private final CaseQueryService caseQueryService;

    // =========================================================================
    // Tasks
    // =========================================================================

    /**
     * GET /api/tasks/my?actor=&from=&to=
     */
    @GetMapping("/tasks/my")
    public ResponseEntity<List<Task>> getMyTasks(
            @RequestParam String actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(taskService.getMyTasks(actor, from, to));
    }

    /**
     * GET /api/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Task> getTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.findById(taskId));
    }

    /**
     * GET /api/tasks
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    /**
     * GET /api/tasks/{taskId}/docs
     */
    @GetMapping("/tasks/{taskId}/docs")
    public ResponseEntity<Map<String, Object>> getTaskDocs(@PathVariable UUID taskId) {
        return ResponseEntity.ok(caseQueryService.getTaskDocs(taskId));
    }

    /**
     * GET /api/navigation/link?lat=&lon=
     */
    @GetMapping("/navigation/link")
    public ResponseEntity<Map<String, Object>> navigationLink(
            @RequestParam double lat, @RequestParam double lon) {
        return ResponseEntity.ok(caseQueryService.navigationLink(lat, lon));
    }

    /**
     * POST /api/tasks
     */
    @PostMapping("/tasks")
    public ResponseEntity<Task> createTask(
            @Valid @RequestBody CreateTaskRequest req,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(taskService.create(req, actor));
    }

    /**
     * PATCH /api/tasks/{taskId}/reassign
     */
    @PatchMapping("/tasks/{taskId}/reassign")
    public ResponseEntity<Task> reassignTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody ReassignRequest req,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(taskService.reassign(taskId, req.getNewAssignee(), actor));
    }

    /**
     * PATCH /api/tasks/{taskId}/reschedule
     */
    @PatchMapping("/tasks/{taskId}/reschedule")
    public ResponseEntity<Task> rescheduleTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody RescheduleRequest req,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(taskService.reschedule(taskId, req.getNewDueAt(), actor));
    }

    // =========================================================================
    // Checklists
    // =========================================================================

    @DeleteMapping("/checklists/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID questionId,
            @RequestParam(defaultValue = "admin") String actor) {

        checklistService.deleteQuestion(questionId, actor);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/checklists/sections/{sectionId}")
    public ResponseEntity<Void> deleteSection(
            @PathVariable UUID sectionId,
            @RequestParam(defaultValue = "admin") String actor) {

        checklistService.deleteSection(sectionId, actor);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/checklists/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable UUID templateId,
            @RequestParam(defaultValue = "admin") String actor) {

        checklistService.deleteTemplate(templateId, actor);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/checklists/active?dg=&category=&phaseType=
     */
    @GetMapping("/checklists/active")
    public ResponseEntity<ChecklistTemplate> getActiveChecklist(
            @RequestParam String dg,
            @RequestParam String category,
            @RequestParam String phaseType) {
        return ResponseEntity.ok(checklistService.getActive(dg, category, phaseType));
    }

    // =========================================================================
    // Inspections
    // =========================================================================

    /**
     * POST /api/inspections/start
     */
    @PostMapping("/inspections/start")
    public ResponseEntity<InspectionResponse> startInspection(
            @Valid @RequestBody StartRequest req,
            @RequestParam(defaultValue = "inspector") String actor) {
        return ResponseEntity.ok(inspectionService.start(req.taskId(), actor));
    }

    /**
     * POST /api/inspections/{inspectionId}/answers
     */
    @PostMapping("/inspections/{inspectionId}/answers")
    public ResponseEntity<InspectionResponse> saveAnswers(
            @PathVariable UUID inspectionId,
            @Valid @RequestBody AnswerBatch batch,
            @RequestParam(defaultValue = "inspector") String actor) {
        return ResponseEntity.ok(
                inspectionService.saveAnswers(inspectionId, batch.answers()));
    }

    @PostMapping("/inspections/{inspectionId}/submit")
    public ResponseEntity<InspectionResponse> submitInspection(
            @PathVariable UUID inspectionId,
            @RequestBody(required = false) SubmitRequest req,
            @RequestParam(defaultValue = "inspector") String actor) {
        // Pass the full SubmitRequest so the service receives outcome,
        // reinspectDate, nextDueDate, followUpDate, and violations.
        // Previously only summaryNote was forwarded — a bug that prevented
        // reinspection tasks and violation persistence.
        return ResponseEntity.ok(
                inspectionService.submit(inspectionId, req, actor)
        );
    }

    /**
     * GET /api/inspections/by-entity/{entityId}
     */
    @GetMapping("/inspections/by-entity/{entityId}")
    public ResponseEntity<List<InspectionRun>> getInspectionsByEntity(
            @PathVariable UUID entityId) {
        return ResponseEntity.ok(inspectionService.findByEntity(entityId));
    }

    // =========================================================================
    // Evidence
    // =========================================================================

    /**
     * POST /api/evidence/upload
     */
    @PostMapping("/evidence/upload")
    public ResponseEntity<EvidenceFile> uploadEvidence(
            @RequestParam UUID inspectionId,
            @RequestParam UUID entityId,
            @RequestParam(required = false) UUID questionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "inspector") String actor) throws Exception {
        return ResponseEntity.ok(
                evidenceService.upload(inspectionId, entityId, questionId, file, actor));
    }

    /**
     * GET /api/evidence?entityId=&fileType=
     */
    @GetMapping("/evidence")
    public ResponseEntity<Page<EvidenceFile>> browseEvidence(
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String fileType,
            Pageable pageable) {
        return ResponseEntity.ok(evidenceService.browse(entityId, fileType, pageable));
    }

    @GetMapping("/evidence/by-inspection/{inspectionId}")
    public ResponseEntity<List<EvidenceFile>> browseEvidenceByInspection(@PathVariable UUID inspectionId) {
        return ResponseEntity.ok(evidenceService.byInspection(inspectionId));
    }
}