import requests
from conftest import AUTH_URL, PROMOTION_URL, REQUEST_TIMEOUT


class TestAdminCorrectionFlow:

    def test_admin_can_manually_correct_user_status(self, admin_headers):
        anonymous_id = "admin-correction-test-user"

        response = requests.post(
            f"{PROMOTION_URL}/api/v1/health/report",
            headers=admin_headers,
            json={"anonymousId": anonymous_id, "status": "ACTIVE", "adminOverride": True},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code in (200, 202, 204)

    def test_non_admin_cannot_correct_status(self, auth_headers):
        response = requests.post(
            f"{PROMOTION_URL}/api/v1/health/report",
            headers=auth_headers,
            json={"anonymousId": "some-user", "status": "ACTIVE"},
            timeout=REQUEST_TIMEOUT
        )
        assert response.status_code in (401, 403)
