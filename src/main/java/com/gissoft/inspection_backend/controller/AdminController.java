package com.gissoft.inspection_backend.controller;

import com.gissoft.inspection_backend.dto.ChecklistDto.*;
import com.gissoft.inspection_backend.dto.UserDto.CreateUserRequest;
import com.gissoft.inspection_backend.dto.UserDto.UpdateUserRequest;
import com.gissoft.inspection_backend.entity.*;
import com.gissoft.inspection_backend.services.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserAdminService userAdminService;
    private final ChecklistService checklistService;
    private final ViolationService violationService;
    private final AuditService auditService;

    // =========================================================================
    // Users (A01)
    // =========================================================================

    @GetMapping("/users")
    public ResponseEntity<Page<AppUser>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(userAdminService.list(pageable));
    }

    @PostMapping("/users")
    public ResponseEntity<AppUser> createUser(
            @Valid @RequestBody CreateUserRequest req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(userAdminService.create(req, actor));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AppUser> getUser(
            @PathVariable String id,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(userAdminService.findByUsername(id));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<AppUser> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(userAdminService.update(id, req, actor));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "admin") String actor) {
        userAdminService.deactivate(id, actor);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/inspectors
     */
    @GetMapping("/inspectors")
    public ResponseEntity<List<AppUser>> getInspectors() {
        return ResponseEntity.ok(userAdminService.getInspectors());
    }

    // =========================================================================
    // Checklists (A03 + A04)
    // =========================================================================

    @GetMapping("/checklists/templates")
    public ResponseEntity<List<ChecklistTemplate>> listTemplates(
            @RequestParam(required = false) String dg,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(checklistService.list(dg, category, status));
    }

    @PostMapping("/checklists/templates")
    public ResponseEntity<ChecklistTemplate> createTemplate(
            @Valid @RequestBody CreateTemplateRequest req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(checklistService.createTemplate(req, actor));
    }

    @PostMapping("/checklists/templates/{templateId}/sections")
    public ResponseEntity<ChecklistSection> addSection(
            @PathVariable UUID templateId,
            @Valid @RequestBody AddSectionRequest req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(checklistService.addSection(templateId, req, actor));
    }

    @PostMapping("/checklists/sections/{sectionId}/questions")
    public ResponseEntity<ChecklistQuestion> addQuestion(
            @PathVariable UUID sectionId,
            @Valid @RequestBody AddQuestionRequest req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(checklistService.addQuestion(sectionId, req, actor));
    }

    @PostMapping("/checklists/questions/{questionId}/rule")
    public ResponseEntity<ChecklistRule> setRule(
            @PathVariable UUID questionId,
            @Valid @RequestBody SetRuleRequest req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(checklistService.setRule(questionId, req, actor));
    }

    @PostMapping("/checklists/templates/{templateId}/publish")
    public ResponseEntity<ChecklistTemplate> publishTemplate(
            @PathVariable UUID templateId,
            @RequestBody(required = false) PublishRequest req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(
                checklistService.publish(templateId,
                        req != null ? req : new PublishRequest(null), actor));
    }

    @PostMapping("/checklists/templates/{templateId}/activate")
    public ResponseEntity<ChecklistTemplate> activateTemplate(
            @PathVariable UUID templateId,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(checklistService.activate(templateId, actor));
    }


    @PutMapping("/checklists/templates/{id}")
    public ResponseEntity<ChecklistTemplate> updateTemplate(
            @PathVariable UUID id,
            @RequestBody UpdateTemplateRequest req,
            @RequestParam(defaultValue = "admin") String actor) {

        return ResponseEntity.ok(checklistService.updateTemplate(id, req, actor));
    }

    @PutMapping("/checklists/sections/{sectionId}")
    public ResponseEntity<ChecklistSection> updateSection(
            @PathVariable UUID sectionId,
            @RequestBody UpdateSectionRequest req,
            @RequestParam(defaultValue = "admin") String actor) {

        return ResponseEntity.ok(checklistService.updateSection(sectionId, req, actor));
    }
    @PutMapping("/checklists/questions/{questionId}")
    public ResponseEntity<ChecklistQuestion> updateQuestion(
            @PathVariable UUID questionId,
            @RequestBody UpdateQuestionRequest req,
            @RequestParam(defaultValue = "admin") String actor) {

        return ResponseEntity.ok(checklistService.updateQuestion(questionId, req, actor));
    }

    @PatchMapping("/checklists/templates/{id}/status")
    public ResponseEntity<ChecklistTemplate> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(defaultValue = "admin") String actor) {

        return ResponseEntity.ok(checklistService.updateStatus(id, status, actor));
    }

    @GetMapping("/checklists/templates/{id}")
    public ResponseEntity<ChecklistTemplate> getTemplateById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(checklistService.getTemplateById(id));
    }

    @GetMapping("/checklists/templates/by-phase")
    public ResponseEntity<List<ChecklistTemplate>> getByPhaseType(
            @RequestParam String phaseType) {

        return ResponseEntity.ok(
                checklistService.getActiveByPhaseType(phaseType)
        );
    }

    // =========================================================================
    // Violations (A05 + A06)
    // =========================================================================

    @GetMapping("/violations/codes")
    public ResponseEntity<List<ViolationCode>> getCodes(
            @RequestParam(required = false) String dg,
            @RequestParam(required = false) String category
    ) {

        // 🔥 IF FILTER PROVIDED
        if (dg != null && category != null) {
            return ResponseEntity.ok(
                    violationService.listCodesbyDg(dg, category)
            );
        }

        // 🔥 DEFAULT (ALL)
        return ResponseEntity.ok(violationService.listCodes());
    }
    @PostMapping("/violations/codes")
    public ResponseEntity<ViolationCode> createViolationCode(
            @RequestBody ViolationCode req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(violationService.createCode(req, actor));
    }

    @PutMapping("/violations/codes/{id}")
    public ResponseEntity<ViolationCode> updateViolationCode(
            @PathVariable UUID id,
            @RequestBody ViolationCode req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(violationService.updateCode(id, req, actor));
    }

    @GetMapping("/violations/fineRules")
    public ResponseEntity<List<FineRule>> listFineRules() {
        return ResponseEntity.ok(violationService.listFineRules());
    }


    @PostMapping("/violations/fineRules")
    public ResponseEntity<FineRule> upsertFineRule(
            @RequestBody FineRule req,
            @RequestParam(defaultValue = "admin") String actor) {
        return ResponseEntity.ok(violationService.upsertFineRule(req, actor));
    }

    // =========================================================================
    // Config / Categories (A02)
    // =========================================================================

    @GetMapping("/config/categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        return ResponseEntity.ok(Map.of(
                "TECHNICAL", Map.of(
                        "Building", List.of("Foundation", "Structural", "MEP", "Final"),
                        "Road", List.of("Traffic", "Excavation", "Asphalt", "Restoration"),
                        "Lights", List.of("Base", "Cabling", "Install", "Commissioning")
                ),
                "HEALTH", Map.of(
                        "Licensing", List.of("Application", "DocVerify", "PreOpen",
                                "Reinspect", "FinalApprove"),
                        "Operational", List.of("Routine", "Random", "Complaint", "FollowUp")
                )
        ));
    }

    @GetMapping("/config/health-recurrence")
    public ResponseEntity<Map<String, Object>> getHealthRecurrence() {
        return ResponseEntity.ok(Map.of(
                "mode", "RISK_BASED",
                "riskHigh_days", 30,
                "riskMed_days", 60,
                "riskLow_days", 90,
                "failFollowUp_hours", 72,
                "condFollowUp_days", 7
        ));
    }

    // =========================================================================
    // Templates
    // =========================================================================

    @GetMapping("/templates/notices")
    public ResponseEntity<List<Map<String, String>>> getNoticeTemplates() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "notice-warning-en", "type", "WARNING", "lang", "EN"),
                Map.of("id", "notice-warning-ar", "type", "WARNING", "lang", "AR"),
                Map.of("id", "notice-fine-en", "type", "FINE", "lang", "EN"),
                Map.of("id", "notice-fine-ar", "type", "FINE", "lang", "AR"),
                Map.of("id", "notice-closure-en", "type", "CLOSURE", "lang", "EN"),
                Map.of("id", "notice-closure-ar", "type", "CLOSURE", "lang", "AR")
        ));
    }

    @GetMapping("/templates/messages")
    public ResponseEntity<List<Map<String, String>>> getMessageTemplates() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "msg-inspection-reminder", "channel", "WHATSAPP"),
                Map.of("id", "msg-notice-sent", "channel", "WHATSAPP"),
                Map.of("id", "msg-sms-reminder", "channel", "SMS")
        ));
    }

    @GetMapping("/training/modules")
    public ResponseEntity<List<Object>> getTrainingModules() {
        return ResponseEntity.ok(List.of());
    }

    // =========================================================================
    // Integrations (A11 + A12)
    // =========================================================================

    @GetMapping("/integrations")
    public ResponseEntity<Map<String, Object>> getIntegrationConfig() {
        return ResponseEntity.ok(Map.of(
                "oracle", Map.of("enabled", false),
                "whatsapp", Map.of("enabled", false),
                "sms", Map.of("enabled", false)
        ));
    }

    @GetMapping("/integrations/logs")
    public ResponseEntity<Map<String, Object>> getIntegrationLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        return ResponseEntity.ok(Map.of(
                "events", List.of()
        ));
    }

    // =========================================================================
    // Audit (A13)
    // =========================================================================

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLog(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        return ResponseEntity.ok(auditService.search(actor, resourceType, from, to, pageable));
    }
}