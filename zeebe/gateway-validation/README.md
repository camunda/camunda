# Zeebe Gateway Domain Validation

High-performance validation of REST request objects against logically grouped polymorphic request variants ("one-of groups"). Provides:

- Annotation-driven request typing via `@OneOfGroup`.
- Generated immutable descriptors (groups + branches) at build time from `rest-api.domain.yaml`.
- Structural, enum, pattern, extra-field, and JSON token kind validation.
- Deterministic resolution (select exactly one matching branch or report NO_MATCH / AMBIGUOUS).
- Rich machine-readable diagnostics appended as JSON to the Bean Validation message.
- Optional raw JSON token capture (including nested) to distinguish e.g. number vs string tokens.
- Thread-local exposure of matched branch id, plus automatic per-request cleanup and header emission.

## Annotation
```java
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OneOfGroup {
	String value();                // group id
	boolean strictExtra() default false;        // reject properties not declared in branch
	boolean strictTokenKinds() default false;   // enforce JSON token kinds
	boolean captureRawTokens() default false;   // capture token kinds for diagnostics/observability
	boolean failOnAmbiguous() default false;    // treat multiple equally specific matches as error
}
```

## Error Codes
`ValidationErrorCode` enum currently includes (subset):
- `NO_MATCH` – no branch satisfied constraints
- `AMBIGUOUS` – multiple branches tied (and `failOnAmbiguous=true`)
- `GROUP_NOT_FOUND` – descriptor absent
- `INTERNAL_ERROR` – unexpected runtime issue

## Diagnostic Payload Format
When a validation error is raised, the message template is:
```
CODE:{jsonPayload}
```
`{jsonPayload}` schema (simplified):
```json
{
	"groupId": "BatchOperationKey",
	"code": "NO_MATCH",
	"summary": "branch=1(5) missing /foo | branch=2(3) extra /bar",
	"branchFailures": [
		{
			"branchId": 1,
			"specificity": 5,
			"reasons": ["missing /foo"]
		},
		{
			"branchId": 2,
			"specificity": 3,
			"reasons": ["extra /bar"]
		}
	]
}
```
JSON Pointers escape `~` → `~0` and `/` → `~1` per RFC 6901.

## Token Kind Capture & Enforcement
If either `captureRawTokens` or `strictTokenKinds` is true, a Jackson `TokenCaptureModule` is auto-registered (classpath scan for annotated DTOs) and records token kinds for top-level and nested object/array members, using pointer-like keys (e.g. `prop/nested/0/name`).

- Enforcement (`strictTokenKinds`) currently validates top-level and first-level nested members (Map/List). Mismatches produce reason `token-kind-mismatch /pointer got=ACTUAL`.
- Capture-only mode (`captureRawTokens`) skips enforcement but still records kinds for logging/analytics.

## Matched Branch Exposure
On success, the `OneOfGroupValidator` stores the winning branch id in a thread-local accessible via:
```java
OneOfGroupValidator.getLastMatchedBranchId();
```
A Spring `OneOfGroupCleanupFilter` adds header `X-OneOf-Matched-Branch` (if present) and clears the ThreadLocal + adds an MDC key `oneOfBranch` for log correlation.

## Controller Usage
Annotate your request type parameter:
```java
public ResponseEntity<?> create(
		@Validated @RequestBody @OneOfGroup(value = "BatchOperationKey", strictExtra = true, strictTokenKinds = true, captureRawTokens = true)
		BatchOperationKeyRequest body) {
	// matched branch available via OneOfGroupValidator.getLastMatchedBranchId()
	return ResponseEntity.ok().build();
}
```

## REST Error Mapping
`RestErrorMapper` parses validation messages, extracts the JSON, and populates a Spring `ProblemDetail` with properties:
- `validationCode`
- `groupId`
- `summary` (from payload)
- `branchFailures` (array) when present

## Testing Summary
Core tests cover:
- Required/optional & specificity selection
- Enum and pattern validation
- Strict extra-field rejection
- Ambiguity detection (failOnAmbiguous)
- Token kind mismatch (including nested one-level)
- POJO reflection fallback
- Error mapping to `ProblemDetail`
- Filter cleanup & header emission

## Performance Notes
- Descriptor specificity is precomputed at generation time.
- Validation short-circuits per-branch on first failing reason (reasons still recorded for mismatched branches needed for summary).
- Token kind map lookup is O(1) per property.

## Future Enhancements
- Deeper nested token enforcement (beyond first nested level) with descriptor hints.
- Build-time generation of the capture target list to remove runtime scan.
- Exposure of matched branch id in structured telemetry events.
- More granular reason codes or structured objects instead of reason strings.

## Contributing
Run (examples):
```
./mvnw -T1C -Dquickly -pl zeebe/gateway-validation test
```
Ensure formatting & checks:
```
./mvnw verify -DskipTests -T1C -Dquickly -Dspotless.checks.skip=false
```

---
Owner: validation
