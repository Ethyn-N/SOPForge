export type SopStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';

export interface SopGenerateRequest {
  title: string;
  sourceDocumentIds: number[];
  instructions: string;
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
