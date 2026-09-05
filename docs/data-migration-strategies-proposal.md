# Elasticsearch/OpenSearch Data Migration Strategies Proposal

## Executive Summary

This document explores alternative approaches to migrating large volumes of data between Elasticsearch/OpenSearch indices, addressing the current challenges with the reindex API that cause cluster resource exhaustion, connection timeouts, and duplicate reindex tasks.

**Current Issues:**
1. Sync client reindex calls timeout with large data volumes
2. Timed-out connections leave orphaned reindex tasks running in the cluster
3. Retry attempts create duplicate tasks, multiplying resource consumption
4. Task API is unreliable (lost data, inconsistent behavior per vendor documentation)
5. CPU throttling and memory exhaustion under heavy reindex load

**Recommended Approach:** Hybrid strategy using manual coordination with batch streaming (Option 3) for large migrations, while retaining enhanced reindex API with task cancellation for smaller operations.

---

## Current State Analysis

### Architecture Overview

The codebase has two distinct reindex implementations:

#### 1. Optimize Schema Upgrade (Schema Migrations)
- **Location:** `optimize/upgrade/src/main/java/io/camunda/optimize/upgrade/`
- **Purpose:** Version migrations during upgrades
- **Pattern:** Submits reindex as async task, polls for completion
- **Issue Mitigation:** Uses `waitForOrSubmitNewTask()` to detect and resume existing tasks

```java
// SchemaUpgradeClientES.java:394-422
private <T extends RequestBase> void waitForOrSubmitNewTask(
    final String identifier,
    final T request,
    final Function<T, Optional<TaskInfo>> getPendingTaskFunction,
    final Function<T, String> submitNewTaskFunction) {

    // Check for existing task first
    final Optional<TaskInfo> pendingTask = getPendingTaskFunction.apply(request);
    if (pendingTask.isEmpty()) {
        taskId = submitNewTaskFunction.apply(request);
    } else {
        taskId = pendingTask.get().node() + ":" + pendingTask.get().id();
        validateStatusOfPendingTask(taskId);
        LOG.info("Found pending task with id {}, will wait for it to finish.", taskId);
    }
    waitUntilTaskIsFinished(taskId, identifier);
}
```

**Strengths:**
- Detects and resumes existing tasks before creating new ones
- Avoids duplicate task creation
- Validates task health before waiting

**Weaknesses:**
- Still uses sync client with `waitUntilTaskIsFinished()` polling
- Long-running tasks can still timeout during wait loop
- No scope reduction on retry

#### 2. Exporter Archiver (Continuous Data Archiving)
- **Location:** `zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/archiver/`
- **Purpose:** Moving historical data to archive indices
- **Pattern:** Uses async client with direct reindex API (no task monitoring)
- **Characteristics:** Batched approach, smaller scopes per operation

```java
// ElasticsearchArchiverRepository.java:337-344
final var request = new ReindexRequest.Builder()
    .source(src -> src.index(sourceIndexName).query(buildFilterQuery(keysByField, filters)))
    .dest(dest -> dest.index(destinationIndexName))
    .conflicts(Conflicts.Proceed)
    .scroll(REINDEX_SCROLL_TIMEOUT)  // 30s scroll timeout
    .slices(AUTO_SLICES)              // Auto slice parallelization
    .build();

return client.reindex(request)  // Async client
    .thenApplyAsync(ignored -> null, executor);
```

**Strengths:**
- Async client avoids connection timeout issues
- Pre-batched by finish date (configurable batch size)
- Smaller scopes make operations complete faster
- Three-phase operation: reindex → set lifecycle → delete source

**Weaknesses:**
- No task tracking or resume capability
- Failures require full batch retry
- Limited observability into reindex progress

### Current Pain Points

1. **Timeout Cascade:** Sync client timeout → orphaned task → retry → new task → duplicate work
2. **Resource Multiplication:** N timeouts = N concurrent tasks doing identical work
3. **No Cancellation:** Cannot cancel orphaned tasks (no task ID from sync client)
4. **Fixed Scope:** Retries use same scope, likely to timeout again
5. **Cluster Impact:** Multiple large reindex tasks exhaust CPU and memory

---

## Proposed Solutions

### Option 1: Enhanced Reindex API with Task Cancellation

**Concept:** Improve current approach by adding task cancellation and scope reduction on retry.

#### Implementation Strategy

```java
public class EnhancedReindexCoordinator {
    private final Map<String, String> activeReindexTasks = new ConcurrentHashMap<>();

    public void reindex(String sourceIndex, String targetIndex,
                       Query filter, String identifier) {
        String lastTaskId = activeReindexTasks.get(identifier);

        // Cancel any existing task
        if (lastTaskId != null) {
            try {
                cancelTask(lastTaskId);
                LOG.info("Cancelled previous task {} for {}", lastTaskId, identifier);
            } catch (Exception e) {
                LOG.warn("Could not cancel task {}", lastTaskId, e);
            }
        }

        // Submit new task with potentially reduced scope
        ReindexRequest request = buildReindexRequest(sourceIndex, targetIndex, filter);
        String taskId = submitReindexTask(request);
        activeReindexTasks.put(identifier, taskId);

        // Wait with timeout
        boolean completed = waitWithTimeout(taskId, identifier, REINDEX_TIMEOUT);
        if (!completed) {
            // Task still running, will be cancelled on next retry
            throw new ReindexTimeoutException("Reindex did not complete in time");
        }

        activeReindexTasks.remove(identifier);
    }

    private boolean waitWithTimeout(String taskId, String identifier,
                                    Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        BackoffCalculator backoff = new BackoffCalculator(1000, 10);

        while (Instant.now().isBefore(deadline)) {
            TaskResponse response = getTaskResponse(taskId);
            if (response.isCompleted()) {
                return true;
            }
            Thread.sleep(backoff.calculateSleepTime());
        }
        return false;
    }

    private void cancelTask(String taskId) throws IOException {
        // POST /_tasks/{taskId}/_cancel
        Request request = new Request("POST",
            "/_tasks/" + taskId + "/_cancel");
        client.performRequest(request);
    }
}
```

#### Scope Reduction Strategy

Track retry count and progressively reduce scope:

```java
public class AdaptiveReindexScope {
    private int retryCount = 0;
    private static final int INITIAL_BATCH_SIZE = 10000;

    public Query buildFilterWithScope(Query baseFilter, int attempt) {
        if (attempt == 0) {
            return baseFilter;  // Full scope on first attempt
        }

        // Reduce batch size exponentially: 10k → 5k → 2.5k → 1.25k
        int batchSize = INITIAL_BATCH_SIZE / (int) Math.pow(2, attempt);
        batchSize = Math.max(batchSize, 100);  // Minimum 100 docs

        return QueryBuilders.bool()
            .filter(baseFilter)
            .must(QueryBuilders.range()
                .field("_id")
                .gte(getLastProcessedId())
                .build())
            .build();
    }

    private String getLastProcessedId() {
        // Track last successfully processed document ID
        // Could be stored in a progress tracking index
    }
}
```

#### Pros
- Builds on existing code patterns
- Prevents duplicate tasks through cancellation
- Adaptive scope increases success rate
- Maintains task API benefits (cluster management)

#### Cons
- Still depends on task API (vendor warns against production use)
- Cancellation is best-effort, not guaranteed
- Task API inconsistencies still possible
- Requires task ID persistence across retries
- Complex state management for progress tracking

#### Risk Assessment
- **Medium Risk:** Task API reliability concerns remain
- **Implementation Complexity:** Medium (add cancellation + scope tracking)
- **Performance Impact:** Low (similar to current approach)

---

### Option 2: Using Task API Despite Warnings

**Concept:** Embrace the async task API fully, implement comprehensive validation and reconciliation.

#### Implementation Strategy

```java
public class ResilientTaskApiReindexer {
    private final TaskStateStore taskStateStore;  // Persistent task tracking

    public void reindex(String sourceIndex, String targetIndex, Query filter) {
        // Submit with waitForCompletion=false
        ReindexRequest request = ReindexRequest.of(r -> r
            .source(s -> s.index(sourceIndex).query(filter))
            .dest(d -> d.index(targetIndex))
            .waitForCompletion(false)
            .scroll(Time.of(t -> t.time("5m")))
            .slices(AUTO_SLICES));

        ReindexResponse response = client.reindex(request).get();
        String taskId = response.task();

        // Persist task metadata
        taskStateStore.save(new TaskMetadata(
            taskId, sourceIndex, targetIndex,
            filter, Instant.now(), "RUNNING"
        ));

        // Monitor asynchronously
        CompletableFuture.runAsync(() -> monitorTask(taskId));
    }

    private void monitorTask(String taskId) {
        TaskResponse response = waitForTaskCompletion(taskId);

        // Validate data integrity
        boolean valid = validateDataIntegrity(
            response.getSourceIndex(),
            response.getDestIndex(),
            response.getFilter()
        );

        if (!valid) {
            LOG.error("Data integrity check failed for task {}", taskId);
            reconcileData(response);
        }

        taskStateStore.updateStatus(taskId, "COMPLETED");
    }

    private boolean validateDataIntegrity(String source, String dest, Query filter) {
        // Count documents in source matching filter
        long sourceCount = client.count(CountRequest.of(c -> c
            .index(source)
            .query(filter))).count();

        // Count documents in destination
        long destCount = client.count(CountRequest.of(c -> c
            .index(dest))).count();

        return sourceCount == destCount;
    }

    private void reconcileData(TaskMetadata task) {
        // Find missing documents using scroll
        Set<String> sourceIds = fetchAllIds(task.sourceIndex, task.filter);
        Set<String> destIds = fetchAllIds(task.destIndex, Query.matchAll());

        Set<String> missing = Sets.difference(sourceIds, destIds);

        if (!missing.isEmpty()) {
            LOG.warn("Found {} missing documents, reindexing", missing.size());
            reindexSpecificDocs(task.sourceIndex, task.destIndex, missing);
        }
    }
}
```

#### Data Integrity Validation

Implement multi-level validation:

1. **Count Validation:** Compare document counts
2. **Sample Validation:** Randomly sample and compare documents
3. **Checksum Validation:** Compare aggregate checksums of document fields
4. **Reconciliation:** Identify and reindex missing documents

```java
public class DataIntegrityValidator {

    public ValidationResult validate(String source, String dest, Query filter) {
        ValidationResult result = new ValidationResult();

        // Level 1: Count validation
        result.sourceCount = countDocuments(source, filter);
        result.destCount = countDocuments(dest, Query.matchAll());

        if (result.sourceCount != result.destCount) {
            result.valid = false;
            result.countMismatch = true;

            // Level 2: Identify missing documents
            result.missingIds = findMissingDocuments(source, dest, filter);
        }

        // Level 3: Sample validation (compare actual content)
        List<String> sampleIds = randomSample(source, filter, 100);
        for (String id : sampleIds) {
            Document srcDoc = getDocument(source, id);
            Document destDoc = getDocument(dest, id);
            if (!srcDoc.equals(destDoc)) {
                result.valid = false;
                result.contentMismatch = true;
                break;
            }
        }

        return result;
    }
}
```

#### Pros
- Async client avoids connection timeout issues
- Can handle very large datasets
- Cluster manages task distribution and resilience
- Built-in progress tracking via task API

#### Cons
- **Vendor explicitly warns against production use**
- Documented issues with lost data and inconsistent behavior
- Requires comprehensive validation and reconciliation logic
- Reconciliation itself can be expensive
- No guarantee of fix timeline from vendor

#### Risk Assessment
- **High Risk:** Going against vendor guidance
- **Implementation Complexity:** High (validation + reconciliation)
- **Performance Impact:** Medium (validation overhead)

---

### Option 3: Manual Coordination with Batch Streaming (RECOMMENDED)

**Concept:** Implement client-side reindex using scroll/search_after for reading and bulk API for writing. Full control over batching, retry, and progress tracking.

#### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Migration Coordinator                     │
│  - Progress tracking                                        │
│  - Batch scheduling                                         │
│  - Error handling & retry                                   │
└──────────────┬──────────────────────────────────────────────┘
               │
      ┌────────┴────────┐
      │                 │
┌─────▼─────┐    ┌─────▼──────┐
│  Reader   │    │   Writer   │
│  Thread   │───▶│   Thread   │
│  Pool     │    │   Pool     │
└───────────┘    └────────────┘
      │                 │
      │                 │
┌─────▼─────┐    ┌─────▼──────┐
│  Source   │    │   Target   │
│  Index    │    │   Index    │
└───────────┘    └────────────┘
```

#### Core Implementation

```java
public class StreamingReindexCoordinator {
    private static final int BATCH_SIZE = 1000;
    private static final int READ_THREADS = 4;
    private static final int WRITE_THREADS = 2;
    private static final Duration CHECKPOINT_INTERVAL = Duration.ofMinutes(5);

    private final ElasticsearchAsyncClient client;
    private final ExecutorService readExecutor;
    private final ExecutorService writeExecutor;
    private final ProgressTracker progressTracker;

    public CompletableFuture<MigrationResult> reindex(
            String sourceIndex, String targetIndex, Query filter) {

        MigrationState state = progressTracker.loadOrCreate(
            sourceIndex, targetIndex, filter);

        // Use search_after for cursor-based pagination
        return streamDocuments(sourceIndex, filter, state.lastSeenSortValue)
            .thenCompose(docStream ->
                processBatches(docStream, targetIndex, state)
            )
            .thenApply(result -> {
                progressTracker.markComplete(state.migrationId);
                return result;
            })
            .exceptionally(error -> {
                progressTracker.markFailed(state.migrationId, error);
                throw new ReindexException("Migration failed", error);
            });
    }

    private CompletableFuture<Stream<Document>> streamDocuments(
            String index, Query filter, String lastSeenSort) {

        return CompletableFuture.supplyAsync(() -> {
            List<Document> documents = new ArrayList<>();
            String currentSort = lastSeenSort;
            boolean hasMore = true;

            while (hasMore) {
                SearchRequest request = SearchRequest.of(s -> s
                    .index(index)
                    .query(filter)
                    .size(BATCH_SIZE)
                    .sort(sort -> sort.field(f -> f.field("_id").order(SortOrder.Asc)))
                    .searchAfter(currentSort != null ?
                        List.of(FieldValue.of(currentSort)) : null)
                    .requestCache(false)  // Don't cache for large scans
                );

                SearchResponse<Document> response =
                    client.search(request, Document.class).join();

                List<Hit<Document>> hits = response.hits().hits();
                if (hits.isEmpty()) {
                    hasMore = false;
                } else {
                    documents.addAll(hits.stream()
                        .map(Hit::source)
                        .collect(Collectors.toList()));

                    // Update cursor for next batch
                    currentSort = hits.get(hits.size() - 1)
                        .sort().get(0).toString();

                    // Checkpoint progress
                    progressTracker.updateCursor(currentSort);
                }
            }

            return documents.stream();
        }, readExecutor);
    }

    private CompletableFuture<MigrationResult> processBatches(
            Stream<Document> documents, String targetIndex,
            MigrationState state) {

        AtomicLong processedCount = new AtomicLong(state.processedCount);
        AtomicLong failedCount = new AtomicLong(0);

        return documents
            .collect(Collectors.groupingBy(
                doc -> processedCount.getAndIncrement() / BATCH_SIZE
            ))
            .values()
            .stream()
            .map(batch -> writeBatch(targetIndex, batch, processedCount))
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                futures -> CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
                )
            ))
            .thenApply(v -> new MigrationResult(
                processedCount.get(), failedCount.get()
            ));
    }

    private CompletableFuture<Void> writeBatch(
            String targetIndex, List<Document> documents,
            AtomicLong processedCount) {

        return CompletableFuture.runAsync(() -> {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Document doc : documents) {
                bulkBuilder.operations(op -> op
                    .index(idx -> idx
                        .index(targetIndex)
                        .id(doc.getId())
                        .document(doc)
                    )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build()).join();

            if (response.errors()) {
                LOG.warn("Bulk write had {} errors",
                    response.items().stream()
                        .filter(BulkResponseItem::error)
                        .count()
                );
                // Could retry failed items here
            }

            // Update progress
            progressTracker.incrementProcessed(documents.size());

        }, writeExecutor);
    }
}
```

#### Progress Tracking

```java
public class ProgressTracker {
    private final String progressIndex = ".migration-progress";
    private final ElasticsearchAsyncClient client;

    public MigrationState loadOrCreate(String source, String target, Query filter) {
        String migrationId = generateId(source, target, filter);

        try {
            GetResponse<MigrationState> response = client.get(
                g -> g.index(progressIndex).id(migrationId),
                MigrationState.class
            ).join();

            if (response.found()) {
                return response.source();
            }
        } catch (Exception e) {
            LOG.debug("No existing migration state found, creating new");
        }

        // Create new state
        MigrationState state = new MigrationState(
            migrationId, source, target, filter,
            Instant.now(), null, 0, "RUNNING"
        );

        client.index(i -> i
            .index(progressIndex)
            .id(migrationId)
            .document(state)
        ).join();

        return state;
    }

    public void updateCursor(String migrationId, String cursor) {
        client.update(u -> u
            .index(progressIndex)
            .id(migrationId)
            .doc(Map.of("lastSeenSortValue", cursor))
        ).join();
    }

    public void incrementProcessed(String migrationId, long count) {
        client.update(u -> u
            .index(progressIndex)
            .id(migrationId)
            .script(s -> s
                .inline(i -> i
                    .source("ctx._source.processedCount += params.count")
                    .params("count", JsonData.of(count))
                )
            )
        ).join();
    }

    @Data
    public static class MigrationState {
        String migrationId;
        String sourceIndex;
        String targetIndex;
        Query filter;
        Instant startTime;
        String lastSeenSortValue;
        long processedCount;
        String status;  // RUNNING, COMPLETED, FAILED
    }
}
```

#### Retry & Error Handling

```java
public class ResilientBatchWriter {
    private static final int MAX_RETRIES = 3;

    public CompletableFuture<BatchResult> writeBatchWithRetry(
            String targetIndex, List<Document> documents, int attempt) {

        if (attempt >= MAX_RETRIES) {
            return CompletableFuture.completedFuture(
                BatchResult.failed(documents.size())
            );
        }

        return writeBatch(targetIndex, documents)
            .handle((result, error) -> {
                if (error != null) {
                    LOG.warn("Batch write failed (attempt {}), retrying",
                        attempt + 1, error);
                    return writeBatchWithRetry(
                        targetIndex, documents, attempt + 1
                    ).join();
                }
                return result;
            });
    }

    private CompletableFuture<BatchResult> writeBatch(
            String targetIndex, List<Document> documents) {

        BulkRequest bulk = buildBulkRequest(targetIndex, documents);

        return client.bulk(bulk)
            .thenApply(response -> {
                if (!response.errors()) {
                    return BatchResult.success(documents.size());
                }

                // Partial failure - identify and retry failed items
                List<Document> failed = new ArrayList<>();
                for (int i = 0; i < response.items().size(); i++) {
                    if (response.items().get(i).error() != null) {
                        failed.add(documents.get(i));
                    }
                }

                if (!failed.isEmpty()) {
                    LOG.warn("Retrying {} failed documents", failed.size());
                    return retryFailedDocuments(targetIndex, failed);
                }

                return BatchResult.success(documents.size());
            });
    }
}
```

#### Performance Optimizations

```java
public class OptimizedReindexConfiguration {

    // Reading optimizations
    public SearchRequest.Builder optimizeSourceRead(SearchRequest.Builder builder) {
        return builder
            .requestCache(false)           // Don't cache large scans
            .source(s -> s.fetch(true))    // Fetch full source
            .storedFields("_none_")        // Don't retrieve stored fields
            .trackTotalHits(th -> th.enabled(false));  // Don't count total
    }

    // Writing optimizations
    public BulkRequest.Builder optimizeTargetWrite(BulkRequest.Builder builder) {
        return builder
            .refresh(Refresh.False)        // Don't refresh immediately
            .timeout(Time.of(t -> t.time("5m")))
            .waitForActiveShards("1");     // Don't wait for all replicas
    }

    // Index settings for target during migration
    public IndexSettings optimizeTargetIndexSettings() {
        return IndexSettings.of(s -> s
            .numberOfReplicas("0")         // No replicas during load
            .refreshInterval(Time.of(t -> t.time("30s")))  // Slower refresh
            .translog(t -> t
                .durability(Durability.Async)     // Async translog
                .syncInterval(Time.of(ti -> ti.time("30s")))
            )
        );
    }

    // Restore normal settings after migration
    public void restoreNormalSettings(String index) {
        client.indices().putSettings(ps -> ps
            .index(index)
            .settings(s -> s
                .numberOfReplicas("1")
                .refreshInterval(Time.of(t -> t.time("1s")))
                .translog(t -> t.durability(Durability.Request))
            )
        );
    }
}
```

#### Pros
- **Full control** over batching, retry, and progress
- **Resilient:** Can resume from last checkpoint after any failure
- **Observable:** Detailed progress tracking and metrics
- **Predictable:** Network/serialization overhead is constant and measurable
- **Flexible:** Can adapt batch sizes based on performance
- **No duplicate work:** Cursor-based pagination guarantees no duplicates
- **Resource friendly:** Bounded memory usage, controlled parallelism

#### Cons
- **Network overhead:** Every document goes through client
- **Serialization cost:** Deserialize from source, serialize to target
- **More complex:** More code than simple reindex API call
- **Coordination overhead:** Progress tracking index adds I/O

#### Performance Characteristics

**Throughput Comparison:**

| Approach | Throughput (docs/sec) | Resource | Notes |
|----------|---------------------|----------|-------|
| Direct Reindex API | 10,000-50,000 | ES cluster | Server-side, but can overwhelm cluster |
| Manual Streaming | 5,000-15,000 | Client + ES | Slower but more stable |

**Resource Usage:**

- **Client Memory:** ~100MB-500MB (batch buffers)
- **Network:** 2x document size (read + write)
- **ES CPU:** Lower than reindex API (distributed across queries)
- **ES Memory:** Much lower (no large scroll contexts)

#### Risk Assessment
- **Low Risk:** Proven pattern, full control
- **Implementation Complexity:** High (comprehensive but straightforward)
- **Performance Impact:** Medium (slower, but more stable)

---

### Option 4: Hybrid Approach (RECOMMENDED)

**Concept:** Combine the best of both worlds - use enhanced reindex API for small operations, manual streaming for large migrations.

#### Decision Logic

```java
public class HybridMigrationStrategy {
    private static final long LARGE_MIGRATION_THRESHOLD = 1_000_000;  // 1M docs
    private static final Duration SIZE_CHECK_TIMEOUT = Duration.ofSeconds(30);

    public CompletableFuture<MigrationResult> reindex(
            String sourceIndex, String targetIndex, Query filter) {

        // Estimate document count
        return estimateDocumentCount(sourceIndex, filter)
            .thenCompose(count -> {
                LOG.info("Estimated {} documents to migrate", count);

                if (count < LARGE_MIGRATION_THRESHOLD) {
                    // Use enhanced reindex API for smaller migrations
                    LOG.info("Using reindex API for small migration");
                    return reindexApiMigration(sourceIndex, targetIndex, filter);
                } else {
                    // Use manual streaming for large migrations
                    LOG.info("Using streaming migration for large dataset");
                    return streamingMigration(sourceIndex, targetIndex, filter);
                }
            });
    }

    private CompletableFuture<Long> estimateDocumentCount(
            String index, Query filter) {

        return client.count(CountRequest.of(c -> c
            .index(index)
            .query(filter)
        ))
        .thenApply(CountResponse::count)
        .orTimeout(SIZE_CHECK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally(error -> {
            LOG.warn("Could not estimate size, defaulting to streaming", error);
            return Long.MAX_VALUE;  // Use streaming on uncertainty
        });
    }

    private CompletableFuture<MigrationResult> reindexApiMigration(
            String source, String target, Query filter) {
        return new EnhancedReindexCoordinator(client)
            .reindex(source, target, filter);
    }

    private CompletableFuture<MigrationResult> streamingMigration(
            String source, String target, Query filter) {
        return new StreamingReindexCoordinator(client)
            .reindex(source, target, filter);
    }
}
```

#### Pros
- **Best of both:** Fast reindex API when appropriate, stable streaming when needed
- **Risk mitigation:** Falls back to streaming on any uncertainty
- **Pragmatic:** Leverages cluster capabilities without overwhelming it
- **Gradual adoption:** Can adjust threshold based on experience

#### Cons
- **Two code paths:** Need to maintain both implementations
- **Threshold tuning:** Optimal threshold may vary by cluster

---

## Comparative Analysis

### Effort vs Risk vs Performance

```
                    │ Effort │  Risk  │ Performance │ Reliability │
────────────────────┼────────┼────────┼─────────────┼─────────────┤
Option 1: Enhanced  │ Medium │ Medium │   High      │   Medium    │
  Reindex API       │        │        │             │             │
────────────────────┼────────┼────────┼─────────────┼─────────────┤
Option 2: Task API  │  High  │  High  │   High      │    Low      │
  Despite Warnings  │        │        │             │             │
────────────────────┼────────┼────────┼─────────────┼─────────────┤
Option 3: Manual    │  High  │  Low   │   Medium    │   High      │
  Streaming         │        │        │             │             │
────────────────────┼────────┼────────┼─────────────┼─────────────┤
Option 4: Hybrid    │  High  │  Low   │ High/Medium │   High      │
  (RECOMMENDED)     │        │        │             │             │
```

### Feature Comparison

| Feature | Option 1 | Option 2 | Option 3 | Option 4 |
|---------|----------|----------|----------|----------|
| Task cancellation | ✅ | ✅ | N/A | ✅ |
| Progress resume | Partial | ✅ | ✅ | ✅ |
| Scope reduction | ✅ | ❌ | ✅ | ✅ |
| Data validation | ❌ | ✅ | ✅ | ✅ |
| Resource control | ❌ | ❌ | ✅ | ✅ |
| Cluster efficiency | Medium | High | Low | High/Low |
| Observability | Medium | High | High | High |
| Production proven | Yes | No | Yes | Hybrid |

---

## Recommendations

### Primary Recommendation: Option 4 (Hybrid)

Implement the hybrid approach for maximum flexibility and reliability:

1. **Phase 1:** Implement Option 3 (Manual Streaming)
   - This addresses immediate large-volume issues
   - Proven pattern with full control
   - Can be production-ready in 2-3 weeks

2. **Phase 2:** Add Option 1 (Enhanced Reindex API)
   - Optimize for small migrations (<1M docs)
   - Add task cancellation and retry logic
   - Tune threshold based on Phase 1 learnings

3. **Phase 3:** Monitoring & Tuning
   - Add comprehensive metrics for both paths
   - Dynamic threshold adjustment based on cluster health
   - A/B testing to validate performance assumptions

### Implementation Priorities

#### Immediate (Next Sprint)
1. Implement `StreamingReindexCoordinator` with search_after pagination
2. Implement `ProgressTracker` with resume capability
3. Add integration tests with large datasets
4. Deploy to staging for validation

#### Short-term (1-2 Months)
1. Add `HybridMigrationStrategy` with threshold logic
2. Implement `EnhancedReindexCoordinator` with task cancellation
3. Add comprehensive monitoring and alerting
4. Production rollout with gradual traffic shift

#### Long-term (3-6 Months)
1. Tune thresholds based on production metrics
2. Implement adaptive batch sizing
3. Add predictive modeling for strategy selection
4. Consider multi-cluster coordination for global deployments

### Monitoring & Observability

Essential metrics to implement:

```java
public class MigrationMetrics {
    // Core metrics
    Counter migrationsStarted;
    Counter migrationsCompleted;
    Counter migrationsFailed;

    // Performance metrics
    Timer documentReadTime;
    Timer documentWriteTime;
    Timer batchProcessingTime;

    // Strategy metrics
    Counter reindexApiUsed;
    Counter streamingUsed;

    // Resource metrics
    Gauge activeReadThreads;
    Gauge activeWriteThreads;
    Gauge memoryUsage;

    // Quality metrics
    Counter documentsProcessed;
    Counter documentsFailed;
    Counter batchRetries;

    // Business metrics
    Timer migrationDuration;
    Histogram batchSizes;
    Histogram documentsPerSecond;
}
```

### Risk Mitigation

1. **Staging Validation:** Test all scenarios in staging before production
2. **Feature Flag:** Deploy behind feature flag for gradual rollout
3. **Rollback Plan:** Keep existing reindex as fallback option
4. **Rate Limiting:** Implement global rate limiter to prevent cluster overwhelm
5. **Circuit Breaker:** Automatically pause migrations if cluster health degrades

---

## Conclusion

The current reindex approach suffers from fundamental issues with timeout handling and duplicate task creation. While the existing `waitForOrSubmitNewTask()` pattern helps, it doesn't address the root cause.

**The hybrid approach (Option 4) is recommended** because it:

1. **Solves immediate problems:** Manual streaming handles large volumes reliably
2. **Maintains performance:** Reindex API for small operations remains fast
3. **Reduces risk:** Avoids dependency on unreliable task API for critical operations
4. **Provides control:** Full observability and resume capability
5. **Production ready:** Builds on proven patterns from archiver implementation

The implementation effort is significant but justified by the reliability gains and resource efficiency improvements. The archiver already demonstrates successful production use of similar patterns.

### Next Steps

1. **Review & Approve:** Stakeholder review of this proposal
2. **Prototype:** 1-week spike to validate Option 3 implementation
3. **Architecture Review:** Detailed design for hybrid strategy
4. **Implementation:** Phased rollout as outlined above

---

## Appendix: Code References

### Existing Implementations to Leverage

1. **Archiver Repository:** `/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/archiver/`
   - Already uses async client successfully
   - Batching and lifecycle management patterns

2. **Schema Upgrade:** `/optimize/upgrade/src/main/java/io/camunda/optimize/upgrade/`
   - Task detection and resume logic
   - Progress tracking patterns

3. **Elasticsearch Repository:** `/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/util/ElasticsearchRepository.java`
   - Scroll API implementation (though we should use search_after)
   - Async client patterns

### Additional Research

1. **Elasticsearch Best Practices:**
   - [Reindex API Documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html)
   - [Task Management API](https://www.elastic.co/guide/en/elasticsearch/reference/current/tasks.html)
   - [Scroll vs Search After](https://www.elastic.co/guide/en/elasticsearch/reference/current/paginate-search-results.html)

2. **OpenSearch Equivalents:**
   - Verify API parity for all proposed approaches
   - Test behavior differences in task management

---

**Document Version:** 1.0
**Date:** 2026-03-08
**Author:** System Analysis
**Status:** Proposal for Review
