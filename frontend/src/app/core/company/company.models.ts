export interface Company {
  id: number;
  name: string;
  role: string;
  createdAt: string;
}

export interface CompanyCreateRequest {
  name: string;
}

export type CompanyRole = 'OWNER' | 'ADMIN' | 'REVIEWER' | 'MEMBER';

export interface CompanyMember {
  id: number;
  companyId: number;
  companyName: string;
  userId: number;
  name: string;
  email: string;
  role: CompanyRole;
  createdAt: string;
}
