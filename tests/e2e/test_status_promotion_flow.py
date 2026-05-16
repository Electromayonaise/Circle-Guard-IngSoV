import requests
import time
from conftest import AUTH_URL, FORM_URL, PROMOTION_URL, REQUEST_TIMEOUT


class TestStatusPromotionFlow:

    def test_survey_with_symptoms_promotes_status_to_suspect(self):
        login_resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "symptom-testuser", "password": "password123"},
            timeout=REQUEST_TIMEOUT
        )
        assert login_resp.status_code == 200
        token = login_resp.json()["token"]
        anonymous_id = login_resp.json()["anonymousId"]

        survey_resp = requests.post(
            f"{FORM_URL}/api/v1/surveys",
            headers={"Authorization": f"Bearer {token}"},
            json={"anonymousId": anonymous_id, "responses": {"fever": "YES", "cough": "YES"}},
            timeout=REQUEST_TIMEOUT
        )
        assert survey_resp.status_code in (200, 201, 202)

        time.sleep(20)

        status_resp = requests.get(
            f"{PROMOTION_URL}/api/v1/health/status/{anonymous_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=REQUEST_TIMEOUT
        )
        assert status_resp.status_code == 200
        assert status_resp.json().get("status") == "SUSPECT"

    def test_healthy_survey_does_not_change_status(self):
        login_resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "healthy-testuser", "password": "password123"},
            timeout=REQUEST_TIMEOUT
        )
        assert login_resp.status_code == 200
        token = login_resp.json()["token"]
        anonymous_id = login_resp.json()["anonymousId"]

        requests.post(
            f"{FORM_URL}/api/v1/surveys",
            headers={"Authorization": f"Bearer {token}"},
            json={"anonymousId": anonymous_id, "responses": {"fever": "NO", "cough": "NO"}},
            timeout=REQUEST_TIMEOUT
        )

        time.sleep(10)

        status_resp = requests.get(
            f"{PROMOTION_URL}/api/v1/health/status/{anonymous_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=REQUEST_TIMEOUT
        )
        assert status_resp.status_code == 200
        assert status_resp.json().get("status") in ("ACTIVE", "CLEAR", None)
