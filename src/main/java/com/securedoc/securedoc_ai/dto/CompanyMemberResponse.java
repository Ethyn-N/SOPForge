package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.CompanyMember;
import com.securedoc.securedoc_ai.model.CompanyRole;

import java.time.LocalDateTime;

public record CompanyMemberResponse(
        Long id,
        Long companyId,
        String companyName,
        Long userId,
        String name,
        String email,
        CompanyRole role,
        LocalDateTime createdAt
) {

    public CompanyMemberResponse(CompanyMember companyMember) {
        this(
                companyMember.getId(),
                companyMember.getCompany().getId(),
                companyMember.getCompany().getName(),
                companyMember.getUser().getId(),
                companyMember.getUser().getName(),
                companyMember.getUser().getEmail(),
                companyMember.getRole(),
                companyMember.getCreatedAt()
        );
    }
}
