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
| 0.1 | App running via Docker (MySQL + seed data); fix fork CI | [22d3def4](https://app.devin.ai/sessions/22d3def4f9f84c8c9d3c3b463021aa7c) | [#1](https://github.com/tedfoley-cog/openboxes/pull/1) | merged |
| 0.2a | Playwright characterization: login, receive stock, create requisition | [35b80cc2](https://app.devin.ai/sessions/35b80cc2487e4b1ea48bff2163e88af9) | [#5](https://github.com/tedfoley-cog/openboxes/pull/5) | merged |
| 0.2b | Playwright characterization: stock movement, ship, putaway | [40deec22](https://app.devin.ai/sessions/40deec2248d343a381a6727faf8f5d12) | [#6](https://github.com/tedfoley-cog/openboxes/pull/6) | merged |
| 0.2c | Playwright characterization: cycle count, invoice | [02b704e7](https://app.devin.ai/sessions/02b704e7b0df44cdab4b79e796574441) | [#7](https://github.com/tedfoley-cog/openboxes/pull/7) | merged |
| 0.3a | API snapshot tests: controllers A–L (26) | [ec2aa633](https://app.devin.ai/sessions/ec2aa63314ab4e77a7886a95bb63259f) | [#3](https://github.com/tedfoley-cog/openboxes/pull/3) | merged (unified into 0.3b harness; combined suite 191/191) |
| 0.3b | API snapshot tests: controllers M–Z (26) | [f12d5fcf](https://app.devin.ai/sessions/f12d5fcf1acc40b0987f1ddfe2a1ff4e) | [#4](https://github.com/tedfoley-cog/openboxes/pull/4) | merged (85 snapshots, M–Z) |
| 0.4 | Dead-screen audit: live-screen inventory of 616 GSPs | [9f0386f7](https://app.devin.ai/sessions/9f0386f72a764fafad375b6a5e2837e6) | [#2](https://github.com/tedfoley-cog/openboxes/pull/2) | merged (LIVE=274, TEMPLATE=231, SUPERSEDED=7, DEAD=104; 49-batch Phase 2 queue in docs/migration/SCREEN_INVENTORY.md) |

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
- 2026-07-21: Coordinator initialized; tracking branch created; Phase 0 wave 1 spawned.
- 2026-07-21: PR #1 (Docker baseline + fork CI) and PR #2 (dead-screen audit) merged; wave 2 spawned (0.2a, 0.3a, 0.3b). 0.2b/0.2c queued behind 0.2a harness.
- 2026-07-21: PR #5 (0.2a Playwright flows) and PR #4 (0.3b API snapshots M–Z) merged. 0.3a PR #3 conflicted with #4's harness — child instructed to rebase/unify. 0.2b + 0.2c spawned.
- 2026-07-22: PRs #3, #6, #7 merged — **Phase 0 complete**. Parity oracle in place: 9 Playwright golden-path flows + 191 API snapshots across all 52 controllers, all wired into CI. Phase 1 wave 1 (Java 8→11) starting; Phase 2/3 waves to interleave.
