export type SopStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';

export interface SopGenerateRequest {
  title: string;
  sourceDocumentIds: number[];
  instructions: string;
  roles: string;
}

export type SopGenerationJobStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface SopGenerationJob {
  id: number;
  companyId: number;
  requestedTitle: string;
  instructions: string | null;
  roles: string | null;
  status: SopGenerationJobStatus;
  sourceDocumentIds: number[];
  sourceDocumentOriginalFileNames: string[];
  resultSopId: number | null;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface RelevanceChunk {
  documentId: number;
  originalFileName: string;
  chunkId: number;
  chunkIndex: number;
  score: number;
  baseScore: number;
  phraseScore: number;
  finalScore: number;
  matchedTerms: string[];
  matchedPhrases: string[];
  contentPreview: string;
}

export interface RelevancePreview {
  queryTerms: string[];
  queryPhrases: string[];
  chunks: RelevanceChunk[];
}

export interface SopSourceChunk {
  documentId: number;
  originalFileName: string;
  chunkId: number;
  chunkIndex: number;
  relevanceScore: number;
  matchedTerms: string[];
  contentPreview: string;
}

export interface Sop {
  id: number;
  title: string;
  purpose: string;
  scope: string;
  procedure: string;
  roles: string;
  status: SopStatus;
  sourceDocumentIds: number[];
  sourceDocumentOriginalFileNames: string[];
  sourceChunkCount: number;
  sourceChunks: SopSourceChunk[];
  ownerId: number;
  ownerEmail: string;
  companyId: number | null;
  companyName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SopUpdateRequest {
  title: string;
  purpose: string;
  scope: string;
  procedure: string;
  roles: string;
}

export interface SopVersion extends SopUpdateRequest {
  id: number;
  sopId: number;
  versionNumber: number;
  status: SopStatus;
  createdById: number;
  createdByEmail: string;
  changeReason: string;
  createdAt: string;
}
