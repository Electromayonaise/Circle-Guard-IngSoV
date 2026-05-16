import pytest
import requests
import os

BASE_URL = os.getenv("BASE_URL", "http://localhost")
AUTH_PORT = os.getenv("AUTH_PORT", "8180")
FORM_PORT = os.getenv("FORM_PORT", "8086")
GATEWAY_PORT = os.getenv("GATEWAY_PORT", "8087")
PROMOTION_PORT = os.getenv("PROMOTION_PORT", "8088")
NOTIFICATION_PORT = os.getenv("NOTIFICATION_PORT", "8082")

AUTH_URL = f"{BASE_URL}:{AUTH_PORT}"
FORM_URL = f"{BASE_URL}:{FORM_PORT}"
GATEWAY_URL = f"{BASE_URL}:{GATEWAY_PORT}"
PROMOTION_URL = f"{BASE_URL}:{PROMOTION_PORT}"
NOTIFICATION_URL = f"{BASE_URL}:{NOTIFICATION_PORT}"

TEST_USERNAME = os.getenv("TEST_USERNAME", "testuser")
TEST_PASSWORD = os.getenv("TEST_PASSWORD", "password123")
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "admin123")

REQUEST_TIMEOUT = int(os.getenv("REQUEST_TIMEOUT", "30"))


@pytest.fixture(scope="session")
def user_token():
    response = requests.post(
        f"{AUTH_URL}/api/v1/auth/login",
        json={"username": TEST_USERNAME, "password": TEST_PASSWORD},
        timeout=REQUEST_TIMEOUT
    )
    assert response.status_code == 200, f"Login failed: {response.text}"
    data = response.json()
    return data["token"], data["anonymousId"]


@pytest.fixture(scope="session")
def admin_token():
    response = requests.post(
        f"{AUTH_URL}/api/v1/auth/login",
        json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD},
        timeout=REQUEST_TIMEOUT
    )
    assert response.status_code == 200, f"Admin login failed: {response.text}"
    data = response.json()
    return data["token"], data["anonymousId"]


@pytest.fixture
def auth_headers(user_token):
    token, _ = user_token
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def admin_headers(admin_token):
    token, _ = admin_token
    return {"Authorization": f"Bearer {token}"}
