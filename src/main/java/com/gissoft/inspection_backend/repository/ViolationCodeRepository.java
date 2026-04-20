package com.gissoft.inspection_backend.repository;

import com.gissoft.inspection_backend.entity.ViolationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ViolationCodeRepository extends JpaRepository<ViolationCode, UUID> {
    Optional<ViolationCode> findByCode(String code);
    boolean existsByCode(String code);
    List<ViolationCode> findByDgAndCategoryAndActiveTrue(String dg, String category);
}
