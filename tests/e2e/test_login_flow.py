import requests
import pytest
from conftest import AUTH_URL, REQUEST_TIMEOUT


class TestLoginFlow:

    def test_valid_credentials_return_jwt_token(self):
        response = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "testuser", "password": "password123"},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code == 200
        body = response.json()
        assert "token" in body
        assert "anonymousId" in body
        assert body["type"] == "Bearer"
        assert len(body["token"]) > 50

    def test_invalid_credentials_return_401(self):
        response = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "testuser", "password": "wrong-password"},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code == 401

    def test_missing_password_returns_4xx(self):
        response = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "testuser"},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code in (400, 401)

    def test_returned_anonymous_id_is_uuid_format(self):
        import re
        response = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "testuser", "password": "password123"},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code == 200
        anonymous_id = response.json()["anonymousId"]
        uuid_pattern = r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        assert re.match(uuid_pattern, anonymous_id, re.IGNORECASE)
