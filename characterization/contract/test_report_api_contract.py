"""Contract tests for ReportApiController (openapi/specs/report-api.yaml)
and DataExportApiController (openapi/specs/data-export-api.yaml),
added in Phase 2 Batch 37 for the React report/dataExport screens.
"""

import pytest

from oas import Spec, check

report_spec = Spec("report-api.yaml")
data_export_spec = Spec("data-export-api.yaml")


@pytest.fixture(scope="module", autouse=True)
def batch37_endpoints(client):
    # The api-snapshot job runs against the pinned released image, which
    # predates the Batch 37 report endpoints. Skip there; these run against
    # source builds (and locally per RUNNING_LOCALLY.md).
    if client.request("GET", "/api/dataExports").status_code == 404:
        pytest.skip("Batch 37 report endpoints not present in target build")


def _seeded_shipment_id(client):
    data = client.get_json("/api/generic/shipment")["data"]
    if not data:
        pytest.skip("no seeded shipments")
    return data[0]["id"]


def test_bin_location_report(client):
    resp = check(client, report_spec, "GET", "/api/reports/binLocationReport")
    body = resp.json()
    assert body["location"]["id"]
    assert isinstance(body["data"], list)


def test_bin_location_report_status_filter(client):
    resp = check(client, report_spec, "GET", "/api/reports/binLocationReport",
                 params={"status": "inStock"})
    unfiltered = client.get_json("/api/reports/binLocationReport")
    assert len(resp.json()["data"]) <= len(unfiltered["data"])


def test_bin_location_report_unknown_location(client):
    check(client, report_spec, "GET", "/api/reports/binLocationReport",
          params={"location.id": "doesnotexist0000"})


def test_cycle_count_report(client):
    resp = check(client, report_spec, "GET", "/api/reports/cycleCountReport")
    body = resp.json()
    assert body["location"]["id"]
    for row in body["data"]:
        assert "quantityOnHand" in row


def test_shipping_report(client):
    sid = _seeded_shipment_id(client)
    resp = check(client, report_spec, "GET", "/api/reports/shippingReport/{id}",
                 path=f"/api/reports/shippingReport/{sid}")
    data = resp.json()["data"]
    assert data["shipment"]["id"] == sid
    assert isinstance(data["entries"], list)


def test_shipping_report_unknown(client):
    check(client, report_spec, "GET", "/api/reports/shippingReport/{id}",
          path="/api/reports/shippingReport/doesnotexist0000")


def test_data_exports(client):
    resp = check(client, data_export_spec, "GET", "/api/dataExports")
    assert isinstance(resp.json()["data"], list)
