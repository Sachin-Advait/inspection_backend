package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InspectionRunRepository inspectionRunRepo;
    private final ApprovalRequestRepository approvalRepo;
    private final NoticeRepository noticeRepo;
    private final InspectionAnswerRepository answerRepo;


    private final TaskRepository taskRepo;
    // OracleOutboxEventRepository removed — Oracle integration not active yet

    public DashboardStats getStats(String dg, OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime todayStart = OffsetDateTime.now()
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        long inspectionsToday = inspectionRunRepo.countSince(todayStart);
        long pendingApprovals = approvalRepo.countByStatus("PENDING");
        long overdueTasks = taskRepo.countByAssignedToAndStatusAndDueAtBefore(
                null, "PENDING", OffsetDateTime.now());
        long finesIssued = noticeRepo.count();
        long finesPaid = noticeRepo.countByPaymentStatus("PAID");
        long finesUnpaid = noticeRepo.countByPaymentStatus("UNPAID");

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("whatsapp", "enabled");
        health.put("cloudinary", "enabled");
        health.put("oracle", "demo-mode");   // swap to real stats when Oracle is live

        return new DashboardStats(inspectionsToday, pendingApprovals, overdueTasks,
                finesIssued, finesPaid, finesUnpaid, health);
    }

    public record DashboardStats(
            long inspectionsToday,
            long pendingApprovals,
            long overdueTasks,
            long finesIssued,
            long finesPaid,
            long finesUnpaid,
            Map<String, Object> integrationHealth
    ) {
    }

    // ───── Chart: Pass / Fail Trend ─────
    public List<Map<String, Object>> getTrend(OffsetDateTime from, OffsetDateTime to) {

        List<Object[]> data = inspectionRunRepo.getOutcomeTrend(from, to);

        return data.stream().map(r -> Map.of(
                "date", r[0],
                "outcome", r[1],
                "count", r[2]
        )).toList();
    }

    // ───── Top Violations ─────
    public List<Map<String, Object>> getTopViolations() {

        return answerRepo.getTopViolations().stream()
                .map(r -> Map.of(
                        "code", r[0],
                        "description", r[1],
                        "count", r[2]
                ))
                .toList();
    }

    // ───── Repeat Offenders ─────
    public List<Map<String, Object>> getRepeatOffenders() {

        return inspectionRunRepo.getRepeatOffenders().stream()
                .limit(10)
                .map(r -> Map.of(
                        "entityId", r[0],
                        "violations", r[1]
                ))
                .toList();
    }
}