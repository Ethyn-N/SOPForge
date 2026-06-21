package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.CompanyMember;
import com.securedoc.securedoc_ai.model.CompanyRole;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
        CompanyRole role,
        LocalDateTime createdAt
) {

    public CompanyResponse(CompanyMember companyMember) {
        this(
                company(companyMember).getId(),
                company(companyMember).getName(),
                companyMember.getRole(),
                company(companyMember).getCreatedAt()
        );
    }

    public CompanyResponse(Company company, CompanyRole role) {
        this(company.getId(), company.getName(), role, company.getCreatedAt());
    }

    private static Company company(CompanyMember companyMember) {
        return companyMember.getCompany();
    }
}
