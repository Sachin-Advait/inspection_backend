package com.gissoft.inspection_backend.controller;

import com.gissoft.inspection_backend.repository.ApprovalRequestRepository;
import com.gissoft.inspection_backend.repository.InspectionRunRepository;
import com.gissoft.inspection_backend.services.AiBotService;
import com.gissoft.inspection_backend.services.AiBotService.AiBotFilters;
import com.gissoft.inspection_backend.services.AiBotService.AiBotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
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
    // GET /api/ai/inspection/questions
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
                        "requiresFilters", false
                ),
                Map.of(
                        "questionCode", "CORRECTION_GUIDANCE",
                        "label", "What correction should be requested?",
                        "description", "Generate a formal correction request message for the establishment.",
                        "requiresInspectionId", true,
                        "requiresFilters", false
                ),
                Map.of(
                        "questionCode", "SUPERVISOR_ACTION",
                        "label", "What action should the supervisor take?",
                        "description", "Get a recommended decision: approve, return, re-inspect or escalate.",
                        "requiresInspectionId", true,
                        "requiresFilters", false
                ),
                Map.of(
                        "questionCode", "RECURRING_FAILURE_ANALYSIS",
                        "label", "Which failures are repeating and why?",
                        "description", "Analyse recurring non-compliance patterns across inspections.",
                        "requiresInspectionId", false,
                        "requiresFilters", true
                ),
                Map.of(
                        "questionCode", "INSPECTION_PRIORITIZATION",
                        "label", "Which inspections should be prioritized today?",
                        "description", "Get a ranked priority list based on risk, due date and history.",
                        "requiresInspectionId", false,
                        "requiresFilters", true
                )
        );

        return ResponseEntity.ok(questions);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/ai/inspection/cases?questionCode=CASE_SUMMARY
    // Returns the case list for the dropdown when user selects Q1, Q2, Q3
    // Q4 and Q5 do not need a case selection — they use date/area filters
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

            // Q2 — only failed or conditional inspections
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
            default -> ResponseEntity.badRequest()
                    .body("This question does not require a case selection. Use filters instead.");
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/ai/inspection/ask
    // Main endpoint — handles all 5 question codes
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/ask")
    public ResponseEntity<AiBotResponse> ask(@RequestBody AskRequest req) {

        AiBotFilters filters = new AiBotFilters(
                req.fromDate(),
                req.toDate(),
                req.area(),
                req.inspectionType()
        );

        AiBotResponse response = aiBotService.ask(
                req.questionCode(),
                req.inspectionId(),
                filters,
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
     * Single request body for all 5 questions.
     * <p>
     * Q1, Q2, Q3 → provide inspectionId
     * {
     * "questionCode": "CASE_SUMMARY",
     * "inspectionId": "uuid-here",
     * "actor": "admin"
     * }
     * <p>
     * Q4, Q5 → provide filters
     * {
     * "questionCode": "RECURRING_FAILURE_ANALYSIS",
     * "fromDate": "2026-02-01T00:00:00Z",
     * "toDate":   "2026-05-16T00:00:00Z",
     * "area":     "Muttrah",
     * "actor":    "admin"
     * }
     */
    public record AskRequest(
            String questionCode,
            UUID inspectionId,
            String actor,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            String area,
            String inspectionType
    ) {
    }
}