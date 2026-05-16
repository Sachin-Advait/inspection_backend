package com.gissoft.inspection_backend.controller;

import com.gissoft.inspection_backend.repository.ApprovalRequestRepository;
import com.gissoft.inspection_backend.repository.InspectionRunRepository;
import com.gissoft.inspection_backend.services.AiBotService;
import com.gissoft.inspection_backend.services.AiBotService.AiBotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ai/inspection")
@RequiredArgsConstructor
public class AiBotController {

    private final AiBotService aiBotService;
    private final InspectionRunRepository inspectionRepo;
    private final ApprovalRequestRepository approvalRepo;

    // ─────────────────────────────────────────────────────────────────────────
    // Returns the 5 questions for the UI to render as buttons/cards
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/questions")
    public ResponseEntity<List<Map<String, Object>>> getQuestions() {

        List<Map<String, Object>> questions = List.of(

                Map.of(
                        "questionCode", "CASE_SUMMARY",
                        "label", "Summarize this inspection case",
                        "description", "Get a full executive summary of an inspection case for supervisor review.",
                        "requiresInspectionId", true,
                        "question", "Q1"
                ),
                Map.of(
                        "questionCode", "CORRECTION_GUIDANCE",
                        "label", "What correction should be requested?",
                        "description", "Generate a formal correction request message for the establishment.",
                        "requiresInspectionId", true,
                        "question", "Q2"
                ),
                Map.of(
                        "questionCode", "SUPERVISOR_ACTION",
                        "label", "What action should the supervisor take?",
                        "description", "Get a recommended decision: approve, return, re-inspect or escalate.",
                        "requiresInspectionId", true,
                        "question", "Q3"
                ),
                Map.of(
                        "questionCode", "RECURRING_FAILURE_ANALYSIS",
                        "label", "Which failures are repeating and why?",
                        "description", "Analyse recurring non-compliance patterns across all inspections.",
                        "requiresInspectionId", false,
                        "question", "Q4"
                ),
                Map.of(
                        "questionCode", "INSPECTION_PRIORITIZATION",
                        "label", "Which inspections should be prioritized today?",
                        "description", "Get a ranked priority list based on risk, due date and history.",
                        "requiresInspectionId", false,
                        "question", "Q5"
                )
        );

        return ResponseEntity.ok(questions);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/ai/inspection/cases?questionCode=CASE_SUMMARY
    // Returns the case list for the dropdown when user selects Q1, Q2, Q3
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/cases")
    public ResponseEntity<?> getCasesForQuestion(@RequestParam String questionCode) {

        return switch (questionCode.toUpperCase()) {

            // Q1 — all submitted inspections
            case "CASE_SUMMARY" -> ResponseEntity.ok(
                    inspectionRepo.findAll()
                            .stream()
                            .filter(r -> r.getSubmittedAt() != null)
                            .map(r -> Map.of(
                                    "inspectionId", r.getId(),
                                    "establishmentName", r.getEntity().getName(),
                                    "externalRef", safe(r.getEntity().getExternalRef()),
                                    "outcome", safe(r.getOutcome()),
                                    "submittedAt", r.getSubmittedAt().toString()
                            ))
                            .toList()
            );

            // Q2 — failed or conditional inspections only
            case "CORRECTION_GUIDANCE" -> ResponseEntity.ok(
                    inspectionRepo.findAll()
                            .stream()
                            .filter(r -> "FAIL".equalsIgnoreCase(r.getOutcome())
                                    || "CONDITIONAL".equalsIgnoreCase(r.getOutcome()))
                            .map(r -> Map.of(
                                    "inspectionId", r.getId(),
                                    "establishmentName", r.getEntity().getName(),
                                    "externalRef", safe(r.getEntity().getExternalRef()),
                                    "outcome", safe(r.getOutcome())
                            ))
                            .toList()
            );

            // Q3 — pending approvals only
            case "SUPERVISOR_ACTION" -> ResponseEntity.ok(
                    approvalRepo
                            .findPendingWithDetails(null, Pageable.ofSize(100))
                            .stream()
                            .map(a -> Map.of(
                                    "inspectionId", a.getInspection().getId(),
                                    "establishmentName", a.getInspection().getEntity().getName(),
                                    "externalRef", safe(a.getInspection().getEntity().getExternalRef()),
                                    "outcome", safe(a.getInspection().getOutcome()),
                                    "requiredLevel", safe(a.getRequiredLevel())
                            ))
                            .toList()
            );

            // Q4, Q5 — no case selection needed
            default -> ResponseEntity.badRequest().body("This question does not require a case selection.");
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/ai/inspection/ask
    // Main endpoint — handles all 5 question codes
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/ask")
    public ResponseEntity<AiBotResponse> ask(@RequestBody AskRequest req) {

        AiBotResponse response = aiBotService.ask(
                req.questionCode(),
                req.inspectionId(),
                req.actor()
        );

        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQUEST RECORD
    // ─────────────────────────────────────────────────────────────────────────

    private String safe(String s) {
        return s != null ? s : "";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Q1, Q2, Q3 → provide inspectionId
     * {
     * "questionCode": "CASE_SUMMARY",
     * "inspectionId": "uuid-here",
     * "actor": "admin"
     * }
     * <p>
     * Q4, Q5 → just questionCode and actor, nothing else needed
     * {
     * "questionCode": "RECURRING_FAILURE_ANALYSIS",
     * "actor": "admin"
     * }
     */
    public record AskRequest(
            String questionCode,
            UUID inspectionId,
            String actor
    ) {
    }
}