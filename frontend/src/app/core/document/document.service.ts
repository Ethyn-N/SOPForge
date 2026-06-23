import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Document } from './document.models';

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
}
