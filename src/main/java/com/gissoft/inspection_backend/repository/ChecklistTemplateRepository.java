package com.gissoft.inspection_backend.repository;

import com.gissoft.inspection_backend.entity.ChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

    /** Returns the single ACTIVE checklist for a given DG / category / phase. */
    @Query("""
        SELECT t FROM ChecklistTemplate t
        WHERE t.dg        = :dg
          AND t.category  = :category
          AND t.phaseType = :phaseType
          AND t.status    = 'ACTIVE'
        ORDER BY t.version DESC
        """)
    Optional<ChecklistTemplate> findActive(@Param("dg")        String dg,
                                            @Param("category")  String category,
                                            @Param("phaseType") String phaseType);

    @Query("""
        SELECT t FROM ChecklistTemplate t
        WHERE (:dg       IS NULL OR t.dg       = :dg)
          AND (:category IS NULL OR t.category = :category)
          AND (:status   IS NULL OR t.status   = :status)
        ORDER BY t.dg, t.category, t.phaseType, t.version DESC
        """)
    List<ChecklistTemplate> findByFilters(@Param("dg")       String dg,
                                           @Param("category") String category,
                                           @Param("status")   String status);

    @Query("""
        SELECT COALESCE(MAX(t.version), 0) FROM ChecklistTemplate t
        WHERE t.dg = :dg AND t.category = :category AND t.phaseType = :phaseType
        """)
    int findMaxVersion(@Param("dg")        String dg,
                       @Param("category")  String category,
                       @Param("phaseType") String phaseType);

    List<ChecklistTemplate> findByPhaseTypeAndStatus(String phaseType, String status);
}
