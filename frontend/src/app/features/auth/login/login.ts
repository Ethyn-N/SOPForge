import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';

import { AuthService } from '../../../core/auth/auth.service';

type LoginStep = 'email' | 'password' | 'new-account';

const STRICT_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    InputTextModule,
    PasswordModule
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly step = signal<LoginStep>('email');
  readonly checkedEmail = signal<string | null>(null);
  readonly submittedEmailError = signal<string | null>(null);
  readonly submittedPasswordError = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.pattern(STRICT_EMAIL_PATTERN)]],
    password: ['', [Validators.required]]
  });

  ngOnInit(): void {
    if (this.authService.consumeSessionExpired()) {
      this.errorMessage.set('Your session expired. Sign in again.');
    }
  }

  submitEmail(): void {
    if (this.isSubmitting()) {
      return;
    }

    this.submittedEmailError.set(this.getEmailError());

    if (this.submittedEmailError()) {
      return;
    }

    this.isSubmitting.set(true);

    this.authService.checkEmail(this.form.controls.email.value).subscribe({
      next: (response) => {
        this.checkedEmail.set(response.email);
        this.form.controls.email.setValue(response.email);
        this.step.set(response.registered ? 'password' : 'new-account');
        this.isSubmitting.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.getRequestErrorMessage(error, 'Email check failed. Please try again.'));
        this.isSubmitting.set(false);
      }
    });
  }

  submitPassword(): void {
    if (this.isSubmitting()) {
      return;
    }

    this.submittedPasswordError.set(this.getPasswordError());

    if (this.submittedPasswordError()) {
      return;
    }

    this.isSubmitting.set(true);

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: (error) => {
        this.errorMessage.set(this.getRequestErrorMessage(error, 'Sign in request failed. Please try again.'));
        this.isSubmitting.set(false);
      }
    });
  }

  changeEmail(): void {
    if (this.isSubmitting()) {
      return;
    }

    this.step.set('email');
    this.checkedEmail.set(null);
    this.form.controls.password.reset('');
    this.errorMessage.set(null);
    this.submittedEmailError.set(null);
    this.submittedPasswordError.set(null);
  }

  createAccount(): void {
    if (this.isSubmitting()) {
      return;
    }

    void this.router.navigate(['/register'], {
      queryParams: {
        email: this.checkedEmail() ?? this.form.controls.email.value
      }
    });
  }

  private getEmailError(): string | null {
    const email = this.form.controls.email;
    const errors = email.errors;

    if (!errors) {
      return null;
    }

    if (errors['required']) {
      return 'Enter your email address.';
    }

    if (errors['pattern']) {
      return 'Enter a valid email address.';
    }

    return null;
  }

  private getPasswordError(): string | null {
    const password = this.form.controls.password;
    const errors = password.errors;

    if (!errors) {
      return null;
    }

    if (errors['required']) {
      return 'Enter your password.';
    }

    return null;
  }

  private getRequestErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message ?? fallback;
    }

    return fallback;
  }
}
