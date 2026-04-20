package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.dto.ChecklistDto.*;
import com.gissoft.inspection_backend.entity.*;
import com.gissoft.inspection_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final ChecklistTemplateRepository templateRepo;
    private final ChecklistSectionRepository  sectionRepo;
    private final ChecklistQuestionRepository questionRepo;
    private final AuditService                auditService;
    private final ViolationCodeRepository violationCodeRepo;

    // ── Mobile: get active checklist ─────────────────────────────────────────

    public ChecklistTemplate getActive(String dg, String category, String phaseType) {
        return templateRepo.findActive(dg, category, phaseType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active checklist for: " + dg + "/" + category + "/" + phaseType));
    }

    // ── Admin: list ───────────────────────────────────────────────────────────

    public List<ChecklistTemplate> list(String dg, String category, String status) {
        return templateRepo.findByFilters(dg, category, status);
    }

    public ChecklistTemplate findById(UUID id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    // ── Admin: create template ────────────────────────────────────────────────

    @Transactional
    public ChecklistTemplate createTemplate(CreateTemplateRequest req, String actor) {
        int next = templateRepo.findMaxVersion(req.dg(), req.category(), req.phaseType()) + 1;

        ChecklistTemplate t = ChecklistTemplate.builder()
                .name(req.name())
                .dg(req.dg())
                .category(req.category())
                .phaseType(req.phaseType())
                .version(next)
                .status("DRAFT")
                .build();

        t = templateRepo.save(t);
        auditService.log(actor, "CREATE", "ChecklistTemplate", t.getId().toString());
        return t;
    }

    // ── Admin: add section ────────────────────────────────────────────────────

    @Transactional
    public ChecklistSection addSection(UUID templateId, AddSectionRequest req, String actor) {
        ChecklistTemplate template = findById(templateId);
        assertNotActive(template);;

        ChecklistSection section = ChecklistSection.builder()
                .template(template)
                .sortOrder(req.sortOrder())
                .title(req.title())
                .description(req.description())
                .build();

        section = sectionRepo.save(section);
        auditService.log(actor, "ADD_SECTION", "ChecklistTemplate", templateId.toString());
        return section;
    }

    // ── Admin: add question ───────────────────────────────────────────────────

    @Transactional
    public ChecklistQuestion addQuestion(UUID sectionId, AddQuestionRequest req, String actor) {
        ChecklistSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));

        assertNotActive(section.getTemplate());

        ChecklistQuestion question = ChecklistQuestion.builder()
                .section(section)
                .sortOrder(req.sortOrder())
                .text(req.text())
                .answerType(req.answerType())
                .required(req.required())
                .validationsJson(req.validationsJson())
                .build();

        question = questionRepo.save(question);
        auditService.log(actor, "ADD_QUESTION", "ChecklistSection", sectionId.toString());
        return question;
    }

    // ── Admin: set rule on question ───────────────────────────────────────────

    @Transactional
    public ChecklistRule setRule(UUID questionId, SetRuleRequest req, String actor) {
        ChecklistQuestion question = questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        assertNotActive(question.getSection().getTemplate());

        ChecklistRule rule = question.getRule();
        if (rule == null) {
            rule = ChecklistRule.builder().question(question).build();
        }
        rule.setFailSeverity(req.failSeverity());
        rule.setEvidencePolicyJson(req.evidencePolicyJson());
        ViolationCode vc = violationCodeRepo.findByCode(req.violationCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid violation code"));

        rule.setViolationCode(vc);
        rule.setDefaultAction(req.defaultAction());
        rule.setForceApprovalLevel(req.forceApprovalLevel());
        rule.setReinspectionSuggestionJson(req.reinspectionSuggestionJson());

        question.setRule(rule);
        questionRepo.save(question);

        auditService.log(actor, "SET_RULE", "ChecklistQuestion", questionId.toString());
        return rule;
    }

    // ── Admin: publish ────────────────────────────────────────────────────────

    @Transactional
    public ChecklistTemplate publish(UUID templateId, PublishRequest req, String actor) {
        ChecklistTemplate t = findById(templateId);
        assertNotActive(t);

        t.setStatus("PUBLISHED");
        t.setReleaseNotes(req.releaseNotes());
        t = templateRepo.save(t);

        auditService.log(actor, "PUBLISH", "ChecklistTemplate", templateId.toString());
        return t;
    }

    // ── Admin: activate ───────────────────────────────────────────────────────

    @Transactional
    public ChecklistTemplate activate(UUID templateId, String actor) {
        ChecklistTemplate t = findById(templateId);
        if (!"PUBLISHED".equals(t.getStatus())) {
            throw new IllegalStateException("Only PUBLISHED templates can be activated");
        }

        templateRepo.findActive(t.getDg(), t.getCategory(), t.getPhaseType())
                .ifPresent(existing -> {
                    existing.setStatus("RETIRED");
                    templateRepo.save(existing);
                });

        t.setStatus("ACTIVE");
        t = templateRepo.save(t);

        auditService.log(actor, "ACTIVATE", "ChecklistTemplate", templateId.toString());
        return t;
    }

    public ChecklistQuestion getQuestion(UUID questionId) {
        return questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void assertNotActive(ChecklistTemplate t) {
        if ("ACTIVE".equals(t.getStatus())) {
            throw new IllegalStateException(
                    "ACTIVE checklist cannot be modified: " + t.getId());
        }
    }

    @Transactional
    public void deleteQuestion(UUID questionId, String actor) {
        ChecklistQuestion question = questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));

        ChecklistTemplate template = question.getSection().getTemplate();
        assertNotActive(template);

        question.getSection().getQuestions().remove(question);
        questionRepo.flush();

        auditService.log(actor, "DELETE_QUESTION", "ChecklistQuestion", questionId.toString());
    }

    @Transactional
    public void deleteSection(UUID sectionId, String actor) {
        ChecklistSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));

        ChecklistTemplate template = section.getTemplate();
        assertNotActive(template);

        template.getSections().remove(section);
        sectionRepo.flush();

        auditService.log(actor, "DELETE_SECTION", "ChecklistSection", sectionId.toString());
    }

    @Transactional
    public void deleteTemplate(UUID templateId, String actor) {
        ChecklistTemplate template = findById(templateId);

        if ("ACTIVE".equals(template.getStatus())) {
            throw new IllegalStateException("Cannot delete ACTIVE template");
        }

        template.getSections().clear();
        templateRepo.delete(template);
        templateRepo.flush();

        auditService.log(actor, "DELETE_TEMPLATE", "ChecklistTemplate", templateId.toString());
    }

    @Transactional
    public ChecklistTemplate updateTemplate(UUID id, UpdateTemplateRequest req, String actor) {
        ChecklistTemplate t = findById(id);

        if ("ACTIVE".equals(t.getStatus())) {
            throw new IllegalStateException("Cannot edit ACTIVE checklist");
        }

        if (req.name() != null)       t.setName(req.name());
        if (req.dg() != null)         t.setDg(req.dg());
        if (req.category() != null)   t.setCategory(req.category());
        if (req.phaseType() != null)  t.setPhaseType(req.phaseType());

        t = templateRepo.save(t);

        auditService.log(actor, "UPDATE_TEMPLATE", "ChecklistTemplate", id.toString());
        return t;
    }
    @Transactional
    public ChecklistSection updateSection(UUID sectionId, UpdateSectionRequest req, String actor) {
        ChecklistSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        if ("ACTIVE".equals(section.getTemplate().getStatus())) {
            throw new IllegalStateException("Cannot edit ACTIVE checklist");
        }

        if (req.title() != null)        section.setTitle(req.title());
        if (req.description() != null)  section.setDescription(req.description());
        if (req.sortOrder() != 0)       section.setSortOrder(req.sortOrder());

        section = sectionRepo.save(section);

        auditService.log(actor, "UPDATE_SECTION", "ChecklistSection", sectionId.toString());
        return section;
    }
    @Transactional
    public ChecklistQuestion updateQuestion(UUID questionId, UpdateQuestionRequest req, String actor) {
        ChecklistQuestion q = questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        if ("ACTIVE".equals(q.getSection().getTemplate().getStatus())) {
            throw new IllegalStateException("Cannot edit ACTIVE checklist");
        }

        if (req.text() != null)              q.setText(req.text());
        if (req.answerType() != null)        q.setAnswerType(req.answerType());
        if (req.sortOrder() != 0)            q.setSortOrder(req.sortOrder());
        if (req.validationsJson() != null)   q.setValidationsJson(req.validationsJson());

        // boolean needs special handling
        q.setRequired(req.required());

        q = questionRepo.save(q);

        auditService.log(actor, "UPDATE_QUESTION", "ChecklistQuestion", questionId.toString());
        return q;
    }

    @Transactional
    public ChecklistTemplate updateStatus(UUID templateId, String status, String actor) {
        ChecklistTemplate t = findById(templateId);

        switch (status.toUpperCase()) {
            case "PUBLISHED":

                // Allow ACTIVE → PUBLISHED (deactivate)
                if ("ACTIVE".equals(t.getStatus())) {
                    t.setStatus("PUBLISHED");
                    break;
                }

                // Existing rule
                if (!"DRAFT".equals(t.getStatus())) {
                    throw new IllegalStateException("Only DRAFT can be published");
                }

                t.setStatus("PUBLISHED");
                break;

            case "ACTIVE":
                if (!"PUBLISHED".equals(t.getStatus())) {
                    throw new IllegalStateException("Only PUBLISHED can be activated");
                }

                // deactivate existing active
                templateRepo.findActive(t.getDg(), t.getCategory(), t.getPhaseType())
                        .ifPresent(existing -> {
                            existing.setStatus("RETIRED");
                            templateRepo.save(existing);
                        });

                t.setStatus("ACTIVE");
                break;

            case "DRAFT":
                throw new IllegalStateException("Cannot revert back to DRAFT");

            default:
                throw new IllegalArgumentException("Invalid status: " + status);
        }

        t = templateRepo.save(t);

        auditService.log(actor, "UPDATE_STATUS_" + status, "ChecklistTemplate", templateId.toString());
        return t;
    }
    @Transactional(readOnly = true)
    public ChecklistTemplate getTemplateById(UUID id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
    }

    @Transactional(readOnly = true)
    public List<ChecklistTemplate> getActiveByPhaseType(String phaseType) {
        return templateRepo.findByPhaseTypeAndStatus(phaseType, "ACTIVE");
    }
}