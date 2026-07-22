"""Contract tests for InventoryApiController (openapi/specs/inventory-api.yaml)."""

from oas import Spec, check

spec = Spec("inventory-api.yaml")

DATE_RANGE = {
    "startDate": "01/01/2010 00:00:00 Z",
    "endDate": "01/01/2011 00:00:00 Z",
}


def test_reorder_report(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET",
                 "/api/facilities/{facilityId}/inventories/reorderReport",
                 path=f"/api/facilities/{main}/inventories/reorderReport")
    assert resp.json()["data"], "seeded Main Warehouse should have reorder rows"


def test_reorder_report_csv(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET",
                 "/api/facilities/{facilityId}/inventories/reorderReport",
                 path=f"/api/facilities/{main}/inventories/reorderReport",
                 params={"format": "csv"})
    assert "attachment" in resp.headers.get("Content-Disposition", "")


def test_expiration_history_report(client):
    check(client, spec, "GET", "/api/inventories/expirationHistoryReport",
          params=DATE_RANGE)


def test_expiration_history_report_csv(client):
    check(client, spec, "GET", "/api/inventories/expirationHistoryReport",
          params={**DATE_RANGE, "format": "csv"})


def test_expiration_history_report_missing_dates(client):
    check(client, spec, "GET", "/api/inventories/expirationHistoryReport")


def test_inventory_summary(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET",
                 "/api/facilities/{facilityId}/inventories/summary",
                 path=f"/api/facilities/{main}/inventories/summary")
    body = resp.json()
    assert body["data"], "seeded Main Warehouse should have inventory rows"
    assert body["totalCount"] == len(body["data"])


def test_inventory_summary_low_stock(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET",
                 "/api/facilities/{facilityId}/inventories/summary",
                 path=f"/api/facilities/{main}/inventories/summary",
                 params={"status": "lowStock"})
    all_rows = client.get_json(
        f"/api/facilities/{main}/inventories/summary")["data"]
    assert len(resp.json()["data"]) <= len(all_rows)


def test_expired_stock(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET",
                 "/api/facilities/{facilityId}/inventories/expiredStock",
                 path=f"/api/facilities/{main}/inventories/expiredStock")
    data = resp.json()["data"]
    assert data["totalCount"] == len(data["items"])


def test_expiring_stock(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET",
                 "/api/facilities/{facilityId}/inventories/expiringStock",
                 path=f"/api/facilities/{main}/inventories/expiringStock")
    data = resp.json()["data"]
    assert data["totalCount"] == len(data["items"])


def test_expiring_stock_status_filter(client):
    main = client.location_id("Main Warehouse")
    check(client, spec, "GET",
          "/api/facilities/{facilityId}/inventories/expiringStock",
          path=f"/api/facilities/{main}/inventories/expiringStock",
          params={"status": "within30Days"})


def test_import_csv_empty_body(client):
    # A successful import would mutate the seeded inventory, so only the
    # empty-body error branch is exercised (IllegalArgumentException -> 500).
    main = client.location_id("Main Warehouse")
    check(client, spec, "POST",
          "/api/facilities/{facilityId}/inventories/import",
          path=f"/api/facilities/{main}/inventories/import",
          data=b"", headers={"Content-Type": "text/csv"})
