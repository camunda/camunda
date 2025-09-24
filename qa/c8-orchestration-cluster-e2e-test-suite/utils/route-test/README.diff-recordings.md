# diff-recordings-vs-spec

Utility script to recommend promoting optional fields to required based on empirical recordings.

## Inputs

- Recording directory: Either first CLI arg or `TEST_RESPONSE_BODY_RECORD_DIR` env var.
- Threshold percentage: Second CLI arg (default 100). Field must appear in at least this % of samples to be suggested.
- Spec source: `responses.json` (default extractor output) or override via `ROUTE_TEST_RESPONSES_FILE`.

## Behavior

1. Loads `responses.json` and builds index of required vs optional top-level fields per (path, method, status).
2. Reads each `*.jsonl` file in the recording directory (produced by the recorder) accumulating counts for:
   - `present` (top-level fields present in a sample)
   - `deepPresent` (deep JSON Pointer paths under optional parents)
3. For each route/method/status group, determines which optional fields (or deep paths under optional parents) meet the presence threshold across all samples.
4. Emits JSON report (to stdout) with promotion candidates and writes a concise human summary to stderr.

## Example

```bash
# 100% threshold – only always-present optional fields
npx ts-node diff-recordings-vs-spec.ts ./recordings > promotion.json

# 90% threshold
npx ts-node diff-recordings-vs-spec.ts ./recordings 90 > promotion.json
```

Sample human summary line (stderr):

```
GET 200 /v1/things  samples=42  promote: foo:100%, bar:95%
```

## Output JSON Shape

```json
{
  "generatedAt": "2024-01-01T00:00:00.000Z",
  "thresholdPct": 100,
  "totalGroups": 3,
  "items": [
    {
      "route": "/v1/things",
      "method": "GET",
      "status": "200",
      "samples": 42,
      "specRequired": ["id"],
      "specOptional": ["foo", "bar"],
      "candidates": [
        {"field": "foo", "pct": 100, "count": 42, "level": "top", "reason": "Observed in 42/42 samples (100.0%)"},
        {"field": "/foo/baz", "pct": 100, "count": 42, "level": "deep", "reason": "Deep presence under optional parent foo in 42/42 samples (100.0%)"}
      ]
    }
  ]
}
```

## Notes

- Ignores recordings whose method or status are missing (ANY) because they cannot be precisely matched to spec entries.
- Deep promotion suggestions only appear for pointers whose top-level parent field is optional (and always present at the given threshold).
- Fields not declared in the spec (extras) are never suggested; they should first be added as optional manually if valid.

## Next Steps

Automate applying promotions by rewriting the source OpenAPI or the intermediate `responses.json` before regeneration. This script provides the decision data; application is intentionally manual for safety.
