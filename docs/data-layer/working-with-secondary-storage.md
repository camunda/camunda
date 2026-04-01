# Working with Secondary Storage: Best Practices & Common Pitfalls

This guide targets contributing engineers who work on the data/search layer of Camunda - covering
Elasticsearch (ES), OpenSearch (OS), and relational databases (RDBMS). It makes performance
characteristics and common failure modes explicit so they are considered during design and
implementation.

> **Scope**
> This document covers the secondary storage layer: ES/OS indices and templates,
> RDBMS tables managed by the RDBMS module, exporters, and the query/aggregation logic that
> reads from them. For the primary (command-side) Zeebe RocksDB layer, refer to the engine
> documentation.

---

## Table of Contents

1. [Performance Considerations & PDP Integration](#1-performance-considerations--pdp-integration)
2. [Schema & Field Type Guidance](#2-schema--field-type-guidance)
3. [Query & Aggregation Best Practices](#3-query--aggregation-best-practices)
4. [Common Pitfalls & Examples](#4-common-pitfalls--examples)
5. [References](#5-references)

---

## 1. Performance Considerations & PDP Integration

### 1.1 Questions to Answer at Define / PDP

Before starting any data-layer-affecting work, answer the following questions in the PDP or solution
design document. Treat these as a checklist - missing answers could be a signal that the design is
not yet ready.

| # |                                                                 Question                                                                  |                                                  Why it matters                                                   |
|---|-------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| 1 | What is the expected data volume? How many documents / rows per index / table, and at what write rate?                                    | Volume drives index sizing, shard strategy, and pagination design.                                                |
| 2 | What is the cardinality of the new/changed fields? (e.g. "one per process instance" vs "one per variable per process instance")           | High-cardinality fields in aggregations or sort clauses are expensive.                                            |
| 3 | How will the query behave under typical **and** worst-case load? (e.g. a user who has 10M process instances and requests the last page)   | Edge cases often expose O(n) scans or deep-pagination problems.                                                   |
| 4 | Does this change introduce new aggregations or significantly change query shapes?                                                         | Aggregations are often the most expensive part of a query.                                                        |
| 5 | How does this change affect indexing / write throughput (including via the Archiver)? (new fields, more documents per event)              | Adding fields or nested objects can slow down the exporter and increase index size.                               |
| 6 | Does the query rely on an existing field index (ES/OS) or database index (RDBMS) that supports the required filter fields and sort order? | Missing index support forces full-index or full-table scans.                                                      |
| 7 | Are there differences in behavior or performance between ES, OS, and RDBMS backends?                                                      | If a query pattern is efficient in ES/OS but expensive in SQL (or vice versa), the design must account for both.  |
| 8 | What is the impact on historical data? (e.g. does adding a new field require a re-index?)                                                 | Re-indexing large indices takes time and can block deployments. We prefer to avoid reindexing and data migrations |

### 1.2 When to Involve the Data Layer Team

Reach out to `#team-data-layer` for review if your answers indicate:

- **New patterns**: The change introduces a query, aggregation, or schema pattern not yet used in
  the codebase.
- **Significant performance impact**: High write rates, large data volumes, or complex aggregations
  on high-cardinality fields.
- **Breaking changes**: Mapping changes requiring re-indexing, or changes affecting existing query
  behavior.

**Work is ready to start when:**

- [ ] All questions in §1.1 have been answered.
- [ ] A performance validation plan exists describing expected performance characteristics and how
  they will be verified.
- [ ] Schema changes have been reviewed against §2 (field type guidance).
- [ ] New queries / aggregations have been reviewed against §3 (query best practices).

> **Note**
> The Data Layer team (`#team-data-layer`) is available to help answer the questions above or
> provide feedback on proposed solutions.

---

## 2. Schema & Field Type Guidance

### 2.1 Choosing Field Types (ES/OS)

|                     Use case                      |              Recommended type              |                                                                                               Notes                                                                                               |
|---------------------------------------------------|--------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Identifiers, enums, state values, IDs             | `keyword`                                  | Enables exact-match filters, aggregations, and sorting.                                                                                                                                           |
| Long integer keys (e.g. process instance key)     | `long` or `keyword`                        | Use `long` when numeric range queries are needed (e.g. `key > X`). Use `keyword` if the field is only used for exact-match filters, aggregations, or sorting - `keyword` is cheaper in that case. |
| Human-readable names used for search/autocomplete | `keyword` (with optional `text` sub-field) | See §2.2 for multi-field guidance.                                                                                                                                                                |
| Free-form text that needs full-text search        | `text`                                     | Cannot be used in aggregations or sort. Expensive. Only use when full-text search is actually needed.                                                                                             |
| Timestamps and dates                              | `date`                                     | Use `date_time` or `epoch_millis` format. Consistent with existing templates. Enables date range queries and date histogram aggregations.                                                         |
| Boolean flags                                     | `boolean`                                  |                                                                                                                                                                                                   |
| Partition, shard, or small integer counters       | `integer`                                  |                                                                                                                                                                                                   |
| Floating-point metrics                            | `double` or `float`                        | Choose precision appropriate to the use case.                                                                                                                                                     |

**Example from `operate-variable` template** (follow this pattern for new fields):

```json
{
  "key": {
    "type": "long"
  },
  "partitionId": {
    "type": "integer"
  },
  "scopeKey": {
    "type": "long"
  },
  "processInstanceKey": {
    "type": "long"
  },
  "name": {
    "type": "keyword"
  },
  "value": {
    "type": "keyword",
    "ignore_above": 8191
  },
  "truncated": {
    "type": "boolean"
  },
  "tenantId": {
    "type": "keyword"
  },
  "fullValue": {
    "type": "keyword"
  }
}
```

**References:**
`webapps-schema/src/main/resources/schema/{elasticsearch|opensearch}/create/template/`

### 2.2 Multi-Fields (keyword + text)

Only add a `text` sub-field when full-text search is actually needed for the field. Adding
unnecessary `text` sub-fields increases index size and indexing time.

```json
// Good: only keyword - for a field used in exact-match filters and aggregations
"bpmnProcessId": {"type": "keyword"}

// Good: keyword primary + text sub-field for a human-readable name that needs search
"processName": {
"type": "keyword",
"normalizer": "case_insensitive",
"fields": {
"search": {"type": "text"}
  }
}

// Bad: text-only for a field that is also used in filters or aggregations
"processName": {"type": "text"}
```

### 2.3 IDs and Keys

- Use `keyword` for string IDs (e.g. `id`, `bpmnProcessId`, `tenantId`).
- For numeric 64-bit keys (e.g. `processInstanceKey`, `key`, `scopeKey`):
  - Use `keyword` when the field is only used for exact-match filters, aggregations, or sorting.
    `keyword` doc values are more compact and do not carry the overhead of numeric field data.
  - Use `long` when the field is also used in numeric range queries (e.g. `key > X`,
    `key BETWEEN a AND b`). If you are unsure, start with `keyword` and change to `long` only when
    a range query is introduced (this is a breaking change requiring a re-index, so decide early).
- Do **not** store keys or IDs as `text` - text fields are analyzed (tokenized), which breaks exact
  matching and prevents aggregations.

### 2.4 High-Cardinality Fields

High-cardinality means the field has many distinct values (e.g. one unique value per process
instance). High-cardinality fields in aggregations are expensive because ES/OS must build large
in-memory data structures.

Rules of thumb:

- **Filter fields**: High cardinality is fine - use `keyword`.
- **Sort fields**: High cardinality is fine - use `keyword` or `long`.
- **Aggregation fields**: Avoid high-cardinality unless you paginate the aggregation using a
  `composite` aggregation with `after_key`, or you know the result set is bounded (e.g. fewer than a
  few thousand distinct values).
- **`top_hits` inside terms aggregation**: Especially dangerous on high-cardinality fields. See §2.2
  and §4.2.

### 2.5 Mapping Compatibility

Changing an existing field's type (e.g. `keyword` → `long`) is a **breaking change** that requires a
re-index. Rules:

- **Never change the type of an existing field** without a migration plan.
- **Never rename a field** in a template without a migration plan.
- **Adding new fields** requires an explicit update to the template JSON files, because all our
  templates use `"dynamic": "strict"`. New fields that are not listed in the mapping will be
  rejected by ES/OS at index time.

### 2.6 Following Existing Template Patterns

Before introducing a new schema pattern, check whether an existing template already solves the
problem. Templates to reference:

|          Template          |                          Location                           |                     Notable patterns                      |
|----------------------------|-------------------------------------------------------------|-----------------------------------------------------------|
| `VariableTemplate`         | `webapps-schema/.../template/VariableTemplate.java`         | `keyword` with `ignore_above` for potentially long values |
| `FlowNodeInstanceTemplate` | `webapps-schema/.../template/FlowNodeInstanceTemplate.java` | Date fields, `long` keys, `keyword` state                 |
| `ListViewTemplate`         | `webapps-schema/.../template/ListViewTemplate.java`         | `join` relation type, `eager_global_ordinals`             |
| `TaskTemplate`             | `webapps-schema/.../template/TaskTemplate.java`             | Combined filter + sort fields                             |
| `JobTemplate`              | `webapps-schema/.../template/JobTemplate.java`              | Boolean flags alongside keyword fields                    |

### 2.7 Controlling Which Fields Are Written to Secondary Storage (Jackson Mixins)

When the exporter serializes a DTO to JSON before indexing it in ES/OS, every field on the object is
written by default. If a DTO contains fields that are needed by other consumers(e.g. the engine,
gRPC API, or in-memory processing) but are not needed in the ES/OS index, those fields waste disk
space and increase index size with no benefit.

**Pattern: Jackson Mixins**

The ES and OS exporters use Jackson Mixins to suppress specific fields from serialization without
modifying the source DTO or protocol buffer class. The mixin is defined in the exporter and
registered on the `ObjectMapper` used for indexing:

```java
// Mixin definition - kept in the exporter, not in the DTO
@JsonIgnoreProperties({"authorizations", "agent"})
private static final class RecordSequenceMixin {

}
```

```java
// Registration - once, during ObjectMapper initialization
new ObjectMapper().addMixIn(Record.class, RecordSequenceMixin.class);
```

For per-method ignores, use an interface-based mixin with `@JsonIgnore`:

```java
public interface TerminateInstructionsMixin {

  @JsonIgnore
  String getElementId();
}
```

**When to use this pattern:**

- A field exists in the DTO because it is needed elsewhere (e.g. in-memory logic, API responses, or
  other services), but is never queried, filtered, or sorted in the index.
- A field is redundant in the index (e.g. it can always be derived from another stored field).
- A field is large (e.g. a nested list) and is not consumed from the index.

**When not to use this pattern:**

- If the field is needed for any query, filter, aggregation, or sort on the index, it must be
  present in the stored document.
- Do not use mixins to silently drop data that index consumers expect to be present.

**Examples in this codebase (`BulkIndexRequest.java` in each exporter):**

|             Mixin class             |                                 Applied to                                 |              Fields suppressed               |
|-------------------------------------|----------------------------------------------------------------------------|----------------------------------------------|
| `RecordSequenceMixin`               | `Record`                                                                   | `authorizations`, `agent`                    |
| `IgnoreRootProcessInstanceKeyMixin` | `JobRecordValue`, `IncidentRecordValue`, `VariableRecordValue`, and others | `rootProcessInstanceKey`                     |
| `ProcessInstanceModificationMixin`  | `ProcessInstanceModificationRecordValue`                                   | `moveInstructions`, `rootProcessInstanceKey` |

Both the Elasticsearch exporter (`zeebe/exporters/elasticsearch-exporter/.../BulkIndexRequest.java`)
and the OpenSearch exporter (`zeebe/exporters/opensearch-exporter/.../BulkIndexRequest.java`) define
their own mixin sets; keep them in sync when adding a new mixin.

---

## 3. Query & Aggregation Best Practices

### 3.1 Prefer Filters Over Full-Text Queries

Always use `filter` context (not `must`/`should` for structured data) when you do not need relevance
scoring. Filter queries are cached and significantly cheaper.

```json
// Good: structured data in filter context
{
  "query": {
    "bool": {
      "filter": [
        {
          "term": {
            "state": "ACTIVE"
          }
        },
        {
          "term": {
            "tenantId": "my-tenant"
          }
        },
        {
          "range": {
            "startDate": {
              "gte": "2024-01-01"
            }
          }
        }
      ]
    }
  }
}

// Bad: same structured data in must context (scores are computed but never used)
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "state": "ACTIVE"
          }
        },
        {
          "term": {
            "tenantId": "my-tenant"
          }
        }
      ]
    }
  }
}
```

### 3.2 Prefer Exact Matches Over Wildcards

Wildcard queries (`*foo*`) scan the entire term dictionary for matching terms. They are especially
expensive when the wildcard is at the beginning of the pattern (leading wildcard).

```json
// Bad: leading wildcard on a potentially large field
{
  "wildcard": {
    "processName": "*payment*"
  }
}

// Better (if exact prefix is sufficient): prefix query on keyword
{
  "prefix": {
    "processName": "payment"
  }
}
```

If users must be able to perform arbitrary substring searches, consider:

- Imposing a minimum query length constraint at the API level.
- Never passing unconstrained user input directly to a wildcard or regex query.

### 3.3 Pagination

Use cursor-based (search-after) pagination for large result sets. Avoid deep `from`/`size` offset
pagination - ES/OS must fetch and discard `from` documents on every shard.

```json
// Bad: deep offset pagination (expensive at high page numbers)
{
  "from": 10000,
  "size": 20
}

// Good: cursor-based pagination with search_after
{
  "size": 20,
  "sort": [
    {
      "startDate": "desc"
    },
    {
      "key": "asc"
    }
  ],
  "search_after": [
    "2024-06-01T12:00:00.000Z",
    12345678
  ]
}
```

For RDBMS, use keyset pagination (WHERE clause on the last-seen key) instead of `OFFSET`.

```sql
-- Bad: deep offset pagination (full table scan to skip rows)
SELECT *
FROM process_instance
WHERE state = 'ACTIVE'
ORDER BY start_date DESC, key ASC
  LIMIT 20
OFFSET 10000;

-- Good: keyset pagination (skips rows using an index seek)
SELECT *
FROM process_instance
WHERE state = 'ACTIVE'
  AND (start_date, key) < ('2024-06-01T12:00:00', 99999)
ORDER BY start_date DESC, key ASC
  LIMIT 20;
```

### 3.4 Aggregations: When and How

Aggregations are powerful but expensive. Follow these guidelines:

**Do:**

- Use aggregations when you need counts, stats, or grouping over a **bounded** set of values.
- Use `composite` aggregation with `after_key` to paginate over large aggregation results.
- Use `filter` aggregations to pre-filter before aggregating.
- Specify a reasonable `size` limit in `terms` aggregations.

**Do not:**

- Run `terms` aggregations on unbounded high-cardinality fields (e.g. one per process instance)
  without a size limit and without first verifying the cardinality is bounded (e.g. fewer than ~
  10,000 distinct values).
- Use `top_hits` inside a `terms` aggregation when you need full documents for each bucket - see
  §3.5.
- Nest aggregations more than 2–3 levels deep without performance testing.

#### 3.4.1 ES/OS vs RDBMS differences

|         Pattern          |              ES/OS              |                        RDBMS                        |
|--------------------------|---------------------------------|-----------------------------------------------------|
| Count of distinct values | `cardinality` agg (approximate) | `COUNT(DISTINCT col)` (exact)                       |
| Top N groups by count    | `terms` agg                     | `GROUP BY col ORDER BY count DESC LIMIT n`          |
| Date histogram           | `date_histogram` agg            | `DATE_TRUNC(interval, col) GROUP BY ...`            |
| Nested aggregations      | Supported (costly)              | `JOIN` + `GROUP BY` (can be efficient with indexes) |

### 3.5 Avoiding `top_hits` Misuse

`top_hits` fetches full documents for each bucket of a parent aggregation. On large datasets it is
one of the most expensive operations in ES/OS.

**When `top_hits` is acceptable:**

- The parent `terms` aggregation has a small, known-bounded `size` (e.g. ≤ 20).
- The total dataset being aggregated is small (e.g. filtered to a single process instance).

**Alternatives to `top_hits` for large result sets:**

1. **Terms + separate queries**: Get the bucket keys first, then fetch documents by key in a
   separate query. This avoids loading full documents during the aggregation phase.
2. **Pre-aggregated structures**: Store the aggregated value at write time (in the exporter) instead
   of computing it at query time.
3. **Composite pagination**: Use `composite` aggregation to page through buckets without loading
   full documents.

---

## 4. Common Pitfalls & Examples

### 4.1 New Aggregation on a High-Cardinality Field

**Problem:** Adding a `terms` aggregation on a field like `processInstanceKey` (one unique value per
process instance) causes ES/OS to allocate memory proportional to the number of distinct values.

```json
// Bad: cardinality of processInstanceKey can be in the millions
{
  "aggs": {
    "instances": {
      "terms": {
        "field": "processInstanceKey",
        "size": 10000
      }
    }
  }
}
```

**Better:** If you need to enumerate process instance keys, use a `composite` aggregation with
pagination, or re-think whether the aggregation is necessary at all.

```json
// Better: paginated composite aggregation
{
  "aggs": {
    "instances": {
      "composite": {
        "size": 100,
        "sources": [
          {
            "key": {
              "terms": {
                "field": "processInstanceKey"
              }
            }
          }
        ],
        "after": {
          "key": 12345678
        }
      }
    }
  }
}
```

**Rule:** Any new aggregation on a high-cardinality field requires careful consideration and
performance testing before merging.

---

### 4.2 Using `top_hits` Where Only Counts Are Needed

**Problem:** `top_hits` is used to retrieve documents when the caller only needs a count or a
summary statistic.

```json
// Bad: top_hits returns full documents - unnecessary I/O and memory
{
  "aggs": {
    "by_process": {
      "terms": {
        "field": "bpmnProcessId",
        "size": 50
      },
      "aggs": {
        "latest": {
          "top_hits": {
            "size": 1,
            "_source": true
          }
        }
      }
    }
  }
}
```

**Better:** Use `value_count` or `max`/`min` aggregations for stats, or fetch only the needed
fields.

```json
// Better: fetch only the needed field, not the full document
{
  "aggs": {
    "by_process": {
      "terms": {
        "field": "bpmnProcessId",
        "size": 50
      },
      "aggs": {
        "latest_start": {
          "max": {
            "field": "startDate"
          }
        }
      }
    }
  }
}
```

---

### 4.3 Wildcard or Regex Queries on User-Controlled Input

**Problem:** A user-provided search string is passed directly to a `wildcard` or `regexp` query
without sanitization or constraints. This allows users to trigger expensive full-index scans.

```java
// Bad: user input passed directly to wildcard query
QueryBuilders.wildcardQuery("processName*" + userInput + "*");
```

**Better:** Sanitize inputs, enforce a minimum query length, and prefer a `prefix` query on a
`keyword` field. If you must support substring search, impose strong input constraints at the API
level first.

```java
// Better: guard against short or empty input, use prefix query
if (userInput == null || userInput.length() < 3){
  // Return empty results or surface a validation error to the caller.
  return Collections.emptyList();
}
QueryBuilders.prefixQuery("processName",userInput);
```

---

### 4.4 Aggregating or Sorting on a `text` Field

**Problem:** ES/OS throws an error (or returns wrong results) when you attempt to sort or aggregate
on a `text` field, because text fields are analyzed and their values are not stored as-is in the doc
values.

```json
// Bad: errorMessage is of type "text" in many templates
{
  "sort": [
    {
      "errorMessage": "asc"
    }
  ]
}

// Also bad: terms aggregation on a text field
{
  "aggs": {
    "errors": {
      "terms": {
        "field": "errorMessage"
      }
    }
  }
}
```

**Better:** Use a `keyword` sub-field for sorting/aggregation, or change the field type to
`keyword` (with `ignore_above` for long values).

```json
// Better: add a keyword sub-field to a text field
"errorMessage": {
"type": "text",
"fields": {
"keyword": {"type": "keyword", "ignore_above": 256}
  }
}

// Then sort/aggregate on the sub-field
{"sort": [{"errorMessage.keyword": "asc"}]}
```

---

### 4.5 Over-Fetching Large Documents

**Problem:** A query fetches the full `_source` when only a few fields are needed. For documents
with large variable values or error messages, this wastes I/O and heap.

```java
// Bad: fetches entire document source
SearchRequest request = new SearchRequest.Builder()
    .index("operate-variable-*")
    .query(q -> q.term(t -> t.field("processInstanceKey").value(key)))
    .build();
```

**Better:** Use `_source` filtering or `docvalue_fields` to fetch only the needed fields.

```java
// Better: project only the needed fields
SearchRequest request = new SearchRequest.Builder()
    .index("operate-variable-*")
    .query(q -> q.term(t -> t.field("processInstanceKey").value(key)))
    .source(s -> s.filter(f -> f.includes("name", "value", "scopeKey")))
    .build();
```

---

### 4.6 Breaking Mapping Compatibility

**Problem:** A developer changes the type of existing field (e.g. from `keyword` to `integer`) and
deploys without a migration plan. The old index template no longer matches the existing data,
causing indexing failures or silent data loss.

**Better:** Treat mapping changes as a versioned schema migration:

1. Add new fields - never remove or retype existing ones in-place.
2. For type changes, introduce a new field (e.g. `processVersionInt`) alongside the old one,
   backfill via re-index, then in a later release:

- Mark the old field as `@Deprecated` in the Java template descriptor class so callers are warned
  at compile time.
- Remove the old field once all consumers have been migrated and the backfill is complete.

3. Only bump the template version in the descriptor class (e.g. `getVersion()` in
   `FlowNodeInstanceTemplate`) when you intentionally create a new index generation (for example,
   for a breaking mapping change that requires reindexing/migration). For additive mapping changes (
   adding new fields without retyping/removing existing ones), keep the version unchanged and rely
   on the schema-manager to update existing templates/mappings in place via `putMapping` on the
   descriptor alias, so existing indices receive the new fields without creating empty generations.

---

### 4.7 Missing Index on a New Filter or Sort Column (RDBMS)

**Problem:** A new filter parameter is added to an RDBMS query, but no index covers the
corresponding column. This causes a full table scan that degrades under load.

```sql
-- Bad: no index on history_cleanup_date
SELECT *
FROM process_instance
WHERE history_cleanup_date < NOW()
ORDER BY history_cleanup_date LIMIT 1000;
```

**Better:** Always add a Liquibase migration that creates an index for any column used in `WHERE`,
`ORDER BY`, or `JOIN` conditions. Run `EXPLAIN ANALYZE` to verify the plan uses the index.

```xml
<!-- In the Liquibase changeset -->
<createIndex tableName="process_instance" indexName="idx_pi_history_cleanup_date">
  <column name="history_cleanup_date"/>
</createIndex>
```

---

## 5. References

|             Resource              |                                      Location                                       |
|-----------------------------------|-------------------------------------------------------------------------------------|
| RDBMS module documentation        | `docs/monorepo-docs/architecture/components/rdbms/rdbms_architecture_docs.md`       |
| Testing strategy                  | `docs/testing.md`                                                                   |
| Reliability testing (load tests)  | `docs/testing/reliability-testing.md`                                               |
| ES/OS index templates (source)    | `webapps-schema/src/main/resources/schema/`                                         |
| ES/OS template descriptors (Java) | `webapps-schema/src/main/java/io/camunda/webapps/schema/descriptors/template/`      |
| Camunda exporter batch request    | `zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/store/`         |
| ES `_profile` API                 | https://www.elastic.co/guide/en/elasticsearch/reference/current/search-profile.html |
| OS `_profile` API                 | https://opensearch.org/docs/latest/api-reference/search/                            |

