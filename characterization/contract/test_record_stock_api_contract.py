"""Contract tests for RecordStockApiController (record-stock-api.yaml).

Recording stock creates baseline + adjustment transactions that cannot be
deleted over the API, so the happy path re-records the product's CURRENT
stock rows (same bins/lots/quantities, read from /api/products/availableItems)
with a fresh transactionDate, leaving product availability unchanged.
"""

from datetime import datetime, timedelta, timezone

from oas import Spec, check

spec = Spec("record-stock-api.yaml")

# In the seeded demo dataset, Main Warehouse (location id "1") owns
# Inventory id "1"; the inventory id is not otherwise discoverable via API.
MAIN_WAREHOUSE_INVENTORY_ID = "1"


def _transaction_date():
    return (datetime.now(timezone.utc) - timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ")


def test_save_record_stock(client):
    main = client.location_id("Main Warehouse")
    product = client.product("GM080")
    current = client.get_json(
        "/api/products/availableItems",
        params={"location.id": main, "product.id": product["id"]},
    )["data"]
    assert current, "seeded demo data should have GM080 stock in Main Warehouse"
    rows = [
        {
            "id": item["inventoryItem.id"],
            "binLocation": {"id": item["binLocation.id"]} if item.get("binLocation.id") else None,
            "lotNumber": item.get("lotNumber") or "",
            "expirationDate": item.get("expirationDate"),
            "oldQuantity": item["quantityOnHand"],
            "newQuantity": item["quantityOnHand"],
            "comment": "",
        }
        for item in current
    ]
    resp = check(
        client, spec, "POST", "/api/facilities/{facility}/inventory/record-stock/save",
        path=f"/api/facilities/{main}/inventory/record-stock/save",
        json={
            "product": {"id": product["id"]},
            "inventory": {"id": MAIN_WAREHOUSE_INVENTORY_ID},
            "transactionDate": _transaction_date(),
            "comment": "ZZ Contract record stock",
            "recordInventoryRows": rows,
        },
    )
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["product"]["id"] == product["id"]
    assert data["comment"] == "ZZ Contract record stock"


def test_save_record_stock_without_inventory_is_validation_error(client):
    main = client.location_id("Main Warehouse")
    product = client.product("GM080")
    resp = check(
        client, spec, "POST", "/api/facilities/{facility}/inventory/record-stock/save",
        path=f"/api/facilities/{main}/inventory/record-stock/save",
        json={
            "product": {"id": product["id"]},
            "transactionDate": _transaction_date(),
            "recordInventoryRows": [],
        },
    )
    assert resp.status_code == 400
    assert "warehouse" in resp.json()["errorMessage"]


def test_save_record_stock_expiry_without_lot_is_validation_error(client):
    main = client.location_id("Main Warehouse")
    product = client.product("GM080")
    resp = check(
        client, spec, "POST", "/api/facilities/{facility}/inventory/record-stock/save",
        path=f"/api/facilities/{main}/inventory/record-stock/save",
        json={
            "product": {"id": product["id"]},
            "inventory": {"id": MAIN_WAREHOUSE_INVENTORY_ID},
            "transactionDate": _transaction_date(),
            "recordInventoryRows": [
                {
                    "lotNumber": "",
                    "expirationDate": "2030-01-01T00:00:00Z",
                    "oldQuantity": 50,
                    "newQuantity": 50,
                },
            ],
        },
    )
    assert resp.status_code == 400
    assert "lot number" in resp.json()["errorMessage"]
