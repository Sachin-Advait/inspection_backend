package com.gissoft.inspection_backend.repository;

import com.gissoft.inspection_backend.entity.OperationalTypeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OperationalTypeRepository extends JpaRepository<OperationalTypeConfig, UUID> {

    List<OperationalTypeConfig> findByDirectorateAndCategoryAndActiveTrue(
            String dg, String category
    );
    List<OperationalTypeConfig> findByDirectorateAndActiveTrue(String dg);
}