package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.entity.ChecklistQuestion;
import com.gissoft.inspection_backend.entity.FineRule;
import com.gissoft.inspection_backend.entity.InspectionAnswer;
import com.gissoft.inspection_backend.entity.ViolationCode;
import com.gissoft.inspection_backend.repository.ChecklistQuestionRepository;
import com.gissoft.inspection_backend.repository.FineRuleRepository;
import com.gissoft.inspection_backend.repository.ViolationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ViolationService {

    private final ViolationCodeRepository violationCodeRepo;
    private final FineRuleRepository      fineRuleRepo;
    private final AuditService            auditService;
    private final ChecklistQuestionRepository questionRepo;

    // ── Violation codes ───────────────────────────────────────────────────────

    public List<ViolationCode> listCodes() {
        return violationCodeRepo.findAll();
    }

    @Transactional
    public ViolationCode createCode(ViolationCode req, String actor) {
        if (violationCodeRepo.existsByCode(req.getCode())) {
            throw new IllegalArgumentException("Code already exists: " + req.getCode());
        }
        req.setActive(true);
        ViolationCode vc = violationCodeRepo.save(req);

        fineRuleRepo.save(
                FineRule.builder()
                        .violationCode(vc.getCode())
                        .baseFine(0L)
                        .maxFine(0L)
                        .approvalRequired("NONE")
                        .build()
        );

        // ✅ AUDIT
        auditService.log(actor, "CREATE", "ViolationCode", vc.getId().toString());

        return vc;
    }

    @Transactional
    public ViolationCode updateCode(UUID id, ViolationCode req, String actor) {
        ViolationCode vc = violationCodeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Code not found: " + id));

        Map<String, Object> changes = new HashMap<>();

        changes.put("description_from", vc.getDescription());
        changes.put("description_to", req.getDescription());

        changes.put("severity_from", vc.getSeverity());
        changes.put("severity_to", req.getSeverity());

        changes.put("defaultAction_from", vc.getDefaultAction());
        changes.put("defaultAction_to", req.getDefaultAction());

        changes.put("legalRef_from", vc.getLegalRef());
        changes.put("legalRef_to", req.getLegalRef());

        changes.put("active_from", vc.isActive());
        changes.put("active_to", req.isActive());

        vc.setDescription(req.getDescription());
        vc.setSeverity(req.getSeverity());
        vc.setDefaultAction(req.getDefaultAction());
        vc.setLegalRef(req.getLegalRef());
        vc.setActive(req.isActive());

        vc = violationCodeRepo.save(vc);

        // ✅ AUDIT with diff
        auditService.log(actor, "UPDATE", "ViolationCode", id.toString(), changes);

        return vc;
    }

    // ── Fine rules ────────────────────────────────────────────────────────────

    public List<FineRule> listFineRules() {
        return fineRuleRepo.findAll();
    }

    @Transactional
    public FineRule upsertFineRule(FineRule req, String actor) {
        FineRule existing = fineRuleRepo.findByViolationCode(req.getViolationCode())
                .orElse(null);

        Map<String, Object> changes = new HashMap<>();

        if (existing != null) {
            changes.put("baseFine_from", existing.getBaseFine());
            changes.put("baseFine_to", req.getBaseFine());

            changes.put("maxFine_from", existing.getMaxFine());
            changes.put("maxFine_to", req.getMaxFine());

            changes.put("approvalRequired_from", existing.getApprovalRequired());
            changes.put("approvalRequired_to", req.getApprovalRequired());
        }

        FineRule rule = existing != null
                ? existing
                : FineRule.builder().violationCode(req.getViolationCode()).build();

        rule.setBaseFine(req.getBaseFine());
        rule.setMaxFine(req.getMaxFine());
        rule.setApprovalRequired(req.getApprovalRequired());

        rule = fineRuleRepo.save(rule);

        // ✅ AUDIT (with diff if update)
        auditService.log(actor, "UPSERT", "FineRule", rule.getId().toString(), changes);

        return rule;
    }

    public long calculateFine(List<InspectionAnswer> answers) {

        return answers.stream()
                .filter(a -> "FAIL".equalsIgnoreCase(a.getAnswer()))
                .map(a -> {
                    ChecklistQuestion q = questionRepo.findById(a.getQuestionId()).orElse(null);

                    if (q == null || q.getRule() == null || q.getRule().getViolationCode() == null) {
                        return 0L;
                    }

                    String code = q.getRule().getViolationCode().getCode();

                    return fineRuleRepo.findByViolationCode(code)
                            .map(rule -> Math.min(rule.getBaseFine(), rule.getMaxFine()))
                            .orElse(0L);
                })
                .reduce(0L, Long::sum);
    }

    public List<ViolationCode> listCodesbyDg(String dg, String category) {
        return violationCodeRepo.findByDgAndCategoryAndActiveTrue(dg, category);
    }
}