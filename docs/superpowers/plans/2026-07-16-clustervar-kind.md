# ClusterVariable `kind` Property Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `kind` enum property (`JSON` | `SECRET_REFERENCE`, default `JSON`) to ClusterVariable across the full stack: engine protocol, exporters, secondary storage (ES/OS + RDBMS), search filters, REST API, and CamundaClient.

**Architecture:** `kind` is set at creation time and immutable thereafter, flowing through the same pipeline as `scope`: engine record → exporter → secondary storage → search domain → REST gateway → Java client. Old records without `kind` deserialize as `JSON` at every layer via defaults.

**Tech Stack:** Java 21, Immutables, MyBatis, Liquibase, OpenAPI/Swagger codegen, JUnit 5, AssertJ, Mockito, WireMock.

## Global Constraints

- All Java files need the Camunda License header (copy from an adjacent file in the same package).
- Apache License header for files under `zeebe/protocol` and `clients/java`.
- Run `./mvnw license:format spotless:apply -T1C` before every commit that touches Java or XML.
- Use `@Nullable` (jspecify) on nullable fields/parameters; add `@NullMarked` to classes where the annotation is new.
- Test method names start with `should`.
- Tests structured with `// given`, `// when`, `// then` comments.
- Use AssertJ (`assertThat`), not JUnit assertions.
- `kind` is **immutable after creation** — never add it to update paths.
- Never touch golden Liquibase files (`db/rdbms-schema/src/test/resources/db/changelog/rdbms-exporter/changesets/golden/`).

---

### Task 1: Protocol — `ClusterVariableKind` enum + record field

**Files:**
- Create: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/ClusterVariableKind.java`
- Modify: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/ClusterVariableRecordValue.java`
- Modify: `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/clustervariable/ClusterVariableRecord.java`
- Modify: `zeebe/gateway/src/main/java/io/camunda/zeebe/gateway/impl/broker/request/BrokerCreateClusterVariableRequest.java`
- Test: `zeebe/protocol-impl/src/test/java/io/camunda/zeebe/protocol/impl/record/value/clustervariable/ClusterVariableRecordTest.java`

**Interfaces:**
- Produces: `ClusterVariableKind` enum at `io.camunda.zeebe.protocol.record.value.ClusterVariableKind`; `ClusterVariableRecordValue.getKind()` returning `ClusterVariableKind`; `ClusterVariableRecord.getKind()` / `setKind(ClusterVariableKind)`; `BrokerCreateClusterVariableRequest.setKind(ClusterVariableKind)`

- [ ] **Step 1: Create `ClusterVariableKind` enum**

```java
// zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/ClusterVariableKind.java
/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.value;

public enum ClusterVariableKind {
  JSON,
  SECRET_REFERENCE;
}
```

- [ ] **Step 2: Add `getKind()` to `ClusterVariableRecordValue`**

Add after `getMetadata()`:

```java
/**
 * Returns the kind of this cluster variable.
 *
 * <p>Determines how the value is interpreted at job activation.
 * {@code SECRET_REFERENCE} values contain {@code camunda.secrets.X} references
 * resolved only at activation time. Defaults to {@code JSON}.
 *
 * @return the variable kind (never {@code null})
 */
ClusterVariableKind getKind();
```

- [ ] **Step 3: Write the failing test for `ClusterVariableRecord.getKind()`**

Add to `ClusterVariableRecordTest`:

```java
@Test
void shouldRoundTripKindViaMsgPack() {
  // given
  final var original =
      new ClusterVariableRecord()
          .setName("myVar")
          .setScope(ClusterVariableScope.GLOBAL)
          .setTenantId("<default>")
          .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"value\"")))
          .setKind(ClusterVariableKind.SECRET_REFERENCE);

  // when
  final var copy = new ClusterVariableRecord();
  copy.copyFrom(original);

  // then
  assertThat(copy.getKind()).isEqualTo(ClusterVariableKind.SECRET_REFERENCE);
}

@Test
void shouldDefaultKindToJsonWhenNotSet() {
  // given / when
  final var record = new ClusterVariableRecord();

  // then
  assertThat(record.getKind()).isEqualTo(ClusterVariableKind.JSON);
}
```

- [ ] **Step 4: Run test to verify it fails**

```bash
./mvnw verify -pl zeebe/protocol-impl -Dtest=ClusterVariableRecordTest -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: compilation failure — `getKind()` / `setKind()` not yet defined.

- [ ] **Step 5: Implement `kind` in `ClusterVariableRecord`**

Add a `KIND_KEY` constant and `kindProp` field. Change the constructor from `super(5)` to `super(6)` and declare the new property.

```java
// New constants (add with existing StringValue constants):
private static final StringValue KIND_KEY = new StringValue("kind");

// New field (add after metadataProp):
private final EnumProperty<ClusterVariableKind> kindProp =
    new EnumProperty<>(KIND_KEY, ClusterVariableKind.class, ClusterVariableKind.JSON);

// Updated constructor:
public ClusterVariableRecord() {
  super(6);
  declareProperty(nameProp)
      .declareProperty(valueProp)
      .declareProperty(scopeProp)
      .declareProperty(tenantIdProp)
      .declareProperty(metadataProp)
      .declareProperty(kindProp);
}

// New getter:
@Override
public ClusterVariableKind getKind() {
  return kindProp.getValue();
}

// New setter:
public ClusterVariableRecord setKind(final ClusterVariableKind kind) {
  kindProp.setValue(kind);
  return this;
}
```

Required import: `io.camunda.zeebe.msgpack.property.EnumProperty`

- [ ] **Step 6: Run test to verify it passes**

```bash
./mvnw verify -pl zeebe/protocol-impl -Dtest=ClusterVariableRecordTest -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 7: Add `setKind()` to `BrokerCreateClusterVariableRequest`**

Add after `setValue()`:

```java
public BrokerCreateClusterVariableRequest setKind(
    final io.camunda.zeebe.protocol.record.value.ClusterVariableKind kind) {
  if (kind != null) {
    requestDto.setKind(kind);
  }
  return this;
}
```

- [ ] **Step 8: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/ClusterVariableKind.java \
        zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/ClusterVariableRecordValue.java \
        zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/clustervariable/ClusterVariableRecord.java \
        zeebe/protocol-impl/src/test/java/io/camunda/zeebe/protocol/impl/record/value/clustervariable/ClusterVariableRecordTest.java \
        zeebe/gateway/src/main/java/io/camunda/zeebe/gateway/impl/broker/request/BrokerCreateClusterVariableRequest.java
git commit -m "feat: add ClusterVariableKind to protocol record"
```

---

### Task 2: Webapps schema — entity + index constant + ES/OS index mappings

**Files:**
- Create: `webapps-schema/src/main/java/io/camunda/webapps/schema/entities/clustervariable/ClusterVariableKind.java`
- Modify: `webapps-schema/src/main/java/io/camunda/webapps/schema/entities/clustervariable/ClusterVariableEntity.java`
- Modify: `webapps-schema/src/main/java/io/camunda/webapps/schema/descriptors/index/ClusterVariableIndex.java`
- Modify: `webapps-schema/src/main/resources/schema/elasticsearch/create/index/camunda-cluster-variable.json`
- Modify: `webapps-schema/src/main/resources/schema/opensearch/create/index/camunda-cluster-variable.json`

**Interfaces:**
- Consumes: `ClusterVariableKind` from Task 1 (protocol layer — for `fromProtocol()` helper)
- Produces: `ClusterVariableKind` at `io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind` with `fromProtocol()`; `ClusterVariableEntity.getKind()` / `setKind()`; `ClusterVariableIndex.KIND = "kind"`

- [ ] **Step 1: Create `ClusterVariableKind` in webapps-schema**

```java
// webapps-schema/src/main/java/io/camunda/webapps/schema/entities/clustervariable/ClusterVariableKind.java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.clustervariable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ClusterVariableKind {
  JSON,
  SECRET_REFERENCE;

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterVariableKind.class);

  public static ClusterVariableKind fromProtocol(
      final io.camunda.zeebe.protocol.record.value.ClusterVariableKind kind) {
    if (kind == null) {
      return JSON;
    }
    return switch (kind) {
      case JSON -> JSON;
      case SECRET_REFERENCE -> SECRET_REFERENCE;
    };
  }
}
```

- [ ] **Step 2: Add `kind` field to `ClusterVariableEntity`**

Add after the `metadata` field:

```java
@SinceVersion(value = "8.10.0", requireDefault = false)
private ClusterVariableKind kind;
```

Add getter and setter (following the existing style — setter returns `this`):

```java
public ClusterVariableKind getKind() {
  return kind;
}

public ClusterVariableEntity setKind(final ClusterVariableKind kind) {
  this.kind = kind;
  return this;
}
```

Also update `hashCode()`, `equals()`, and `toString()` to include `kind`. In `hashCode`:

```java
return Objects.hash(id, name, value, fullValue, isPreview, scope, tenantId, metadata, kind);
```

In `equals` — add `&& kind == that.kind` in the return expression. In `toString` — append `", kind=" + kind`.

- [ ] **Step 3: Add `KIND` constant to `ClusterVariableIndex`**

Add after the existing constants:

```java
public static final String KIND = "kind";
```

- [ ] **Step 4: Add `kind` to ES index mapping**

In `webapps-schema/src/main/resources/schema/elasticsearch/create/index/camunda-cluster-variable.json`, add after the `isPreview` entry:

```json
"kind": {
  "type": "keyword"
},
```

- [ ] **Step 5: Add `kind` to OS index mapping**

Same change in `webapps-schema/src/main/resources/schema/opensearch/create/index/camunda-cluster-variable.json`.

- [ ] **Step 6: Build webapps-schema to verify**

```bash
./mvnw verify -pl webapps-schema -Dquickly -DskipTests=false -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add webapps-schema/
git commit -m "feat: add ClusterVariableKind to webapps schema entity and ES/OS index mapping"
```

---

### Task 3: ES/OS exporter handler — set `kind` on entity

**Files:**
- Modify: `zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/handlers/ClusterVariableCreatedUpdatedHandler.java`
- Test: `zeebe/exporters/camunda-exporter/src/test/java/io/camunda/exporter/handlers/ClusterVariableCreatedUpdatedHandlerTest.java`

**Interfaces:**
- Consumes: `ClusterVariableKind.fromProtocol()` from Task 2; `ClusterVariableRecordValue.getKind()` from Task 1

- [ ] **Step 1: Write the failing test**

Add to `ClusterVariableCreatedUpdatedHandlerTest`:

```java
@ParameterizedTest
@EnumSource(
    value = ClusterVariableIntent.class,
    names = {"CREATED", "UPDATED"},
    mode = Mode.INCLUDE)
void shouldSetKindOnEntity(final ClusterVariableIntent intent) {
  // given
  final ClusterVariableRecordValue recordValue =
      ImmutableClusterVariableRecordValue.builder()
          .from(factory.generateObject(ClusterVariableRecordValue.class))
          .withScope(ClusterVariableScope.GLOBAL)
          .withKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE)
          .build();

  final Record<ClusterVariableRecordValue> record =
      factory.generateRecord(
          ValueType.CLUSTER_VARIABLE,
          r -> r.withIntent(intent).withValue(recordValue));

  // when
  final ClusterVariableEntity entity = new ClusterVariableEntity();
  underTest.updateEntity(record, entity);

  // then
  assertThat(entity.getKind())
      .isEqualTo(
          io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind.SECRET_REFERENCE);
}

@ParameterizedTest
@EnumSource(
    value = ClusterVariableIntent.class,
    names = {"CREATED", "UPDATED"},
    mode = Mode.INCLUDE)
void shouldDefaultKindToJsonWhenNotInRecord(final ClusterVariableIntent intent) {
  // given
  final ClusterVariableRecordValue recordValue =
      ImmutableClusterVariableRecordValue.builder()
          .from(factory.generateObject(ClusterVariableRecordValue.class))
          .withScope(ClusterVariableScope.GLOBAL)
          .withKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.JSON)
          .build();

  final Record<ClusterVariableRecordValue> record =
      factory.generateRecord(
          ValueType.CLUSTER_VARIABLE,
          r -> r.withIntent(intent).withValue(recordValue));

  // when
  final ClusterVariableEntity entity = new ClusterVariableEntity();
  underTest.updateEntity(record, entity);

  // then
  assertThat(entity.getKind())
      .isEqualTo(
          io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind.JSON);
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw verify -pl zeebe/exporters/camunda-exporter -Dtest=ClusterVariableCreatedUpdatedHandlerTest -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: FAIL — `entity.getKind()` is null.

- [ ] **Step 3: Set kind in `updateEntity()`**

In `ClusterVariableCreatedUpdatedHandler.updateEntity()`, add after `.setName(recordValue.getName())`:

```java
entity.setKind(ClusterVariableKind.fromProtocol(recordValue.getKind()));
```

Add import: `import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind;`

- [ ] **Step 4: Run test to verify passing**

```bash
./mvnw verify -pl zeebe/exporters/camunda-exporter -Dtest=ClusterVariableCreatedUpdatedHandlerTest -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add zeebe/exporters/camunda-exporter/
git commit -m "feat: export ClusterVariable kind in ES/OS exporter handler"
```

---

### Task 4: Search domain — `ClusterVariableKind` enum, entity record field, filter operations, transformers

**Files:**
- Create: `search/search-domain/src/main/java/io/camunda/search/entities/ClusterVariableKind.java`
- Modify: `search/search-domain/src/main/java/io/camunda/search/entities/ClusterVariableEntity.java`
- Modify: `search/search-domain/src/main/java/io/camunda/search/filter/ClusterVariableFilter.java`
- Modify: `search/search-client-query-transformer/src/main/java/io/camunda/search/clients/transformers/entity/ClusterVariableEntityTransformer.java`
- Modify: `search/search-client-query-transformer/src/main/java/io/camunda/search/clients/transformers/filter/ClusterVariableFilterTransformer.java`
- Test: `search/search-client-query-transformer/src/test/java/io/camunda/search/clients/transformers/filter/ClusterVariableFilterMetadataTransformerTest.java`

**Interfaces:**
- Consumes: `ClusterVariableKind` (webapps-schema) from Task 2; `ClusterVariableIndex.KIND` from Task 2
- Produces: `ClusterVariableKind` at `io.camunda.search.entities.ClusterVariableKind`; `ClusterVariableEntity` record gains `@Nullable ClusterVariableKind kind`; `ClusterVariableFilter.Builder.kinds()` / `kindOperations()` methods

- [ ] **Step 1: Create `ClusterVariableKind` in search-domain**

```java
// search/search-domain/src/main/java/io/camunda/search/entities/ClusterVariableKind.java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

public enum ClusterVariableKind {
  JSON,
  SECRET_REFERENCE;
}
```

- [ ] **Step 2: Add `kind` to `ClusterVariableEntity` record**

`ClusterVariableEntity` is a Java record. Add `@Nullable ClusterVariableKind kind` as a new component after `metadata`:

```java
public record ClusterVariableEntity(
    String id,
    String name,
    String value,
    @Nullable String fullValue,
    @Nullable Boolean isPreview,
    ClusterVariableScope scope,
    @Nullable String tenantId,
    @Nullable List<MetadataEntry> metadata,
    @Nullable ClusterVariableKind kind)
    implements TenantOwnedEntity {

  public ClusterVariableEntity {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(scope, "scope");
    metadata = metadata != null ? new ArrayList<>(metadata) : new ArrayList<>();
  }
  // ... rest unchanged
}
```

Add import: `import io.camunda.search.entities.ClusterVariableKind;`

- [ ] **Step 3: Add `kindOperations` to `ClusterVariableFilter`**

The filter is also a Java record. Add `List<Operation<String>> kindOperations` as a new component after `metadataOperations`, before `isTruncated`.

In the `Builder` class add:

```java
List<Operation<String>> kindOperations;

public Builder kindOperations(final List<Operation<String>> operations) {
  kindOperations = addValuesToList(kindOperations, operations);
  return this;
}

public Builder kinds(final String value, final String... values) {
  return kindOperations(FilterUtil.mapDefaultToOperation(value, values));
}

public Builder kinds(final List<String> values) {
  return kindOperations(FilterUtil.mapDefaultToOperation(values));
}

@SafeVarargs
public final Builder kindOperations(
    final Operation<String> operation, final Operation<String>... operations) {
  return kindOperations(collectValues(operation, operations));
}
```

Update `build()` to pass `kindOperations` to the record constructor, defaulting to empty list:

```java
Objects.requireNonNullElseGet(kindOperations, Collections::emptyList),
```

- [ ] **Step 4: Write a failing test for the kind filter transformer**

Add to `ClusterVariableFilterMetadataTransformerTest`:

```java
@Test
void shouldTransformKindFilter() {
  // given
  final var filter =
      FilterBuilders.clusterVariable()
          .kinds("JSON")
          .build();

  // when
  final SearchQuery query = new ClusterVariableFilterTransformer(
      new ClusterVariableIndex("", true)).toSearchQuery(filter);

  // then
  assertThat(query)
      .isInstanceOf(SearchBoolQuery.class);
  final var bool = (SearchBoolQuery) query;
  assertThat(bool.must()).hasSize(1);
  final var kindQuery = bool.must().get(0);
  assertThat(kindQuery).isInstanceOf(SearchTermsQuery.class);
  final var terms = (SearchTermsQuery) kindQuery;
  assertThat(terms.field()).isEqualTo(ClusterVariableIndex.KIND);
  assertThat(terms.values()).containsExactly("JSON");
}
```

- [ ] **Step 5: Run test to confirm failure**

```bash
./mvnw verify -pl search/search-client-query-transformer -Dtest=ClusterVariableFilterMetadataTransformerTest -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: FAIL — compilation error or null result.

- [ ] **Step 6: Update `ClusterVariableEntityTransformer`**

Replace the `apply` method body to map `kind`:

```java
@Override
public ClusterVariableEntity apply(
    final io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity value) {
  final var webappsKind = value.getKind();
  final ClusterVariableKind kind =
      webappsKind != null ? ClusterVariableKind.valueOf(webappsKind.name()) : ClusterVariableKind.JSON;
  return new ClusterVariableEntity(
      value.getId(),
      value.getName(),
      value.getValue(),
      value.getFullValue(),
      value.getIsPreview(),
      ClusterVariableScope.valueOf(value.getScope().name()),
      value.getTenantId(),
      toMetadata(value.getMetadata()),
      kind);
}
```

Add import: `import io.camunda.search.entities.ClusterVariableKind;`

- [ ] **Step 7: Update `ClusterVariableFilterTransformer.toSearchQuery()`**

Add kind query generation. In `toSearchQuery()`:

```java
queries.addAll(getKindQuery(filter.kindOperations()));
```

Add the private method:

```java
private Collection<SearchQuery> getKindQuery(final List<Operation<String>> operations) {
  return stringOperations(ClusterVariableIndex.KIND, operations);
}
```

- [ ] **Step 8: Run tests**

```bash
./mvnw verify -pl search/search-client-query-transformer -Dtest=ClusterVariableFilterMetadataTransformerTest -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add search/
git commit -m "feat: add kind to search domain ClusterVariable entity and filter"
```

---

### Task 5: RDBMS — db model, mapper XML, Liquibase migration, RDBMS exporter

**Files:**
- Modify: `db/rdbms/src/main/java/io/camunda/db/rdbms/write/domain/ClusterVariableDbModel.java`
- Modify: `db/rdbms/src/main/resources/mapper/ClusterVariableMapper.xml`
- Modify: `db/rdbms-schema/src/main/resources/db/changelog/rdbms-exporter/changesets/8.10.0.xml`
- Modify: `zeebe/exporters/rdbms-exporter/src/main/java/io/camunda/exporter/rdbms/handlers/ClusterVariableExportHandler.java`
- Test: `qa/acceptance-tests/src/test/java/io/camunda/it/rdbms/db/clustervariables/ClusterVariableIT.java`

**Interfaces:**
- Consumes: `ClusterVariableKind` from Task 4 (search-domain); `ClusterVariableRecordValue.getKind()` from Task 1
- Produces: `ClusterVariableDbModel.kind()` component; KIND column in RDBMS; RDBMS exporter maps kind

- [ ] **Step 1: Add `kind` to `ClusterVariableDbModel`**

The record currently has components: `id, name, type, doubleValue, longValue, value, fullValue, isPreview, tenantId, scope`. Add `ClusterVariableKind kind` at the end:

```java
public record ClusterVariableDbModel(
    String id,
    String name,
    ValueTypeEnum type,
    Double doubleValue,
    Long longValue,
    String value,
    String fullValue,
    boolean isPreview,
    String tenantId,
    ClusterVariableScope scope,
    ClusterVariableKind kind)
    implements Copyable<ClusterVariableDbModel> {
```

Update `copy()` to pass `kind` through:

```java
@Override
public ClusterVariableDbModel copy(
    final Function<ObjectBuilder<ClusterVariableDbModel>, ObjectBuilder<ClusterVariableDbModel>>
        builderFunction) {
  return builderFunction
      .apply(
          new ClusterVariableDbModelBuilder()
              .name(name)
              .value(value)
              .tenantId(tenantId)
              .scope(scope)
              .kind(kind))
      .build();
}
```

Update `truncateValue()` to pass `kind` in the `new ClusterVariableDbModel(...)` call (add `kind` as last argument after `scope`).

Add `ClusterVariableKind kind` field to `ClusterVariableDbModelBuilder`, with builder method:

```java
private ClusterVariableKind kind = ClusterVariableKind.JSON;

public ClusterVariableDbModelBuilder kind(final ClusterVariableKind kind) {
  this.kind = Objects.requireNonNullElse(kind, ClusterVariableKind.JSON);
  return this;
}
```

Update all `new ClusterVariableDbModel(...)` calls inside the builder's `getLongModel()`, `getModel()`, and `getDoubleModel()` to pass `kind` as the final argument.

- [ ] **Step 2: Update `ClusterVariableMapper.xml`**

**resultMap** — add after the `METADATA` arg:

```xml
<arg column="KIND" javaType="io.camunda.search.entities.ClusterVariableKind"/>
```

Also update the `ClusterVariableEntity` record constructor call to match the new component count (the resultMap args must match the record canonical constructor order: id, name, value, fullValue, isPreview, scope, tenantId, metadata, kind).

**`get` select** — add `KIND` to the column list:

```sql
CLUSTER_VARIABLE_ID,
VAR_NAME,
VAR_VALUE,
VAR_FULL_VALUE,
TENANT_ID,
SCOPE,
IS_PREVIEW,
NULL AS METADATA,
KIND
```

**`search` inner select** — add `KIND` to the column list same way.

**`insert` statement** — add `KIND` to the column list and `#{kind}` to the values:

```xml
INSERT INTO ${prefix}CLUSTER_VARIABLE (CLUSTER_VARIABLE_ID, VAR_NAME, TYPE, DOUBLE_VALUE,
                                       LONG_VALUE, VAR_VALUE, VAR_FULL_VALUE, IS_PREVIEW,
                                       TENANT_ID, SCOPE, KIND)
VALUES (#{id}, #{name}, #{type}, #{doubleValue, jdbcType=DOUBLE},
        #{longValue, jdbcType=NUMERIC},
        #{value},
        #{fullValue}, #{isPreview}, #{tenantId}, #{scope}, #{kind})
```

**`update` statement** — do NOT add KIND (kind is immutable).

**`searchFilter` SQL** — add kind filter support after the `scopeOperations` block:

```xml
<if test="filter.kindOperations != null and !filter.kindOperations.isEmpty()">
  <foreach collection="filter.kindOperations" item="operation">
    AND KIND
    <include refid="io.camunda.db.rdbms.sql.Commons.operationCondition"/>
  </foreach>
</if>
```

- [ ] **Step 3: Add Liquibase changeset to `8.10.0.xml`**

Add at the end of the file, before `</databaseChangeLog>`:

```xml
<changeSet id="add_kind_to_cluster_variable" author="camunda">
  <preConditions onFail="MARK_RAN">
    <not>
      <columnExists tableName="${prefix}CLUSTER_VARIABLE" columnName="KIND"/>
    </not>
  </preConditions>
  <addColumn tableName="${prefix}CLUSTER_VARIABLE">
    <column name="KIND" type="VARCHAR(255)" defaultValue="JSON">
      <constraints nullable="false"/>
    </column>
  </addColumn>
</changeSet>

<changeSet id="create_cluster_variable_kind_index" author="camunda">
  <preConditions onFail="MARK_RAN">
    <not>
      <indexExists indexName="${prefix}IDX_CLUSTER_VARIABLE_KIND"
        tableName="${prefix}CLUSTER_VARIABLE"/>
    </not>
  </preConditions>
  <createIndex tableName="${prefix}CLUSTER_VARIABLE"
    indexName="${prefix}IDX_CLUSTER_VARIABLE_KIND">
    <column name="KIND"/>
  </createIndex>
</changeSet>
```

- [ ] **Step 4: Update RDBMS exporter handler to map `kind`**

In `ClusterVariableExportHandler.map()`, add `.kind(...)` to the builder:

```java
private ClusterVariableDbModel map(final Record<ClusterVariableRecordValue> record) {
  final var value = record.getValue();
  final var builder =
      new ClusterVariableDbModel.ClusterVariableDbModelBuilder()
          .name(value.getName())
          .value(value.getValue())
          .kind(ClusterVariableKind.valueOf(value.getKind().name()));
  if (value.getScope() == GLOBAL) {
    builder.scope(ClusterVariableScope.GLOBAL).tenantId(null);
  } else {
    builder.scope(ClusterVariableScope.TENANT).tenantId(value.getTenantId());
  }
  return builder.build();
}
```

Add import: `import io.camunda.search.entities.ClusterVariableKind;`

- [ ] **Step 5: Write a failing test for kind persistence**

Add to `ClusterVariableIT`:

```java
@TestTemplate
public void shouldSaveAndFindClusterVariableWithKind(
    final CamundaRdbmsTestApplication testApplication) {
  // given
  final var dbModel =
      new ClusterVariableDbModel.ClusterVariableDbModelBuilder()
          .name(generateRandomString())
          .value("\"secret\"")
          .scope(ClusterVariableScope.GLOBAL)
          .tenantId(null)
          .kind(io.camunda.search.entities.ClusterVariableKind.SECRET_REFERENCE)
          .build();
  createAndSaveVariables(testApplication.getRdbmsService(), dbModel);

  // when
  final var found =
      testApplication
          .getRdbmsService()
          .getClusterVariableReader()
          .getGloballyScopedClusterVariable(
              dbModel.name(),
              CommonFixtures.resourceAccessChecksFromResourceIds(
                  AuthorizationResourceType.CLUSTER_VARIABLE, dbModel.name()));

  // then
  assertThat(found).isNotNull();
  assertThat(found.kind())
      .isEqualTo(io.camunda.search.entities.ClusterVariableKind.SECRET_REFERENCE);
}
```

- [ ] **Step 6: Run RDBMS test**

```bash
./mvnw verify -pl qa/acceptance-tests -Dit.test=ClusterVariableIT -DskipTests=false -DskipUTs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: test passes.

- [ ] **Step 7: Also run RDBMS schema tests**

```bash
./mvnw verify -pl db/rdbms-schema -DskipTests=false -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add db/rdbms/ \
        db/rdbms-schema/ \
        zeebe/exporters/rdbms-exporter/ \
        qa/acceptance-tests/src/test/java/io/camunda/it/rdbms/db/clustervariables/ClusterVariableIT.java
git commit -m "feat: add kind to RDBMS cluster variable storage and exporter"
```

---

### Task 6: OpenAPI spec + service layer + HTTP gateway mappers

**Files:**
- Modify: `zeebe/gateway-protocol/src/main/proto/v2/cluster-variables.yaml`
- Modify: `service/src/main/java/io/camunda/service/ClusterVariableServices.java`
- Modify: `gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/mapper/ClusterVariableMapper.java`
- Modify: `gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/ResponseMapper.java`
- Modify: `gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/search/SearchQueryResponseMapper.java`
- Modify: `gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/search/SearchQueryFilterMapper.java`
- Test: `zeebe/gateway-rest/src/test/java/io/camunda/zeebe/gateway/rest/controller/ClusterVariableControllerTest.java`

**Interfaces:**
- Consumes: `ClusterVariableKind` (protocol) from Task 1; `ClusterVariableKind` (search-domain) from Task 4
- Produces: `CreateClusterVariableRequest.getKind()` in generated model; `ClusterVariableResultBase.getKind()` in generated model; `ClusterVariableSearchQueryFilterRequest.getKind()` in generated model; `ClusterVariableServices.ClusterVariableRequest.kind`

- [ ] **Step 1: Update `cluster-variables.yaml`**

Add `ClusterVariableKindEnum` schema to `components.schemas` (before `ClusterVariableScopeEnum`):

```yaml
ClusterVariableKindEnum:
  type: string
  enum: [JSON, SECRET_REFERENCE]
  description: The kind of a cluster variable. JSON is the default. SECRET_REFERENCE allows the value to contain camunda.secrets.X references that are resolved at job activation time.
```

Add `ClusterVariableKindFilterProperty` and `AdvancedClusterVariableKindFilter` (following the scope filter pattern, after `AdvancedClusterVariableScopeFilter`):

```yaml
ClusterVariableKindFilterProperty:
  description: ClusterVariableKindEnum property with full advanced search capabilities.
  oneOf:
    - type: string
      title: Exact match
      description: Matches the value exactly.
      allOf:
        - $ref: '#/components/schemas/ClusterVariableKindEnum'
    - $ref: '#/components/schemas/AdvancedClusterVariableKindFilter'
AdvancedClusterVariableKindFilter:
  title: Advanced filter
  description: Advanced ClusterVariableKindEnum filter.
  type: object
  properties:
    $eq:
      description: Checks for equality with the provided value.
      allOf:
        - $ref: '#/components/schemas/ClusterVariableKindEnum'
    $neq:
      description: Checks for inequality with the provided value.
      allOf:
        - $ref: '#/components/schemas/ClusterVariableKindEnum'
    $exists:
      description: Checks if the current property exists.
      type: boolean
    $in:
      description: Checks if the property matches any of the provided values.
      type: array
      items:
        $ref: '#/components/schemas/ClusterVariableKindEnum'
    $like:
      $ref: "filters.yaml#/components/schemas/LikeFilter"
```

Add `kind` as an **optional** property to `CreateClusterVariableRequest`:

```yaml
CreateClusterVariableRequest:
  type: object
  required:
    - name
    - value
  properties:
    name:
      # ... existing
    value:
      # ... existing
    kind:
      description: The kind of the cluster variable. Defaults to JSON if not specified.
      allOf:
        - $ref: '#/components/schemas/ClusterVariableKindEnum'
```

Add `kind` as a **required** property to `ClusterVariableResultBase`:

```yaml
ClusterVariableResultBase:
  type: object
  required:
    - name
    - scope
    - tenantId
    - kind
  properties:
    name:
      # ... existing
    scope:
      # ... existing
    tenantId:
      # ... existing
    kind:
      $ref: '#/components/schemas/ClusterVariableKindEnum'
```

Add `kind` filter to `ClusterVariableSearchQueryFilterRequest`:

```yaml
ClusterVariableSearchQueryFilterRequest:
  # ...
  properties:
    # ... existing properties
    kind:
      description: The kind filter for cluster variables.
      allOf:
        - $ref: '#/components/schemas/ClusterVariableKindFilterProperty'
```

- [ ] **Step 2: Regenerate server model and client model**

```bash
./mvnw install -pl gateways/gateway-model,clients/java -am -Dquickly -T2 2>&1 | grep -E "BUILD|ERROR|FAIL"
```

Expected: `BUILD SUCCESS`. This generates `ClusterVariableKindEnum`, `ClusterVariableKindFilterProperty`, `AdvancedClusterVariableKindFilter` in both `gateways/gateway-model` and `clients/java`.

- [ ] **Step 3: Update `ClusterVariableServices.ClusterVariableRequest`**

Add `kind` to the record:

```java
public record ClusterVariableRequest(String name, Object value, String tenantId,
    io.camunda.zeebe.protocol.record.value.ClusterVariableKind kind) {}
```

Update all four methods that construct `ClusterVariableRequest` to pass `kind` — for create methods use `request.getKind()` (mapped from the gateway model), and for delete/get methods (where value is null) pass `null` for kind.

Also update the create methods to pass kind to the broker request:

```java
// in createGloballyScopedClusterVariable:
new BrokerCreateClusterVariableRequest()
    .setName(request.name())
    .setValue(toDirectBufferValue(request.value()))
    .setKind(request.kind())
    .setGlobalScope(),

// in createTenantScopedClusterVariable:
new BrokerCreateClusterVariableRequest()
    .setName(request.name())
    .setValue(toDirectBufferValue(request.value()))
    .setKind(request.kind())
    .setTenantScope(request.tenantId()),
```

- [ ] **Step 4: Update `ClusterVariableMapper` (gateway mapper)**

The `ClusterVariableMapper` builds `ClusterVariableRequest` objects from REST requests. Update the four `toXxx` methods that produce create requests to map `request.getKind()`:

```java
// Helper to convert gateway model kind to protocol kind
private static io.camunda.zeebe.protocol.record.value.ClusterVariableKind toProtocolKind(
    final io.camunda.gateway.protocol.model.ClusterVariableKindEnum kind) {
  if (kind == null) {
    return io.camunda.zeebe.protocol.record.value.ClusterVariableKind.JSON;
  }
  return switch (kind) {
    case JSON -> io.camunda.zeebe.protocol.record.value.ClusterVariableKind.JSON;
    case SECRET_REFERENCE ->
        io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE;
  };
}
```

In `toGlobalClusterVariableCreateRequest`:

```java
() -> new ClusterVariableRequest(request.getName(), request.getValue(), null,
    toProtocolKind(request.getKind()))
```

In `toTenantClusterVariableCreateRequest`:

```java
() -> new ClusterVariableRequest(request.getName(), request.getValue(), tenantId,
    toProtocolKind(request.getKind()))
```

For delete/get `ClusterVariableRequest` constructors (where `value` is null), pass `null` for kind.

- [ ] **Step 5: Update `ResponseMapper.toClusterVariableResponse()`**

Map `kind` from `ClusterVariableRecord` to `ClusterVariableKindEnum`:

```java
public static ClusterVariableResult toClusterVariableResponse(
    final ClusterVariableRecord clusterVariableRecord) {
  final ClusterVariableScopeEnum scope =
      clusterVariableRecord.isTenantScoped()
          ? ClusterVariableScopeEnum.TENANT
          : ClusterVariableScopeEnum.GLOBAL;
  final @Nullable String tenantId =
      clusterVariableRecord.isTenantScoped() ? clusterVariableRecord.getTenantId() : null;
  final ClusterVariableKindEnum kind =
      switch (clusterVariableRecord.getKind()) {
        case SECRET_REFERENCE -> ClusterVariableKindEnum.SECRET_REFERENCE;
        default -> ClusterVariableKindEnum.JSON;
      };
  return ClusterVariableResult.Builder.create()
      .name(clusterVariableRecord.getName())
      .scope(scope)
      .tenantId(tenantId)
      .kind(kind)
      .value(clusterVariableRecord.getValue())
      .build();
}
```

Add import: `import io.camunda.gateway.protocol.model.ClusterVariableKindEnum;`

- [ ] **Step 6: Update `SearchQueryResponseMapper`**

In both `toClusterVariableSearchResult()` and `toClusterVariableResult()`, add kind mapping before the builder call:

```java
private static ClusterVariableKindEnum toKindEnum(
    final io.camunda.search.entities.ClusterVariableKind kind) {
  if (kind == null) {
    return ClusterVariableKindEnum.JSON;
  }
  return switch (kind) {
    case SECRET_REFERENCE -> ClusterVariableKindEnum.SECRET_REFERENCE;
    default -> ClusterVariableKindEnum.JSON;
  };
}
```

Then add `.kind(toKindEnum(clusterVariableEntity.kind()))` to both builder calls.

- [ ] **Step 7: Update `SearchQueryFilterMapper.toClusterVariableFilter()`**

Add kind mapping inside the filter builder block:

```java
ofNullable(filter.getKind()).map(mapToStringOperations()).ifPresent(builder::kindOperations);
```

- [ ] **Step 8: Write failing controller test**

Add to `ClusterVariableControllerTest`:

```java
@Test
void shouldCreateGlobalClusterVariableWithSecretReferenceKind() throws Exception {
  // given
  final var record = new ClusterVariableRecord()
      .setName("myVar")
      .setScope(io.camunda.zeebe.protocol.record.value.ClusterVariableScope.GLOBAL)
      .setKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE);
  when(clusterVariableServices.createGloballyScopedClusterVariable(
          createRequestCaptor.capture(), any()))
      .thenReturn(CompletableFuture.completedFuture(record));

  // when / then
  mockMvc
      .perform(
          post(GLOBAL_URL)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"myVar\",\"value\":\"camunda.secrets.MY_SECRET\",\"kind\":\"SECRET_REFERENCE\"}"))
      .andExpect(status().isOk())
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(
          content().json("{\"kind\":\"SECRET_REFERENCE\"}", JsonCompareMode.LENIENT));

  assertThat(createRequestCaptor.getValue().kind())
      .isEqualTo(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE);
}
```

- [ ] **Step 9: Run controller test**

```bash
./mvnw verify -pl zeebe/gateway-rest -Dtest=ClusterVariableControllerTest -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add zeebe/gateway-protocol/ \
        service/src/main/java/io/camunda/service/ClusterVariableServices.java \
        gateways/gateway-mapping-http/ \
        zeebe/gateway-rest/src/test/java/io/camunda/zeebe/gateway/rest/controller/ClusterVariableControllerTest.java
git commit -m "feat: add kind to REST API, gateway mappers and service layer"
```

---

### Task 7: CamundaClient — kind enum, filter property, create commands, response

**Files:**
- Create: `clients/java/src/main/java/io/camunda/client/api/search/enums/ClusterVariableKind.java`
- Create: `clients/java/src/main/java/io/camunda/client/api/search/filter/builder/ClusterVariableKindProperty.java`
- Create: `clients/java/src/main/java/io/camunda/client/impl/search/filter/builder/ClusterVariableKindPropertyImpl.java`
- Modify: `clients/java/src/main/java/io/camunda/client/api/search/filter/ClusterVariableFilter.java`
- Modify: `clients/java/src/main/java/io/camunda/client/impl/search/filter/ClusterVariableFilterImpl.java`
- Modify: `clients/java/src/main/java/io/camunda/client/api/command/GloballyScopedClusterVariableCreationCommandStep1.java`
- Modify: `clients/java/src/main/java/io/camunda/client/impl/command/GloballyScopedCreateClusterVariableImpl.java`
- Modify: `clients/java/src/main/java/io/camunda/client/api/command/TenantScopedClusterVariableCreationCommandStep1.java`
- Modify: `clients/java/src/main/java/io/camunda/client/impl/command/TenantScopedCreateClusterVariableImpl.java`
- Modify: `clients/java/src/main/java/io/camunda/client/api/response/CreateClusterVariableResponse.java`
- Modify: `clients/java/src/main/java/io/camunda/client/impl/response/CreateClusterVariableResponseImpl.java`
- Test: `clients/java/src/test/java/io/camunda/client/clustervariable/CreateClusterVariableTest.java`
- Test: `clients/java/src/test/java/io/camunda/client/clustervariable/SearchClusterVariableTest.java`

**Interfaces:**
- Consumes: Generated `ClusterVariableKindEnum` and `ClusterVariableKindFilterProperty` from `io.camunda.client.protocol.rest` (from Task 6 regeneration)

- [ ] **Step 1: Create `ClusterVariableKind` client enum**

```java
// clients/java/src/main/java/io/camunda/client/api/search/enums/ClusterVariableKind.java
/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 * ... Apache License 2.0 header ...
 */
package io.camunda.client.api.search.enums;

public enum ClusterVariableKind {
  JSON,
  SECRET_REFERENCE;
}
```

- [ ] **Step 2: Create `ClusterVariableKindProperty` interface**

```java
// clients/java/src/main/java/io/camunda/client/api/search/filter/builder/ClusterVariableKindProperty.java
/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 * ... Apache License 2.0 header ...
 */
package io.camunda.client.api.search.filter.builder;

import io.camunda.client.api.search.enums.ClusterVariableKind;

public interface ClusterVariableKindProperty
    extends LikeProperty<ClusterVariableKind, String, ClusterVariableKindProperty> {}
```

- [ ] **Step 3: Create `ClusterVariableKindPropertyImpl`**

```java
// clients/java/src/main/java/io/camunda/client/impl/search/filter/builder/ClusterVariableKindPropertyImpl.java
/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 * ... Apache License 2.0 header ...
 */
package io.camunda.client.impl.search.filter.builder;

import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.client.api.search.filter.builder.ClusterVariableKindProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ClusterVariableKindEnum;
import io.camunda.client.protocol.rest.ClusterVariableKindFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class ClusterVariableKindPropertyImpl
    extends TypedSearchRequestPropertyProvider<ClusterVariableKindFilterProperty>
    implements ClusterVariableKindProperty {

  private final ClusterVariableKindFilterProperty filterProperty =
      new ClusterVariableKindFilterProperty();

  @Override
  public ClusterVariableKindProperty eq(final ClusterVariableKind value) {
    filterProperty.set$Eq(EnumUtil.convert(value, ClusterVariableKindEnum.class));
    return this;
  }

  @Override
  public ClusterVariableKindProperty neq(final ClusterVariableKind value) {
    filterProperty.set$Neq(EnumUtil.convert(value, ClusterVariableKindEnum.class));
    return this;
  }

  @Override
  public ClusterVariableKindProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public ClusterVariableKindProperty in(final List<ClusterVariableKind> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> EnumUtil.convert(source, ClusterVariableKindEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public ClusterVariableKindProperty in(final ClusterVariableKind... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected ClusterVariableKindFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public ClusterVariableKindProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
```

- [ ] **Step 4: Add `kind()` to `ClusterVariableFilter` interface**

Add after `scope(Consumer<ClusterVariableScopeProperty> fn)`:

```java
/**
 * Filters cluster variables by kind.
 *
 * @param kind the kind (JSON or SECRET_REFERENCE)
 * @return the updated filter
 */
ClusterVariableFilter kind(final ClusterVariableKind kind);

/**
 * Filters cluster variables by kind using advanced filter operations.
 *
 * @param fn the kind property consumer
 * @return the updated filter
 */
ClusterVariableFilter kind(final Consumer<ClusterVariableKindProperty> fn);
```

Add imports:

```java
import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.client.api.search.filter.builder.ClusterVariableKindProperty;
```

- [ ] **Step 5: Implement `kind()` in `ClusterVariableFilterImpl`**

```java
@Override
public ClusterVariableFilter kind(final ClusterVariableKind kind) {
  kind(b -> b.eq(kind));
  return this;
}

@Override
public ClusterVariableFilter kind(final Consumer<ClusterVariableKindProperty> fn) {
  final ClusterVariableKindProperty property = new ClusterVariableKindPropertyImpl();
  fn.accept(property);
  filter.setKind(provideSearchRequestProperty(property));
  return this;
}
```

Add imports for `ClusterVariableKind`, `ClusterVariableKindProperty`, `ClusterVariableKindPropertyImpl`.

- [ ] **Step 6: Add `kind()` to create command interfaces**

In `GloballyScopedClusterVariableCreationCommandStep1`, add:

```java
/**
 * Sets the kind of the cluster variable (optional, defaults to JSON).
 *
 * @param kind the kind (JSON or SECRET_REFERENCE)
 * @return this builder for method chaining
 */
GloballyScopedClusterVariableCreationCommandStep1 kind(
    io.camunda.client.api.search.enums.ClusterVariableKind kind);
```

Same method in `TenantScopedClusterVariableCreationCommandStep1`, returning `TenantScopedClusterVariableCreationCommandStep1`.

- [ ] **Step 7: Implement `kind()` in create command impls**

In `GloballyScopedCreateClusterVariableImpl`:

```java
@Override
public GloballyScopedClusterVariableCreationCommandStep1 kind(
    final io.camunda.client.api.search.enums.ClusterVariableKind kind) {
  createVariableRequest.setKind(
      io.camunda.client.impl.util.EnumUtil.convert(
          kind, io.camunda.client.protocol.rest.ClusterVariableKindEnum.class));
  return this;
}
```

Same pattern in `TenantScopedCreateClusterVariableImpl`.

- [ ] **Step 8: Add `getKind()` to `CreateClusterVariableResponse`**

```java
io.camunda.client.api.search.enums.ClusterVariableKind getKind();
```

- [ ] **Step 9: Implement `getKind()` in `CreateClusterVariableResponseImpl`**

Add field:

```java
private io.camunda.client.api.search.enums.ClusterVariableKind kind;
```

In `setResponse()`, add:

```java
kind = EnumUtil.convert(
    clusterVariableResult.getKind(),
    io.camunda.client.api.search.enums.ClusterVariableKind.class);
```

Add getter:

```java
@Override
public io.camunda.client.api.search.enums.ClusterVariableKind getKind() {
  return kind;
}
```

- [ ] **Step 10: Write failing client tests**

In `CreateClusterVariableTest`, add:

```java
@Test
void shouldCreateGlobalClusterVariableWithKind() {
  // given
  final var responseProto =
      Instancio.create(ClusterVariableResult.class)
          .scope(ClusterVariableScopeEnum.GLOBAL)
          .kind(io.camunda.client.protocol.rest.ClusterVariableKindEnum.SECRET_REFERENCE);
  gatewayService.onCreateGlobalClusterVariableRequest(responseProto);

  // when
  final var response =
      client
          .newGloballyScopedClusterVariableCreateRequest()
          .create(VARIABLE_NAME, VARIABLE_VALUE)
          .kind(io.camunda.client.api.search.enums.ClusterVariableKind.SECRET_REFERENCE)
          .send()
          .join();

  // then
  assertThat(response.getKind())
      .isEqualTo(io.camunda.client.api.search.enums.ClusterVariableKind.SECRET_REFERENCE);
  final var sentRequest =
      gatewayService.getLastRequest(
          io.camunda.client.protocol.rest.CreateClusterVariableRequest.class);
  assertThat(sentRequest.getKind())
      .isEqualTo(io.camunda.client.protocol.rest.ClusterVariableKindEnum.SECRET_REFERENCE);
}
```

In `SearchClusterVariableTest`, add:

```java
@Test
void shouldFilterClusterVariablesByKind() {
  // when
  client
      .newClusterVariableSearchRequest()
      .filter(f -> f.kind(io.camunda.client.api.search.enums.ClusterVariableKind.SECRET_REFERENCE))
      .send()
      .join();

  // then
  final var request =
      gatewayService.getLastRequest(
          io.camunda.client.protocol.rest.ClusterVariableSearchQueryRequest.class);
  assertThat(request.getFilter().getKind()).isNotNull();
}
```

- [ ] **Step 11: Run client tests**

```bash
./mvnw verify -pl clients/java -Dtest="CreateClusterVariableTest,SearchClusterVariableTest" -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 12: Full module build + format + commit**

```bash
./mvnw license:format spotless:apply -T1C
./mvnw verify -pl clients/java -DskipTests=false -DskipITs -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
git add clients/java/
git commit -m "feat: add ClusterVariable kind to CamundaClient filter and create command"
```

---

### Task 8: Final build verification

- [ ] **Step 1: Full repo compile check**

```bash
./mvnw install -Dquickly -T1C 2>&1 | grep -E "BUILD|ERROR|FAIL"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run all ClusterVariable-related tests in key modules**

```bash
./mvnw verify \
  -pl zeebe/protocol-impl,zeebe/exporters/camunda-exporter,search/search-client-query-transformer,clients/java,zeebe/gateway-rest \
  -Dtest="ClusterVariableRecordTest,ClusterVariableCreatedUpdatedHandlerTest,ClusterVariableFilterMetadataTransformerTest,CreateClusterVariableTest,SearchClusterVariableTest,ClusterVariableControllerTest" \
  -DskipTests=false -DskipITs -Dquickly -T2 2>&1 | grep -E "BUILD|ERROR|FAIL|Tests run"
```

Expected: all pass.

- [ ] **Step 3: Commit if any formatting drifted**

```bash
./mvnw license:format spotless:apply -T1C
git diff --stat
# commit only if there are changes
```

