# Branching Strategy

## Overview

CircleGuard follows a **GitFlow-inspired** branching model adapted for a CI/CD pipeline with three promotion environments (dev → stage → master).

## Branch Structure

```
main          ← production-ready code; tagged with SemVer on each release
develop       ← integration branch; all features merge here first
feature/<N>-<name>  ← one branch per feature/task (N = GitHub issue number)
```

## Branch Descriptions

| Branch | Purpose | CI/CD Pipeline |
|--------|---------|----------------|
| `main` | Stable, released code. Every commit here triggers `Jenkinsfile.master`: full build → unit tests → Docker build/push → deploy to master namespace → system tests → performance tests → tag release. | `Jenkinsfile.master` |
| `develop` | Integration branch. Triggers `Jenkinsfile.stage`: build → unit tests → Docker build/push → deploy to stage namespace → integration tests → E2E tests. | `Jenkinsfile.stage` |
| `feature/<N>-<name>` | Short-lived feature branches. Triggers `Jenkinsfile.dev`: build → static analysis → unit tests → Docker build/push → deploy to dev namespace → smoke tests. | `Jenkinsfile.dev` |

## Workflow

```
1. Pick an issue from the Kanban board (move to "In Progress")
2. Create a feature branch from develop:
       git checkout develop
       git pull origin develop
       git checkout -b feature/<issue-number>-<short-description>

3. Implement the feature with conventional commits:
       feat(scope): description       → minor version bump
       fix(scope): description        → patch version bump
       BREAKING CHANGE in body        → major version bump

4. Push the branch — dev pipeline runs automatically

5. Open a Pull Request: feature/* → develop
       - All CI checks must pass
       - At least one reviewer approval required
       - Squash merge preferred to keep develop history clean

6. Merge to develop — stage pipeline runs automatically

7. When develop is stable and ready for release, open a PR: develop → main
       - Master pipeline runs: full test suite + performance tests + tag release
```

## Naming Convention

```
feature/<issue-number>-<kebab-case-description>

Examples:
  feature/1-k8s-secrets
  feature/2-terraform-iac
  feature/3-observability
  feature/4-sonarqube-trivy
  feature/5-circuit-breaker
  feature/6-feature-toggle
  feature/7-c4-diagram
```

## Conventional Commits

All commits must follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

Types: feat | fix | docs | refactor | test | ci | chore
```

The master pipeline uses commit messages since the last tag to compute the next SemVer version automatically.

## Protection Rules (recommended)

- `main`: require PR, require CI pass, no direct push
- `develop`: require PR, require CI pass, no direct push
- `feature/*`: open — developers push freely
