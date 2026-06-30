/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static io.camunda.operate.archiver.AbstractArchiverJob.DATES_AGG;
import static io.camunda.operate.archiver.AbstractArchiverJob.INSTANCES_AGG;
import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static io.camunda.operate.schema.templates.BatchOperationTemplate.END_DATE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.bucketSortAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.dateHistogramAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.topHitsAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.intTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.lte;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.reindexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.time;
import static org.opensearch.client.opensearch._types.SortOrder.Asc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.ArchiveByIdTaskSupplier.IdWithRouting;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.Either;
import io.camunda.operate.util.OpensearchUtil;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchArchiverRepository implements ArchiverRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchArchiverRepository.class);

  private final RichOpenSearchClient richOpenSearchClient;
  private final OpenSearchAsyncClient osAsyncClient;
  private final OperateProperties operateProperties;
  private final Metrics metrics;
  private final ListViewTemplate processInstanceTemplate;
  private final BatchOperationTemplate batchOperationTemplate;
  private final DecisionInstanceTemplate decisionInstanceTemplate;
  private final ThreadPoolTaskScheduler archiverExecutor;

  @Autowired
  public OpensearchArchiverRepository(
      final RichOpenSearchClient richOpenSearchClient,
      @Qualifier("openSearchAsyncClient") final OpenSearchAsyncClient osAsyncClient,
      final OperateProperties operateProperties,
      final Metrics metrics,
      final ListViewTemplate processInstanceTemplate,
      final BatchOperationTemplate batchOperationTemplate,
      final DecisionInstanceTemplate decisionInstanceTemplate,
      @Qualifier("archiverThreadPoolExecutor") final ThreadPoolTaskScheduler archiverExecutor) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.osAsyncClient = osAsyncClient;
    this.operateProperties = operateProperties;
    this.metrics = metrics;
    this.processInstanceTemplate = processInstanceTemplate;
    this.batchOperationTemplate = batchOperationTemplate;
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.archiverExecutor = archiverExecutor;
  }

  private final Cache<String, Boolean> ilmApplied =
      Caffeine.newBuilder().maximumSize(200).expireAfterWrite(1, TimeUnit.HOURS).build();

  private <R> ArchiveBatch createArchiveBatch(
      final SearchResponse<R> searchResponse,
      final String datesAggName,
      final String instancesAggName) {
    final var buckets =
        searchResponse.aggregations().get(datesAggName).dateHistogram().buckets().keyed();
    if (!buckets.isEmpty()) {
      final var entry = buckets.entrySet().iterator().next();
      final var hits =
          entry.getValue().aggregations().get(instancesAggName).topHits().hits().hits();
      final var ids = hits.stream().map(hit -> (Object) hit.id()).toList();
      return new ArchiveBatch(entry.getKey(), ids);
    } else {
      return null;
    }
  }

  private CompletableFuture<ArchiveBatch> search(
      final SearchRequest.Builder searchRequestBuilder,
      final Function<Throwable, String> errorMessage) {
    final var batchFuture = new CompletableFuture<ArchiveBatch>();

    final var startTimer = Timer.start();
    OpensearchUtil.searchAsync(searchRequestBuilder.build(), Object.class, osAsyncClient)
        .whenComplete(
            (response, e) -> {
              final var timer = metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
              startTimer.stop(timer);

              final var result = handleSearchResponse(response, e, errorMessage);
              result.ifRightOrLeft(batchFuture::complete, batchFuture::completeExceptionally);
            });

    return batchFuture;
  }

  private SearchRequest.Builder nextBatchSearchRequestBuilder(
      final String index, final String idColumn, final String endDateField, final Query query) {
    final var format = operateProperties.getArchiver().getElsRolloverDateFormat();
    final var interval = operateProperties.getArchiver().getRolloverInterval();
    final var rollOverBatchSize = operateProperties.getArchiver().getRolloverBatchSize();

    final Aggregation agg =
        withSubaggregations(
            dateHistogramAggregation(END_DATE, interval, format, true),
            Map.of(
                // we want to get only one bucket at a time
                "datesSortedAgg",
                bucketSortAggregation(1, sortOptions("_key", Asc))._toAggregation(),
                // we need process instance ids, also taking into account batch size
                INSTANCES_AGG,
                topHitsAggregation(List.of(idColumn), rollOverBatchSize, sortOptions(idColumn, Asc))
                    ._toAggregation()));

    return searchRequestBuilder(index)
        .query(query)
        .aggregations(DATES_AGG, agg)
        .source(SourceConfig.of(b -> b.fetch(false)))
        .size(0)
        .sort(sortOptions(endDateField, Asc))
        .requestCache(false); // we don't need to cache this, as each time we need new data
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationNextBatch() {
    final Query query =
        constantScore(
            lte(
                BatchOperationTemplate.END_DATE,
                operateProperties.getArchiver().getArchivingTimepoint()));
    final var searchRequestBuilder =
        nextBatchSearchRequestBuilder(
            batchOperationTemplate.getFullQualifiedName(),
            BatchOperationTemplate.ID,
            BatchOperationTemplate.END_DATE,
            query);
    return search(
        searchRequestBuilder,
        e -> "Failed to search in " + batchOperationTemplate.getFullQualifiedName());
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch(
      final List<Integer> partitionIds) {
    final Query query =
        constantScore(
            and(
                lte(
                    ListViewTemplate.END_DATE,
                    operateProperties.getArchiver().getArchivingTimepoint()),
                term(
                    ListViewTemplate.JOIN_RELATION,
                    ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
                intTerms(ListViewTemplate.PARTITION_ID, partitionIds)));

    final var searchRequestBuilder =
        nextBatchSearchRequestBuilder(
            processInstanceTemplate.getFullQualifiedName(),
            ListViewTemplate.ID,
            ListViewTemplate.END_DATE,
            query);

    return search(
        searchRequestBuilder,
        e -> "Failed to search in " + processInstanceTemplate.getFullQualifiedName());
  }

  @Override
  public CompletableFuture<ArchiveBatch> getStandaloneDecisionNextBatch(
      final List<Integer> partitionIds) {
    final Query query =
        constantScore(
            and(
                lte(
                    DecisionInstanceTemplate.EVALUATION_DATE,
                    operateProperties.getArchiver().getArchivingTimepoint()),
                term(DecisionInstanceTemplate.PROCESS_INSTANCE_KEY, -1),
                intTerms(DecisionInstanceTemplate.PARTITION_ID, partitionIds)));

    final var searchRequestBuilder =
        nextBatchSearchRequestBuilder(
            decisionInstanceTemplate.getFullQualifiedName(),
            DecisionInstanceTemplate.ID,
            DecisionInstanceTemplate.EVALUATION_DATE,
            query);

    return search(
        searchRequestBuilder,
        e -> "Failed to search in " + decisionInstanceTemplate.getFullQualifiedName());
  }

  @Override
  public CompletableFuture<Void> moveDocumentsById(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<Object>> keysByField,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters,
      final Executor executor) {
    final var supplier =
        new ArchiveByIdTaskSupplier<String>(
            sourceIndexName,
            destinationIndexName,
            (searchAfter, size) ->
                getArchiveDocIdsBatch(
                    sourceIndexName,
                    keysByField,
                    inclusionFilters,
                    exclusionFilters,
                    size,
                    searchAfter,
                    executor),
            (src, dst, docs) -> reindexDocumentsById(src, dst, docs, executor),
            (src, docs) -> deleteDocumentsById(src, docs, executor),
            executor,
            operateProperties.getArchiver(),
            metrics,
            LOGGER);
    final var indexTimer = Timer.start();
    return AsyncRepeatUntil.repeatUntil(supplier::moveNextBatch, count -> supplier.isComplete())
        .thenComposeAsync(
            ignored -> {
              setIndexLifeCycle(destinationIndexName);
              return CompletableFuture.<Void>completedFuture(null);
            },
            executor)
        .whenComplete(
            (ignored, error) -> {
              final var totalArchived = supplier.getTotalArchived();
              indexTimer.stop(
                  metrics.getHistogram(
                      Metrics.TIMER_NAME_ARCHIVER_INDEX_DURATION, "source", sourceIndexName));
              metrics.recordCounts(
                  Metrics.COUNTER_NAME_ARCHIVER_INDEX_DOCS,
                  totalArchived,
                  "source",
                  sourceIndexName);
              if (error != null) {
                LOGGER.warn(
                    "Failed archiving {} to the {} index, moved {} docs so far in {}s, error={}",
                    sourceIndexName,
                    destinationIndexName,
                    totalArchived,
                    supplier.getTotalTimeTakenMs() / 1000,
                    error.getMessage(),
                    error);
              } else {
                LOGGER.debug(
                    "Successfully completed archiving {} to the {} index, moved {} docs in {}s",
                    sourceIndexName,
                    destinationIndexName,
                    totalArchived,
                    supplier.getTotalTimeTakenMs() / 1000);
              }
            });
  }

  @Override
  public void setIndexLifeCycle(final String destinationIndexName) {
    if (ilmApplied.getIfPresent(destinationIndexName) != null) {
      return;
    }
    try {
      if (operateProperties.getArchiver().isIlmEnabled()
          && richOpenSearchClient.index().indexExists(destinationIndexName)) {
        richOpenSearchClient
            .ism()
            .addPolicyToIndex(destinationIndexName, OPERATE_DELETE_ARCHIVED_INDICES);
        ilmApplied.put(destinationIndexName, Boolean.TRUE);
      }
    } catch (final Exception e) {
      LOGGER.warn(
          "Could not set ILM policy {} for index {}: {}",
          OPERATE_DELETE_ARCHIVED_INDICES,
          destinationIndexName,
          e.getMessage());
    }
  }

  @Override
  public CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys) {
    final var deleteByQueryRequestBuilder =
        deleteByQueryRequestBuilder(sourceIndexName)
            .query(
                stringTerms(
                    idFieldName, processInstanceKeys.stream().map(Object::toString).toList()))
            .slices(getAutoSlices())
            .conflicts(Conflicts.Proceed);

    LOGGER.debug("Deleting Process Instances: {} from {}", processInstanceKeys, sourceIndexName);

    final var deletionFuture = new CompletableFuture<Long>();
    final var startTimer = Timer.start();
    OpensearchUtil.deleteAsync(deleteByQueryRequestBuilder.build(), osAsyncClient)
        .whenComplete(
            (response, e) -> {
              final var timer = metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
              startTimer.stop(timer);
              final var result = OpensearchUtil.handleResponse(response, e, sourceIndexName);
              result.ifRightOrLeft(deletionFuture::complete, deletionFuture::completeExceptionally);
            });
    return deletionFuture.thenApply(ok -> null);
  }

  @Override
  public CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys) {

    final Query sourceQuery =
        stringTerms(idFieldName, processInstanceKeys.stream().map(Object::toString).toList());
    final var reindexRequest =
        reindexRequestBuilder(sourceIndexName, sourceQuery, destinationIndexName)
            .scroll(time(OpenSearchDocumentOperations.INTERNAL_SCROLL_KEEP_ALIVE_MS))
            .slices(getAutoSlices())
            .conflicts(Conflicts.Proceed)
            .build();

    LOGGER.debug(
        "Reindexing Process Instances: {} from {} to {}",
        processInstanceKeys,
        sourceIndexName,
        destinationIndexName);

    final var reindexFuture = new CompletableFuture<Long>();
    final var startTimer = Timer.start();
    OpensearchUtil.reindexAsync(reindexRequest, osAsyncClient)
        .whenComplete(
            (response, e) -> {
              final var timer = metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
              startTimer.stop(timer);
              final var result = OpensearchUtil.handleResponse(response, e, sourceIndexName);
              result.ifRightOrLeft(reindexFuture::complete, reindexFuture::completeExceptionally);
            });
    return reindexFuture.thenApply(ok -> null);
  }

  private CompletableFuture<ArchiveByIdTaskSupplier.ArchiveDocIdsBatch<String>>
      getArchiveDocIdsBatch(
          final String sourceIndexName,
          final Map<String, List<Object>> keysByField,
          final Map<String, String> inclusionFilters,
          final Map<String, String> exclusionFilters,
          final int batchSize,
          final List<String> searchAfter,
          final Executor executor) {
    final List<Query> filterClauses = new ArrayList<>();
    keysByField.forEach(
        (field, vals) ->
            filterClauses.add(stringTerms(field, vals.stream().map(Object::toString).toList())));
    inclusionFilters.forEach((field, val) -> filterClauses.add(term(field, val)));

    final List<Query> mustNotClauses =
        exclusionFilters.entrySet().stream().map(e -> term(e.getKey(), e.getValue())).toList();

    final Query finalQuery =
        constantScore(Query.of(q -> q.bool(b -> b.filter(filterClauses).mustNot(mustNotClauses))));

    final SearchRequest.Builder requestBuilder =
        searchRequestBuilder(sourceIndexName)
            .trackTotalHits(t -> t.enabled(false))
            .query(finalQuery)
            .sort(sortOptions("id", Asc))
            .size(batchSize)
            .source(SourceConfig.of(s -> s.fetch(false)))
            .requestCache(false);

    if (!searchAfter.isEmpty()) {
      requestBuilder.searchAfter(searchAfter);
    }

    LOGGER.trace(
        "Getting archive doc IDs batch from index '{}' with query '{}'",
        sourceIndexName,
        finalQuery);

    final var timer = Timer.start();
    return OpensearchUtil.searchAsync(requestBuilder.build(), Object.class, osAsyncClient)
        .whenCompleteAsync(
            (ignored, error) ->
                timer.stop(
                    metrics.getHistogram(
                        Metrics.TIMER_NAME_ARCHIVER_REQUEST_DURATION,
                        Metrics.TAG_KEY_TYPE,
                        "search")),
            executor)
        .thenApply(
            response -> {
              final List<Hit<Object>> hits = response.hits().hits();
              if (hits.isEmpty()) {
                return ArchiveByIdTaskSupplier.ArchiveDocIdsBatch.empty();
              }
              return ArchiveByIdTaskSupplier.ArchiveDocIdsBatch.from(
                  hits.stream().map(h -> new IdWithRouting(h.id(), h.routing())).toList(),
                  hits.getLast().sort());
            });
  }

  private CompletableFuture<Long> reindexDocumentsById(
      final String sourceIndexName,
      final String destinationIndexName,
      final List<IdWithRouting> docs,
      final Executor executor) {
    final var docIds = docs.stream().map(IdWithRouting::id).toList();
    final var reindexRequest =
        reindexRequestBuilder(sourceIndexName, ids(docIds), destinationIndexName)
            .scroll(time(OpenSearchDocumentOperations.INTERNAL_SCROLL_KEEP_ALIVE_MS))
            .slices(getAutoSlices())
            .conflicts(Conflicts.Proceed)
            .build();

    final var timer = Timer.start();
    return OpensearchUtil.reindexAsync(reindexRequest, osAsyncClient)
        .thenApplyAsync(
            response -> {
              validateReindexResponse(sourceIndexName, response);
              return getReindexedDocumentsCount(response);
            },
            executor)
        .whenCompleteAsync(
            (total, error) -> {
              if (total != null) {
                metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVER_REINDEXED_DOCS, total);
              }
              timer.stop(
                  metrics.getHistogram(
                      Metrics.TIMER_NAME_ARCHIVER_REQUEST_DURATION,
                      Metrics.TAG_KEY_TYPE,
                      "reindex"));
            },
            executor);
  }

  private static void validateReindexResponse(
      final String sourceIndex, final ReindexResponse response) {
    if (Boolean.TRUE.equals(response.timedOut())) {
      throw new IllegalStateException("Reindex request from %s timed out".formatted(sourceIndex));
    }
    final var failures = response.failures();
    if (!failures.isEmpty()) {
      throw new IllegalStateException(
          "Reindex request from %s index completed with %d failures"
              .formatted(sourceIndex, failures.size()));
    }
  }

  private static long getReindexedDocumentsCount(final ReindexResponse response) {
    return Math.addExact(
        Objects.requireNonNullElse(response.created(), 0L),
        Objects.requireNonNullElse(response.updated(), 0L));
  }

  CompletableFuture<Long> deleteDocumentsById(
      final String sourceIndexName, final List<IdWithRouting> docs, final Executor executor) {
    final var bulkRequestBuilder = new BulkRequest.Builder();
    docs.forEach(
        doc ->
            bulkRequestBuilder.operations(
                BulkOperation.of(
                    op ->
                        op.delete(
                            d -> d.index(sourceIndexName).id(doc.id()).routing(doc.routing())))));

    final var timer = Timer.start();
    try {
      return osAsyncClient
          .bulk(bulkRequestBuilder.build())
          .thenApplyAsync(response -> getDeletedDocCount(sourceIndexName, response), executor)
          .whenCompleteAsync(
              (deleted, error) -> {
                if (deleted != null) {
                  metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVER_DELETED_DOCS, deleted);
                }
                timer.stop(
                    metrics.getHistogram(
                        Metrics.TIMER_NAME_ARCHIVER_REQUEST_DURATION,
                        Metrics.TAG_KEY_TYPE,
                        "delete"));
              },
              executor);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  private long getDeletedDocCount(final String sourceIndex, final BulkResponse response) {
    if (response.errors()) {
      final long errorCount =
          response.items().stream().filter(item -> item.error() != null).count();
      throw new IllegalStateException(
          "Deleting reindexed documents from %s index completed with %d failures"
              .formatted(sourceIndex, errorCount));
    }

    // only count DELETE bulk operation where result was `deleted`
    return response.items().stream().filter(i -> "deleted".equals(i.result())).count();
  }

  private long getAutoSlices() {
    return operateProperties.getOpensearch().getNumberOfShards();
  }

  private Either<Throwable, ArchiveBatch> handleSearchResponse(
      final SearchResponse searchResponse,
      final Throwable error,
      final Function<Throwable, String> errorMessage) {
    if (error != null) {
      return Either.left(new OperateRuntimeException(errorMessage.apply(error), error));
    }

    final var batch = createArchiveBatch(searchResponse, DATES_AGG, INSTANCES_AGG);
    return Either.right(batch);
  }
}
