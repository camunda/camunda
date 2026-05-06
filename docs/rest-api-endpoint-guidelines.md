# REST API Endpoint Guidelines

> **Audience:** All engineers implementing new endpoints or modifying existing ones in the Orchestration Cluster REST API.

This document is the single reference for building, validating, and shipping REST API endpoints in the Camunda Orchestration Cluster. It covers every step—from designing the OpenAPI spec to writing tests and passing CI—so that every team delivers consistent, SDK-friendly, and well-documented endpoints.

---

## Table of contents

1. [End-to-end workflow](#1-end-to-end-workflow)
2. [OpenAPI specification rules](#2-openapi-specification-rules)
3. [Spectral linting & custom rules](#3-spectral-linting--custom-rules)
4. [REST controller implementation](#4-rest-controller-implementation)
5. [Service layer](#5-service-layer)
6. [Camunda Client extension](#6-camunda-client-extension)
7. [Testing strategy](#7-testing-strategy)
8. [CI pipeline & merge checklist](#8-ci-pipeline--merge-checklist)
9. [Documentation generation](#9-documentation-generation)
10. [Common mistakes & how to fix them](#10-common-mistakes--how-to-fix-them)
11. [Reference links](#11-reference-links)

---

## 1. End-to-end workflow

Every new or modified endpoint must follow these stages in order:

| # |          Stage          |                                Artefact                                 |                                                  Owner                                                   |
|---|-------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| 1 | **Design**              | Endpoint contract (method, path, request/response shapes)               | Feature team + [`#top-c8-cluster-api-governance`](https://camunda.slack.com/archives/C0A154VV8DB) review |
| 2 | **OpenAPI spec**        | YAML definition in `zeebe/gateway-protocol/src/main/proto/v2/`          | Feature team, reviewed by `@camunda/c8-api-team`                                                         |
| 3 | **Spectral validation** | Pass local `spectral lint` (two passes)                                 | Feature team                                                                                             |
| 4 | **Model generation**    | Run `mvn clean install -Dquickly` on `gateway-model` / `gateway-rest`   | Feature team                                                                                             |
| 5 | **Controller**          | Spring `@CamundaRestController` in `zeebe/gateway-rest`                 | Feature team                                                                                             |
| 6 | **Service layer**       | Extend/create service in `service/` module                              | Feature team                                                                                             |
| 7 | **Client**              | New command/query in `clients/java`                                     | Feature team                                                                                             |
| 8 | **Tests**               | Unit + (optional) integration + acceptance tests                        | Feature team                                                                                             |
| 9 | **Documentation**       | Descriptions in the OpenAPI spec generate the public docs automatically | Feature team                                                                                             |

> **Tip:** Share your endpoint design early in [`#top-c8-cluster-api-governance`](https://camunda.slack.com/archives/C0A154VV8DB) (Slack) to catch issues before you write code.

---

## 2. OpenAPI specification rules

All endpoint definitions live in `zeebe/gateway-protocol/src/main/proto/v2/`. The main entry point is `rest-api.yaml`, which `$ref`s to per-domain files (e.g., `process-definitions.yaml`, `user-tasks.yaml`).

### 2.1 File structure

```
v2/
├── rest-api.yaml              ← Entry point (paths + tags)
├── common-responses.yaml      ← Shared HTTP response definitions
├── problem-detail.yaml        ← RFC 9457 ProblemDetail schema
├── search-models.yaml         ← Pagination, sort, SearchQueryRequest/Response
├── filters.yaml               ← Advanced filter schemas (string, integer, date-time)
├── keys.yaml                  ← Key type schemas (LongKey, ProcessDefinitionKey, …)
├── identifiers.yaml           ← Identifier types (TenantId, ProcessDefinitionId, …)
├── cursors.yaml               ← Cursor schemas for pagination
├── <domain>.yaml              ← Per-domain paths + schemas (e.g., process-definitions.yaml)
```

**When adding a new domain**, create a new `<domain>.yaml` file following the same pattern and add path `$ref`s to `rest-api.yaml`.

### 2.2 Naming and terminology

All descriptive text (summaries, descriptions, property docs) should follow the [Camunda style guide](https://confluence.camunda.com/display/HAN/Camunda+style+guide).

|                           Rule                           |                              Example                               |
|----------------------------------------------------------|--------------------------------------------------------------------|
| **Never use "flow node"** — use "element" instead        | ❌ `flowNodeId` → ✅ `elementId`                                     |
| **Key properties must be `type: string`**                | `processDefinitionKey: type: string` (backed by `LongKey` pattern) |
| **Summaries**: Sentence case, no trailing period         | `summary: Search process definitions`                              |
| **Descriptions**: Sentence case **with** trailing period | `description: Returns process definition as JSON.`                 |
| **Tags**: Sentence case, no trailing period              | `tags: [Process definition]`                                       |
| **Resource references in text**: lower case              | ✅ "the process definition" ❌ "the Process Definition"              |

### 2.3 Path and operation conventions

**Every operation MUST have:**

- `operationId` — unique, camelCase (e.g., `searchProcessDefinitions`, `completeUserTask`)
- `summary` — short, no period
- `description` — full sentence(s) with period. First line must be a complete sentence (used as `meta description` in docs)
- `tags` — exactly one tag matching the resource
- `x-eventually-consistent` — explicitly `true` or `false` (see §2.5)
- `x-added-in-version` — the Camunda version in which the operation was introduced, e.g., `"8.9"` (see §2.17)

**Path patterns:**

|  Operation type  | HTTP method |                  Path pattern                   |                         Example                          |
|------------------|-------------|-------------------------------------------------|----------------------------------------------------------|
| Search / list    | `POST`      | `/<resources>/search`                           | `POST /process-definitions/search`                       |
| Get by key       | `GET`       | `/<resources>/{resourceKey}`                    | `GET /process-definitions/{processDefinitionKey}`        |
| Get sub-resource | `GET`       | `/<resources>/{resourceKey}/<sub>`              | `GET /process-definitions/{key}/xml`                     |
| Statistics       | `POST`      | `/<resources>/statistics/<metric>`              | `POST /process-definitions/statistics/process-instances` |
| Create / command | `POST`      | `/<resources>` or `/<resources>/{key}/<action>` | `POST /user-tasks/{key}/completion`                      |
| Update           | `PATCH`     | `/<resources>/{resourceKey}`                    | `PATCH /user-tasks/{userTaskKey}`                        |
| Delete           | `DELETE`    | `/<resources>/{resourceKey}`                    | `DELETE /roles/{roleKey}`                                |

#### Detailed path design guidance

When designing a new endpoint path, follow these rules derived from the existing API surface:

1. **Use lowercase kebab-case for all path segments.** Multi-word resources use hyphens:
   - ✅ `/process-definitions`, `/user-tasks`, `/decision-instances`, `/batch-operations`
   - ❌ `/processDefinitions`, `/user_tasks`
2. **Use plural nouns for resource collections.** Even singletons like `/license` are rare exceptions—prefer plurals:
   - ✅ `/roles`, `/groups`, `/tenants`
   - ❌ `/role`, `/group`
3. **Name actions as nouns, not verbs.** Commands are modelled as sub-resource nouns:
   - ✅ `/user-tasks/{key}/completion`, `/process-instances/{key}/cancellation`
   - ❌ `/user-tasks/{key}/complete`, `/process-instances/{key}/cancel`
   - Existing examples: `completion`, `assignment`, `activation`, `migration`, `modification`, `resolution`, `deletion`, `broadcast`, `evaluation`, `publication`, `correlation`
4. **Nest sub-resource searches under the parent.** When searching for child resources scoped to a parent, nest the path:
   - ✅ `/user-tasks/{userTaskKey}/variables/search`
   - ✅ `/process-instances/{processInstanceKey}/incidents/search`
   - ✅ `/groups/{groupId}/users/search`
   - ✅ `/element-instances/{elementInstanceKey}/incidents/search`
5. **Statistics endpoints live under `/statistics/<metric>`.** They can be top-level or scoped to a specific resource:
   - Top-level: `/process-definitions/statistics/process-instances`
   - Scoped: `/process-definitions/{key}/statistics/element-instances`
   - Jobs example: `/jobs/statistics/global`, `/jobs/statistics/by-types`, `/jobs/statistics/time-series`
6. **Path parameters must use the typed key name**, not a generic `{id}` or `{key}`:
   - ✅ `/{processDefinitionKey}`, `/{userTaskKey}`, `/{incidentKey}`
   - ❌ `/{id}`, `/{key}`
   - Exception: identity resources that use natural identifiers — `/{username}`, `/{tenantId}`, `/{roleId}`, `/{groupId}`
7. **Batch/bulk operations on collections** use the action noun directly on the collection:
   - `/process-instances/cancellation` (batch cancel)
   - `/process-instances/deletion` (batch delete)
   - `/process-instances/migration` (batch migrate)
8. **Register paths in `rest-api.yaml`.** Every path from your domain YAML must be `$ref`'d into `rest-api.yaml` under the appropriate `# <Resource> endpoints` comment block. Keep paths **alphabetically sorted** within each block.
9. **One domain YAML per resource.** Do not add paths for resource A in resource B's YAML file. Create a new `<resource>.yaml` file if needed.

### 2.4 Schema conventions

#### Semantic domain types (`x-semantic-type` and `format`)

Plain `type: string` or `type: integer` tells consumers nothing about the *domain meaning* of a property. To enable SDK generators to produce **type-safe, domain-aware types** (e.g., `ProcessDefinitionKey` instead of `String`, `TenantId` instead of `str`), every identifier and key property must declare two complementary fields:

|       Field       |                                Purpose                                 |
|-------------------|------------------------------------------------------------------------|
| `format`          | Standard OpenAPI field. SDK generators use it to select type mappings. |
| `x-semantic-type` | Vendor extension. Documents the domain concept for tooling and humans. |

Both must be set to the **same value** (the domain type name). This convention applies to:

- **Key types** in `keys.yaml` — system-generated numeric keys serialised as strings (extend `LongKey`).
- **Identifier types** in `identifiers.yaml` — user-facing string IDs (e.g., BPMN process IDs, tenant IDs).
- **Cursor types** in `cursors.yaml` — pagination cursors.

**Example — key type (`keys.yaml`):**

```yaml
ProcessDefinitionKey:
  description: System-generated key for a deployed process definition.
  format: ProcessDefinitionKey
  x-semantic-type: ProcessDefinitionKey
  example: "2251799813686749"
  type: string
  allOf:
    - $ref: '#/components/schemas/LongKey'
```

**Example — identifier type (`identifiers.yaml`):**

```yaml
ProcessDefinitionId:
  description: Id of a process definition, from the model.
  format: ProcessDefinitionId
  x-semantic-type: ProcessDefinitionId
  type: string
  minLength: 1
  pattern: ^[a-zA-Z_][a-zA-Z0-9_\-\.]*$
  example: new-account-onboarding-workflow
```

#### Key properties

All `*Key` properties must use `type: string` and reference the shared key schemas in `keys.yaml`:

```yaml
processDefinitionKey:
  allOf:
    - $ref: 'keys.yaml#/components/schemas/ProcessDefinitionKey'
  description: The key for this process definition.
```

If you need a new key type, add it to `keys.yaml` extending `LongKey`:

```yaml
MyNewResourceKey:
  description: System-generated key for a new resource.
  format: MyNewResourceKey
  x-semantic-type: MyNewResourceKey
  example: "2251799813686749"
  type: string
  allOf:
    - $ref: '#/components/schemas/LongKey'
```

If you need a new string identifier, add it to `identifiers.yaml` with `format` and `x-semantic-type`:

```yaml
MyNewResourceId:
  description: User-defined id for my new resource.
  format: MyNewResourceId
  x-semantic-type: MyNewResourceId
  type: string
  minLength: 1
  example: my-resource-1
```

#### Property descriptions

**Every schema property MUST have a `description`.** This is enforced by the Spectral rule `require-property-descriptions`.

#### `required` field

`required` is an **array at the object level**, never a boolean on individual properties:

```yaml
# ✅ Correct
MySchema:
  type: object
  required:
    - name
    - version
  properties:
    name:
      type: string
      description: The name.
    version:
      type: integer
      description: The version.

# ❌ Wrong — will fail Spectral validation
MySchema:
  type: object
  properties:
    name:
      type: string
      required: false    # Invalid!
```

Every entry in `required` must correspond to a key in `properties` (or `allOf` compositions). This is enforced by the `required-properties-must-exist` Spectral rule.

#### Array properties in responses

Response arrays **must** be listed in `required` and **must not** be `nullable`. The gateway coerces `null` to `[]` at runtime, and SDK generators produce cleaner types when arrays are required/non-nullable.

```yaml
# ✅ Correct
ProcessDefinitionSearchQueryResult:
  type: object
  required:
    - items
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/ProcessDefinitionResult'
```

This is enforced by the `array-properties-must-be-required` Spectral rule.

#### Nullable properties

Use `nullable: true` only when the property can genuinely be `null` in the response (e.g., `name` on a process definition that may not have one). Never use `nullable` on array properties.

For how the `required` / `nullable` flags propagate from the spec down through the generated POJOs, search-domain entities, transformers, and response mapper — and how NullAway and ArchUnit enforce the contract at compile and build time — see [api-entities-nullaway-enforcement.md](api-entities-nullaway-enforcement.md).

### 2.5 Eventually consistent annotation (`x-eventually-consistent`)

Every operation **should** declare `x-eventually-consistent` explicitly:

|                     Operation type                      |  Value  |                   Reason                    |
|---------------------------------------------------------|---------|---------------------------------------------|
| **Queries** (search, statistics, GET reads)             | `true`  | Read from eventually-consistent projections |
| **Commands** (create, update, delete, complete, assign) | `false` | Synchronous writes                          |

> **What the Spectral rule enforces:** The `no-eventually-consistent-on-commands` rule only prevents command (mutating) operations from being marked `x-eventually-consistent: true`. It does **not** require the extension to be present on every operation, nor does it enforce that query operations set it to `true`. Declaring the annotation on all operations is a **team convention** for clarity and SDK correctness; setting `true` on a command causes SDK generators to emit unnecessary polling wrappers.

#### Relationship to `@RequiresSecondaryStorage`

On the Java controller side, query endpoints that read from the secondary storage (Elasticsearch/OpenSearch) are annotated with `@RequiresSecondaryStorage`. This annotation causes the endpoint to return **HTTP 403** when secondary storage is not configured (`camunda.database.type=none`).

**Keep these two in sync:**
- If a spec operation has `x-eventually-consistent: true`, the corresponding controller method should have `@RequiresSecondaryStorage`.
- If you add `@RequiresSecondaryStorage` to a controller method, set `x-eventually-consistent: true` on the matching spec operation.

A mismatch means SDK consumers get incorrect consistency guarantees. See §2.16 for more details on controller–spec alignment.

### 2.6 Component reuse and schema organisation

The OpenAPI spec is split into shared building blocks. **Reuse them aggressively** to keep the spec DRY, consistent, and safe from accidental divergence.

#### What to reuse

|       Shared file       |                                What it provides                                 |              When to `$ref` it              |
|-------------------------|---------------------------------------------------------------------------------|---------------------------------------------|
| `common-responses.yaml` | Standard error responses (400, 401, 403, 500, 503)                              | Every endpoint's error responses            |
| `problem-detail.yaml`   | `ProblemDetail` schema (RFC 9457)                                               | 404 and 409 inline responses                |
| `search-models.yaml`    | `SearchQueryRequest`, `SearchQueryResponse`, pagination, `SortOrderEnum`        | Every search endpoint                       |
| `filters.yaml`          | `StringFilterProperty`, `IntegerFilterProperty`, `DateTimeFilterProperty`, etc. | Every search filter attribute               |
| `keys.yaml`             | Typed key schemas (`ProcessDefinitionKey`, `UserTaskKey`, etc.)                 | Every `*Key` property                       |
| `identifiers.yaml`      | Identifier types (`TenantId`, `ProcessDefinitionId`, etc.)                      | Every `*Id` property                        |
| `cursors.yaml`          | Cursor schemas for pagination                                                   | Via `search-models.yaml` (usually indirect) |

#### How to reference correctly

Use **relative `$ref`** paths from your domain YAML:

```yaml
# ✅ Correct — references from a domain file to shared files
filter:
  allOf:
    - $ref: 'filters.yaml#/components/schemas/StringFilterProperty'
processDefinitionKey:
  allOf:
    - $ref: 'keys.yaml#/components/schemas/ProcessDefinitionKey'
responses:
  "400":
    $ref: 'common-responses.yaml#/components/responses/InvalidData'
```

#### When to create a new shared schema vs. inline

- **Create a shared schema** when 2+ endpoints or schemas would reference the same structure.
- **Inline** when the schema is truly unique to one endpoint and unlikely to be reused.
- When in doubt, **favour extraction** — it is easier to inline later than to deduplicate after the fact.

#### Separate request and response schemas

Request and response contracts often have different semantics for the same fields:

- A request field may be **optional** (the user can omit it).
- The same field in a response may be **required** (the server always returns it).

**Never share a single schema for both request and response** when their `required` / `nullable` semantics differ. This was a real source of breaking changes (see [#46877](https://github.com/camunda/camunda/issues/46877) and [#47465](https://github.com/camunda/camunda/issues/47465)).

```yaml
# ✅ Correct — separate schemas
DocumentMetadataRequest:
  type: object
  properties:
    customProperties:
      type: object
      description: Custom metadata properties.

DocumentMetadataResponse:
  type: object
  required:
    - customProperties
  properties:
    customProperties:
      type: object
      description: Custom metadata properties.

# ❌ Wrong — shared schema forces the same requiredness on both sides
DocumentMetadata:
  type: object
  required:
    - customProperties    # Forces request callers to always send this!
  properties:
    customProperties:
      type: object
```

**Rule of thumb:** If a property is required in the response but optional in the request (or vice versa), split the schema.

### 2.7 Backwards compatibility and breaking changes

We guarantee **forward-compatibility of the API between minor versions**. Any breaking change must be deliberately accepted and clearly communicated to customers. The lessons below come from real regressions triaged in [#46877](https://github.com/camunda/camunda/issues/46877).

#### What counts as a breaking change?

|                                               Change                                               |          Breaking?          |                                                                         Why                                                                          |
|----------------------------------------------------------------------------------------------------|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Removing a response property                                                                       | 🔴 **Yes**                  | Clients that read it will fail                                                                                                                       |
| Making a previously optional request field **required**                                            | 🔴 **Yes**                  | Existing requests without the field will be rejected                                                                                                 |
| Making a previously required response field **optional/nullable**                                  | 🔴 **Yes**                  | Clients assuming non-null will fail                                                                                                                  |
| Changing a property type (e.g., `string` → `object`, `integer` → `string`)                         | 🔴 **Yes**                  | Serialisation/deserialisation breaks                                                                                                                 |
| Renaming a schema used by generated clients                                                        | 🔴 **Yes**                  | Generated type names change, breaking compilation                                                                                                    |
| Removing an endpoint                                                                               | 🔴 **Yes**                  | Obvious                                                                                                                                              |
| Removing an enum value                                                                             | 🔴 **Yes**                  | Clients matching on it will fail                                                                                                                     |
| Hardening a type (e.g., `string` → branded `ProcessDefinitionKey`)                                 | 🟡 **SDK breaking**         | Generated SDKs produce stricter types; runtime payloads may still work                                                                               |
| Making a search filter criterion advanced (e.g., `string` → `oneOf[string, StringFilterProperty]`) | 🟡 **SDK breaking**         | Prior plain-value requests still work at runtime, but generated SDKs change the filter type from a primitive to a union/object, breaking compilation |
| Adding a new **enum value**                                                                        | 🟡 **Potentially breaking** | Clients with exhaustive switches/pattern matching and no default branch break at compile time                                                        |
| Adding a new optional response property                                                            | 🟢 No                       | Additive                                                                                                                                             |
| Adding a new optional request field                                                                | 🟢 No                       | Additive                                                                                                                                             |
| Removing `null` from a response union (hardening)                                                  | 🟢 No                       | Existing null-checks still work at runtime                                                                                                           |
| Adding a new endpoint                                                                              | 🟢 No                       | Additive                                                                                                                                             |

#### Rules to prevent accidental breaking changes

1. **Never change `required` on an existing field** without explicit sign-off from `@camunda/c8-api-team`. Making an optional request field required breaks all existing callers that omit it.

2. **Never remove or rename an existing schema.** If you need to restructure, introduce a new schema and deprecate the old one. The rename of `ProcessInstanceIncidentSearchQuery` → `IncidentSearchQuery` ([#47457](https://github.com/camunda/camunda/issues/47457)) was a real breaking change.

3. **Separate request and response schemas** when they diverge in `required`/`nullable` (see §2.6). Sharing a schema and then changing `required` on one side silently breaks the other.

4. **Be careful with spec refactoring** (e.g., splitting a monolithic YAML into multi-part files). Verify that the resolved spec is **semantically identical** before and after. The 8.8→8.9 multi-part split introduced ~20 regressions because `required` arrays and schema references shifted during the refactor ([#46877](https://github.com/camunda/camunda/issues/46877)).

5. **Treat enum additions as potentially breaking.** While additive at schema level, many generated SDKs (TypeScript, Java, Go) treat enums as closed sets. Always:

   - Use the `x-deprecated-enum-members` extension to deprecate values instead of removing them.
   - Document new enum values in release notes.
   - Advise customers to include a `default` branch in exhaustive enum handling.
6. **Type-hardening changes** (e.g., `string` → `TenantId` branded type) are source-breaking for generated clients even when runtime payloads remain string-like. These must be documented as breaking changes ([#46298](https://github.com/camunda/camunda/issues/46298)).
7. **Verify backwards compatibility before merging.** Compare the resolved spec between the target version and the previous stable version. The API team uses contract-diff tooling (`tsc-api-changelog`) to detect regressions — but catching issues before merge is always cheaper.

#### Deprecation instead of removal

When you need to evolve the API in a non-additive way:

1. **Mark the old schema/property/enum value as deprecated** in the current version.
2. **Introduce the new schema/property alongside** the old one.
3. **Communicate the deprecation** in release notes and upgrade guides.
4. **Remove the deprecated artefact** only in the next **major** version (or after a documented deprecation period of at least two minor versions).

For enum values, use the `x-deprecated-enum-members` extension:

```yaml
MyStatusEnum:
  type: string
  enum:
    - ACTIVE
    - COMPLETED
    - LEGACY_STATUS
  x-deprecated-enum-members:
    - name: LEGACY_STATUS
      deprecatedInVersion: "8.9.0"
```

### 2.8 Alpha endpoints and properties

Some endpoints or individual properties are released as **alpha features** — they are functional but may change or be removed in future releases without following the standard deprecation process.

#### Marking an entire endpoint as alpha

Append `(alpha)` to the `summary` and add the **exact** sentinel paragraph to the `description`:

```yaml
/clock:
  put:
    operationId: pinClock
    summary: Pin internal clock (alpha)
    description: |
      Set a precise, static time for the Zeebe engine's internal clock.

      This endpoint is an alpha feature and may be subject to change
      in future releases.
```

#### Marking an individual property as alpha

Use the same sentinel text in the property's `description`:

```yaml
runtimeInstructions:
  description: |
    Runtime instructions (alpha). List of instructions that affect the
    runtime behavior of the process instance.

    This parameter is an alpha feature and may be subject to change
    in future releases.
  type: array
  items:
    $ref: '#/components/schemas/ProcessInstanceCreationRuntimeInstruction'
```

#### Why the exact wording matters

The `camunda-docs` generation pipeline uses **regex-based transforms** to:

1. **Wrap** the sentinel text in a Docusaurus `:::note` admonition, producing a visible banner.
2. **Linkify** "alpha feature" to the [alpha features reference page](https://docs.camunda.io/docs/next/components/early-access/alpha/alpha-features/).

The detection regex matches the literal string:

> `This endpoint is an alpha feature and may be subject to change`
> `in future releases.`

If you rephrase or reformat this text, the automation will **not** pick it up and no banner will appear in the docs. Use the exact two-line format shown above.

See §9.3 for more details on the docs-side automation.

### 2.9 Response codes

Use consistent response codes across endpoints. Reuse `common-responses.yaml` definitions:

| Code  |               When to use                |                                Definition                                 |
|-------|------------------------------------------|---------------------------------------------------------------------------|
| `200` | Successful response with body            | Inline per endpoint                                                       |
| `204` | Successful command with no body          | Inline (e.g., `completeUserTask`)                                         |
| `400` | Invalid request data                     | `$ref: 'common-responses.yaml#/components/responses/InvalidData'`         |
| `401` | Missing/invalid authentication           | `$ref: 'common-responses.yaml#/components/responses/Unauthorized'`        |
| `403` | Authorization failure / disabled feature | `$ref: 'common-responses.yaml#/components/responses/Forbidden'`           |
| `404` | Resource not found                       | Inline with `ProblemDetail` schema                                        |
| `409` | Conflict / wrong state                   | Inline with `ProblemDetail` schema                                        |
| `500` | Internal server error                    | `$ref: 'common-responses.yaml#/components/responses/InternalServerError'` |
| `503` | Backpressure / unavailable               | `$ref: 'common-responses.yaml#/components/responses/ServiceUnavailable'`  |

All error responses use `application/problem+json` with the `ProblemDetail` schema (RFC 9457).

### 2.10 Search endpoint conventions

Search endpoints follow a standard pattern. Use these shared models:

**Request:**

```yaml
MyResourceSearchQuery:
  type: object
  additionalProperties: false
  allOf:
    - $ref: 'search-models.yaml#/components/schemas/SearchQueryRequest'   # provides `page`
  properties:
    sort:
      description: Sort field criteria.
      type: array
      items:
        $ref: '#/components/schemas/MyResourceSearchQuerySortRequest'
    filter:
      allOf:
        - $ref: '#/components/schemas/MyResourceFilter'
      description: The search filters.
```

**Sort request:**

```yaml
MyResourceSearchQuerySortRequest:
  type: object
  required:
    - field
  properties:
    field:
      description: The field to sort by.
      type: string
      enum:
        - myField1
        - myField2
    order:
      $ref: 'search-models.yaml#/components/schemas/SortOrderEnum'
```

**Response:**

```yaml
MyResourceSearchQueryResult:
  type: object
  required:
    - items
  allOf:
    - $ref: 'search-models.yaml#/components/schemas/SearchQueryResponse'   # provides `page`
  properties:
    items:
      description: The matching resources.
      type: array
      items:
        $ref: '#/components/schemas/MyResourceResult'
```

### 2.11 Advanced search filters

New search endpoints should feature **advanced search capabilities** for all attributes (except `boolean`). Use the filter types from `filters.yaml`:

|          Property type          |                                              Filter schema to use                                               |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `string`                        | `StringFilterProperty` (supports exact match + `$eq`, `$neq`, `$in`, `$notIn`, `$like`, `$exists`)              |
| `string` (key-like, no `$like`) | `BasicStringFilterProperty` (supports exact match + `$eq`, `$neq`, `$in`, `$notIn`, `$exists`)                  |
| `integer`                       | `IntegerFilterProperty` (supports exact match + `$eq`, `$neq`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$exists`)  |
| `date-time`                     | `DateTimeFilterProperty` (supports exact match + `$eq`, `$neq`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$exists`) |
| `boolean`                       | Plain `type: boolean` (no advanced filter)                                                                      |
| `enum`                          | Use advanced search; map in the `simple` OpenAPI generator execution in `gateways/gateway-model/pom.xml`        |

**Example filter with advanced search:**

```yaml
MyResourceFilter:
  type: object
  properties:
    name:
      allOf:
        - $ref: 'filters.yaml#/components/schemas/StringFilterProperty'
      description: Name of the resource.
    version:
      allOf:
        - $ref: 'filters.yaml#/components/schemas/IntegerFilterProperty'
      description: Version of the resource.
    createdDate:
      allOf:
        - $ref: 'filters.yaml#/components/schemas/DateTimeFilterProperty'
      description: Creation date of the resource.
    active:
      type: boolean
      description: Whether the resource is active.
```

### 2.12 Upgrading an existing filter field to advanced search

When an **existing** search filter field needs to be upgraded from a plain string to support advanced
operators (e.g., adding `$like` to `elementId`), do not swap the type to `StringFilterProperty`
directly. This would change the codegen output and break generated SDKs (see the compatibility table
in §2.8). Instead, create a **dedicated filter property type** in `identifiers.yaml` that preserves
the original identifier type.

**Do not use `StringFilterProperty` directly** for existing fields because it loses identifier
metadata (`example`, `format`, `x-semantic-type`) and causes codegen type conflicts in the gateway
model.

#### Checklist

1. **`identifiers.yaml`** - Define a `<Type>FilterProperty` as a `oneOf` of the original identifier
   (plain string, backward compatible) and a new `Advanced<Type>Filter` object. The advanced filter
   references the original identifier in each operator field.
2. **Endpoint spec** - Reference the new filter type instead of the original identifier.
3. **`gateways/gateway-model/pom.xml`** - Add type mappings for both profiles: advanced
   (`=StringFilterProperty`) and simple (`=String`).
4. **Java client** - Add `Consumer<StringProperty>` overload. Keep the existing `String` method,
   delegating to `b -> b.eq(value)`.
5. **Search domain** - Change `List<String>` to `List<Operation<String>>` in the filter record.
   Keep convenience methods that wrap in `EQUALS`. Update the transformer from `stringTerms()` to
   `stringOperations()`. Update RDBMS MyBatis mapper to use `operationCondition`.

For a complete reference implementation, see [#50744](https://github.com/camunda/camunda/pull/50744).

### 2.13 Deprecated enum members

When deprecating enum values, use the `x-deprecated-enum-members` vendor extension:

```yaml
MyStatusEnum:
  type: string
  enum:
    - ACTIVE
    - COMPLETED
    - LEGACY_STATUS
  x-deprecated-enum-members:
    - name: LEGACY_STATUS
      deprecatedInVersion: "8.9.0"
```

Rules enforced by the `valid-deprecated-enum-members` Spectral rule:
- Must be a non-empty array.
- Each entry needs `name` (string referencing an existing enum value) and `deprecatedInVersion` (semver string).
- No duplicate names.
- No extra keys beyond `name` and `deprecatedInVersion`.

### 2.14 Polymorphic union types (`x-polymorphic-schema`)

When a request body accepts **mutually exclusive** sets of properties — e.g., identify a process by `processDefinitionId` **or** by `processDefinitionKey`, but never both — model this as a `oneOf` composition and annotate the wrapper schema with `x-polymorphic-schema: true`.

#### Why not a single flat schema?

A flat schema with all fields optional lets callers send contradictory combinations (e.g., both `processDefinitionId` and `processDefinitionKey`). `oneOf` makes the mutual exclusion explicit and allows SDK generators to produce type-safe union types (e.g., TypeScript discriminated unions, Java sealed interfaces).

#### Pattern

```yaml
# Wrapper: marks the union as polymorphic for code generators
ProcessInstanceCreationInstruction:
  x-polymorphic-schema: true
  description: |
    Instructions for creating a process instance. The process definition can be
    specified either by id or by key.
  oneOf:
    - $ref: '#/components/schemas/ProcessInstanceCreationInstructionByKey'
    - $ref: '#/components/schemas/ProcessInstanceCreationInstructionById'

# Variant A — identified by key
ProcessInstanceCreationInstructionByKey:
  type: object
  title: Process creation by key          # title is used in SDK type names
  required:
    - processDefinitionKey
  additionalProperties: false
  properties:
    processDefinitionKey:
      $ref: 'keys.yaml#/components/schemas/ProcessDefinitionKey'
    variables:
      type: object
      additionalProperties: true
      description: Variables for the root scope.
    tenantId:
      allOf:
        - $ref: 'identifiers.yaml#/components/schemas/TenantId'

# Variant B — identified by id
ProcessInstanceCreationInstructionById:
  type: object
  title: Process creation by id
  required:
    - processDefinitionId
  additionalProperties: false
  properties:
    processDefinitionId:
      allOf:
        - $ref: 'identifiers.yaml#/components/schemas/ProcessDefinitionId'
    processDefinitionVersion:
      type: integer
      format: int32
      default: -1
      description: The version. Defaults to latest.
    variables:
      type: object
      additionalProperties: true
      description: Variables for the root scope.
    tenantId:
      allOf:
        - $ref: 'identifiers.yaml#/components/schemas/TenantId'
```

#### When to use a `discriminator`

If the variants share a common **type** field whose value selects the variant, add a `discriminator`:

```yaml
SourceElementInstruction:
  x-polymorphic-schema: true
  discriminator:
    propertyName: sourceType
    mapping:
      byId: '#/components/schemas/SourceElementIdInstruction'
      byKey: '#/components/schemas/SourceElementInstanceKeyInstruction'
  oneOf:
    - $ref: '#/components/schemas/SourceElementIdInstruction'
    - $ref: '#/components/schemas/SourceElementInstanceKeyInstruction'
```

Use a discriminator when:
- There are ≥ 3 variants and the generator needs a clear dispatch field.
- The variants share a type/kind property anyway.

Omit the discriminator when:
- The variants are distinguished by which `required` field is present (like the key-vs-id example above).

#### Guidelines

1. **Always set `x-polymorphic-schema: true`** on the wrapper schema so code generators can produce union types instead of `Object`.
2. **Set `additionalProperties: false`** on each variant to ensure the generator detects which variant is in use by checking for unexpected fields.
3. **Give each variant a `title`** — it is used as the variant's class/type name in generated SDKs.
4. **Never flatten the union into a single object** with all fields optional — this loses the mutual exclusion guarantee.
5. **Repeat shared properties** (like `variables`, `tenantId`) in each variant rather than extracting a common base. This avoids `allOf` + `oneOf` compositions that most generators handle poorly.

#### Existing polymorphic schemas in the spec

|                  Wrapper schema                   |            File             |                                    Variants                                    |
|---------------------------------------------------|-----------------------------|--------------------------------------------------------------------------------|
| `ProcessInstanceCreationInstruction`              | `process-instances.yaml`    | `...ByKey`, `...ById`                                                          |
| `ProcessInstanceCreationRuntimeInstruction`       | `process-instances.yaml`    | `...TerminateInstruction`                                                      |
| `ProcessInstanceMigrationInstruction`             | `process-instances.yaml`    | `...ByProcessDefinitionKey`, `...ByProcessDefinitionId`                        |
| `ProcessInstanceModificationTerminateInstruction` | `process-instances.yaml`    | `...ByIdInstruction`, `...ByKeyInstruction`                                    |
| `SourceElementInstruction`                        | `process-instances.yaml`    | `...IdInstruction`, `...InstanceKeyInstruction`                                |
| `AncestorScopeInstruction`                        | `process-instances.yaml`    | `Direct...`, `Inferred...`, `UseSourceParent...`                               |
| `DecisionEvaluationInstruction`                   | `decision-definitions.yaml` | `...ById`, `...ByKey`                                                          |
| `AuthorizationPatchInstruction`                   | `authorizations.yaml`       | (permission variants)                                                          |
| `SearchQueryPageRequest`                          | `search-models.yaml`        | `LimitPagination`, `OffsetPagination`, `CursorForward...`, `CursorBackward...` |

### 2.15 Security schemes

Ensure that `securitySchemes` in the OpenAPI YAML mirrors the security schemes defined in `OpenApiResourceConfig.java`. Any changes to the security config must be reflected in both places for SDK generators to produce correct authentication boilerplate.

### 2.16 Controller–spec alignment and the `@Hidden` annotation

Every public endpoint in a REST controller **must** have a corresponding path/operation in the OpenAPI spec. Conversely, every spec operation must be backed by a controller method.

#### `@Hidden` for undocumented internal endpoints

If a controller endpoint exists for internal or platform-specific reasons (e.g., SaaS-only token exchange) and must **not** appear in the public API spec, annotate the handler method with `@Hidden` (from `io.swagger.v3.oas.annotations`):

```java
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@CamundaGetMapping(path = "/me/token")
public ResponseEntity<String> getCurrentToken() { … }
```

This prevents the endpoint from appearing in the generated spec while keeping the route functional at runtime. Current uses include the SaaS token endpoint in `SaaSTokenController`.

#### `@RequiresSecondaryStorage` ↔ `x-eventually-consistent` alignment

Controller methods annotated with `@RequiresSecondaryStorage` serve data from the eventually-consistent secondary storage (e.g., Elasticsearch/OpenSearch). Their corresponding spec operations should have `x-eventually-consistent: true` so that SDK generators can emit appropriate polling behaviour.

If you add `@RequiresSecondaryStorage` to a method, verify the spec counterpart has `x-eventually-consistent: true` — and vice versa. A mismatch means either:
- The SDK will incorrectly advise callers about consistency guarantees, or
- The endpoint will reject requests when secondary storage is disabled but the spec doesn't indicate it requires it.

> **Note:** There is currently no automated CI check that cross-references `@RequiresSecondaryStorage` against `x-eventually-consistent`. This is a manual review responsibility. [#36469](https://github.com/camunda/camunda/issues/36469) tracks the aspiration to add such static analysis.

### 2.17 Operation versioning annotation (`x-added-in-version`)

Every operation **must** declare the Camunda version in which it was introduced via the `x-added-in-version` extension. This enables generated docs and SDKs to surface endpoint version availability to consumers.

```yaml
/process-instances/{processInstanceKey}/sequence-flows:
  get:
    operationId: getProcessInstanceSequenceFlows
    summary: Get sequence flows of a process instance
    description: Returns the sequence flows traversed by the given process instance.
    x-added-in-version: "8.9"
    tags:
      - Process instance
```

#### Rules

- The value is a **string** containing the minor version (e.g., `"8.6"`, `"8.9"`). Patch versions are not used.
- Set the value to the version in which the endpoint **first** ships. Do **not** update it on subsequent changes — that's what changelogs and the breaking-change rules in §2.7 are for.

#### What the Spectral rule enforces

The `require-added-in-version` rule (severity `error`) checks every operation under `paths` (`get`, `post`, `put`, `patch`, `delete`) and fails the build if `x-added-in-version` is missing. The rule does **not** validate the version string format — that's a reviewer responsibility.

If you add a new endpoint without this annotation, CI will fail with a message such as:

```
Operation "createMyResource" (POST /my-resources) is missing x-added-in-version. Every endpoint must declare the Camunda version in which it was introduced.
```

---

### 2.18 Semantic graph annotations (`x-semantic-establishes`, `x-semantic-requires`, `x-semantic-provider`, `x-semantic-client-minted`)

These annotations declare the **producer/consumer dependencies between
operations and entities**, and are consumed by the API test generator
(camunda/api-test-generator) to plan dependency-respecting integration test
chains. They have no effect on generated SDK code or the published wire
contract — they're vendor extensions for tooling.

The single source of truth for which kinds exist is
[`zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json`](../zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json).
Read the `$comment` block at the top of that file before adding a new
entry.

Tracking issues: camunda/camunda#52169 (audit), camunda/camunda#52272
(establishes/requires), camunda/camunda#52320 (`shape: external-entity`),
camunda/camunda#52322 (`identifiedBy.acceptsExternal`).

#### `x-semantic-establishes` (operation-level)

Declares that an operation **creates** (or first observes the existence
of) an instance of an entity or edge. Mutating CRUD endpoints, edge
assignments, and a small number of search endpoints that are the only
observation point for a derived entity all carry it.

```yaml
/groups:
  post:
    operationId: createGroup
    x-semantic-establishes:
      kind: Group               # PascalCase singular noun, must appear in semantic-kinds.json
      identifiedBy:
        - { in: body, name: groupId, semanticType: GroupId }

/roles/{roleId}/groups/{groupId}:
  put:
    operationId: assignRoleToGroup
    x-semantic-establishes:
      kind: RoleGroupMembership
      shape: edge               # required for membership-style relationships
      identifiedBy:
        - { in: path, name: roleId,  semanticType: RoleId }
        - { in: path, name: groupId, semanticType: GroupId }
```

Field reference:

|     Field      |                                                  Meaning                                                  |
|----------------|-----------------------------------------------------------------------------------------------------------|
| `kind`         | The kind name, registered in `semantic-kinds.json`. PascalCase singular noun.                             |
| `shape`        | Optional. Defaults to `entity`. Use `edge` for memberships observable only via member-list search.        |
| `identifiedBy` | Ordered tuple of identifying members. Length 1 for single-key entities, ≥ 2 for composite keys and edges. |

Each `identifiedBy[]` entry has `in` (`body` / `path` / `query` / `header`),
`name` (the parameter or top-level body property name), `semanticType`
(the `x-semantic-type` of the identifier — must match the registered
`identifiers` of exactly one entity kind in `semantic-kinds.json`), and
optionally `acceptsExternal: true` (see below).

#### `x-semantic-requires` (operation-level)

Declares that an operation **needs** an existing entity to operate on.
Maps each member of the established tuple to a local parameter of the
consumer.

```yaml
/groups/{groupId}/users/{username}:
  put:
    operationId: assignUserToGroup
    x-semantic-requires:
      kind: Group
      bind:
        groupId: { from: path, name: groupId }
```

The planner uses `requires` to schedule a call to a `Group`-establishing
operation (e.g. `createGroup`) before this one. The `bind` map propagates
the identifier values produced by the upstream call into this call's
parameters.

> **Composite-key entities — declare a `requires` for every foreign
> identifier you borrow.** When an entity producer's `identifiedBy`
> includes an identifier registered to a different entity kind (e.g.
> `createTenantClusterVariable` carries `TenantId`, which is owned by
> `Tenant`, in its identity tuple), the operation MUST also declare an
> `x-semantic-requires` on that owning kind. The runtime rejects
> requests against unknown referenced entities (`NOT_FOUND`); without
> the explicit `requires`, downstream chain planners walk the producer
> as a root, synthesise the identifier value, and the call fails.
> Enforced by `verify-semantic-kinds-registered`. Exceptions: edge
> producers (the planner derives implicit `requires` from the edge's
> `identifiedBy`), and the multi-owner sibling pattern where the
> identifier is also one of the establishing kind's own `identifiers`
> (e.g. `ClusterVariableName` is shared between
> `GlobalClusterVariable` and `TenantClusterVariable` as parallel
> variants, not parent/child).
>
> When the foreign identifier is owned by an `external-entity` kind
> (e.g. `ClientId`, owned by `Client`), `x-semantic-requires` is
> **not** the answer — direct `requires` on an external-entity is
> forbidden. The producer MUST instead set `acceptsExternal: true` on
> that `identifiedBy` entry (see the section below), declaring that
> the site accepts an externally-minted ID. Without either an
> `acceptsExternal: true` or a routable `requires`, the producer is
> unreachable via chain planning and is flagged as an unreachable
> orphan.

#### Per-tuple `acceptsExternal: true` (camunda/camunda#52322)

Marks a single `identifiedBy` endpoint as **bimodal** — either an in-API
producer (the canonical local entity) OR an externally-minted ID is
acceptable. Set it on edge endpoints whose runtime accepts client-minted
IDs in some deployments but the kind itself is still locally producible
elsewhere.

```yaml
# roles.yaml — assignRoleToGroup
x-semantic-establishes:
  kind: RoleGroupMembership
  shape: edge
  identifiedBy:
    - { in: path, name: roleId,  semanticType: RoleId }
    # BYOG: under OIDC `groupsClaim`, runtime accepts external IdP IDs.
    - { in: path, name: groupId, semanticType: GroupId, acceptsExternal: true }
```

Effect on the verifier: single-owner identifier resolution still runs
(typo / config-error check), but the resolved entity kind is **not**
pushed onto the producer-existence required-set for that tuple. A chain
planner that prefers an in-API producer when one exists still sees the
`createGroup → assignRoleToGroup` chain, but is free to fall back to
client-minting an external ID when no producer is reachable.

Distinct from kind-level `shape: external-entity` (below) — use the
per-tuple flag when the kind IS locally producible elsewhere and only a
specific edge endpoint is bimodal.

#### Kind-level `shape: external-entity` (camunda/camunda#52320)

Set in `semantic-kinds.json` (not in the path YAML files) for kinds
whose identifier is **always** minted outside the Camunda REST API
(e.g. `Client` IDs minted by Console or an external IdP). External
kinds are referenced via edge `identifiedBy` tuples only — it is an
error to use them directly in operation-level
`x-semantic-establishes` or `x-semantic-requires`, and the
producer-existence cross-reference is skipped for them entirely.

```jsonc
// semantic-kinds.json
{ "name": "Client", "shape": "external-entity", "identifiers": ["ClientId"] }
```

#### `x-semantic-provider` (schema-level)

Declares that a response/result schema is the **canonical producer** of
one or more identifier values. Lists the property names that carry the
produced IDs.

```yaml
DeploymentProcessResult:
  description: A deployed process.
  x-semantic-provider:
    - processDefinitionKey
    - processDefinitionId
  type: object
  required:
    - processDefinitionId
    # …
```

Used by the test generator to lift identifier values out of upstream
responses and bind them into downstream `x-semantic-requires.bind`
slots. Schema-level annotation; a single schema can provide multiple
identifiers.

> **Caveat — shared response schemas.** Because the annotation lives on
> the schema, it implicitly claims provider semantics for **every**
> operation whose response `$ref`s that schema. Do not add
> `x-semantic-provider` to a schema that is shared across operations
> with different identity contracts (e.g. a single `FooResult` reused by
> `createFoo`, `getFoo`, `updateFoo`, and `searchFoos`) — the create
> response is the canonical producer of `id`, but the search items are
> not, and the annotation cannot distinguish. In that case either split
> the schema (a dedicated `FooCreateResult` for the producing
> operation), or omit `x-semantic-provider` and let consumers chain via
> `x-semantic-establishes` on the producing operation instead. A
> structural lint guard for this is tracked in
> [#52414](https://github.com/camunda/camunda/issues/52414).

#### `x-semantic-client-minted` (identifier-level)

Set on identifier schemas in `identifiers.yaml` to declare that the ID
value is **minted by the client** rather than allocated by the server
(e.g. `TenantId`, `Username`, `RoleId`, `GroupId`). The test generator
uses this signal to know it can synthesise a fresh value at chain-plan
time instead of needing to extract one from an upstream response.

```yaml
# identifiers.yaml
TenantId:
  description: The unique identifier of the tenant.
  type: string
  format: TenantId
  x-semantic-type: TenantId
  x-semantic-client-minted: true
  pattern: ^(<default>|[\w\.\-]{1,31})$
```

#### What the Spectral rules enforce

- `semantic-establishes-shape` (severity `error`) — `x-semantic-establishes` matches the documented schema (`kind`, optional `shape`, `identifiedBy[]` with `in` / `name` / `semanticType` / optional `acceptsExternal`).
- `semantic-requires-shape` (severity `error`) — `x-semantic-requires` matches the documented schema (`kind`, `bind` map of `from` / `name`).
- `verify-semantic-kinds-registered` (severity `error`, custom JS function) — every referenced `kind:` appears in `semantic-kinds.json`; every required kind is established somewhere in the spec; an operation's `x-semantic-establishes.shape` (defaulting to `entity` when omitted) must match the kind's registered shape, so e.g. forgetting `shape: edge` on a membership operation is a lint error rather than a style issue; every `identifiedBy` / `bind` member references a parameter or top-level requestBody property that exists; no operation directly establishes/requires an `external-entity` kind; per-tuple `acceptsExternal: true` skips the producer-existence cross-reference for that tuple while still running single-owner resolution.

`x-semantic-provider` and `x-semantic-client-minted` are **not** lint-enforced — they're consumer-only signals. Add them by review.

---

## 3. Spectral linting & custom rules

### 3.1 What is Spectral?

[Spectral](https://docs.stoplight.io/docs/spectral/) is our OpenAPI linter. It validates the spec against standard OpenAPI rules **plus** our custom business rules. It replaces the need for manual review of many conventions.

### 3.2 Running locally

```bash
# Install (one-time)
npm install -g @stoplight/spectral-cli

# From the repository root, run BOTH passes:

# Pass 1: Structural validation (resolves $ref across files)
spectral lint zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml \
  --ruleset zeebe/gateway-protocol/.spectral.yaml \
  --fail-severity error

# Pass 2: File-level validation (needed for per-file schema rules)
spectral lint "zeebe/gateway-protocol/src/main/proto/v2/*.yaml" \
  --ruleset zeebe/gateway-protocol/.spectral.yaml \
  --fail-severity error
```

> ⚠️ **Both passes are required.** Pass 1 validates structural cross-file concerns (like response arrays). Pass 2 validates per-file schema concerns (like property descriptions, key types).

### 3.3 Custom rules reference

The ruleset lives in `zeebe/gateway-protocol/.spectral.yaml`. Custom functions are in `spectral-functions/`.

|                    Rule                    | Severity |                                          What it checks                                           |
|--------------------------------------------|----------|---------------------------------------------------------------------------------------------------|
| `operation-tag-defined`                    | error    | Every operation must have at least one tag                                                        |
| `no-period-in-summary`                     | error    | Path summaries must not end with a period                                                         |
| `no-flow-node-in-*` (5 rules)              | error    | "flow node" terminology is banned; use "element"                                                  |
| `require-property-descriptions`            | error    | Every schema property must have a `description`                                                   |
| `operation-key-properties-must-be-strings` | warn     | Path parameters ending in `Key` must be `type: string`                                            |
| `schema-key-properties-must-be-strings`    | warn     | Schema properties ending in `Key` must be `type: string` (or compose `BasicStringFilterProperty`) |
| `required-properties-must-exist`           | error    | Every entry in `required` must exist in `properties` or `allOf`                                   |
| `array-properties-must-be-required`        | error    | Response array properties must be `required` and not `nullable`                                   |
| `no-eventually-consistent-on-commands`     | error    | Command operations must not have `x-eventually-consistent: true`                                  |
| `valid-deprecated-enum-members`            | error    | `x-deprecated-enum-members` must be well-formed                                                   |
| `require-added-in-version`                 | error    | Every operation must declare `x-added-in-version` (see §2.17)                                     |
| `oas3-valid-schema-example`                | error    | Schemas must be valid per OpenAPI 3.0 JSON Schema rules                                           |

### 3.4 Adding new Spectral rules

1. Create the function in `spectral-functions/<functionName>.js`.
2. Register it in `.spectral.yaml` under `functions:` and define the rule under `rules:`.
3. Add tests:
   - Create `spectral-tests/fixtures/<rule-name>/` with multi-part YAML fixtures.
   - Create `spectral-tests/<functionName>.test.js` using `lintFixture()` and `filterByRule()` from `helpers.js`.
   - Run: `node --test spectral-tests/<functionName>.test.js`

Tests invoke `spectral lint` on fixture files using the production ruleset and assert on JSON results, ensuring end-to-end validation through Spectral's resolution engine.

---

## 4. REST controller implementation

Controllers live in `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/`.

### 4.1 Architecture overview

```
rest-api.yaml (OpenAPI spec)
        │
        ▼
gateway-model (openapi-generator) ──► io.camunda.gateway.protocol.model.* (generated DTOs)
        │
        ▼
gateway-mapping-http ──► RequestMapper          (protocol model → service domain)
                     ──► SearchQueryRequestMapper / SearchQueryResponseMapper
                     ──► GatewayErrorMapper     (Throwable → ProblemDetail)
                     ──► ErrorMessages           (validation message constants)
                     ──► validator/*             (request validators)
        │
        ▼
gateway-rest (zeebe) ──► controller/*           (Spring REST controllers)
                     ──► RestErrorMapper         (thin wrapper → ResponseEntity<ProblemDetail>)
                     ──► RequestExecutor         (async service call + error handling)
                     ──► @CamundaRestController, @CamundaPostMapping, etc.
```

### 4.2 Controller conventions

1. **Annotation:** Use `@CamundaRestController` (not plain `@RestController`). This is a composed annotation that includes conditional bean registration.

2. **HTTP method annotations:** Use Camunda-specific annotations that set default `produces`/`consumes`:

   - `@CamundaPostMapping`
   - `@CamundaGetMapping`
   - `@CamundaPutMapping`
   - `@CamundaPatchMapping`
   - `@CamundaDeleteMapping`
3. **Query endpoints** that require search infrastructure: annotate with `@RequiresSecondaryStorage`.
4. **Constructor injection:** Receive services and mappers via constructor (not field injection).
5. **Controller responsibilities are limited to:**
   - Mapping and validating user input (via `RequestMapper`, `RequestValidator`)
   - Invoking the service method
   - Mapping the result back to success or failure (via `ResponseMapper`, `RestErrorMapper`)

### 4.3 Command (write) endpoints

Command endpoints return `CompletableFuture<ResponseEntity<Object>>`:

```java
@CamundaPostMapping(path = "/{resourceKey}/action")
public CompletableFuture<ResponseEntity<Object>> performAction(
    @PathVariable final long resourceKey,
    @RequestBody(required = false) final ActionRequest actionRequest) {

  return RequestMapper.toActionRequest(actionRequest, resourceKey)
      .fold(
          RestErrorMapper::mapProblemToCompletedResponse,  // Left: validation error
          request ->
              RequestExecutor.executeServiceMethod(        // Right: execute command
                  () -> services.performAction(request),
                  ResponseMapper::toActionResponse,
                  HttpStatus.OK));
}
```

For commands returning **204 No Content**:

```java
return RequestMapper.toCompleteRequest(request, resourceKey)
    .fold(
        RestErrorMapper::mapProblemToCompletedResponse,
        req -> RequestExecutor.executeServiceMethodWithNoContentResult(
            () -> services.complete(req)));
```

### 4.4 Query (read) endpoints

Query endpoints return `ResponseEntity<Object>` synchronously, wrapped in try/catch:

```java
@RequiresSecondaryStorage
@CamundaPostMapping(path = "/search")
public ResponseEntity<Object> searchResources(
    @RequestBody(required = false) final ResourceSearchQuery query) {
  return SearchQueryRequestMapper
      .toResourceSearchQuery(query)
      .fold(
          RestErrorMapper::mapProblemToResponse,
          request -> {
            try {
              return ResponseEntity.ok()
                  .body(SearchQueryResponseMapper.toResourceSearchQueryResult(
                      services.search(request)));
            } catch (final Exception e) {
              return RestErrorMapper.mapErrorToResponse(e);
            }
          });
}
```

### 4.5 Error handling

|       Layer        |                                          Mechanism                                           |
|--------------------|----------------------------------------------------------------------------------------------|
| Request validation | `RequestMapper` returns `Either<ProblemDetail, T>` → `RestErrorMapper::mapProblemToResponse` |
| Service exceptions | `GatewayErrorMapper` maps `ServiceException` status → HTTP status                            |
| Async errors       | `RequestExecutor` handles via `.handleAsync()` on `CompletableFuture`                        |
| Global fallback    | `GlobalControllerExceptionHandler` catches unhandled exceptions                              |

Error responses always use `application/problem+json` with the `ProblemDetail` schema (RFC 9457).

### 4.6 Request and response mapping

- **`RequestMapper`** — static utility converting OpenAPI protocol models to internal service domain objects. Collects validation errors in `ErrorMessages`.
- **`SearchQueryResponseMapper`** — static utility converting search result entities to OpenAPI protocol model responses.
- **`RestErrorMapper`** — thin wrapper: `Throwable` → `ResponseEntity<ProblemDetail>`.
- **`ErrorMessages`** — constants class with parameterized validation messages.

When adding new mappings, follow existing patterns. Add new error messages to `ErrorMessages` for consistency.

---

## 5. Service layer

Services live in the `service/` module and bridge controllers with Zeebe brokers or search clients.

### 5.1 Search services

1. Extend `SearchQueryService`.
2. Create a `TypedSearchQuery` implementation with `Filter` and `Sort` classes (e.g., `ProcessInstanceQuery`, `ProcessInstanceFilter`, `ProcessInstanceSort`).
3. Create a Java `record` entity in `search/search-domain` (e.g., `ProcessInstanceEntity`).
4. Use the query and entity classes as generics when extending `SearchQueryService`.

### 5.2 Command services

1. Extend `ApiServices`.
2. Reuse existing broker request classes in `zeebe/gateway/src/main/java/io/camunda/zeebe/gateway/impl/broker/request` or create new ones.
3. Send requests via `sendBrokerRequest`.
4. If the broker command is new, implement and test it in the engine first.

---

## 6. Camunda Client extension

After the REST endpoint is complete, extend the Java client:

1. Add a new command method to `CamundaClient.java`.
2. For **search**, implement `TypedSearchQueryRequest`.
3. For **commands**, implement a step-builder chain guiding users from required to optional inputs, ending in `FinalCommandStep`.
4. **Choose the correct pagination type** for your request based on the OpenAPI schema's `page` field:
   - `AnyPage` (factory: `SearchRequestBuilders.anyPage()`) — when the schema uses `SearchQueryPageRequest` (supports offset *and* cursor pagination). Used by most search endpoints.
   - `OffsetPage` (factory: `SearchRequestBuilders.offsetPage()`) — when the schema uses `OffsetPagination` (offset-only, `from` + `limit`).
   - `CursorForwardPage` (factory: `SearchRequestBuilders.cursorForwardPage()`) — when the schema uses `CursorForwardPagination` (cursor-only, `after` + `limit`).

   Each page type has a corresponding `*Impl` class that implements `TypedSearchRequestPropertyProvider<T>`, where `T` must match the generated protocol model expected by the endpoint. Using the wrong page type causes a `ClassCastException` at runtime. Follow the same type-safety pattern used for sort (`TypedSortableRequest<S, SELF>`).

5. Provide client-level unit tests mocking REST API interactions.

---

## 7. Testing strategy

### 7.1 Unit tests (required)

**Controller-level unit tests** mock the service layer and validate:
- Input mapping (request body → service request)
- Output mapping (service response → response body)
- Error handling (service exceptions → ProblemDetail responses)

**Service-level unit tests** mock broker/search interactions and validate:
- Input validation and transformation
- Broker exception mapping

**General conventions:**
- Use **JUnit 5** and **AssertJ** (never JUnit/Hamcrest assertions).
- Prefix test names with `should...` (e.g., `shouldReturnProcessDefinitionWhenKeyExists`).
- Structure tests with `// given`, `// when`, `// then` comments.
- Test one behavior per test method.
- Use [Awaitility](http://www.awaitility.org/) when waiting is unavoidable. **Never use `Thread.sleep`.**

### 7.2 Spectral rule tests

When adding or modifying custom Spectral rules:

```bash
# Run all rule tests
cd zeebe/gateway-protocol && node --test spectral-tests/*.test.js

# Run a specific test
node --test spectral-tests/verifyArrayPropertiesRequired.test.js
```

### 7.3 Integration tests (optional, recommended for new broker commands)

- **Engine ITs**: `zeebe/qa/integration-tests` — tests using the Camunda Client against a running engine.
- **Acceptance tests**: `qa/acceptance-tests` — end-to-end tests validating features across all supported data layers.

When writing ITs that depend on exported data (deployments, process instances), use Awaitility to verify exports completed before asserting:

```java
Awaitility.await("should receive data from ES")
    .atMost(Duration.ofMinutes(1))
    .ignoreExceptions()
    .untilAsserted(() -> {
      assertThat(camundaClient
          .newProcessDefinitionSearchRequest()
          .filter(filter -> filter.processDefinitionId(PROCESS_ID))
          .send().join().items())
          .hasSize(1);
    });
```

### 7.4 E2E API test suite (Playwright)

A Playwright-based E2E test suite validates REST API v2 endpoints against a real running Camunda instance. Tests are organised by resource under `qa/c8-orchestration-cluster-e2e-test-suite/tests/api/v2/<resource>/` and run in CI via the dedicated **`api-tests`** Playwright project.

The suite uses `assert-json-body` to automatically validate response shapes against the OpenAPI spec, ensuring that actual responses match the contract defined in the spec.

> ℹ️ For full setup, utilities reference, and contribution instructions see the [E2E test suite README](../qa/c8-orchestration-cluster-e2e-test-suite/README.md).

### 7.5 Auto-generated request validation tests

Machine-generated negative tests (expected HTTP 400) cover every endpoint in the OpenAPI spec. They live under `qa/c8-orchestration-cluster-e2e-test-suite/v2-stateless-tests/tests/request-validation/` and are produced by the request validation test generator. Scenarios include missing required fields, type mismatches, enum violations, `oneOf` ambiguities, constraint breaches, and more.

> ⚠️ Generated test files must not be edited manually — they are overwritten on regeneration.
>
> ℹ️ For details see the [generator README](../qa/c8-orchestration-cluster-e2e-test-suite/v2-stateless-tests/request-validation-test-generator/README.md).

### 7.6 Forward compatibility tests (nightly)

A **nightly CI workflow** verifies that newer server versions remain compatible with API tests written for older versions. This catches accidental breaking changes that would otherwise only surface after release.

**Workflow:** [`c8-orchestration-cluster-forward-compatibility-tests.yml`](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-forward-compatibility-tests.yml)

**How it works:**

1. Discovers all `stable/X.Y` branches ≥ 8.8.
2. Builds a dynamic matrix of server → test branch pairs:
   - `main` server → newest `stable/X.Y` tests
   - Each consecutive `stable/X.Y` server → `stable/X.(Y-1)` tests
3. For each pair, pulls the server Docker image from the **server branch** and runs the API tests from the **test branch**.

Because the _tests_ come from an older branch, any endpoint that previously worked must still produce a compatible response on the newer server. A failure signals a **forward-incompatible (breaking) change**.

**Manual dispatch** is also supported — you can specify a custom `server_branch` / `test_branch` pair via the GitHub Actions UI.

Failures are reported to Slack and visible in the [Actions tab](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-forward-compatibility-tests.yml).

**What this means for contributors:**

- If you change an endpoint's response shape, remove a field, or alter error codes, the forward compatibility tests will detect it.
- If your PR intentionally introduces a breaking change (see §2.7), coordinate with `@camunda/camunda-ex` to update the test expectations on the affected older branches _before_ merging.

There is also an **on-demand workflow** ([`c8-orchestration-cluster-e2e-api-test-branch-on-demand.yml`](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-api-test-branch-on-demand.yml)) that builds the server _and_ runs the tests from the same branch. This is useful for verifying that a feature branch passes all API tests — both against Elasticsearch _and_ H2/RDBMS — before merging. It also detects differential behavior between storage engines.

---

## 8. CI pipeline & merge checklist

### 8.1 Automated CI checks

The `openapi-lint` job runs automatically when OpenAPI files change. It executes two Spectral passes (structural + file-level) and fails on any error-severity violation.

### 8.2 Pre-merge checklist

Before opening a PR:

- [ ] **OpenAPI spec** defined/updated in `zeebe/gateway-protocol/src/main/proto/v2/`
- [ ] **Spectral passes locally** (both structural and file-level passes)
- [ ] **No "flow node" terminology** anywhere in paths, operation IDs, summaries, descriptions, or property names
- [ ] **All properties have descriptions**
- [ ] **Key properties are `type: string`**
- [ ] **`x-eventually-consistent`** set correctly (`true` for queries, `false` for commands)
- [ ] **`x-added-in-version`** field present on every new operation (see §2.17)
- [ ] **Response arrays** are `required` and not `nullable`
- [ ] **`required` entries** all exist in `properties`
- [ ] **Controller implemented** following conventions (§4)
- [ ] **Unit tests pass** for controller and service
- [ ] **Code formatted:** `./mvnw spotless:apply -T1C`
- [ ] **License headers:** `./mvnw license:format -T1C`
- [ ] **`@camunda/c8-api-team`** assigned as reviewer (auto-assigned via CODEOWNERS)

### 8.3 Useful commands

```bash
# Format code
./mvnw license:format spotless:apply -T1C

# Build quickly (skip tests & checks)
./mvnw install -Dquickly -T1C

# Run unit tests
./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C

# Run integration tests
./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C

# Generate models locally
./mvnw -f gateways/gateway-model/pom.xml clean install -Dquickly
```

---

## 9. Documentation generation

[The public REST API reference documentation](https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/orchestration-cluster-rest-api/) is generated from the OpenAPI spec.

### 9.1 Automatic synchronization

A [weekly workflow](https://github.com/camunda/camunda-docs/actions/workflows/sync-rest-api-docs.yaml) in the `camunda-docs` repository automatically syncs the OpenAPI spec into the docs:

- **"next" version:** Pulled from the `main` branch.
- **Older supported versions** (e.g., 8.6, 8.7, 8.8): Pulled from the corresponding `stable/<version>` branches (based on `versions.json` in `camunda-docs`). See [#8177](https://github.com/camunda/camunda-docs/pull/8177).

This means **backported spec fixes to `stable/*` branches are picked up automatically** — you no longer need to manually sync docs for older versions.

If your changes are not urgent, you can wait for the next scheduled run. Otherwise, trigger the workflow manually (see §9.2).

### 9.2 Manual synchronization

1. Trigger a manual run of the [synchronization workflow](https://github.com/camunda/camunda-docs/actions/workflows/sync-rest-api-docs.yaml). This syncs both `main` ("next") and all `stable/*` branches at once.
2. If you need to tweak the generated output beyond what the spec provides, follow the [documentation generation guide](https://github.com/camunda/camunda-docs/blob/main/howtos/interactive-api-explorers.md) and create a docs PR following the [documentation team's guidelines](https://github.com/camunda/camunda-docs/blob/main/CONTRIBUTING.MD).

> **Important:** The OpenAPI spec in `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml` is the **single source of truth**. Never edit the generated docs directly — always fix the spec and regenerate.

### 9.3 Alpha feature banners

The docs generation pipeline automatically detects the alpha sentinel text (see §2.8) and applies two transforms before generating the MDX pages:

1. **Admonition wrapping** — The paragraph is wrapped in a `:::note` block, rendering as a visible callout banner.
2. **Link injection** — The phrase "alpha feature" is turned into a link to the [alpha features reference page](https://docs.camunda.io/docs/next/components/early-access/alpha/alpha-features/). The link target varies by version.

This happens in the `preGenerateDocs` step of the version-specific generation strategies in `camunda-docs`. No manual docs edits are needed — just use the exact sentinel text in your OpenAPI description and the banner will appear automatically.

---

## 10. Common mistakes & how to fix them

### ❌ `required: false` on a property

```yaml
# Wrong
properties:
  myField:
    type: string
    required: false
```

**Fix:** Remove `required: false`. If the field is required, add it to the `required` array at the object level.

---

### ❌ `x-eventually-consistent: true` on a command

```yaml
# Wrong
/resources:
  post:
    operationId: createResource
    x-eventually-consistent: true
```

**Fix:** Change to `x-eventually-consistent: false`. Only search/statistics/GET endpoints use `true`.

---

### ❌ Missing `x-added-in-version` on a new operation

```yaml
# Wrong — Spectral error: require-added-in-version
/my-resources:
  post:
    operationId: createMyResource
    summary: Create a my-resource
    tags: [My resource]
```

**Fix:** Add `x-added-in-version` set to the version in which the endpoint first ships:

```yaml
/my-resources:
  post:
    operationId: createMyResource
    summary: Create a my-resource
    x-added-in-version: "8.9"
    tags: [My resource]
```

See §2.17 for full conventions.

---

### ❌ Missing description on a property

```yaml
# Wrong — Spectral error: require-property-descriptions
properties:
  name:
    type: string
```

**Fix:** Add a `description`:

```yaml
properties:
  name:
    type: string
    description: The name of the resource.
```

---

### ❌ Optional array property in response

```yaml
# Wrong — will produce `string[] | undefined` in SDKs
MyResult:
  type: object
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/Thing'
```

**Fix:** Add `items` to `required`:

```yaml
MyResult:
  type: object
  required:
    - items
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/Thing'
```

---

### ❌ Numeric key type

```yaml
# Wrong — will overflow in JavaScript
properties:
  processDefinitionKey:
    type: integer
    format: int64
```

**Fix:** Use `type: string` and reference the shared key schema:

```yaml
properties:
  processDefinitionKey:
    allOf:
      - $ref: 'keys.yaml#/components/schemas/ProcessDefinitionKey'
    description: The key for this process definition.
```

---

### ❌ Using "flow node" in any API surface

**Fix:** Replace with "element" everywhere — paths, operation IDs, summaries, descriptions, property names.

---

### ❌ `required` entry referencing a non-existent property

```yaml
# Wrong — "status" doesn't exist in properties
MySchema:
  type: object
  required:
    - name
    - status
  properties:
    name:
      type: string
      description: The name.
```

**Fix:** Either add `status` to `properties` or remove it from `required`.

---

### ❌ Inconsistent response codes

**Fix:** Reuse `common-responses.yaml` definitions. Align with similar existing endpoints. Use the response code table in §2.8.

---

## 11. Reference links

|           Resource           |                                                                                   Location                                                                                    |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Public REST API reference    | [docs.camunda.io](https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/orchestration-cluster-rest-api/)                                 |
| OpenAPI 3.0.3 specification  | [spec.openapis.org](https://spec.openapis.org/oas/v3.0.3)                                                                                                                     |
| OpenAPI guide                | [learn.openapis.org](https://learn.openapis.org/)                                                                                                                             |
| Camunda style guide          | [Confluence](https://confluence.camunda.com/display/HAN/Camunda+style+guide)                                                                                                  |
| OpenAPI spec (v2)            | `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml`                                                                                                                      |
| Spectral ruleset             | `zeebe/gateway-protocol/.spectral.yaml`                                                                                                                                       |
| Custom Spectral functions    | `zeebe/gateway-protocol/spectral-functions/`                                                                                                                                  |
| Spectral tests               | `zeebe/gateway-protocol/spectral-tests/`                                                                                                                                      |
| OpenAPI validation guide     | `zeebe/gateway-protocol/OPENAPI_VALIDATION.md`                                                                                                                                |
| REST controller guide        | `docs/rest-controller.md`                                                                                                                                                     |
| Testing guide                | `docs/testing.md`                                                                                                                                                             |
| Unit test guide              | `docs/testing/unit.md`                                                                                                                                                        |
| Acceptance test guide        | `docs/testing/acceptance.md`                                                                                                                                                  |
| E2E API test suite           | `qa/c8-orchestration-cluster-e2e-test-suite/`                                                                                                                                 |
| E2E test suite README        | `qa/c8-orchestration-cluster-e2e-test-suite/README.md`                                                                                                                        |
| Request validation generator | `qa/c8-orchestration-cluster-e2e-test-suite/v2-stateless-tests/request-validation-test-generator/README.md`                                                                   |
| Forward compat tests         | [c8-orchestration-cluster-forward-compatibility-tests.yml](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-forward-compatibility-tests.yml)     |
| On-demand API tests          | [c8-orchestration-cluster-e2e-api-test-branch-on-demand.yml](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-api-test-branch-on-demand.yml) |
| Nightly API tests            | [c8-orchestration-cluster-e2e-tests-nightly.yml](https://github.com/camunda/camunda/actions/workflows/c8-orchestration-cluster-e2e-tests-nightly.yml)                         |
| Docs sync workflow           | [sync-rest-api-docs.yaml](https://github.com/camunda/camunda-docs/actions/workflows/sync-rest-api-docs.yaml)                                                                  |
| Docs generation guide        | [interactive-api-explorers.md](https://github.com/camunda/camunda-docs/blob/main/howtos/interactive-api-explorers.md)                                                         |
| Controllers                  | `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/`                                                                                                  |
| Request/Response mappers     | `gateways/gateway-mapping-http/`                                                                                                                                              |
| Generated models             | `gateways/gateway-model/` (generated from OpenAPI spec)                                                                                                                       |
| Service layer                | `service/`                                                                                                                                                                    |
| Camunda Java Client          | `clients/java/`                                                                                                                                                               |
| CI OpenAPI lint job          | `.github/workflows/ci.yml` (job: `openapi-lint`)                                                                                                                              |
| Slack channel                | [`#top-c8-cluster-api-governance`](https://camunda.slack.com/archives/C0A154VV8DB)                                                                                            |
| API team (reviewers)         | `@camunda/c8-api-team`                                                                                                                                                        |

