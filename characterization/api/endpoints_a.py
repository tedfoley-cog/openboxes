"""
Endpoint plan for Phase 0.3a — API controllers A-L.

Each plan entry: name (snapshot file name), method, path (str or callable
resolved at run time), optional json body, optional expect_status, optional
capture(status, body) hook used by CRUD flows to grab created ids.

Dedicated, deterministically-named test records are used for the write
flows (category / location group / organization CRUD); leftovers from a
previous aborted run are cleaned up before the flow starts so re-runs stay
deterministic.

See docs/migration/API_SNAPSHOT_COVERAGE_A.md for the coverage table and
the reasons endpoints are skipped.
"""

import json
import re
import urllib.parse


class Context:
    """Holds ids resolved at run time and masks them back out of recorded paths."""

    def __init__(self):
        self.ids = {}       # token -> concrete id

    def set(self, token, value):
        self.ids[token] = value

    def get(self, token):
        return self.ids[token]

    ID_IN_PATH = re.compile(r"[0-9a-f]{16,}")

    def mask_path(self, path):
        # entity ids are runtime-generated hex UUIDs; mask them generically
        # (ids can share long prefixes, so token substitution is unsafe)
        return self.ID_IN_PATH.sub("{id}", path)


def _data(body):
    try:
        return json.loads(body.decode("utf-8", errors="replace"))
    except ValueError:
        return {}


def build_plan(client, base_url, username, password):
    ctx = Context()

    # --- authenticate & resolve seed data --------------------------------
    status, _, body = client.request(
        "POST", "/api/login", json_body={"username": username, "password": password}
    )
    if status != 200:
        raise SystemExit("Login failed (HTTP %s) against %s" % (status, base_url))

    locations = client.get_json("/api/locations")["data"]
    main = next(l for l in locations if l["name"] == "Main Warehouse")
    boston = next((l for l in locations if l["name"] == "Boston Warehouse"), main)
    ctx.set("mainWarehouseId", main["id"])
    ctx.set("bostonWarehouseId", boston["id"])

    # set session warehouse (required by session-scoped endpoints)
    client.request("GET", "/api/chooseLocation/%s" % main["id"])

    bins = client.get_json("/api/binLocations")["data"]
    ctx.set("binLocationId", bins[0]["id"] if bins else None)

    categories = client.get_json("/api/categories")["data"]
    root = next((c for c in categories if c.get("name") == "ROOT"), categories[0])
    ctx.set("rootCategoryId", root["id"])

    organizations = client.get_json("/api/organizations")["data"]
    ctx.set("organizationId", organizations[0]["id"] if organizations else None)

    location_groups = client.get_json("/api/locationGroups")["data"]
    ctx.set("locationGroupId", location_groups[0]["id"] if location_groups else None)

    # cleanup leftovers from any previous aborted run
    _cleanup(client, ctx)

    main_id = ctx.get("mainWarehouseId")

    plan = []

    def ep(name, path, method="GET", **kw):
        entry = {"name": name, "path": path, "method": method}
        entry.update(kw)
        plan.append(entry)

    # --- ApiController ----------------------------------------------------
    ep("api__login", "/api/login", "POST",
       json={"username": username, "password": password})
    ep("api__status", "/api/status")
    ep("api__chooseLocation", "/api/chooseLocation/%s" % main_id)
    ep("api__chooseLocale", "/api/chooseLocale/en")
    ep("api__getMenuConfig", "/api/getMenuConfig")
    ep("api__getAppContext", "/api/getAppContext")
    ep("api__getRequestTypes", "/api/getRequestTypes")
    ep("api__supportLinks", "/api/supportLinks")
    ep("api__resettingInstance_command", "/api/resettingInstance/command")
    # NOTE: /api/logout is exercised last (see bottom of plan)

    # --- AttributeApiController -------------------------------------------
    ep("attribute__list", "/api/attributes")
    ep("attribute__list_product_type", "/api/attributes?entityType=PRODUCT")

    # --- BinLocationApiController ------------------------------------------
    ep("binLocation__list", "/api/binLocations")
    if ctx.get("binLocationId"):
        ep("binLocation__read", "/api/binLocations/%s" % ctx.get("binLocationId"))

    # --- CategoryApiController (list/read + dedicated CRUD record) ---------
    ep("category__list", "/api/categories")
    ep("category__read", "/api/categories/%s" % ctx.get("rootCategoryId"))

    def capture_category(status, body):
        parsed = _data(body)
        data = parsed.get("data") or parsed  # save() renders the bare category
        ctx.set("testCategoryId", data.get("id"))

    ep("category__create", "/api/categories", "POST",
       json={"name": "ZZ Characterization Category",
             "parentCategory": {"id": ctx.get("rootCategoryId")}},
       capture=capture_category)
    ep("category__read_created", lambda: "/api/categories/%s" % ctx.get("testCategoryId"))
    ep("category__delete_created", lambda: "/api/categories/%s" % ctx.get("testCategoryId"),
       "DELETE")

    # --- CombineShipmentApiController --------------------------------------
    # read() renders {"data": null} for an unknown order id — demo data has
    # no orders, so we characterize that behaviour with a fixed bogus id.
    ep("combineShipment__read_unknown", "/api/combineShipments/doesnotexist0000")

    # --- CombinedShipmentItemApiController ----------------------------------
    ep("combinedShipmentItem__orderNumberOptions", "/api/orderNumberOptions")
    ep("combinedShipmentItem__getProductsInOrders",
       "/api/combinedShipmentItems/getProductsInOrders?orderIds=")
    ep("combinedShipmentItem__exportTemplate",
       "/api/combinedShipmentItems/exportTemplate?blank=true")
    ep("combinedShipmentItem__findOrderItems",
       "/api/combinedShipmentItems/findOrderItems", "POST", json={})

    # --- CycleCountApiController (GET/report endpoints) ---------------------
    ep("cycleCount__candidates",
       "/api/facilities/%s/cycle-counts/candidates?max=10&offset=0" % main_id)
    ep("cycleCount__pendingRequests",
       "/api/facilities/%s/cycle-counts/requests/pending?max=10&offset=0" % main_id)
    ep("cycleCount__list", "/api/facilities/%s/cycle-counts" % main_id)
    ep("cycleCount__report_details",
       "/api/reports/cycle-count-details?facility=%s&max=10&offset=0" % main_id)
    ep("cycleCount__report_summary",
       "/api/reports/cycle-count-summary?facility=%s&max=10&offset=0" % main_id)

    # --- DashboardApiController ---------------------------------------------
    ep("dashboard__config", "/api/dashboard/mainDashboard/config")
    ep("dashboard__subdashboardKeys", "/api/dashboard/mainDashboard/subdashboardKeys")
    for action in [
        "inventoryByLotAndBin", "inProgressShipments", "inProgressPutaways",
        "receivingBin", "itemsInventoried", "defaultBin",
        "expiredProductsInStock", "fillRate",
        "fillRateDestinations", "inventorySummary", "requisitionsByYear",
        "sentStockMovements", "receivedStockMovements", "outgoingStock",
        "incomingStock", "discrepancy", "delayedShipments",
        "productWithNegativeInventory", "lossCausedByExpiry",
        "productsInventoried", "percentageAdHoc", "stockOutLastMonth",
        "openStockRequests", "requestsPendingApproval", "inventoryValue",
        "openPurchaseOrdersCount", "backdatedOutboundShipments",
        "backdatedInboundShipments", "itemsWithBackdatedShipments",
    ]:
        ep("dashboard__%s" % action,
           "/api/dashboard/%s?locationId=%s" % (action, main_id))
    ep("dashboard__expirationSummary",
       "/api/dashboard/expirationSummary?locationId=%s&querySize=3" % main_id)
    ep("dashboard__fillRateSnapshot",
       "/api/dashboard/fillRateSnapshot?locationId=%s&destinationLocation=%s"
       % (main_id, ctx.get("bostonWarehouseId")))

    # --- GenericApiController (via a small stable domain: locationType) -----
    ep("generic__locationType_list", "/api/generic/locationType/")
    ep("generic__locationType_search", "/api/generic/locationType/search?max=5", "POST",
       json={})

    def capture_location_type(status, body):
        data = _data(body).get("data") or []
        ctx.set("locationTypeId", data[0]["id"] if data else None)

    # capture an id for the generic read
    plan[-2]["capture"] = capture_location_type
    ep("generic__locationType_read",
       lambda: "/api/generic/locationType/%s" % ctx.get("locationTypeId"))

    # generic create/update/delete flow using a dedicated locationType record
    def capture_generic_location_type(status, body):
        data = _data(body).get("data") or {}
        ctx.set("testLocationTypeId", data.get("id"))

    ep("generic__locationType_create", "/api/generic/locationType/", "POST",
       json={"name": "ZZ Characterization Location Type",
             "locationTypeCode": "INTERNAL"},
       capture=capture_generic_location_type)
    ep("generic__locationType_update",
       lambda: "/api/generic/locationType/%s" % ctx.get("testLocationTypeId"),
       "PUT", json={"description": "renamed by characterization suite"})
    ep("generic__locationType_delete",
       lambda: "/api/generic/locationType/%s" % ctx.get("testLocationTypeId"),
       "DELETE")

    # --- HelpScoutApiController ---------------------------------------------
    ep("helpScout__configuration", "/api/helpscout/configuration")

    # --- IndicatorApiController ---------------------------------------------
    ep("indicator__productsInventoried",
       "/api/reports/indicators/productsInventoried?facility=%s" % main_id)
    ep("indicator__inventoryAccuracy",
       "/api/reports/indicators/inventoryAccuracy?facility=%s" % main_id)
    ep("indicator__inventoryShrinkage",
       "/api/reports/indicators/inventoryShrinkage?facility=%s" % main_id)

    # --- InternalLocationApiController ---------------------------------------
    ep("internalLocation__list", "/api/internalLocations?location.id=%s" % main_id)
    ep("internalLocation__search", "/api/internalLocations/search?max=5")
    ep("internalLocation__listReceiving",
       "/api/internalLocations/receiving?location.id=%s&shipmentNumber=TEST123" % main_id)
    if ctx.get("binLocationId"):
        ep("internalLocation__read",
           "/api/internalLocations/%s" % ctx.get("binLocationId"))

    # --- InventoryApiController ----------------------------------------------
    ep("inventory__reorderReport",
       "/api/facilities/%s/inventories/reorderReport?max=10&offset=0" % main_id)
    # startDate/endDate are required; fixed historic range keeps it deterministic
    ep("inventory__expirationHistoryReport",
       "/api/inventories/expirationHistoryReport?facilityId=%s" % main_id
       + "&startDate=" + urllib.parse.quote("01/01/2010 00:00:00 Z")
       + "&endDate=" + urllib.parse.quote("01/01/2011 00:00:00 Z"))

    # --- InventoryLevelApiController -------------------------------------------
    ep("inventoryLevel__list", "/api/facilities/%s/inventory-levels" % main_id)

    # --- InventoryTransactionSummaryApiController -------------------------------
    ep("inventoryTransactionSummary__get",
       "/api/reports/inventory-transactions-summary?facility=%s&max=10&offset=0" % main_id)

    # --- InvoiceApiController ------------------------------------------------
    ep("invoice__list", "/api/invoices?max=10&offset=0")
    # demo data contains no invoices; characterize the unknown-id error shape
    ep("invoice__read_unknown", "/api/invoices/doesnotexist0000")
    ep("invoice__statusOptions", "/api/invoiceStatuses")
    ep("invoice__invoiceTypeCodes", "/api/invoiceTypeCodes")

    # --- LoadDataApiController --------------------------------------------------
    ep("loadData__listOfDemoData", "/api/loadData/listOfDemoData")

    # --- LocalizationApiController ----------------------------------------------
    ep("localization__list", "/api/localizations?languageCode=en&prefix=default.button")
    ep("localization__read", "/api/localizations/default.button.save.label?lang=en")

    # --- LocationApiController -----------------------------------------------
    ep("location__list", "/api/locations")
    ep("location__read", "/api/locations/%s" % main_id)
    ep("location__locationTypes", "/api/locations/locationTypes")
    ep("location__supportedActivities", "/api/locations/supportedActivities")
    ep("location__productSummary", "/api/locations/%s/productSummary" % main_id)
    ep("location__downloadTemplate", "/api/locations/template")
    ep("location__downloadBinLocationTemplate", "/api/locations/binLocations/template")

    # --- LocationGroupApiController (list/read + dedicated CRUD record) --------
    ep("locationGroup__list", "/api/locationGroups")
    if ctx.get("locationGroupId"):
        ep("locationGroup__read", "/api/locationGroups/%s" % ctx.get("locationGroupId"))

    def capture_location_group(status, body):
        data = _data(body).get("data") or {}
        ctx.set("testLocationGroupId", data.get("id"))

    ep("locationGroup__create", "/api/locationGroups", "POST",
       json={"name": "ZZ Characterization Location Group"},
       capture=capture_location_group)
    ep("locationGroup__update",
       lambda: "/api/locationGroups/%s" % ctx.get("testLocationGroupId"), "PUT",
       json={"name": "ZZ Characterization Location Group (renamed)"})
    ep("locationGroup__delete",
       lambda: "/api/locationGroups/%s" % ctx.get("testLocationGroupId"), "DELETE")

    # --- NoopApiController ------------------------------------------------------
    # all five actions render the same NotImplementedException; list + read
    # snapshotted as representatives
    ep("noop__list", "/api/noops")
    ep("noop__read", "/api/noops/doesnotexist0000")

    # --- PackListApiController ----------------------------------------------------
    # demo data contains no shipments; characterize the unknown-id error shape
    ep("packList__exportPackTemplate_unknown",
       "/api/stockMovements/packlistTemplate/doesnotexist0000")

    # --- OrganizationApiController (list/read + dedicated create/delete) --------
    ep("organization__list", "/api/organizations")
    if ctx.get("organizationId"):
        ep("organization__read", "/api/organizations/%s" % ctx.get("organizationId"))

    def capture_organization(status, body):
        data = _data(body).get("data") or {}
        ctx.set("testOrganizationId", data.get("id"))

    ep("organization__create", "/api/organizations", "POST",
       json={"name": "ZZ Characterization Organization"},
       capture=capture_organization)
    ep("organization__delete",
       lambda: "/api/generic/organization/%s" % ctx.get("testOrganizationId"),
       "DELETE")

    # --- ApiController: logout last ----------------------------------------------
    ep("api__logout", "/api/logout")

    return plan, ctx


def _cleanup(client, ctx):
    """Remove dedicated test records left over from a previous aborted run."""
    for cat in client.get_json("/api/categories")["data"]:
        if cat.get("name") == "ZZ Characterization Category":
            client.request("DELETE", "/api/categories/%s" % cat["id"])
    for group in client.get_json("/api/locationGroups")["data"]:
        if str(group.get("name", "")).startswith("ZZ Characterization Location Group"):
            client.request("DELETE", "/api/locationGroups/%s" % group["id"])
    for lt in client.get_json("/api/generic/locationType/")["data"]:
        if lt.get("name") == "ZZ Characterization Location Type":
            client.request("DELETE", "/api/generic/locationType/%s" % lt["id"])
    for org in client.get_json("/api/organizations")["data"]:
        if org.get("name") == "ZZ Characterization Organization":
            client.request("DELETE", "/api/generic/organization/%s" % org["id"])
