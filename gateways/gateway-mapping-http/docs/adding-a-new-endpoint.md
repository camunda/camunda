# Adding a New Endpoint (Spec-Driven Flow)

> **Audience:** Engineers adding or modifying REST API endpoints in the Orchestration Cluster.
>
> **Prerequisites:** Read [contract-adaptation-architecture.md](contract-adaptation-architecture.md) for the
> design rationale behind this approach.

This guide covers the spec-driven flow where the OpenAPI specification is the single source of
truth and the contract generator produces controllers, DTOs, and adapter interfaces.

---

## Overview

```
YAML spec
  → Generator produces: StrictContract DTO + Controller + Adapter interface
    → Controller deserializes JSON into DTO
      → Adapter calls RequestMapper (validates + maps to service types)
        → Service layer executes business logic
          → ResponseMapper converts result back to DTO (if needed)
            → Controller returns HTTP response
```

## Steps

### 1. Define the OpenAPI spec

Add or edit a YAML file in `zeebe/gateway-protocol/src/main/proto/v2/`.

Define:

- The **path** and **HTTP method** under `paths:`
- An **`operationId`** (e.g., `assignUserTask`)
- **Request/response schemas** under `components: schemas:`

Example from `user-tasks.yaml`:

```yaml
paths:
  /user-tasks/{userTaskKey}/assignment:
    post:
      operationId: assignUserTask
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UserTaskAssignmentRequest'
      responses:
        '204':
          description: The user task was assigned.

components:
  schemas:
    UserTaskAssignmentRequest:
      type: object
      required:
        - assignee
      properties:
        assignee:
          type: string
        allowOverride:
          type: boolean
          nullable: true
        action:
          type: string
          nullable: true
```

### 2. Run the contract generator

```bash
cd gateways/gateway-mapping-http
JAVA_HOME="$HOME/.jenv/versions/21.0.9" java tools/GenerateContractMappingPoc.java
```

The generator reads the YAML specs from `zeebe/gateway-protocol/src/main/proto/v2/` and produces:

|         Output          |                               Location                                |           Naming convention           |
|-------------------------|-----------------------------------------------------------------------|---------------------------------------|
| **Strict contract DTO** | `gateways/gateway-mapping-http/src/main/java/.../contract/generated/` | `Generated<SchemaName>StrictContract` |
| **Controller**          | `zeebe/gateway-rest/src/main/java/.../controller/generated/`          | `Generated<Resource>Controller`       |
| **Adapter interface**   | `zeebe/gateway-rest/src/main/java/.../controller/adapter/`            | `<Resource>ServiceAdapter`            |

For a schema named `UserTaskAssignmentRequest`, the generated DTO is
`GeneratedUserTaskAssignmentRequestStrictContract` — an immutable Java record with:

- Direct field accessors: `assignee()`, `allowOverride()`, `action()`
- Null-coercion accessors: `actionOrDefault()` returns `""` instead of `null`

### 3. Add validation (if needed)

Create or update a validator in `gateways/gateway-mapping-http/src/main/java/.../validator/`.

Validators return `Optional<ProblemDetail>` — empty means valid:

```java
public static Optional<ProblemDetail> validateMyRequest(
    final GeneratedMyRequestStrictContract request) {
  return RequestValidator.validate(violations -> {
    if (request.requiredField() == null || request.requiredField().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("requiredField"));
    }
  });
}
```

See `UserTaskRequestValidator`, `JobRequestValidator`, `SignalRequestValidator` for examples.

### 4. Add request mapping

In `RequestMapper.java`, add a method that converts the strict contract DTO to the service-layer
request type. The standard pattern:

```java
public static Either<ProblemDetail, ServiceLayerRequest> toMyRequest(
    final GeneratedMyRequestStrictContract request, final long pathKey) {
  return getResult(
      MyValidator.validateMyRequest(request),
      () -> new ServiceLayerRequest(
          pathKey,
          request.someFieldOrDefault()));
}
```

**Key conventions:**

- Use `fooOrDefault()` accessors for nullable fields whose service layer requires non-null values
  (e.g., `String → ""`, `Map → Map.of()`, `List → List.of()`)
- Return `Either<ProblemDetail, T>` so validation errors flow back as 400 responses
- If multiple fields need to be extracted for the adapter to destructure, define an intermediate
  record in `MappedCommandRequests.java`

### 5. Add response mapping (if the endpoint returns a body)

For `204 No Content` commands (assign, complete, delete, etc.) — no response mapping needed.

For endpoints returning data, add a method in `ResponseMapper.java` using the generated
**null-safe step builder**.

#### The step builder pattern

Generated response DTOs use a typed step builder that enforces required fields at compile time.
Each required field is a step interface that returns the next step — you cannot call `.build()`
until all required fields are set in order. Optional/nullable fields are set on the final
`OptionalStep`.

```
builder() → TypeStep → NameStep → ... → OptionalStep → build()
                                              ↑
                                     nullable fields set here
```

The builder also handles **key coercion** automatically: fields typed as `Object` in the builder
accept both `Long` and `String` values. At `build()` time, coercion helpers convert `Long` keys
to `String` (e.g., `12345L → "12345"`).

**Required fields** are validated in the record's compact constructor — if any are null, an
`IllegalArgumentException` is thrown listing all missing fields.

#### Simple response (record constructor)

For responses with few fields, use the record constructor directly:

```java
public static GeneratedMyResponseStrictContract toMyResponse(
    final MyDomainObject domain) {
  return new GeneratedMyResponseStrictContract(
      String.valueOf(domain.key()),
      domain.name(),
      domain.otherField());
}
```

#### Complex response (step builder)

For responses with many fields or key coercion, use the step builder:

```java
static GeneratedActivatedJobStrictContract toActivatedJob(
    final long jobKey, final JobRecord job) {
  return GeneratedActivatedJobStrictContract.builder()
      .type(job.getType())                           // required (step interface)
      .processDefinitionId(job.getBpmnProcessId())   // required (step interface)
      .processDefinitionVersion(job.getProcessDefinitionVersion())
      .elementId(job.getElementId())
      .customHeaders(job.getCustomHeadersObjectMap())
      .worker(bufferAsString(job.getWorkerBuffer()))
      .retries(job.getRetries())
      .deadline(job.getDeadline())
      .variables(job.getVariables())
      .tenantId(job.getTenantId())
      .jobKey(jobKey)                                // Long → String coercion at build()
      .processInstanceKey(job.getProcessInstanceKey())
      .processDefinitionKey(job.getProcessDefinitionKey())
      .elementInstanceKey(job.getElementInstanceKey())
      .kind(EnumUtil.convert(job.getJobKind(), GeneratedJobKindEnum.class))
      .listenerEventType(EnumUtil.convert(
          job.getJobListenerEventType(), GeneratedJobListenerEventTypeEnum.class))
      .tags(job.getTags())
      .userTask(toUserTaskProperties(job))           // nullable (OptionalStep)
      .rootProcessInstanceKey(                       // nullable (OptionalStep)
          rootProcessInstanceKey > 0 ? rootProcessInstanceKey : null)
      .build();                                      // triggers coercion + validation
}
```

#### How the generator decides required vs nullable

The generator reads the OpenAPI spec to classify fields:

- **Required** = listed in the `required:` array AND NOT `nullable: true`
- **Nullable** = NOT in `required:` array OR has `nullable: true` in the spec

Required fields get step interfaces (compile-time enforcement). Nullable fields are set on
`OptionalStep` and can safely be `null`.

### 6. Implement the service adapter

In `zeebe/gateway-rest/src/main/java/.../controller/adapter/`, implement the generated adapter
interface. The adapter wires together validation, mapping, service invocation, and response
construction:

```java
@Component
public class DefaultMyServiceAdapter implements MyServiceAdapter {

  private final MyServices myService;

  @Override
  public ResponseEntity<Object> doSomething(
      final Long key,
      final GeneratedMyRequestStrictContract request,
      final CamundaAuthentication authentication) {

    return RequestMapper.toMyRequest(request, key)
        .fold(
            RestErrorMapper::mapProblemToResponse,        // validation failure → 400
            mapped -> RequestExecutor.executeSync(
                () -> myService.doSomething(              // service call
                    mapped.field(), authentication),
                ResponseMapper::toMyResponse));           // domain → DTO
  }
}
```

For void commands (204 No Content), omit the `ResponseMapper` reference:

```java
return RequestMapper.toMyRequest(request, key)
    .fold(
        RestErrorMapper::mapProblemToResponse,
        mapped -> RequestExecutor.executeSync(
            () -> myService.doSomething(mapped.field(), authentication)));
```

### 7. Build and test

```bash
# Build both modules + dependencies
./mvnw install -pl gateways/gateway-mapping-http -am -Dquickly -T1C
./mvnw install -pl zeebe/gateway-rest -am -Dquickly -T1C

# Run unit tests in the mapping module
./mvnw verify -pl gateways/gateway-mapping-http -DskipTests=false -DskipITs -Dquickly -T1C

# Run MCP tool schema tests (validates tool schemas match the contract)
./mvnw verify -pl gateways/gateway-mcp -DskipTests=false -DskipITs -Dquickly -T1C

# Build for e2e testing
./mvnw clean install -pl dist -am -Dquickly -T1C
```

---

## Layer Reference

|            Layer            |          Module          |                              Key files                               |
|-----------------------------|--------------------------|----------------------------------------------------------------------|
| **OpenAPI spec**            | `zeebe/gateway-protocol` | `src/main/proto/v2/*.yaml`                                           |
| **Generator**               | `gateway-mapping-http`   | `tools/GenerateContractMappingPoc.java`                              |
| **Generated DTOs**          | `gateway-mapping-http`   | `src/main/java/.../contract/generated/Generated*StrictContract.java` |
| **RequestMapper**           | `gateway-mapping-http`   | `src/main/java/.../RequestMapper.java`                               |
| **ResponseMapper**          | `gateway-mapping-http`   | `src/main/java/.../ResponseMapper.java`                              |
| **Validators**              | `gateway-mapping-http`   | `src/main/java/.../validator/*Validator.java`                        |
| **Intermediate records**    | `gateway-mapping-http`   | `src/main/java/.../MappedCommandRequests.java`                       |
| **Generated controllers**   | `gateway-rest`           | `src/main/java/.../controller/generated/Generated*Controller.java`   |
| **Adapter interfaces**      | `gateway-rest`           | `src/main/java/.../controller/adapter/*ServiceAdapter.java`          |
| **Adapter implementations** | `gateway-rest`           | `src/main/java/.../controller/adapter/Default*ServiceAdapter.java`   |

---

## Concrete Example: Assign User Task

Tracing `POST /v2/user-tasks/{userTaskKey}/assignment` end to end:

1. **Spec:** `user-tasks.yaml` defines `operationId: assignUserTask` with schema
   `UserTaskAssignmentRequest`

2. **Generated DTO:** `GeneratedUserTaskAssignmentRequestStrictContract` — record with
   `assignee()`, `allowOverride()`, `action()`, `actionOrDefault()`

3. **Generated controller:** `GeneratedUserTaskController.assignUserTask()` — deserializes
   JSON, extracts path variable, delegates to `UserTaskServiceAdapter`

4. **Validator:** `UserTaskRequestValidator.validateAssignmentRequest()` — checks
   `assignee` is non-empty

5. **RequestMapper:** `toUserTaskAssignmentRequest()` — validates, then returns
   `Either.right(new AssignUserTaskRequest(key, assignee, action, allowOverride))`

6. **Adapter:** `DefaultUserTaskServiceAdapter.assignUserTask()` — folds the Either:
   error → 400, success → `userTaskServices.assignUserTask(key, assignee, action, allowOverride, auth)`

7. **Response:** 204 No Content (void command, no ResponseMapper needed)

