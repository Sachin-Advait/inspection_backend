package com.gissoft.inspection_backend.repository;

import com.gissoft.inspection_backend.entity.InspectionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InspectionAnswerRepository extends JpaRepository<InspectionAnswer, UUID> {

    @Query("""
        SELECT r.violationCode, COUNT(a)
        FROM InspectionAnswer a
        JOIN ChecklistQuestion q ON q.id = a.questionId
        JOIN ChecklistRule r ON r.question = q
        WHERE a.answer = 'FAIL'
        GROUP BY r.violationCode
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> getTopViolations();
}