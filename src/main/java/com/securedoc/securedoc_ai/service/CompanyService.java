package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.CompanyCreateRequest;
import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.CompanyMember;
import com.securedoc.securedoc_ai.model.CompanyRole;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.CompanyMemberRepository;
import com.securedoc.securedoc_ai.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;

    public List<CompanyMember> getCompaniesForUser(User user) {
        return companyMemberRepository.findByUserOrderByCompanyNameAsc(user);
    }

    @Transactional
    public CompanyMember createCompany(CompanyCreateRequest request, User user) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalStateException("Company name must not be blank.");
        }

        Company company = companyRepository.save(new Company(request.name().trim()));
        return companyMemberRepository.save(new CompanyMember(company, user, CompanyRole.OWNER));
    }

    public Company getCompanyForUser(Long companyId, User user) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException(
                        "company with id " + companyId + " does not exist"
                ));

        if (!companyMemberRepository.existsByCompanyAndUser(company, user)) {
            throw new IllegalStateException("company with id " + companyId + " does not exist");
        }

        return company;
    }
}
