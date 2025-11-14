# Custom Headers Implementation Summary

## Overview
This document summarizes the implementation of two custom headers features for user tasks:
1. **FEEL expressions for custom header values** - Dynamically evaluate header values at task creation
2. **Custom header filtering** - Search user tasks by custom headers via REST API

## Key Design Decisions

### Elasticsearch/OpenSearch: Flattened Field Type
**Problem**: Cannot use dynamic fields in production (risk of mapping explosion)

**Solution**: Use Elasticsearch's `flattened` field type
- Handles arbitrary key-value pairs without dynamic mapping
- All header values indexed as keywords  
- Simple dot notation queries: `customHeaders.department: "engineering"`
- No custom serializers needed - `Map<String,String>` serializes naturally
- Production-safe with no risk of mapping explosion

### Alternative Approaches Considered
1. ❌ **Nested Array** - Requires custom Jackson serializers, dependency changes
2. ❌ **Object with dynamic:true** - Violates "no dynamic fields" requirement
3. ✅ **Flattened Type** - Simple, production-safe, no code/dependency changes

## Architecture

### Expression Evaluation
1. **Parse Time**: Static values wrapped as FEEL string literals (e.g., `"value"` → `"\"value\""`)
2. **Creation Time**: Expressions evaluated when user task is created
3. **Type Coercion**: All types converted to strings (`123` → `"123"`, `true` → `"true"`)
4. **Null Handling**: Null expressions skip the header (no error)
5. **System Headers**: Filtered in Java code before SQL/search query building

### Storage

| Backend | Format | Schema |
|---------|--------|--------|
| RDBMS | `Map<String,String>` in JSONB column | `CUSTOM_HEADERS JSONB` |
| Elasticsearch | `Map<String,String>` as flattened field | `"customHeaders": {"type": "flattened"}` |
| OpenSearch | `Map<String,String>` as flattened field | `"customHeaders": {"type": "flattened"}` |

## Files Changed

### Core Engine (Expression Parsing & Evaluation) - 3 files

**`zeebe/engine/.../UserTaskProperties.java`**
- Added `taskHeaderExpressions` map to store parsed FEEL expressions
- Updated `wrap()` method to copy expressions

**`zeebe/engine/.../UserTaskTransformer.java`**
- Modified `transformModelTaskHeaders()` to parse custom header values as FEEL expressions
- Static values wrapped as string literals
- Added `Expression` import

**`zeebe/engine/.../BpmnUserTaskBehavior.java`**
- Added `evaluateCustomHeaderExpressions()` method  
- Integrated into `evaluateUserTaskExpressions()` chain
- Updated `createNewUserTask()` to use evaluated headers

### Schema Updates (Elasticsearch/OpenSearch) - 2 files

**`webapps-schema/.../elasticsearch/.../tasklist-task.json`**
- Changed `customHeaders` from `enabled: false` to `type: flattened`

**`webapps-schema/.../opensearch/.../tasklist-task.json`**
- Same changes as Elasticsearch template

### Domain Model & Query Transformation - 6 files

**`search/search-domain/.../HeaderValueFilter.java`** (NEW)
- Domain record for filtering by custom headers

**`search/search-domain/.../UserTaskFilter.java`**
- Added `customHeaderFilters` field

**`search/search-client-query-transformer/.../HeaderValueFilterTransformer.java`** (NEW)
- Transforms filters to flat field queries (`customHeaders.headerName`)

**`search/search-client-query-transformer/.../UserTaskFilterTransformer.java`**
- Added `getCustomHeadersQuery()` method
- Filters out system headers before query building

**`db/rdbms/.../UserTaskMapper.xml`**
- Added SQL for custom header filtering (all 6 RDBMS types)

**`db/rdbms/.../Commons.xml`**
- Added `jsonFieldAccessor` - database-specific JSON field access
- Added `jsonHeaderOperationCondition` - applies filter operations

### Client API - 10 files

**Java Client** (5 new files + 4 modified)
- `HeaderValueFilter.java` - Interface
- `HeaderValueFilterImpl.java` - Implementation
- `HeaderFilterMapper.java` - Maps to REST protocol
- Modified: `UserTaskFilter.java`, `UserTaskFilterImpl.java`, `SearchRequestBuilders.java`

**REST API** (2 new files + 1 modified)
- `headers.yaml` - OpenAPI schema for `HeaderValueFilterProperty`
- Modified: `user-tasks.yaml` - Added `customHeaders` filter
- Modified: `SearchQueryFilterMapper.java` - Transforms REST to domain

### Testing - 2 files

**`zeebe/engine/.../UserTaskTransformerTest.java`**
- Parameterized tests for static and FEEL header expressions
- Tests for multiple headers and edge cases

**`qa/acceptance-tests/.../UserTaskSearchTest.java`**
- End-to-end tests for custom header filtering
- Tests for equals, like, exists operators
- Multi-header AND logic verification

## Example Usage

### BPMN Deployment (FEEL Expressions)
```xml
<zeebe:taskHeaders>
  <zeebe:header key="department" value="engineering" />
  <zeebe:header key="priority" value="=priorityVariable + 1" />
  <zeebe:header key="dueDate" value="=now() + duration(\"P3D\")" />
</zeebe:taskHeaders>
```

### REST API Query (Filtering)
```json
POST /v2/user-tasks/search
{
  "filter": {
    "customHeaders": [
      {
        "name": "department",
        "value": {"$eq": "engineering"}
      },
      {
        "name": "priority",
        "value": {"$like": "high*"}
      }
    ]
  }
}
```

### Java Client API
```java
client.newUserTaskSearchRequest()
  .filter(f -> f
    .customHeaders(List.of(
      h -> h.name("department").value("engineering"),
      h -> h.name("region").value(v -> v.like("EMEA*"))
    ))
  )
  .send()
  .join();
```

## Test Results

✅ All unit tests passing:
- Expression parsing and wrapping
- Expression evaluation with type coercion
- Null handling
- System header filtering

✅ Integration tests verified:
- Custom header filtering (equals, like, exists, in)
- Multiple header AND logic
- System header exclusion

✅ Compilation successful across all modules

## Backward Compatibility

- ✅ Old user tasks remain unchanged (no migration needed)
- ✅ Existing deployments continue to work
- ✅ New feature only applies to tasks created after deployment
- ✅ No API breaking changes

## Performance Considerations

- **Flattened fields**: Optimized for key-value pair storage, no mapping explosion
- **RDBMS**: JSONB/JSON queries use database-native JSON operators  
- **Expression evaluation**: One-time cost at task creation (not query time)
- **System header filtering**: Done in Java before query building (no DB overhead)

## Future Enhancements

- Add support for numeric/boolean custom header types (currently all strings)
- Add aggregations on custom header fields
- Add custom header templates/presets
