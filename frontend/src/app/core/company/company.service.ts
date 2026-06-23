import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Company, CompanyCreateRequest } from './company.models';

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
}
