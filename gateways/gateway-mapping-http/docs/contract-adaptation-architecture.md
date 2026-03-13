# Contract Adaptation Architecture (Generation-First)

Tracking issue: [#48123 — Compile time data contract for data layer](https://github.com/camunda/camunda/issues/48123)

## Problem

The API contract and search-domain entities evolve independently. Currently there is no compile-time
reasoning about the system's data contract at the data layer. Regressions are caught — when they are
caught — in integration testing, leading to cross-team coordination friction, late-cycle detection,
and rework.

In addition:

- **Business policy and DTO mapping mechanics are mixed in one layer.**
  `SearchQueryResponseMapper` mixes contract enforcement, coercion/defaulting policy, and
  mechanical DTO mapping in a single class.
- **Mechanical concerns are hand-maintained.**
  Deterministic transformations (key coercion, nested traversal, field projection) are replicated
  manually even though they are derivable from schema semantics.
- **Hand-written nested traversal is incomplete by construction risk.**
  When the OpenAPI schema adds or reshapes nested fields, mapper updates depend on engineer
  reasoning and can miss sub-fields.

## Goals

The following goals are drawn from the tracking issue and from design insights that emerged during
implementation.

### From the tracking issue

1. **Move API regression detection to the compiler level.**
   Null-handling decisions become explicit at the mapper boundary. Contract drift is caught during
   coding, not in regression tests.

2. **Generate a strict boundary SDK.**
   Boundary models that are null-safe by construction: immutable, with explicit nullability
   semantics.

3. **Turn nullness into hard compiler/IDE errors.**
   Engineers get immediate feedback when a nullable entity field is assigned to a non-null contract
   field without handling.

4. **Make the boundary mandatory in architecture.**
   All outbound data passes through boundary mappers. Architecture tests prevent controllers from
   exposing internal entities directly.

### Discovered during implementation

5. **Separate business policy from mechanical mapping.**
   Business decisions (fail-fast, default, derive) belong in a policy layer. Shape-mapping to
   protocol DTOs is mechanical and can be generated.

6. **Eliminate hand-coded mechanical boilerplate.**
   Deterministic key coercion (`Long` → `String`), nested object/list traversal, and field
   projection are generated from the OpenAPI schema graph rather than replicated in mapper code.

7. **Guarantee schema-graph traversal completeness.**
   Generated code follows the specification graph, so nested-field handling is not limited by what
   an engineer remembered to traverse.

## Architecture

### Target layers

1. **Search Domain Layer**
   - Keeps storage-shaped entities (`io.camunda.search.entities.*`).
   - No API-contract enforcement here.
2. **Contract Adaptation Layer (Policy Layer)**
   - Hand-written adapters convert internal entities to generated strict contract views.
   - Contains business policy for missing/invalid source values: fail fast, default, derive.
   - This is the only place for null-handling decisions and business logic.
   - Deterministic type coercion driven by schema semantics (for example key `Long` → `String`)
     is generated into strict contract DTOs — not hand-coded here.
   - Shared policy operations are centralized in `ContractPolicy`.
3. **Search Mapper Layer (Mechanical Layer)**
   - Accepts only strict contract views.
   - Performs only shape mapping to generated protocol DTOs.
   - No coercion/default/policy logic.
   - Entirely generated.

### Data flow

```
SearchDomainEntity
  → ContractAdapter (hand-written business policy)
    → GeneratedStrictContract (generated, null-safe record)
      → GeneratedResultMapper.toProtocol() (generated, mechanical)
        → Protocol DTO (returned to controller)
```

### Generated artifacts

From the OpenAPI spec (`zeebe/gateway-protocol/src/main/proto/v2/*.yaml`), the generator produces:

- **Strict contract DTOs** — immutable Java records with:
  - `Objects.requireNonNull` validation for required non-nullable fields.
  - `@Nullable` annotation for nullable fields.
  - Typed step-builder that enforces required fields at compile time.
  - LongKey coercion encapsulated in builder accept methods.
  - Recursive nested object/list coercion from the schema graph.
- **Result mappers** — `toProtocol(strictContract)` methods that mechanically project strict
  contract fields onto protocol model setters. Generated for response-only schemas (schemas
  referenced exclusively in response bodies, identified via schema-graph analysis) where all
  fields are type-compatible with the protocol model.

### Incompatibility detection

The generator detects schemas where the strict contract's Java types cannot be directly assigned to
the protocol model's setter types, and skips mapper generation for those schemas:

- **Inline enums** — spec defines `type: string` with `enum` values (either inline
  `enum: [VAL1, VAL2]` or `allOf` overlay to an enum schema), but the protocol model generates
  inner enum classes.
- **`uniqueItems` arrays** — spec defines `uniqueItems: true`, causing the protocol model to use
  `Set`, while the strict contract uses `List`.
- **Map narrowing** — spec defines `type: object` (mapped to `Map<String, Object>`) but the
  protocol model narrows the value type.
- **`format: uri` / `uri-reference` fields** — spec defines `format: uri` or `format: uri-reference`,
  which the protocol model may map to `java.net.URI`, while the strict contract uses `String`.
- **Dotted property names with inline enums** — spec defines properties with dots in names
  (e.g., `changeset.resourceType`) combined with inline enum values; the protocol model generates
  nested enum types for these.
- **Missing nested mappers** — a field references a nested strict type whose own mapper was
  excluded. Detection iterates to convergence so cascading exclusions are handled.

Schemas excluded by incompatibility detection retain their strict contract DTO (useful for policy
enforcement) but require hand-written protocol mapping.

### Generation wiring

- OpenAPI source: `zeebe/gateway-protocol/src/main/proto/v2/*.yaml`
- Generator: `tools/GenerateContractMappingPoc.java`
- Maven phase: `generate-sources` via `exec-maven-plugin`
- Output: `src/main/java/.../search/contract/generated/`

## Design rationale: what the adapter layer buys

The POC demonstrates two things:

1. **Mechanical boilerplate abstracted to a generated layer.**
   Key coercion (`Long` → `String`), nested traversal, and field projection are derived from
   schema semantics and generated. The adapter author never touches `KeyUtil` — the generated
   strict contract accepts the raw entity value and coerces internally.

2. **Null handling surfaced in the IDE via the type system.**
   The step builder forces the developer through every required field before `build()` is
   reachable. Each required step demands an explicit `FieldPolicy<T>` parameter — typically
   `ContractPolicy.requiredNonNull()`. This is a deliberate confrontation: the developer must
   acknowledge "what happens if this field is null at runtime" for every required field, at the
   moment they wire it. In the hand-mapped code, null handling is absent — the mapping compiles
   silently and NPEs surface in integration tests or production.

### The per-field tradeoff

The hand-written character budget per field is roughly constant before and after. What changes is
what that budget buys:

```java
// Before: mechanical noise, hidden null assumption
.processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))

// After: automatic coercion, explicit null contract
.processDefinitionKey(entity.processDefinitionKey(), ContractPolicy.requiredNonNull())
```

The old per-field overhead is accidental complexity (how to coerce). The new per-field overhead is
semantic intent (what to do about null). The generated layer absorbs the mechanical work; the
adapter line becomes a policy declaration.

This is not "less code" in the line-count sense — it is the same field count with every line
carrying meaning instead of ceremony. The developer writes the same number of lines, but those
lines now express a contract decision rather than a coercion recipe.

## Shared policy operations

Recurring operation classes observed in `SearchQueryResponseMapper` are now policy-layer concerns,
supported by reusable utilities in `ContractPolicy`:

- **Required non-null enforcement:** `ContractPolicy.requiredNonNull()`
- **String policy:** `ContractPolicy.isNotBlank()`, `ContractPolicy.blankToNull()`
- **Null collection normalization:** `ContractPolicy.nullToEmptyList()`,
  `ContractPolicy.nullToEmptyMap()`
- **Defaulting:** `ContractPolicy.defaultIfNull(defaultValue)`

Policy is expressed once per field in adapters and reused consistently instead of being inlined per
mapping path.

## Current implementation status

### Verticals using the full generated pipeline

Entity → Adapter → GeneratedStrictContract → GeneratedResultMapper → Protocol DTO:

|             Vertical              |                      Adapter                       |                  Generated mapper                  |
|-----------------------------------|----------------------------------------------------|----------------------------------------------------|
| ProcessDefinition                 | `ProcessDefinitionContractAdapter`                 | `GeneratedProcessDefinitionResultMapper`           |
| DecisionInstance                  | `DecisionInstanceContractAdapter`                  | `GeneratedDecisionInstanceResultMapper`            |
| BatchOperation (main)             | `BatchOperationResponseContractAdapter`            | `GeneratedBatchOperationResponseMapper`            |
| BatchOperation (item)             | `BatchOperationItemResponseContractAdapter`        | `GeneratedBatchOperationItemResponseMapper`        |
| ProcessInstanceCallHierarchyEntry | `ProcessInstanceCallHierarchyEntryContractAdapter` | `GeneratedProcessInstanceCallHierarchyEntryMapper` |

### Verticals using strict contracts with manual protocol mapping

Entity → Adapter → GeneratedStrictContract → hand-written protocol mapping:

|    Vertical     |             Adapter              |                                   Blocker for generated mapper                                   |
|-----------------|----------------------------------|--------------------------------------------------------------------------------------------------|
| Variable        | `VariableContractAdapter`        | Composes from 3 strict contracts; generated mappers cover variant fields only                    |
| ClusterVariable | `ClusterVariableContractAdapter` | Composes from 3 strict contracts; generated mappers cover variant fields only (same as Variable) |

### Generation coverage

- 357 strict contract DTOs generated (all schemas with properties).
- 147 result mappers generated (response-only schemas passing compatibility checks).

## What this solves

- **Compile-time contract enforcement**: required fields must be explicitly handled in the adapter;
  missing handling is a compile error via the step-builder.
- **Guaranteed traversal completeness**: coverage is derived from the schema graph, not from what
  an engineer remembered to traverse.
- **Maintenance reduction**: contract evolution is handled by regeneration instead of hand-edits.
- **Lower drift risk**: generation and compilation surface schema deltas early, instead of silent
  runtime omissions.
- **Separation of concerns**: business policy in adapters, mechanical mapping in generated code.
- **Generated mechanical correctness**: key coercion, nested traversal, and field projection are
  produced by code generation.

## Request-side analysis

The response-side architecture above covers data flowing *out* (entity → protocol DTO). The same
generation-first approach applies to data flowing *in* (HTTP request → domain query/command).

### Current request-side architecture

Request handling follows a uniform `Either<ProblemDetail, T>` pattern:

```
HTTP Request
  → Controller (routing only, no logic)
    → RequestMapper (validation + shape mapping → Either<ProblemDetail, DomainQuery>)
      → .fold(RestErrorMapper::mapProblemToResponse, service::execute)
```

Controllers are pure delegation — receive request, call mapper, fold the Either:

```java
@CamundaPostMapping(path = "/search")
public ResponseEntity<ProcessDefinitionSearchQueryResult> searchProcessDefinitions(
    @RequestBody(required = false) final ProcessDefinitionSearchQuery query) {
  return SearchQueryRequestMapper.toProcessDefinitionQuery(query)
      .fold(RestErrorMapper::mapProblemToResponse, this::search);
}
```

### Request mapper composition

There are 82 `Either`-returning mapper methods across three files:

|            File            | Methods | With semantic validation | Pure shape mapping |
|----------------------------|---------|--------------------------|--------------------|
| `SearchQueryRequestMapper` | 51      | 2 (3.9%)                 | 49 (96.1%)         |
| `RequestMapper`            | 30      | 27 (90%)                 | 3 (10%)            |
| `SimpleRequestMapper`      | 1       | 1                        | 0                  |
| **Total**                  | **82**  | **30 (36.6%)**           | **52 (63.4%)**     |

The split follows endpoint type:

- **Search endpoints** (51 methods) are almost entirely mechanical — they decompose the request
  into filter, sort, and page sub-components and call `buildSearchQuery()`. Only 2 of 51 have
  custom validation logic.
- **Command endpoints** (31 methods) almost always contain semantic validation — cross-field
  checks, tenant resolution, format assertions. The `Either` plumbing is uniform but the
  validation body is hand-written business logic.

### What is generatable on the request side

|              Concern               | Generatable |                           Reason                            |
|------------------------------------|-------------|-------------------------------------------------------------|
| Controller routing and annotations | Yes         | Direct spec derivation (`operationId`, paths, HTTP methods) |
| Request DTO shape                  | Yes         | Already generated by OpenAPI Generator                      |
| `Either` plumbing and fold pattern | Yes         | Completely uniform across all 82 methods                    |
| Search query decomposition         | Yes         | 96% of search mappers are pure filter/sort/page delegation  |
| Cross-field semantic validation    | No          | Business logic not expressible in OpenAPI                   |

For search endpoints, the complete request mapper is generatable. For command endpoints, the
generator can produce the `Either` skeleton with a hook for the validation lambda.

### Symmetry with the response side

On both sides of the request/response boundary, the same separation of concerns applies:

| Direction |          Generated (mechanical)           |           Hand-written (policy)           |
|-----------|-------------------------------------------|-------------------------------------------|
| Response  | Key coercion, nested traversal, DTO shape | Null handling policy per field            |
| Request   | Routing, query decomposition, DTO shape   | Semantic validation (cross-field, format) |

The mechanical work is schema-derivable. The policy work requires human judgment. Generation
handles one; adapters/validators handle the other.

## Implementation roadmap

Each stage is delivered as a separate branch to allow incremental review and evaluation.

### Stage 1 — Response slice (POC)

Proves the generation pipeline and null-safety enforcement for a representative subset of
response verticals. Self-contained within `gateway-mapping-http`.

**Scope:**
- Generated strict contract DTOs and result mappers for the POC verticals.
- Hand-written contract adapters with explicit `FieldPolicy` per required field.
- Shared policy utilities in `ContractPolicy`.
- Tests per vertical: happy-path mapping, required non-null fails when source is null, required
nullable allows null.

**Demonstrates:**
- Compile-time null handling via step builders.
- Mechanical boilerplate (key coercion, nested traversal) absorbed by generation.
- Clean separation of business policy from DTO mapping.

### Stage 2 — Complete response refactor

Extends Stage 1 to all response verticals and adds static null-safety tooling.

**Scope:**
1. Resolve per-vertical incompatibility blockers (inline enums, `uniqueItems`, Map narrowing) to
bring remaining verticals onto the full generated pipeline.
2. Add contract adapters for all `SearchQueryResponseMapper` verticals.
3. Enable JSpecify annotations (`@NullMarked`, `@Nullable`) in generated output.
4. Enable NullAway or Error Prone nullness checking as a compiler plugin for the module.
5. Add ArchUnit rules: forbid controllers from exposing `io.camunda.search.entities.*` types
directly; require all outbound response types to pass through the contract adaptation layer.

**Demonstrates:**
- Full response-side generation coverage.
- IDE red squiggles when nullable entity fields are assigned to non-null contract fields.
- Architectural enforcement that the boundary cannot be bypassed.

### Stage 3 — Request and response generation

Extends Stage 2 to include request-side generation: controller routing, request mappers for
search endpoints, and validation scaffolding for command endpoints.

**Scope:**
1. Generate controller method signatures, routing annotations, and `Either` fold wiring from the
OpenAPI spec.
2. Generate search query request mappers (filter/sort/page decomposition) — covers 49 of 51
search mapper methods with zero hand-written code.
3. Generate command endpoint skeletons with a validation hook for semantic validation lambdas.
4. Evaluate replacing protocol DTOs with strict contracts as the serialization type, eliminating
the `toProtocol()` result mapper layer entirely.

**Demonstrates:**
- Spec-driven endpoint development: define the schema in YAML, regenerate, write only
the contract adapter (response) and validation lambda (request).
- One unified pattern for all endpoints instead of two parallel mapping systems.
- "Adding a new search endpoint" reduces to: spec definition, regeneration, and a single
adapter file.

### Future direction — unified codegen pipeline

The strict contracts and protocol DTOs are both derived from the same OpenAPI YAML and have
identical fields. For the 147 schemas where `toProtocol()` is a pure field-by-field copy with no
transformation, the mapper layer exists only as a compatibility shim between two generation
pipelines. If the strict contracts carried the necessary serialization annotations
(`@JsonProperty`, `@Schema`), they could serve as the protocol DTOs directly, and the data flow
would simplify to:

```
SearchDomainEntity
  → ContractAdapter (hand-written policy)
    → StrictContract (null-safe, serialization-ready)
      → Controller (returns directly)
```

This would unify the two codegen pipelines (OpenAPI Generator for protocol DTOs, custom generator
for strict contracts) into one, and eliminate the result mapper layer entirely. This is a
cross-cutting change to `gateway-model` and downstream consumers, and is out of scope for the
current work, but it is the natural end state of the generation-first architecture.

## Non-goals

- Do not force the data layer to use API DTOs directly.
- Do not move search-domain entity semantics into the API layer.
- Do not mix policy logic back into `SearchQueryResponseMapper`.

