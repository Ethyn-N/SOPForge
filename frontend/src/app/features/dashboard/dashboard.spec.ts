import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { Company } from '../../core/company/company.models';
import { CompanyService } from '../../core/company/company.service';
import { Document } from '../../core/document/document.models';
import { DocumentService } from '../../core/document/document.service';
import { Sop } from '../../core/sop/sop.models';
import { SopService } from '../../core/sop/sop.service';
import { Dashboard } from './dashboard';

const companies: Company[] = [
  { id: 1, name: 'Restaurant Group', role: 'OWNER', createdAt: '2026-06-01T12:00:00Z' },
  { id: 2, name: 'Community Kitchen', role: 'ADMIN', createdAt: '2026-06-02T12:00:00Z' }
];

const restaurantDocument = documentFixture(11, 1, 'server-guide.pdf');
const kitchenDocument = documentFixture(21, 2, 'kitchen-guide.docx');
const draftSop = sopFixture(31, 1, 'Opening Procedure', 'DRAFT', [11]);
const approvedSop = sopFixture(32, 1, 'Closing Procedure', 'APPROVED', [11]);

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard> | undefined;
  let companyService: {
    getCompanies: ReturnType<typeof vi.fn>;
    createCompany: ReturnType<typeof vi.fn>;
    deleteCompany: ReturnType<typeof vi.fn>;
  };
  let documentService: {
    getCompanyDocuments: ReturnType<typeof vi.fn>;
    uploadCompanyDocument: ReturnType<typeof vi.fn>;
    downloadCompanyDocument: ReturnType<typeof vi.fn>;
    deleteCompanyDocument: ReturnType<typeof vi.fn>;
  };
  let sopService: {
    getCompanySops: ReturnType<typeof vi.fn>;
    previewCompanyRelevance: ReturnType<typeof vi.fn>;
    generateCompanySop: ReturnType<typeof vi.fn>;
    createCompanyGenerationJob: ReturnType<typeof vi.fn>;
    getCompanyGenerationJob: ReturnType<typeof vi.fn>;
    getCompanyGenerationJobs: ReturnType<typeof vi.fn>;
    updateCompanySop: ReturnType<typeof vi.fn>;
    submitCompanySop: ReturnType<typeof vi.fn>;
    approveCompanySop: ReturnType<typeof vi.fn>;
    rejectCompanySop: ReturnType<typeof vi.fn>;
    archiveCompanySop: ReturnType<typeof vi.fn>;
    getCompanySopVersions: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    localStorage.clear();

    companyService = {
      getCompanies: vi.fn(() => of(companies)),
      createCompany: vi.fn(),
      deleteCompany: vi.fn()
    };
    documentService = {
      getCompanyDocuments: vi.fn((companyId: number) =>
        of(companyId === 1 ? [restaurantDocument] : [kitchenDocument])
      ),
      uploadCompanyDocument: vi.fn(),
      downloadCompanyDocument: vi.fn(),
      deleteCompanyDocument: vi.fn()
    };
    sopService = {
      getCompanySops: vi.fn((companyId: number) =>
        of(companyId === 1 ? [draftSop, approvedSop] : [])
      ),
      previewCompanyRelevance: vi.fn(),
      generateCompanySop: vi.fn(),
      createCompanyGenerationJob: vi.fn(),
      getCompanyGenerationJob: vi.fn(),
      getCompanyGenerationJobs: vi.fn(),
      updateCompanySop: vi.fn(),
      submitCompanySop: vi.fn(),
      approveCompanySop: vi.fn(),
      rejectCompanySop: vi.fn(),
      archiveCompanySop: vi.fn(),
      getCompanySopVersions: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        {
          provide: AuthService,
          useValue: {
            currentUser: signal({
              token: 'token',
              id: 7,
              email: 'owner@example.com',
              role: 'USER',
              message: 'Authenticated'
            }).asReadonly(),
            logout: vi.fn()
          }
        },
        { provide: CompanyService, useValue: companyService },
        { provide: DocumentService, useValue: documentService },
        { provide: SopService, useValue: sopService }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    fixture = undefined;
    vi.useRealTimers();
    localStorage.clear();
  });

  it('loads the first workspace with its documents and SOPs', () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;

    expect(dashboard.selectedCompanyId()).toBe(1);
    expect(dashboard.documents()).toEqual([restaurantDocument]);
    expect(dashboard.sops()).toEqual([draftSop, approvedSop]);
    expect(documentService.getCompanyDocuments).toHaveBeenCalledWith(1);
    expect(sopService.getCompanySops).toHaveBeenCalledWith(1);
  });

  it('switches workspace data without rebuilding the dashboard', () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;

    dashboard.selectCompany(2);

    expect(dashboard.selectedCompanyId()).toBe(2);
    expect(dashboard.documents()).toEqual([kitchenDocument]);
    expect(dashboard.sops()).toEqual([]);
    expect(documentService.getCompanyDocuments).toHaveBeenCalledWith(2);
  });

  it('keeps a newly created workspace selected in the workspace control', () => {
    const createdCompany: Company = {
      id: 3,
      name: 'New Workspace',
      role: 'OWNER',
      createdAt: '2026-06-27T12:00:00Z'
    };
    companyService.createCompany.mockReturnValue(of(createdCompany));
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;

    dashboard.companyName.set('New Workspace');
    dashboard.createCompany();
    fixture.detectChanges();

    const workspaceSelect = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLSelectElement>('select[aria-label="Current workspace"]');
    expect(dashboard.selectedCompanyId()).toBe(3);
    expect(workspaceSelect?.value).toBe('3');
  });

  it('uses real Documents and SOP navigation views', () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;

    dashboard.showSops('generate');
    expect(dashboard.activeView()).toBe('sops');
    expect(dashboard.sopView()).toBe('generate');

    dashboard.showDocuments();
    expect(dashboard.activeView()).toBe('documents');
  });

  it('filters the SOP library by workflow status', () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;

    dashboard.setSopFilter('APPROVED');

    expect(dashboard.filteredSops()).toEqual([approvedSop]);
  });

  it('shows archived SOPs only in the archived filter', () => {
    const archivedSop = sopFixture(33, 1, 'Old Procedure', 'ARCHIVED', [11]);
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;
    dashboard.sops.set([draftSop, archivedSop]);

    expect(dashboard.filteredSops()).toEqual([draftSop]);
    dashboard.setSopFilter('ARCHIVED');
    expect(dashboard.filteredSops()).toEqual([archivedSop]);
  });

  it('opens and closes a complete SOP reader', () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;

    dashboard.openSop(draftSop);
    fixture.detectChanges();

    expect(dashboard.selectedSop()).toBe(draftSop);
    expect((fixture.nativeElement as HTMLElement).querySelector('.sop-reader')?.textContent)
      .toContain('Follow each documented step.');

    dashboard.closeSop();
    expect(dashboard.selectedSop()).toBeNull();
  });

  it('dismisses success notices automatically', () => {
    vi.useFakeTimers();
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const dashboard = fixture.componentInstance;

    dashboard.successMessage.set('Generated successfully.');
    fixture.detectChanges();
    vi.advanceTimersByTime(5000);

    expect(dashboard.successMessage()).toBeNull();
  });

  it('recovers a completed SOP from pending generation polling', () => {
    vi.useFakeTimers();
    const createdAt = new Date().toISOString();
    const recoveredSop = {
      ...sopFixture(40, 1, 'Recovered SOP', 'DRAFT', [11]),
      createdAt,
      updatedAt: createdAt
    };
    const completedJob = {
      id: 70,
      companyId: 1,
      requestedTitle: 'Recovered SOP',
      instructions: null,
      roles: 'Manager',
      status: 'SUCCESS' as const,
      sourceDocumentIds: [11],
      sourceDocumentOriginalFileNames: ['server-guide.pdf'],
      resultSopId: 40,
      errorMessage: null,
      createdAt,
      startedAt: createdAt,
      completedAt: createdAt
    };
    localStorage.setItem(
      'sopforge_pending_generation',
      JSON.stringify(completedJob)
    );
    sopService.getCompanySops.mockReturnValue(of([recoveredSop]));
    sopService.getCompanyGenerationJob.mockReturnValue(of(completedJob));

    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);

    const dashboard = fixture.componentInstance;
    expect(dashboard.pendingGeneration()).toBeNull();
    expect(dashboard.generatedSop()).toEqual(recoveredSop);
    expect(localStorage.getItem('sopforge_pending_generation')).toBeNull();
  });
});

function documentFixture(id: number, companyId: number, fileName: string): Document {
  return {
    id,
    originalFileName: fileName,
    storedFileName: `${id}-${fileName}`,
    fileType: fileName.endsWith('.docx')
      ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      : 'application/pdf',
    fileSize: 2048,
    storageUrl: `/uploads/documents/${id}-${fileName}`,
    uploadedAt: '2026-06-20T12:00:00Z',
    textExtractedAt: '2026-06-20T12:00:01Z',
    extractionStatus: 'SUCCESS',
    extractionError: null,
    ownerId: 7,
    ownerEmail: 'owner@example.com',
    companyId,
    companyName: companies.find((company) => company.id === companyId)?.name ?? null
  };
}

function sopFixture(
  id: number,
  companyId: number,
  title: string,
  status: Sop['status'],
  sourceDocumentIds: number[]
): Sop {
  return {
    id,
    title,
    purpose: 'Keep work consistent.',
    scope: 'All assigned team members.',
    procedure: '1. Prepare the workspace.\n2. Follow each documented step.',
    roles: 'Manager, Team Member',
    status,
    sourceDocumentIds,
    sourceDocumentOriginalFileNames: ['server-guide.pdf'],
    sourceChunkCount: 2,
    sourceChunks: [],
    ownerId: 7,
    ownerEmail: 'owner@example.com',
    companyId,
    companyName: 'Restaurant Group',
    createdAt: '2026-06-26T12:00:01Z',
    updatedAt: '2026-06-26T12:00:01Z'
  };
}
