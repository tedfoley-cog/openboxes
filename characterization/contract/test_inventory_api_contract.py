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


PRODUCT_CODE = "AX738"


def test_browse(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET", "/api/inventories/browse",
                 params={"locationId": main, "max": 5})
    body = resp.json()
    assert body["data"], "seeded Main Warehouse should have browse rows"
    assert body["totalCount"] >= len(body["data"])


def test_browse_search(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET", "/api/inventories/browse",
                 params={"locationId": main, "searchTerms": PRODUCT_CODE})
    codes = [row["productCode"] for row in resp.json()["data"]]
    assert PRODUCT_CODE in codes


def test_transaction_candidates(client):
    main = client.location_id("Main Warehouse")
    resp = check(client, spec, "GET", "/api/inventories/transactionCandidates",
                 params={"locationId": main,
                         "product.id": client.product_id(PRODUCT_CODE)})
    body = resp.json()
    assert body["data"], "seeded product should have candidate rows"
    assert body["totalCount"] == len(body["data"])


def test_transaction_candidates_missing_products(client):
    check(client, spec, "GET", "/api/inventories/transactionCandidates")


def test_bin_location_details(client):
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


def test_adjust_stock_roundtrip(client):
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


def test_adjust_stock_unchanged_quantity(client):
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
