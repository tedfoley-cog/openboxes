"""Contract tests for ReportApiController (openapi/specs/report-api.yaml)."""

import pytest

from oas import Spec, check

spec = Spec("report-api.yaml")


@pytest.fixture(autouse=True)
def _require_report_api(client):
    # The Batch 38 report endpoints only exist in source builds; the pinned
    # baseline image responds 404 for them.
    if client.request("GET", "/api/reports/on-order-summary").status_code == 404:
        pytest.skip("report API endpoints not present in this build")


def _location_id(client, name="Main Warehouse"):
    return client.location_id(name)


def test_on_order_summary(client):
    resp = check(client, spec, "GET", "/api/reports/on-order-summary")
    assert isinstance(resp.json()["data"], list)


def test_on_order_details(client):
    resp = check(client, spec, "GET", "/api/reports/on-order-details")
    assert isinstance(resp.json()["data"], list)


def test_request_details(client):
    origin = _location_id(client)
    resp = check(client, spec, "GET", "/api/reports/request-details",
                 params={"originId": origin,
                         "startDate": "01/01/2000",
                         "endDate": "01/01/2050"})
    assert isinstance(resp.json()["data"], list)


def test_request_details_missing_params(client):
    resp = check(client, spec, "GET", "/api/reports/request-details")
    assert resp.status_code == 400
    assert "required" in resp.json()["errorMessage"]


def test_request_reason_codes(client):
    resp = check(client, spec, "GET", "/api/reports/request-reason-codes")
    data = resp.json()["data"]
    assert data
    assert all("id" in rc and "name" in rc for rc in data)


def test_inventory_by_location(client):
    origin = _location_id(client)
    resp = check(client, spec, "GET", "/api/reports/inventory-by-location",
                 params={"locations": origin})
    body = resp.json()
    assert body["locations"] == [{"id": origin, "name": "Main Warehouse"}]
    assert isinstance(body["data"], list)


def test_inventory_by_location_no_locations(client):
    resp = check(client, spec, "GET", "/api/reports/inventory-by-location")
    body = resp.json()
    assert body["locations"] == []
    assert body["data"] == []


def test_packing_list_shipments(client):
    resp = check(client, spec, "GET", "/api/reports/packing-list-shipments")
    assert isinstance(resp.json()["data"], list)


def test_packing_list_unknown_shipment(client):
    resp = check(client, spec, "GET", "/api/reports/packing-list",
                 params={"shipmentId": "bogus-shipment-id"})
    assert resp.json()["data"] is None


def test_packing_list(client):
    shipments = check(client, spec, "GET",
                      "/api/reports/packing-list-shipments").json()["data"]
    if not shipments:
        pytest.skip("no seeded shipments destined for the current location")
    resp = check(client, spec, "GET", "/api/reports/packing-list",
                 params={"shipmentId": shipments[0]["id"]})
    data = resp.json()["data"]
    assert data["shipment"]["id"] == shipments[0]["id"]
    assert isinstance(data["containers"], list)


def test_transaction_report(client):
    location = _location_id(client)
    resp = check(client, spec, "GET", "/api/reports/transaction-report",
                 params={"locationId": location,
                         "startDate": "01/01/2000",
                         "endDate": "01/01/2020"})
    assert isinstance(resp.json()["data"], list)


def test_transaction_report_missing_params(client):
    resp = check(client, spec, "GET", "/api/reports/transaction-report")
    assert resp.status_code == 400
    assert "required" in resp.json()["errorMessage"]


def test_transaction_report_invalid_date_range(client):
    location = _location_id(client)
    resp = check(client, spec, "GET", "/api/reports/transaction-report",
                 params={"locationId": location,
                         "startDate": "01/01/2020",
                         "endDate": "01/01/2000"})
    assert resp.status_code == 400
    assert "Start date" in resp.json()["errorMessage"]


def test_transaction_report_metadata(client):
    location = _location_id(client)
    resp = check(client, spec, "GET", "/api/reports/transaction-report-metadata",
                 params={"locationId": location})
    data = resp.json()["data"]
    assert isinstance(data["productCount"], (int, float))
    assert isinstance(data["transactionCount"], (int, float))


def test_transaction_report_details(client):
    location = _location_id(client)
    rows = check(client, spec, "GET", "/api/reports/transaction-report",
                 params={"locationId": location,
                         "startDate": "01/01/2000",
                         "endDate": "01/01/2020"}).json()["data"]
    if not rows:
        pytest.skip("no seeded transactions in the date range")
    resp = check(client, spec, "GET", "/api/reports/transaction-report-details",
                 params={"productCode": rows[0]["productCode"],
                         "locationId": location,
                         "startDate": "01/01/2000",
                         "endDate": "01/01/2020"})
    data = resp.json()["data"]
    assert data[0]["transactionCode"] == "BALANCE_OPENING"
    assert data[-1]["transactionCode"] == "BALANCE_CLOSING"


def test_transaction_report_details_missing_params(client):
    resp = check(client, spec, "GET", "/api/reports/transaction-report-details")
    assert resp.status_code == 400
    assert "required" in resp.json()["errorMessage"]
