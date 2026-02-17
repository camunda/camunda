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

## CI Integration

Spectral runs automatically in the `openapi-lint` CI job when OpenAPI files are modified. See `.github/workflows/ci.yml` for details.
