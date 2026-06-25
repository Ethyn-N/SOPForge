import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  ElementRef,
  effect,
  inject,
  OnDestroy,
  OnInit,
  signal,
  ViewChild
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, of, Subscription, switchMap, timer } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../core/auth/auth.service';
import { Company } from '../../core/company/company.models';
import { CompanyService } from '../../core/company/company.service';
import { Document } from '../../core/document/document.models';
import { DocumentService } from '../../core/document/document.service';
import { RelevancePreview, Sop } from '../../core/sop/sop.models';
import { SopService } from '../../core/sop/sop.service';

interface PendingSopGeneration {
  companyId: number;
  title: string;
  sourceDocumentIds: number[];
  startedAt: string;
}

const PENDING_GENERATION_KEY = 'sopforge_pending_generation';
const GENERATION_TIMEOUT_MS = 15 * 60 * 1000;

@Component({
  selector: 'app-dashboard',
  imports: [ButtonModule, FormsModule, ProgressSpinnerModule, TagModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit, OnDestroy {
  @ViewChild('docxPreview') private docxPreview?: ElementRef<HTMLElement>;

  private readonly authService = inject(AuthService);
  private readonly companyService = inject(CompanyService);
  private readonly documentService = inject(DocumentService);
  private readonly sopService = inject(SopService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly user = this.authService.currentUser;
  readonly companies = signal<Company[]>([]);
  readonly documents = signal<Document[]>([]);
  readonly sops = signal<Sop[]>([]);
  readonly selectedCompanyId = signal<number | null>(null);
  readonly activeView = signal<'documents' | 'sops'>('documents');
  readonly sopView = signal<'library' | 'generate'>('library');
  readonly sopFilter = signal<'ALL' | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED'>('ALL');
  readonly showCompanyCreator = signal(false);
  readonly selectedDocumentIds = signal<Set<number>>(new Set());
  readonly companyName = signal('');
  readonly selectedFileName = signal('');
  readonly sopTitle = signal('');
  readonly sopInstructions = signal('');
  readonly documentPreview = signal<{
    document: Document;
    objectUrl: string | null;
    safeUrl: SafeResourceUrl | null;
    isDocx: boolean;
  } | null>(null);
  readonly selectedSop = signal<Sop | null>(null);
  readonly pendingGeneration = signal<PendingSopGeneration | null>(this.loadPendingGeneration());
  readonly deleteCandidate = signal<Document | null>(null);
  readonly relevancePreview = signal<RelevancePreview | null>(null);
  readonly generatedSop = signal<Sop | null>(null);
  readonly isLoadingCompanies = signal(false);
  readonly isCreatingCompany = signal(false);
  readonly isLoadingDocuments = signal(false);
  readonly isUploadingDocument = signal(false);
  readonly isLoadingDocumentPreview = signal(false);
  readonly isDeletingDocument = signal(false);
  readonly isLoadingSops = signal(false);
  readonly isPreviewingRelevance = signal(false);
  readonly isGeneratingSop = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  private noticeDismissTimer?: ReturnType<typeof setTimeout>;
  private readonly noticeAutoDismissEffect = effect(() => {
    const error = this.errorMessage();
    const success = this.successMessage();

    if (this.noticeDismissTimer) {
      clearTimeout(this.noticeDismissTimer);
      this.noticeDismissTimer = undefined;
    }

    if (!error && !success) {
      return;
    }

    this.noticeDismissTimer = setTimeout(() => {
      this.errorMessage.set(null);
      this.successMessage.set(null);
    }, error ? 8000 : 5000);
  });

  readonly selectedCompany = computed(() => {
    const id = this.selectedCompanyId();
    return this.companies().find((company) => company.id === id) ?? null;
  });

  readonly canManageSelectedCompany = computed(() => {
    const role = this.selectedCompany()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  readonly selectedDocuments = computed(() => {
    const selectedIds = this.selectedDocumentIds();
    return this.documents().filter((document) => selectedIds.has(document.id));
  });

  readonly filteredSops = computed(() => {
    const filter = this.sopFilter();
    return filter === 'ALL' ? this.sops() : this.sops().filter((sop) => sop.status === filter);
  });

  readonly generationInProgress = computed(
    () => this.isGeneratingSop() || this.pendingGeneration() !== null
  );

  readonly generationIsForSelectedCompany = computed(
    () => this.pendingGeneration()?.companyId === this.selectedCompanyId()
  );

  private selectedFile: File | null = null;
  private readonly documentCache = new Map<number, Document[]>();
  private readonly sopCache = new Map<number, Sop[]>();
  private generationPollingSubscription?: Subscription;

  ngOnInit(): void {
    this.loadCompanies();
    this.startPendingGenerationPolling();
  }

  ngOnDestroy(): void {
    this.generationPollingSubscription?.unsubscribe();
    this.noticeAutoDismissEffect.destroy();

    if (this.noticeDismissTimer) {
      clearTimeout(this.noticeDismissTimer);
    }

    this.closeDocumentPreview();
  }

  logout(): void {
    this.clearPendingGeneration();
    this.authService.logout();
  }

  loadCompanies(): void {
    this.isLoadingCompanies.set(true);
    this.clearMessages();

    this.companyService
      .getCompanies()
      .pipe(finalize(() => this.isLoadingCompanies.set(false)))
      .subscribe({
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
            this.showCachedCompanyData(nextCompanyId);
            this.loadDocuments(nextCompanyId);
            this.loadSops(nextCompanyId);
          } else {
            this.documents.set([]);
            this.sops.set([]);
          }
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
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

    this.companyService
      .createCompany({ name })
      .pipe(finalize(() => this.isCreatingCompany.set(false)))
      .subscribe({
        next: (company) => {
          this.companyName.set('');
          this.companies.update((companies) => [...companies, company]);
          this.selectedCompanyId.set(company.id);
          this.documentCache.set(company.id, []);
          this.sopCache.set(company.id, []);
          this.resetCompanyWorkspace();
          this.showCompanyCreator.set(false);
          this.successMessage.set(`${company.name} was created.`);
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  selectCompany(companyId: number): void {
    if (companyId === this.selectedCompanyId()) {
      return;
    }

    this.selectedCompanyId.set(companyId);
    this.resetTransientState();
    this.showCachedCompanyData(companyId);
    this.loadDocuments(companyId);
    this.loadSops(companyId);
  }

  selectCompanyFromControl(value: string): void {
    const companyId = Number(value);

    if (Number.isFinite(companyId)) {
      this.selectCompany(companyId);
    }
  }

  showDocuments(): void {
    this.activeView.set('documents');
  }

  showSops(view: 'library' | 'generate' = 'library'): void {
    this.activeView.set('sops');
    this.sopView.set(view);
  }

  setSopFilter(filter: 'ALL' | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED'): void {
    this.sopFilter.set(filter);
  }

  toggleCompanyCreator(): void {
    this.showCompanyCreator.update((visible) => !visible);
  }

  openPendingGeneration(): void {
    const generation = this.pendingGeneration();

    if (!generation) {
      return;
    }

    this.selectCompany(generation.companyId);
    this.showSops('library');
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    this.selectedFile = file;
    this.selectedFileName.set(file?.name ?? '');
  }

  uploadDocument(): void {
    const companyId = this.selectedCompanyId();

    if (!companyId || !this.canManageSelectedCompany()) {
      this.errorMessage.set('You need company owner or admin access to upload documents.');
      return;
    }

    if (!this.selectedFile) {
      this.errorMessage.set('Choose a document to upload.');
      return;
    }

    this.isUploadingDocument.set(true);
    this.clearMessages();

    this.documentService
      .uploadCompanyDocument(companyId, this.selectedFile)
      .pipe(finalize(() => this.isUploadingDocument.set(false)))
      .subscribe({
        next: (document) => {
          this.documents.update((documents) => [document, ...documents]);
          this.documentCache.set(companyId, this.documents());
          this.selectedFile = null;
          this.selectedFileName.set('');
          this.successMessage.set(`${document.originalFileName} uploaded.`);
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  refreshDocuments(): void {
    const companyId = this.selectedCompanyId();

    if (companyId) {
      this.loadDocuments(companyId);
    }
  }

  toggleDocumentSelection(document: Document): void {
    if (document.extractionStatus !== 'SUCCESS') {
      return;
    }

    const nextSelection = new Set(this.selectedDocumentIds());

    if (nextSelection.has(document.id)) {
      nextSelection.delete(document.id);
    } else {
      nextSelection.add(document.id);
    }

    this.selectedDocumentIds.set(nextSelection);
    this.relevancePreview.set(null);
  }

  isDocumentSelected(documentId: number): boolean {
    return this.selectedDocumentIds().has(documentId);
  }

  previewDocument(document: Document): void {
    const companyId = this.selectedCompanyId();

    if (!companyId) {
      return;
    }

    this.closeDocumentPreview();
    this.isLoadingDocumentPreview.set(true);
    this.clearMessages();

    this.documentService
      .downloadCompanyDocument(companyId, document.id)
      .pipe(finalize(() => this.isLoadingDocumentPreview.set(false)))
      .subscribe({
        next: (file) => {
          const isDocx =
            document.fileType ===
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

          if (isDocx) {
            this.documentPreview.set({
              document,
              objectUrl: null,
              safeUrl: null,
              isDocx: true
            });
            window.setTimeout(() => this.renderDocx(file), 0);
            return;
          }

          const objectUrl = URL.createObjectURL(file);
          this.documentPreview.set({
            document,
            objectUrl,
            safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl),
            isDocx: false
          });
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  closeDocumentPreview(): void {
    const objectUrl = this.documentPreview()?.objectUrl;

    if (objectUrl) {
      URL.revokeObjectURL(objectUrl);
    }

    this.documentPreview.set(null);
  }

  openSop(sop: Sop): void {
    this.selectedSop.set(sop);
  }

  closeSop(): void {
    this.selectedSop.set(null);
  }

  downloadDocument(document: Document): void {
    const companyId = this.selectedCompanyId();

    if (!companyId) {
      return;
    }

    this.clearMessages();
    this.documentService.downloadCompanyDocument(companyId, document.id).subscribe({
      next: (file) => {
        const downloadUrl = URL.createObjectURL(file);
        const link = window.document.createElement('a');
        link.href = downloadUrl;
        link.download = document.originalFileName;
        window.document.body.appendChild(link);
        link.click();
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 0);
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error))
    });
  }

  requestDocumentDelete(document: Document): void {
    this.deleteCandidate.set(document);
  }

  cancelDocumentDelete(): void {
    this.deleteCandidate.set(null);
  }

  confirmDocumentDelete(): void {
    const companyId = this.selectedCompanyId();
    const document = this.deleteCandidate();

    if (!companyId || !document) {
      return;
    }

    this.isDeletingDocument.set(true);
    this.clearMessages();

    this.documentService
      .deleteCompanyDocument(companyId, document.id)
      .pipe(finalize(() => this.isDeletingDocument.set(false)))
      .subscribe({
        next: () => {
          this.documents.update((documents) =>
            documents.filter((currentDocument) => currentDocument.id !== document.id)
          );
          this.documentCache.set(companyId, this.documents());

          const nextSelection = new Set(this.selectedDocumentIds());
          nextSelection.delete(document.id);
          this.selectedDocumentIds.set(nextSelection);
          this.deleteCandidate.set(null);

          if (this.documentPreview()?.document.id === document.id) {
            this.closeDocumentPreview();
          }

          this.successMessage.set(`${document.originalFileName} was deleted.`);
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  previewRelevance(): void {
    const request = this.sopRequest();
    const companyId = this.selectedCompanyId();

    if (!companyId || !request) {
      return;
    }

    this.isPreviewingRelevance.set(true);
    this.relevancePreview.set(null);
    this.clearMessages();

    this.sopService
      .previewCompanyRelevance(companyId, request)
      .pipe(finalize(() => this.isPreviewingRelevance.set(false)))
      .subscribe({
        next: (preview) => this.relevancePreview.set(preview),
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  generateSop(): void {
    const request = this.sopRequest();
    const companyId = this.selectedCompanyId();

    if (!companyId || !request) {
      return;
    }

    this.isGeneratingSop.set(true);
    this.generatedSop.set(null);
    this.clearMessages();
    this.trackPendingGeneration({
      companyId,
      title: request.title,
      sourceDocumentIds: request.sourceDocumentIds,
      startedAt: new Date().toISOString()
    });

    this.sopService
      .generateCompanySop(companyId, request)
      .pipe(finalize(() => this.isGeneratingSop.set(false)))
      .subscribe({
        next: (sop) => {
          this.acceptGeneratedSop(sop, companyId);
          this.sopView.set('library');
          this.successMessage.set(`${sop.title} was generated as a draft.`);
          this.clearPendingGeneration();
        },
        error: (error) => {
          this.clearPendingGeneration();
          this.errorMessage.set(this.messageFromError(error));
        }
      });
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

  sopStatusSeverity(status: Sop['status']): 'success' | 'warn' | 'danger' | 'info' | 'secondary' {
    if (status === 'APPROVED') {
      return 'success';
    }

    if (status === 'PENDING_REVIEW') {
      return 'warn';
    }

    if (status === 'REJECTED') {
      return 'danger';
    }

    if (status === 'ARCHIVED') {
      return 'secondary';
    }

    return 'info';
  }

  private loadDocuments(companyId: number): void {
    this.isLoadingDocuments.set(true);
    this.clearMessages();

    this.documentService
      .getCompanyDocuments(companyId)
      .pipe(finalize(() => this.isLoadingDocuments.set(false)))
      .subscribe({
        next: (documents) => {
          this.documentCache.set(companyId, documents);

          if (this.selectedCompanyId() === companyId) {
            this.documents.set(documents);
            const availableIds = new Set(documents.map((document) => document.id));
            this.selectedDocumentIds.update(
              (selectedIds) => new Set([...selectedIds].filter((id) => availableIds.has(id)))
            );
          }
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  private loadSops(companyId: number): void {
    this.isLoadingSops.set(true);

    this.sopService
      .getCompanySops(companyId)
      .pipe(finalize(() => this.isLoadingSops.set(false)))
      .subscribe({
        next: (sops) => {
          this.sopCache.set(companyId, sops);

          if (this.selectedCompanyId() === companyId) {
            this.sops.set(sops);
          }
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  private sopRequest() {
    if (!this.canManageSelectedCompany()) {
      this.errorMessage.set('You need company owner or admin access to generate SOPs.');
      return null;
    }

    const title = this.sopTitle().trim();
    const sourceDocumentIds = [...this.selectedDocumentIds()];

    if (!title) {
      this.errorMessage.set('Enter a title for the SOP.');
      return null;
    }

    if (!sourceDocumentIds.length) {
      this.errorMessage.set('Select at least one successfully extracted document.');
      return null;
    }

    return {
      title,
      sourceDocumentIds,
      instructions: this.sopInstructions().trim()
    };
  }

  private showCachedCompanyData(companyId: number): void {
    this.documents.set(this.documentCache.get(companyId) ?? []);
    this.sops.set(this.sopCache.get(companyId) ?? []);
  }

  private resetCompanyWorkspace(): void {
    this.documents.set([]);
    this.sops.set([]);
    this.resetTransientState();
  }

  private resetTransientState(): void {
    this.selectedDocumentIds.set(new Set());
    this.closeDocumentPreview();
    this.selectedSop.set(null);
    this.deleteCandidate.set(null);
    this.relevancePreview.set(null);
    this.generatedSop.set(null);
    this.sopTitle.set('');
    this.sopInstructions.set('');
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

  private trackPendingGeneration(generation: PendingSopGeneration): void {
    this.pendingGeneration.set(generation);
    localStorage.setItem(PENDING_GENERATION_KEY, JSON.stringify(generation));
    this.startPendingGenerationPolling();
  }

  private loadPendingGeneration(): PendingSopGeneration | null {
    const storedGeneration = localStorage.getItem(PENDING_GENERATION_KEY);

    if (!storedGeneration) {
      return null;
    }

    try {
      return JSON.parse(storedGeneration) as PendingSopGeneration;
    } catch {
      localStorage.removeItem(PENDING_GENERATION_KEY);
      return null;
    }
  }

  private startPendingGenerationPolling(): void {
    this.generationPollingSubscription?.unsubscribe();

    if (!this.pendingGeneration()) {
      return;
    }

    this.generationPollingSubscription = timer(0, 3000)
      .pipe(
        switchMap(() => {
          const generation = this.pendingGeneration();

          if (!generation) {
            return of([]);
          }

          if (Date.now() - new Date(generation.startedAt).getTime() > GENERATION_TIMEOUT_MS) {
            this.clearPendingGeneration();
            this.errorMessage.set('SOP generation did not finish within 15 minutes.');
            return of([]);
          }

          return this.sopService
            .getCompanySops(generation.companyId)
            .pipe(catchError(() => of([])));
        })
      )
      .subscribe((sops) => {
        const generation = this.pendingGeneration();

        if (!generation) {
          return;
        }

        const generatedSop = sops.find((sop) => this.matchesPendingGeneration(sop, generation));

        if (!generatedSop) {
          return;
        }

        this.sopCache.set(generation.companyId, sops);

        if (this.selectedCompanyId() === generation.companyId) {
          this.sops.set(sops);
        }

        this.generatedSop.set(generatedSop);
        this.successMessage.set(`${generatedSop.title} finished generating.`);
        this.clearPendingGeneration();
      });
  }

  private matchesPendingGeneration(sop: Sop, generation: PendingSopGeneration): boolean {
    if (new Date(sop.createdAt).getTime() < new Date(generation.startedAt).getTime() - 1000) {
      return false;
    }

    const expectedDocumentIds = [...generation.sourceDocumentIds].sort((a, b) => a - b);
    const actualDocumentIds = [...sop.sourceDocumentIds].sort((a, b) => a - b);

    return (
      expectedDocumentIds.length === actualDocumentIds.length &&
      expectedDocumentIds.every((id, index) => id === actualDocumentIds[index])
    );
  }

  private acceptGeneratedSop(sop: Sop, companyId: number): void {
    const companySops = this.sopCache.get(companyId) ?? [];
    const updatedSops = [sop, ...companySops.filter((existing) => existing.id !== sop.id)];

    this.sopCache.set(companyId, updatedSops);

    if (this.selectedCompanyId() === companyId) {
      this.sops.set(updatedSops);
    }

    this.generatedSop.set(sop);
  }

  private clearPendingGeneration(): void {
    this.pendingGeneration.set(null);
    localStorage.removeItem(PENDING_GENERATION_KEY);
    this.generationPollingSubscription?.unsubscribe();
    this.generationPollingSubscription = undefined;
  }

  private async renderDocx(file: Blob): Promise<void> {
    const container = this.docxPreview?.nativeElement;

    if (!container) {
      this.errorMessage.set('The DOCX preview could not be opened.');
      return;
    }

    container.replaceChildren();

    try {
      const { renderAsync } = await import('docx-preview');
      await renderAsync(file, container, undefined, {
        className: 'docx',
        inWrapper: true,
        ignoreWidth: false,
        ignoreHeight: false
      });
    } catch {
      this.errorMessage.set('This DOCX file could not be rendered. You can still download it.');
    }
  }
}
