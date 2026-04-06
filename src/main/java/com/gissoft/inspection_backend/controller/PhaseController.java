package com.gissoft.inspection_backend.controller;

import com.gissoft.inspection_backend.entity.PhaseConfig;
import com.gissoft.inspection_backend.services.PhaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/admin/phases")
@RequiredArgsConstructor
public class PhaseController {

    private final PhaseService phaseService;

    // ✅ Get categories by DG
    @GetMapping("/categories")
    public List<String> getCategories(@RequestParam String dg) {
        return phaseService.getCategoriesByDirectorate(dg);
    }

    // ✅ Get phases by DG + category
    @GetMapping
    public List<PhaseConfig> getPhases(
            @RequestParam(required = false) String dg,
            @RequestParam(required = false) String category
    ) {
        return phaseService.getPhases(dg, category);
    }


    // ✅ Create
    @PostMapping
    public PhaseConfig create(@RequestBody PhaseConfig phase,
                              Principal principal) {
        return phaseService.create(phase, principal.getName());
    }

    // ✅ Bulk save
    @PostMapping("/bulk")
    public List<PhaseConfig> bulkSave(@RequestBody List<PhaseConfig> phases,
                                      Principal principal) {
        return phaseService.saveAll(phases, principal.getName());
    }

    @GetMapping("/phase-types")
    public List<String> getPhaseTypes(@RequestParam String dg) {
        return phaseService.getPhaseTypesByDirectorate(dg);
    }
}