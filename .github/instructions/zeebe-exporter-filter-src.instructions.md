```yaml
---
applyTo: "zeebe/exporter-filter/src/**"
---
```
# Zeebe Exporter Filter Module

## Purpose

This module provides a backend-agnostic record filtering framework for Zeebe exporters. It determines which engine records (from the commit log) should be exported to secondary storage. Both the Elasticsearch and OpenSearch exporters depend on this module and use `DefaultRecordFilter` as their `Context.RecordFilter` implementation, set via `context.setFilter()` during exporter configuration.

## Architecture

The module has two layers: a **configuration layer** (`FilterConfiguration`) that defines what record/value types to index, and a **filter chain** (`ExportRecordFilterChain`) that applies content-based filters to individual records.

```
FilterConfiguration (interface, implemented by exporter configs)
       │
       ▼
DefaultRecordFilter (implements Context.RecordFilter)
  ├── acceptType(RecordType)     → delegates to FilterConfiguration.shouldIndexRecordType()
  ├── acceptValue(ValueType)     → delegates to FilterConfiguration.shouldIndexValueType()
  └── acceptRecord(Record<?>)    → delegates to ExportRecordFilterChain
           │
           ▼
     ExportRecordFilterChain (logical AND of all filters)
       ├── OptimizeModeFilter      (Optimize-specific intent/element filtering)
       ├── VariableNameFilter      (include/exclude by variable name patterns)
       ├── VariableTypeFilter      (include/exclude by JSON value type)
       └── BpmnProcessFilter       (include/exclude by BPMN process id)
```

**Data flow**: The exporter runtime calls `acceptType` → `acceptValue` → `acceptRecord` in sequence. The first two are metadata-level gates; `acceptRecord` runs the content-based filter chain.

## Key Abstractions

- **`ExporterRecordFilter`** (`ExporterRecordFilter.java`): Core `@FunctionalInterface` with `boolean accept(Record<?>)`. All content filters implement this.
- **`RecordVersionFilter`** (`RecordVersionFilter.java`): Marker interface for version-gated filters. Returns a `SemanticVersion` minimum; the chain skips the filter for records from older brokers. Currently all content filters require broker ≥ 8.9.0.
- **`ExportRecordFilterChain`** (`ExportRecordFilterChain.java`): Evaluates filters with logical AND and short-circuit. Handles version gating: if a filter implements `RecordVersionFilter` and the record's broker version is below the minimum (or unparseable), the filter is skipped (treated as accepted).
- **`FilterConfiguration`** (`FilterConfiguration.java`): Interface implemented by exporter configuration classes (`ElasticsearchExporterConfiguration`, `OpensearchExporterConfiguration`). Defines `shouldIndexRecordType`, `shouldIndexValueType`, `shouldIndexRequiredValueType`, and `filterIndexConfig()`.
- **`FilterConfiguration.IndexConfig`**: Nested interface exposing variable name inclusion/exclusion lists (exact, starts-with, ends-with), variable value type inclusion/exclusion, BPMN process ID inclusion/exclusion, and optimize mode toggle.
- **`NameFilter`** / **`NameFilterRule`**: Reusable name-matching engine. `NameFilterRule` is a record with `Type` enum (`EXACT`, `STARTS_WITH`, `ENDS_WITH`) and a pattern string. `NameFilter` converts rules to predicates and applies inclusion-then-exclusion logic.
- **`DefaultRecordFilter`** (`DefaultRecordFilter.java`): The public entry point. Implements `Context.RecordFilter` from `zeebe-exporter-api`. Assembles the filter chain from `FilterConfiguration` at construction time.

## Filter Implementations

| Filter | Targets | Logic |
|--------|---------|-------|
| `OptimizeModeFilter` | All records | Whitelists specific intents for PROCESS, PROCESS_INSTANCE, INCIDENT, USER_TASK, VARIABLE; rejects everything else |
| `VariableNameFilter` | `VariableRecordValue` | Delegates to `NameFilter` on `variableRecordValue.getName()` |
| `VariableTypeFilter` | `VariableRecordValue` | Infers JSON type via Jackson `ObjectMapper.readTree()`, maps to `VariableValueType` enum, checks against allowed set |
| `BpmnProcessFilter` | 14 `RecordValue` subtypes | Extracts BPMN process ID via pattern-matched `switch` on `RecordValue`, checks inclusion/exclusion sets |

All filters pass through non-targeted record types (return `true` for records they don't understand).

## Consumers

- `ElasticsearchExporter` → `context.setFilter(new DefaultRecordFilter(configuration))` in `configure()`
- `OpensearchExporter` → same pattern

## How to Add a New Filter

1. Create a class implementing `ExporterRecordFilter`. If the filter requires broker ≥ some version, also implement `RecordVersionFilter`.
2. Return `true` for record types the filter does not care about (non-targeted records always pass through).
3. Add configuration fields to `FilterConfiguration.IndexConfig` interface.
4. Wire the new filter in `DefaultRecordFilter.createRecordFilters()` — add it conditionally based on configuration.
5. Add a corresponding getter/setter to `TestIndexConfig` for testing.
6. Ensure exporter configuration classes (`ElasticsearchExporterConfiguration`, `OpensearchExporterConfiguration`) implement the new `IndexConfig` methods.

## Invariants

- Filters are combined with logical AND in the chain; the first `false` short-circuits evaluation.
- Versioned filters are **skipped** (treated as accepted) when the record's broker version is below `minRecordBrokerVersion()` or unparseable — this ensures backward compatibility during rolling upgrades.
- Filters are created once at construction of `DefaultRecordFilter` and are immutable thereafter.
- Non-targeted records always pass through individual filters — never reject a record type you don't understand.
- Exclusion overrides inclusion when both are configured (the allowed set = inclusion − exclusion).

## Testing Patterns

- Use `TestConfiguration` and `TestIndexConfig` (in `test/.../config/`) as fluent test doubles for `FilterConfiguration`.
- Mock `Record<?>` and `RecordValue` subtypes with Mockito. Always set `getBrokerVersion()` to `"8.9.0"` or higher for versioned filters to activate.
- Use `// given`, `// when`, `// then` sections. Prefix methods with `should`.
- Run scoped: `./mvnw -pl zeebe/exporter-filter -am test -DskipITs -DskipChecks -T1C`

## Key Files

- `main/.../DefaultRecordFilter.java` — public entry point, filter chain assembly
- `main/.../ExportRecordFilterChain.java` — chain evaluation with version gating
- `main/.../FilterConfiguration.java` — configuration interface (implemented by exporter configs)
- `main/.../BpmnProcessFilter.java` — most complex filter, 14 record value types via switch
- `main/.../NameFilter.java` + `NameFilterRule.java` — reusable inclusion/exclusion name matching
- `test/.../config/TestConfiguration.java` + `TestIndexConfig.java` — test configuration doubles

## Common Pitfalls

- Never forget to handle `null`/empty BPMN process IDs or variable names gracefully (return `true`).
- When adding a new `RecordValue` subtype to `BpmnProcessFilter`, add it to both the `switch` in `bpmnProcessIdOf()` and the Javadoc list, and add a test in `BpmnProcessFilterTest.shouldExtractBpmnProcessIdFromAllSupportedRecordValues`.
- The `VariableTypeFilter` uses Jackson to parse variable values as JSON — invalid JSON maps to `UNKNOWN`, not an exception.
- Do not mutate filter state after construction; filters are shared across record processing.