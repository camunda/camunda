# API Nullability Contract

> **Audience:** Engineers adding or modifying entities and fields backing the
> Orchestration Cluster REST API.

The OpenAPI spec is the single source of truth for whether a field is required and
whether it may be null. Every layer below the spec — generated models, search-domain
entities, transformers, response mapper — must honour that contract. JSpecify
annotations + NullAway + ArchUnit enforce it at compile and build time so contract
drift fails fast instead of leaking nulls into client SDKs.

This page explains the end-to-end picture and what to do when you add a new field or a
new entity. For the full endpoint workflow see
[`rest-api-endpoint-guidelines.md`](rest-api-endpoint-guidelines.md).

## Layered architecture

```
┌──────────────────────────────────────────────────────────────────┐
│ API layer                                                        │
│   OpenAPI spec (rest-api.yaml) — contract source of truth        │
│   Generated model POJOs (gateways/gateway-model) — @NullMarked,  │
│   @Nullable mirrors the spec; NullAway enforces at compile time. │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │ written by
┌──────────────────────────────────────────────────────────────────┐
│ Mapping layer                                                    │
│   SearchQueryResponseMapper (gateways/gateway-mapping-http)      │
│     Contract boundary. Bridges entity-nullable → API-required    │
│     via requireNonNullElse(value, sentinel); otherwise delegates.│
│   Entity transformers (search/search-client-query-transformer)   │
│     Pass-through. No sentinels, no null coercion.                │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │ reads
┌──────────────────────────────────────────────────────────────────┐
│ Domain layer                                                     │
│   Search-domain entity records (search/search-domain)            │
│     @NullMarked package; @Nullable iff the data layer can store  │
│     null; non-@Nullable fields guarded by Objects.requireNonNull │
│     in the compact ctor. Locked in by SearchEntityArchTest.      │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │ hydrated from
┌──────────────────────────────────────────────────────────────────┐
│ Storage                                                          │
│   ES/OS listview docs, RDBMS rows. May store null where the data │
│   layer allows it.                                               │
└──────────────────────────────────────────────────────────────────┘
```

The three invariants the layered architecture exists to enforce:

- **Domain reflects storage reality.** If the data layer can store null for a field,
  the entity component is `@Nullable`. If the data layer always writes non-null, the
  component is non-`@Nullable` and the compact ctor's `requireNonNull` enforces it.
  The guard is the runtime safety net: `@Nullable` and NullAway only constrain code
  that the Java compiler sees, but entities are commonly hydrated reflectively by
  Jackson and MyBatis, which can feed null into a non-`@Nullable` component without
  any compile-time signal. The compact-ctor guard turns that silent corruption into
  a fail-fast NPE at the construction boundary, with the offending field name in the
  message. `SearchEntityArchTest` enforces that every non-`@Nullable` reference
  component has the corresponding guard, so the contract can't drift.
- **Transformer is pass-through.** No null coercion, no sentinel injection. Mapping
  bugs and DL anomalies surface as nulls in the entity, not as fake values that
  silently corrupt downstream logic.
- **Mapper is the contract boundary.** Where the entity says nullable but the API
  contract says required + non-null, the mapper bridges the gap via
  `requireNonNullElse(value, sentinel)`. The sentinel is documented next to the call.

## Adding a new field

1. **Spec it first.** Decide `required: true|false` and `nullable: true|false` in the
   OpenAPI YAML. Be honest — the rest of the pipeline trusts this.
2. **Regenerate the model.** `./mvnw install -pl gateways/gateway-model -Dquickly -T1C`.
3. **Add to the entity** (`search-domain`):
   - **Required + non-nullable in spec, AND DL always populates** → declare the
     component without `@Nullable`, add `Objects.requireNonNull(field, "field")` to
     the compact ctor.
   - **Nullable in spec, OR DL may store null** → declare with `@Nullable`. Add a
     short comment naming *why* it can be null (e.g. `// only written on
     ELEMENT_ACTIVATING; absent on docs first created by a later intent.`). No
     `requireNonNull` line.
4. **Add to the transformer.** A single new pass-through line. No sentinels.
5. **Add to the response mapper.**
   - Entity `@Nullable` and API nullable → direct delegation
     (`...processDefinitionName(p.processDefinitionName())`).
   - Entity non-`@Nullable` and API non-null → direct delegation; the entity already
     guarantees the value.
   - Entity `@Nullable` but API non-null → wrap with
     `requireNonNullElse(p.field(), <sentinel>)`. Pick a spec-compliant sentinel
     (empty string for IDs, `-1` for versions, `EPOCH_DATE_SENTINEL` for dates,
     a default enum value for states). Comment the rationale.
6. **Validate.** ArchUnit (`SearchEntityArchTest`) verifies the entity guards.
   NullAway on `gateway-mapping-http` verifies the mapper paths. Run module tests on
   `search-domain`, `gateway-mapping-http`, `gateway-rest`, plus `db/rdbms` if you
   flipped an existing field's nullability — the rdbms mapper tests often surface
   case-2 fields masquerading as case-1.

## Adding a new entity

Same flow as above, plus:

- Place the record in `io.camunda.search.entities`. The `package-info.java` already
  declares `@NullMarked`, so non-annotated reference components are implicitly
  non-null. `SearchEntityArchTest` will fail the build if any non-`@Nullable`
  reference component lacks a compact-ctor `requireNonNull` guard.
- For mutable collection fields hydrated by MyBatis (lists, sets, maps), default the
  field in the compact ctor to a **mutable** empty collection
  (`tags = tags != null ? tags : new HashSet<>();`). MyBatis hydrates collections by
  calling `.add()` on the existing instance, so `Set.of()` / `List.of()` would throw
  `UnsupportedOperationException` at runtime.
- If a state enum needs translation (`toProtocolState(...)` style), make the helper's
  signature non-null in/out when the entity guarantees a non-null state, and
  `@Nullable` in/out otherwise. Don't have it both ways.

## Validation gates (before committing)

1. `./mvnw license:format spotless:apply -T1C` — mandatory, even for one-line
   contract changes.
2. `./mvnw clean compile -pl gateways/gateway-mapping-http -T1C` — NullAway runs
   here. Skip `-Dquickly` for this step; it disables ErrorProne and silently masks
   NullAway errors.
3. `./mvnw verify -pl qa/archunit-tests -Dtest=SearchEntityArchTest` — entity
   contract enforcement.
4. Module test sweep: `search/search-domain`, `gateways/gateway-mapping-http`,
   `zeebe/gateway-rest`, plus `db/rdbms` if entity nullability changed.

