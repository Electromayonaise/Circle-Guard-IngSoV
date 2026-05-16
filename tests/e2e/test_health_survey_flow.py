import requests
import pytest
from conftest import AUTH_URL, FORM_URL, REQUEST_TIMEOUT


class TestHealthSurveyFlow:

    def _get_token_and_id(self):
        resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "testuser", "password": "password123"},
            timeout=REQUEST_TIMEOUT
        )
        data = resp.json()
        return data["token"], data["anonymousId"]

    def test_authenticated_user_can_submit_health_survey(self):
        token, anonymous_id = self._get_token_and_id()

        response = requests.post(
            f"{FORM_URL}/api/v1/surveys",
            headers={"Authorization": f"Bearer {token}"},
            json={
                "anonymousId": anonymous_id,
                "responses": {"fever": "NO", "cough": "NO", "shortness_of_breath": "NO"}
            },
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code in (200, 201, 202)

    def test_unauthenticated_survey_submission_returns_401(self):
        response = requests.post(
            f"{FORM_URL}/api/v1/surveys",
            json={"anonymousId": "some-id", "responses": {}},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code in (400, 401)

    def test_survey_with_symptoms_returns_success(self):
        token, anonymous_id = self._get_token_and_id()

        response = requests.post(
            f"{FORM_URL}/api/v1/surveys",
            headers={"Authorization": f"Bearer {token}"},
            json={
                "anonymousId": anonymous_id,
                "responses": {"fever": "YES", "cough": "YES"}
            },
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code in (200, 201, 202)
