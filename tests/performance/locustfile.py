import random
import uuid
from locust import HttpUser, task, between, events
from locust.runners import MasterRunner


AUTH_URL = "http://localhost:8180"
FORM_URL = "http://localhost:8086"
GATEWAY_URL = "http://localhost:8087"
PROMOTION_URL = "http://localhost:8088"

TEST_USERS = [
    {"username": "symptom-testuser", "password": "password123"},
    {"username": "healthy-testuser", "password": "password123"},
    {"username": "testuser", "password": "password123"},
]


class CircleGuardUser(HttpUser):
    """Simulates a regular campus user: login, check status, submit survey, validate QR."""

    host = "http://localhost:8180"
    wait_time = between(1, 3)
    token = None
    anonymous_id = None

    def on_start(self):
        """Login once at the start of each simulated user session."""
        user = random.choice(TEST_USERS)
        with self.client.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": user["username"], "password": user["password"]},
            catch_response=True,
            name="[Auth] Login"
        ) as resp:
            if resp.status_code == 200:
                data = resp.json()
                self.token = data.get("token")
                self.anonymous_id = data.get("anonymousId")
                resp.success()
            else:
                resp.failure(f"Login failed with {resp.status_code}")

    def _headers(self):
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    @task(4)
    def check_health_status(self):
        """Most frequent: user checks their health status."""
        if not self.anonymous_id:
            return
        with self.client.get(
            f"{PROMOTION_URL}/api/v1/health/status/{self.anonymous_id}",
            headers=self._headers(),
            catch_response=True,
            name="[Promotion] Check Status"
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()
            else:
                resp.failure(f"Status check failed: {resp.status_code}")

    @task(3)
    def submit_health_survey(self):
        """Submit daily health survey."""
        if not self.anonymous_id:
            return
        with self.client.post(
            f"{FORM_URL}/api/v1/surveys",
            headers=self._headers(),
            json={
                "anonymousId": self.anonymous_id,
                "responses": {
                    "fever": random.choice(["YES", "NO"]),
                    "cough": random.choice(["YES", "NO"]),
                    "shortness_of_breath": "NO"
                }
            },
            catch_response=True,
            name="[Form] Submit Survey"
        ) as resp:
            if resp.status_code in (200, 201, 202):
                resp.success()
            else:
                resp.failure(f"Survey failed: {resp.status_code}")

    @task(2)
    def validate_qr_at_gate(self):
        """Validate QR token at campus gate."""
        if not self.token:
            return
        qr_token = None
        with self.client.get(
            f"{AUTH_URL}/api/v1/auth/qr/generate",
            headers=self._headers(),
            catch_response=True,
            name="[Auth] Generate QR Token"
        ) as qr_resp:
            if qr_resp.status_code == 200:
                qr_token = qr_resp.json().get("qrToken")
                qr_resp.success()
            else:
                qr_resp.failure(f"QR generation failed: {qr_resp.status_code}")
                return

        with self.client.post(
            f"{GATEWAY_URL}/api/v1/gate/validate",
            json={"token": qr_token},
            catch_response=True,
            name="[Gateway] Validate QR"
        ) as gate_resp:
            if gate_resp.status_code == 200:
                gate_resp.success()
            else:
                gate_resp.failure(f"Gate validation failed: {gate_resp.status_code}")
