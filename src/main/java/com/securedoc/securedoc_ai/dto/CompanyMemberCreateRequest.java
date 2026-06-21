package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.CompanyRole;

public record CompanyMemberCreateRequest(
        String email,
        CompanyRole role
) {
}
