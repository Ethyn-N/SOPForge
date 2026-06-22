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

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly tokenState = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly userState = signal<AuthResponse | null>(this.loadStoredUser());

  readonly token = this.tokenState.asReadonly();
  readonly currentUser = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => Boolean(this.tokenState()));

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
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenState.set(null);
    this.userState.set(null);
    void this.router.navigateByUrl('/login');
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
}
