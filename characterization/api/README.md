# API characterization (snapshot) tests

JSON snapshot tests that pin down the current behaviour of the OpenBoxes
REST API. They are the parity oracle for the backend migration: any change
in an API response shows up as a snapshot diff and fails the suite.

Phase 0.3a covers the 26 API controllers A–L; see
[`docs/migration/API_SNAPSHOT_COVERAGE_A.md`](../../docs/migration/API_SNAPSHOT_COVERAGE_A.md)
for the endpoint-by-endpoint coverage table.

## Prerequisites

A running OpenBoxes instance seeded with the standard demo dataset:

```bash
cd docker
docker compose up -d          # wait for /health to report UP (2-5 min first boot)
./load-demo-data.sh           # seed demo data (run once, on a fresh DB)
```

Python 3.8+ (standard library only — no pip installs needed).

## Run the suite

```bash
characterization/api/run.sh
```

Exits non-zero and prints a unified diff for every endpoint whose
normalized response differs from the committed snapshot in `snapshots/`.

## Re-baseline (intentional behaviour change)

```bash
characterization/api/run.sh --update
```

Rewrites all snapshots from the live instance. Review and commit the diff.

Other options: `--only <substring>` restricts the run to matching endpoint
names. `OPENBOXES_URL` / `OPENBOXES_USERNAME` / `OPENBOXES_PASSWORD` env
vars point the runner at a different instance.

## How it works

- `snapshot_runner.py` — generic runner: authenticates, executes the plan,
  normalizes responses, writes/compares snapshots. The masking rules are
  documented in its module docstring.
- `endpoints_a.py` — declarative endpoint plan for controllers A–L,
  including runtime id resolution (demo-data ids are generated at load
  time) and the dedicated CRUD test records (category / location group /
  organization) that keep write-flow snapshots deterministic.
- `snapshots/` — committed, normalized snapshots (one JSON file per
  endpoint: request, status, content type, masked body).

### Masking rules (summary)

1. Entity ids (16+ char hex UUIDs) → `<ID>` (in bodies) / `{id}` (in paths).
2. Date/timestamp values → `<DATE>`; date-like map keys → `<DATEKEY:n>`.
3. Volatile-by-name keys (`dateCreated`, `lastUpdated`, `buildNumber`,
   `ipAddress`, `hostname`, ...) → `<VOLATILE>`.
4. Object keys sorted; arrays sorted canonically (DB ordering is not
   deterministic).
5. Non-JSON bodies (CSV templates etc.) stored as raw text with inline
   dates masked.

## CI

`.github/workflows/api-snapshot-tests.yml` boots the docker-compose stack,
loads demo data and runs the suite on every pull request.
