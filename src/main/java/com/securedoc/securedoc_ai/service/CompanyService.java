package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.CompanyCreateRequest;
import com.securedoc.securedoc_ai.dto.CompanyMemberCreateRequest;
import com.securedoc.securedoc_ai.dto.CompanyMemberUpdateRequest;
import com.securedoc.securedoc_ai.exception.BadRequestException;
import com.securedoc.securedoc_ai.exception.ForbiddenException;
import com.securedoc.securedoc_ai.exception.NotFoundException;
import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.CompanyMember;
import com.securedoc.securedoc_ai.model.CompanyRole;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.CompanyMemberRepository;
import com.securedoc.securedoc_ai.repository.CompanyRepository;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import com.securedoc.securedoc_ai.repository.UserRepository;
import com.securedoc.securedoc_ai.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    public List<CompanyMember> getCompaniesForUser(User user) {
        return companyMemberRepository.findByUserOrderByCompanyNameAsc(user);
    }

    public List<CompanyMember> getCompanyMembers(Long companyId, User user) {
        Company company = getCompanyForUser(companyId, user);
        return companyMemberRepository.findByCompanyOrderByUserEmailAsc(company);
    }

    @Transactional
    public CompanyMember createCompany(CompanyCreateRequest request, User user) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Company name must not be blank.");
        }

        Company company = companyRepository.save(new Company(request.name().trim()));
        return companyMemberRepository.save(new CompanyMember(company, user, CompanyRole.OWNER));
    }

    @Transactional
    public void deleteCompany(Long companyId, User user) {
        Company company = requireCompanyRole(companyId, user, CompanyRole.OWNER);
        List<String> storedFileNames = documentRepository.findByCompany(company).stream()
                .map(document -> document.getStoredFileName())
                .toList();

        companyRepository.delete(company);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storedFileNames.forEach(fileStorageService::delete);
            }
        });
    }

    public Company getCompanyForUser(Long companyId, User user) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(
                        "company with id " + companyId + " does not exist"
                ));

        if (!companyMemberRepository.existsByCompanyAndUser(company, user)) {
            throw new NotFoundException("company with id " + companyId + " does not exist");
        }

        return company;
    }

    public Company requireCompanyRole(Long companyId, User user, CompanyRole... allowedRoles) {
        Company company = getCompanyForUser(companyId, user);
        CompanyMember companyMember = companyMemberRepository.findByCompanyAndUser(company, user)
                .orElseThrow(() -> new NotFoundException("company with id " + companyId + " does not exist"));

        for (CompanyRole allowedRole : allowedRoles) {
            if (companyMember.getRole() == allowedRole) {
                return company;
            }
        }

        throw new ForbiddenException("You do not have permission to perform this company action.");
    }

    @Transactional
    public CompanyMember addCompanyMember(Long companyId, CompanyMemberCreateRequest request, User user) {
        Company company = requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);

        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Member email must not be blank.");
        }

        CompanyRole role = request.role() == null ? CompanyRole.MEMBER : request.role();
        User memberUser = userRepository.findByEmail(request.email().trim())
                .orElseThrow(() -> new NotFoundException(
                        "user with email " + request.email().trim() + " does not exist"
                ));

        if (companyMemberRepository.existsByCompanyAndUser(company, memberUser)) {
            throw new BadRequestException("User is already a member of this company.");
        }

        return companyMemberRepository.save(new CompanyMember(company, memberUser, role));
    }

    @Transactional
    public CompanyMember updateCompanyMember(
            Long companyId,
            Long memberId,
            CompanyMemberUpdateRequest request,
            User user
    ) {
        Company company = requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);

        if (request == null || request.role() == null) {
            throw new BadRequestException("Company member role must not be blank.");
        }

        CompanyMember companyMember = getMember(company, memberId);
        if (companyMember.getRole() == CompanyRole.OWNER && request.role() != CompanyRole.OWNER) {
            requireAnotherOwner(company);
        }

        companyMember.setRole(request.role());
        return companyMemberRepository.save(companyMember);
    }

    @Transactional
    public void removeCompanyMember(Long companyId, Long memberId, User user) {
        Company company = requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);
        CompanyMember companyMember = getMember(company, memberId);

        if (companyMember.getRole() == CompanyRole.OWNER) {
            requireAnotherOwner(company);
        }

        companyMemberRepository.delete(companyMember);
    }

    private CompanyMember getMember(Company company, Long memberId) {
        return companyMemberRepository.findByIdAndCompany(memberId, company)
                .orElseThrow(() -> new NotFoundException(
                        "company member with id " + memberId + " does not exist"
                ));
    }

    private void requireAnotherOwner(Company company) {
        if (companyMemberRepository.countByCompanyAndRole(company, CompanyRole.OWNER) <= 1) {
            throw new BadRequestException("Company must have at least one owner.");
        }
    }
}
