# OpenBoxes Modernization — Migration Status

Coordinator work queue for the Grails 3.3.16/Java 8 → Spring Boot 3/Java 21 + React 18 migration.
Plan reference: openboxes-coordinator-plan.md (coordinator session).

**Target:** Spring Boot 3 / Java 21 backend (staged Grails 3→4→5→6 upgrades), single React 18 SPA (all live GSP screens migrated), OpenAPI-specified REST layer, parity proven by a characterization test harness.

**Parity gate (applies to every PR):**
- Characterization suite green (Playwright UI flows + API JSON snapshots) — no merge otherwise.
- Per-screen before/after screenshots + data assertions in PR description.
- No MySQL schema changes outside Liquibase; old and new must run on the same data.

## Repo facts (verified 2026-07-21)
- Grails 3.3.16, Java 8, develop is default branch
- 52 `*ApiController*` controllers, 616 GSP views
- Docker setup under `docker/` (docker-compose.yml, MySQL)
- CI: GitHub Actions (`backend-tests.yml`, `frontend-tests.yml`, `on-change.yml`, etc.)

## Phase 0 — Baseline & harness (blocks everything else)
| # | Task | Child session | PR | Status |
|---|------|---------------|----|--------|
| 0.1 | App running via Docker (MySQL + seed data); fix fork CI | [22d3def4](https://app.devin.ai/sessions/22d3def4f9f84c8c9d3c3b463021aa7c) | — | in progress |
| 0.2a | Playwright characterization: login, receive stock, create requisition | — | — | pending |
| 0.2b | Playwright characterization: stock movement, ship, putaway | — | — | pending |
| 0.2c | Playwright characterization: cycle count, invoice | — | — | pending |
| 0.3a | API snapshot tests: controllers A–L (~26) | — | — | pending |
| 0.3b | API snapshot tests: controllers M–Z (~26) | — | — | pending |
| 0.4 | Dead-screen audit: live-screen inventory of 616 GSPs | [9f0386f7](https://app.devin.ai/sessions/9f0386f72a764fafad375b6a5e2837e6) | — | in progress |

## Phase 1 — Backend platform (sequential waves; app must boot after each)
| # | Task | Status |
|---|------|--------|
| 1.1 | Java 8→11 + dependency cleanup (2 children) | pending |
| 1.2 | Grails 3→4 (3–5 children) | pending |
| 1.3 | Grails 4→5 (3–5 children) | pending |
| 1.4 | Grails 5→6 / Java 21 / javax→jakarta (4–6 children) | pending |
| 1.5 | Re-verify Quartz jobs, Liquibase, mail/reporting (3–4 children) | pending |

## Phase 2 — UI: GSP → React (parallel module waves; needs Phase 0)
| # | Task | Status |
|---|------|--------|
| 2.x | Module batches (3–6 live screens each): inventory browser, product catalog, locations/orgs, requisitions, shipments, orders, admin/config, reporting | pending (scoped after 0.4 audit) |
| 2.R | React 16.8→18 + Redux/router modernization of existing SPA (3–5 children) | pending |

## Phase 3 — API formalization (parallel with Phase 2)
| # | Task | Status |
|---|------|--------|
| 3.1–3.9 | OpenAPI specs + contract tests, ~6 controllers per child; wire into CI | pending |

## Phase 4 — Validation & cutover
| # | Task | Status |
|---|------|--------|
| 4.1 | Full Playwright regression vs. Phase 0 baseline | pending |
| 4.2 | Liquibase dry-run on production-shaped data | pending |
| 4.3 | Performance smoke | pending |
| 4.4 | Remove GSP layer + dead code | pending |

## Log
- 2026-07-21: Coordinator initialized; tracking branch created; Phase 0 wave being spawned.
