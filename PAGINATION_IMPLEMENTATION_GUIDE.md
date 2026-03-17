# Pagination Model Selection - Implementation Guide

## Overview

This document describes the changes needed to match pagination model selection to sort polymorphism support in the Camunda Java client.

## Problem

The Zeebe gateway protocol defines `SearchQueryPageRequest` as a polymorphic type with four variants:
- `LimitPagination` - only `limit`
- `OffsetPagination` - `from` + `limit`
- `CursorForwardPagination` - `after` + `limit`
- `CursorBackwardPagination` - `before` + `limit`

However, the Java client API (`TypedPageableRequest`) has no mechanism to enforce which pagination model is valid for a given endpoint, unlike the type-safe `TypedSortableRequest<S, SELF>` for sorts.

## Solution

Add a generic type parameter `P` to `TypedPageableRequest` to enforce pagination models at compile-time, matching the sort polymorphism pattern.

## Changes Made

### 1. New Pagination Model Interfaces (✅ Complete)

Created typed pagination interfaces in `clients/java/src/main/java/io/camunda/client/api/search/page/`:
- `SearchRequestPage<P>` - Base interface (similar to `SearchRequestSort<S>`)
- `LimitPage` - Only `limit()` method
- `OffsetPage` - `from()` + `limit()` methods
- `CursorForwardPage` - `after()` + `limit()` methods
- `CursorBackwardPage` - `before()` + `limit()` methods
- `AnyPage` - All methods (for general search endpoints)

### 2. Updated Core Interfaces (✅ Complete)

- `TypedPageableRequest<P, SELF>` - Added generic type parameter `P extends SearchRequestPage<P>`
- `TypedSearchRequest<F, S, P, SELF>` - Added pagination type parameter `P`
- Deprecated old `SearchRequestPage` (in `request` package) with migration guidance

### 3. Updated All Request Interfaces (✅ Complete)

**Regular search requests** (use `AnyPage`): 39 files updated
- JobSearchRequest, IncidentSearchRequest, ProcessInstanceSearchRequest, etc.

**Statistics requests with cursor pagination** (use `CursorForwardPage`): 11 files updated
- JobWorkerStatisticsRequest, JobTypeStatisticsRequest, JobErrorStatisticsRequest, etc.

**Statistics requests with offset pagination** (use `OffsetPage`): 3 files updated
- IncidentProcessInstanceStatisticsByDefinitionRequest
- IncidentProcessInstanceStatisticsByErrorRequest
- ProcessDefinitionInstanceStatisticsRequest
- ProcessDefinitionInstanceVersionStatisticsRequest

### 4. Implementation Classes Created (✅ Partial - 3 of N)

Created in `clients/java/src/main/java/io/camunda/client/impl/search/page/`:
- `AnyPageImpl` - Implements all pagination methods
- `CursorForwardPageImpl` - Implements cursor-forward pagination
- `OffsetPageImpl` - Implements offset-based pagination

**Still needed:**
- `LimitPageImpl` - Only implements `limit()`
- `CursorBackwardPageImpl` - Implements `before()` + `limit()`

### 5. Implementation Classes to Update (❌ Not Started)

All `*SearchRequestImpl` and `*StatisticsRequestImpl` classes need their `page()` method signatures updated:

**Pattern to follow:**

```java
// OLD:
@Override
public JobSearchRequest page(final SearchRequestPage value) {
  request.setPage(provideSearchRequestProperty(value));
  return this;
}

@Override
public JobSearchRequest page(final Consumer<SearchRequestPage> fn) {
  return page(searchRequestPage(fn));
}

// NEW (for JobSearchRequest which uses AnyPage):
@Override
public JobSearchRequest page(final AnyPage value) {
  request.setPage(provideSearchRequestProperty(value));
  return this;
}

@Override
public JobSearchRequest page(final Consumer<AnyPage> fn) {
  return page(anyPage(fn));
}
```

**Files to update (~50+ files):**
- All files in `clients/java/src/main/java/io/camunda/client/impl/search/request/*SearchRequestImpl.java`
- All files in `clients/java/src/main/java/io/camunda/client/impl/statistics/request/*StatisticsRequestImpl.java`

### 6. Builder Methods to Add (❌ Not Started)

In `SearchRequestBuilders.java`, add new builder methods:

```java
public static AnyPage anyPage(final Consumer<AnyPage> fn) {
  final AnyPage page = new AnyPageImpl();
  fn.accept(page);
  return page;
}

public static CursorForwardPage cursorForwardPage(final Consumer<CursorForwardPage> fn) {
  final CursorForwardPage page = new CursorForwardPageImpl();
  fn.accept(page);
  return page;
}

public static OffsetPage offsetPage(final Consumer<OffsetPage> fn) {
  final OffsetPage page = new OffsetPageImpl();
  fn.accept(page);
  return page;
}

public static LimitPage limitPage(final Consumer<LimitPage> fn) {
  final LimitPage page = new LimitPageImpl();
  fn.accept(page);
  return page;
}

public static CursorBackwardPage cursorBackwardPage(final Consumer<CursorBackwardPage> fn) {
  final CursorBackwardPage page = new CursorBackwardPageImpl();
  fn.accept(page);
  return page;
}
```

Also add corresponding imports at the top of the file.

### 7. Tests to Update (❌ Not Started)

All tests using `.page()` methods need to be updated. Tests currently use the old `SearchRequestPage` interface.

**Example update:**

```java
// OLD:
client.newJobSearchRequest()
    .page(fn -> fn.limit(10).from(0))
    .send()
    .join();

// NEW (no change in test code - builder method handles it):
// The builder methods will automatically provide the correct type
client.newJobSearchRequest()
    .page(fn -> fn.limit(10).from(0))
    .send()
    .join();
```

Tests should still work if builder methods are updated correctly, because the lambda Consumer pattern remains the same.

### 8. Documentation to Add (❌ Not Started)

- Add migration guide explaining the breaking API change
- Document that this enforces what was already validated at runtime
- Note that mixing pagination styles (e.g., `from()` + `after()`) was never valid and would have failed with 400 error
- Add Javadoc examples showing correct usage of each pagination model

## Breaking Changes

This is a **breaking API change** from Camunda 8.8, but:
1. The restricted functionality should not have worked before (would fail at runtime with 400 error)
2. This change enforces at compile-time what was already enforced at runtime
3. Users mixing incompatible pagination styles would have encountered errors anyway

## Next Steps

1. Complete remaining implementation classes (LimitPageImpl, CursorBackwardPageImpl)
2. Update all ~50+ implementation classes to use typed pagination models
3. Add builder methods to SearchRequestBuilders
4. Update tests (may work automatically if builders handle type inference)
5. Run full build and fix any remaining compilation errors
6. Run tests and ensure backwards compatibility where possible
7. Add comprehensive documentation and migration guide

## Script-Based Approach for Implementation Classes

Given the large number of implementation files, consider creating a script to:
1. Detect which pagination type each request uses (by looking at interface)
2. Update method signatures automatically
3. Update builder method calls (searchRequestPage → anyPage/cursorForwardPage/offsetPage)

## Testing Strategy

1. Verify compilation succeeds
2. Run existing unit tests - they should pass if builder methods work correctly
3. Add new tests demonstrating compile-time enforcement:
   - Positive: Using correct pagination model compiles
   - Negative: Using wrong pagination model doesn't compile (if possible to test)
4. Integration tests to ensure runtime behavior unchanged

