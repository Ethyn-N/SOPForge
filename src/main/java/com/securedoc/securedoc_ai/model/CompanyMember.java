package com.securedoc.securedoc_ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(
        name = "company_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "user_id"})
)
public class CompanyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyRole role;

    @Setter
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public CompanyMember(Company company, User user, CompanyRole role) {
        this.company = company;
        this.user = user;
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }
}
