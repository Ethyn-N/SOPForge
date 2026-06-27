import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Company, CompanyCreateRequest, CompanyMember, CompanyRole } from './company.models';

@Injectable({
  providedIn: 'root'
})
export class CompanyService {
  constructor(private readonly http: HttpClient) {}

  getCompanies(): Observable<Company[]> {
    return this.http.get<Company[]>(`${API_BASE_URL}/companies`);
  }

  createCompany(request: CompanyCreateRequest): Observable<Company> {
    return this.http.post<Company>(`${API_BASE_URL}/companies`, request);
  }

  deleteCompany(companyId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/companies/${companyId}`);
  }

  getCompanyMembers(companyId: number): Observable<CompanyMember[]> {
    return this.http.get<CompanyMember[]>(`${API_BASE_URL}/companies/${companyId}/members`);
  }

  addCompanyMember(companyId: number, email: string, role: CompanyRole): Observable<CompanyMember> {
    return this.http.post<CompanyMember>(`${API_BASE_URL}/companies/${companyId}/members`, { email, role });
  }

  updateCompanyMember(companyId: number, memberId: number, role: CompanyRole): Observable<CompanyMember> {
    return this.http.patch<CompanyMember>(`${API_BASE_URL}/companies/${companyId}/members/${memberId}`, { role });
  }

  removeCompanyMember(companyId: number, memberId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/companies/${companyId}/members/${memberId}`);
  }
}
