"""Contract tests for TransactionApiController (openapi/specs/transaction-api.yaml)."""

from oas import Spec, check

spec = Spec("transaction-api.yaml")

PRODUCT_CODE = "AX738"
ADJUSTMENT_CREDIT_TYPE_ID = "3"
ADJUSTMENT_DEBIT_TYPE_ID = "10"


def candidate_entry(client):
    main = client.location_id("Main Warehouse")
    candidates = client.get_json(
        "/api/inventories/transactionCandidates",
        params={"locationId": main,
                "product.id": client.product_id(PRODUCT_CODE)})["data"]
    return main, next(e for e in candidates if e.get("inventoryItem"))


def test_create_adjustment_roundtrip(client):
    # Credit +1 then debit 1 so the seeded quantities are restored and the
    # suite stays re-runnable.
    main, entry = candidate_entry(client)
    entry_payload = {
        "inventoryItemId": entry["inventoryItem"]["id"],
        "binLocationId": (entry.get("binLocation") or {}).get("id"),
        "quantity": 1,
    }
    resp = check(client, spec, "POST", "/api/transactions",
                 json={
                     "transactionTypeId": ADJUSTMENT_CREDIT_TYPE_ID,
                     "locationId": main,
                     "comment": "ZZ Contract transaction credit",
                     "entries": [entry_payload],
                 })
    assert resp.json()["data"]["id"]
    check(client, spec, "POST", "/api/transactions",
          json={
              "transactionTypeId": ADJUSTMENT_DEBIT_TYPE_ID,
              "locationId": main,
              "comment": "ZZ Contract transaction debit",
              "entries": [entry_payload],
          })


def test_create_missing_transaction_type(client):
    check(client, spec, "POST", "/api/transactions",
          json={"entries": [{"quantity": 1}]})


def test_create_empty_entries(client):
    check(client, spec, "POST", "/api/transactions",
          json={"transactionTypeId": ADJUSTMENT_CREDIT_TYPE_ID,
                "entries": []})
