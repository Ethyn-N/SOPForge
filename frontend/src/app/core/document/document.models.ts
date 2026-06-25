export type ExtractionStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

export interface Document {
  id: number;
  originalFileName: string;
  storedFileName: string;
  fileType: string;
  fileSize: number;
  storageUrl: string;
  uploadedAt: string;
  textExtractedAt: string | null;
  extractionStatus: ExtractionStatus;
  extractionError: string | null;
  ownerId: number;
  ownerEmail: string;
  companyId: number | null;
  companyName: string | null;
}

export interface DocumentText {
  documentId: number;
  originalFileName: string;
  extractedText: string | null;
  textExtractedAt: string | null;
  extractionStatus: ExtractionStatus;
  extractionError: string | null;
}
