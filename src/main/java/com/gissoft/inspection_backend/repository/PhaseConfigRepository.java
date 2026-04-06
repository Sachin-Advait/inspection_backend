package com.gissoft.inspection_backend.repository;

import com.gissoft.inspection_backend.entity.OperationalTypeConfig;
import com.gissoft.inspection_backend.entity.PhaseConfig;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface PhaseConfigRepository extends JpaRepository<PhaseConfig, UUID> {

    Optional<PhaseConfig> findByDirectorateAndCategoryAndPhaseType(
            String dg, String category, String phaseType
    );

    List<PhaseConfig> findByDirectorateAndCategoryAndActiveTrueOrderBySortOrderAsc(
            String dg, String category
    );

    // ✅ Get all phases by DG
    List<PhaseConfig> findByDirectorateAndActiveTrueOrderBySortOrderAsc(String dg);

    // ✅ Get distinct categories
    @Query("SELECT DISTINCT p.category FROM PhaseConfig p WHERE p.directorate = :dg AND p.active = true")
    List<String> findDistinctCategoryByDirectorateAndActiveTrue(@Param("dg") String dg);

    @Query("SELECT DISTINCT p.phaseType FROM PhaseConfig p WHERE p.directorate = :dg AND p.active = true")
    List<String> findDistinctPhaseTypeByDirectorate(@Param("dg") String dg);

    List<PhaseConfig> findByCategoryAndActiveTrueOrderBySortOrderAsc(String category);
}