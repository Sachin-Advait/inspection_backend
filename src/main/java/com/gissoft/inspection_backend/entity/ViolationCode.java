package com.gissoft.inspection_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "violation_code")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ViolationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 400)
    private String description;

    /** MINOR | MAJOR | CRITICAL */
    @Column(nullable = false, length = 20)
    private String severity;

    /** WARNING | FINE | CLOSURE */
    @Column(name = "default_action", nullable = false, length = 30)
    private String defaultAction;

    @Column(name = "legal_ref", length = 120)
    private String legalRef;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "dg", length = 20)
    private String dg;

    @Column(name = "category")
    private String category;
}
