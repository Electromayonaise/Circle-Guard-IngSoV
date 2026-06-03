# SonarQube Security Hotspots

CircleGuard had 14 security hotspots across 6 categories. All have been resolved.

---

## CSRF Disable Justification — `java:S4502`

**1. auth-service `SecurityConfig`**
CSRF protection is disabled in the Spring Security filter chain. All endpoints are stateless REST APIs authenticated via JWT Bearer tokens in the `Authorization` header; no session cookies are ever set. Without cookies, CSRF attacks are not physically possible. Resolved by adding `@SuppressWarnings("java:S4502")` with an explanatory comment.

**2. identity-service `SecurityConfig`**
Same situation as auth-service. CSRF disabled intentionally for the same JWT-stateless reason. Resolved with the same suppression annotation.

**3. promotion-service `SecurityConfig`**
Same situation as auth-service. CSRF disabled intentionally for the same JWT-stateless reason. Resolved with the same suppression annotation.

---

## SQL String Concatenation — `java:S2077`

**4. `AnalyticsService.getTimeSeries`**
The method built its SQL query by concatenating a `truncation` variable (`"day"` or `"hour"`) directly into the SQL string literal. SonarQube flags any string concatenation inside SQL, even when the values are validated. Resolved by extracting both queries as named static `final` constants (`TIMESERIES_DAILY` / `TIMESERIES_HOURLY`) and selecting between them with a ternary — no concatenation, no behavioral change.

---

## Weak Pseudorandom Number Generator — `java:S2245`

**5. `CircleService.generateUniqueInviteCode`**
Invite codes (`MESH-XXXX`) were generated using `java.util.Random`, a linear-congruential PRNG that is predictable given its output. Invite codes grant membership in exposure-alert circles, so a predictable code allows an attacker to enumerate and join any circle. Resolved by replacing `new Random()` with a class-level `new SecureRandom()` field.

**6. `AnalyticsService.generateMockTimeSeries`**
A fallback mock-data generator used `new Random(42)` with a fixed seed. Although this is only PoC/demo data, a seeded `Random` is still flagged. Resolved by replacing it with an unseeded `SecureRandom`.

---

## World-Writable Upload Directory — `java:S5443`

**7. `StorageService`**
The upload directory was hardcoded to `/tmp/circleguard-uploads`. `/tmp` is world-writable (`chmod 1777`) on Linux, meaning any local process can read, overwrite, or delete uploaded files. Additionally, `getOriginalFilename()` was passed directly to the file system without sanitization, allowing path-traversal filenames like `../../etc/cron.d/evil`. Resolved by making the directory configurable via `@Value("${storage.upload-dir:/opt/circleguard/uploads}")` (a non-world-writable path) and adding validation that rejects filenames containing `..` or `/` before resolving the target path.

---

## Wildcard CORS — `java:S5122`

All seven controllers used `@CrossOrigin(origins = "*")`, which allows any domain to issue credentialed cross-origin browser requests to these endpoints. Resolved across all controllers by replacing the wildcard with the explicit known clients: `{"http://localhost:8081", "http://localhost:8080"}`.

**8. `AnalyticsController` (dashboard-service)**
Exposes time-series and analytics endpoints.

**9. `FileUploadController` (file-service)**
Handles file upload and download operations.

**10. `AttachmentController` (form-service)**
Manages attachments linked to health survey submissions.

**11. `CertificateValidationController` (form-service)**
Handles certificate validation requests.

**12. `QuestionnaireController` (form-service)**
Exposes questionnaire CRUD endpoints.

**13. `HealthSurveyController` (form-service)**
Manages health survey submissions.

**14. `HealthStatsController` (promotion-service)**
Exposes aggregated health statistics.

---

## Additional Fix — Debug Output in Production (`java:S106`, `java:S4507`)

`LoginController` used `System.out.println`, `System.err.println`, and `e.printStackTrace()` for debug output, and logged `password.length()` — a credential detail. These are not counted as hotspots in SonarQube (they are code smells), but were fixed in the same pass: all debug output replaced with structured SLF4J `log.info` / `log.warn` / `log.error` calls via Lombok `@Slf4j`, and the password length removed from log messages.
