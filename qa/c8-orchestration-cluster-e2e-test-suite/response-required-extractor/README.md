# response-required-extractor

Extract required/optional response field lists from the Camunda Orchestration Cluster OpenAPI specification.

## Usage

Build (or run directly with tsx):

```bash
npm install
npm run build
npm run generate
```

Or in dev mode:

```bash
npm run dev
```

Output is written to `output/responses.json` (override with `OUTPUT_DIR`).

```bash
OUTPUT_DIR=artifacts npm run generate
```

The output JSON schema is used by the `utils/route-test` test utility to assert specification-compliant response bodies in tests. Refer to that project for usage details.

## CI

The script prints a one-line summary and produces a deterministic JSON file with metadata (commit, hash, timestamp).
