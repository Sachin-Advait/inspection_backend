package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.entity.PhaseConfig;
import com.gissoft.inspection_backend.repository.PhaseConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PhaseService {

    private final PhaseConfigRepository repo;
    private final AuditService auditService;

    // ✅ Get categories
    public List<String> getCategoriesByDirectorate(String dg) {
        return repo.findDistinctCategoryByDirectorateAndActiveTrue(dg);
    }

    // ✅ Get phases by DG + category
    public List<PhaseConfig> getPhases(String dg, String category) {

        // ❌ both null
        if (dg == null && category == null) {
            throw new IllegalArgumentException("At least one filter (dg or category) is required");
        }

        // ✅ both present
        if (dg != null && category != null) {
            return repo.findByDirectorateAndCategoryAndActiveTrueOrderBySortOrderAsc(dg, category);
        }

        // ✅ only dg
        if (dg != null) {
            return repo.findByDirectorateAndActiveTrueOrderBySortOrderAsc(dg);
        }

        // ✅ only category
        return repo.findByCategoryAndActiveTrueOrderBySortOrderAsc(category);
    }


    // ✅ Create
    public PhaseConfig create(PhaseConfig phase, String actor) {
        PhaseConfig saved = repo.save(phase);
        auditService.log(actor, "CREATE", "PhaseConfig", saved.getId().toString());
        return saved;
    }

    // ✅ Bulk save
    public List<PhaseConfig> saveAll(List<PhaseConfig> phases, String actor) {
        List<PhaseConfig> saved = repo.saveAll(phases);
        auditService.log(actor, "UPSERT", "PhaseConfig", "BULK");
        return saved;
    }

    public List<String> getPhaseTypesByDirectorate(String dg) {
        return repo.findDistinctPhaseTypeByDirectorate(dg);
    }
}