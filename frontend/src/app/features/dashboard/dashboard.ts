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
import { catchError, concatMap, finalize, from, map, of, Subscription, switchMap, timer, toArray } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TagModule } from 'primeng/tag';

import { AuthService } from '../../core/auth/auth.service';
import { Company, CompanyMember, CompanyRole } from '../../core/company/company.models';
import { CompanyService } from '../../core/company/company.service';
import { Document } from '../../core/document/document.models';
import { DocumentService } from '../../core/document/document.service';
import {
  RelevancePreview,
  Sop,
  SopGenerationJob,
  SopVersion
} from '../../core/sop/sop.models';
import { SopService } from '../../core/sop/sop.service';

const PENDING_GENERATION_KEY = 'sopforge_pending_generation';

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
  readonly activeView = signal<'documents' | 'sops' | 'members'>('documents');
  readonly members = signal<CompanyMember[]>([]);
  readonly memberEmail = signal('');
  readonly memberRole = signal<CompanyRole>('MEMBER');
  readonly openRoleMenuMemberId = signal<number | null>(null);
  readonly isLoadingMembers = signal(false);
  readonly isSavingMember = signal(false);
  readonly sopView = signal<'library' | 'generate'>('library');
  readonly sopFilter = signal<'ALL' | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'ARCHIVED'>('ALL');
  readonly showCompanyCreator = signal(false);
  readonly selectedDocumentIds = signal<Set<number>>(new Set());
  readonly companyName = signal('');
  readonly selectedFileName = signal('');
  readonly sopTitle = signal('');
  readonly sopInstructions = signal('');
  readonly sopRoles = signal('');
  readonly documentPreview = signal<{
    document: Document;
    objectUrl: string | null;
    safeUrl: SafeResourceUrl | null;
    isDocx: boolean;
  } | null>(null);
  readonly selectedSop = signal<Sop | null>(null);
  readonly sopVersions = signal<SopVersion[]>([]);
  readonly isEditingSop = signal(false);
  readonly isSavingSop = signal(false);
  readonly isUpdatingSopStatus = signal(false);
  readonly isLoadingSopVersions = signal(false);
  readonly editSopTitle = signal('');
  readonly editSopPurpose = signal('');
  readonly editSopScope = signal('');
  readonly editSopRoles = signal('');
  readonly editSopProcedure = signal('');
  readonly pendingGeneration = signal<SopGenerationJob | null>(this.loadPendingGeneration());
  readonly deleteCandidate = signal<Document | null>(null);
  readonly relevancePreview = signal<RelevancePreview | null>(null);
  readonly generatedSop = signal<Sop | null>(null);
  readonly isLoadingCompanies = signal(false);
  readonly isCreatingCompany = signal(false);
  readonly isDeletingCompany = signal(false);
  readonly companyDeleteCandidate = signal<Company | null>(null);
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

  readonly canReviewSelectedCompany = computed(() => {
    const role = this.selectedCompany()?.role;
    return role === 'OWNER' || role === 'ADMIN' || role === 'REVIEWER';
  });

  readonly canApproveSelectedCompany = computed(() => {
    const role = this.selectedCompany()?.role;
    return role === 'OWNER' || role === 'ADMIN' || role === 'REVIEWER';
  });

  readonly selectedDocuments = computed(() => {
    const selectedIds = this.selectedDocumentIds();
    return this.documents().filter((document) => selectedIds.has(document.id));
  });

  readonly filteredSops = computed(() => {
    const filter = this.sopFilter();
    return filter === 'ALL'
      ? this.sops().filter((sop) => sop.status !== 'ARCHIVED')
      : this.sops().filter((sop) => sop.status === filter);
  });

  readonly generationInProgress = computed(
    () => this.isGeneratingSop() || this.pendingGeneration() !== null
  );

  readonly generationIsForSelectedCompany = computed(
    () => this.pendingGeneration()?.companyId === this.selectedCompanyId()
  );

  private selectedFiles: File[] = [];
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

  requestCompanyDelete(): void {
    const company = this.selectedCompany();
    if (company?.role === 'OWNER') {
      this.companyDeleteCandidate.set(company);
    }
  }

  confirmCompanyDelete(): void {
    const company = this.companyDeleteCandidate();
    if (!company) return;

    this.isDeletingCompany.set(true);
    this.companyService.deleteCompany(company.id)
      .pipe(finalize(() => this.isDeletingCompany.set(false)))
      .subscribe({
        next: () => {
          this.companyDeleteCandidate.set(null);
          this.documentCache.delete(company.id);
          this.sopCache.delete(company.id);
          const remainingCompanies = this.companies().filter((item) => item.id !== company.id);
          this.companies.set(remainingCompanies);
          const nextCompanyId = remainingCompanies[0]?.id ?? null;
          this.selectedCompanyId.set(nextCompanyId);

          if (nextCompanyId) {
            this.showCachedCompanyData(nextCompanyId);
            this.loadDocuments(nextCompanyId);
            this.loadSops(nextCompanyId);
          } else {
            this.resetCompanyWorkspace();
          }

          this.successMessage.set(`${company.name} was deleted.`);
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

  showMembers(): void {
    this.activeView.set('members');
    const companyId = this.selectedCompanyId();
    if (companyId) this.loadMembers(companyId);
  }

  addMember(): void {
    const companyId = this.selectedCompanyId();
    const email = this.memberEmail().trim();
    if (!companyId || !email) return;
    this.isSavingMember.set(true);
    this.companyService.addCompanyMember(companyId, email, this.memberRole())
      .pipe(finalize(() => this.isSavingMember.set(false)))
      .subscribe({
        next: (member) => {
          this.members.update((members) => [...members, member]);
          this.memberEmail.set('');
          this.successMessage.set(`${member.email} was added.`);
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  changeMemberRole(member: CompanyMember, value: string): void {
    const companyId = this.selectedCompanyId();
    if (!companyId) return;
    this.companyService.updateCompanyMember(companyId, member.id, value as CompanyRole).subscribe({
      next: (updated) => {
        this.members.update((members) => members.map((item) => item.id === updated.id ? updated : item));
        this.openRoleMenuMemberId.set(null);
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error))
    });
  }

  toggleRoleMenu(memberId: number): void {
    this.openRoleMenuMemberId.update((openId) => openId === memberId ? null : memberId);
  }

  removeMember(member: CompanyMember): void {
    const companyId = this.selectedCompanyId();
    if (!companyId) return;
    this.companyService.removeCompanyMember(companyId, member.id).subscribe({
      next: () => this.members.update((members) => members.filter((item) => item.id !== member.id)),
      error: (error) => this.errorMessage.set(this.messageFromError(error))
    });
  }

  private loadMembers(companyId: number): void {
    this.isLoadingMembers.set(true);
    this.companyService.getCompanyMembers(companyId)
      .pipe(finalize(() => this.isLoadingMembers.set(false)))
      .subscribe({
        next: (members) => this.members.set(members),
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  setSopFilter(filter: 'ALL' | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'ARCHIVED'): void {
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
    this.selectedFiles = Array.from(input.files ?? []);
    this.selectedFileName.set(
      this.selectedFiles.length === 1
        ? this.selectedFiles[0].name
        : `${this.selectedFiles.length} documents`
    );
  }

  uploadDocument(): void {
    const companyId = this.selectedCompanyId();

    if (!companyId || !this.canManageSelectedCompany()) {
      this.errorMessage.set('You need company owner or admin access to upload documents.');
      return;
    }

    if (!this.selectedFiles.length) {
      this.errorMessage.set('Choose one or more documents to upload.');
      return;
    }

    this.isUploadingDocument.set(true);
    this.clearMessages();

    const files = [...this.selectedFiles];
    from(files)
      .pipe(
        concatMap((file) => this.documentService.uploadCompanyDocument(companyId, file).pipe(
          map((document) => ({ document, error: null as unknown })),
          catchError((error) => of({ document: null, error }))
        )),
        toArray(),
        finalize(() => this.isUploadingDocument.set(false))
      )
      .subscribe({
        next: (results) => {
          const uploaded = results.flatMap((result) => result.document ? [result.document] : []);
          const failedCount = results.filter((result) => result.error !== null).length;
          this.documents.update((documents) => [...uploaded, ...documents]);
          this.documentCache.set(companyId, this.documents());
          this.selectedFiles = [];
          this.selectedFileName.set('');
          if (uploaded.length) {
            this.successMessage.set(`${uploaded.length} ${uploaded.length === 1 ? 'document' : 'documents'} uploaded.`);
          }
          if (failedCount) {
            this.errorMessage.set(`${failedCount} ${failedCount === 1 ? 'document' : 'documents'} could not be uploaded.`);
          }
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
    this.isEditingSop.set(false);
    this.sopVersions.set([]);
  }

  closeSop(): void {
    this.selectedSop.set(null);
    this.isEditingSop.set(false);
    this.sopVersions.set([]);
  }

  startEditingSop(): void {
    const sop = this.selectedSop();
    if (!sop) return;
    this.editSopTitle.set(sop.title);
    this.editSopPurpose.set(sop.purpose);
    this.editSopScope.set(sop.scope);
    this.editSopRoles.set(sop.roles);
    this.editSopProcedure.set(sop.procedure);
    this.isEditingSop.set(true);
  }

  saveSop(): void {
    const companyId = this.selectedCompanyId();
    const sop = this.selectedSop();
    if (!companyId || !sop) return;
    this.isSavingSop.set(true);
    this.sopService.updateCompanySop(companyId, sop.id, {
      title: this.editSopTitle().trim(),
      purpose: this.editSopPurpose().trim(),
      scope: this.editSopScope().trim(),
      roles: this.editSopRoles().trim(),
      procedure: this.editSopProcedure().trim()
    }).pipe(finalize(() => this.isSavingSop.set(false))).subscribe({
      next: (updated) => {
        this.replaceSop(updated);
        this.isEditingSop.set(false);
        this.successMessage.set(`${updated.title} was saved.`);
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error))
    });
  }

  updateSopStatus(action: 'submit' | 'approve' | 'reject' | 'archive'): void {
    const companyId = this.selectedCompanyId();
    const sop = this.selectedSop();
    if (!companyId || !sop) return;
    const request = action === 'submit' ? this.sopService.submitCompanySop(companyId, sop.id)
      : action === 'approve' ? this.sopService.approveCompanySop(companyId, sop.id)
      : action === 'reject' ? this.sopService.rejectCompanySop(companyId, sop.id)
      : this.sopService.archiveCompanySop(companyId, sop.id);
    this.isUpdatingSopStatus.set(true);
    request.pipe(finalize(() => this.isUpdatingSopStatus.set(false))).subscribe({
      next: (updated) => {
        this.replaceSop(updated);
        this.successMessage.set(`${updated.title} is now ${updated.status.toLowerCase().replace('_', ' ')}.`);
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error))
    });
  }

  loadSopVersions(): void {
    const companyId = this.selectedCompanyId();
    const sop = this.selectedSop();
    if (!companyId || !sop) return;
    this.isLoadingSopVersions.set(true);
    this.sopService.getCompanySopVersions(companyId, sop.id)
      .pipe(finalize(() => this.isLoadingSopVersions.set(false)))
      .subscribe({
        next: (versions) => this.sopVersions.set([...versions].reverse()),
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  private replaceSop(updated: Sop): void {
    this.selectedSop.set(updated);
    this.sops.update((sops) => sops.map((sop) => sop.id === updated.id ? updated : sop));
    const companyId = this.selectedCompanyId();
    if (companyId) this.sopCache.set(companyId, this.sops());
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

    this.sopService
      .createCompanyGenerationJob(companyId, request)
      .pipe(finalize(() => this.isGeneratingSop.set(false)))
      .subscribe({
        next: (job) => {
          this.trackPendingGeneration(job);
          this.sopView.set('library');
          this.successMessage.set(`${job.requestedTitle} is generating in the background.`);
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

    if (!this.sopRoles().trim()) {
      this.errorMessage.set('Enter the roles responsible for this SOP.');
      return null;
    }

    return {
      title,
      sourceDocumentIds,
      instructions: this.sopInstructions().trim(),
      roles: this.sopRoles().trim()
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
    this.sopRoles.set('');
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

  private trackPendingGeneration(job: SopGenerationJob): void {
    this.pendingGeneration.set(job);
    localStorage.setItem(PENDING_GENERATION_KEY, JSON.stringify(job));
    this.startPendingGenerationPolling();
  }

  private loadPendingGeneration(): SopGenerationJob | null {
    const storedGeneration = localStorage.getItem(PENDING_GENERATION_KEY);

    if (!storedGeneration) {
      return null;
    }

    try {
      return JSON.parse(storedGeneration) as SopGenerationJob;
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
          const job = this.pendingGeneration();

          if (!job) {
            return of(null);
          }

          return this.sopService
            .getCompanyGenerationJob(job.companyId, job.id)
            .pipe(catchError(() => of(null)));
        })
      )
      .subscribe((job) => {
        if (!job) {
          return;
        }

        this.pendingGeneration.set(job);
        localStorage.setItem(PENDING_GENERATION_KEY, JSON.stringify(job));

        if (job.status === 'FAILED') {
          this.errorMessage.set(job.errorMessage ?? 'SOP generation failed.');
          this.clearPendingGeneration();
          return;
        }

        if (job.status !== 'SUCCESS' || job.resultSopId === null) {
          return;
        }

        this.generationPollingSubscription?.unsubscribe();
        this.generationPollingSubscription = undefined;
        this.sopService.getCompanySops(job.companyId).subscribe({
          next: (sops) => {
            this.sopCache.set(job.companyId, sops);

            if (this.selectedCompanyId() === job.companyId) {
              this.sops.set(sops);
            }

            const generatedSop = sops.find((sop) => sop.id === job.resultSopId) ?? null;
            this.generatedSop.set(generatedSop);
            this.successMessage.set(`${job.requestedTitle} finished generating.`);
            this.clearPendingGeneration();
          },
          error: (error) => this.errorMessage.set(this.messageFromError(error))
        });
      });
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
