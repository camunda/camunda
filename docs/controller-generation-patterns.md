# Controller Generation Patterns

This document describes the four mechanical patterns found in the hand-written REST controllers,
how each pattern maps to signals in the OpenAPI specification, and the ~10 custom endpoints that
fall outside these patterns.

## Pattern Classification Algorithm

Every operation in the spec can be classified by inspecting four fields:

|        Spec Signal        |                        Value                         |
|---------------------------|------------------------------------------------------|
| `httpMethod`              | GET, POST, PUT, DELETE, PATCH                        |
| `path`                    | e.g. `/incidents/search`, `/incidents/{incidentKey}` |
| `status`                  | The primary success response code: 200, 201, 204     |
| `requestBody`             | Schema ref, `MULTIPART`, or absent                   |
| `response 200`            | Schema ref, `XML`, or absent                         |
| `queryParams`             | e.g. `truncateValues`, `from`, `to`                  |
| `x-eventually-consistent` | `true` or `false`                                    |

### Classification rules (evaluated in order)

```
1. SEARCH:    POST + path ends with /search + status=200 + response schema exists
2. STATISTICS: POST + path contains /statistics/ + status=200 + response schema exists
3. GET_BY_KEY: GET + pathParams non-empty + status=200 + response schema (JSON) exists
4. MUTATION_VOID: status=204 + no response body
5. MUTATION_RESPONSE: status=200|201 + response body exists + not SEARCH/STATISTICS/GET_BY_KEY
6. CUSTOM: anything else (XML, MULTIPART, streaming, query-param-only GET, etc.)
```

---

## Pattern 1: SEARCH (fold pattern)

**Spec signature:**
- `POST /{resource}/search` (or `POST /{resource}/{key}/{sub-resource}/search`)
- `status=200`
- `requestBody` references a `*SearchQuery` or `*SearchQueryRequest` schema
- `response` references a `*SearchQueryResult` or `*SearchResult` schema
- `x-eventually-consistent: true`

**Controller shape:**

```java
@Override
public ResponseEntity<{ResponseType}> {operationId}(final {RequestType} request) {
    return SearchQueryRequestMapper.to{QueryName}(request)
        .fold(RestErrorMapper::mapProblemToResponse, this::{operationId}Internal);
}

private ResponseEntity<{ResponseType}> {operationId}Internal(final {InternalQueryType} query) {
    try {
        final var result = {service}.{serviceMethod}(query, auth);
        return ResponseEntity.ok(SearchQueryResponseMapper.to{ResponseName}(result));
    } catch (final Exception e) {
        return mapErrorToResponse(e);
    }
}
```

**Derivable from spec:**
- `{RequestType}`: from `requestBody.$ref` schema name
- `{ResponseType}`: from `responses.200.$ref` schema name
- `{operationId}`: directly from spec `operationId`

**Not directly in spec (convention-based):**
- `SearchQueryRequestMapper.to{X}Query()` — naming convention
- `SearchQueryResponseMapper.to{X}Response()` — naming convention
- `{InternalQueryType}` — `io.camunda.search.query.{X}Query`
- `{service}.search()` — service injection + method name

**Count:** ~25 endpoints across the surface

---

## Pattern 2: GET_BY_KEY (try-catch pattern)

**Spec signature:**
- `GET /{resource}/{key}`
- `status=200`
- No `requestBody`
- One path parameter (the key)
- `response` references a `*Result` schema
- `x-eventually-consistent: true`

**Controller shape:**

```java
@Override
public ResponseEntity<{ResponseType}> {operationId}(final String {pathParam}) {
    try {
        final var result = {service}.getByKey({maybeParseKey}, auth);
        return ResponseEntity.ok(SearchQueryResponseMapper.to{EntityName}(result));
    } catch (final Exception e) {
        return mapErrorToResponse(e);
    }
}
```

**Derivable from spec:**
- `{ResponseType}`: from `responses.200.$ref` schema name
- `{pathParam}`: from `parameters[0].name`
- `{operationId}`: from spec `operationId`
- Key type: whether to call `Long.parseLong(key)` — determined by the key schema
(`keys.yaml` integer-based → parse, string-based → pass through)

**Not directly in spec (convention-based):**
- `{service}.getByKey()` or `{service}.getById()` — service method name
- `SearchQueryResponseMapper.to{X}()` — naming convention

**Count:** ~15 endpoints

---

## Pattern 3: MUTATION_VOID (executor pattern)

**Spec signature:**
- `POST`, `PUT`, `DELETE`, or `PATCH`
- `status=204`
- No response body
- May or may not have `requestBody`
- `x-eventually-consistent: false`

**Controller shape:**

```java
@Override
public ResponseEntity<Void> {operationId}(
        final String {pathParam},     // if path params exist
        final {RequestType} request)  // if request body exists
{
    return RequestExecutor.executeSync(
        () -> {service}.{action}({args}, auth));
}
```

**Derivable from spec:**
- `{RequestType}`: from `requestBody.$ref` if present
- `{pathParam}`: from path parameters
- `{operationId}`: from spec `operationId`

**Not directly in spec:**
- `{service}.{action}()` — service method name and argument mapping
- Whether to use `RequestExecutor.executeSync()` vs
`RequestExecutor.executeServiceMethodWithNoContentResult()`

**Count:** ~20 endpoints

---

## Pattern 4: MUTATION_RESPONSE (executor-with-body pattern)

**Spec signature:**
- `POST`, `PUT`
- `status=200` or `status=201`
- Response body exists (JSON schema)
- **Not** a search or statistics endpoint (path doesn't end in `/search` or
contain `/statistics/`)
- `x-eventually-consistent: false`

**Controller shape:**

```java
@Override
public ResponseEntity<{ResponseType}> {operationId}(
        final String {pathParam},     // if path params exist
        final {RequestType} request)
{
    return RequestMapper.to{InternalRequest}(request, ...)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            req -> RequestExecutor.executeServiceMethod(
                () -> {service}.{action}(req, auth),
                ResponseMapper::to{Response},
                HttpStatus.{STATUS}));
}
```

**Derivable from spec:**
- `{RequestType}`, `{ResponseType}`: from schema refs
- `{pathParam}`: from path parameters
- `{operationId}`: from spec `operationId`
- `{STATUS}`: `CREATED` for 201, `OK` for 200

**Not directly in spec:**
- `RequestMapper.to{X}()` — request mapper name and extra arguments
- `ResponseMapper.to{X}()` — response mapper name
- `{service}.{action}()` — service method and arguments

**Count:** ~15 endpoints

---

## Custom Endpoints (not fitting any mechanical pattern)

These ~10 endpoints require hand-written logic beyond the four patterns:

| #  |         operationId          |                  Path                  |                     Why it's custom                      |
|----|------------------------------|----------------------------------------|----------------------------------------------------------|
| 1  | `getDecisionDefinitionXML`   | `GET /decision-definitions/{key}/xml`  | Returns `text/xml` content type, not JSON                |
| 2  | `getDecisionRequirementsXML` | `GET /decision-requirements/{key}/xml` | Returns `text/xml` content type, not JSON                |
| 3  | `getProcessDefinitionXML`    | `GET /process-definitions/{key}/xml`   | Returns `text/xml` content type, not JSON                |
| 4  | `getStartProcessForm`        | `GET /process-definitions/{key}/form`  | Returns form model from different schema file            |
| 5  | `getUserTaskForm`            | `GET /user-tasks/{key}/form`           | Returns form model, Optional wrapping                    |
| 6  | `activateJobs`               | `POST /jobs/activation`                | Async streaming via ResponseObserver                     |
| 7  | `getDocument`                | `GET /documents/{id}`                  | StreamingResponseBody, binary content                    |
| 8  | `createDocument`             | `POST /documents`                      | Multipart upload                                         |
| 9  | `createDocuments`            | `POST /documents/batch`                | Batch multipart upload                                   |
| 10 | `getStatus`                  | `GET /status`                          | 204 if healthy, 503 if not — status-dependent            |
| 11 | `getLicense`                 | `GET /license`                         | Direct model construction, no service layer              |
| 12 | `getSystemConfiguration`     | `GET /system/configuration`            | Direct config object read                                |
| 13 | `getAuthentication`          | `GET /authentication/me`               | CamundaUserService, 401 logic                            |
| 14 | `getResourceContent`         | `GET /resources/{key}/content`         | Returns raw binary content                               |
| 15 | `getGlobalJobStatistics`     | `GET /jobs/statistics/global`          | Query params (from, to, jobType) instead of body         |
| 16 | `getUsageMetrics`            | `GET /system/usage-metrics`            | Query params (startTime, endTime, tenantId, withTenants) |

### XML responses (1–3)

All three follow the same sub-pattern: service returns `Optional<String>`,
controller returns `ResponseEntity<String>` with `MediaType.TEXT_XML_VALUE`.
Could be a fifth mechanical pattern if needed.

### Form responses (4–5)

Service returns `Optional<FormItem>`, mapped via `SearchQueryResponseMapper.toFormItem()`.
Similar to GET_BY_KEY but with Optional unwrapping.

### Streaming/binary (6–8, 14)

Fundamentally different: async observers, multipart parsing, streaming bodies.
Not generatable from spec alone.

### Status/config/auth (10–13)

Bespoke logic with no service-layer search/mutation pattern.

### Query-param statistics (15–16)

Like STATISTICS but parameters come from query string instead of request body.

---

## What the Generator Can Derive From the Spec

For every operation, these fields are available in the YAML:

```
operationId          → Java method name
tags[0]              → API interface grouping (→ controller class)
httpMethod + path    → pattern classification (SEARCH/GET_BY_KEY/MUTATION_VOID/etc.)
requestBody.$ref     → request DTO type name
responses.200.$ref   → response DTO type name
parameters[in=path]  → path parameter names
parameters[in=query] → query parameter names
x-eventually-consistent → whether @RequiresSecondaryStorage applies
```

**Convention derivations** (from the schema/type names):

|                Need                |                                           Derivation                                           |
|------------------------------------|------------------------------------------------------------------------------------------------|
| API interface class                | `{Tag}Api` where Tag = tag with spaces removed (e.g. "Batch operation" → `BatchOperationApi`)  |
| Service class                      | `{Tag}Services` (e.g. `IncidentServices`)                                                      |
| Service field                      | `{tag}Services` (camelCase)                                                                    |
| `SearchQueryRequestMapper` method  | Drop `SearchQuery`/`SearchQueryRequest` suffix from request type, prepend `to`, append `Query` |
| `SearchQueryResponseMapper` method | Drop `SearchQuery` prefix/suffix from response type, prepend `to`                              |
| Internal query FQN                 | `io.camunda.search.query.{QueryName}`                                                          |
| GET_BY_KEY service method          | `getByKey` (standard) or `getById` (for string keys)                                           |
| GET_BY_KEY response mapper         | `to{EntityName}` — strip `Result` suffix from response schema                                  |

Operations where conventions don't hold (MUTATION_VOID, MUTATION_RESPONSE) require
service method names and argument mappings that are **not derivable from the spec**.
These need either explicit entries in a mapping table or a different generation strategy.
