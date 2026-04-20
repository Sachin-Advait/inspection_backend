package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.entity.OperationalTypeConfig;
import com.gissoft.inspection_backend.repository.OperationalTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationalTypeService {

    private final OperationalTypeRepository repo;
    private final AuditService auditService;

    public List<OperationalTypeConfig> get(String dg, String category, String phase) {
        return repo.findByDirectorateAndCategoryAndActiveTrue(dg, category);
    }

    public List<OperationalTypeConfig> saveAll(List<OperationalTypeConfig> list, String actor) {
        List<OperationalTypeConfig> saved = repo.saveAll(list);

        // ✅ AUDIT
        auditService.log(actor, "UPSERT", "OperationalTypeConfig", "BULK");

        return saved;
    }
}