# Spectral Custom Function Tests

Unit tests for the custom Spectral functions in `../spectral-functions/`.

## Running

```bash
cd zeebe/gateway-protocol
node --test spectral-tests/*.test.js
```

Requires [Spectral CLI](https://docs.stoplight.io/docs/spectral/b8391e051b7d8-installation) (globally installed or via `npx`).

## Structure

```
spectral-tests/
├── helpers.js          # Shared utilities (run spectral, filter results)
├── *.test.js           # Test files (one per custom function)
└── fixtures/
    └── <rule-name>/    # Multi-part YAML spec fixtures per rule
        ├── rest-api.yaml         # Entry point (mirrors real spec structure)
        ├── things.yaml           # Domain schemas with valid + invalid cases
        ├── search-models.yaml    # Shared models (allOf composition targets)
        └── semantic-kinds.json   # Optional: fixture-local semantic-kinds
                                  # registry (auto-loaded via the
                                  # SPECTRAL_SEMANTIC_KINDS_REGISTRY env
                                  # var by helpers.js when present).
```

## Adding tests for a new rule

1. Create a fixture directory under `fixtures/<rule-name>/` with a multi-part
   YAML spec that exercises both valid and invalid cases.
2. Create `<ruleName>.test.js` using the helpers from `helpers.js`.
3. Run `node --test spectral-tests/<ruleName>.test.js` to verify.

