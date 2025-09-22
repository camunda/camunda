## Request Validation Test Generator

Generates comprehensive negative Playwright tests (QA suite format) targeting request validation surfaces (expected HTTP 400) for the Camunda Orchestration Cluster REST API.

The generator dereferences the OpenAPI spec, synthesizes a valid baseline request per operation, then mutates the body / params to produce targeted invalid scenarios. Output includes test specs plus coverage artifacts to show which scenario kinds each endpoint exercises.

### Quick Summary (Non‑Technical)

If you are not deep into the codebase, here is the 30‑second view:

* What it does: Automatically creates API tests that intentionally send broken requests to make sure the backend rejects them correctly (HTTP 400).
* Why it matters: Catches gaps in validation logic early and shows which endpoints still lack certain kinds of negative tests.
* How to run: `npm run regenerate` (it fetches the latest spec, generates tests & coverage reports).
* Where to look: `generated/` for test files, `generated/COVERAGE.md` for a table of endpoints vs. validation scenarios covered (now with % coverage per endpoint).
* When to re-run: After any API schema change (new fields, enums, unions, constraints) or before a release validation round.
* Safe to edit? Generated test files: NO (they’ll be overwritten). Extend logic by adding a new generator module instead.
* Next planned enhancement: Parameter enum violations (placeholder already reserved).

### Implemented Scenario Kinds

|                Kind                |                      Purpose                       |
|------------------------------------|----------------------------------------------------|
| `missing-required`                 | Single required property omitted                   |
| `missing-required-combo`           | Combined omissions (pairs / triples)               |
| `type-mismatch`                    | Wrong primitive type for required/important fields |
| `body-top-type-mismatch`           | Entire body wrong JSON type                        |
| `missing-body`                     | Body omitted where object expected/allowed         |
| `union`                            | Hybrid body mixing two `oneOf` variant shapes      |
| `oneof-ambiguous`                  | Body valid for multiple `oneOf` variants           |
| `oneof-none-match`                 | Body matching zero variants                        |
| `oneof-multi-ambiguous`            | (deep) Multi-variant ambiguity stress              |
| `oneof-cross-bleed`                | Required set of one variant + fields from another  |
| `discriminator-mismatch`           | Invalid discriminator value                        |
| `discriminator-structure-mismatch` | Discriminator value + wrong structure              |
| `enum-violation`                   | Invalid enum value injection                       |
| `param-missing`                    | Required path/query param omitted                  |
| `param-type-mismatch`              | Param supplied with wrong type                     |
| `param-enum-violation`             | (reserved – implement when needed)                 |
| `additional-prop-general`          | Unexpected top-level body property                 |
| `nested-additional-prop`           | Unexpected nested property                         |
| `unique-items-violation`           | Duplicate elements where `uniqueItems: true`       |
| `multiple-of-violation`            | Violates `multipleOf` constraint                   |
| `constraint-violation`             | Bounds / length / items constraint breaches        |
| `format-invalid`                   | Violates `format` (e.g. date-time, uuid)           |
| `allof-missing-required`           | Missing fields mandated by merged `allOf`          |
| `allof-conflict`                   | Conflicting values across `allOf` parts            |

All generated scenarios currently expect 400; "stateful" follow-on semantic checks are out of scope here.

### Quick Start

```
cd api-test/request-validation
npm install
npm run regenerate
```

Artifacts are written to `generated/`:

|      File       |                Description                |
|-----------------|-------------------------------------------|
| `*.spec.ts`     | Playwright tests grouped by tag/domain    |
| `MANIFEST.json` | Global scenario kind counts               |
| `COVERAGE.json` | Per-operation kind counts & missing kinds |
| `COVERAGE.md`   | Human-readable coverage table             |

View coverage:

```
less generated/COVERAGE.md
```

### CLI Flags (scripts/generate.ts)

|         Flag          |                        Description                         |                 Example                  |
|-----------------------|------------------------------------------------------------|------------------------------------------|
| `--only`              | Base kinds subset (`missing-required,type-mismatch,union`) | `--only=missing-required`                |
| `--out-dir`           | Output directory                                           | `--out-dir=../some/path`                 |
| `--qa-import-depth`   | Relative depth to QA `utils/`                              | `--qa-import-depth=4`                    |
| `--max-missing`       | Cap per-operation missing-required                         | `--max-missing=4`                        |
| `--max-type-mismatch` | Cap per-operation type-mismatch                            | `--max-type-mismatch=8`                  |
| `--only-operations`   | Comma list of operationIds                                 | `--only-operations=createGroup,getGroup` |
| `--deep`              | Enable full deep coverage set                              | `--deep`                                 |
| (removed)             | (Multipart adaptation now automatic; flags removed)        | N/A                                      |

### Spec Source

`npm run fetch-spec` downloads `specification/rest-api.yaml` from `camunda-orchestration-cluster-api` (main) and records its latest commit SHA for reproducibility.

### Regeneration Contract

Deterministic for a given spec & caps. Re-run after spec updates:

```
npm run regenerate
```

Scenario IDs are stable unless the required / enum / composition structure of schemas changes.

### Deduplication

Fingerprints: `(method, path, kind, target, bodyHash)` to collapse structurally identical mutations across overlapping generators while preserving semantic breadth.

### Integration into QA Suite

Place (or symlink) `generated/` under the QA test tree: `camunda/qa/c8-orchestration-cluster-e2e-test-suite/tests/api/v2/<module>/`. Adjust `--qa-import-depth` so relative imports reach `utils/http` correctly.

### Generated File Metadata

Each emitted spec includes:

```
/*
 * GENERATED FILE - DO NOT EDIT MANUALLY
 * Generated At: <ISO timestamp>
 * Spec Commit: <git sha>
 */
```

### Limitations / Known Gaps

* Parameter enum violations not yet emitted (`param-enum-violation`).
* Some `allOf` conflict detection is conservative to avoid false positives.
* Dedupe may hide certain exotic overlapping oneOf shapes (acceptable trade-off).
* Binary-only bodies still skipped intentionally.
* No response semantic validation (focus is request -> 400 correctness).

### Extending with New Scenario Classes

1. Add a module under `src/analysis/` returning `ValidationScenario[]`.
2. Integrate into `scripts/generate.ts` (`--deep` block unless fundamental).
3. Use a deterministic `id` (`makeId([...])`).
4. Prefer marker objects for invalid values (e.g., `{ __invalidEnum: true, value: 'X' }`).
5. Respect existing caps or introduce a new per-operation cap if high fan-out.

### License Header

Emitted test files include the Camunda License header. Generator source here is MIT-style for flexibility.

---

Happy (intentionally) breaking requests!

### Multipart Support (Experimental)

The generator now recognizes `multipart/form-data` request bodies whose schema is a top-level object and emits omission scenarios for required parts. These reuse the `missing-required` scenario kind so coverage accounting remains consistent. Emission uses Playwright's `FormData` API and does not set JSON headers.

Current scope:
* Only single-part omission (one required part removed per scenario)
* Placeholder string values (`"x"`) for other required parts (no file streaming yet)

Deferred / future enhancements:
* Additional unexpected multipart part (`additional-prop` analog)
* Type/format mismatch for textual parts
* File/binary part synthesis when schema specifies `format: binary`

If an operation supports both JSON and multipart, both sets of missing-required scenarios may appear (dedupe key includes `bodyEncoding`).

#### Automatic Multipart Adaptation

If an operation declares `multipart/form-data` and does NOT declare `application/json`, the generator automatically:

1. Converts any JSON-style body scenarios (produced via fallback body schema logic) into multipart scenarios by flattening the top-level object into `FormData` fields (non-primitives JSON-stringified).
2. Converts `missing-body` scenarios into submissions with an empty `FormData` (still exercising server 400 behavior without sending incorrect JSON headers).

This eliminates false 415 responses caused by sending `application/json` to multipart-only endpoints while preserving negative coverage semantics. Previous flags (`--suppress-json-when-multipart`, `--adapt-multipart`) have been removed because the adaptive behavior is now the default.
