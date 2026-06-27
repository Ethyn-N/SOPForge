import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  AuthResponse,
  EmailCheckResponse,
  LoginRequest,
  RegisterRequest,
  UserResponse
} from './auth.models';

const TOKEN_KEY = 'sopforge_token';
const USER_KEY = 'sopforge_user';
const SESSION_EXPIRED_KEY = 'sopforge_session_expired';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly tokenState = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly userState = signal<AuthResponse | null>(this.loadStoredUser());

  readonly token = this.tokenState.asReadonly();
  readonly currentUser = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => {
    const token = this.tokenState();
    return Boolean(token) && !this.isTokenExpired(token!);
  });

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => this.storeSession(response))
    );
  }

  checkEmail(email: string): Observable<EmailCheckResponse> {
    return this.http.post<EmailCheckResponse>(`${API_BASE_URL}/auth/email-check`, { email });
  }

  register(request: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${API_BASE_URL}/auth/register`, request);
  }

  logout(): void {
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  expireSession(redirect = true): void {
    if (!this.tokenState()) {
      return;
    }

    this.clearSession();
    sessionStorage.setItem(SESSION_EXPIRED_KEY, 'true');

    if (redirect) {
      void this.router.navigateByUrl('/login');
    }
  }

  consumeSessionExpired(): boolean {
    const sessionExpired = sessionStorage.getItem(SESSION_EXPIRED_KEY) === 'true';
    sessionStorage.removeItem(SESSION_EXPIRED_KEY);
    return sessionExpired;
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenState.set(null);
    this.userState.set(null);
  }

  private storeSession(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response));
    this.tokenState.set(response.token);
    this.userState.set(response);
  }

  private loadStoredUser(): AuthResponse | null {
    const rawUser = localStorage.getItem(USER_KEY);

    if (!rawUser) {
      return null;
    }

    try {
      return JSON.parse(rawUser) as AuthResponse;
    } catch {
      localStorage.removeItem(USER_KEY);
      return null;
    }
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payloadSegment = token.split('.')[1];

      if (!payloadSegment) {
        return true;
      }

      const normalizedPayload = payloadSegment.replace(/-/g, '+').replace(/_/g, '/');
      const paddedPayload = normalizedPayload.padEnd(Math.ceil(normalizedPayload.length / 4) * 4, '=');
      const payload = JSON.parse(atob(paddedPayload)) as { exp?: number };
      return typeof payload.exp !== 'number' || payload.exp * 1000 <= Date.now();
    } catch {
      return true;
    }
  }
}
