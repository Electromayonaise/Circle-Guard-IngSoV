import requests
import pytest
import time
from conftest import AUTH_URL, GATEWAY_URL, REQUEST_TIMEOUT


class TestCampusEntryFlow:

    def test_valid_qr_token_grants_campus_entry(self):
        login_resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "testuser", "password": "password123"},
            timeout=REQUEST_TIMEOUT
        )
        assert login_resp.status_code == 200
        jwt_token = login_resp.json()["token"]

        qr_resp = requests.get(
            f"{AUTH_URL}/api/v1/auth/qr/generate",
            headers={"Authorization": f"Bearer {jwt_token}"},
            timeout=REQUEST_TIMEOUT
        )
        assert qr_resp.status_code == 200
        qr_token = qr_resp.json()["qrToken"]

        gate_resp = requests.post(
            f"{GATEWAY_URL}/api/v1/gate/validate",
            json={"token": qr_token},
            timeout=REQUEST_TIMEOUT
        )
        assert gate_resp.status_code == 200
        body = gate_resp.json()
        assert body["valid"] is True
        assert body["status"] in ("GREEN", "YELLOW")

    def test_missing_qr_token_returns_400(self):
        response = requests.post(
            f"{GATEWAY_URL}/api/v1/gate/validate",
            json={},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code in (400, 422)

    def test_malformed_qr_token_denies_entry(self):
        response = requests.post(
            f"{GATEWAY_URL}/api/v1/gate/validate",
            json={"token": "not-a-valid-jwt-token"},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code == 200
        body = response.json()
        assert body["valid"] is False
