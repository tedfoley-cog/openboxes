"""Contract tests for MigrationApiController (openapi/specs/migration-api.yaml)."""

import pytest

from oas import Spec, check

spec = Spec("migration-api.yaml")


@pytest.fixture(scope="module", autouse=True)
def require_endpoint(client):
    # The pinned released image predates the Batch 45 migration endpoints;
    # only source builds of this branch expose them.
    if client.request("GET", "/api/migration/materializedViews").status_code != 200:
        pytest.skip("app build does not expose /api/migration/materializedViews")


def test_materialized_view_counts(client):
    resp = check(client, spec, "GET", "/api/migration/materializedViews")
    data = resp.json()["data"]
    assert data["productDemandCount"] >= 0
    assert data["productAvailabilityCount"] >= 0


def test_product_availability_by_depot(client):
    resp = check(client, spec, "GET", "/api/migration/productAvailability")
    data = resp.json()["data"]
    assert data, "demo data should have depot locations"
    assert all(row["locationId"] and row["locationName"] for row in data)
    # Sorted by count ascending, nulls first (matching the legacy screen)
    counts = [row.get("productAvailabilityCount") for row in data]
    non_null = [c for c in counts if c is not None]
    assert non_null == sorted(non_null)


def test_product_availability_count_for_location(client):
    rows = client.get_json("/api/migration/productAvailability")["data"]
    row = max(rows, key=lambda r: r.get("productAvailabilityCount") or 0)
    resp = check(client, spec, "GET", "/api/migration/productAvailability/count",
                 params={"locationId": row["locationId"]})
    data = resp.json()["data"]
    assert data["locationId"] == row["locationId"]
    assert data["count"] == row["productAvailabilityCount"]


def test_product_availability_count_unknown_location(client):
    resp = check(client, spec, "GET", "/api/migration/productAvailability/count",
                 params={"locationId": "no-such-location"})
    assert resp.status_code == 404


def test_calculate_product_availability_unknown_location(client):
    resp = check(client, spec, "GET", "/api/migration/productAvailability/calculate",
                 params={"locationId": "no-such-location"})
    assert resp.status_code == 404


def test_refresh_product_availability_unknown_location(client):
    resp = check(client, spec, "POST", "/api/migration/productAvailability/refresh",
                 params={"locationId": "no-such-location"})
    assert resp.status_code == 404


def test_refresh_product_availability_for_location(client):
    rows = client.get_json("/api/migration/productAvailability")["data"]
    row = max(rows, key=lambda r: r.get("productAvailabilityCount") or 0)
    resp = check(client, spec, "POST", "/api/migration/productAvailability/refresh",
                 params={"locationId": row["locationId"]})
    assert row["locationName"] in resp.json()["data"]
    # The refresh rebuilds the location's rows (count reflects current stock,
    # which earlier suites may have mutated, so only pin the shape)
    after = client.get_json("/api/migration/productAvailability/count",
                            params={"locationId": row["locationId"]})["data"]
    assert isinstance(after["count"], int) and after["count"] > 0
