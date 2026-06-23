export interface Company {
  id: number;
  name: string;
  role: string;
  createdAt: string;
}

export interface CompanyCreateRequest {
  name: string;
}
