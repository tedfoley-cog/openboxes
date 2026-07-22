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
| 1.1a | Java 8→11 build/runtime | [ea699678](https://app.devin.ai/sessions/ea699678f4864796aa48a3400435a9ba) — [#9](https://github.com/tedfoley-cog/openboxes/pull/9) merged |
| 1.1b | Dependency cleanup / Grails 4 de-risk audit | [d11e6110](https://app.devin.ai/sessions/d11e611086ee4de4840215ef5cb2f7e4) — [#19](https://github.com/tedfoley-cog/openboxes/pull/19) merged (audit in docs/migration/DEPENDENCY_AUDIT.md) |
| 1.2 | Grails 3→4 | [f3400cd2](https://app.devin.ai/sessions/f3400cd2cb624535ab89390e465486dd) — [#27](https://github.com/tedfoley-cog/openboxes/pull/27) merged (Grails 4.1.4, GORM 7, Hibernate 5.4, Gradle 6.9.4) |
| 1.3 | Grails 4→5 | [7943163d](https://app.devin.ai/sessions/7943163d2df74a1ea669461b357271c3) — [#34](https://github.com/tedfoley-cog/openboxes/pull/34) merged (Grails 5.3.6, Groovy 3, GORM 7.3, Boot 2.7, Gradle 7) |
| 1.4 | Grails 5→6 / Java 21 | [24db1e26](https://app.devin.ai/sessions/24db1e2655464a0591029bb420745f63) — [#41](https://github.com/tedfoley-cog/openboxes/pull/41) merged (Grails 6.2.3, GORM 8.1, Gradle 8, Java 21). javax→jakarta/Boot 3/Groovy 4 only arrive with Grails 7 — deferred to 1.4b |
| 1.4b | Grails 6→7.2.1 / Boot 3.5 / Groovy 4 / jakarta / Liquibase 4.27 | [3583f869](https://app.devin.ai/sessions/3583f8697c57472e9401ebc65444dd7e) — [#48](https://github.com/tedfoley-cog/openboxes/pull/48) merged |
| 1.5 | Re-verify Quartz jobs, Liquibase, mail/reporting (3–4 children) | pending |

## Phase 2 — UI: GSP → React (parallel module waves; needs Phase 0)
| # | Task | Status |
|---|------|--------|
| 2.x | Module batches: 49 total per SCREEN_INVENTORY.md | wave 1: B7 [#24](https://github.com/tedfoley-cog/openboxes/pull/24) merged, B14 [#25](https://github.com/tedfoley-cog/openboxes/pull/25) merged, B31 [#22](https://github.com/tedfoley-cog/openboxes/pull/22) merged; B2 [#23](https://github.com/tedfoley-cog/openboxes/pull/23) merged, B26 [#20](https://github.com/tedfoley-cog/openboxes/pull/20) merged; B1 [#21](https://github.com/tedfoley-cog/openboxes/pull/21) merged. Wave 2: B8 [#31](https://github.com/tedfoley-cog/openboxes/pull/31) merged, B27 [#32](https://github.com/tedfoley-cog/openboxes/pull/32) merged, B32 [#28](https://github.com/tedfoley-cog/openboxes/pull/28) merged; B3 [#30](https://github.com/tedfoley-cog/openboxes/pull/30) merged, B4 [#33](https://github.com/tedfoley-cog/openboxes/pull/33) merged; B15 [#29](https://github.com/tedfoley-cog/openboxes/pull/29) merged. Wave 3: B6 [#35](https://github.com/tedfoley-cog/openboxes/pull/35), B9 [#39](https://github.com/tedfoley-cog/openboxes/pull/39), B16 [#40](https://github.com/tedfoley-cog/openboxes/pull/40), B28 [#37](https://github.com/tedfoley-cog/openboxes/pull/37), B33 [#36](https://github.com/tedfoley-cog/openboxes/pull/36) all merged; B5 [#38](https://github.com/tedfoley-cog/openboxes/pull/38) merged. Wave 4 running: B10 [5cc0497b](https://app.devin.ai/sessions/5cc0497b52df4a8e9a661c54383b12ce), B11 [9bdb168a](https://app.devin.ai/sessions/9bdb168a7a124d0cbb01ec67559162c6), B17 [6c1e9a75](https://app.devin.ai/sessions/6c1e9a75930e4adf8dfd3095831d2864), B19 [eeab8e53](https://app.devin.ai/sessions/eeab8e53a47644339f9733f88562246d), B29 [95d69576](https://app.devin.ai/sessions/95d69576baf349b288fb4c3cd83a989d), B34 [b1036343](https://app.devin.ai/sessions/b1036343d803411ab1a0aa2709706bdf) |
| 2.R | React 16.8→18 + Redux/router modernization of existing SPA | [1674abb2](https://app.devin.ai/sessions/1674abb277894ead9017aaf991b0597a) — [#18](https://github.com/tedfoley-cog/openboxes/pull/18) merged (React 18.3.1, react-redux 8; router v6 deferred) |

## Phase 3 — API formalization (parallel with Phase 2)
| # | Task | Status |
|---|------|--------|
| 3.1 | OpenAPI harness + specs: Api, Attribute, BinLocation, Category, CombineShipment, CombinedShipmentItem | [893ec63d](https://app.devin.ai/sessions/893ec63d5d5744ad9ffc28c4539856f7) — [#8](https://github.com/tedfoley-cog/openboxes/pull/8) merged (harness + 6 specs, contract suite 28/28) |
| 3.2 | Base, BaseDomain, CycleCount, Dashboard, Fulfillment, Generic | [016e8528](https://app.devin.ai/sessions/016e8528fa6246f1bb58ef003407f4af) — [#15](https://github.com/tedfoley-cog/openboxes/pull/15) merged |
| 3.3 | HelpScout, Indicator, InternalLocation, Inventory, InventoryLevel, InventoryTransactionSummary | [3bf3df8f](https://app.devin.ai/sessions/3bf3df8f6f2a4cd98ee684297991a1d2) — [#10](https://github.com/tedfoley-cog/openboxes/pull/10) merged |
| 3.4 | Invoice, LoadData, Localization, Location, LocationGroup, Noop | [2c36091e](https://app.devin.ai/sessions/2c36091e7182440e822171e141f1e874) — [#14](https://github.com/tedfoley-cog/openboxes/pull/14) merged |
| 3.5 | Organization, PackList, PartialReceiving, Person, Picklist, PrepaymentInvoice | [e07843a8](https://app.devin.ai/sessions/e07843a8d87d44919fa71b89a58888bc) — [#12](https://github.com/tedfoley-cog/openboxes/pull/12) merged |
| 3.6 | PrepaymentInvoiceItem, Product, ProductClassification, ProductPackage, ProductSupplier, ProductSupplierAttribute | [7b9334a7](https://app.devin.ai/sessions/7b9334a78d01402fb40b345350092d1c) — [#11](https://github.com/tedfoley-cog/openboxes/pull/11) merged |
| 3.7 | ProductSupplierPreference, ProductsConfiguration, PurchaseOrder, Putaway, PutawayItem, ReasonCode | [3592a490](https://app.devin.ai/sessions/3592a49054e14f71b143ac843c0e8c51) — [#16](https://github.com/tedfoley-cog/openboxes/pull/16) merged |
| 3.8 | RecordStock, Replenishment, SelectOptions, StockAdjustment, StockMovement, StockMovementItem | [9b23fb73](https://app.devin.ai/sessions/9b23fb7380d44cb1b702689d20b11d4d) — [#17](https://github.com/tedfoley-cog/openboxes/pull/17) merged |
| 3.9 | StockTransfer, Stocklist, StocklistItem, UnitOfMeasure | [ac15c4c2](https://app.devin.ai/sessions/ac15c4c2986a4235a4aaf68c4448a88d) — [#13](https://github.com/tedfoley-cog/openboxes/pull/13) merged |
| 3.10 | Make contract suite re-run-safe | [d87216b4](https://app.devin.ai/sessions/d87216b44c06463da4b70581970390bc) — [#26](https://github.com/tedfoley-cog/openboxes/pull/26) merged |

## Phase 4 — Validation & cutover
| # | Task | Status |
|---|------|--------|
| 4.1 | Full Playwright regression vs. Phase 0 baseline | pending |
| 4.2 | Liquibase dry-run on production-shaped data | pending |
| 4.3 | Performance smoke | pending |
| 4.4 | Remove GSP layer + dead code | pending |

## Log
- 2026-07-22: #57 (B21) merged. 37/49 Phase 2 batches done. B37 + wave 7 (B39-B44) in flight.
- 2026-07-22: #62 (B25) merged. 36/49 Phase 2 batches done.
- 2026-07-22: #59 (B24) merged. 35/49 Phase 2 batches done.
- 2026-07-22: Wave 7 spawned (B39-B44 reports/admin/docs/localization): 4f84612e, 7c525d48, ce28cedf, c807a61b, e31890cc, 6fda6ebd.
- 2026-07-22: #58 (B36), #63 (B22) merged. 34/49 Phase 2 batches done.
- 2026-07-22: #61 (B38), #56 (B23) merged. 32/49 Phase 2 batches done.
- 2026-07-22: #54 (B12), #50 (B20) merged. 30/49 Phase 2 batches done. Wave 6 (B21-25, B36-38) in flight.
- 2026-07-22: Wave 6 spawned (B21-B25 shipments/stock transfer, B36 people/suppliers, B37-B38 reports): b7093244, 2fb48d45, 1ca02513, 487e0a7e, 03a7041d, a2e164b3, ec9700e5, 95db3e77.
- 2026-07-22: #47 (B19 create-shipment wizard), #55 (P1.5 integration reverify) merged. Phase 1 complete. 28/49 Phase 2 batches done.
- 2026-07-22: #51 (B13), #53 (B18) merged. 27/49 Phase 2 batches done.
- 2026-07-22: #45 (B17), #49 (B30), #52 (B35) merged. 25/49 Phase 2 batches done.
- 2026-07-22: Wave 5 spawned: P1.5 reverify + B12 [f71be77f](https://app.devin.ai/sessions/f71be77f3ca342fa9e9a5f303f3a6622), B13 [61e38fe7](https://app.devin.ai/sessions/61e38fe7c07f4620be134673a4500deb), B18 [fd4f1f12](https://app.devin.ai/sessions/fd4f1f1216d54ca99d3278b662c39d06), B20 [e90b1dbc](https://app.devin.ai/sessions/e90b1dbc4b8b4809b27bce543c8bf3f9), B30 [1bb0b972](https://app.devin.ai/sessions/1bb0b972c60b4a9eb16ca715e8e1906f), B35 [485864c6](https://app.devin.ai/sessions/485864c689964a02b8b658e4cebdc2dc).
- 2026-07-22: #43 (B11), #48 (Grails 7.2.1 + Boot 3.5 + jakarta + Liquibase 4.27) merged. Backend platform target reached; 1.5 integration reverify next.
- 2026-07-22: #42 (B34), #44 (B10), #46 (B29) merged. 21/49 Phase 2 batches done. Grails 7 child (1.4b) running.
- 2026-07-22: #38 (B5), #41 (Grails 6.2.3 + Java 21) merged. Grails 7 wave (jakarta/Boot3/Groovy4) queued as 1.4b.
- 2026-07-22: #29, #35, #36, #37, #39, #40 merged (B15, B6, B33, B28, B9, B16). 17/49 Phase 2 batches done.
- 2026-07-22: #34 (Grails 4→5) merged. Grails 6 + Java 21 + jakarta child spawned.
- 2026-07-22: #30 (B3), #33 (B4) merged. Phase 2 wave 3 spawned (B5,B6,B9,B16,B28,B33).
- 2026-07-22: #28 (B32), #31 (B8), #32 (B27) merged. Grails 4→5 child running.
- 2026-07-22: #21 (B1) and #27 (Grails 3→4) merged. B3 #30 resolving conflicts post-Grails-4 merge.
- 2026-07-22: PRs #23 (B2), #20 (B26) merged. Phase 2 wave 2 spawned (B3,B4,B8,B15,B27,B32).
- 2026-07-22: PRs #26 (contract re-run-safety), #22/#24/#25 (Phase 2 batches B31/B7/B14) merged. Grails 3→4 (P1.2) in progress.
- 2026-07-21: Coordinator initialized; tracking branch created; Phase 0 wave 1 spawned.
- 2026-07-21: PR #1 (Docker baseline + fork CI) and PR #2 (dead-screen audit) merged; wave 2 spawned (0.2a, 0.3a, 0.3b). 0.2b/0.2c queued behind 0.2a harness.
- 2026-07-21: PR #5 (0.2a Playwright flows) and PR #4 (0.3b API snapshots M–Z) merged. 0.3a PR #3 conflicted with #4's harness — child instructed to rebase/unify. 0.2b + 0.2c spawned.
- 2026-07-22: **Phase 3 complete** — all 9 OpenAPI spec/contract PRs merged (52/52 controllers). Known issue: some contract modules not re-run-safe (docs/migration/KNOWN_ISSUES_CONTRACT_SUITE.md) — P3.10 spawned to fix.
- 2026-07-22: PR #9 (Java 11) + PR #18 (React 18) merged. Phase 3 PRs #10–13, #16 merged; #14/#15 resolving conflicts; 3.8 still running. Note from 1.1a: 18/191 API snapshots drift between pinned release image and source builds — re-baselined for source builds. Wave: 1.1b + Phase 2 batches B1,B2,B7,B14,B26,B31 spawned.
- 2026-07-22: PRs #3, #6, #7 merged — **Phase 0 complete**. Parity oracle in place: 9 Playwright golden-path flows + 191 API snapshots across all 52 controllers, all wired into CI. Phase 1 wave 1 (Java 8→11) starting; Phase 2/3 waves to interleave.
