# Design: Add `kind` property to ClusterVariable

**Issue:** #57920  
**Date:** 2026-07-16  
**Status:** Approved

## Summary

Add a `kind` enum property to ClusterVariable with values `JSON` (default) and `SECRET_REFERENCE`. The kind determines how the variable value is interpreted at job activation — `SECRET_REFERENCE` values contain `camunda.secrets.X` references that get resolved only when a job is activated.

## Acceptance criteria

- ClusterVariables have a `kind` property (JSON | SECRET_REFERENCE)
- `kind` is stored in secondary storage (ES/OS and RDBMS)
- ClusterVariables can be filtered by `kind` via the search API
- `kind` can be set on create; defaults to `JSON` when not specified
- `kind` is **immutable** after creation (not present on the update endpoint)
- `kind` is supported in the CamundaClient (create command + search filter)

## Architecture

`kind` flows through the same stack as `scope`:

```
Protocol record (msgpack / engine state)
  → Exporter (ES/OS handler + RDBMS handler)
  → Secondary storage (ES/OS index + RDBMS table)
  → Search filter + response mapper
  → HTTP gateway REST API (OpenAPI spec + mappers)
  → CamundaClient (create command + search filter)
```

## Layer-by-layer design

### 1. Protocol layer (`zeebe/protocol` + `zeebe/protocol-impl`)

**New enum** `ClusterVariableKind` at `io.camunda.zeebe.protocol.record.value.ClusterVariableKind`:

```java
public enum ClusterVariableKind {
  JSON,
  SECRET_REFERENCE;
}
```

No `UNSPECIFIED` sentinel — the protocol record's default is `JSON`, so old records without a `kind` field deserialize as `JSON` automatically.

`ClusterVariableRecordValue` gains:

```java
ClusterVariableKind getKind();
```

`ClusterVariableRecord` (protocol-impl) gains a new `EnumProperty`:

```java
private final EnumProperty<ClusterVariableKind> kindProp =
    new EnumProperty<>(KIND_KEY, ClusterVariableKind.class, ClusterVariableKind.JSON);
```

The property is declared in the constructor (`super(6)` instead of 5). Getter and setter added.

**Backwards compatibility:** The msgpack `EnumProperty` default of `JSON` means any existing record without a `kind` field deserializes as `JSON`. No migration needed at the protocol level.

`BrokerCreateClusterVariableRequest` gains `setKind(ClusterVariableKind)`.

### 2. Engine state

No changes. `ClusterVariableInstance` wraps the full `ClusterVariableRecord` as a msgpack `ObjectProperty`. Adding a new property to `ClusterVariableRecord` is automatically persisted.

### 3. Exporters

**ES/OS exporter** (`ClusterVariableCreatedUpdatedHandler`): call `entity.setKind(ClusterVariableKind.fromProtocol(recordValue.getKind()))` in `updateEntity()`.

**RDBMS exporter** (`ClusterVariableExportHandler`): pass `kind` to `ClusterVariableDbModel.ClusterVariableDbModelBuilder`.

### 4. Secondary storage — ES/OS

**`ClusterVariableKind`** enum added to `webapps-schema` at `io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind`:

```java
public enum ClusterVariableKind {
  JSON, SECRET_REFERENCE;

  public static ClusterVariableKind fromProtocol(
      final io.camunda.zeebe.protocol.record.value.ClusterVariableKind kind) {
    return switch (kind) {
      case JSON -> JSON;
      case SECRET_REFERENCE -> SECRET_REFERENCE;
    };
  }
}
```

**`ClusterVariableEntity`** (webapps-schema) gains:

```java
@SinceVersion(value = "8.10.0", requireDefault = false)
private ClusterVariableKind kind;
```

With getter + setter.

**`ClusterVariableIndex`** gains `public static final String KIND = "kind"`.

**ES/OS index mappings** (`camunda-cluster-variable.json` for both ES and OS) gain:

```json
"kind": { "type": "keyword" }
```

### 5. Secondary storage — RDBMS

**`ClusterVariableDbModel`** gains `ClusterVariableKind kind` as a record component.

**`ClusterVariableMapper.xml`**:
- `resultMap`: add `<arg column="KIND" javaType="io.camunda.search.entities.ClusterVariableKind"/>`
- `select` (get and search): include `KIND` column
- `insert`: include `#{kind}` in INSERT statement
- `searchFilter`: add `AND KIND = #{filter.kindOperations...}` for kind filter operations
- `update` statement is NOT changed (kind is immutable)

**Liquibase changeset** (added to `8.10.0.xml`):

```xml
<changeSet id="add_kind_to_cluster_variable" author="camunda">
  <addColumn tableName="${prefix}CLUSTER_VARIABLE">
    <column name="KIND" type="VARCHAR(255)" defaultValue="JSON">
      <constraints nullable="false"/>
    </column>
  </addColumn>
</changeSet>
```

Also add an index on KIND for filter performance.

### 6. Search domain

**`ClusterVariableKind`** enum added at `io.camunda.search.entities.ClusterVariableKind`:

```java
public enum ClusterVariableKind { JSON, SECRET_REFERENCE; }
```

**`ClusterVariableEntity`** record gains `@Nullable ClusterVariableKind kind` component.

**`ClusterVariableFilter`** gains `List<Operation<String>> kindOperations` (parallel to `scopeOperations`). Builder gains `kinds()` and `kindOperations()` methods.

**`ClusterVariableEntityTransformer`**: maps `value.getKind()` with null-default fallback:

```java
final var kind = value.getKind() != null
    ? ClusterVariableKind.valueOf(value.getKind().name())
    : ClusterVariableKind.JSON;
```

**`ClusterVariableFilterTransformer`**: adds `getKindQuery(filter.kindOperations())` using `stringOperations(ClusterVariableIndex.KIND, operations)`.

**RDBMS filter** (`ClusterVariableMapper.xml` `searchFilter`): adds kind operations using the same `operationCondition` pattern as scope.

### 7. REST API — OpenAPI spec (`cluster-variables.yaml`)

New schema components:

```yaml
ClusterVariableKindEnum:
  type: string
  enum: [JSON, SECRET_REFERENCE]
  description: The kind of a cluster variable.

ClusterVariableKindFilterProperty:
  oneOf:
    - type: string
      allOf: [$ref: ClusterVariableKindEnum]
    - $ref: AdvancedClusterVariableKindFilter

AdvancedClusterVariableKindFilter:
  type: object
  properties:
    $eq: { allOf: [$ref: ClusterVariableKindEnum] }
    $neq: { allOf: [$ref: ClusterVariableKindEnum] }
    $exists: { type: boolean }
    $in: { type: array, items: { $ref: ClusterVariableKindEnum } }
```

`CreateClusterVariableRequest` gains optional `kind` (type: `$ref: ClusterVariableKindEnum`).

`ClusterVariableResultBase` gains required `kind` (type: `$ref: ClusterVariableKindEnum`).

`ClusterVariableSearchQueryFilterRequest` gains `kind` (type: `$ref: ClusterVariableKindFilterProperty`).

### 8. HTTP gateway mappers

`ClusterVariableMapper.java`: `toTenantClusterVariableCreateRequest` and `toGlobalClusterVariableCreateRequest` pass `request.getKind()` into `ClusterVariableRequest`.

`ResponseMapper.toClusterVariableResponse()`: maps `clusterVariableRecord.getKind()` → `ClusterVariableKindEnum`.

`SearchQueryResponseMapper.toClusterVariableSearchResult()` and `toClusterVariableResult()`: map `clusterVariableEntity.kind()` → `ClusterVariableKindEnum`.

`SearchQueryFilterMapper.toClusterVariableFilter()`: maps `filter.getKind()` → `kindOperations`.

`ClusterVariableServices.ClusterVariableRequest` gains `ClusterVariableKind kind` field.

### 9. CamundaClient (`clients/java`)

**New enum** `io.camunda.client.api.search.enums.ClusterVariableKind`: `JSON`, `SECRET_REFERENCE`.

**New filter builder types**:
- `ClusterVariableKindProperty` interface (analogous to `ClusterVariableScopeProperty`)
- `ClusterVariableKindPropertyImpl` implementation

**`ClusterVariableFilter`** API interface gains:

```java
ClusterVariableFilter kind(ClusterVariableKind kind);
ClusterVariableFilter kind(Consumer<ClusterVariableKindProperty> fn);
```

**`ClusterVariableFilterImpl`** implements both.

**`GloballyScopedClusterVariableCreationCommandStep1`** gains:

```java
GloballyScopedClusterVariableCreationCommandStep1 kind(ClusterVariableKind kind);
```

**`TenantScopedClusterVariableCreationCommandStep1`** gains the same.

Both create impls pass `kind` to the REST request body.

**`CreateClusterVariableResponse`** gains `ClusterVariableKind getKind()`.

**`CreateClusterVariableResponseImpl`** maps kind from `ClusterVariableResult.getKind()`.

## Backwards compatibility

|      Layer       |             Old state              |                                    Migration                                    |
|------------------|------------------------------------|---------------------------------------------------------------------------------|
| Engine (msgpack) | Old records have no `kind` field   | `EnumProperty` default `JSON` — reads as `JSON` automatically                   |
| ES/OS documents  | Old documents have no `kind` field | `@JsonIgnoreProperties(ignoreUnknown=true)` + null→JSON fallback in transformer |
| RDBMS rows       | Old rows have no `KIND` column     | Liquibase `addColumn` with `defaultValue="JSON"`                                |

## Files changed (summary)

~33 files across 11 modules:

|                  Module                  |                                                                                                                                                                                                                                          Files                                                                                                                                                                                                                                           |
|------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `zeebe/protocol`                         | `ClusterVariableKind.java` (new), `ClusterVariableRecordValue.java`                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `zeebe/protocol-impl`                    | `ClusterVariableRecord.java`                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `zeebe/gateway` (broker requests)        | `BrokerCreateClusterVariableRequest.java`                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `zeebe/exporters/camunda-exporter`       | `ClusterVariableCreatedUpdatedHandler.java`                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `zeebe/exporters/rdbms-exporter`         | `ClusterVariableExportHandler.java`                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `webapps-schema`                         | `ClusterVariableKind.java` (new), `ClusterVariableEntity.java`, `ClusterVariableIndex.java`, 2× JSON mappings                                                                                                                                                                                                                                                                                                                                                                            |
| `search/search-domain`                   | `ClusterVariableKind.java` (new), `ClusterVariableEntity.java`, `ClusterVariableFilter.java`                                                                                                                                                                                                                                                                                                                                                                                             |
| `search/search-client-query-transformer` | `ClusterVariableEntityTransformer.java`, `ClusterVariableFilterTransformer.java`                                                                                                                                                                                                                                                                                                                                                                                                         |
| `db/rdbms`                               | `ClusterVariableDbModel.java`, `ClusterVariableMapper.xml`                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `db/rdbms-schema`                        | `8.10.0.xml` (add changeset)                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `zeebe/gateway-protocol` (OpenAPI)       | `cluster-variables.yaml`                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `gateways/gateway-mapping-http`          | `ClusterVariableMapper.java`, `ResponseMapper.java`, `SearchQueryResponseMapper.java`, `SearchQueryFilterMapper.java`                                                                                                                                                                                                                                                                                                                                                                    |
| `service`                                | `ClusterVariableServices.java`                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `clients/java`                           | `ClusterVariableKind.java` (new), `ClusterVariableKindProperty.java` (new), `ClusterVariableKindPropertyImpl.java` (new), `ClusterVariableFilter.java`, `ClusterVariableFilterImpl.java`, `GloballyScopedClusterVariableCreationCommandStep1.java`, `GloballyScopedCreateClusterVariableImpl.java`, `TenantScopedClusterVariableCreationCommandStep1.java`, `TenantScopedCreateClusterVariableImpl.java`, `CreateClusterVariableResponse.java`, `CreateClusterVariableResponseImpl.java` |

