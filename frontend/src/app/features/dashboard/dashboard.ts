import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
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
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, concatMap, finalize, from, last, map, of, Subscription, switchMap, tap, timer, toArray } from 'rxjs';
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

type UploadStatus = 'QUEUED' | 'UPLOADING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';

interface DocumentUploadItem {
  id: string;
  fileName: string;
  fileSize: number;
  progress: number;
  status: UploadStatus;
  error?: string;
}

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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly user = this.authService.currentUser;
  readonly companies = signal<Company[]>([]);
  readonly documents = signal<Document[]>([]);
  readonly sops = signal<Sop[]>([]);
  readonly selectedCompanyId = signal<number | null>(null);
  readonly activeView = signal<'documents' | 'sops' | 'members' | 'settings'>('documents');
  readonly members = signal<CompanyMember[]>([]);
  readonly memberSearch = signal('');
  readonly memberSort = signal<'NAME' | 'ROLE'>('NAME');
  readonly memberSortAscending = signal(true);
  readonly memberEmail = signal('');
  readonly memberRole = signal<CompanyRole>('MEMBER');
  readonly openRoleMenuMemberId = signal<number | null>(null);
  readonly removeMemberCandidate = signal<CompanyMember | null>(null);
  readonly isLoadingMembers = signal(false);
  readonly isSavingMember = signal(false);
  readonly sopView = signal<'library' | 'generate'>('library');
  readonly sopFilter = signal<'ALL' | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'ARCHIVED'>('ALL');
  readonly showCompanyCreator = signal(false);
  readonly selectedDocumentIds = signal<Set<number>>(new Set());
  readonly bulkSelectedDocumentIds = signal<Set<number>>(new Set());
  readonly bulkActionsOpen = signal(false);
  readonly bulkDeleteCandidates = signal<Document[] | null>(null);
  readonly documentPreviewQueue = signal<Document[]>([]);
  readonly documentSearch = signal('');
  readonly companyName = signal('');
  readonly uploadItems = signal<DocumentUploadItem[]>([]);
  readonly uploadPanelCollapsed = signal(false);
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
  readonly isBulkDownloading = signal(false);
  readonly isBulkDeleting = signal(false);
  readonly isLoadingSops = signal(false);
  readonly isPreviewingRelevance = signal(false);
  readonly isGeneratingSop = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly completedUploadCount = computed(() =>
    this.uploadItems().filter((item) => item.status === 'SUCCESS' || item.status === 'FAILED').length
  );

  readonly uploadOverallProgress = computed(() => {
    const items = this.uploadItems();
    const totalBytes = items.reduce((total, item) => total + item.fileSize, 0);
    if (!totalBytes) return 0;
    return Math.round(items.reduce(
      (total, item) => total + item.fileSize * item.progress,
      0
    ) / totalBytes);
  });

  readonly uploadsComplete = computed(() =>
    this.uploadItems().length > 0 && this.completedUploadCount() === this.uploadItems().length
  );

  private noticeDismissTimer?: ReturnType<typeof setTimeout>;
  private uploadDismissTimer?: ReturnType<typeof setTimeout>;
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

  readonly bulkSelectedDocuments = computed(() => {
    const selectedIds = this.bulkSelectedDocumentIds();
    return this.documents().filter((document) => selectedIds.has(document.id));
  });

  readonly bulkSopSourceCount = computed(() =>
    this.bulkSelectedDocuments().filter((document) => document.extractionStatus === 'SUCCESS').length
  );

  readonly allFilteredDocumentsSelected = computed(() => {
    const filtered = this.filteredDocuments();
    const selectedIds = this.bulkSelectedDocumentIds();
    return filtered.length > 0 && filtered.every((document) => selectedIds.has(document.id));
  });

  readonly someFilteredDocumentsSelected = computed(() => {
    const filtered = this.filteredDocuments();
    const selectedIds = this.bulkSelectedDocumentIds();
    return filtered.some((document) => selectedIds.has(document.id))
      && !filtered.every((document) => selectedIds.has(document.id));
  });

  readonly documentPreviewPosition = computed(() => {
    const previewId = this.documentPreview()?.document.id;
    return this.documentPreviewQueue().findIndex((document) => document.id === previewId);
  });

  readonly filteredDocuments = computed(() => {
    const query = this.documentSearch().trim().toLowerCase();
    if (!query) return this.documents();
    return this.documents().filter((document) =>
      document.originalFileName.toLowerCase().includes(query)
      || document.fileType.toLowerCase().includes(query)
      || document.extractionStatus.toLowerCase().includes(query)
    );
  });

  readonly filteredMembers = computed(() => {
    const query = this.memberSearch().trim().toLowerCase();
    const members = query
      ? this.members().filter((member) =>
          member.name.toLowerCase().includes(query)
          || member.email.toLowerCase().includes(query)
          || member.role.toLowerCase().includes(query)
        )
      : [...this.members()];
    const field = this.memberSort() === 'NAME' ? 'name' : 'role';
    const direction = this.memberSortAscending() ? 1 : -1;
    return members.sort((left, right) => left[field].localeCompare(right[field]) * direction);
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
  private documentPollingSubscription?: Subscription;
  private dashboardRouteSubscription?: Subscription;

  ngOnInit(): void {
    this.dashboardRouteSubscription = this.route.paramMap.subscribe((params) => {
      const view = params.get('view');
      if (view === 'documents' || view === 'sops' || view === 'members' || view === 'settings') {
        this.activeView.set(view);
      } else if (view) {
        void this.router.navigateByUrl('/dashboard/documents', { replaceUrl: true });
      }
    });
    this.loadCompanies();
    this.startPendingGenerationPolling();
    this.documentPollingSubscription = timer(15000, 15000).subscribe(() => {
      const companyId = this.selectedCompanyId();
      if (companyId && !this.isUploadingDocument()) this.refreshDocumentsInBackground(companyId);
    });
  }

  ngOnDestroy(): void {
    this.generationPollingSubscription?.unsubscribe();
    this.documentPollingSubscription?.unsubscribe();
    this.dashboardRouteSubscription?.unsubscribe();
    this.noticeAutoDismissEffect.destroy();

    if (this.noticeDismissTimer) {
      clearTimeout(this.noticeDismissTimer);
    }
    this.clearUploadDismissTimer();

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
            this.loadMembers(nextCompanyId);
          } else {
            this.documents.set([]);
            this.sops.set([]);
            this.members.set([]);
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
          this.loadMembers(company.id);
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
            this.loadMembers(nextCompanyId);
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
    this.loadMembers(companyId);
  }

  selectCompanyFromControl(value: string | number): void {
    const companyId = Number(value);

    if (Number.isFinite(companyId)) {
      this.selectCompany(companyId);
    }
  }

  showDocuments(): void {
    this.activeView.set('documents');
    void this.router.navigateByUrl('/dashboard/documents');
  }

  showSops(view: 'library' | 'generate' = 'library'): void {
    this.activeView.set('sops');
    this.sopView.set(view);
    void this.router.navigateByUrl('/dashboard/sops');
  }

  showMembers(): void {
    this.activeView.set('members');
    void this.router.navigateByUrl('/dashboard/members');
  }

  showSettings(): void {
    this.activeView.set('settings');
    void this.router.navigateByUrl('/dashboard/settings');
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
          this.memberRole.set('MEMBER');
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

  sortMembers(field: 'NAME' | 'ROLE'): void {
    if (this.memberSort() === field) {
      this.memberSortAscending.update((ascending) => !ascending);
    } else {
      this.memberSort.set(field);
      this.memberSortAscending.set(true);
    }
  }

  requestMemberRemoval(member: CompanyMember): void {
    this.openRoleMenuMemberId.set(null);
    this.removeMemberCandidate.set(member);
  }

  confirmMemberRemoval(): void {
    const member = this.removeMemberCandidate();
    const companyId = this.selectedCompanyId();
    if (!companyId || !member) return;
    this.companyService.removeCompanyMember(companyId, member.id).subscribe({
      next: () => {
        this.members.update((members) => members.filter((item) => item.id !== member.id));
        this.removeMemberCandidate.set(null);
        this.successMessage.set(`${member.name} was removed.`);
      },
      error: (error) => this.errorMessage.set(this.messageFromError(error))
    });
  }

  private loadMembers(companyId: number): void {
    this.isLoadingMembers.set(true);
    this.companyService.getCompanyMembers(companyId)
      .pipe(finalize(() => this.isLoadingMembers.set(false)))
      .subscribe({
        next: (members) => {
          if (this.selectedCompanyId() === companyId) this.members.set(members);
        },
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
    input.value = '';
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
    const uploadItems = files.map((file, index) => ({
      id: `${Date.now()}-${index}`,
      fileName: file.name,
      fileSize: file.size,
      progress: 0,
      status: 'QUEUED' as UploadStatus
    }));
    this.clearUploadDismissTimer();
    this.uploadItems.set(uploadItems);
    this.uploadPanelCollapsed.set(false);

    from(files.map((file, index) => ({ file, item: uploadItems[index] })))
      .pipe(
        concatMap(({ file, item }) => this.documentService.uploadCompanyDocument(companyId, file).pipe(
          tap((event) => {
            if (event.type === HttpEventType.Sent) {
              this.updateUploadItem(item.id, { status: 'UPLOADING' });
            } else if (event.type === HttpEventType.UploadProgress) {
              const total = event.total ?? file.size;
              const progress = total > 0 ? Math.min(100, Math.round((event.loaded / total) * 100)) : 0;
              this.updateUploadItem(item.id, {
                progress,
                status: progress === 100 ? 'PROCESSING' : 'UPLOADING'
              });
            }
          }),
          last(),
          map((event) => {
            if (event.type !== HttpEventType.Response || !event.body) {
              throw new Error('The upload completed without a document response.');
            }
            this.updateUploadItem(item.id, { progress: 100, status: 'SUCCESS' });
            return { document: event.body, error: null as unknown };
          }),
          catchError((error) => {
            this.updateUploadItem(item.id, {
              status: 'FAILED',
              error: this.messageFromError(error)
            });
            return of({ document: null, error });
          })
        )),
        toArray(),
        finalize(() => {
          this.isUploadingDocument.set(false);
          if (!this.uploadItems().some((item) => item.status === 'FAILED')) {
            this.uploadDismissTimer = setTimeout(() => this.dismissUploadPanel(), 6000);
          }
        })
      )
      .subscribe({
        next: (results) => {
          const uploaded = results.flatMap((result) => result.document ? [result.document] : []);
          const failedCount = results.filter((result) => result.error !== null).length;
          this.documents.update((documents) => [...uploaded, ...documents]);
          this.documentCache.set(companyId, this.documents());
          this.selectedFiles = [];
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

  toggleUploadPanel(): void {
    this.uploadPanelCollapsed.update((collapsed) => !collapsed);
  }

  dismissUploadPanel(): void {
    if (!this.uploadsComplete()) return;
    this.clearUploadDismissTimer();
    this.uploadItems.set([]);
    this.uploadPanelCollapsed.set(false);
  }

  uploadStatusLabel(item: DocumentUploadItem): string {
    switch (item.status) {
      case 'QUEUED': return 'Waiting';
      case 'UPLOADING': return `${item.progress}%`;
      case 'PROCESSING': return 'Processing';
      case 'SUCCESS': return 'Uploaded';
      case 'FAILED': return 'Failed';
    }
  }

  private updateUploadItem(id: string, updates: Partial<DocumentUploadItem>): void {
    this.uploadItems.update((items) => items.map((item) =>
      item.id === id ? { ...item, ...updates } : item
    ));
  }

  private clearUploadDismissTimer(): void {
    if (this.uploadDismissTimer) {
      clearTimeout(this.uploadDismissTimer);
      this.uploadDismissTimer = undefined;
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

  toggleBulkDocumentSelection(documentId: number): void {
    const nextSelection = new Set(this.bulkSelectedDocumentIds());
    nextSelection.has(documentId) ? nextSelection.delete(documentId) : nextSelection.add(documentId);
    this.bulkSelectedDocumentIds.set(nextSelection);
    if (!nextSelection.size) this.bulkActionsOpen.set(false);
  }

  toggleAllFilteredDocuments(selected: boolean): void {
    const nextSelection = new Set(this.bulkSelectedDocumentIds());
    for (const document of this.filteredDocuments()) {
      selected ? nextSelection.add(document.id) : nextSelection.delete(document.id);
    }
    this.bulkSelectedDocumentIds.set(nextSelection);
  }

  clearBulkDocumentSelection(): void {
    this.bulkSelectedDocumentIds.set(new Set());
    this.bulkActionsOpen.set(false);
  }

  createSopFromBulkSelection(): void {
    const sourceIds = this.bulkSelectedDocuments()
      .filter((document) => document.extractionStatus === 'SUCCESS')
      .map((document) => document.id);
    this.selectedDocumentIds.set(new Set(sourceIds));
    this.bulkActionsOpen.set(false);
    this.showSops('generate');
  }

  previewBulkDocuments(): void {
    const documents = this.bulkSelectedDocuments();
    if (!documents.length) return;
    this.bulkActionsOpen.set(false);
    this.previewDocument(documents[0], documents);
  }

  previewAdjacentDocument(offset: number): void {
    const queue = this.documentPreviewQueue();
    const target = queue[this.documentPreviewPosition() + offset];
    if (target) this.previewDocument(target, queue);
  }

  previewDocument(document: Document, queue: Document[] = [document]): void {
    const companyId = this.selectedCompanyId();

    if (!companyId) {
      return;
    }

    this.releaseDocumentPreview();
    this.documentPreviewQueue.set(queue);
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
    this.releaseDocumentPreview();
    this.documentPreviewQueue.set([]);
  }

  private releaseDocumentPreview(): void {
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

  downloadBulkDocuments(): void {
    const companyId = this.selectedCompanyId();
    const documents = this.bulkSelectedDocuments();
    if (!companyId || !documents.length) return;

    this.bulkActionsOpen.set(false);
    this.isBulkDownloading.set(true);
    this.clearMessages();
    this.documentService.downloadCompanyDocuments(companyId, documents.map((document) => document.id))
      .pipe(finalize(() => this.isBulkDownloading.set(false)))
      .subscribe({
        next: (archive) => {
          const downloadUrl = URL.createObjectURL(archive);
          const link = window.document.createElement('a');
          link.href = downloadUrl;
          link.download = 'documents.zip';
          window.document.body.appendChild(link);
          link.click();
          link.remove();
          window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 0);
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  requestBulkDocumentDelete(): void {
    const documents = this.bulkSelectedDocuments();
    if (!documents.length) return;
    this.bulkActionsOpen.set(false);
    this.bulkDeleteCandidates.set(documents);
  }

  confirmBulkDocumentDelete(): void {
    const companyId = this.selectedCompanyId();
    const documents = this.bulkDeleteCandidates();
    if (!companyId || !documents?.length) return;

    const deletedIds = new Set(documents.map((document) => document.id));
    this.isBulkDeleting.set(true);
    this.clearMessages();
    this.documentService.deleteCompanyDocuments(companyId, [...deletedIds])
      .pipe(finalize(() => this.isBulkDeleting.set(false)))
      .subscribe({
        next: () => {
          this.documents.update((current) => current.filter((document) => !deletedIds.has(document.id)));
          this.documentCache.set(companyId, this.documents());
          this.selectedDocumentIds.update((selected) =>
            new Set([...selected].filter((id) => !deletedIds.has(id)))
          );
          this.clearBulkDocumentSelection();
          this.bulkDeleteCandidates.set(null);
          if (this.documentPreview() && deletedIds.has(this.documentPreview()!.document.id)) {
            this.closeDocumentPreview();
          }
          this.successMessage.set(`${deletedIds.size} ${deletedIds.size === 1 ? 'document was' : 'documents were'} deleted.`);
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
          this.bulkSelectedDocumentIds.update((selected) => {
            const next = new Set(selected);
            next.delete(document.id);
            return next;
          });
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
            this.bulkSelectedDocumentIds.update(
              (selectedIds) => new Set([...selectedIds].filter((id) => availableIds.has(id)))
            );
          }
        },
        error: (error) => this.errorMessage.set(this.messageFromError(error))
      });
  }

  private refreshDocumentsInBackground(companyId: number): void {
    this.documentService.getCompanyDocuments(companyId).subscribe({
      next: (documents) => {
        this.documentCache.set(companyId, documents);
        if (this.selectedCompanyId() === companyId) {
          this.documents.set(documents);
          const availableIds = new Set(documents.map((document) => document.id));
          this.bulkSelectedDocumentIds.update(
            (selectedIds) => new Set([...selectedIds].filter((id) => availableIds.has(id)))
          );
        }
      }
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
    this.clearBulkDocumentSelection();
    this.bulkDeleteCandidates.set(null);
    this.documentSearch.set('');
    this.memberSearch.set('');
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
