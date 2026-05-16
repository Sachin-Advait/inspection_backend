package com.gissoft.inspection_backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gissoft.inspection_backend.entity.*;
import com.gissoft.inspection_backend.repository.ApprovalRequestRepository;
import com.gissoft.inspection_backend.repository.EntityMasterRepository;
import com.gissoft.inspection_backend.repository.InspectionRunRepository;
import com.gissoft.inspection_backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiBotService {

    // ── Repositories ──────────────────────────────────────────────────────────

    private static final String GPT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GPT_MODEL = "gpt-4o";
    private final InspectionRunRepository inspectionRepo;
    private final ApprovalRequestRepository approvalRepo;
    private final TaskRepository taskRepo;

    // ── Config ────────────────────────────────────────────────────────────────
    private final EntityMasterRepository entityRepo;
    private final ChecklistService checklistService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${openai.api.key}")
    private String openAiApiKey;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Single method called by the controller.
     *
     * @param questionCode One of: CASE_SUMMARY, CORRECTION_GUIDANCE,
     *                     SUPERVISOR_ACTION, RECURRING_FAILURE_ANALYSIS,
     *                     INSPECTION_PRIORITIZATION
     * @param inspectionId Required for Q1, Q2, Q3. Null for Q4, Q5.
     * @param filters      Optional: fromDate, toDate, area, inspectionType
     * @param actor        Logged-in admin username (for audit)
     */
    public AiBotResponse ask(String questionCode,
                             UUID inspectionId,
                             AiBotFilters filters,
                             String actor) {
        try {
            Map<String, Object> payload = switch (questionCode.toUpperCase()) {
                case "CASE_SUMMARY" -> buildCaseSummaryPayload(inspectionId);
                case "CORRECTION_GUIDANCE" -> buildCorrectionGuidancePayload(inspectionId);
                case "SUPERVISOR_ACTION" -> buildSupervisorActionPayload(inspectionId);
                case "RECURRING_FAILURE_ANALYSIS" -> buildRecurringFailurePayload(filters);
                case "INSPECTION_PRIORITIZATION" -> buildPrioritizationPayload(filters);
                default -> throw new IllegalArgumentException(
                        "Unknown question code: " + questionCode);
            };

            String systemPrompt = resolveSystemPrompt(questionCode);
            String userMessage = objectMapper.writeValueAsString(payload);
            String aiAnswer = callGpt(systemPrompt, userMessage);

            log.info("[AI-BOT] {} | actor={} | inspectionId={}", questionCode, actor, inspectionId);

            return new AiBotResponse(questionCode, aiAnswer, OffsetDateTime.now());

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI-BOT] Failed for questionCode={}: {}", questionCode, e.getMessage(), e);
            throw new RuntimeException("AI bot error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q1 — CASE SUMMARY
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildCaseSummaryPayload(UUID inspectionId) {

        InspectionRun run = getInspection(inspectionId);
        EntityMaster entity = run.getEntity();
        Task task = run.getTask();

        // Checklist answers
        List<InspectionAnswer> answers = run.getAnswers();
        long totalItems = answers.size();
        long passedCount = answers.stream().filter(a -> "PASS".equalsIgnoreCase(a.getAnswer())).count();
        long failedCount = totalItems - passedCount;

        // Failed items with question text
        List<Map<String, Object>> failedItems = answers.stream()
                .filter(a -> !"PASS".equalsIgnoreCase(a.getAnswer()))
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    try {
                        ChecklistQuestion q = checklistService.getQuestion(a.getQuestionId());
                        item.put("question", q.getText());
                        item.put("answer", a.getAnswer());
                        item.put("note", a.getNote());
                        if (q.getRule() != null) {
                            item.put("severity", q.getRule().getFailSeverity());
                            item.put("isCritical", q.getRule().getForceApprovalLevel() != null);
                        }
                    } catch (Exception e) {
                        item.put("question", a.getQuestionId().toString());
                        item.put("answer", a.getAnswer());
                    }
                    return item;
                })
                .collect(Collectors.toList());

        long criticalFailed = failedItems.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("isCritical")))
                .count();

        // Approval history
        List<Map<String, Object>> approvalHistory =
                approvalRepo.findByInspectionIdOrderByCreatedAtDesc(inspectionId)
                        .stream()
                        .map(a -> Map.<String, Object>of(
                                "status", a.getStatus(),
                                "level", safe(a.getRequiredLevel()),
                                "decidedBy", safe(a.getDecisionBy()),
                                "note", safe(a.getDecisionNote()),
                                "decidedAt", a.getDecidedAt() != null ? a.getDecidedAt().toString() : ""
                        ))
                        .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionCode", "CASE_SUMMARY");

        Map<String, Object> inspection = new LinkedHashMap<>();
        inspection.put("inspectionId", run.getId());
        inspection.put("establishmentName", entity.getName());
        inspection.put("externalRef", entity.getExternalRef());
        inspection.put("area", safe(entity.getDirectorate()));
        inspection.put("category", safe(entity.getCategory()));
        inspection.put("inspectionType", safe(task.getTaskType()));
        inspection.put("phase", safe(task.getPhase()));
        inspection.put("status", safe(task.getStatus()));
        inspection.put("outcome", safe(run.getOutcome()));
        inspection.put("inspectorName", safe(run.getStartedBy()));
        inspection.put("scheduledDate", task.getDueAt() != null ? task.getDueAt().toString() : "");
        inspection.put("completedAt", run.getSubmittedAt() != null ? run.getSubmittedAt().toString() : "In Progress");
        inspection.put("totalChecklistItems", totalItems);
        inspection.put("passedItems", passedCount);
        inspection.put("failedItems", failedCount);
        inspection.put("criticalFailedItems", criticalFailed);
        inspection.put("summaryNote", safe(run.getSummaryNote()));
        inspection.put("currentTask", safe(task.getStatus()));
        payload.put("inspection", inspection);
        payload.put("failedChecklistItems", failedItems);
        payload.put("approvalHistory", approvalHistory);

        return payload;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q2 — CORRECTION GUIDANCE
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildCorrectionGuidancePayload(UUID inspectionId) {

        InspectionRun run = getInspection(inspectionId);
        EntityMaster entity = run.getEntity();
        Task task = run.getTask();

        List<Map<String, Object>> corrections = run.getAnswers().stream()
                .filter(a -> !"PASS".equalsIgnoreCase(a.getAnswer()))
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    try {
                        ChecklistQuestion q = checklistService.getQuestion(a.getQuestionId());
                        item.put("question", q.getText());
                        item.put("answer", a.getAnswer());
                        item.put("inspectorRemarks", safe(a.getNote()));
                        if (q.getRule() != null) {
                            item.put("severity", safe(q.getRule().getFailSeverity()));
                            item.put("defaultAction", safe(q.getRule().getDefaultAction()));
                            item.put("guidance", safe(q.getRule().getEvidencePolicyJson().toString()));
                        }
                    } catch (Exception e) {
                        item.put("question", a.getQuestionId().toString());
                    }
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionCode", "CORRECTION_GUIDANCE");

        Map<String, Object> caseInfo = new LinkedHashMap<>();
        caseInfo.put("inspectionId", run.getId());
        caseInfo.put("establishmentName", entity.getName());
        caseInfo.put("externalRef", entity.getExternalRef());
        caseInfo.put("inspectionType", safe(task.getTaskType()));
        caseInfo.put("resultStatus", safe(run.getOutcome()));
        payload.put("case", caseInfo);
        payload.put("correctionsRequired", corrections);
        payload.put("tone", "formal and helpful");

        return payload;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q3 — SUPERVISOR ACTION
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildSupervisorActionPayload(UUID inspectionId) {

        InspectionRun run = getInspection(inspectionId);
        EntityMaster entity = run.getEntity();
        Task task = run.getTask();

        List<InspectionAnswer> answers = run.getAnswers();

        // Count failed/critical
        List<Map<String, Object>> failedItems = answers.stream()
                .filter(a -> !"PASS".equalsIgnoreCase(a.getAnswer()))
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    try {
                        ChecklistQuestion q = checklistService.getQuestion(a.getQuestionId());
                        item.put("question", q.getText());
                        if (q.getRule() != null) {
                            item.put("severity", safe(q.getRule().getFailSeverity()));
                            item.put("isCritical", q.getRule().getForceApprovalLevel() != null);
                        }
                    } catch (Exception e) {
                        item.put("question", a.getQuestionId().toString());
                    }
                    return item;
                })
                .collect(Collectors.toList());

        long criticalCount = failedItems.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("isCritical"))).count();

        long highCount = failedItems.stream()
                .filter(i -> "HIGH".equalsIgnoreCase((String) i.get("severity"))).count();

        // Previous failed inspections on same entity
        long previousFailed = inspectionRepo
                .findByEntityIdOrderByStartedAtDesc(entity.getId())
                .stream()
                .filter(r -> !r.getId().equals(inspectionId))
                .filter(r -> "FAIL".equalsIgnoreCase(r.getOutcome()))
                .count();

        // Task age in days
        long taskAgeDays = 0;
        if (task.getDueAt() != null) {
            taskAgeDays = java.time.temporal.ChronoUnit.DAYS.between(
                    task.getDueAt().toLocalDate(),
                    OffsetDateTime.now().toLocalDate()
            );
        }

        // Approval history
        List<Map<String, Object>> previousDecisions =
                approvalRepo.findByInspectionIdOrderByCreatedAtDesc(inspectionId)
                        .stream()
                        .map(a -> Map.<String, Object>of(
                                "status", a.getStatus(),
                                "level", safe(a.getRequiredLevel()),
                                "decidedBy", safe(a.getDecisionBy()),
                                "note", safe(a.getDecisionNote())
                        ))
                        .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionCode", "SUPERVISOR_ACTION");

        Map<String, Object> caseInfo = new LinkedHashMap<>();
        caseInfo.put("inspectionId", run.getId());
        caseInfo.put("establishmentName", entity.getName());
        caseInfo.put("status", safe(task.getStatus()));
        caseInfo.put("resultStatus", safe(run.getOutcome()));
        caseInfo.put("criticalFailedCount", criticalCount);
        caseInfo.put("highSeverityFailedCount", highCount);
        caseInfo.put("previousFailedInspections", previousFailed);
        caseInfo.put("currentTaskAgeDays", taskAgeDays);
        caseInfo.put("permitExpiryDate", entity.getNextDueAt() != null
                ? entity.getNextDueAt().toString() : "");
        payload.put("case", caseInfo);
        payload.put("failedItems", failedItems);
        payload.put("previousDecisions", previousDecisions);
        payload.put("allowedActions", List.of(
                "APPROVE",
                "RETURN_FOR_CORRECTION",
                "REQUEST_REINSPECTION",
                "ESCALATE"
        ));

        return payload;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q4 — RECURRING FAILURE ANALYSIS
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildRecurringFailurePayload(AiBotFilters filters) {

        OffsetDateTime from = filters != null && filters.fromDate() != null
                ? filters.fromDate() : OffsetDateTime.now().minusMonths(3);
        OffsetDateTime to = filters != null && filters.toDate() != null
                ? filters.toDate() : OffsetDateTime.now();

        // Get all inspections in range
        List<InspectionRun> runs = inspectionRepo
                .findAll()
                .stream()
                .filter(r -> r.getStartedAt() != null
                        && !r.getStartedAt().isBefore(from)
                        && !r.getStartedAt().isAfter(to))
                .filter(r -> filters == null || filters.area() == null
                        || filters.area().equalsIgnoreCase(r.getEntity().getDirectorate()))
                .filter(r -> filters == null || filters.inspectionType() == null
                        || filters.inspectionType().equalsIgnoreCase(r.getTask().getTaskType()))
                .collect(Collectors.toList());

        // Aggregate failures by question
        Map<UUID, Long> failCountByQuestion = new LinkedHashMap<>();
        Map<UUID, String> questionText = new LinkedHashMap<>();
        Map<UUID, String> questionSeverity = new LinkedHashMap<>();

        for (InspectionRun run : runs) {
            for (InspectionAnswer answer : run.getAnswers()) {
                if (!"PASS".equalsIgnoreCase(answer.getAnswer())) {
                    UUID qId = answer.getQuestionId();
                    failCountByQuestion.merge(qId, 1L, Long::sum);
                    if (!questionText.containsKey(qId)) {
                        try {
                            ChecklistQuestion q = checklistService.getQuestion(qId);
                            questionText.put(qId, q.getText());
                            if (q.getRule() != null) {
                                questionSeverity.put(qId, safe(q.getRule().getFailSeverity()));
                            }
                        } catch (Exception e) {
                            questionText.put(qId, qId.toString());
                        }
                    }
                }
            }
        }

        // Top 10 failing questions
        List<Map<String, Object>> aggregated = failCountByQuestion.entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("question", questionText.getOrDefault(entry.getKey(), entry.getKey().toString()));
                    item.put("severity", questionSeverity.getOrDefault(entry.getKey(), "UNKNOWN"));
                    item.put("failedCount", entry.getValue());
                    item.put("failureRate", runs.isEmpty() ? "0%"
                            : Math.round((entry.getValue() * 100.0) / runs.size()) + "%");
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionCode", "RECURRING_FAILURE_ANALYSIS");
        payload.put("period", Map.of("from", from.toString(), "to", to.toString()));
        payload.put("filters", Map.of(
                "area", filters != null && filters.area() != null ? filters.area() : "ALL",
                "inspectionType", filters != null && filters.inspectionType() != null ? filters.inspectionType() : "ALL"
        ));
        payload.put("totalInspectionsAnalyzed", runs.size());
        payload.put("aggregatedFailures", aggregated);

        return payload;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q5 — INSPECTION PRIORITIZATION
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildPrioritizationPayload(AiBotFilters filters) {

        OffsetDateTime today = OffsetDateTime.now();

        // Fetch all pending/in-progress tasks
        List<Task> pendingTasks = taskRepo
                .findByFilters(
                        filters != null ? filters.area() : null,
                        null,   // status — we filter below
                        null,
                        null,
                        null,
                        org.springframework.data.domain.Pageable.ofSize(100)
                )
                .getContent()
                .stream()
                .filter(t -> List.of("PENDING", "IN_PROGRESS").contains(t.getStatus()))
                .collect(Collectors.toList());

        List<Map<String, Object>> inspections = pendingTasks.stream()
                .map(task -> {
                    EntityMaster entity = task.getEntity();

                    // Count previous failures
                    long prevFailed = inspectionRepo
                            .findByEntityIdOrderByStartedAtDesc(entity.getId())
                            .stream()
                            .filter(r -> "FAIL".equalsIgnoreCase(r.getOutcome()))
                            .count();

                    // Task age
                    long taskAgeDays = task.getDueAt() != null
                            ? java.time.temporal.ChronoUnit.DAYS.between(
                            task.getDueAt().toLocalDate(),
                            today.toLocalDate())
                            : 0;

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("inspectionId", task.getId());
                    item.put("establishmentName", entity.getName());
                    item.put("externalRef", entity.getExternalRef());
                    item.put("area", safe(entity.getDirectorate()));
                    item.put("inspectionType", safe(task.getTaskType()));
                    item.put("status", safe(task.getStatus()));
                    item.put("dueDate", task.getDueAt() != null ? task.getDueAt().toString() : "");
                    item.put("taskAgeDays", taskAgeDays);
                    item.put("riskLevel", safe(entity.getComplianceFlag()));
                    item.put("assignedInspector", safe(task.getAssignedTo()));
                    item.put("previousFailedCount", prevFailed);
                    item.put("permitExpiryDate", entity.getNextDueAt() != null
                            ? entity.getNextDueAt().toString() : "");
                    item.put("priority", safe(task.getPriority()));
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionCode", "INSPECTION_PRIORITIZATION");
        payload.put("date", today.toLocalDate().toString());
        payload.put("pendingInspections", inspections);
        payload.put("priorityRules", List.of(
                "Overdue first (taskAgeDays > 0)",
                "High risk / OVERDUE complianceFlag before others",
                "Permit expiry soon",
                "Repeated failures (previousFailedCount > 0)",
                "Supervisor escalation / HIGH priority"
        ));

        return payload;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GPT CALL
    // ─────────────────────────────────────────────────────────────────────────

    private String callGpt(String systemPrompt, String userMessage) throws Exception {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", GPT_MODEL);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        body.put("max_tokens", 1000);
        body.put("temperature", 0.3);

        String requestBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GPT_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[AI-BOT] GPT error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("GPT API error: " + response.statusCode());
        }

        Map<?, ?> parsed = objectMapper.readValue(response.body(), Map.class);
        List<?> choices = (List<?>) parsed.get("choices");
        Map<?, ?> first = (Map<?, ?>) choices.get(0);
        Map<?, ?> msg = (Map<?, ?>) first.get("message");

        return (String) msg.get("content");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SYSTEM PROMPTS  (one per question)
    // ─────────────────────────────────────────────────────────────────────────

    private String resolveSystemPrompt(String questionCode) {
        return switch (questionCode.toUpperCase()) {

            case "CASE_SUMMARY" -> """
                    You are a municipal inspection assistant.
                    You receive a JSON payload containing inspection data.
                    Return a concise executive summary with:
                    1. Case overview (establishment, type, status)
                    2. Key findings (failed items, critical issues)
                    3. Risk level assessment
                    4. Evidence note
                    5. Recommended next action for the supervisor
                    Be professional and concise. Do NOT approve or reject automatically.
                    """;

            case "CORRECTION_GUIDANCE" -> """
                    You are a municipal compliance officer assistant.
                    You receive a JSON payload with failed checklist items.
                    Return a formal correction request message containing:
                    1. Clear list of required corrections
                    2. Documents or evidence required for each item
                    3. Professional tone suitable for sending to the establishment owner
                    Do not include legal finality unless explicitly configured.
                    """;

            case "SUPERVISOR_ACTION" -> """
                    You are a decision-support assistant for municipal supervisors.
                    You receive a JSON payload with inspection results and history.
                    Return:
                    1. Recommended action (must be one of the allowedActions in the payload)
                    2. Reason for the recommendation
                    3. Confidence level (HIGH / MEDIUM / LOW)
                    4. Any missing information the supervisor should check
                    Do NOT create a final decision in the database. Support only.
                    """;

            case "RECURRING_FAILURE_ANALYSIS" -> """
                    You are a municipal inspection analytics assistant.
                    You receive aggregated failure data across multiple inspections.
                    Return:
                    1. Top recurring failure patterns
                    2. Likely operational root causes
                    3. Suggested awareness or training campaigns
                    4. Recommended inspection focus areas
                    5. Management action points
                    Be data-driven and concise.
                    """;

            case "INSPECTION_PRIORITIZATION" -> """
                    You are a workload planning assistant for municipal supervisors.
                    You receive a list of pending inspections with risk and due date data.
                    Return:
                    1. Ranked priority list (most urgent first)
                    2. Reason for each inspection's priority
                    3. Recommended assignments if applicable
                    4. Workload warning if any inspector has too many pending tasks
                    Focus on overdue, high-risk, repeat offenders, and expiring permits.
                    """;

            default -> "You are a helpful municipal inspection assistant.";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private InspectionRun getInspection(UUID inspectionId) {
        if (inspectionId == null) {
            throw new IllegalArgumentException("inspectionId is required for this question");
        }
        return inspectionRepo.findById(inspectionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inspection not found: " + inspectionId));
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INNER RECORDS  (can be moved to dto package if preferred)
    // ─────────────────────────────────────────────────────────────────────────

    public record AiBotFilters(
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            String area,
            String inspectionType
    ) {
    }

    public record AiBotResponse(
            String questionCode,
            String answer,
            OffsetDateTime answeredAt
    ) {
    }
}