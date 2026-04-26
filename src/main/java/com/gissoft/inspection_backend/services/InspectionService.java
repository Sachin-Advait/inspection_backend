package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.dto.InspectionDto.AnswerItem;
import com.gissoft.inspection_backend.dto.InspectionDto.InspectionResponse;
import com.gissoft.inspection_backend.dto.InspectionDto.SubmitRequest;
import com.gissoft.inspection_backend.entity.*;
import com.gissoft.inspection_backend.repository.*;
import com.gissoft.inspection_backend.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InspectionService {

    private final InspectionRunRepository inspectionRepo;
    private final TaskRepository taskRepo;
    private final ChecklistService checklistService;
    private final PhaseResolverService phaseResolverService;
    private final PhaseConfigRepository phaseRepo;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final NoticeRepository noticeRepo;
    private final AppUserRepository userRepo;
    private final ApprovalRequestRepository approvalRepo;

    // ── START ────────────────────────────────────────────────────────────────

    public InspectionResponse start(UUID taskId, String actor) {

        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        Optional<InspectionRun> existing =
                inspectionRepo.findByTaskIdAndSubmittedAtIsNull(taskId);

        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        ChecklistTemplate checklist = resolveChecklist(task);

        InspectionRun run = InspectionRun.builder()
                .task(task)
                .entity(task.getEntity())
                .checklistTemplateId(checklist.getId())
                .checklistVersion(checklist.getVersion())
                .startedBy(actor)
                .startedAt(OffsetDateTime.now())
                .build();

        task.setStatus("IN_PROGRESS");
        taskRepo.save(task);

        return toResponse(inspectionRepo.save(run));
    }

    public InspectionResponse saveAnswers(UUID inspectionId, List<AnswerItem> answers) {

        InspectionRun run = getRun(inspectionId);

        Map<UUID, InspectionAnswer> existingAnswers =
                run.getAnswers().stream()
                        .collect(Collectors.toMap(
                                InspectionAnswer::getQuestionId,
                                a -> a
                        ));

        for (AnswerItem item : answers) {

            InspectionAnswer existing = existingAnswers.get(item.questionId());

            if (existing != null) {
                existing.setAnswer(item.answer());
                existing.setNote(item.note());
            } else {
                run.getAnswers().add(
                        InspectionAnswer.builder()
                                .inspection(run)
                                .questionId(item.questionId())
                                .answer(item.answer())
                                .note(item.note())
                                .build()
                );
            }
        }

        return toResponse(inspectionRepo.save(run));
    }

    // ── SUBMIT ───────────────────────────────────────────────────────────────

    @Transactional
    public InspectionResponse submit(UUID inspectionId, SubmitRequest req, String actor) {

        InspectionRun run = getRun(inspectionId);

        if (run.getSubmittedAt() != null) {
            throw new IllegalStateException("Already submitted");
        }

        Task task = run.getTask();
        EntityMaster entity = run.getEntity();

        // 🔥 Inspector decides outcome
        if (req.outcome() == null || req.outcome().isBlank()) {
            throw new IllegalArgumentException("Outcome is required");
        }

        String outcome = req.outcome().toUpperCase();

        run.setOutcome(outcome);
        run.setSubmittedAt(OffsetDateTime.now());

        // 🔥 Update entity
        entity.setLastInspectionAt(run.getSubmittedAt());
        entity.setLastInspectionResult(outcome);

        if (req.nextDueDate() != null) {
            entity.setNextDueAt(req.nextDueDate());
        }

        // 🔥 COMPLETE TASK (inspection ends here)
        task.setStatus("COMPLETED");
        task.setCompletedBy(actor);
        task.setCompletedAt(OffsetDateTime.now());
        taskRepo.save(task);

        // 🔥 Create approval request if needed
        if (!"PASS".equalsIgnoreCase(outcome)) {
            approvalRepo.save(
                    ApprovalRequest.builder()
                            .inspection(run)
                            .status("PENDING")
                            .requiredLevel("SUPERVISOR")
                            .createdAt(OffsetDateTime.now())
                            .build()
            );
        }

        // ✅ HANDLE PASS → MOVE TO NEXT PHASE
        if ("PASS".equalsIgnoreCase(outcome)) {

            String nextPhase = phaseResolverService.resolveNextPhase(
                    entity.getDirectorate(),
                    entity.getCategory(),
                    task.getPhase(),
                    outcome
            );

            if (nextPhase != null && !nextPhase.isBlank()) {

                PhaseConfig config = phaseRepo
                        .findByDirectorateAndCategoryAndPhaseType(
                                entity.getDirectorate(),
                                entity.getCategory(),
                                nextPhase
                        ).orElseThrow();

                Task nextTask = Task.builder()
                        .entity(entity)
                        .taskType("INSPECTION")
                        .phase(nextPhase)
                        .subtype("GENERAL")
                        .status("PENDING")
                        .priority("HIGH")
                        .sourceSystem("INTERNAL")
                        .dueAt(OffsetDateTime.now().plusDays(7))
                        .assignedTo(null)
                        .build();

                if (config.getDueDays() != null) {
                    nextTask.setDueAt(OffsetDateTime.now().plusDays(config.getDueDays()));
                }

                taskRepo.save(nextTask);
            }
        }

        if (req.violations() != null && !req.violations().isEmpty()) {
            String violationSummary = req.violations().stream()
                    .map(v -> String.format("[%s|%s|%s|%s|%s]",
                            v.code(),
                            v.severity(),
                            v.action(),
                            v.fineAmount() != null ? "OMR" + v.fineAmount() : "0",
                            v.evidenceRef() != null ? v.evidenceRef() : ""))
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);

            // Append to summaryNote (non-destructive — adds after inspector note)
            String existingNote = req.summaryNote() != null ? req.summaryNote() : "";
            run.setSummaryNote(existingNote.isEmpty()
                    ? "VIOLATIONS:" + violationSummary
                    : existingNote + " | VIOLATIONS:" + violationSummary);

            log.info("Persisted {} violation(s) for inspection {}",
                    req.violations().size(), inspectionId);

            req.violations().forEach(v ->
                    auditService.log(actor, "VIOLATION_RECORDED",
                            "InspectionRun",
                            inspectionId + "|" + v.code() + "|" + v.action()));
        } else {
            // No violations — just set the summary note as-is
            if (req.summaryNote() != null) {
                run.setSummaryNote(req.summaryNote());
            }
        }

        // 🔥 Start workflow (tracking only)
        long supervisorLimit = userRepo.findByUsername(actor)
                .filter(u -> u.getSupervisorFineLimit() != null)
                .map(AppUser::getSupervisorFineLimit)
                .orElse(200L);

        String noticeType = null;

        if ("FAIL".equalsIgnoreCase(outcome)) {
            noticeType = "FINE";
        } else if ("CONDITIONAL".equalsIgnoreCase(outcome)) {
            noticeType = "WARNING";
        }
        long totalFine = req.violations() != null
                ? req.violations().stream()
                .map(v -> v.fineAmount() != null ? v.fineAmount() : 0L)
                .reduce(0L, Long::sum)
                : 0L;
    if (!"PASS".equalsIgnoreCase(outcome)) {
      workflowService.startInspectionProcess(
          totalFine,
          run.getId().toString(),
          entity.getId().toString(),
          outcome,
          noticeType, // ✅ FIXED
          actor,
          supervisorLimit);
        }

        run = inspectionRepo.save(run);

        auditService.log(
                actor,
                "SUBMIT_INSPECTION",
                "InspectionRun",
                run.getId().toString(),
                Map.of("outcome", outcome)
        );

        return toResponse(run);
    }
    // ── HELPERS ──────────────────────────────────────────────────────────────

    private String computeOutcome(InspectionRun run) {
        int failCount = (int) run.getAnswers().stream()
                .filter(a -> {
                    ChecklistQuestion q = checklistService.getQuestion(a.getQuestionId());
                    return q.getRule() != null && "FAIL".equalsIgnoreCase(a.getAnswer());
                })
                .count();

        if (failCount == 0) return "PASS";
        if (failCount <= 2) return "CONDITIONAL";
        return "FAIL";
    }

    private ChecklistTemplate resolveChecklist(Task task) {

        String dg = task.getEntity().getDirectorate();
        String category = task.getEntity().getCategory();
        String phase =
                "REINSPECTION".equalsIgnoreCase(task.getSubtype())
                        ? "FOLLOW_UP"
                        : task.getPhase();

        PhaseConfig phaseConfig = phaseRepo
                .findByDirectorateAndCategoryAndPhaseType(dg, category, phase)
                .orElseThrow();

        if (phaseConfig.getOverrideChecklistId() != null) {
            return checklistService.findById(phaseConfig.getOverrideChecklistId());
        }

        if (phaseConfig.getDefaultChecklistId() != null) {
            return checklistService.findById(phaseConfig.getDefaultChecklistId());
        }

        return checklistService.getActive(dg, category, phase);
    }

    private InspectionRun getRun(UUID id) {
        return inspectionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inspection not found: " + id));
    }

    public List<InspectionRun> findByEntity(UUID entityId) {
        return inspectionRepo.findByEntityIdOrderByStartedAtDesc(entityId);
    }

    private InspectionResponse toResponse(InspectionRun run) {
        return new InspectionResponse(
                run.getId(),
                run.getTask().getId(),
                run.getEntity().getId(),
                run.getEntity().getName(),
                run.getEntity().getExternalRef(),
                run.getChecklistTemplateId(),
                run.getChecklistVersion(),
                run.getStartedBy(),
                run.getStartedAt(),
                run.getSubmittedAt(),
                run.getOutcome(),
                run.getSummaryNote(),
                run.getAnswers().stream()
                        .map(a -> new AnswerItem(a.getQuestionId(), a.getAnswer(), a.getNote()))
                        .toList()
        );
    }
}