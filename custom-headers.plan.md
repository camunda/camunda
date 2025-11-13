# Custom Headers Enhancement for User Tasks

**Target Version**: 8.X.Y (to be determined during implementation)

## Overview

Implement two related features:
1. **FEEL expressions for custom header values** - Allow FEEL expressions in header values when deploying process definitions
2. **Custom header filtering** - Enable filtering user tasks by custom headers (user-defined only) with full string operations across all backends

## Architecture & Critical Concepts

### Data Architecture: Headers vs Variables

**Critical Difference**: Headers use object field queries, NOT child document queries:
- **Variables**: Stored as separate child documents → use `hasChildQuery()` with parent-child relationship
- **Headers**: Stored as `Map<String, String>` directly on entity → use **object field queries** (not nested)

**Storage by Backend**:
- **RDBMS**: JSONB column `CUSTOM_HEADERS` on `USER_TASK` table
- **Elasticsearch**: Object field `customHeaders` (currently `enabled: false` - **requires migration**)
- **OpenSearch**: Object field `customHeaders` (currently `enabled: false` - **requires migration**)

**Timing**:
- **Expression Parsing**: At deployment time (one-time cost)
- **Expression Evaluation**: At task creation time (runtime, with access to process variables)
- **Storage**: Evaluated string values (post-evaluation) are persisted to all backends
- **Old Tasks**: No migration needed - existing tasks with static headers remain unchanged

### System Headers Definition

System headers use prefix: **`io.camunda.zeebe:`** (defined in `Protocol.RESERVED_HEADER_NAME_PREFIX`)

Complete list from `Protocol.java`:
- `io.camunda.zeebe:action`
- `io.camunda.zeebe:assignee`
- `io.camunda.zeebe:candidateGroups`
- `io.camunda.zeebe:candidateUsers`
- `io.camunda.zeebe:changedAttributes`
- `io.camunda.zeebe:dueDate`
- `io.camunda.zeebe:followUpDate`
- `io.camunda.zeebe:formKey`
- `io.camunda.zeebe:userTaskKey`
- `io.camunda.zeebe:priority`

**Filtering Exclusion**: System headers already have dedicated filter fields (assignee, dueDate, etc.). Excluding them from customHeaders filter prevents confusion and maintains API consistency. System header filtering happens **in Java code before query building** (UserTaskFilterTransformer line ~320), not in SQL/ES queries.

### Expression Evaluation Specification

**Variable Scope**: Process instance scope (same as assignee/dueDate expressions) - variables available at task activation time

**Type Coercion Rules**:
| Input Type | Output String | Example |
|------------|---------------|---------|
| String | As-is | `"hello"` → `"hello"` |
| Number | `toString()` | `123` → `"123"` |
| Boolean | `toString()` | `true` → `"true"` |
| Object/List | **Error - Fail task creation** | Fail with message below |
| Null | **Skip header** | Not added to map |

**Complex Type Handling**: Headers must evaluate to String, Number, or Boolean. If expression returns Object/List/Array → **fail task creation** with error:

```
"Custom header '{headerName}' must evaluate to String, Number, or Boolean. Got: {actualType}"
```

Rationale: Prevents confusing JSON-string values; maintains header simplicity as key-value pairs.

**Error Handling**: Expression evaluation failure → **Task creation fails** with `EXPRESSION_EVALUATION_FAILURE` incident (consistent with assignee failures)

**Static Value Wrapping**: During parsing, static string values are wrapped as FEEL string literals to ensure they're treated as strings, not variable references:

```java
// For static value "department-A":
final var assigneeExpression = expressionLanguage.parseExpression(value);
if (assigneeExpression.isStatic()) {
  // Wrap as string literal (same pattern as assignee, line 165-168)
  userTaskProperties.setCustomHeader(key,
    expressionLanguage.parseExpression(
      ExpressionTransformer.asFeelExpressionString(
        ExpressionTransformer.asStringLiteral(value))));
}
// Result: "department-A" → "\"department-A\"" (FEEL string literal)
```

### Supported Filter Operations

All backends support these operations:

| Operator  | RDBMS | ES | OS |    Description     |
|-----------|-------|----|----|--------------------|
| `$eq`     | ✓     | ✓  | ✓  | Exact match        |
| `$neq`    | ✓     | ✓  | ✓  | Not equal          |
| `$like`   | ✓     | ✓  | ✓  | Wildcard (use `*`) |
| `$exists` | ✓     | ✓  | ✓  | Header name exists |
| `$in`     | ✓     | ✓  | ✓  | Value in list      |

Backend-specific implementations:
- **RDBMS**: All 6 databases supported with database-specific JSON syntax:
- PostgreSQL: JSONB operators (`->>`, `@>`, `?`)
- MySQL/MariaDB: JSON functions (`JSON_EXTRACT`, `JSON_UNQUOTE`)
- H2: JSON functions (`JSON_VALUE`)
- SQL Server: JSON functions (`JSON_VALUE`)
- Oracle: JSON functions (`JSON_VALUE`)
- **Elasticsearch**: Object field queries with `term`/`match`/`wildcard`
- **OpenSearch**: Same as Elasticsearch

### Performance Limits

**Soft Limit**: ~50 custom headers per task recommended for optimal performance

**Enforcement**: This is a **documented recommendation**, not enforced limit

**Behavior if exceeded**:
- No hard error
- Potential performance degradation on queries with many header filters
- Task creation still succeeds
- Search performance may degrade with 100+ headers per task

**Future consideration**: Add deployment-time warning (not error) if > 50 headers detected

## Implementation Plan

### Part 1: FEEL Expressions for Custom Header Values

#### 1.1 Model Layer - Expression Storage

**File**: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/element/UserTaskProperties.java`

Add fields:

```java
private Map<String, Expression> taskHeaderExpressions = Map.of();
```

Add getters/setters and update `wrap()` method to copy expressions.

#### 1.2 Transformer - Parse Header Values

**File**: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/transformer/UserTaskTransformer.java`

In `collectModelTaskHeaders()`:
- Parse each header value: `expressionLanguage.parseExpression(value)`
- If static → wrap as string literal (see code example in Architecture section above)
- Store in `taskHeaderExpressions` map
- Keep original `taskHeaders` for backward compatibility

#### 1.3 Runtime Evaluation

**File**: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnUserTaskBehavior.java`

Add method:

```java
private Either<Failure, Map<String, String>> evaluateCustomHeaderExpressions(
    Map<String, Expression> headerExpressions, long scopeKey) {
  
  final var evaluatedHeaders = new HashMap<String, String>();
  
  for (var entry : headerExpressions.entrySet()) {
    final var headerName = entry.getKey();
    final var expression = entry.getValue();
    
    // Evaluate expression
    final var result = expressionBehavior.evaluateAnyExpression(expression, scopeKey);
    
    if (result.isLeft()) {
      return Either.left(result.getLeft()); // Propagate failure
    }
    
    final var value = result.get();
    
    // Handle null - skip header
    if (value == null) {
      continue;
    }
    
    // Type coercion
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      evaluatedHeaders.put(headerName, value.toString());
    } else {
      // Fail for complex types
      return Either.left(new Failure(
        "Custom header '" + headerName + "' must evaluate to String, Number, or Boolean. Got: " 
        + value.getClass().getSimpleName()));
    }
  }
  
  return Either.right(evaluatedHeaders);
}
```

Update `evaluateUserTaskExpressions()` (line 90-126):
- Add chain: `.flatMap(p -> evaluateCustomHeaderExpressions(...).map(p::customHeaders))`

Update `createNewUserTask()` (line 135):
- Use `userTaskProperties.getCustomHeaders()` instead of `element.getUserTaskProperties().getTaskHeaders()`

#### 1.4 Tests

**File**: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/bpmn/activity/CamundaUserTaskTest.java`

Add tests:
- `shouldCreateUserTaskWithExpressionInCustomHeader()` - FEEL expression
- `shouldCreateUserTaskWithNumericExpressionInHeader()` - Type coercion to string
- `shouldCreateUserTaskWithBooleanExpressionInHeader()` - Boolean to string
- `shouldFailTaskCreationWhenHeaderExpressionReturnsObject()` - Complex type error
- `shouldSkipHeaderWhenExpressionEvaluatesToNull()` - Null handling
- `shouldCreateUserTaskWithMixedStaticAndExpressionHeaders()` - Mixed
- `shouldFailTaskCreationWhenHeaderExpressionInvalid()` - Syntax error

### Part 2: Schema Template Updates for Elasticsearch/OpenSearch

**CRITICAL**: Current schema has `customHeaders.enabled: false` - headers are NOT searchable!

**Important**: Only template updates needed - **NO migration of existing indices required**. New user tasks (created after deployment) will be indexed and searchable; old tasks remain unchanged. This aligns with 8.8+ policy to avoid schema migrations.

**Schema Type Decision**: Use `object` type with `enabled: true`, NOT `nested` type.

**Rationale**:
- Headers are simple key-value pairs (Map<String, String>), not arrays of objects
- `object` type is more performant for this use case
- `nested` type is for arrays of objects (unnecessary complexity here)
- Query syntax simpler: `term: {"customHeaders.headerName": "value"}`

#### 2.1 Update Elasticsearch Template

**File**: `webapps-schema/src/main/resources/schema/elasticsearch/create/template/tasklist-task.json`

Change (around line 94-130):

```json
"customHeaders": {
  "type": "object",
  "enabled": false
}
```

To:

```json
"customHeaders": {
  "type": "object",
  "enabled": true,
  "dynamic": true
}
```

#### 2.2 Update OpenSearch Template

**File**: `webapps-schema/src/main/resources/schema/opensearch/create/template/tasklist-task.json`

Apply same changes as Elasticsearch (around line 93-129).

**Note**: No migration scripts needed - these template changes only affect NEW indices/tasks created after deployment. Existing tasks remain unchanged and unsearchable by custom headers, which aligns with the requirement that only new tasks should use the new functionality.

### Part 3: Custom Header Filtering - Domain & API

#### 3.1 Domain Model

**File**: `search/search-domain/src/main/java/io/camunda/search/filter/HeaderValueFilter.java` (NEW)

```java
public record HeaderValueFilter(String name, List<UntypedOperation> valueOperations) implements FilterBase {
  public static final class Builder implements ObjectBuilder<HeaderValueFilter>, ListBuilder<HeaderValueFilter> {
    // Pattern: exactly like VariableValueFilter.Builder
  }
}
```

#### 3.2 Update UserTaskFilter

**File**: `search/search-domain/src/main/java/io/camunda/search/filter/UserTaskFilter.java`

Add field (line 41):

```java
List<HeaderValueFilter> customHeaderFilters,
```

Update constructor and Builder methods.

#### 3.3 REST API Schema

**File**: `zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml`

In `UserTaskFilter` (after line 421):

```yaml
customHeaders:
  type: array
  description: |
    Filter by custom headers (user-defined headers only).
    System headers (prefixed with 'io.camunda.zeebe:') are excluded.
  items:
    $ref: 'headers.yaml#/components/schemas/HeaderValueFilterProperty'
```

**Note**: UserTaskResult schema (line 505-509) already includes `customHeaders` in response - no change needed.

**File**: `zeebe/gateway-protocol/src/main/proto/v2/headers.yaml` (NEW)

```yaml
components:
  schemas:
    HeaderValueFilterProperty:
      type: object
      required: [name]
      properties:
        name:
          type: string
          description: The custom header name (user-defined headers only)
        value:
          $ref: 'filters.yaml#/components/schemas/StringFilterProperty'
```

#### 3.4 TypeScript/Zod Schemas

**File**: `client-components/packages/camunda-api-zod-schemas/src/schemas/user-tasks.ts`

Add to UserTaskFilter schema:

```typescript
customHeaders: z.array(HeaderValueFilterProperty).optional()
```

Create HeaderValueFilterProperty schema similar to VariableValueFilterProperty pattern.

#### 3.5 Request Mapping

**File**: `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/mapper/search/SearchQueryFilterMapper.java`

Add method (similar to `toVariableValueFilters` pattern at line 824-877):

```java
private static List<HeaderValueFilter> toHeaderValueFilters(final List<Map<String, Object>> filters) {
  // Parse name and value operations
  // Return List<HeaderValueFilter>
}
```

Update `toUserTaskFilter()` to map customHeaders field.

### Part 4: Query Transformation - All Backends

#### 4.1 Header Value Filter Transformer

**File**: `search/search-client-query-transformer/src/main/java/io/camunda/search/clients/transformers/filter/HeaderValueFilterTransformer.java` (NEW)

```java
public final class HeaderValueFilterTransformer implements FilterTransformer<HeaderValueFilter> {
  
  @Override
  public SearchQuery apply(HeaderValueFilter filter) {
    return toSearchQuery(filter, "customHeaders");
  }
  
  public SearchQuery toSearchQuery(HeaderValueFilter filter, String fieldPrefix) {
    // Build query for: fieldPrefix + "." + filter.name()
    // Apply string operations to field value
    // Use standard field queries (NOT hasChildQuery)
  }
}
```

**Note**: Implements standard `FilterTransformer<HeaderValueFilter>` interface with `apply()` method. Helper method `toSearchQuery(filter, fieldPrefix)` allows field prefix customization internally.

#### 4.2 Update UserTaskFilterTransformer

**File**: `search/search-client-query-transformer/src/main/java/io/camunda/search/clients/transformers/filter/UserTaskFilterTransformer.java`

Add method (after line 185):

```java
private SearchQuery getCustomHeadersQuery(List<HeaderValueFilter> headerFilters) {
  if (headerFilters == null || headerFilters.isEmpty()) return null;
  
  // IMPORTANT: Filter out system headers BEFORE building queries
  // This is where system header exclusion happens (not in SQL/ES queries)
  var userHeaderFilters = headerFilters.stream()
    .filter(h -> !h.name().startsWith(Protocol.RESERVED_HEADER_NAME_PREFIX))
    .collect(toList());
  
  if (userHeaderFilters.isEmpty()) return null;
  
  var transformer = transformers.getFilterTransformer(HeaderValueFilter.class);
  var queries = userHeaderFilters.stream()
    .map(transformer::apply)
    .collect(toList());
  
  return and(queries);
}
```

In `toSearchQuery()` (line 77):

```java
ofNullable(getCustomHeadersQuery(filter.customHeaderFilters())).ifPresent(queries::add);
```

#### 4.3 Elasticsearch Implementation

**File**: `search/search-client-elasticsearch/` - Update query builders

Query structure for `object` type (NOT `nested`):

Single header filter:

```json
{
  "term": {
    "customHeaders.departmentName": "engineering"
  }
}
```

Wildcard:

```json
{
  "wildcard": {
    "customHeaders.departmentName": "*engineer*"
  }
}
```

Multiple headers with AND logic:

```json
{
  "bool": {
    "must": [
      {"term": {"customHeaders.department": "engineering"}},
      {"term": {"customHeaders.region": "EMEA"}}
    ]
  }
}
```

**Note**: Simple object field queries, NOT nested queries. Much simpler than variables.

#### 4.4 OpenSearch Implementation

**File**: `search/search-client-opensearch/` (similar structure to Elasticsearch)

Implement same query builders as Elasticsearch.

#### 4.5 RDBMS Implementation

**File**: `db/rdbms/src/main/java/io/camunda/db/rdbms/read/domain/UserTaskDbQuery.java`

Add field:

```java
private List<HeaderValueFilter> customHeaderFilters;
```

**File**: `db/rdbms/src/main/resources/mapper/UserTaskMapper.xml`

Add query fragment (after line 294):

**CRITICAL**: Must support all 6 RDBMS types (PostgreSQL, MySQL, MariaDB, H2, Oracle, SQL Server)

```xml
<!-- Custom Headers Filter -->
<!-- Note: System headers already filtered out in Java code (UserTaskFilterTransformer) -->
<!-- This SQL only queries the user-defined headers that passed validation -->
<if test="filter.customHeaderFilters != null and !filter.customHeaderFilters.isEmpty()">
  <foreach collection="filter.customHeaderFilters" item="headerFilter">
    AND (
      <choose>
        <!-- PostgreSQL: JSONB operators -->
        <when test="_databaseId == 'postgres'">
          CUSTOM_HEADERS ->> #{headerFilter.name} IS NOT NULL
          <if test="headerFilter.valueOperations != null and !headerFilter.valueOperations.isEmpty()">
            <foreach collection="headerFilter.valueOperations" item="operation">
              AND
              <choose>
                <when test="operation.operator == 'EQ'">
                  CUSTOM_HEADERS ->> #{headerFilter.name} = #{operation.value}
                </when>
                <when test="operation.operator == 'NEQ'">
                  CUSTOM_HEADERS ->> #{headerFilter.name} != #{operation.value}
                </when>
                <when test="operation.operator == 'LIKE'">
                  CUSTOM_HEADERS ->> #{headerFilter.name} LIKE #{operation.value}
                </when>
                <when test="operation.operator == 'IN'">
                  CUSTOM_HEADERS ->> #{headerFilter.name} IN
                  <foreach item="val" collection="operation.values" open="(" separator="," close=")">
                    #{val}
                  </foreach>
                </when>
                <when test="operation.operator == 'EXISTS'">
                  <!-- Already checked above with IS NOT NULL -->
                </when>
              </choose>
            </foreach>
          </if>
        </when>
        
        <!-- MySQL: JSON functions -->
        <when test="_databaseId == 'mysql'">
          JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) IS NOT NULL
          <if test="headerFilter.valueOperations != null and !headerFilter.valueOperations.isEmpty()">
            <foreach collection="headerFilter.valueOperations" item="operation">
              AND
              <choose>
                <when test="operation.operator == 'EQ'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) = #{operation.value}
                </when>
                <when test="operation.operator == 'NEQ'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) != #{operation.value}
                </when>
                <when test="operation.operator == 'LIKE'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) LIKE #{operation.value}
                </when>
                <when test="operation.operator == 'IN'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) IN
                  <foreach item="val" collection="operation.values" open="(" separator="," close=")">
                    #{val}
                  </foreach>
                </when>
                <when test="operation.operator == 'EXISTS'">
                  <!-- Already checked above with IS NOT NULL -->
                </when>
              </choose>
            </foreach>
          </if>
        </when>
        
        <!-- MariaDB: JSON functions (similar to MySQL) -->
        <when test="_databaseId == 'mariadb'">
          JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) IS NOT NULL
          <if test="headerFilter.valueOperations != null and !headerFilter.valueOperations.isEmpty()">
            <foreach collection="headerFilter.valueOperations" item="operation">
              AND
              <choose>
                <when test="operation.operator == 'EQ'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) = #{operation.value}
                </when>
                <when test="operation.operator == 'NEQ'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) != #{operation.value}
                </when>
                <when test="operation.operator == 'LIKE'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) LIKE #{operation.value}
                </when>
                <when test="operation.operator == 'IN'">
                  JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name}))) IN
                  <foreach item="val" collection="operation.values" open="(" separator="," close=")">
                    #{val}
                  </foreach>
                </when>
                <when test="operation.operator == 'EXISTS'">
                  <!-- Already checked above with IS NOT NULL -->
                </when>
              </choose>
            </foreach>
          </if>
        </when>
        
        <!-- H2: JSON functions (H2 2.0+) -->
        <when test="_databaseId == 'h2'">
          JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) IS NOT NULL
          <if test="headerFilter.valueOperations != null and !headerFilter.valueOperations.isEmpty()">
            <foreach collection="headerFilter.valueOperations" item="operation">
              AND
              <choose>
                <when test="operation.operator == 'EQ'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) = #{operation.value}
                </when>
                <when test="operation.operator == 'NEQ'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) != #{operation.value}
                </when>
                <when test="operation.operator == 'LIKE'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) LIKE #{operation.value}
                </when>
                <when test="operation.operator == 'IN'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) IN
                  <foreach item="val" collection="operation.values" open="(" separator="," close=")">
                    #{val}
                  </foreach>
                </when>
                <when test="operation.operator == 'EXISTS'">
                  <!-- Already checked above with IS NOT NULL -->
                </when>
              </choose>
            </foreach>
          </if>
        </when>
        
        <!-- SQL Server: JSON_VALUE -->
        <when test="_databaseId == 'sqlserver'">
          JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) IS NOT NULL
          <if test="headerFilter.valueOperations != null and !headerFilter.valueOperations.isEmpty()">
            <foreach collection="headerFilter.valueOperations" item="operation">
              AND
              <choose>
                <when test="operation.operator == 'EQ'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) = #{operation.value}
                </when>
                <when test="operation.operator == 'NEQ'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) != #{operation.value}
                </when>
                <when test="operation.operator == 'LIKE'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) LIKE #{operation.value}
                </when>
                <when test="operation.operator == 'IN'">
                  JSON_VALUE(CUSTOM_HEADERS, CONCAT('$.', #{headerFilter.name})) IN
                  <foreach item="val" collection="operation.values" open="(" separator="," close=")">
                    #{val}
                  </foreach>
                </when>
                <when test="operation.operator == 'EXISTS'">
                  <!-- Already checked above with IS NOT NULL -->
                </when>
              </choose>
            </foreach>
          </if>
        </when>
        
        <!-- Oracle: JSON functions (Oracle 12c+) -->
        <when test="_databaseId == 'oracle'">
          JSON_VALUE(CUSTOM_HEADERS, '$.${headerFilter.name}') IS NOT NULL
          <if test="headerFilter.valueOperations != null and !headerFilter.valueOperations.isEmpty()">
            <foreach collection="headerFilter.valueOperations" item="operation">
              AND
              <choose>
                <when test="operation.operator == 'EQ'">
                  JSON_VALUE(CUSTOM_HEADERS, '$.${headerFilter.name}') = #{operation.value}
                </when>
                <when test="operation.operator == 'NEQ'">
                  JSON_VALUE(CUSTOM_HEADERS, '$.${headerFilter.name}') != #{operation.value}
                </when>
                <when test="operation.operator == 'LIKE'">
                  JSON_VALUE(CUSTOM_HEADERS, '$.${headerFilter.name}') LIKE #{operation.value}
                </when>
                <when test="operation.operator == 'IN'">
                  JSON_VALUE(CUSTOM_HEADERS, '$.${headerFilter.name}') IN
                  <foreach item="val" collection="operation.values" open="(" separator="," close=")">
                    #{val}
                  </foreach>
                </when>
                <when test="operation.operator == 'EXISTS'">
                  <!-- Already checked above with IS NOT NULL -->
                </when>
              </choose>
            </foreach>
          </if>
        </when>
        
        <!-- Default/fallback (should not reach here if MyBatis databaseId configured properly) -->
        <otherwise>
          1=0 <!-- Force failure if database type not supported -->
        </otherwise>
      </choose>
    )
  </foreach>
</if>
```

**Database-Specific Implementation Details**:

|       Database       |                   JSON Query Syntax                   |             Index Support             |                 Notes                 |
|----------------------|-------------------------------------------------------|---------------------------------------|---------------------------------------|
| **PostgreSQL**       | `CUSTOM_HEADERS ->> 'key'`                            | GIN index on JSONB column             | Best performance, native JSONB type   |
| **MySQL 8.0+**       | `JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, '$.key'))` | Functional indexes or virtual columns | Use JSON_UNQUOTE to remove quotes     |
| **MariaDB 10.2.7+**  | `JSON_UNQUOTE(JSON_EXTRACT(CUSTOM_HEADERS, '$.key'))` | JSON indexes (similar to MySQL)       | Compatible with MySQL JSON syntax     |
| **H2 2.0+**          | `JSON_VALUE(CUSTOM_HEADERS, '$.key')`                 | Limited JSON indexing                 | Primarily for testing, not production |
| **SQL Server 2016+** | `JSON_VALUE(CUSTOM_HEADERS, '$.key')`                 | Computed columns with indexes         | JSON stored as NVARCHAR               |
| **Oracle 12c+**      | `JSON_VALUE(CUSTOM_HEADERS, '$.key')`                 | Function-based indexes                | Requires Oracle 12c or higher         |

**Testing Requirements**: Integration tests must run against ALL 6 supported databases to verify JSON query syntax compatibility

### Part 5: Client API

#### 5.1 Java Client - Interface

**File**: `clients/java/src/main/java/io/camunda/client/api/search/filter/HeaderValueFilter.java` (NEW)

```java
public interface HeaderValueFilter extends SearchRequestFilter {
  HeaderValueFilter name(String value);
  HeaderValueFilter value(String value);  // Shorthand for $eq
  HeaderValueFilter value(Consumer<StringProperty> stringFilter);  // Advanced operations
}
```

**Pattern**: Matches `VariableValueFilter` interface pattern for consistency.

#### 5.2 Java Client - Implementation

**File**: `clients/java/src/main/java/io/camunda/client/impl/search/filter/HeaderValueFilterImpl.java` (NEW)

Implement interface similar to `VariableValueFilterImpl.java`.

#### 5.3 Update UserTaskFilter

**File**: `clients/java/src/main/java/io/camunda/client/api/search/filter/UserTaskFilter.java`

Add methods:

```java
UserTaskFilter customHeaders(List<HeaderValueFilter> filters);
UserTaskFilter customHeader(Consumer<HeaderValueFilter> fn);  // Single header convenience method
```

**File**: `clients/java/src/main/java/io/camunda/client/impl/search/filter/UserTaskFilterImpl.java`

Implement methods and add field.

### Part 6: Testing

#### 6.1 Expression Evaluation Tests

**File**: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/bpmn/activity/CamundaUserTaskTest.java`

Add comprehensive tests (see Part 1.4 for full list).

#### 6.2 Multi-Backend Filter Tests

**File**: `qa/acceptance-tests/src/test/java/io/camunda/it/client/UserTaskSearchTest.java`

Add tests (run against all 3 backends with test matrix):
- `shouldSearchUserTasksByCustomHeaderExactMatch()`
- `shouldSearchUserTasksByCustomHeaderWithLike()`
- `shouldSearchUserTasksByCustomHeaderExists()`
- `shouldSearchUserTasksByMultipleCustomHeaders()` - AND logic
- `shouldExcludeSystemHeadersFromCustomHeaderFilter()` - Verify exclusion
- `shouldSearchByEvaluatedHeaderValue()` - FEEL expression result
- `shouldSearchWithSpecialCharactersInHeaderValue()` - Edge case

**Test Configuration**: Use parameterized tests or test matrix to run against:
1. RDBMS (All 6 databases: PostgreSQL, MySQL, MariaDB, H2, SQL Server, Oracle)
2. Elasticsearch
3. OpenSearch

#### 6.3 Unit Tests

**File**: `search/search-client-query-transformer/src/test/java/io/camunda/search/clients/transformers/filter/UserTaskFilterTransformerTest.java`

Add tests:
- `shouldTransformCustomHeaderFilter()`
- `shouldExcludeSystemHeadersFromFilter()`
- `shouldHandleMultipleHeaderFilters()`
- `shouldHandleEmptyHeaderFilters()`

### Part 7: Documentation

#### 7.1 BPMN Modeling Guide

**Location**: Camunda docs repository (external)

**File**: `docs/components/modeler/bpmn/user-tasks/` (exact path TBD)

Add section: "Using FEEL Expressions in Custom Headers"
- Examples with static values
- Examples with expressions
- Type coercion behavior
- Error handling

**Action**: File documentation PR in camunda-docs repository

#### 7.2 REST API Documentation & Swagger

**Auto-generated from OpenAPI schema** (`user-tasks.yaml`, `headers.yaml`)

**Swagger UI**:
- **Location**: `http://localhost:8080/swagger-ui.html` (when running locally)
- **Updates automatically**: Changes to YAML files are reflected in Swagger UI on startup
- **No manual steps needed**: OpenAPI spec is read by Spring Boot automatically
- **Testing**: Use "Try it out" feature in Swagger to test custom header filtering

**Published Documentation**: `https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-user-tasks/`

**Example Request in Swagger**:

```json
{
  "filter": {
    "customHeaders": [
      {"name": "department", "value": "engineering"},
      {"name": "priority", "value": {"$gte": "5"}},
      {"name": "region", "value": {"$like": "*EMEA*"}}
    ]
  }
}
```

**Verification Steps**:
1. Start Camunda with changes
2. Navigate to Swagger UI
3. Find `POST /v2/user-tasks/search`
4. Verify `customHeaders` appears in filter schema
5. Test with "Try it out" button

#### 7.3 Java Client Documentation

**JavaDoc** in client interfaces + external developer guide

Example usage:

```java
// Shorthand for exact match
client.newUserTaskQuery()
  .filter(f -> f
    .customHeader(h -> h
      .name("department")
      .value("engineering")))  // Shorthand: equivalent to .value(v -> v.$eq("engineering"))
  .send();

// Advanced filter with operations
client.newUserTaskQuery()
  .filter(f -> f
    .customHeader(h -> h
      .name("department")
      .value("engineering"))
    .customHeader(h -> h
      .name("priority")
      .value(v -> v.$gte("5"))))
  .send();
```

#### 7.4 Release Notes & Changelog

**File**: `CHANGELOG.md` (root of repository)

**Section**: Version 8.X.Y

```markdown
## [8.X.Y] - YYYY-MM-DD

### Added
- Custom headers in user tasks now support FEEL expressions for dynamic values at task creation time
- Added filtering by custom headers in User Task Search API (`/v2/user-tasks/search`)
  - Supports all string operations: $eq, $neq, $like, $exists, $in
  - System headers (prefixed with 'io.camunda.zeebe:') are automatically excluded
- Java client API: New `HeaderValueFilter` for programmatic header filtering

### Changed
- Elasticsearch/OpenSearch: `customHeaders` field mapping in templates updated to `enabled: true` (previously disabled)
  - Only affects NEW user tasks created after deployment
  - No migration of existing indices required
  - Existing tasks remain unchanged

### Migration Notes
- **No schema migrations required** - aligns with 8.8+ policy
- Elasticsearch/OpenSearch: Template updates only affect new tasks
- All 6 supported RDBMS types fully supported: PostgreSQL, MySQL, MariaDB, H2, SQL Server, Oracle
- RDBMS: No database schema changes required - `CUSTOM_HEADERS` column already exists
```

**External Release Announcement**: Add to quarterly release blog post

## Backward Compatibility

- **Existing processes**: Static headers work unchanged (backward compatible)
- **Existing tasks**:
  - Old tasks remain functional with static headers
  - Old tasks will NOT be searchable by custom headers (as intended)
  - Only new tasks created after deployment will support FEEL expressions and filtering
- **API**: `customHeaders` filter is optional (additive change)
- **Storage**: Evaluated headers stored after evaluation; no data migration needed
- **ES/OS Templates**: Only NEW indices/tasks affected by template changes
  - Non-breaking: Existing indices and tasks remain unchanged
  - No automatic migration or reindexing
  - Aligns with 8.8+ policy to avoid schema migrations
- **Client API**: New methods added, existing methods unchanged

## Summary of New/Modified Files

### New Files (14 files)

1. **`search/search-domain/.../HeaderValueFilter.java`** - Domain model for header name + value operations filter (mirrors VariableValueFilter pattern)
2. **`search/search-client-query-transformer/.../HeaderValueFilterTransformer.java`** - Transforms HeaderValueFilter to backend-specific queries (ES/OS/RDBMS)
3. **`clients/java/.../api/.../HeaderValueFilter.java`** - Public Java client API interface for header filtering
4. **`clients/java/.../impl/.../HeaderValueFilterImpl.java`** - Implementation of Java client HeaderValueFilter interface
5. **`zeebe/gateway-protocol/.../headers.yaml`** - OpenAPI schema definitions for header filter (HeaderValueFilterProperty)
   6-14. **Additional test files** - Comprehensive new test classes to cover expression evaluation and custom header filtering across all backends

### Modified Files (15+ files)

1. **`zeebe/engine/.../UserTaskProperties.java`** - Add `taskHeaderExpressions` field to store parsed FEEL expressions
2. **`zeebe/engine/.../UserTaskTransformer.java`** - Parse header values as expressions during deployment; wrap static values as literals
3. **`zeebe/engine/.../BpmnUserTaskBehavior.java`** - Add `evaluateCustomHeaderExpressions()` method; evaluate at runtime with type coercion
4. **`search/search-domain/.../UserTaskFilter.java`** - Add `customHeaderFilters` field to enable header filtering
5. **`search/search-client-query-transformer/.../UserTaskFilterTransformer.java`** - Add `getCustomHeadersQuery()` to filter system headers and build queries
6. **`zeebe/gateway-protocol/.../user-tasks.yaml`** - Add `customHeaders` array property to UserTaskFilter schema
7. **`db/rdbms/.../UserTaskDbQuery.java`** - Add `customHeaderFilters` field for RDBMS query building
8. **`db/rdbms/.../UserTaskMapper.xml`** - Add MyBatis XML fragment for JSONB/JSON header queries supporting all 6 databases (PostgreSQL, MySQL, MariaDB, H2, SQL Server, Oracle)
9. **`clients/java/.../api/.../UserTaskFilter.java`** - Add `customHeaders()` and `customHeader()` methods
10. **`clients/java/.../impl/.../UserTaskFilterImpl.java`** - Implement header filter methods
11. **`webapps-schema/.../tasklist-task.json` (ES)** - Change customHeaders from `enabled: false` to `enabled: true, dynamic: true`
12. **`webapps-schema/.../tasklist-task.json` (OS)** - Change customHeaders from `enabled: false` to `enabled: true, dynamic: true`
13. **`client-components/.../user-tasks.ts` (TypeScript/Zod)** - Add HeaderValueFilterProperty schema for type safety
14. **`zeebe/gateway-rest/.../SearchQueryFilterMapper.java`** - Add `toHeaderValueFilters()` method to parse REST requests
15. **`CHANGELOG.md`** - Document new features and migration notes
16. **Multiple existing test files** - Update/extend for header filtering coverage

**Total Impact**: ~29 files (14 new + 15 modified)

## Key Architectural Decisions Recap

1. **Object field vs Nested field**: Chose `object` for simplicity and performance
2. **System header exclusion**: Filter in Java code, not in queries (consistency)
3. **Complex type handling**: Error on Object/List instead of JSON serialization (simplicity)
4. **Filter pattern**: Reuse VariableValueFilter structure (consistency)
5. **Expression evaluation**: At task creation time (runtime, not deployment) with access to process variables
6. **Multi-RDBMS support**: Use MyBatis databaseId to handle all 6 databases (PostgreSQL, MySQL, MariaDB, H2, SQL Server, Oracle) with database-specific JSON syntax
7. **No old task migration**: Only new tasks created after deployment will use expression evaluation

