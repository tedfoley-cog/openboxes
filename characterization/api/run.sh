#!/usr/bin/env bash
# Run the API characterization snapshot suite against a running,
# demo-data-seeded OpenBoxes instance (see docs/migration/RUNNING_LOCALLY.md).
#
# Usage:
#   ./run.sh                # verify: fails on any snapshot diff
#   ./run.sh --update       # intentionally re-baseline the snapshots
#
# Environment overrides: OPENBOXES_URL, OPENBOXES_USERNAME, OPENBOXES_PASSWORD
set -euo pipefail
cd "$(dirname "$0")"
exec python3 snapshot_runner.py "$@"
