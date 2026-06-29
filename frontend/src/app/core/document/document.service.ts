import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Document, DocumentText } from './document.models';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  constructor(private readonly http: HttpClient) {}

  getCompanyDocuments(companyId: number): Observable<Document[]> {
    return this.http.get<Document[]>(`${API_BASE_URL}/companies/${companyId}/documents`);
  }

  uploadCompanyDocument(companyId: number, file: File): Observable<Document> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<Document>(`${API_BASE_URL}/companies/${companyId}/documents`, formData);
  }

  getCompanyDocumentText(companyId: number, documentId: number): Observable<DocumentText> {
    return this.http.get<DocumentText>(
      `${API_BASE_URL}/companies/${companyId}/documents/${documentId}/text`
    );
  }

  downloadCompanyDocument(companyId: number, documentId: number): Observable<Blob> {
    return this.http.get(
      `${API_BASE_URL}/companies/${companyId}/documents/${documentId}/download`,
      { responseType: 'blob' }
    );
  }

  downloadCompanyDocuments(companyId: number, documentIds: number[]): Observable<Blob> {
    return this.http.post(
      `${API_BASE_URL}/companies/${companyId}/documents/bulk-download`,
      { documentIds },
      { responseType: 'blob' }
    );
  }

  deleteCompanyDocument(companyId: number, documentId: number): Observable<void> {
    return this.http.delete<void>(
      `${API_BASE_URL}/companies/${companyId}/documents/${documentId}`
    );
  }

  deleteCompanyDocuments(companyId: number, documentIds: number[]): Observable<void> {
    return this.http.post<void>(
      `${API_BASE_URL}/companies/${companyId}/documents/bulk-delete`,
      { documentIds }
    );
  }
}
