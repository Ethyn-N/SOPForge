package com.securedoc.securedoc_ai.repository;

import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.CompanyMember;
import com.securedoc.securedoc_ai.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {

    List<CompanyMember> findByUserOrderByCompanyNameAsc(User user);

    Optional<CompanyMember> findByCompanyAndUser(Company company, User user);

    boolean existsByCompanyAndUser(Company company, User user);
}
