# SOPForge

SOPForge is a secure AI-powered document and SOP management platform. The project lets users upload business documents, extract text, select company-scoped source material, and generate structured Standard Operating Procedures with ownership, approval workflows, version history, and relevance debugging.

This is a learning project, but it is being built with production-style patterns: JWT auth, role-based access, company scoping, Flyway migrations, DTO responses, local-only secret config, and a separate Angular frontend.

## Current Status

SOPForge currently has a working Spring Boot backend and an early Angular frontend.

Implemented so far:

- User registration and login with JWT authentication
- Global user roles: `USER` and `ADMIN`
- Company workspaces
- Company member roles and permissions
- Admin-only user management endpoints
- Real file upload with local disk storage
- TXT, PDF, and DOCX text extraction
- Document extraction status and error tracking
- Company-scoped document upload/list/download/delete
- Document chunking for retrieval foundations
- Multi-document SOP generation
- Ollama-powered local AI generation
- Relevance preview and source chunk tracking
- SOP CRUD endpoints
- SOP approval workflow
- SOP version history
- Flyway database migrations
- Angular auth flow with guarded dashboard
- Basic frontend company creation, company selection, and document upload/listing

Not finished yet:

- Full frontend SOP generation screens
- Frontend approval workflow
- Frontend version history views
- Frontend member management screens
- Vector embeddings for real semantic RAG
- Production file storage
- Production deployment

## Tech Stack

Backend:

- Java 26
- Spring Boot 4.1
- Spring Security
- JWT
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Apache PDFBox
- Apache POI
- Ollama for local AI generation

Frontend:

- Angular 21
- PrimeNG
- TypeScript
- RxJS

Local infrastructure:

- PostgreSQL
- Ollama
- Local filesystem document storage

## Project Structure

```text
securedoc-ai/
  src/main/java/com/securedoc/securedoc_ai/
    config/        Spring config, security, AI config, admin bootstrap
    controller/    REST API controllers
    dto/           Request and response DTOs
    exception/     API error handling
    model/         JPA entities and enums
    repository/    Spring Data repositories
    service/       Business logic
  src/main/resources/
    db/migration/  Flyway SQL migrations
    application.properties
  frontend/
    src/app/
      core/        Angular services, auth, API models
      features/    Auth and dashboard screens
  uploads/         Local uploaded files, ignored by Git
```

## Core Backend Concepts

### Authentication

Users register and log in through:

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/email-check
```

Successful login returns a JWT. Protected API calls use:

```http
Authorization: Bearer <token>
```

### Companies

Documents and SOPs are moving toward company-scoped workflows. A user can belong to multiple companies with different roles.

Company endpoints include:

```http
GET    /api/companies
POST   /api/companies
GET    /api/companies/{companyId}/members
POST   /api/companies/{companyId}/members
PATCH  /api/companies/{companyId}/members/{memberId}
DELETE /api/companies/{companyId}/members/{memberId}
```

### Documents

Documents can be uploaded to a company:

```http
POST /api/companies/{companyId}/documents
```

The backend saves the file locally, extracts text, stores extraction metadata, and creates text chunks.

Supported extraction:

- `.txt` via Java file reading
- `.pdf` via Apache PDFBox
- `.docx` via Apache POI

### SOPs

SOPs can be generated from one or more source documents:

```http
POST /api/companies/{companyId}/sops/generate
```

SOPs include:

- title
- purpose
- scope
- procedure
- roles
- status
- owner
- source documents
- source chunks
- version history

Workflow statuses:

- `DRAFT`
- `PENDING_REVIEW`
- `APPROVED`
- `REJECTED`
- `ARCHIVED`

### RAG Foundations

The project has early retrieval foundations:

- Extracted document text is split into chunks.
- Chunks are scored using keyword/phrase relevance.
- SOP generation can use selected relevant chunks.
- Generated SOPs store source chunk references for debugging.

This is not full vector search yet. A future version should add embeddings and similarity search.

## Local Setup

### Backend Requirements

- Java 26
- Maven
- PostgreSQL
- Ollama, if testing AI generation

Create a PostgreSQL database matching `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/securedoc_ai
spring.datasource.username=securedoc_user
spring.datasource.password=SecureDoc123!
```

Start the backend:

```powershell
.\mvnw spring-boot:run
```

The backend runs on:

```text
http://localhost:8080
```

Health check:

```http
GET /api/health
```

### Local Admin User

Local admin settings live in:

```text
src/main/resources/application-local.properties
```

That file is ignored by Git. Use this example file as a template:

```text
src/main/resources/application-local.example.properties
```

To create or reset a local admin, set:

```properties
sopforge.admin.bootstrap.enabled=true
sopforge.admin.bootstrap.reset-password=true
sopforge.admin.bootstrap.email=admin@sopforge.local
sopforge.admin.bootstrap.password=Admin12345!
```

Start the backend once. After the admin works, change the local file back to:

```properties
sopforge.admin.bootstrap.enabled=false
sopforge.admin.bootstrap.reset-password=false
```

### Frontend Requirements

- Node.js
- npm

Start the frontend:

```powershell
cd frontend
npm.cmd start
```

The frontend runs on:

```text
http://localhost:4200
```

Build the frontend:

```powershell
cd frontend
npm.cmd run build
```

## Local AI Setup

SOPForge currently uses Ollama for local AI generation.

Recommended local model setting:

```properties
securedoc.ai.ollama.base-url=http://localhost:11434
securedoc.ai.ollama.model=qwen2.5:14b
```

Example Ollama setup:

```powershell
ollama pull qwen2.5:14b
ollama run qwen2.5:14b
```

Ollama keeps the project free while learning. Later, the AI provider can be swapped for a hosted model provider behind the same service boundary.

## Testing

Run backend tests:

```powershell
.\mvnw test
```

Run frontend build verification:

```powershell
cd frontend
npm.cmd run build
```

## Roadmap

### Frontend Next

- Build full document detail pages
- Add document text viewer
- Add chunk and relevance debugging views
- Add SOP generation form
- Let users choose source documents and generation instructions
- Add SOP list and detail pages
- Add SOP edit screen
- Add submit/approve/reject/archive controls
- Add SOP version history screen
- Add source chunk viewer for generated SOPs
- Add company member management UI
- Add role-aware UI states
- Add loading skeletons and empty states
- Add better toast/alert system
- Add frontend tests

### Backend Next

- Replace keyword relevance with vector embeddings
- Add pgvector or another vector store
- Store embeddings for document chunks
- Add async extraction/generation jobs
- Add upload progress and background job status
- Add audit log events
- Add password reset flow
- Add refresh tokens or more complete session management
- Add better validation annotations on request DTOs
- Add pagination for documents, SOPs, users, and companies
- Add rate limiting for auth and AI generation endpoints
- Add more integration tests around company permissions

### AI/RAG Next

- Generate embeddings for chunks
- Use semantic search instead of only keyword scoring
- Add document relevance filtering for unrelated uploads
- Add prompt templates by SOP type
- Add generation confidence/source coverage indicators
- Add user feedback loop for generated SOP quality
- Add provider abstraction for Ollama, OpenAI, Anthropic, or cloud-hosted models

### Storage Next

Current storage is local:

```text
uploads/documents
```

Future production storage options:

- AWS S3 for uploaded files
- Supabase Storage for simpler hosted file storage
- Signed download URLs
- File virus scanning
- Object lifecycle policies
- Per-company storage prefixes

### Database and Hosting Options

Possible production path:

- Supabase Postgres for the database
- Vercel for the Angular frontend if exported as static assets, or another static host
- Render, Railway, Fly.io, or AWS for the Spring Boot backend
- Supabase Storage or AWS S3 for uploaded files

More production-style path:

- Dockerize backend
- Docker Compose for local backend/Postgres/Ollama
- Deploy backend container to AWS ECS, Fly.io, Render, or Railway
- Use managed Postgres
- Use S3 or Supabase Storage
- Store secrets in platform environment variables or AWS Secrets Manager

### DevOps Roadmap

- Add Dockerfile for Spring Boot backend
- Add Dockerfile or build workflow for Angular frontend
- Add `docker-compose.yml` for local Postgres + backend
- Add GitHub Actions for backend tests and frontend build
- Add production profile config
- Add deployment documentation
- Add environment variable examples
- Add health checks for deployment platforms
- Add database migration checks in CI

## Production Notes

This project is not production-ready yet, but the direction is production-friendly:

- Secrets should not be committed.
- `application-local.properties` is ignored by Git.
- Uploaded local files are ignored by Git.
- Flyway owns schema changes.
- User data is isolated by owner/company checks.
- Admin routes are role-restricted.
- File storage should move out of the app folder before production.
- AI generation should eventually run as a background job.

## Why This Project Exists

SOPForge is meant to be more than "upload files to ChatGPT." The goal is to build a structured business workflow around documents:

- company ownership
- permissions
- selected source documents
- traceable source chunks
- generated SOP drafts
- review and approval
- version history
- future semantic retrieval

That structure is what turns raw AI output into a real document management product.
