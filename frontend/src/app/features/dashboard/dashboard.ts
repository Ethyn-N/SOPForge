import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../core/auth/auth.service';
import { Company } from '../../core/company/company.models';
import { CompanyService } from '../../core/company/company.service';
import { Document } from '../../core/document/document.models';
import { DocumentService } from '../../core/document/document.service';

@Component({
  selector: 'app-dashboard',
  imports: [ButtonModule, FormsModule, ProgressSpinnerModule, TagModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly companyService = inject(CompanyService);
  private readonly documentService = inject(DocumentService);

  readonly user = this.authService.currentUser;
  readonly companies = signal<Company[]>([]);
  readonly documents = signal<Document[]>([]);
  readonly selectedCompanyId = signal<number | null>(null);
  readonly companyName = signal('');
  readonly selectedFileName = signal('');
  readonly isLoadingCompanies = signal(false);
  readonly isCreatingCompany = signal(false);
  readonly isLoadingDocuments = signal(false);
  readonly isUploadingDocument = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly selectedCompany = computed(() => {
    const id = this.selectedCompanyId();
    return this.companies().find((company) => company.id === id) ?? null;
  });

  private selectedFile: File | null = null;
  private readonly documentCache = new Map<number, Document[]>();

  ngOnInit(): void {
    this.loadCompanies();
  }

  logout(): void {
    this.authService.logout();
  }

  loadCompanies(): void {
    this.isLoadingCompanies.set(true);
    this.clearMessages();

    this.companyService.getCompanies().subscribe({
      next: (companies) => {
        this.companies.set(companies);

        const selectedStillExists = companies.some(
          (company) => company.id === this.selectedCompanyId()
        );
        const nextCompanyId = selectedStillExists
          ? this.selectedCompanyId()
          : companies[0]?.id ?? null;

        this.selectedCompanyId.set(nextCompanyId);

        if (nextCompanyId) {
          this.showCachedDocuments(nextCompanyId);
          this.loadDocuments(nextCompanyId);
        } else {
          this.documents.set([]);
        }
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error)),
      complete: () => this.isLoadingCompanies.set(false)
    });
  }

  createCompany(): void {
    const name = this.companyName().trim();

    if (!name) {
      this.errorMessage.set('Enter a company name.');
      return;
    }

    this.isCreatingCompany.set(true);
    this.clearMessages();

    this.companyService.createCompany({ name }).subscribe({
      next: (company) => {
        this.companyName.set('');
        this.companies.update((companies) => [...companies, company]);
        this.selectedCompanyId.set(company.id);
        this.documentCache.set(company.id, []);
        this.documents.set([]);
        this.successMessage.set(`${company.name} was created.`);
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error)),
      complete: () => this.isCreatingCompany.set(false)
    });
  }

  selectCompany(companyId: number): void {
    if (companyId === this.selectedCompanyId()) {
      return;
    }

    this.selectedCompanyId.set(companyId);
    this.showCachedDocuments(companyId);
    this.loadDocuments(companyId);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    this.selectedFile = file;
    this.selectedFileName.set(file?.name ?? '');
  }

  uploadDocument(): void {
    const companyId = this.selectedCompanyId();

    if (!companyId) {
      this.errorMessage.set('Create or select a company before uploading.');
      return;
    }

    if (!this.selectedFile) {
      this.errorMessage.set('Choose a document to upload.');
      return;
    }

    this.isUploadingDocument.set(true);
    this.clearMessages();

    this.documentService.uploadCompanyDocument(companyId, this.selectedFile).subscribe({
      next: (document) => {
        this.documents.update((documents) => [document, ...documents]);
        this.documentCache.set(companyId, this.documents());
        this.selectedFile = null;
        this.selectedFileName.set('');
        this.successMessage.set(`${document.originalFileName} uploaded.`);
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error)),
      complete: () => this.isUploadingDocument.set(false)
    });
  }

  refreshDocuments(): void {
    const companyId = this.selectedCompanyId();

    if (companyId) {
      this.loadDocuments(companyId);
    }
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }

    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  formatDate(value: string | null): string {
    if (!value) {
      return 'Not yet';
    }

    return new Intl.DateTimeFormat('en', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value));
  }

  statusSeverity(status: Document['extractionStatus']): 'success' | 'warn' | 'danger' {
    if (status === 'SUCCESS') {
      return 'success';
    }

    if (status === 'FAILED') {
      return 'danger';
    }

    return 'warn';
  }

  private loadDocuments(companyId: number): void {
    this.isLoadingDocuments.set(true);
    this.clearMessages();

    this.documentService.getCompanyDocuments(companyId).subscribe({
      next: (documents) => {
        this.documentCache.set(companyId, documents);

        if (this.selectedCompanyId() === companyId) {
          this.documents.set(documents);
        }
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error)),
      complete: () => this.isLoadingDocuments.set(false)
    });
  }

  private showCachedDocuments(companyId: number): void {
    this.documents.set(this.documentCache.get(companyId) ?? []);
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  private messageFromError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.error?.message) {
      return error.error.message;
    }

    return 'Request failed. Check that the backend is running and try again.';
  }
}
