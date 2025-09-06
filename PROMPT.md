Handoff Prompt for Next Copilot Instance (Validation & REST Error Mapping)

Project Summary Monorepo (Java 21, Maven) with Zeebe gateway REST module. New custom one-of group validation system implemented in module gateway-validation and integrated into gateway-rest. Branch: custom-validate.

Implemented Features

Annotation @OneOfGroup (flags: strictExtra, strictTokenKinds, captureRawTokens, failOnAmbiguous).
Generic OneOfGroupValidator (Bean Validation ConstraintValidator) with:
Group + branch descriptors generated at build (generator already runs; provider class written to target/generated-sources).
Matching: required, optional, enum literals, regex patterns, strict extra field rejection.
Specificity selection; ambiguity handling (error if failOnAmbiguous=true).
Token kind enforcement (top-level + first nested level for Map/List) when strictTokenKinds=true.
Raw token kind capture (including nested) via auto-registered Jackson TokenCaptureModule.
Structured JSON error payload appended to message: CODE:{json} with groupId, summary, branchFailures.
Reason codes embedded as strings: missing, extra, invalid-enum, pattern-mismatch, token-kind-mismatch.
ThreadLocal matched branch id (public accessors).
Auto classpath scan (in JacksonConfig) for classes annotated with @OneOfGroup to register token capture module.
REST integration:
RestErrorMapper parses validation messages and builds Spring ProblemDetail with properties (validationCode, groupId, summary, branchFailures).
OneOfGroupCleanupFilter adds header X-OneOf-Matched-Branch, sets MDC key oneOfBranch, clears ThreadLocal.
Nested token kind capture map uses pointer-like keys (prop/sub/0/name).
README updated with usage, schema, flags, future work.
Tests present (validation core, error mapping, filter). Full gateway-rest test suite previously skipped under -Dquickly; now runs when skip flags overridden.
Current Usage Example Controller parameter-level annotation added in MessageController:

Important: Token capture instrumentation only happens if the model class itself is annotated (class-level) or a wrapper class is annotated; parameter-only annotation triggers validation but not custom deserializer binding.

Key Files

OneOfGroupValidator.java
TokenCaptureModule.java
JacksonConfig.java
zeebe/gateway-rest/.../RestErrorMapper.java
zeebe/gateway-rest/.../web/OneOfGroupCleanupFilter.java
README.md
Runtime Flow

Jackson deserializes request (with capturing deserializer if class annotated).
Spring method validation (needs @Validated on controller or @Valid/@Validated on parameter) invokes OneOfGroupValidator.
On failure, Bean Validation message carries CODE:{json}. Mapped to ProblemDetail.
On success, ThreadLocal branch id captured; filter emits header & clears state.
Constraints & Style

Follow existing Maven/Spotless formatting.
Avoid introducing new third-party dependencies.
Keep diagnostics backward compatible (existing codes/payload shape).
Do not degrade performance (avoid repeated reflection per request).
Open / Next Tasks (Backlog)

Deep nested token kind enforcement beyond first level (currently only one level for Map/List).
AMBIGUOUS error ProblemDetail test (ensure parser handles ambiguity payload).
Success instrumentation test asserting X-OneOf-Matched-Branch header appears for a validated endpoint.
Ensure controller classes using parameter-only annotation either migrate to class-level or add wrappers so token capture works (decide policy; possibly document).
Add logging pattern documentation referencing MDC key oneOfBranch.
Optional: Convert reason strings to structured objects (kind, pointer, detail) internally while preserving outward JSON shape.
Optional performance pass:
Cache merged required+optional arrays per branch.
Precompute allowed property set for strictExtra instead of rebuilding per validation.
Build-time generated list of @OneOfGroup classes (remove runtime scan) – implement annotation processor or generator step writing index for JacksonConfig to read.
Expand tests:
Nested array/object token kind mismatch deeper than one level (after enhancement).
failOnAmbiguous=false path chooses most specific branch (ensure ThreadLocal set).
Add telemetry hook (e.g., Micrometer tag with branch id) if desired.
Verification Commands (examples) Run validation tests only: ./mvnw -pl zeebe/gateway-validation test -Dquickly Run gateway-rest with tests (disable skip): ./mvnw -pl zeebe/gateway-rest -am test -DskipTests=false -Dquickly

Adoption Checklist for New Endpoint

Annotate DTO class (preferred) with @OneOfGroup + flags.
Ensure DTO has optional Map<String,String> tokens field for capture (no accessor required).
Add @Validated on controller class if not present.
Add parameter annotation if class-level not feasible.
Add test expecting structured ProblemDetail or success header.
Risks / Caveats

Parameter-only annotation does not trigger token capture; may lead to missing token-kind-mismatch reasons when strictTokenKinds=true (enforcement depends on captured map).
ThreadLocal must always be cleared; filter currently handles this—confirm it’s registered in every servlet context (review component scanning if new modules added).
Reason strings currently flat; changing format could affect consumers (be cautious).
Immediate Suggested Next Step Decide whether to enforce class-level annotation for token capture (update README + maybe add runtime warning if strictTokenKinds=true but no tokens captured for properties).

End of handoff prompt.
