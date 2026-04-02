---
toc_min_heading_level: 2
toc_max_heading_level: 4
---

# Contributing to the RDBMS module

This guide provides practical development guidelines for contributors working on the `db/rdbms`
module. For high-level architectural decisions (ADRs) and a component overview, see the
[architecture documentation](./rdbms_architecture_docs.md).

---

## Module structure

```
db/rdbms/
├── src/main/java/io/camunda/db/rdbms/
│   ├── read/
│   │   ├── domain/          # DbQuery records (e.g. ProcessDefinitionDbQuery)
│   │   ├── mapper/          # Entity mappers: DbModel → API entity (used when direct MyBatis
│   │   │                    #   result mapping is not sufficient)
│   │   ├── security/        # RdbmsResourceAccessController (authorization filter)
│   │   ├── service/         # DbReader implementations (e.g. ProcessDefinitionDbReader)
│   │   └── RdbmsReaderConfig.java
│   ├── sql/
│   │   ├── columns/         # SearchColumn enums (e.g. ProcessDefinitionSearchColumn)
│   │   ├── typehandler/     # DB-specific MyBatis TypeHandlers (PostgreSQL array, Oracle XML)
│   │   └── *Mapper.java     # MyBatis Mapper interfaces
│   ├── typehandler/         # Generic MyBatis TypeHandlers (JSON, null/empty-string handling)
│   ├── write/
│   │   ├── domain/          # DbModel records (e.g. ProcessDefinitionDbModel)
│   │   ├── queue/           # ExecutionQueue, QueueItem, Mergers
│   │   ├── service/         # Writer implementations (e.g. ProcessDefinitionWriter)
│   │   └── util/            # Internal write-path utilities (e.g. TruncateUtil)
│   └── RdbmsService.java    # Main entry point, wires readers and writers
│
└── src/main/resources/
    └── mapper/              # MyBatis Mapper XML files (e.g. ProcessDefinitionMapper.xml)

db/rdbms-schema/
└── src/main/resources/
    └── db/
        ├── changelog/
        │   └── rdbms-exporter/
        │       ├── changelog-master.xml
        │       └── changesets/
        │           └── 8.9.0.xml        # Schema definitions
        └── vendor-properties/           # Database-specific properties
            ├── h2.properties
            ├── mariadb.properties
            ├── mssql.properties
            ├── mysql.properties
            ├── oracle.properties
            └── postgresql.properties
```

---

## Development guidelines

### General conventions

- **No Spring IoC in the `db/rdbms` module**: Spring bean annotations (`@Component`, `@Bean`,
  `@Autowired`, etc.) are not used in `db/rdbms`. All wiring is done externally in `dist/` using
  constructor injection. `LiquibaseSchemaManager` is the only class that extends a Spring-Liquibase
  integration base class; it carries no Spring annotations itself.
- **CQRS**: Read and write paths are strictly separated. Readers use MyBatis mappers directly;
  writers enqueue operations through the `ExecutionQueue`.
- **Immutable domain models**: DbModels and DbQueries are Java `record` types. Use the nested
  `Builder` to construct them.
- **Queue-based writes**: Never call mapper methods directly from a writer. Always enqueue
  operations via `ExecutionQueue.executeInQueue(...)`.
- **Always run all supported databases** before merging: schema and SQL changes must be verified
  against H2, PostgreSQL, MariaDB, MySQL, MSSQL, and Oracle. Use the `db/docker-compose.yml` file
  to start all supported database containers locally:

  ```bash
  cd db/
  docker compose up postgres mssql mariadb oracle -d
  ```

  H2 runs in-process during tests and does not need a separate container. MySQL and MariaDB share
  the same port (3306), so start only one at a time unless you override the port mapping.

---

### Adding a new entity

This section walks through adding support for a completely new entity — for example, a fictional
`Widget` entity — by following the same patterns used by `ProcessDefinition`.

#### 1. Create the DbModel

The DbModel is an immutable Java `record` that mirrors the database table columns.

**File:** `db/rdbms/src/main/java/io/camunda/db/rdbms/write/domain/WidgetDbModel.java`

```java
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record WidgetDbModel(
  Long widgetKey,
  String name) {

  public static class WidgetDbModelBuilder implements ObjectBuilder<WidgetDbModel> {

    private Long widgetKey;

    public WidgetDbModelBuilder widgetKey(final Long widgetKey) {
      this.widgetKey = widgetKey;
      return this;
    }

    public WidgetDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public WidgetDbModel build() {
      return new WidgetDbModel(widgetKey, name);
    }
  }
}
```

> **Tip:** If the entity supports merge/update optimizations in the `ExecutionQueue`, implement
> `Copyable<WidgetDbModel>` and add a `copy(Function<Builder, Builder>)` method. See
> `ProcessInstanceDbModel` for an example.

---

#### 2. Create the DbQuery

The DbQuery bundles the filter, authorization constraints, sort, and pagination in a single
immutable object that is passed directly to the MyBatis mapper.

**File:** `db/rdbms/src/main/java/io/camunda/db/rdbms/read/domain/WidgetDbQuery.java`

See `GroupDbQuery` for a complete example.

---

#### 3. Create the SearchColumn Enum

The `SearchColumn` enum maps API property names to database column names and is used by the
`AbstractEntityReader` to convert sort fields.

**File:** `db/rdbms/src/main/java/io/camunda/db/rdbms/sql/columns/WidgetSearchColumn.java`

```java
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.WidgetEntity;

public enum WidgetSearchColumn implements SearchColumn<WidgetEntity> {
  WIDGET_KEY("widgetKey"),
  NAME("name");

  private final String property;

  WidgetSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<WidgetEntity> getEntityClass() {
    return WidgetEntity.class;
  }
}
```

> The `property` value must exactly match the field name in the API entity class (e.g.,
> `WidgetEntity`). The enum name (e.g., `WIDGET_KEY`) is used as the SQL column name by convention.

---

#### 4. Create the Mapper Interface

The mapper interface is the MyBatis contract between Java and SQL.

**File:** `db/rdbms/src/main/java/io/camunda/db/rdbms/sql/WidgetMapper.java`

```java
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.WidgetDbQuery;
import io.camunda.db.rdbms.write.domain.WidgetDbModel;
import io.camunda.search.entities.WidgetEntity;
import java.util.List;

public interface WidgetMapper {

  void insert(WidgetDbModel widget);

  Long count(WidgetDbQuery query);

  List<WidgetEntity> search(WidgetDbQuery query);
}
```

---

#### 5. Create the Mapper XML

The XML file provides the actual SQL for each method declared in the mapper interface.
It must be placed in `src/main/resources/mapper/` and the `namespace` must exactly match the
fully qualified class name of the mapper interface.

**File:** `db/rdbms/src/main/resources/mapper/WidgetMapper.xml`

- Always use `${prefix}` before table names — this is substituted with the configured table
  prefix at runtime.
- Use `#{param}` (prepared statement binding) for user-supplied values to prevent SQL injection.
- Use `${variable}` (string substitution) only for structural SQL fragments like column names
  from the vendor properties or trusted internal values.
- Reuse the shared SQL fragments from `Commons.xml` for sorting, paging, and filter operators.

See `GroupMapper.xml` as complete example of a mapper XML with search queries, filters, sorting, and
paging.

---

#### 6. Create the DbReader

The `DbReader` translates the API-level search query into a `DbQuery` and delegates to the mapper.

**File:** `db/rdbms/src/main/java/io/camunda/db/rdbms/read/service/WidgetDbReader.java`

See `GroupDbReader` for a complete example.

---

#### 7. Create the Writer

The `Writer` enqueues insert/update/delete operations into the `ExecutionQueue`. Operations are
batched and flushed periodically or when the queue is full.

**File:** `db/rdbms/src/main/java/io/camunda/db/rdbms/write/service/WidgetWriter.java`

```java
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.WidgetDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class WidgetWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public WidgetWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final WidgetDbModel widget) {
    executionQueue.executeInQueue(
      new QueueItem(
        ContextType.WIDGET,
        WriteStatementType.INSERT,
        widget.widgetKey(),
        "io.camunda.db.rdbms.sql.WidgetMapper.insert",
        widget));
  }
}
```

> **Merge optimization:** If you expect multiple updates to the same entity within the same flush
> cycle (e.g., state changes on a running process instance), implement a `QueueItemMerger` to
> combine queue items rather than executing redundant SQL statements. See `UpsertMerger` and
> `InsertVariableMerger` for examples.
>
> **`ContextType`:** Add a new constant to the `ContextType` enum for the new entity.

---

#### 8. Register in RdbmsService

Wire the new reader and writer into `RdbmsService` so they can be accessed by exporters and search
clients.

```java
// in RdbmsService constructor / factory
final WidgetMapper widgetMapper = sqlSession.getMapper(WidgetMapper.class);
final var widgetWriter = new WidgetWriter(executionQueue);
final var widgetReader = new WidgetDbReader(widgetMapper, readerConfig);
```

---

### Adding a new field to an existing entity

Adding a new column to an existing entity requires changes in the following places:

1. **Liquibase changeset** (`db/rdbms-schema`): Add an `addColumn` changeset in a new version file
   (see [Changeset Conventions](#changeset-conventions)). Never modify a released changeset.

2. **DbModel** (`write/domain/`): Add the field to the record and the builder.

3. **Mapper XML** (`src/main/resources/mapper/`): Add the column to the `INSERT` statement, any
   relevant `UPDATE` statements, and the `resultMap` (or constructor mapping) for `SELECT`.

4. **MyBatis Mapper interface** (`sql/`): Usually no change is needed unless you are adding a new
   query method.

5. **DbReader / SearchColumn** (`read/service/`, `sql/columns/`): If the new field is sortable or
   filterable, add it to the `SearchColumn` enum and update the search filter in the mapper XML.

---

## Liquibase guidelines

### Changeset conventions

- **One changeset file per release**: e.g., `changesets/8.9.0.xml`. New changesets for the next
  release go in a new version file.
- **Never modify a released changeset**: Liquibase checksums existing changesets. Modifying a
  released changeset will break schema migration for all existing deployments.
- **One change per `<changeSet>`**: Keep changesets minimal. For example, creating a table
  and adding its indexes should be multiple `<changeSet>`. That way, if an error occurs in the
  middle of the changeset, the successfully applied changes are still recorded, and the failed
  change can be fixed and re-run without affecting the already applied changes.
- **`id` naming**: Use snake_case and describe the change (e.g.,
  `create_widget_table`, `add_widget_status_column`).
- **Author**: Always use `author="camunda"`.
- **Register new files** in `changelog-master.xml`:

**Add idempotency preconditions to all changesets**
In some rare cases, the Liquibase migration might get interrupted by the surrounding infrastructure
orchestration (e.g., Kubernetes Job) before it can mark the changeset as executed. This can lead to
the same changeset being applied multiple times on the next run, which causes errors (e.g., "table
already exists"). To prevent this, add an appropriate precondition to each changeset that checks
whether the change has already been applied and mark it as ran if yes. For example:

|  Change type  |           Precondition            |
|---------------|-----------------------------------|
| `createTable` | `<not><tableExists .../></not>`   |
| `createIndex` | `<not><indexExists .../></not>`   |
| `addColumn`   | `<not><columnExists .../></not>`  |
| `dropIndex`   | `<indexExists .../>` (no `<not>`) |

```xml
<include file="db/changelog/rdbms-exporter/changesets/8.10.0.xml"/>
```

**Example changeset:**

```xml
<changeSet id="create_widget_table" author="camunda">
  <preConditions onFail="MARK_RAN">
    <not>
      <tableExists tableName="${prefix}WIDGET"/>
    </not>
  </preConditions>
  <createTable tableName="${prefix}WIDGET">
    <column name="WIDGET_KEY" type="BIGINT">
      <constraints primaryKey="true"/>
    </column>
    <column name="NAME" type="NVARCHAR(${userCharColumnSize})"/>
  </createTable>
</changeSet>
```

---

### Data types

Use the following Liquibase-level types which are mapped correctly across all supported databases:

|          Liquibase Type           |                      Use for                       |                                                        Notes                                                        |
|-----------------------------------|----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `BIGINT`                          | Entity keys, foreign keys, numeric values          | Maps to `NUMBER(19)` on Oracle, `BIGINT` elsewhere                                                                  |
| `NUMBER`                          | Partition IDs, counters, small integers            | Use `SMALLINT` for columns with a small known range (e.g., version, count)                                          |
| `SMALLINT`                        | Small counters (version, num_incidents)            |                                                                                                                     |
| `NVARCHAR(${userCharColumnSize})` | User-facing string fields (names, IDs, tenant IDs) | The size is 256 chars on most DBs, 512 bytes on Oracle (due to multi-byte character sets). Never hardcode the size. |
| `VARCHAR(n)`                      | Internal fixed-format strings (state enums, types) | Prefer a generous but bounded size                                                                                  |
| `CLOB`                            | Large text (BPMN XML, full variable values)        | Maps to `CLOB` on Oracle/H2/PostgreSQL, `TEXT` or `LONGTEXT` elsewhere                                              |
| `BOOLEAN`                         | Boolean flags                                      | Maps to `BOOLEAN` on PostgreSQL/H2, `BIT(1)` on MySQL/MariaDB, `NUMBER(1)` on Oracle, `BIT` on MSSQL                |
| `TIMESTAMP WITH TIME ZONE(3)`     | Timestamps with timezone                           | Override with `<modifySql>` for MariaDB/MySQL and MSSQL; see key rules below                                        |

**Key rules:**

- Always use `${userCharColumnSize}` (not a hardcoded number) for user-facing string columns.
- Always add `<modifySql>` overrides for `TIMESTAMP WITH TIME ZONE`:
  - MariaDB/MySQL: replace `TIMESTAMP WITH TIME ZONE` with `TIMESTAMP`.
  - MSSQL: replace Liquibase's generated `datetime2` with `DATETIMEOFFSET` (Liquibase generates
    `datetime2` for `TIMESTAMP WITH TIME ZONE` on MSSQL).
- Add `HISTORY_CLEANUP_DATE` (`TIMESTAMP WITH TIME ZONE(3)`) and `PARTITION_ID` (`NUMBER`) to any
  table that participates in history cleanup and is not automatically cleaned by the
  process-instance cleanup.

---

### Indexes

Add indexes for any column that is used as a filter, sort key, or join condition in common search
queries. Follow these rules:

- **Index numeric/key columns over string columns.** A `BIGINT` foreign key (e.g.,
  `PROCESS_INSTANCE_KEY`) is far cheaper to index and compare than an `NVARCHAR` field.
- **Index every foreign key column.** This prevents full-table scans on the child table when
  the parent row is deleted or joined.
- **Index history-cleanup columns together with `PARTITION_ID`** so that range scans during
  scheduled cleanup stay efficient:

  ```xml
  <createIndex tableName="${prefix}MY_TABLE" indexName="${prefix}IDX_MY_TABLE_HIST">
    <column name="PARTITION_ID"/>
    <column name="HISTORY_CLEANUP_DATE"/>
  </createIndex>
  ```
- **Use a descriptive `indexName`** that encodes the table and purpose, always prefixed with
  `${prefix}` (e.g., `${prefix}IDX_VARIABLE_PROCESS_INSTANCE_KEY`).
- **One `<createIndex>` per column set** — do not bundle unrelated columns into a single index
  unless the combination is specifically required by a multi-column search pattern.

---

### Foreign Keys and Cascade Deletes

Use foreign keys **only** for strict 1:m ownership relations where the child rows have no
independent lifecycle — for example, a tag or candidate list that belongs exclusively to one parent
entity. Do **not** add foreign keys for loose references between independent entities (e.g.,
`PROCESS_INSTANCE_KEY` stored on `VARIABLE` — the variable might outlive the process instance
during history retention).

Rules:

- **Always `deleteCascade="true"`** on ownership FKs so that deleting the parent automatically
  removes all child rows, keeping history cleanup simple.
- **Declare the FK on the child column** using Liquibase's inline `<constraints>` element:

  ```xml
  <column name="WIDGET_KEY" type="BIGINT">
    <constraints
      foreignKeyName="${prefix}FK_WIDGET_TAG_WIDGET"
      referencedTableName="${prefix}WIDGET"
      referencedColumnNames="WIDGET_KEY"
      deleteCascade="true"/>
  </column>
  ```
- **Always prefix `foreignKeyName`** with `${prefix}` to avoid name conflicts in shared schemas.
- **Always add a matching `<createIndex>`** on the FK column (see [Indexes](#indexes) above).

---

### Database-specific code in Liquibase

When a Liquibase type or syntax is not portable across databases, use:

- **`<modifySql dbms="...">`**: Replaces a specific string in the generated SQL for the named
  database(s) only. Use this for type overrides in `CREATE TABLE`.
- **`<property name="..." value="..." dbms="...">`**: Defines a variable that resolves to a
  different value depending on the active database. Use for types that differ between databases and
  need to appear in multiple changesets.

---

## MyBatis guidelines

### Common practices

- **Always prefix table names** with `${prefix}` to support configurable schema prefixes:

  ```sql
  SELECT * FROM ${prefix}WIDGET
  ```
- **Use `#{param}`** (prepared statement) for all user-supplied values; never use `${param}` for
  user data to avoid SQL injection.
- **Use `${variable}`** only for structural SQL fragments from the vendor properties (e.g.,
  `${paging.after}`, `${count.limit}`, `${escapeChar}`, `${true}`, `${false}`) or for column
  names that come from internal, trusted enums (e.g., sort column names).
- **Map result columns explicitly** using `<resultMap>` with `<constructor>` args (preferred) or
  `<result>` mappings. This makes the mapping resilient to column order changes.
- **Avoid N+1 queries**: Load related data in the same query using JOINs rather than triggering
  separate queries per row.
- **Boolean values** must use the vendor-specific property instead of literals:

  ```sql
  -- correct
  WHERE IS_PREVIEW = ${true}
  -- wrong
  WHERE IS_PREVIEW = true
  ```

---

### Using `Commons.xml`

`Commons.xml` (namespace `io.camunda.db.rdbms.sql.Commons`) contains reusable SQL fragments for
cross-cutting concerns. Always use these instead of duplicating the SQL.

|             Fragment ID              |                                  Purpose                                  |
|--------------------------------------|---------------------------------------------------------------------------|
| `operationCondition`                 | Maps a filter `Operation` (EQUALS, LIKE, IN, …) to its SQL condition      |
| `keySetPageFilter`                   | Adds the keyset-pagination `WHERE` clause                                 |
| `orderBy`                            | Generates an `ORDER BY` clause from sort orderings                        |
| `paging`                             | Adds the vendor-specific pagination clause (LIMIT/OFFSET or FETCH/OFFSET) |
| `historyCleanup`                     | Vendor-specific batched `DELETE` by `HISTORY_CLEANUP_DATE`                |
| `historyDeletionByKeys`              | Vendor-specific batched `DELETE` by a list of keys                        |
| `rootProcessInstanceHistoryDeletion` | History deletion by `ROOT_PROCESS_INSTANCE_KEY`                           |
| `processInstanceHistoryDeletion`     | History deletion by `PROCESS_INSTANCE_KEY`                                |
| `timeBucket`                         | Vendor-specific time-bucketing for aggregations                           |

**Usage example — sorting and paging:**

```xml
<select id="search" parameterType="io.camunda.db.rdbms.read.domain.WidgetDbQuery"
  resultMap="searchResultMap">
  SELECT * FROM (
  SELECT WIDGET_KEY, NAME, TENANT_ID
  FROM ${prefix}WIDGET
  <include refid="io.camunda.db.rdbms.sql.WidgetMapper.searchFilter"/>
  ) t
  <include refid="io.camunda.db.rdbms.sql.Commons.keySetPageFilter"/>
  <include refid="io.camunda.db.rdbms.sql.Commons.orderBy"/>
  <include refid="io.camunda.db.rdbms.sql.Commons.paging"/>
</select>
```

**Usage example — filter operation condition:**

```xml
<if test="filter.nameOperations != null and !filter.nameOperations.isEmpty()">
  <foreach collection="filter.nameOperations" item="operation">
    AND NAME
    <include refid="io.camunda.db.rdbms.sql.Commons.operationCondition"/>
  </foreach>
</if>
```

The `operationCondition` fragment expects a variable named `operation` in scope (provided via the
`foreach item="operation"` binding). The filter field (e.g., `nameOperations`) must be a
`List<Operation<T>>` where each `Operation` (see `io.camunda.search.filter.Operation`) carries an
`operator` (e.g., `EQUALS`, `LIKE`, `IN`) and one or more `values`.

**Usage example — history cleanup:**

```xml
<delete id="deleteRootProcessInstanceRelatedData">
  <bind name="tableName" value="'WIDGET'"/>
  <bind name="primaryKeyColumn" value="'WIDGET_KEY'"/>
  <include refid="io.camunda.db.rdbms.sql.Commons.rootProcessInstanceHistoryDeletion"/>
</delete>
```

---

### Database-specific SQL

When a single SQL statement cannot be written in a way that works on all supported databases, use
the `databaseId` attribute to provide separate implementations. MyBatis selects the matching
statement at runtime.

Supported `databaseId` values: `h2`, `postgresql`, `mysql`, `mariadb`, `oracle`, `mssql`.

```xml
<!-- PostgreSQL -->
<insert id="upsert"
  parameterType="io.camunda.db.rdbms.write.domain.WidgetDbModel" databaseId="postgresql">
  INSERT INTO ${prefix}WIDGET (WIDGET_KEY, NAME)
  VALUES (#{widgetKey}, #{name})
  ON CONFLICT (WIDGET_KEY) DO UPDATE SET
    NAME = #{name}
</insert>

<!-- MySQL / MariaDB -->
<insert id="upsert"
  parameterType="io.camunda.db.rdbms.write.domain.WidgetDbModel" databaseId="mysql">
  INSERT INTO ${prefix}WIDGET (WIDGET_KEY, NAME)
  VALUES (#{widgetKey}, #{name})
  ON DUPLICATE KEY UPDATE
    NAME = #{name}
</insert>

<insert id="upsert"
  parameterType="io.camunda.db.rdbms.write.domain.WidgetDbModel" databaseId="mariadb">
  INSERT INTO ${prefix}WIDGET (WIDGET_KEY, NAME)
  VALUES (#{widgetKey}, #{name})
  ON DUPLICATE KEY UPDATE
    NAME = #{name}
</insert>

<!-- H2 / Oracle: MERGE INTO … USING dual -->
<insert id="upsert"
  parameterType="io.camunda.db.rdbms.write.domain.WidgetDbModel" databaseId="h2">
  MERGE INTO ${prefix}WIDGET w
    USING dual
    ON (w.WIDGET_KEY = #{widgetKey})
  WHEN MATCHED THEN
    UPDATE SET w.NAME = #{name}
  WHEN NOT MATCHED THEN
    INSERT (WIDGET_KEY, NAME)
    VALUES (#{widgetKey}, #{name})
</insert>

<insert id="upsert"
  parameterType="io.camunda.db.rdbms.write.domain.WidgetDbModel" databaseId="oracle">
  MERGE INTO ${prefix}WIDGET w
    USING dual
    ON (w.WIDGET_KEY = #{widgetKey})
  WHEN MATCHED THEN
    UPDATE SET w.NAME = #{name}
  WHEN NOT MATCHED THEN
    INSERT (WIDGET_KEY, NAME)
    VALUES (#{widgetKey}, #{name}, #{tenantId}, #{partitionId})
</insert>

<!-- MSSQL: MERGE INTO … USING (SELECT 1 AS dummy) -->
<insert id="upsert"
  parameterType="io.camunda.db.rdbms.write.domain.WidgetDbModel" databaseId="mssql">
  MERGE INTO ${prefix}WIDGET AS target
  USING (SELECT 1 AS dummy) AS src
    ON (target.WIDGET_KEY = #{widgetKey})
  WHEN MATCHED THEN
    UPDATE SET target.NAME = #{name}
  WHEN NOT MATCHED THEN
    INSERT (WIDGET_KEY, NAME)
    VALUES (#{widgetKey}, #{name}, #{tenantId}, #{partitionId});
</insert>
```

> **Important**: If you add a database-specific override and do **not** provide a generic default,
> the statement is unavailable on databases for which no `databaseId` matches. Always ensure
> coverage for all six supported databases (either via a default or explicit `databaseId` entries
> for each).

Shared SQL fragments in `Commons.xml` use the same mechanism. For example, `orderBy` and
`historyCleanup` have explicit Oracle overrides because Oracle's default NULL ordering and DELETE
syntax differ from other databases.

---

### Oracle-specific pitfalls

Oracle has several unique behaviors that require explicit workarounds:

#### Batch inserts: `INSERT ALL INTO … SELECT * FROM dual`

Oracle does not support the `VALUES (...), (...), (...)` multi-row insert syntax. When inserting
multiple rows in a single statement (e.g., using a `BatchInsertDto`), use `INSERT ALL`:

```xml
<!-- Default: standard multi-row VALUES syntax -->
<insert id="insert" parameterType="io.camunda.db.rdbms.write.queue.BatchInsertDto">
  INSERT INTO ${prefix}WIDGET (WIDGET_KEY, NAME, TENANT_ID, PARTITION_ID)
  VALUES
  <foreach collection="dbModels" item="widget" separator=",">
    (#{widget.widgetKey}, #{widget.name}, #{widget.tenantId}, #{widget.partitionId})
  </foreach>
</insert>

  <!-- Oracle override: INSERT ALL syntax -->
<insert id="insert" parameterType="io.camunda.db.rdbms.write.queue.BatchInsertDto"
databaseId="oracle">
INSERT ALL
<foreach collection="dbModels" item="widget">
  INTO ${prefix}WIDGET (WIDGET_KEY, NAME, TENANT_ID, PARTITION_ID)
  VALUES (#{widget.widgetKey}, #{widget.name}, #{widget.tenantId}, #{widget.partitionId})
</foreach>
SELECT * FROM dual
</insert>
```

The `SELECT * FROM dual` at the end is mandatory Oracle syntax to close the `INSERT ALL` statement.

> **Note:** Because Oracle's `INSERT ALL` cannot benefit from JDBC batch mode, the vendor property
> `supportsInsertBatching=false` is set in `oracle.properties`. This disables JDBC batching for
> Oracle and relies solely on the `INSERT ALL` syntax for multi-row inserts.

#### Empty string vs. NULL

Oracle treats empty strings (`''`) as `NULL`. Because NULL comparisons use three-valued logic
(TRUE/FALSE/UNKNOWN), conditions like `WHERE NAME = ''` or `WHERE NAME != ''` never match any
rows — they evaluate to UNKNOWN rather than TRUE.

`Commons.xml` already provides an Oracle-specific override for `operationCondition` that rewrites
`EQUALS ''` and `NOT_EQUALS ''` to `IS NULL` / `IS NOT NULL` automatically. Always use
`operationCondition` for string filter conditions instead of writing the comparison inline.

#### IN clause limit of 1000

Oracle does not allow more than 1000 elements in an `IN (...)` clause. In some cases (e.g., history
cleanup by keys), we need to delete by a potentially large list of keys. To work around this, we use
an XMLTABLE-based approach that passes the list as an XML document and parses it back into rows:

```xml
<!-- Oracle override for large key lists -->
<delete id="deleteByKeys" databaseId="oracle">
  DELETE FROM ${prefix}WIDGET
  WHERE WIDGET_KEY IN (
  SELECT COLUMN_VALUE
  FROM XMLTABLE('/d/r'
  PASSING
  XMLTYPE(#{keys, typeHandler=io.camunda.db.rdbms.sql.typehandler.OracleXmlArrayTypeHandler})
  COLUMNS COLUMN_VALUE NUMBER PATH 'text()')
  )
</delete>
```

The `OracleXmlArrayTypeHandler` converts a Java `List<Long>` into an Oracle XML document of the
form `<d><r>1</r><r>2</r>…</d>`, which `XMLTABLE` then parses back into a relational result set.
See the `OracleXmlArrayTypeHandler` implementation in `db/rdbms/src/main/java/…/sql/typehandler/` for
details.

#### NULL ordering

Oracle sorts `NULL` values **last for ASC** and **first for DESC** — the opposite of the behavior
on PostgreSQL, H2, MySQL, and MariaDB. `Commons.xml` provides an Oracle-specific override for
`orderBy` that explicitly adds `NULLS FIRST` / `NULLS LAST` to restore consistent cross-database
ordering. Since you are using the shared `orderBy` fragment, this is handled automatically.

---

### Other database-specific considerations

#### MariaDB / MySQL

- `TIMESTAMP WITH TIME ZONE` is not supported. Override it with `TIMESTAMP` using `<modifySql>`.
- Upsert uses `ON DUPLICATE KEY UPDATE`:

  ```xml
  INSERT INTO ${prefix}WIDGET (WIDGET_KEY, NAME)
  VALUES (#{widgetKey}, #{name})
  ON DUPLICATE KEY UPDATE
    NAME = #{name}
  ```

  List all columns that should be updated when a duplicate key is detected. Columns that must
  remain unchanged (e.g., the primary key itself) should simply be omitted from the `UPDATE` clause.

#### MSSQL

- `TIMESTAMP WITH TIME ZONE` must be replaced with `DATETIMEOFFSET` (see `<modifySql>` example
  above).
- Boolean literals are `1` / `0` (same as Oracle), not `TRUE` / `FALSE`. Always use `${true}` and
  `${false}` from the vendor properties.
- Row-limited DELETE uses `DELETE TOP (n)` instead of `DELETE ... LIMIT n`:

  ```xml
  <delete id="cleanupOldWidgets" databaseId="mssql">
    DELETE TOP (#{limit})
    FROM ${prefix}WIDGET
    WHERE HISTORY_CLEANUP_DATE &lt; #{cleanupDate, jdbcType=TIMESTAMP}
  </delete>
  ```
- Keyset paging requires `ORDER BY` before `OFFSET … FETCH NEXT`. The `${count.limit}` vendor
  property includes an `ORDER BY col` placeholder for MSSQL to satisfy this requirement.

#### H2

- H2 is used for unit tests and local development. It closely resembles standard SQL but has minor
  differences. For upsert, H2 supports `MERGE INTO` with either `USING dual` (simple cases) or
  `USING (SELECT … CAST(…)) source` (when explicit type casts are needed for correct type
  inference). See `SequenceFlowMapper.xml` for the `USING dual` pattern and
  `PersistentWebSessionMapper.xml` for the `USING (SELECT … CAST(…)) source` pattern.
- When adding new SQL functions, verify H2 compatibility first since test coverage depends on it.

