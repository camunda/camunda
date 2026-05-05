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
  - Semantic graph annotation shape + cross-reference validation (`x-semantic-establishes`, `x-semantic-requires`, `semantic-kinds.json` registry)

## Running Validation Locally

```bash
# Install Spectral CLI (if not already installed)
npm install -g @stoplight/spectral-cli

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

### Semantic graph annotations (`x-semantic-establishes`, `x-semantic-requires`)

Three rules validate the producer/consumer dependency annotations consumed by the API test generator:

- `semantic-establishes-shape` — `x-semantic-establishes` must conform to the documented schema (`kind`, optional `shape`, `identifiedBy[]` with `in` / `name` / `semanticType` / optional `acceptsExternal: true`).
- `semantic-requires-shape` — `x-semantic-requires` must conform to the documented schema (`kind`, `bind` map of `from` / `name`).
- `verify-semantic-kinds-registered` (custom JS function in `spectral-functions/`) — every `kind:` appears in `semantic-kinds.json`; every required kind is established somewhere in the spec; every `identifiedBy` / `bind` member references a parameter or top-level body property that exists on the operation; operations must not directly establish/require kinds whose registry shape is `external-entity`; `identifiedBy[].acceptsExternal: true` skips the producer-existence cross-reference for that tuple while still running single-owner resolution.

For the full annotation reference (including `x-semantic-provider`, `x-semantic-client-minted`, kind-level `shape: external-entity`, and the per-tuple `acceptsExternal` flag), see §2.18 of [`docs/rest-api-endpoint-guidelines.md`](../../docs/rest-api-endpoint-guidelines.md). The kind registry itself is [`src/main/proto/v2/semantic-kinds.json`](src/main/proto/v2/semantic-kinds.json) — its `$comment` block documents how to add a new kind.

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
