"""Contract tests for UserApiController (openapi/specs/user-api.yaml)."""

import pytest

from oas import Spec, check

spec = Spec("user-api.yaml")


@pytest.fixture(scope="module", autouse=True)
def require_endpoint(client):
    # The pinned released image predates the user API; only source
    # builds of this branch expose it.
    if client.request("GET", "/api/users/list",
                      params={"max": "1"}).status_code != 200:
        pytest.skip("app build does not expose /api/users/list")


def test_list(client):
    resp = check(client, spec, "GET", "/api/users/list")
    body = resp.json()
    assert body["totalCount"] >= len(body["data"])
    assert body["data"], "seeded dataset should have users"


def test_list_paged_and_sorted(client):
    resp = check(client, spec, "GET", "/api/users/list",
                 params={"max": "5", "offset": "0",
                         "sort": "username", "order": "asc"})
    data = resp.json()["data"]
    assert len(data) <= 5
    usernames = [u["username"] for u in data]
    assert usernames == sorted(usernames)


def test_list_filtered_by_query(client):
    resp = check(client, spec, "GET", "/api/users/list",
                 params={"q": "admin", "max": "100"})
    data = resp.json()["data"]
    assert data
    for user in data:
        haystack = " ".join(
            str(user.get(key) or "")
            for key in ("username", "name", "email"))
        assert "admin" in haystack.lower()


def test_list_filtered_by_status(client):
    resp = check(client, spec, "GET", "/api/users/list",
                 params={"status": "true", "max": "100"})
    data = resp.json()["data"]
    assert data
    assert all(user["active"] for user in data)


def test_read(client):
    listed = check(client, spec, "GET", "/api/users/list",
                   params={"q": "admin", "max": "1"}).json()["data"][0]
    resp = check(client, spec, "GET", "/api/users/{id}",
                 path=f"/api/users/{listed['id']}")
    data = resp.json()["data"]
    assert data["id"] == listed["id"]
    assert data["username"] == listed["username"]
    assert isinstance(data["roles"], list)
    assert isinstance(data["locationRoles"], list)


def test_read_unknown(client):
    resp = check(client, spec, "GET", "/api/users/{id}",
                 path="/api/users/doesnotexist0000")
    assert resp.status_code == 404


def test_update_noop(client):
    listed = check(client, spec, "GET", "/api/users/list",
                   params={"q": "admin", "max": "1"}).json()["data"][0]
    before = check(client, spec, "GET", "/api/users/{id}",
                   path=f"/api/users/{listed['id']}").json()["data"]
    resp = check(client, spec, "PUT", "/api/users/{id}",
                 path=f"/api/users/{listed['id']}",
                 json={"firstName": before["firstName"],
                       "lastName": before["lastName"]})
    data = resp.json()["data"]
    assert data["firstName"] == before["firstName"]
    assert data["lastName"] == before["lastName"]


def test_update_unknown(client):
    resp = check(client, spec, "PUT", "/api/users/{id}",
                 path="/api/users/doesnotexist0000",
                 json={"firstName": "X"})
    assert resp.status_code == 404


def test_delete_unknown(client):
    resp = check(client, spec, "DELETE", "/api/users/{id}",
                 path="/api/users/doesnotexist0000")
    assert resp.status_code == 404


def test_change_password_unknown(client):
    resp = check(client, spec, "PUT", "/api/users/{id}/password",
                 path="/api/users/doesnotexist0000/password",
                 json={"password": "x", "passwordConfirm": "x"})
    assert resp.status_code == 404


def test_location_role_delete_unknown(client):
    listed = check(client, spec, "GET", "/api/users/list",
                   params={"q": "admin", "max": "1"}).json()["data"][0]
    resp = check(client, spec, "DELETE",
                 "/api/users/{id}/locationRoles/{locationRoleId}",
                 path=f"/api/users/{listed['id']}"
                      "/locationRoles/doesnotexist0000")
    assert resp.status_code == 404
