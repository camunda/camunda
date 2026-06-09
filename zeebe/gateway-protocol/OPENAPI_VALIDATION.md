# OpenAPI Validation

This directory contains the OpenAPI specifications for the Camunda REST API and related validation tools.

## Validation Tool

We use [Spectral CLI](https://docs.stoplight.io/docs/spectral/) to validate the OpenAPI specifications.

- **Ruleset**: `.spectral.yaml`
- **Custom functions**: `spectral-functions/` directory
- **What it catches**:
  - Schema structure errors (e.g., `required` field misplacement)
  - Invalid schema property combinations
  - Type mismatches in schema definitions
  - Schema compliance with OpenAPI 3.0 JSON Schema spec
  - Custom naming conventions (e.g., no "flow node" terminology)
  - Property description requirements
  - Key property type validation (`...Key` properties must be strings)
  - Eventually-consistent annotation validation (command operations must not be marked as eventually consistent)
  - Required property existence validation (entries in required array must exist in properties)
  - Operation versioning annotation validation (every operation must declare `x-added-in-version`)
  - Property versioning annotation shape validation (`x-properties-added-in-version` entries must be `{propertyName, addedInVersion}` only)
  - Semantic graph annotation shape + cross-reference validation (`x-semantic-establishes`, `x-semantic-requires`, `semantic-kinds.json` registry)

> Property-level version annotations (`x-properties-added-in-version`) have their shape checked by the Spectral rule `properties-added-in-version-shape` (see below). Their semantic correctness (property exists, version is right) is validated in CI by a separate Node verifier under [`.github/scripts/x-added-in-version-check/`](../../.github/scripts/x-added-in-version-check/README.md). See §2.17 of [`docs/rest-api-endpoint-guidelines.md`](../../docs/rest-api-endpoint-guidelines.md) for the rules.

## Running Validation Locally

```bash
# Install Spectral CLI (if not already installed)
npm install -g @camunda8/spectral-cli

# Run validation from the repository root

# This first pass validates schema structure
spectral lint zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml \
  --ruleset zeebe/gateway-protocol/.spectral.yaml \
  --fail-severity error

# This second pass is necessary for file-level analysis
spectral lint zeebe/gateway-protocol/src/main/proto/v2/*.yaml \
  --ruleset zeebe/gateway-protocol/.spectral.yaml \
  --fail-severity error
```

## Common Issues

### `required: false` at Property Level

**Wrong:**

```yaml
properties:
  myField:
    type: string
    required: false  # ❌ Invalid!
```

**Correct:**

```yaml
properties:
  myField:
    type: string
# If field is optional, simply don't include it in the required array at object level
# If field is required:
required:
  - myField  # ✓ Correct - array at object level
```

### `x-eventually-consistent` on Command Operations

Command (mutating) operations must not be marked with `x-eventually-consistent: true`.

**Wrong:**

```yaml
paths:
  /resources:
    post:
      operationId: createResource
      x-eventually-consistent: true  # ❌ Invalid for command operations!
```

**Correct:**

```yaml
paths:
  /resources:
    post:
      operationId: createResource
      x-eventually-consistent: false  # ✓ Correct for commands (create, update, delete)
  /resources/search:
    post:
      operationId: searchResources
      x-eventually-consistent: true   # ✓ Correct for query operations (search, get, statistics)
```

Query operations (GET requests, POST to `/search` or `/statistics` endpoints) read from eventually-consistent projections and should use `x-eventually-consistent: true`. Command operations (POST, PUT, PATCH, DELETE that create/modify/delete resources) are synchronous writes and must use `x-eventually-consistent: false`. See [#45968](https://github.com/camunda/camunda/issues/45968) for details.

### Array Properties Must Be Required and Non-Nullable

Response arrays must always be listed in `required` and must not be `nullable`. The gateway coerces `null` arrays to `[]`, so the spec should reflect the actual runtime behaviour.

**Wrong:**

```yaml
schema:
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/Thing'
# Missing from required array
```

**Correct:**

```yaml
schema:
  required:
    - items
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/Thing'
```

See [#46224](https://github.com/camunda/camunda/issues/46224) for details.

### Missing `x-added-in-version` on an Operation

Every operation must declare the Camunda version in which it was first introduced via the `x-added-in-version` extension. The `require-added-in-version` rule fails the build if this annotation is missing.

**Wrong:**

```yaml
paths:
  /my-resources:
    post:
      operationId: createMyResource
      summary: Create a my-resource
      tags: [My resource]
      # ❌ Missing x-added-in-version
```

**Correct:**

```yaml
paths:
  /my-resources:
    post:
      operationId: createMyResource
      summary: Create a my-resource
      x-added-in-version: "8.9"  # ✓ String containing the minor version (no patch)
      tags: [My resource]
```

Set the value to the version in which the endpoint **first** ships and do not update it on later changes. See §2.17 of [`docs/rest-api-endpoint-guidelines.md`](../../docs/rest-api-endpoint-guidelines.md) for the full convention.

### Malformed `x-properties-added-in-version`

The `properties-added-in-version-shape` rule (severity `error`) checks the shape of every `x-properties-added-in-version` annotation. It must be a non-empty array of `{propertyName, addedInVersion}` objects with no other keys, where `addedInVersion` matches `^[0-9]+\.[0-9]+(\.[0-9]+)?$` (e.g. `"8.8"`).

The rule runs with `resolved: false` so paths point at the schema's source file. It fires in the file-level pass (`spectral lint "...*.yaml"`), not the entry-point pass on `rest-api.yaml`.

**Wrong:**

```yaml
ProcessInstanceModificationInstruction:
  x-properties-added-in-version:
    - propertyame: moveInstructions   # ❌ typo: should be propertyName
      addedInVersion: "8.8"
```

**Correct:**

```yaml
ProcessInstanceModificationInstruction:
  x-properties-added-in-version:
    - propertyName: moveInstructions
      addedInVersion: "8.8"
```

Semantic correctness of these annotations (does the property actually exist? was it really introduced in that version?) is still validated in CI by the verifier under [`.github/scripts/x-added-in-version-check/`](../../.github/scripts/x-added-in-version-check/README.md) — the Spectral rule only checks shape.

### Semantic graph annotations (`x-semantic-establishes`, `x-semantic-requires`)

Three rules validate the producer/consumer dependency annotations consumed by the API test generator:

- `semantic-establishes-shape` — `x-semantic-establishes` must conform to the documented schema (`kind`, optional `shape`, `identifiedBy[]` with `in` / `name` / `semanticType` / optional `acceptsExternal: true`).
- `semantic-requires-shape` — `x-semantic-requires` must conform to the documented schema (`kind`, `bind` map of `from` / `name`).
- `verify-semantic-kinds-registered` (custom JS function in `spectral-functions/`) — every `kind:` appears in `semantic-kinds.json`; every required kind is established somewhere in the spec; an operation's `x-semantic-establishes.shape` (defaulting to `entity` when omitted) must match the kind's registered shape, so e.g. forgetting `shape: edge` on a membership operation is a lint error rather than a style issue; every `identifiedBy` / `bind` member references a parameter or top-level body property that exists on the operation; operations must not directly establish/require kinds whose registry shape is `external-entity`; `identifiedBy[].acceptsExternal: true` skips the producer-existence cross-reference for that tuple while still running single-owner resolution.

For the full annotation reference (including `x-semantic-provider`, `x-semantic-client-minted`, kind-level `shape: external-entity`, and the per-tuple `acceptsExternal` flag), see §2.18 of [`docs/rest-api-endpoint-guidelines.md`](../../docs/rest-api-endpoint-guidelines.md). The kind registry itself is [`src/main/proto/v2/semantic-kinds.json`](src/main/proto/v2/semantic-kinds.json) — its `$comment` block documents how to add a new kind.

### Required-permission annotations (`x-required-permissions`)

Two rules validate the canonical endpoint → required-permission binding consumed by the API test generator and docs (camunda/camunda#54727):

- `required-permissions-shape` — each `x-required-permissions` entry must conform to the documented schema (exactly one of: a static `{resourceType, permissionType}` pair, an `anyOf` OR-group, or `{dynamic: true, note}`).
- `verify-required-permissions` (custom JS function in `spectral-functions/`) — every operation under `paths` must declare `x-required-permissions` (an array; `[]` means unrestricted — no specific permission required, though the endpoint is still authenticated), and every static `{resourceType, permissionType}` pair must be a valid pair per [`src/main/proto/v2/resource-permissions.json`](src/main/proto/v2/resource-permissions.json). Adding a new endpoint without a declared binding fails the build. It also checks that an operation marked `x-permission-enforcement: filter` declares at least one required permission.
- `permission-enforcement-shape` — the optional operation-level `x-permission-enforcement` marker, when present, must be `reject` (default deny → `4xx`) or `filter` (deny → `200` with results scoped to authorized resources). Used by the runtime auth test (api-test-generator#374) to assert the correct deny shape.

The registry `resource-permissions.json` mirrors `AuthorizationResourceType.buildResourcePermissionsMap()` and is kept honest by `ResourcePermissionsRegistryTest`. For the full reference see §2.19 of [`docs/rest-api-endpoint-guidelines.md`](../../docs/rest-api-endpoint-guidelines.md) and ADR [`docs/adr/security/001-endpoint-required-permission-mapping.md`](../../docs/adr/security/001-endpoint-required-permission-mapping.md).

## Testing Custom Spectral Functions

Unit tests for the custom Spectral functions live in `spectral-tests/`. Each rule has its own test file and a set of multi-part YAML fixture specs that mirror the real spec structure (entry-point `rest-api.yaml` with `$ref`s to domain and shared-model files).

### Running Tests

```bash
# From zeebe/gateway-protocol/
node --test spectral-tests/*.test.js

# Run a specific rule's tests
node --test spectral-tests/verifyArrayPropertiesRequired.test.js
```

### Structure

```
spectral-tests/
├── helpers.js                          # Shared: lintFixture(), filterByRule(), filterByPathSegment()
├── verifyArrayPropertiesRequired.test.js
└── fixtures/
    └── array-required/                 # Multi-part fixture for this rule
        ├── rest-api.yaml               # Entry point ($ref to things.yaml)
        ├── things.yaml                 # Paths + schemas with valid/invalid cases
        └── search-models.yaml          # Shared SearchQueryResponse for allOf composition
```

### Adding Tests for a New Rule

1. Create `spectral-tests/fixtures/<rule-name>/` with a multi-part YAML spec exercising valid and invalid cases.
2. Create `spectral-tests/<ruleName>.test.js` using `lintFixture()` and `filterByRule()` from `helpers.js`.
3. Run `node --test spectral-tests/<ruleName>.test.js` to verify.

Tests invoke `spectral lint` on the fixture spec using the production `.spectral.yaml` ruleset, then assert on the JSON results. This ensures custom functions are tested end-to-end through Spectral's resolution and rule engine — not just in isolation.

## CI Integration

Spectral runs automatically in the `openapi-lint` CI job when OpenAPI files are modified. See `.github/workflows/ci.yml` for details.
