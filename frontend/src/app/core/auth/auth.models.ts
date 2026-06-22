export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  id: number;
  email: string;
  role: string;
  message: string;
}

export interface EmailCheckResponse {
  email: string;
  registered: boolean;
}

export interface UserResponse {
  id: number;
  email: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}
