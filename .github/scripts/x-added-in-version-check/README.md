# `x-added-in-version` annotation check

CI-only tooling that verifies the Camunda 8 REST OpenAPI specs under
[`zeebe/gateway-protocol/src/main/proto/v2/`](../../../zeebe/gateway-protocol/src/main/proto/v2/)
are correctly annotated with `x-added-in-version` (on every operation) and
`x-properties-added-in-version` (on every schema parent that has versioned
properties).

The authoritative version map and endpoint map are generated from the [camunda/return-of-api-added-in-analysis](https://github.com/camunda/return-of-api-added-in-analysis)
repo. This directory consumes both and reports any drift.

## Used by

|                                                            Workflow                                                             |       npm script        |
|---------------------------------------------------------------------------------------------------------------------------------|-------------------------|
| [`verify-x-added-in-version-annotations.yml`](../../workflows/verify-x-added-in-version-annotations.yml) (scheduled / dispatch) | `verify:specs:workflow` |
| `openapi-x-added-in-version-check` job in [`ci.yml`](../../workflows/ci.yml) (PR / push, non-blocking)                          | `verify:specs:ci`       |

## Layout

```
build-artefacts.mjs       # clones return-of-api-added-in-analysis and copies its
                          # output to artefacts/{endpoint-map,version-map}.json
verify-specs.mjs          # shared verification core (used by both CI variants)
verify-specs-workflow.mjs # entry point: scheduled workflow (fails on drift)
verify-specs-ci.mjs       # entry point: PR check (emits inline annotations; exits non-zero on findings — kept non-blocking via continue-on-error in ci.yml)
artefacts/                # generated; gitignored (endpoint-map.json, version-map.json, bundler-specs/)
```

## Env

|         Var         |             Default             |                                                             Purpose                                                             |
|---------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `OCA_SPEC_PATH`     | _required_                      | Directory of multi-file specs to verify (CI sets it to `zeebe/gateway-protocol/src/main/proto/v2`)                              |
| `RETURN_OF_API_REF` | `main`                          | Git ref of `return-of-api-added-in-analysis` to fetch the endpoint and version maps from                                        |
| `ENDPOINT_MAP_PATH` | `./artefacts/endpoint-map.json` | Where the endpoint map is written/read                                                                                          |
| `VERSION_MAP_PATH`  | `./artefacts/version-map.json`  | Where the version map is written/read                                                                                           |
| `BUNDLER_SPECS_DIR` | `./artefacts/bundler-specs`     | Persistent cache; `build:bundler:latest` refetches every version listed in `MAIN_BRANCH_VERSIONS` (the entries tracking `main`) |

## Annotation rules

The verifier checks two annotations:

- `x-added-in-version` — set on **every operation** (`paths.<route>.<method>`). Value must equal the operation's added version from the version map. Operations listed in the version map's `deletedOperations` must **not** carry this annotation.
- `x-properties-added-in-version` — a list set on a **schema object** (the node that owns a `properties:` map). Each entry has the form `{ propertyName, addedInVersion }` and records when a single property under that schema was introduced — unless that entry is suppressed by one of the rules below.

### Rule 1 — Property version differs from its endpoint version

A property is annotated only when its introduction version is different from the endpoint's own introduction version. Same version ⇒ skipped, since the endpoint-level `x-added-in-version` already covers it.

```yaml
# POST /jobs/activation introduced in 8.6
paths:
  /jobs/activation:
    post:
      x-added-in-version: '8.6'
      requestBody:
        content:
          application/json:
            schema:
              properties:
                type:        { type: string }                              # 8.6 — same as endpoint, NOT annotated
                worker:      { type: string }                              # 8.6 — NOT annotated
                tenantIds:   { type: array,  x-added-in-version: '8.7' }   # added later, annotated
```

### Rule 2 — Property version differs from its nearest property ancestor

A child property added in the SAME version as its nearest ancestor property is skipped (the ancestor property already covers it). A child property added in a LATER version gets its own annotation.

The parent/child relation also traverses `$ref` boundaries. For example, `DecisionInstanceFilter.evaluationDate` (in `decision-instances.yaml`) is recognised as the parent of `DateTimeFilterProperty.$eq` (in `filters.yaml`), even though the two properties live in different YAML files. Suppression only fires when every aggregated consumer's ancestor property location shares the same intro version as the child property — otherwise the child property gets its own annotation.

```yaml
# endpoint introduced in 8.6
properties:
  result:
    x-added-in-version: '8.7'      # ancestor property annotated (later than endpoint)
    type: object
    properties:
      variables:                   # 8.7 — same as ancestor property, NOT annotated
        type: object
      denied:                      # 8.8 — later than ancestor property, annotated
        type: boolean
        x-added-in-version: '8.8'
```

### Rule 3 — Shared schemas: earliest version across all consumers

When a schema (or property) is referenced from multiple endpoints (via `$ref` or `allOf`), the chosen introduction version is the earliest version seen across all consumers. The property is then annotated unless every consumer endpoint's own introduction version equals the property's added version — in which case all endpoint-level annotations already cover it.

Example — `element-instances.yaml#/components/schemas/AdvancedElementInstanceStateFilter` is referenced from seven endpoints. The `$exists` operator inside it was first seen in 8.8 by every consumer (the filter itself was introduced in 8.8), but the consuming endpoints span three different intro versions:

|                                    Endpoint                                     | Endpoint version | First saw `$exists` |
|---------------------------------------------------------------------------------|------------------|---------------------|
| `POST /process-instances/search`                                                | 8.6              | 8.8                 |
| `POST /process-definitions/{processDefinitionKey}/statistics/element-instances` | 8.8              | 8.8                 |
| `POST /process-instances/cancellation`                                          | 8.8              | 8.8                 |
| `POST /process-instances/incident-resolution`                                   | 8.8              | 8.8                 |
| `POST /process-instances/migration`                                             | 8.8              | 8.8                 |
| `POST /process-instances/modification`                                          | 8.8              | 8.8                 |
| `POST /process-instances/deletion`                                              | 8.9              | 8.8                 |

Earliest property version across consumers = 8.8. Not every consumer endpoint was introduced in 8.8 (`/process-instances/search` is 8.6 and `/process-instances/deletion` is 8.9), so a property-level annotation is required to cover those mismatched cases:

```yaml
# element-instances.yaml
AdvancedElementInstanceStateFilter:
  properties:
    $exists:
      type: boolean
      x-added-in-version: '8.8'
```

Had every consumer endpoint also been introduced in 8.8, no property-level annotation would be written — each endpoint's own `x-added-in-version: '8.8'` would already cover the shared schema.

#### When ancestor properties disagree across consumers

Because Rule 3 aggregates each location independently, the same upstream child property can be reached by ancestor properties that resolve to different intro versions. Suppression then only fires if every ancestor property agrees with the child property.

Real example — `OffsetPagination.from` (in `search-models.yaml`) is referenced by ~47 endpoints across versions 8.6–8.10, so its aggregated intro is 8.6. Two of its ancestor properties are:

|                  Ancestor property location                   | Consumers | Aggregated intro |
|---------------------------------------------------------------|-----------|------------------|
| `ProcessInstanceSearchQueryRequest.page` (8.6 endpoint)       | many      | 8.6              |
| `UserTaskEffectiveVariableSearchQueryRequest.page` (8.8 only) | 1         | 8.8              |

The 8.6 ancestor property matches the child property, but the 8.8 ancestor property doesn't. Rule 2 therefore does not suppress, and `from` keeps its own annotation:

```yaml
# search-models.yaml
OffsetPagination:
  properties:
    from:
      type: integer
      x-added-in-version: '8.6'    # kept — one ancestor property's intro (8.8) differs
```

If both ancestor properties had aggregated to 8.6, Rule 2 would have suppressed the child property annotation. This is exactly why `LimitPagination.limit` and `CursorBackwardPagination.limit` get no annotation in `search-models.yaml` while their siblings `OffsetPagination.limit` and `CursorForwardPagination.limit` keep one: the former two are only ever reached through `SearchQueryRequest.page` (single ancestor property, aggregated intro 8.6, matches the child property → Rule 2 suppresses); the latter two are additionally reached from statistics-query ancestor properties (`JobTypeStatisticsQuery.page` etc., introduced in 8.9), so not every ancestor property agrees with the child property's 8.6 intro and Rule 2 cannot fire.

## Local run

```bash
cd .github/scripts/x-added-in-version-check
npm ci
OCA_SPEC_PATH="$(git rev-parse --show-toplevel)/zeebe/gateway-protocol/src/main/proto/v2" \
  npm run verify:specs
```

