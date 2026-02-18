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

## Running Validation Locally

```bash
# Install Spectral CLI (if not already installed)
npm install -g @stoplight/spectral-cli

# Run validation from the repository root
spectral lint zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml \
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

## CI Integration

Spectral runs automatically in the `openapi-lint` CI job when OpenAPI files are modified. See `.github/workflows/ci.yml` for details.
