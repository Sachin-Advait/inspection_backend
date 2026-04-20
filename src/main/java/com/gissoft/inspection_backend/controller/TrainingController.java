package com.gissoft.inspection_backend.controller;

import com.gissoft.inspection_backend.dto.TrainingEditDTO;
import com.gissoft.inspection_backend.dto.TrainingEngagementDTO;
import com.gissoft.inspection_backend.dto.TrainingUploadAssignDTO;
import com.gissoft.inspection_backend.dto.UserTrainingDTO;
import com.gissoft.inspection_backend.entity.TrainingAssignment;
import com.gissoft.inspection_backend.entity.TrainingMaterial;
import com.gissoft.inspection_backend.services.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;

    // ================= ADMIN =================
    @PostMapping
    public ResponseEntity<TrainingMaterial> uploadTraining(
            @RequestBody TrainingUploadAssignDTO request,
            Principal principal) {

        TrainingMaterial savedMaterial =
                trainingService.uploadAndAssign(request, principal.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(savedMaterial);
    }

    @GetMapping
    public ResponseEntity<List<TrainingMaterial>> getAllTrainings() {
        return ResponseEntity.ok(trainingService.getAllMaterials());
    }

    @PostMapping("/assign")
    public ResponseEntity<Void> assignTraining(
            @RequestBody Map<String, Object> payload,
            Principal principal) {

        Long trainingId = Long.valueOf(payload.get("trainingId").toString());
        List<String> userIds = (List<String>) payload.get("usernames");
        Instant dueDate = Instant.parse(payload.get("dueDate").toString());

        trainingService.assignTraining(trainingId, userIds, dueDate, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ================= USER =================
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TrainingAssignment>> getUserTrainings(@PathVariable String userId) {
        return ResponseEntity.ok(trainingService.getUserTrainings(userId));
    }

    @GetMapping("/user/{userId}/details")
    public ResponseEntity<List<UserTrainingDTO>> getUserTrainingDetails(@PathVariable String userId) {
        return ResponseEntity.ok(trainingService.getUserTrainingDetails(userId));
    }

    @PostMapping("/progress")
    public ResponseEntity<TrainingAssignment> updateProgress(
            @RequestBody Map<String, Object> payload) {

        String username = payload.get("username").toString();
        Long trainingId = Long.valueOf(payload.get("trainingId").toString());
        int progress = Integer.parseInt(payload.get("progress").toString());

        TrainingAssignment updated =
                trainingService.updateProgress(username, trainingId, progress);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/engagement")
    public ResponseEntity<List<TrainingEngagementDTO>> getEngagement(
            @RequestParam(required = false) Long trainingId) {
        return ResponseEntity.ok(trainingService.getEngagement(trainingId));
    }

    // ================= ADMIN =================
    @PutMapping("/{trainingId}")
    public ResponseEntity<TrainingMaterial> updateTraining(
            @PathVariable Long trainingId,
            @RequestBody TrainingUploadAssignDTO request,
            Principal principal) {

        TrainingMaterial updated =
                trainingService.updateTraining(trainingId, request, principal.getName());

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{trainingId}")
    public ResponseEntity<Void> deleteTraining(
            @PathVariable Long trainingId,
            Principal principal) {

        trainingService.deleteTraining(trainingId, principal.getName());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{trainingId}")
    public ResponseEntity<TrainingEditDTO> getTrainingById(
            @PathVariable Long trainingId) {

        TrainingEditDTO material = trainingService.getTrainingById(trainingId);
        return ResponseEntity.ok(material);
    }

    @GetMapping("/{trainingId}/assignments")
    public ResponseEntity<List<TrainingAssignment>> getAssignmentsByTraining(
            @PathVariable Long trainingId) {

        return ResponseEntity.ok(
                trainingService.getAssignmentsByTraining(trainingId)
        );
    }
}