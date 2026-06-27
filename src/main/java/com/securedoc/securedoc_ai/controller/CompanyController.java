package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.CompanyCreateRequest;
import com.securedoc.securedoc_ai.dto.CompanyMemberCreateRequest;
import com.securedoc.securedoc_ai.dto.CompanyMemberResponse;
import com.securedoc.securedoc_ai.dto.CompanyMemberUpdateRequest;
import com.securedoc.securedoc_ai.dto.CompanyResponse;
import com.securedoc.securedoc_ai.model.CompanyMember;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> deleteCompany(
            @PathVariable Long companyId,
            @AuthenticationPrincipal User user
    ) {
        companyService.deleteCompany(companyId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{companyId}/members")
    public List<CompanyMemberResponse> getCompanyMembers(
            @PathVariable Long companyId,
            @AuthenticationPrincipal User user
    ) {
        return companyService.getCompanyMembers(companyId, user)
                .stream()
                .map(CompanyMemberResponse::new)
                .toList();
    }

    @PostMapping("/{companyId}/members")
    public CompanyMemberResponse addCompanyMember(
            @PathVariable Long companyId,
            @RequestBody CompanyMemberCreateRequest request,
            @AuthenticationPrincipal User user
    ) {
        CompanyMember companyMember = companyService.addCompanyMember(companyId, request, user);
        return new CompanyMemberResponse(companyMember);
    }

    @PatchMapping("/{companyId}/members/{memberId}")
    public CompanyMemberResponse updateCompanyMember(
            @PathVariable Long companyId,
            @PathVariable Long memberId,
            @RequestBody CompanyMemberUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        CompanyMember companyMember = companyService.updateCompanyMember(companyId, memberId, request, user);
        return new CompanyMemberResponse(companyMember);
    }

    @DeleteMapping("/{companyId}/members/{memberId}")
    public ResponseEntity<Void> removeCompanyMember(
            @PathVariable Long companyId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal User user
    ) {
        companyService.removeCompanyMember(companyId, memberId, user);
        return ResponseEntity.noContent().build();
    }
}
