# Advanced CI/CD Pipeline Guide

CircleGuard uses three progressive pipelines. Each one adds more quality and security gates before code reaches production.

---

## Pipeline Overview

```
Push to branch
      │
      ├─► Jenkinsfile.dev    → fast feedback: build, tests, deploy to dev
      │
      ├─► Jenkinsfile.stage  → quality gates: SonarQube + Trivy + integration tests
      │
      └─► Jenkinsfile.master → full pipeline: quality + security + manual approval + release
```

### Stage-by-stage breakdown

| Stage | dev | stage | master |
|---|:---:|:---:|:---:|
| Build + Unit Tests | x | x | x |
| SonarQube Analysis | — | x | x |
| Quality Gate | — | x | x (hard fail) |
| Docker Build & Push | x | x | x |
| Trivy Security Scan | — | x | x |
| Deploy | dev | stage | master |
| Integration / E2E Tests | — | x | x |
| OWASP ZAP | — | x | x |
| Performance Tests | — | — | x |
| Approval Gate | — | — | x |
| Release + Git Tag | — | — | x |
| Email notifications | failure | failure + success | failure + aborted + success |

---

## New Stages

### SonarQube Analysis

Runs after unit tests. Scans all 8 microservices together as a single multi-module project.

What it sends to SonarQube:
- Jacoco XML coverage reports
- Bugs, code smells, duplications
- Security hotspots

Credential required in Jenkins: `sonarqube-token` (Secret Text).

---

### Quality Gate

Runs immediately after SonarQube Analysis. Calls the SonarQube API and waits up to 150 seconds for a result.

- Result `OK` → pipeline continues
- Result `ERROR` → pipeline fails with a clear message
- Timeout → master fails; stage fails

**Custom Quality Gate — CircleGuard conditions:**

| Condition | Threshold | Meaning |
|---|---|---|
| Issues | > 0 | Any open issue blocks the pipeline |
| Security Hotspots Reviewed | < 100% | All hotspots must be reviewed |
| Coverage | < 80% | At least 80% line coverage required |
| Duplicated Lines (%) | > 3% | No more than 3% code duplication |
| Maintainability Rating | worse than A | Code must be easy to maintain |
| Reliability Rating | worse than A | No bugs allowed |
| Security Rating | worse than A | No vulnerabilities allowed |

To view the Quality Gate result: `http://40.88.229.241` → Projects → Circle Guard → Summary.

---

### Trivy Security Scan

Runs after Docker Build & Push. Scans all 8 service images directly from ACR.

- Scans for `CRITICAL` and `HIGH` CVEs
- **Fails the pipeline** if any `CRITICAL` vulnerability is found
- `HIGH` findings are reported but do not block
- Full report saved as a Jenkins build artifact: `trivy-stage-report.txt` / `trivy-master-report.txt`

**Reading the report:** Go to the build → Artifacts → open the report. Each section shows the service name, CVE ID, severity, affected package, installed version, and fixed version. A `[PASS]` line means no criticals; `[FAIL]` means the pipeline was stopped.

**To suppress a known false positive:** add the CVE ID on a new line in `.trivyignore` at the repo root.

No extra credentials needed — reuses the ACR token from the Docker push stage.

---

### Approval Gate (master only)

Sits between Trivy Scan and Deploy to Master. Prevents any code from reaching production without a human decision.

What happens:
1. An email is sent with links to the SonarQube dashboard and Trivy report
2. The pipeline pauses for up to **60 minutes**
3. An admin opens `http://48.202.171.66:8080`, finds the build, and clicks **Proceed** or **Abort**
4. **Proceed** → deployment continues to the master namespace
5. **Abort** or timeout → pipeline is marked ABORTED, no deployment, notification email sent

---

### Email Notifications

Emails are sent from `vagos.tuneados@gmail.com`. Recipients are managed in Jenkins only — no email addresses in code.

**To add or remove a recipient:**
1. Go to `http://48.202.171.66:8080` → Manage Jenkins → Configure System
2. Scroll to **Extended E-mail Notification**
3. Update the **Default Recipients** field (comma-separated)
4. Save — takes effect on the next build

---

## SonarQube

**URL:** `http://40.88.229.241` (public, accessible from anywhere)

**Navigating the dashboard:**
- **Projects → Circle Guard → Summary** — overall health and Quality Gate status
- **Issues** — bugs, code smells, vulnerabilities grouped by severity
- **Security Hotspots** — security-sensitive code that needs manual review
- **Measures → Coverage** — line and branch coverage per service

---

## Rotating the sonarqube-token

1. SonarQube → My Account → Security → revoke old token → generate new one
2. Jenkins → Manage Jenkins → Credentials → System → Global → `sonarqube-token` → Update
3. Paste the new value → Save

No code changes needed.

---

## Infrastructure Reference

| Component | Namespace | Access |
|---|---|---|
| SonarQube 10.4 | `sonarqube` | `http://40.88.229.241` (via ingress-nginx) |
| PostgreSQL 15 | `sonarqube` | Internal only (`sonarqube-postgres:5432`) |
| Jenkins | `jenkins` | `http://48.202.171.66:8080` |
| In-cluster SonarQube DNS | — | `http://sonarqube.sonarqube.svc.cluster.local:9000` |

**Terraform backup:** `terraform/modules/sonarqube/` mirrors the k8s manifests. It is guarded by `deploy_shared_infra = true` and is documentation only — do not apply it independently. The authoritative deployment is `kubectl apply -f k8s/sonarqube/`.

---

## File Reference

| File | What changed |
|---|---|
| `k8s/sonarqube/` | New — SonarQube + PostgreSQL + Ingress manifests |
| `terraform/modules/sonarqube/` | New — Terraform backup (not applied) |
| `build.gradle.kts` | Added `org.sonarqube` plugin and project configuration |
| `.trivyignore` | New — CVEs excluded from Trivy hard-fail |
| `Jenkinsfile.dev` | Added failure email |
| `Jenkinsfile.stage` | Added SonarQube Analysis, Quality Gate, Trivy Scan, emails |
| `Jenkinsfile.master` | Added SonarQube Analysis, Quality Gate, Trivy Scan, Approval Gate, emails |
