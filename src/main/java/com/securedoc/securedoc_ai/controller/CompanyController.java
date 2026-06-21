package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.CompanyCreateRequest;
import com.securedoc.securedoc_ai.dto.CompanyResponse;
import com.securedoc.securedoc_ai.model.CompanyMember;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public List<CompanyResponse> getCompanies(@AuthenticationPrincipal User user) {
        return companyService.getCompaniesForUser(user)
                .stream()
                .map(CompanyResponse::new)
                .toList();
    }

    @PostMapping
    public CompanyResponse createCompany(
            @RequestBody CompanyCreateRequest request,
            @AuthenticationPrincipal User user
    ) {
        CompanyMember companyMember = companyService.createCompany(request, user);
        return new CompanyResponse(companyMember);
    }
}
