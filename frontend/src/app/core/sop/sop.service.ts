import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  RelevancePreview,
  Sop,
  SopGenerateRequest,
  SopGenerationJob,
  SopUpdateRequest,
  SopVersion
} from './sop.models';

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

  createCompanyGenerationJob(
    companyId: number,
    request: SopGenerateRequest
  ): Observable<SopGenerationJob> {
    return this.http.post<SopGenerationJob>(
      `${API_BASE_URL}/companies/${companyId}/sop-generation-jobs`,
      request
    );
  }

  getCompanyGenerationJob(companyId: number, jobId: number): Observable<SopGenerationJob> {
    return this.http.get<SopGenerationJob>(
      `${API_BASE_URL}/companies/${companyId}/sop-generation-jobs/${jobId}`
    );
  }

  getCompanyGenerationJobs(companyId: number): Observable<SopGenerationJob[]> {
    return this.http.get<SopGenerationJob[]>(
      `${API_BASE_URL}/companies/${companyId}/sop-generation-jobs`
    );
  }

  updateCompanySop(companyId: number, sopId: number, request: SopUpdateRequest): Observable<Sop> {
    return this.http.patch<Sop>(`${API_BASE_URL}/companies/${companyId}/sops/${sopId}`, request);
  }

  submitCompanySop(companyId: number, sopId: number): Observable<Sop> {
    return this.sopAction(companyId, sopId, 'submit');
  }

  approveCompanySop(companyId: number, sopId: number): Observable<Sop> {
    return this.sopAction(companyId, sopId, 'approve');
  }

  rejectCompanySop(companyId: number, sopId: number): Observable<Sop> {
    return this.sopAction(companyId, sopId, 'reject');
  }

  archiveCompanySop(companyId: number, sopId: number): Observable<Sop> {
    return this.sopAction(companyId, sopId, 'archive');
  }

  getCompanySopVersions(companyId: number, sopId: number): Observable<SopVersion[]> {
    return this.http.get<SopVersion[]>(
      `${API_BASE_URL}/companies/${companyId}/sops/${sopId}/versions`
    );
  }

  private sopAction(companyId: number, sopId: number, action: string): Observable<Sop> {
    return this.http.post<Sop>(
      `${API_BASE_URL}/companies/${companyId}/sops/${sopId}/${action}`,
      {}
    );
  }
}
