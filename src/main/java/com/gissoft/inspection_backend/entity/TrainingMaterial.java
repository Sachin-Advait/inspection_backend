package com.gissoft.inspection_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

@Entity
@Table(
        name = "training_materials",
        indexes = {
                @Index(name = "idx_training_active", columnList = "active"),
                @Index(name = "idx_training_cloudinary", columnList = "cloudinary_public_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // 🔁 Mongo String → SQL Long

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String type; // video, document, interactive

    private String duration;

    @Column(name = "assigned_to")
    private Integer assignedTo;

    @Column(name = "completion_rate")
    private Integer completionRate;

    private Integer views;

    /* ===== Cloudinary fields ===== */

    @Column(name = "cloudinary_public_id", unique = true)
    private String cloudinaryPublicId;

    @Column(name = "cloudinary_url", length = 1000)
    private String cloudinaryUrl;

    @Column(name = "cloudinary_resource_type")
    private String cloudinaryResourceType;

    @Column(name = "cloudinary_format")
    private String cloudinaryFormat;

    /* ===== Soft Delete ===== */

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /* ===== Audit ===== */

    @CreatedDate
    @Column(name = "upload_date", updatable = false)
    private Instant uploadDate;

    @Column(name = "due_date")
    private Instant dueDate;
}
