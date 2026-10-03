# System Architecture Specification (`ai-cicd-demo`)

This document outlines the detailed system architecture, CI/CD pipeline mechanics, AI test-generation workflow, security model, and automated deployment/rollback strategies for the `ai-cicd-demo` platform.

---

## 1. Application Layer Architecture

The Spring Boot backend follows a clean, strict layered architectural pattern separating concerns between Controllers, Services, Repositories, Domain Entities, and Security Filters.

```mermaid
graph TD
    Client[REST Client / Browser] -->|HTTP Requests| Controllers[Controller Layer]
    Controllers -->|DTO Validation| AuthController[AuthController]
    Controllers -->|DTO Validation| UserController[UserController]
    Controllers -->|Public Health| HealthController[HealthController]
    
    Controllers -->|Invokes Business Logic| UserService[UserService]
    
    UserService -->|Encodes Passwords| BCrypt[BCrypt PasswordEncoder]
    UserService -->|Issues/Validates Tokens| JwtTokenProvider[JwtTokenProvider]
    UserService -->|Queries/Persists| UserRepository[UserRepository]
    
    UserRepository -->|JPA Queries| Database[(H2 / PostgreSQL DB)]

    subgraph Security Layer
        JwtAuthenticationFilter --> CustomUserDetailsService
        SecurityConfig --> JwtAuthenticationFilter
    end

    subgraph Exception Handling
        GlobalExceptionHandler -->|Formats ErrorResponse| Client
    end
```

---

## 2. CI/CD & AI Test Generation Pipeline

The CI/CD workflow is automated using GitHub Actions and an embedded AI Test Generator engine.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant Git as GitHub Repository
    participant PR as Pull Request (#1)
    participant CI as GitHub Actions (CI & AI Workflows)
    participant AI as AI Engine (OpenAI / GitHub Models)
    participant GHCR as GitHub Container Registry
    participant Target as Deployment Server

    Dev->>Git: git push feature/user-authentication
    Dev->>PR: Open Pull Request against main
    PR->>CI: Trigger ai-test-generation.yml
    CI->>CI: Detect changed Java files (git diff origin/main...HEAD)
    CI->>AI: Send prompt & production code (.ai/scripts/generate_tests.py)
    AI-->>CI: Return generated JUnit 5 test class
    CI->>CI: Compile code & execute mvn test
    CI->>CI: Evaluate JaCoCo Line Coverage (Enforce >= 80%)
    CI->>Git: Commit generated tests with 'chore(ai): add generated junit tests'
    CI->>PR: Post AI Generation Report Summary Comment
    
    Dev->>PR: Merge PR to main
    Git->>CI: Trigger cd.yml
    CI->>GHCR: Build Multi-Stage Docker Image & Push ghcr.io/owner/ai-cicd-demo:latest
    CI->>Target: SSH Deploy & Start Container
    CI->>Target: Poll GET /api/health for verification
    alt Health Check PASSED
        CI-->>Dev: Deployment Successful
    else Health Check FAILED
        CI->>Target: Execute Zero-Downtime Rollback to CURRENT_IMAGE
        CI-->>Dev: Deployment Failed & Rollback Executed
    end
```

---

## 3. AI Test Generator Mechanics & Infinite Loop Prevention

To avoid recursive workflow triggers when AI commits generated tests back to the PR branch, the workflow evaluates commit markers and git diff boundaries.

```mermaid
flowchart TD
    A[Pull Request Event] --> B{Commit Message contains 'chore(ai): add generated junit tests'?}
    B -- YES --> C[Skip Workflow Trigger - Prevents Infinite Loop]
    B -- NO --> D[Checkout Branch & Run .ai/scripts/generate_tests.py]
    D --> E[Scan src/main/java for added/modified files]
    E --> F{AI_API_KEY Configured?}
    F -- YES --> G[Invoke Chat Completions API with System Prompt]
    F -- NO --> H[Generate Fallback JUnit 5 Structural Test Class]
    G --> I[Write Test Classes to src/test/java]
    H --> I
    I --> J[Run mvn test & JaCoCo 80% Check]
    J --> K{git status --porcelain src/test/java has changes?}
    K -- YES --> L[Git Commit & Push with 'chore(ai)' marker]
    K -- NO --> M[Complete Workflow without Commit]
```

---

## 4. Docker Container Architecture

The application is containerized using a multi-stage `Dockerfile` to produce minimal, secure runtime images.

```text
+-------------------------------------------------------------------+
| Stage 1: Builder (maven:3.9-eclipse-temurin-17-alpine)             |
|  - Copies pom.xml & resolves dependencies                         |
|  - Compiles source code & packages ai-cicd-demo.jar               |
+-------------------------------------------------------------------+
                                 |
                                 v (Copies built app.jar artifact)
+-------------------------------------------------------------------+
| Stage 2: Runtime (eclipse-temurin:17-jre-alpine)                  |
|  - Creates non-root system group appgroup & user appuser           |
|  - Sets file ownership chown appuser:appgroup /app                |
|  - Configures HEALTHCHECK: GET http://localhost:8080/api/health   |
|  - USER appuser                                                   |
|  - ENTRYPOINT ["java", "-jar", "app.jar"]                         |
+-------------------------------------------------------------------+
```

---

## 5. Deployment & Automated Rollback Strategy

The CD workflow (`.github/workflows/cd.yml`) uses an automated health verification loop to guarantee service availability.

1. **Pre-deployment capture**: Queries container inspection to record `CURRENT_IMAGE` ID.
2. **Container replacement**: Pulls `NEW_IMAGE` from `ghcr.io`, stops and removes the old container instance, and launches the new image.
3. **Health verification loop**: Polls `GET http://localhost:8080/api/health` up to 10 attempts (with 3s delay).
4. **Automated Rollback**: If HTTP status `200` is not received within 10 attempts, the deployment script immediately stops the failed container and redeploys `CURRENT_IMAGE`.

---

## 6. Coverage Strategy & Quality Gates

Code coverage is monitored and enforced by **JaCoCo Maven Plugin** during build execution:

- **Target Metric**: Line Coverage Ratio (`COVEREDRATIO`)
- **Threshold Limit**: Minimum 80% (`0.80`)
- **Exclusion Filters**: Data Transfer Objects (`com/example/aicicddemo/dto/**`), JPA Entities (`com/example/aicicddemo/entity/**`), and OpenAPI Configurations (`com/example/aicicddemo/config/**`).
- **Enforcement Action**: If line coverage falls below `0.80`, `mvn jacoco:check` throws a build error, breaking the CI pipeline and preventing deployment.
