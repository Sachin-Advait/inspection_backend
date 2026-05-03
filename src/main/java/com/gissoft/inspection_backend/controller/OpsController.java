package com.gissoft.inspection_backend.controller;

import com.gissoft.inspection_backend.dto.ApprovalDto.DecisionRequest;
import com.gissoft.inspection_backend.dto.CreateDemoTaskRequest;
import com.gissoft.inspection_backend.dto.NoticeDto.GenerateRequest;
import com.gissoft.inspection_backend.dto.NoticeDto.SendRequest;
import com.gissoft.inspection_backend.dto.PermitDto.CreatePermitRequest;
import com.gissoft.inspection_backend.dto.ReportDto.ReportRequest;
import com.gissoft.inspection_backend.dto.WorkPlanDto.CreatePlanRequest;
import com.gissoft.inspection_backend.entity.*;
import com.gissoft.inspection_backend.repository.TaskRepository;
import com.gissoft.inspection_backend.services.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
public class OpsController {

    private final DashboardService dashboardService;
    private final WorkPlanService workPlanService;
    private final CaseQueryService caseQueryService;
    private final TaskService taskService;
    private final InspectionService inspectionService;
    private final ApprovalService approvalService;
    private final InternalPermitService permitService;
    private final NoticeService noticeService;
    private final ReportingService reportingService;
    private final ViolationService violationService;
    private final TaskRepository taskRepo;

    // =========================================================================
    // Dashboard (O01)
    // =========================================================================

    @GetMapping("/ops/dashboard")
    public ResponseEntity<DashboardService.DashboardStats> getDashboard(
            @RequestParam(defaultValue = "ALL") String dg,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(dashboardService.getStats(dg, from, to));
    }

    // =========================================================================
    // Work Plans (O02)
    // =========================================================================

    @PostMapping("/work-plans")
    public ResponseEntity<WorkPlan> createWorkPlan(
            @Valid @RequestBody CreatePlanRequest req,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(workPlanService.createPlan(req, actor));
    }

    @PostMapping("/work-plans/{planId}/publish")
    public ResponseEntity<WorkPlan> publishWorkPlan(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(workPlanService.publish(planId, actor));
    }

    @GetMapping("/work-plans")
    public ResponseEntity<List<WorkPlan>> listWorkPlans(
            @RequestParam(defaultValue = "DRAFT") String status) {
        return ResponseEntity.ok(workPlanService.findByStatus(status));
    }

    @GetMapping("/work-plans/{planId}")
    public ResponseEntity<WorkPlan> getWorkPlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(workPlanService.findById(planId));
    }

    @PostMapping("/work-plans/auto-distribute")
    public ResponseEntity<Map<String, List<UUID>>> autoDistribute(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<UUID> taskIds = ((List<String>) body.get("taskIds"))
                .stream().map(UUID::fromString).toList();
        @SuppressWarnings("unchecked")
        List<String> inspectors = (List<String>) body.get("inspectors");
        return ResponseEntity.ok(workPlanService.autoDistribute(taskIds, inspectors));
    }

    // =========================================================================
    // Cases (O03 + O05)
    // =========================================================================

    @GetMapping("/cases")
    public ResponseEntity<Page<EntityMaster>> searchCases(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String dg,
            @RequestParam(required = false) String category,
            Pageable pageable) {
        return ResponseEntity.ok(caseQueryService.searchCases(query, dg, category, pageable));
    }

    @GetMapping("/cases/{entityId}")
    public ResponseEntity<EntityMaster> getCaseDetail(@PathVariable UUID entityId) {
        return ResponseEntity.ok(caseQueryService.getCaseDetail(entityId));
    }

    @GetMapping("/cases/{entityId}/tasks")
    public ResponseEntity<List<Task>> getCaseTasks(@PathVariable UUID entityId) {
        return ResponseEntity.ok(taskService.findByEntity(entityId));
    }

    @GetMapping("/cases/{entityId}/inspections")
    public ResponseEntity<List<InspectionRun>> getCaseInspections(@PathVariable UUID entityId) {
        return ResponseEntity.ok(inspectionService.findByEntity(entityId));
    }

    // =========================================================================
    // Approvals (O04)
    // =========================================================================

    @GetMapping("/approvals/pending")
    public ResponseEntity<Page<ApprovalRequest>> getPendingApprovals(
            @RequestParam(required = false) String level,
            Pageable pageable) {
        return ResponseEntity.ok(approvalService.getPending(level, pageable));
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public ResponseEntity<ApprovalRequest> approve(
            @PathVariable UUID approvalId,
            @RequestBody(required = false) DecisionRequest req,
            @RequestParam(defaultValue = "supervisor") String actor) throws Exception {
        return ResponseEntity.ok(approvalService.approve(approvalId, req, actor));
    }

    @PostMapping("/approvals/{approvalId}/reject")
    public ResponseEntity<ApprovalRequest> reject(
            @PathVariable UUID approvalId,
            @RequestBody(required = false) DecisionRequest req,
            @RequestParam(defaultValue = "supervisor") String actor) {
        return ResponseEntity.ok(approvalService.reject(approvalId, req, actor));
    }

    @PostMapping("/approvals/{approvalId}/escalate")
    public ResponseEntity<ApprovalRequest> escalate(
            @PathVariable UUID approvalId,
            @RequestParam(defaultValue = "supervisor") String actor) {
        return ResponseEntity.ok(approvalService.escalate(approvalId, actor));
    }

    // =========================================================================
    // Health Due List (O06)
    // =========================================================================

    @GetMapping("/ops/health/due")
    public ResponseEntity<List<EntityMaster>> getHealthDueList(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(caseQueryService.getHealthDueList(category, limit));
    }

    // =========================================================================
    // Internal Permit Registry (O07)
    // =========================================================================

    @PostMapping("/internal/permits")
    public ResponseEntity<InternalPermit> createPermit(
            @Valid @RequestBody CreatePermitRequest req,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(permitService.create(req, actor));
    }

    @GetMapping("/internal/permits")
    public ResponseEntity<Page<InternalPermit>> listPermits(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String area,
            Pageable pageable) {
        return ResponseEntity.ok(permitService.list(category, status, area, pageable));
    }

    @GetMapping("/internal/permits/{permitId}")
    public ResponseEntity<InternalPermit> getPermit(@PathVariable UUID permitId) {
        return ResponseEntity.ok(permitService.findById(permitId));
    }

    @PostMapping("/internal/permits/{permitId}/activate")
    public ResponseEntity<InternalPermit> activatePermit(
            @PathVariable UUID permitId,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(permitService.activate(permitId, actor));
    }

    @PostMapping("/internal/permits/{permitId}/close")
    public ResponseEntity<InternalPermit> closePermit(
            @PathVariable UUID permitId,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(permitService.close(permitId, actor));
    }

    // =========================================================================
    // Notices & Fines (O08)
    // =========================================================================

    @PostMapping("/notices/generate")
    public ResponseEntity<Notice> generateNotice(
            @Valid @RequestBody GenerateRequest req,
            @RequestParam(defaultValue = "system") String actor) throws Exception {
        return ResponseEntity.ok(noticeService.generate(req, actor));
    }

    @PostMapping("/notices/{noticeId}/send")
    public ResponseEntity<Notice> sendNotice(
            @PathVariable UUID noticeId,
            @RequestBody(required = false) SendRequest req,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(noticeService.send(noticeId, req, actor));
    }

    @PostMapping("/notices/{noticeId}/mark-served")
    public ResponseEntity<Notice> markServed(
            @PathVariable UUID noticeId,
            @RequestParam(defaultValue = "system") String actor) {
        return ResponseEntity.ok(noticeService.markServed(noticeId, actor));
    }

    @GetMapping("/notices")
    public ResponseEntity<Page<Notice>> listNotices(
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            Pageable pageable) {
        return ResponseEntity.ok(noticeService.list(noticeType, status, paymentStatus, pageable));
    }

    // =========================================================================
    // Reports (O11)
    // =========================================================================

    @PostMapping("/reports/export")
    public ResponseEntity<?> exportReport(
            @RequestBody ReportRequest req) throws Exception {

        String fmt = req.format() != null ? req.format().toUpperCase() : "JSON";
        return switch (fmt) {
            case "EXCEL" -> {
                byte[] bytes = reportingService.exportExcel(req);
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=report.xlsx")
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(bytes);
            }
            case "CSV" -> {
                byte[] bytes = reportingService.exportCsv(req);
                yield ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=report.csv")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(bytes);
            }
            default -> ResponseEntity.ok(reportingService.buildReport(req));
        };
    }



    /**
     * GET /ops/team-today?supervisor=ahmed.ali
     * My Team Today panel — open/due-soon/breach counts + case list
     */
    @GetMapping("/ops/team-today")
    public ResponseEntity<Map<String, Object>> getTeamToday(
            @RequestParam(required = false) String supervisor) {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime todayEnd = now.withHour(23).withMinute(59).withSecond(59);
        OffsetDateTime soonCut = now.plusHours(4);

        long open = taskRepo.countByAssignedToAndStatusAndDueAtBefore(null, "PENDING", todayEnd);
        long dueSoon = taskRepo.countByAssignedToAndStatusAndDueAtBefore(null, "PENDING", soonCut);
        long breach = taskRepo.countByAssignedToAndStatusAndDueAtBefore(null, "PENDING", now);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("open", open);
        result.put("dueSoon", dueSoon);
        result.put("breach", breach);
        result.put("cases", List.of());  // wire to task list with entity details
        return ResponseEntity.ok(result);
    }

    /**
     * GET /ops/critical-cases?limit=5
     * Critical · Needs Attention panel — overdue HIGH priority tasks
     */
    @GetMapping("/ops/critical-cases")
    public ResponseEntity<List<Map<String, Object>>> getCriticalCases(
            @RequestParam(defaultValue = "5") int limit) {

        // Tasks that are overdue + HIGH priority
        List<Map<String, Object>> cases = taskRepo
                .findByFilters(null, "PENDING", null, null, "HIGH",
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .getContent()
                .stream()
                .filter(t -> t.getDueAt() != null && t.getDueAt().isBefore(OffsetDateTime.now()))
                .map(t -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("taskId", t.getId());
                    row.put("entityRef", t.getEntity().getExternalRef());
                    row.put("entityName", t.getEntity().getName());
                    row.put("phase", t.getPhase());
                    row.put("subtype", t.getSubtype());
                    row.put("priority", t.getPriority());
                    row.put("status", "SLA_BREACH");
                    return row;
                })
                .toList();
        return ResponseEntity.ok(cases);
    }

    @PostMapping("/admin/tasks/demo")
    public ResponseEntity<Task> createDemoTask(
            @Valid @RequestBody CreateDemoTaskRequest req,
            @RequestParam(defaultValue = "system") String actor) {

        return ResponseEntity.ok(taskService.createDemoTask(req, actor));
    }





    // ───── Trend API ─────
    @GetMapping("/ops/dashboard/trend")
    public ResponseEntity<?> getTrend(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to) {

        return ResponseEntity.ok(
                dashboardService.getTrend(from, to)
        );
    }

    // ───── Top Violations ─────
    @GetMapping("/ops/dashboard/top-violations")
    public ResponseEntity<?> getTopViolations() {
        return ResponseEntity.ok(
                dashboardService.getTopViolations()
        );
    }

    // ───── Repeat Offenders ─────
    @GetMapping("/ops/dashboard/repeat-offenders")
    public ResponseEntity<?> getRepeatOffenders() {
        return ResponseEntity.ok(
                dashboardService.getRepeatOffenders()
        );
    }

}