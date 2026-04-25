package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.dto.ApprovalDto.DecisionRequest;
import com.gissoft.inspection_backend.dto.NoticeDto;
import com.gissoft.inspection_backend.entity.ApprovalRequest;
import com.gissoft.inspection_backend.entity.Notice;
import com.gissoft.inspection_backend.entity.Task;
import com.gissoft.inspection_backend.repository.ApprovalRequestRepository;
import com.gissoft.inspection_backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepo;
    private final AuditService auditService;
    private final NoticeService noticeService;
    private final TaskRepository taskRepo;
    private final ViolationService violationService;

    // ── Queue ─────────────────────────────────────────────────────────────────

    public Page<ApprovalRequest> getPending(String level, Pageable pageable) {
        return approvalRepo.findPending(level, pageable);
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Transactional
    public ApprovalRequest approve(UUID approvalId, DecisionRequest req, String actor) throws Exception {

        ApprovalRequest approval = findAndAssertPending(approvalId);

        approval.setStatus("APPROVED");
        approval.setDecisionBy(actor);
        approval.setDecisionNote(req != null ? req.decisionNote() : null);
        approval.setDecidedAt(OffsetDateTime.now());

        var run = approval.getInspection();
        var entity = run.getEntity();

        String outcome = run.getOutcome();

        // ✅ PASS → nothing happens
        if ("PASS".equalsIgnoreCase(outcome)) {
            approvalRepo.save(approval);

            auditService.log(
                    actor,
                    "APPROVE_PASS",
                    "ApprovalRequest",
                    approvalId.toString()
            );

            return approval;
        }

        // 🔥 Decide notice type
        String noticeType = "FAIL".equalsIgnoreCase(outcome) ? "FINE" : "WARNING";
        long fineAmount = violationService.calculateFine(run.getAnswers());

        // 🔥 Generate Notice
        Notice notice = noticeService.generate(
                new NoticeDto.GenerateRequest(
                        run.getId(),          // ✅ inspectionId FIRST
                        entity.getId(),       // ✅ entityId SECOND
                        noticeType,
                        fineAmount,
                        "EN"                  // ✅ required field
                ),
                actor
        );

        // 🔥 Send Notice
        noticeService.send(
                notice.getId(),
                new NoticeDto.SendRequest("WHATSAPP"),
                actor
        );

        // 🔥 Create Reinspection Task
        taskRepo.save(Task.builder()
                .entity(entity)
                .taskType("REINSPECTION")
                .subtype("REINSPECTION")
                .phase("FOLLOW_UP")
                .status("PENDING")
                .priority("HIGH")
                .sourceSystem("INTERNAL")
                .dueAt(OffsetDateTime.now().plusDays(7))
                .build());

        approvalRepo.save(approval);

        auditService.log(
                actor,
                "APPROVE",
                "ApprovalRequest",
                approvalId.toString(),
                Map.of("outcome", outcome, "noticeType", noticeType)
        );

        return approval;
    }
    // ── Reject ────────────────────────────────────────────────────────────────

    @Transactional
    public ApprovalRequest reject(UUID approvalId, DecisionRequest req, String actor) {

        ApprovalRequest approval = findAndAssertPending(approvalId);

        approval.setStatus("REJECTED");
        approval.setDecisionBy(actor);
        approval.setDecisionNote(req != null ? req.decisionNote() : null);
        approval.setDecidedAt(OffsetDateTime.now());

        approvalRepo.save(approval);

        auditService.log(
                actor,
                "REJECT",
                "ApprovalRequest",
                approvalId.toString(),
                Map.of("note", approval.getDecisionNote() != null ? approval.getDecisionNote() : "")
        );

        return approval;
    }

    // ── Escalate ──────────────────────────────────────────────────────────────

    @Transactional
    public ApprovalRequest escalate(UUID approvalId, String actor) {

        ApprovalRequest approval = findAndAssertPending(approvalId);

        approval.setRequiredLevel("MANAGER");
        approval.setStatus("PENDING");

        approvalRepo.save(approval);

        auditService.log(
                actor,
                "ESCALATE",
                "ApprovalRequest",
                approvalId.toString()
        );

        return approval;
    }

    // ── Request reinspection ──────────────────────────────────────────────────

    @Transactional
    public ApprovalRequest requestReinspection(UUID approvalId, String note, String actor) {

        ApprovalRequest approval = findAndAssertPending(approvalId);

        approval.setStatus("REINSPECTION_REQUESTED");
        approval.setDecisionBy(actor);
        approval.setDecisionNote(note);
        approval.setDecidedAt(OffsetDateTime.now());

        approvalRepo.save(approval);

        Map<String, Object> diff = new HashMap<>();
        diff.put("note", note);

        auditService.log(
                actor,
                "REQUEST_REINSPECTION",
                "ApprovalRequest",
                approvalId.toString(),
                diff
        );

        return approval;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ApprovalRequest findAndAssertPending(UUID id) {
        ApprovalRequest approval = approvalRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + id));
        if (!"PENDING".equals(approval.getStatus())) {
            throw new IllegalStateException("Approval already decided: " + approval.getStatus());
        }
        return approval;
    }
}