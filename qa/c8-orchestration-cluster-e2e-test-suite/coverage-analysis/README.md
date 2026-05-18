# OC API v2 Test Coverage Analysis

This directory holds a static snapshot of the Playwright test suite under
[`../tests/api/v2/`](../tests/api/v2). It exists so we can compare the manual
suite against the output of the
[Camunda API test generator](https://github.com/camunda/api-test-generator)
and reason about coverage of the C8 Orchestration Cluster public REST API.

## Files

|          File           |                                                                                                    Purpose                                                                                                    |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `build_coverage.py`     | Regenerates every artifact below by re-scanning `../tests/api/v2/**/*.spec.ts`.                                                                                                                               |
| `category_breakdown.md` | **Per-category narrative**: for each category, the canonical Form, the prerequisite to create, the GET-vs-search observation split, variant counts, and the **actual test names** with file:line. Start here. |
| `tests.csv`             | One row per `test(...)` declaration. Columns: `file,line,entity,category,operation,form_step,prerequisite,stateless,variants,dynamic,test_name`.                                                              |
| `coverage_matrix.csv`   | Entity × operation grid with one column per variant (counts).                                                                                                                                                 |
| `coverage_matrix.md`    | Same matrix in two views — "at-a-glance presence" (✓/blank) and "counts per cell".                                                                                                                            |
| `gaps.md`               | Heuristic gap report (entities missing 401/400 coverage and a — noisy — delete/observe-absence check).                                                                                                        |

## Regenerating

From this directory:

```bash
python3 build_coverage.py
```

The script auto-locates the test directory via its own path, so it works from
any CWD as long as the directory layout is unchanged.

## Headline numbers (snapshot)

- **132 spec files** under `tests/api/v2/`.
- **1001 statically-declared** `test(...)` calls.
- **+ ~55 additional runtime tests** from 5 parameterised loops (counted as 5 in
  the static total):
  - `expression-api-tests.spec.ts` — 3 loops → 27 runtime tests
    (20 success + 5 warning + 2 error).
  - `audit-log-search-api-tests.spec.ts` — 2 loops → 33 runtime tests
    (24 sort + 9 filter).
- **Effective runtime total: ~1056 tests.**

### Per-entity totals

|        Entity        | Tests |        Entity         | Tests |
|----------------------|------:|-----------------------|------:|
| process-instance     |   113 | mapping-rule          |    27 |
| tenant               |    87 | document              |    23 |
| role                 |    78 | decision-instance     |    23 |
| authorization        |    78 | message               |    22 |
| cluster-variables    |    67 | resource              |    19 |
| job                  |    57 | audit-log             |    17 |
| user-task            |    51 | decision-requirements |    14 |
| group                |    48 | variable              |    14 |
| element-instance     |    35 | conditional           |    13 |
| decision-definition  |    33 | optimize              |     6 |
| batch-operation      |    32 | signal                |     5 |
| user                 |    30 | authentication        |     4 |
| global-task-listener |    30 | usage-metrics         |     4 |
| process-definition   |    30 | cluster               |     3 |
| incident             |    28 | clock                 |     3 |
|                      |       | expression            |     3 |
|                      |       | license               |     2 |
|                      |       | message-subscriptions |     2 |

### Variant distribution (cross-cutting)

|          Variant          | Count |
|---------------------------|------:|
| Bad Request / validation  |   218 |
| Unauthorized (401)        |   163 |
| Success / happy path      |   137 |
| Not Found (404)           |   123 |
| Filter                    |    40 |
| Pagination / sort         |    29 |
| Conflict (409)            |    28 |
| Forbidden / no permission |    28 |

## Categorisation scheme

Tests are bucketed into the following categories (see the `category` column of
`tests.csv`). The buckets are picked so they match the typical shape of
generator output — entity-driven where the API has a lifecycle, and
behaviour-driven where it doesn't.

|  #  |             Category             |                                                                                       What it means                                                                                       |
|-----|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A   | Entity Lifecycle (CRUD)          | Configurable resources with create/get/update/delete/search: `user`, `group`, `role`, `tenant`, `mapping-rule`, `authorization`, `cluster-variables`, `global-task-listener`, `document`. |
| B   | Membership / Association         | "X assigned to Y" — e.g. `group-users`, `role-mapping-rules`, `tenant-clients`. Assign → Search members → Unassign → Search members.                                                      |
| C   | Deployment Lifecycle             | Deploy resource → Get definition (JSON/XML) → Search → Delete. Covers `resource`, `process-definition`, `decision-definition`, `decision-requirements`.                                   |
| D   | Process-Instance Lifecycle & Ops | Largest bucket. Single-instance ops (cancel, migrate, modify, delete, resolve-incident) plus batch creators.                                                                              |
| E   | Batch-Operation Lifecycle        | Created indirectly by batch APIs → Get → Search → Search items → Suspend → Cancel.                                                                                                        |
| F   | User-Task Lifecycle              | Assign / Unassign / Update / Complete / Search / Get form / Search variables.                                                                                                             |
| G   | Job Lifecycle & Stats            | Activate → Complete / Fail / Error / Update plus 5 statistics endpoints.                                                                                                                  |
| H   | Incident Lifecycle               | Raised by failing jobs → Get → Search → Resolve → Statistics.                                                                                                                             |
| I   | Decision-Instance Lifecycle      | Evaluate → Get → Search → Delete (single + batch).                                                                                                                                        |
| J–L | Observation-only                 | `element-instance`, `variable`, `audit-log` — perform actions elsewhere, observe state via search/get.                                                                                    |
| M   | Messaging / Signals              | `message`, `signal`, `message-subscriptions`. Publish/Correlate/Broadcast + observation.                                                                                                  |
| N   | Engine Evaluation                | Stateless request/response: `expression`, `conditional`.                                                                                                                                  |
| O   | System / Admin                   | `authentication`, `cluster`, `license`, `clock`, `usage-metrics`, `optimize`.                                                                                                             |

### Lifecycle "form" examples

The form pattern most lifecycle tests follow:

```
Create Entity → Get Entity (observe present) → Delete Entity → Get Entity (observe absent)
```

Common variants layered on top:

- **Prerequisite to create** — e.g. deploy a process before creating an
  instance, create a parent entity before assigning members.
- **Observation channel** — observe via `GET /entity/{id}` vs observe via
  search results.
- **Negative variants** — unauthorized (401), forbidden (403), not-found (404),
  bad-request (400), conflict (409).
- **Pagination & filter** — only meaningful on search endpoints.

## Form step labelling

`tests.csv` carries a `form_step` column that places each test inside the canonical lifecycle FORM:

|        form_step         |                                meaning                                |
|--------------------------|-----------------------------------------------------------------------|
| `create`                 | Happy-path creation of the entity.                                    |
| `observe-present-get`    | Reads the entity by id (or equivalent) to verify it exists.           |
| `observe-present-search` | Verifies presence via a search/list endpoint.                         |
| `mutate`                 | Update / assign / complete / migrate / etc. on an existing entity.    |
| `delete`                 | Happy-path deletion (or cancellation).                                |
| `observe-absence`        | GET returns 404 after delete, or search no longer returns the entity. |
| `aggregate`              | Statistics / count / metric endpoint.                                 |
| `evaluate`               | Stateless evaluation (expression, conditional).                       |
| `negative-<step>`        | Same step on an unhappy path (401/403/404/400/409).                   |
| `parameterized`          | Placeholder for `for (...) test(tc.description, ...)` loops.          |

## Prerequisite labelling

`tests.csv` carries a `prerequisite` column that says what the test needs already in place. Examples: `deployed-process`, `running-process-instance-with-user-task`, `group + user`, `none`. Membership tests are auto-tagged with `<parent> + <member>` derived from the file name; everything else is mapped from the entity type.

## Stateless tag

`tests.csv` carries a `stateless` column (`yes`/`no`). A test is stateless when the API call doesn't persist anything to the engine — submit input, get a result, nothing left behind. Stateless entities: `expression`, `conditional`, `authentication`, `cluster`, `license`, `usage-metrics`. Everything else (including `clock` pin/reset and `optimize` config tests, which mutate or observe persisted state) is `no`. Useful when comparing against the AI generator because stateless endpoints have no prerequisite or cleanup and are the easiest to generate.

## Operation labelling

The `operation` column in `tests.csv` is a first-match-wins CRUD verb derived
from the test name:

|    Operation    |                                  Trigger keywords (case-insensitive)                                  |
|-----------------|-------------------------------------------------------------------------------------------------------|
| `create`        | create, add, deploy, publish, broadcast, pin, register                                                |
| `delete`        | delete, remove, unassign, cancel, reset                                                               |
| `update`        | update, assign, complete, migrate, modify, resolve, correlate, evaluate, fail, error, suspend, resume |
| `search`        | search, sort, filter, paginate, list, statistics                                                      |
| `get`           | get, fetch, retrieve, return, read, exists, by-id                                                     |
| `parameterized` | rows synthesised from `for (const tc of …) test(tc.description, …)` patterns                          |
| `other`         | nothing matched                                                                                       |

## Variant labelling

The `variants` column is **multi-label** (pipe-joined). A test named
*"Should fail to delete user when unauthorized"* gets tagged
`delete + unauthorized + observe-via-get` etc.

|       Variant        |                          What it captures                          |
|----------------------|--------------------------------------------------------------------|
| `happy-path`         | "Should X successfully" / "Success" / verbed-without-error names.  |
| `bad-request`        | invalid, missing required, empty/null, too-long, negative, exceed. |
| `unauthorized`       | 401 / "unauthorized".                                              |
| `forbidden`          | 403 / "no permission", "no granted", "without permission".         |
| `not-found`          | 404 / "not found", "non-existing", "does not exist".               |
| `conflict`           | 409 / "duplicate", "already".                                      |
| `pagination-sort`    | paginate, page size, cursor, sort.                                 |
| `filter`             | "filter" present in name.                                          |
| `observe-via-get`    | GET / fetch / retrieve language.                                   |
| `observe-via-search` | "search" present in name.                                          |
| `observe-absence`    | "after delete / once removed / no longer / absent / gone".         |
| `data-driven`        | placeholder row for a parameterised loop body.                     |
| `unlabeled`          | none of the above matched.                                         |

## Known caveats

These are properties of the **heuristic labelling**, not of the underlying suite.

1. **"Delete-then-observe-absence" gap is noisy.** Most entities verify
   deletion via "GET returns 404", which my heuristic tags `not-found` rather
   than `observe-absence`. Treat the gaps in `gaps.md` as candidates to
   eyeball, not gospel.
2. **Parameterised loops are counted as 1 declaration each.** `tests.csv` adds
   a `<parameterized: varname>` row per loop so they're not invisible, but the
   1001 total under-counts effective runtime tests by ~55. Use the "effective
   runtime total" of ~1056 for fairness-vs-generator comparisons.
3. **Operation is first-match-wins.** A test named "Should fail to delete a
   non-existent user" is tagged `delete`, not `get`, even though the action
   performed is delete-then-observe.
4. **Variants are name-derived.** A test that exercises a 401 path without
   saying "unauthorized" in its name will be missed. Spot-check before drawing
   strong conclusions for a specific entity.

## Comparing against the AI generator

A useful workflow:

1. Have the generator emit a CSV with the same columns
   (`file,line,entity,category,operation,variants,test_name`).
2. Diff on `(entity, operation, variant)` tuples to find gaps in either
   direction.
3. For any "missing" cell, inspect the actual test names in `tests.csv` —
   sometimes the generator covers the case under a different operation tag.

