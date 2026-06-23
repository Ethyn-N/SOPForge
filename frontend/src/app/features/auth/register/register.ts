import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';

import { AuthService } from '../../../core/auth/auth.service';

const STRICT_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CardModule,
    InputTextModule,
    PasswordModule
  ],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class Register implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly submittedEmailError = signal<string | null>(null);
  readonly submittedPasswordError = signal<string | null>(null);
  readonly submittedConfirmPasswordError = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.pattern(STRICT_EMAIL_PATTERN)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]]
  });

  ngOnInit(): void {
    const email = this.activatedRoute.snapshot.queryParamMap.get('email');

    if (email) {
      this.form.controls.email.setValue(email);
    }
  }

  private getEmailError(): string | null {
    const email = this.form.controls.email;
    const errors = email.errors;

    if (!errors) {
      return null;
    }

    if (errors['required']) {
      return 'Email is required.';
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
      return 'Password is required.';
    }

    if (errors['minlength']) {
      return 'Password must be at least 8 characters.';
    }

    return null;
  }

  private getConfirmPasswordError(): string | null {
    const confirmPassword = this.form.controls.confirmPassword;
    const errors = confirmPassword.errors;

    if (errors?.['required']) {
      return 'Re-enter your password.';
    }

    if (this.form.controls.password.value !== confirmPassword.value) {
      return 'Passwords must match.';
    }

    return null;
  }

  submit(): void {
    if (this.isSubmitting()) {
      return;
    }

    this.submittedEmailError.set(this.getEmailError());
    this.submittedPasswordError.set(this.getPasswordError());
    this.submittedConfirmPasswordError.set(this.getConfirmPasswordError());

    if (
      this.submittedEmailError() ||
      this.submittedPasswordError() ||
      this.submittedConfirmPasswordError()
    ) {
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    const request = {
      email: this.form.controls.email.value,
      password: this.form.controls.password.value
    };

    this.authService.register(request).subscribe({
      next: () => {
        this.authService.login(request).subscribe({
          next: () => void this.router.navigateByUrl('/dashboard'),
          error: (error) => {
            this.errorMessage.set(this.getRegisterErrorMessage(error));
            this.isSubmitting.set(false);
          }
        });
      },
      error: (error) => {
        this.errorMessage.set(this.getRegisterErrorMessage(error));
        this.isSubmitting.set(false);
      }
    });
  }

  private getRegisterErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message ?? 'Registration request failed. Please try again.';
    }

    return 'Registration request failed. Please try again.';
  }
}
