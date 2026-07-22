"""Contract tests for InventoryApiController (openapi/specs/inventory-api.yaml)."""

import pytest

from oas import Spec, check

spec = Spec("inventory-api.yaml")


@pytest.fixture(scope="module")
def batch1(client):
    # The browse/transactionCandidates/binLocationDetails/adjustStock actions
    # ship with the Phase 2 batch 1 migration; skip their tests until the
    # pinned baseline image (OB_VERSION) is bumped to a build that has them.
    if client.request("GET", "/api/inventories/browse").status_code == 404:
        pytest.skip("batch 1 inventory API not deployed in the pinned baseline image")


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


PRODUCT_CODE = "AX738"


def category_id(client, name):
    matches = [c for c in client.get_json("/api/categories")["data"]
               if c.get("name") == name]
    assert matches, f"Category not found in seeded data: {name}"
    return matches[0]["id"]


def test_browse_default_is_root_category(client, batch1):
    # Like the legacy screen, browsing without filters defaults to the ROOT
    # category, which has no direct products in the seeded dataset.
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET", "/api/inventories/browse",
                 params={"locationId": main, "max": 5})
    body = resp.json()
    assert body["totalCount"] == 0


def test_browse_category(client, batch1):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET", "/api/inventories/browse",
                 params={"locationId": main,
                         "categoryId": category_id(client, "ARVS"),
                         "max": 5})
    body = resp.json()
    assert body["data"], "seeded ARVS category should have browse rows"
    assert body["totalCount"] >= len(body["data"])


def test_browse_search(client, batch1):
    # PRODUCT_CODE is seeded under IT Equipment; the search is scoped to a
    # category because the default ROOT category matches nothing.
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET", "/api/inventories/browse",
                 params={"locationId": main,
                         "categoryId": category_id(client, "IT Equipment"),
                         "searchTerms": PRODUCT_CODE})
    codes = [row["productCode"] for row in resp.json()["data"]]
    assert PRODUCT_CODE in codes


def test_transaction_candidates(client, batch1):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET", "/api/inventories/transactionCandidates",
                 params={"locationId": main,
                         "product.id": client.product_id(PRODUCT_CODE)})
    body = resp.json()
    assert body["data"], "seeded product should have candidate rows"
    assert body["totalCount"] == len(body["data"])


def test_transaction_candidates_missing_products(client, batch1):
    check(client, spec, "GET", "/api/inventories/transactionCandidates")


def test_bin_location_details(client, batch1):
    main = client.location_id("Main Warehouse")
    candidates = client.get_json(
        "/api/inventories/transactionCandidates",
        params={"locationId": main,
                "product.id": client.product_id(PRODUCT_CODE)})["data"]
    entry = candidates[0]
    resp = check(client, spec, "GET", "/api/inventories/binLocationDetails",
                 params={"locationId": main,
                         "productCode": PRODUCT_CODE,
                         "binLocation": (entry.get("binLocation") or {}).get("name") or "",
                         "lotNumber": (entry.get("inventoryItem") or {}).get("lotNumber") or ""})
    assert resp.json()["data"]["product"]["productCode"] == PRODUCT_CODE


def test_adjust_stock_roundtrip(client, batch1):
    # Adjust up by 1 and back down so the suite stays re-runnable.
    main = client.location_id("Main Warehouse")
    candidates = client.get_json(
        "/api/inventories/transactionCandidates",
        params={"locationId": main,
                "product.id": client.product_id(PRODUCT_CODE)})["data"]
    entry = next(e for e in candidates if e.get("inventoryItem"))
    payload = {
        "locationId": main,
        "inventoryItemId": entry["inventoryItem"]["id"],
        "binLocationId": (entry.get("binLocation") or {}).get("id"),
        "currentQuantity": entry["quantityOnHand"],
        "reasonCode": "CORRECTION",
        "comment": "ZZ Contract adjust stock",
    }
    check(client, spec, "POST", "/api/inventories/adjustStock",
          json={**payload, "newQuantity": entry["quantityOnHand"] + 1})
    check(client, spec, "POST", "/api/inventories/adjustStock",
          json={**payload,
                "currentQuantity": entry["quantityOnHand"] + 1,
                "newQuantity": entry["quantityOnHand"]})


def test_adjust_stock_unchanged_quantity(client, batch1):
    main = client.location_id("Main Warehouse")
    candidates = client.get_json(
        "/api/inventories/transactionCandidates",
        params={"locationId": main,
                "product.id": client.product_id(PRODUCT_CODE)})["data"]
    entry = next(e for e in candidates if e.get("inventoryItem"))
    check(client, spec, "POST", "/api/inventories/adjustStock",
          json={
              "locationId": main,
              "inventoryItemId": entry["inventoryItem"]["id"],
              "binLocationId": (entry.get("binLocation") or {}).get("id"),
              "currentQuantity": entry["quantityOnHand"],
              "newQuantity": entry["quantityOnHand"],
              "reasonCode": "CORRECTION",
              "comment": "ZZ Contract adjust stock noop",
          })


def test_import_csv_empty_body(client):
    # A successful import would mutate the seeded inventory, so only the
    # empty-body error branch is exercised (IllegalArgumentException -> 500).
    main = client.location_id("Main Warehouse")
    check(client, spec, "POST",
          "/api/facilities/{facilityId}/inventories/import",
          path=f"/api/facilities/{main}/inventories/import",
          data=b"", headers={"Content-Type": "text/csv"})
