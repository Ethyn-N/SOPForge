package com.securedoc.securedoc_ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    @Column(nullable = false)
    private String password;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Setter
    @Column(nullable = false)
    private Boolean enabled;

    @Setter
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public User(String email, String password) {
        this.email = email;
        this.password = password;
        this.role = UserRole.USER;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }
}
