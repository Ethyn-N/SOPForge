import { HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const authService = {
    token: vi.fn(() => 'expired-token'),
    expireSession: vi.fn()
  };

  beforeEach(() => {
    authService.token.mockReturnValue('expired-token');
    authService.expireSession.mockClear();

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authService }]
    });
  });

  it('adds the bearer token to protected API requests', async () => {
    const request = new HttpRequest('GET', 'http://localhost:8080/api/companies');

    await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authInterceptor(request, (handledRequest) => {
          expect(handledRequest.headers.get('Authorization')).toBe('Bearer expired-token');
          return of(new HttpResponse({ status: 200 }));
        })
      )
    );
  });

  it('expires the local session when a protected request returns 401', async () => {
    const request = new HttpRequest('GET', 'http://localhost:8080/api/companies');
    const unauthorized = new HttpErrorResponse({ status: 401 });

    await expect(
      firstValueFrom(
        TestBed.runInInjectionContext(() =>
          authInterceptor(request, () => throwError(() => unauthorized))
        )
      )
    ).rejects.toBe(unauthorized);

    expect(authService.expireSession).toHaveBeenCalledOnce();
  });

  it('does not expire the session for a normal authorization failure', async () => {
    const request = new HttpRequest('GET', 'http://localhost:8080/api/admin-only');
    const forbidden = new HttpErrorResponse({ status: 403 });

    await expect(
      firstValueFrom(
        TestBed.runInInjectionContext(() =>
          authInterceptor(request, () => throwError(() => forbidden))
        )
      )
    ).rejects.toBe(forbidden);

    expect(authService.expireSession).not.toHaveBeenCalled();
  });

  it('leaves authentication endpoint errors for the login page to display', async () => {
    const request = new HttpRequest('POST', 'http://localhost:8080/api/auth/login', null);
    const unauthorized = new HttpErrorResponse({ status: 401 });

    await expect(
      firstValueFrom(
        TestBed.runInInjectionContext(() =>
          authInterceptor(request, (handledRequest) => {
            expect(handledRequest.headers.has('Authorization')).toBe(false);
            return throwError(() => unauthorized);
          })
        )
      )
    ).rejects.toBe(unauthorized);

    expect(authService.expireSession).not.toHaveBeenCalled();
  });
});
