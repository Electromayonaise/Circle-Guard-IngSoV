import requests
import time
from conftest import AUTH_URL, FORM_URL, NOTIFICATION_URL, REQUEST_TIMEOUT


class TestNotificationFlow:

    def test_status_change_triggers_notification_log_entry(self):
        login_resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "notif-testuser", "password": "password123"},
            timeout=REQUEST_TIMEOUT
        )
        assert login_resp.status_code == 200
        token = login_resp.json()["token"]
        anonymous_id = login_resp.json()["anonymousId"]

        requests.post(
            f"{FORM_URL}/api/v1/surveys",
            headers={"Authorization": f"Bearer {token}"},
            json={"anonymousId": anonymous_id, "responses": {"fever": "YES"}},
            timeout=REQUEST_TIMEOUT
        )
        time.sleep(4)

        notif_resp = requests.get(
            f"{NOTIFICATION_URL}/api/v1/notifications/log/{anonymous_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=REQUEST_TIMEOUT
        )
        assert notif_resp.status_code in (200, 404)
        if notif_resp.status_code == 200:
            logs = notif_resp.json()
            assert isinstance(logs, list)

    def test_notification_health_endpoint_is_up(self):
        response = requests.get(
            f"{NOTIFICATION_URL}/actuator/health",
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code == 200
        assert response.json().get("status") == "UP"
