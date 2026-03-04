```yaml
---
applyTo: "zeebe/gateway-protocol/**"
---
```
# Gateway Protocol Module

This module is the **single source of truth for all Camunda 8 API contracts** — both the OpenAPI 3.0.3 REST API v2 specification and the Protobuf gRPC service definition. It contains no Java source code; it produces a JAR of spec files consumed by downstream code generators and gateway implementations.

## Module Structure

```
zeebe/gateway-protocol/
├── src/main/proto/
│   ├── gateway.proto              # gRPC service definition (~1,300 lines)
│   ├── rest-api.yaml              # Redirect stub → v2/rest-api.yaml
│   ├── rest-api-v1.yaml           # Legacy REST API v1 spec (minimal, /v1 prefix)
│   └── v2/
│       ├── rest-api.yaml          # OpenAPI 3.0.3 entry point (aggregates all domains via $ref)
│       ├── <domain>.yaml          # Per-domain endpoint + schema definitions (~42 files)
│       ├── common-responses.yaml  # Shared HTTP error responses (401, 403, 400, 500, 503, 415)
│       ├── problem-detail.yaml    # RFC 9457 ProblemDetail schema
│       ├── search-models.yaml     # Shared search pagination, sort, page response schemas
│       ├── filters.yaml           # Reusable advanced filter types ($eq, $neq, $in, $like, etc.)
│       ├── keys.yaml              # Typed key schemas (LongKey base, ~16 semantic key types)
│       ├── identifiers.yaml       # String-based model IDs (ProcessDefinitionId, FormId, etc.)
│       └── cursors.yaml           # Base64 cursor schemas for keyset pagination
├── .spectral.yaml                 # Spectral ruleset (extends oas:recommended + 9 custom rules)
├── spectral-functions/            # Custom Spectral validation functions (6 JS files)
├── spectral-tests/                # Node.js test:test-based tests for custom rules
├── OPENAPI_VALIDATION.md          # Validation tool docs and common issues
└── pom.xml                        # Packages proto + v2/*.yaml into JAR (Java 8 target)
```

## Downstream Consumers

- **`gateways/gateway-model`**: Runs OpenAPI Generator on `v2/rest-api.yaml` to produce Java model classes (advanced + simple variants). The `simple` execution ID produces MCP-friendly models.
- **`zeebe/gateway-rest`**: Implements REST controllers matching the OpenAPI spec's `operationId` values.
- **`zeebe/gateway-grpc`** / **`zeebe/gateway-protocol-impl`**: Generates gRPC stubs from `gateway.proto`.
- **`clients/java`**: Java SDK uses gRPC stubs from `gateway.proto` for the `CamundaClient` API.
- **SDK generators**: External tooling consumes the OpenAPI spec for multi-language SDK generation.

## OpenAPI Spec Architecture (v2/)

The spec uses a **hub-and-spoke pattern**: `rest-api.yaml` is the entry point that aggregates all path references via `$ref` to per-domain YAML files. Each domain file (e.g., `process-definitions.yaml`, `incidents.yaml`) is self-contained with its own `paths` and `components/schemas` sections.

### Vendor Extensions

| Extension | Purpose | Validated by |
|-----------|---------|-------------|
| `x-eventually-consistent` | `true` for query ops (search/get), `false` for commands | `verifyEventuallyConsistent` rule |
| `x-semantic-type` | Typed key identity (e.g., `ProcessInstanceKey`) on key schemas in `keys.yaml` | — |
| `x-semantic-provider` | Lists which properties provide semantic identity for a schema | — |
| `x-polymorphic-schema` | Marks `oneOf` schemas used for polymorphic pagination/filter models | — |
| `x-deprecated-enum-members` | Array of `{name, deprecatedInVersion}` for deprecated enum values | `verifyDeprecatedEnumMembers` rule |

### Shared Schemas

- **Keys**: Define all `*Key` types in `keys.yaml` extending `LongKey` (string with `^-?[0-9]+$` pattern). Always use `type: string` for key properties — never `integer`.
- **Identifiers**: String-based model IDs in `identifiers.yaml` with format/pattern constraints.
- **Filters**: Reusable filter types in `filters.yaml` — `BasicStringFilterProperty`, `StringFilterProperty` (with `$like`), `IntegerFilterProperty`, `DateTimeFilterProperty`, etc.
- **Search**: All search endpoints compose `SearchQueryRequest` (from `search-models.yaml`) with domain-specific filter and sort schemas. Responses compose `SearchQueryResponse` which includes `SearchQueryPageResponse` with cursor pagination.
- **Errors**: Use `$ref` to `common-responses.yaml` for standard error responses; always include `ProblemDetail` from `problem-detail.yaml`.

## Adding a New Domain Endpoint

1. Create `v2/<domain>.yaml` with `paths` and `components/schemas` sections following existing patterns (mirror `incidents.yaml` or `process-definitions.yaml`).
2. Add path entries in `v2/rest-api.yaml` under the appropriate comment section, using `$ref` with URL-encoded path syntax (`~1` for `/`).
3. Add a tag in the `tags` array of `rest-api.yaml`.
4. Set `x-eventually-consistent: true` on query operations (GET, POST to `/search` or `/statistics`), `false` on commands (POST/PUT/PATCH/DELETE that mutate state).
5. Reference key types from `keys.yaml`, identifiers from `identifiers.yaml`, error responses from `common-responses.yaml`.
6. Every schema property **must** have a `description`.
7. Response array properties **must** be listed in `required` and **must not** be `nullable`.
8. Run Spectral validation before committing.

## Spectral Validation Rules

The `.spectral.yaml` ruleset extends `spectral:oas` recommended rules plus 9 custom rules:

| Rule | Severity | What it enforces |
|------|----------|-----------------|
| `no-period-in-summary` | error | Path summaries must not end with a period |
| `no-flow-node-in-*` (4 rules) | error | Use "element" instead of "flow node" everywhere |
| `require-property-descriptions` | error | All schema properties need descriptions |
| `operation-key-properties-must-be-strings` | warn | Path `*Key` parameters must be `type: string` |
| `schema-key-properties-must-be-strings` | warn | Schema `*Key` properties must be `type: string` (or compose `BasicStringFilterProperty`) |
| `required-properties-must-exist` | error | Every `required` entry must match a property key |
| `array-properties-must-be-required` | error | Response arrays must be `required` and non-nullable |
| `no-eventually-consistent-on-commands` | error | Command operations must not have `x-eventually-consistent: true` |
| `valid-deprecated-enum-members` | error | `x-deprecated-enum-members` must reference existing enum values with valid semver |

### Running Validation

```bash
# Two passes: entry-point resolution + file-level schema validation
spectral lint zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml \
  --ruleset zeebe/gateway-protocol/.spectral.yaml --fail-severity error
spectral lint zeebe/gateway-protocol/src/main/proto/v2/*.yaml \
  --ruleset zeebe/gateway-protocol/.spectral.yaml --fail-severity error

# Run custom rule unit tests
cd zeebe/gateway-protocol && node --test spectral-tests/*.test.js
```

## gRPC Protocol (`gateway.proto`)

- Package: `gateway_protocol`, Java package: `io.camunda.zeebe.gateway.protocol`
- Defines the `Gateway` service with ~20 RPCs (ActivateJobs, CreateProcessInstance, CompleteJob, etc.)
- Uses `proto3` syntax with `java_multiple_files = false`
- Message types include nested enums (e.g., `ActivatedJob.JobKind`, `ActivatedJob.ListenerEventType`)
- The gRPC API is the **legacy API**; new endpoints are added to the OpenAPI REST spec, not here

## Common Pitfalls

- Never use `required: false` as a boolean on a property — `required` is an array at the object level.
- Never add `x-eventually-consistent: true` to command (mutating) operations.
- Never use `type: integer` for `*Key` properties — always `type: string` referencing `keys.yaml`.
- Never omit `description` from schema properties — Spectral enforces this.
- Never make response array properties optional or nullable — the gateway coerces `null` to `[]`.
- Do not edit `rest-api.yaml` (root-level redirect) — the real spec is at `v2/rest-api.yaml`.
- Do not modify the OpenAPI spec for MCP-specific schema shaping — use `gateways/gateway-model/pom.xml` instead.

## Key Reference Files

- `src/main/proto/v2/rest-api.yaml` — OpenAPI entry point aggregating all domains
- `src/main/proto/v2/search-models.yaml` — Shared search query/response/pagination schemas
- `src/main/proto/v2/filters.yaml` — Reusable advanced filter type definitions
- `src/main/proto/v2/keys.yaml` — All typed key schemas with `x-semantic-type`
- `src/main/proto/gateway.proto` — gRPC `Gateway` service and message definitions
- `.spectral.yaml` — Validation ruleset with custom functions
- `OPENAPI_VALIDATION.md` — Validation docs, common issues, CI integration notes