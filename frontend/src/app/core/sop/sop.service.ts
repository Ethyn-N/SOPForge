import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { RelevancePreview, Sop, SopGenerateRequest } from './sop.models';

@Injectable({
  providedIn: 'root'
})
export class SopService {
  constructor(private readonly http: HttpClient) {}

  getCompanySops(companyId: number): Observable<Sop[]> {
    return this.http.get<Sop[]>(`${API_BASE_URL}/companies/${companyId}/sops`);
  }

  previewCompanyRelevance(
    companyId: number,
    request: SopGenerateRequest
  ): Observable<RelevancePreview> {
    return this.http.post<RelevancePreview>(
      `${API_BASE_URL}/companies/${companyId}/sops/relevance-preview`,
      request
    );
  }

  generateCompanySop(companyId: number, request: SopGenerateRequest): Observable<Sop> {
    return this.http.post<Sop>(
      `${API_BASE_URL}/companies/${companyId}/sops/generate`,
      request
    );
  }
}
