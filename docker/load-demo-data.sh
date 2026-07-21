#!/usr/bin/env bash
#
# Loads the OpenBoxes demo dataset (products, locations, users, inventory,
# stock lists) into a running instance via the configuration-wizard API.
#
# Usage: ./load-demo-data.sh [base_url] [username] [password]
#   base_url defaults to http://localhost:8080/openboxes
#
# The demo data CSVs are fetched by the app itself from the upstream
# openboxes/openboxes repository (see openboxes.configurationWizard.dataInit
# in grails-app/conf/runtime.groovy), so the app container needs outbound
# internet access. The endpoint is idempotent enough for a fresh database;
# re-running it against an already-seeded database may create duplicates.

set -euo pipefail

BASE_URL="${1:-http://localhost:8080/openboxes}"
USERNAME="${2:-admin}"
PASSWORD="${3:-password}"

COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

echo "Logging in to ${BASE_URL} as ${USERNAME}..."
curl --fail --silent --show-error --output /dev/null \
    --cookie-jar "$COOKIE_JAR" \
    "${BASE_URL}/auth/login"
curl --fail --silent --show-error --output /dev/null \
    --cookie "$COOKIE_JAR" --cookie-jar "$COOKIE_JAR" \
    --data "username=${USERNAME}&password=${PASSWORD}" \
    "${BASE_URL}/auth/handleLogin"

echo "Loading demo data (this takes a minute or two)..."
curl --fail --silent --show-error \
    --cookie "$COOKIE_JAR" \
    --max-time 600 \
    "${BASE_URL}/api/config/data/demo" > /dev/null

echo "Demo data loaded. Log in and choose a depot (e.g. Main Warehouse, Boston Warehouse, Chicago Warehouse) to see inventory."
