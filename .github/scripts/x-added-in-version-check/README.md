# `x-added-in-version` annotation check

CI-only tooling that verifies the Camunda 8 REST OpenAPI specs under
[`zeebe/gateway-protocol/src/main/proto/v2/`](../../../zeebe/gateway-protocol/src/main/proto/v2/)
are correctly annotated with `x-added-in-version` (on every operation) and
`x-properties-added-in-version` (on every schema parent that has versioned
properties).

The authoritative version map is generated from the
[camunda/return-of-api-added-in-analysis](https://github.com/camunda/return-of-api-added-in-analysis)
repo; the endpoint map is bundled from the camunda spec via
[camunda-schema-bundler](https://github.com/camunda/camunda-schema-bundler).
This directory consumes both and reports any drift.

## Used by

| Workflow | npm script |
|---|---|
| [`verify-x-added-in-version-annotations.yml`](../../workflows/verify-x-added-in-version-annotations.yml) (scheduled / dispatch) | `verify:specs:workflow` |
| `openapi-x-added-in-version-check` job in [`ci.yml`](../../workflows/ci.yml) (PR / push, warnings only) | `verify:specs:ci` |

## Layout

```
build-endpoint-map.mjs    # fetches camunda specs → artefacts/endpoint-map.json
get-version-map.mjs       # clones return-of-api-added-in-analysis → artefacts/version-map.json
build-artefacts.mjs       # runs the two above in parallel
verify-specs.mjs          # shared verification core (used by both CI variants)
verify-specs-workflow.mjs # entry point: scheduled workflow (fails on drift)
verify-specs-ci.mjs       # entry point: PR check (annotation-only, never fails)
artefacts/                # generated; gitignored (endpoint-map.json, version-map.json, bundler-specs/)
```

## Env

| Var | Default | Purpose |
|---|---|---|
| `OCA_SPEC_PATH` | _required_ | Directory of multi-file specs to verify (CI sets it to `zeebe/gateway-protocol/src/main/proto/v2`) |
| `CAMUNDA_REF` | `main` | Git ref of `camunda/camunda` to fetch the endpoint map from |
| `RETURN_OF_API_REF` | `main` | Git ref of `return-of-api-added-in-analysis` to fetch the version map from |
| `ENDPOINT_MAP_PATH` | `./artefacts/endpoint-map.json` | Where the endpoint map is written/read |
| `VERSION_MAP_PATH` | `./artefacts/version-map.json` | Where the version map is written/read |
| `BUNDLER_SPECS_DIR` | `./artefacts/bundler-specs` | Persistent cache; `build:bundler:latest` only refetches the latest version |

## Local run

```bash
cd .github/scripts/x-added-in-version-check
npm ci
OCA_SPEC_PATH="$(git rev-parse --show-toplevel)/zeebe/gateway-protocol/src/main/proto/v2" \
  npm run verify:specs
```
