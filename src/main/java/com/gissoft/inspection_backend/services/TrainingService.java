package com.gissoft.inspection_backend.services;

import com.gissoft.inspection_backend.dto.TrainingEditDTO;
import com.gissoft.inspection_backend.dto.TrainingEngagementDTO;
import com.gissoft.inspection_backend.dto.TrainingUploadAssignDTO;
import com.gissoft.inspection_backend.dto.UserTrainingDTO;
import com.gissoft.inspection_backend.entity.AppUser;
import com.gissoft.inspection_backend.entity.TrainingAssignment;
import com.gissoft.inspection_backend.entity.TrainingMaterial;
import com.gissoft.inspection_backend.repository.AppUserRepository;
import com.gissoft.inspection_backend.repository.TrainingAssignmentRepository;
import com.gissoft.inspection_backend.repository.TrainingMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingMaterialRepository materialRepo;
    private final TrainingAssignmentRepository assignmentRepo;
    private final AppUserRepository userRepo;
    private final AuditService auditService;

    /* ======================================================
       ADMIN
       ====================================================== */
    public TrainingMaterial uploadAndAssign(TrainingUploadAssignDTO request, String actor) {

        TrainingMaterial material = TrainingMaterial.builder()
                .title(request.getTitle())
                .type(request.getType())
                .duration(request.getDuration())
                .cloudinaryPublicId(request.getCloudinaryPublicId())
                .cloudinaryUrl(request.getCloudinaryUrl())
                .cloudinaryResourceType(request.getCloudinaryResourceType())
                .cloudinaryFormat(request.getCloudinaryFormat())
                .assignedTo(0)
                .completionRate(0)
                .views(0)
                .active(true)
                .uploadDate(Instant.now())
                .dueDate(request.getDueDate())
                .build();

        TrainingMaterial savedMaterial = materialRepo.save(material);

        if (request.getUsernames() != null && !request.getUsernames().isEmpty()) {

            for (String username : request.getUsernames()) {

                boolean alreadyAssigned = assignmentRepo
                        .findByUsernameAndTrainingId(username, savedMaterial.getId())
                        .isPresent();

                if (alreadyAssigned) continue;

                TrainingAssignment assignment = TrainingAssignment.builder()
                        .username(username)
                        .trainingId(savedMaterial.getId())
                        .progress(0)
                        .status("not-started")
                        .dueDate(request.getDueDate())
                        .assignedAt(Instant.now())
                        .build();

                assignmentRepo.save(assignment);
            }

            long totalAssigned = assignmentRepo.countByTrainingId(savedMaterial.getId());
            savedMaterial.setAssignedTo((int) totalAssigned);
            materialRepo.save(savedMaterial);
        }

        // ✅ AUDIT
        auditService.log(actor, "UPLOAD_TRAINING", "TrainingMaterial", savedMaterial.getId().toString());

        return savedMaterial;
    }

    public List<TrainingMaterial> getAllMaterials() {
        return materialRepo.findAllByActiveTrue();
    }

    public void assignTraining(Long trainingId, List<String> userIds, Instant dueDate, String actor) {

        TrainingMaterial material = materialRepo.findByIdAndActiveTrue(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        for (String userId : userIds) {

            boolean alreadyAssigned =
                    assignmentRepo.findByUsernameAndTrainingId(userId, trainingId).isPresent();

            if (alreadyAssigned) continue;

            TrainingAssignment assignment = TrainingAssignment.builder()
                    .username(userId)
                    .trainingId(trainingId)
                    .progress(0)
                    .status("not-started")
                    .dueDate(dueDate)
                    .assignedAt(Instant.now())
                    .build();

            assignmentRepo.save(assignment);
        }

        material.setAssignedTo((int) assignmentRepo.countByTrainingId(trainingId));
        materialRepo.save(material);

        // ✅ AUDIT
        auditService.log(actor, "ASSIGN_TRAINING", "TrainingMaterial", trainingId.toString());
    }

    /* ======================================================
       USER
       ====================================================== */

    public List<TrainingAssignment> getUserTrainings(String userId) {
        return assignmentRepo.findByUsername(userId);
    }

    public List<UserTrainingDTO> getUserTrainingDetails(String userId) {

        List<TrainingAssignment> assignments = assignmentRepo.findByUsername(userId);

        return assignments.stream()
                .map(a -> {
                    TrainingMaterial material =
                            materialRepo.findByIdAndActiveTrue(a.getTrainingId()).orElse(null);

                    if (material == null) return null;

                    return new UserTrainingDTO(
                            a.getId(),
                            material.getId(),
                            material.getTitle(),
                            material.getType(),
                            material.getDuration(),
                            material.getCloudinaryUrl(),
                            material.getCloudinaryFormat(),
                            material.getCloudinaryResourceType(),
                            a.getProgress(),
                            a.getStatus(),
                            a.getDueDate()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public TrainingAssignment updateProgress(String username, Long trainingId, int progress) {

        TrainingAssignment assignment =
                assignmentRepo.findByUsernameAndTrainingId(username, trainingId)
                        .orElseThrow(() -> new RuntimeException("Training not assigned"));

        if (progress > assignment.getProgress()) {
            assignment.setProgress(progress);
        }

        if (assignment.getProgress() >= 100) {
            assignment.setStatus("completed");
        } else if (assignment.getProgress() > 0) {
            assignment.setStatus("in-progress");
        }

        assignmentRepo.save(assignment);

        if (assignment.getProgress() >= 100) {
            long total = assignmentRepo.countByTrainingId(trainingId);
            long completed = assignmentRepo.countByTrainingIdAndStatus(trainingId, "completed");

            materialRepo.findByIdAndActiveTrue(trainingId).ifPresent(material -> {
                int rate = total == 0 ? 0 : (int) ((completed * 100) / total);
                material.setCompletionRate(rate);
                materialRepo.save(material);
            });
        }

        return assignment;
    }

    public List<TrainingEngagementDTO> getEngagement(Long trainingId) {

        List<TrainingAssignment> assignments =
                trainingId != null
                        ? assignmentRepo.findByTrainingId(trainingId)
                        : assignmentRepo.findAll();

        return assignments.stream()
                .map(a -> {
                    AppUser user = userRepo.findByUsername(a.getUsername()).orElse(null);
                    TrainingMaterial material =
                            materialRepo.findByIdAndActiveTrue(a.getTrainingId()).orElse(null);

                    if (user == null || material == null) return null;

                    return TrainingEngagementDTO.builder()
                            .userId(user.getId())
                            .learner(user.getFullName())
                            .trainingId(material.getId())
                            .video(material.getTitle())
                            .progress(a.getProgress())
                            .status(a.getStatus())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /* ======================================================
       UPDATE / DELETE
       ====================================================== */

    public TrainingMaterial updateTraining(Long trainingId,
                                           TrainingUploadAssignDTO request,
                                           String actor) {

        TrainingMaterial material =
                materialRepo.findByIdAndActiveTrue(trainingId)
                        .orElseThrow(() -> new RuntimeException("Training not found"));

        if (request.getTitle() != null) material.setTitle(request.getTitle());
        if (request.getType() != null) material.setType(request.getType());
        if (request.getDuration() != null) material.setDuration(request.getDuration());

        if (request.getCloudinaryUrl() != null) {
            material.setCloudinaryUrl(request.getCloudinaryUrl());
            material.setCloudinaryPublicId(request.getCloudinaryPublicId());
            material.setCloudinaryResourceType(request.getCloudinaryResourceType());
            material.setCloudinaryFormat(request.getCloudinaryFormat());
        }

        materialRepo.save(material);

        List<String> newUsernames = request.getUsernames() != null ? request.getUsernames() : List.of();
        List<TrainingAssignment> existing = assignmentRepo.findByTrainingId(trainingId);

        List<String> existingUsername =
                existing.stream().map(TrainingAssignment::getUsername).toList();

        for (String username : newUsernames) {
            if (existingUsername.contains(username)) continue;

            TrainingAssignment assignment = TrainingAssignment.builder()
                    .username(username)
                    .trainingId(trainingId)
                    .progress(0)
                    .status("not-started")
                    .dueDate(request.getDueDate())
                    .assignedAt(Instant.now())
                    .build();

            assignmentRepo.save(assignment);
        }

        for (TrainingAssignment a : existing) {
            if (!newUsernames.contains(a.getUsername())) {
                assignmentRepo.delete(a);
            } else {
                a.setDueDate(request.getDueDate());
                assignmentRepo.save(a);
            }
        }

        material.setAssignedTo((int) assignmentRepo.countByTrainingId(trainingId));
        materialRepo.save(material);

        // ✅ AUDIT
        auditService.log(actor, "UPDATE_TRAINING", "TrainingMaterial", trainingId.toString());

        return material;
    }

    public void deleteTraining(Long trainingId, String actor) {

        TrainingMaterial material =
                materialRepo.findByIdAndActiveTrue(trainingId)
                        .orElseThrow(() -> new RuntimeException("Training not found"));

        material.setActive(false);
        material.setDeletedAt(Instant.now());

        materialRepo.save(material);

        // ✅ AUDIT
        auditService.log(actor, "DELETE_TRAINING", "TrainingMaterial", trainingId.toString());
    }

    public TrainingEditDTO getTrainingById(Long trainingId) {

        TrainingMaterial material = materialRepo.findByIdAndActiveTrue(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        List<String> assignedUsers = assignmentRepo.findByTrainingId(trainingId)
                .stream()
                .map(TrainingAssignment::getUsername)
                .toList();

        return TrainingEditDTO.builder()
                .id(String.valueOf(material.getId()))
                .title(material.getTitle())
                .type(material.getType())
                .duration(material.getDuration())

                .assignedTo(material.getAssignedTo())
                .completionRate(material.getCompletionRate())

                .videoProvider("cloudinary")
                .videoPublicId(material.getCloudinaryPublicId())
                .videoPlaybackUrl(material.getCloudinaryUrl())
                .videoFormat(material.getCloudinaryFormat())
                .dueDate(material.getDueDate())

                .active(material.getActive())
                .uploadDate(material.getUploadDate())

                .assignedUserIds(assignedUsers)
                .build();
    }

    public List<TrainingAssignment> getAssignmentsByTraining(Long trainingId) {

        materialRepo.findByIdAndActiveTrue(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        return assignmentRepo.findByTrainingId(trainingId);
    }
}