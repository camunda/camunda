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
import static io.camunda.operate.schema.SchemaManager.INDEX_LIFECYCLE_NAME;
import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static java.lang.String.format;
import static org.elasticsearch.action.DocWriteResponse.Result.DELETED;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.PipelineAggregatorBuilders.bucketSort;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.operate.Metrics;
import io.camunda.operate.archiver.ArchiveByIdTaskSupplier.IdWithRouting;
import io.camunda.operate.archiver.util.DateOfArchivedDocumentsUtil;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.Either;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchArchiverRepository implements ArchiverRepository {

  private static final int INTERNAL_SCROLL_KEEP_ALIVE_MS =
      30000; // this scroll timeout value is used for reindex and delete queries
  private static final String TOTALS_AGG_NAME = "total_pending_archive_count";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepository.class);
  private static final int UPDATE_RETRY_COUNT = 3;

  private final ThreadPoolTaskScheduler archiverExecutor;
  private final OperateProperties operateProperties;
  private final Metrics metrics;
  private final RestHighLevelClient esClient;
  private final BatchOperationTemplate batchOperationTemplate;
  private final ListViewTemplate processInstanceTemplate;
  private final DecisionInstanceTemplate decisionInstanceTemplate;

  private final Cache<String, Boolean> ilmApplied =
      Caffeine.newBuilder().maximumSize(200).expireAfterWrite(1, TimeUnit.HOURS).build();

  @Autowired
  public ElasticsearchArchiverRepository(
      @Qualifier("archiverThreadPoolExecutor") final ThreadPoolTaskScheduler archiverExecutor,
      final OperateProperties operateProperties,
      final Metrics metrics,
      final RestHighLevelClient esClient,
      final ListViewTemplate processInstanceTemplate,
      final BatchOperationTemplate batchOperationTemplate,
      final DecisionInstanceTemplate decisionInstanceTemplate) {
    this.archiverExecutor = archiverExecutor;
    this.operateProperties = operateProperties;
    this.metrics = metrics;
    this.esClient = esClient;
    this.processInstanceTemplate = processInstanceTemplate;
    this.batchOperationTemplate = batchOperationTemplate;
    this.decisionInstanceTemplate = decisionInstanceTemplate;
  }

  private ArchiveBatch createArchiveBatch(
      final SearchResponse searchResponse,
      final String datesAggName,
      final String instancesAggName) {
    final List<? extends Histogram.Bucket> buckets =
        ((Histogram) searchResponse.getAggregations().get(datesAggName)).getBuckets();

    if (buckets.size() > 0) {
      final Histogram.Bucket bucket = buckets.get(0);
      final String finishDate = bucket.getKeyAsString();
      final SearchHits hits = ((TopHits) bucket.getAggregations().get(instancesAggName)).getHits();
      final ArrayList<Object> ids =
          Arrays.stream(hits.getHits())
              .collect(
                  ArrayList::new,
                  (list, hit) -> list.add(hit.getId()),
                  (list1, list2) -> list1.addAll(list2));
      return new ArchiveBatch(finishDate, ids);
    } else {
      return null;
    }
  }

  private ArchiveBatch createArchiveBatchFromHits(
      final SearchResponse searchResponse,
      final String dateField,
      final String rolloverInterval,
      final String dateFormat) {
    final SearchHit[] hits = searchResponse.getHits().getHits();
    if (hits.length == 0) {
      return null;
    }
    DateOfArchivedDocumentsUtil.validateRolloverConfiguration(rolloverInterval, dateFormat);
    final String firstEndDate = hits[0].field(dateField).getValue();
    final String bucketStart =
        DateOfArchivedDocumentsUtil.getBucketStart(firstEndDate, rolloverInterval, dateFormat);
    final String nextBucketStart =
        DateOfArchivedDocumentsUtil.getNextBucketStart(firstEndDate, rolloverInterval, dateFormat);
    // hits are sorted by endDate ASC, so the first hit is the earliest and defines the bucket.
    // Lower bound is therefore satisfied automatically; only the exclusive upper bound is checked.
    final List<Object> ids =
        Arrays.stream(hits)
            .takeWhile(
                hit -> ((String) hit.field(dateField).getValue()).compareTo(nextBucketStart) < 0)
            .map(hit -> (Object) hit.getId())
            .toList();
    return new ArchiveBatch(bucketStart, ids, getTotalPendingCount(searchResponse));
  }

  private AggregationBuilder getTotalPendingCountAggregation(final List<Integer> partitionIds) {
    return AggregationBuilders.terms(TOTALS_AGG_NAME)
        .field(ListViewTemplate.PARTITION_ID)
        .size(partitionIds.size());
  }

  private Map<Integer, Long> getTotalPendingCount(final SearchResponse searchResponse) {
    final Aggregations aggs = searchResponse.getAggregations();
    if (aggs != null
        && aggs.get(TOTALS_AGG_NAME) instanceof final ParsedLongTerms totalsByPartition) {
      return totalsByPartition.getBuckets().stream()
          .collect(
              Collectors.toMap(bucket -> bucket.getKeyAsNumber().intValue(), Bucket::getDocCount));
    }
    return Map.of();
  }

  private CompletableFuture<SearchResponse> sendSearchRequest(final SearchRequest searchRequest) {
    return ElasticsearchUtil.searchAsync(searchRequest, archiverExecutor, esClient);
  }

  private CompletableFuture<ArchiveBatch> searchAsync(
      final SearchRequest searchRequest, final Function<Throwable, String> errorMessage) {
    return searchAsync(
        searchRequest, errorMessage, r -> createArchiveBatch(r, DATES_AGG, INSTANCES_AGG));
  }

  private CompletableFuture<ArchiveBatch> searchAsync(
      final SearchRequest searchRequest,
      final Function<Throwable, String> errorMessage,
      final Function<SearchResponse, ArchiveBatch> batchExtractor) {
    final var batchFuture = new CompletableFuture<ArchiveBatch>();

    final var startTimer = Timer.start();
    sendSearchRequest(searchRequest)
        .whenComplete(
            (response, e) -> {
              try {
                final var timer = getArchiverQueryTimer();
                startTimer.stop(timer);
                final var result = handleSearchResponse(response, e, errorMessage, batchExtractor);
                result.ifRightOrLeft(batchFuture::complete, batchFuture::completeExceptionally);
              } catch (final Exception ex) {
                // Ensure the future is always completed; an uncaught exception in whenComplete
                // would otherwise leave batchFuture pending indefinitely.
                batchFuture.completeExceptionally(ex);
              }
            });

    return batchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationNextBatch() {
    final var aggregation = createFinishedBatchOperationsAggregation(DATES_AGG, INSTANCES_AGG);
    final var searchRequest = createFinishedBatchOperationsSearchRequest(aggregation);
    final Function<Throwable, String> errorMessage =
        t ->
            format(
                "Exception occurred, while obtaining finished batch operations: %s",
                t.getMessage());
    return searchAsync(searchRequest, errorMessage);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch(
      final List<Integer> partitionIds) {
    final var searchRequest = createFinishedInstancesSearchRequest(partitionIds);
    final Function<Throwable, String> errorMessage =
        t ->
            format(
                "Exception occurred, while obtaining finished process instances: %s",
                t.getMessage());
    final var interval = operateProperties.getArchiver().getRolloverInterval();
    final var dateFormat = operateProperties.getArchiver().getElsRolloverDateFormat();
    return searchAsync(
        searchRequest,
        errorMessage,
        response ->
            createArchiveBatchFromHits(response, ListViewTemplate.END_DATE, interval, dateFormat));
  }

  @Override
  public CompletableFuture<ArchiveBatch> getStandaloneDecisionNextBatch(
      final List<Integer> partitionIds) {
    final var aggregation = createStandaloneDecisionInstancesAggregation(DATES_AGG, INSTANCES_AGG);
    final var searchRequest =
        createStandaloneDecisionInstancesSearchRequest(aggregation, partitionIds);
    final Function<Throwable, String> errorMessage =
        t ->
            format(
                "Exception occurred, while obtaining finished standalone decision evaluations: %s",
                t.getMessage());
    return searchAsync(searchRequest, errorMessage);
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
        new ArchiveByIdTaskSupplier<>(
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
          && esClient
              .indices()
              .exists(new GetIndexRequest(destinationIndexName), RequestOptions.DEFAULT)) {
        esClient
            .indices()
            .putSettings(
                new UpdateSettingsRequest(destinationIndexName)
                    .settings(
                        Settings.builder()
                            .put(INDEX_LIFECYCLE_NAME, OPERATE_DELETE_ARCHIVED_INDICES)
                            .build()),
                RequestOptions.DEFAULT);
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
    final var deleteFuture = new CompletableFuture<Long>();
    final var deleteRequest =
        createDeleteByQueryRequestWithDefaults(sourceIndexName)
            .setQuery(termsQuery(idFieldName, processInstanceKeys))
            .setMaxRetries(UPDATE_RETRY_COUNT);

    final var startTimer = Timer.start();
    ElasticsearchUtil.deleteAsync(deleteRequest, archiverExecutor, esClient)
        .whenComplete(
            (response, e) -> {
              final var timer = getArchiverDeleteQueryTimer();
              startTimer.stop(timer);
              final var result = handleResponse(response, e, sourceIndexName, "delete");
              result.ifLeft(
                  throwable -> trackMetricForDeleteFailures(processInstanceKeys, throwable));
              result.ifRightOrLeft(deleteFuture::complete, deleteFuture::completeExceptionally);
            });
    return deleteFuture.thenApply(ok -> null);
  }

  @Override
  public CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys) {
    final var reindexFuture = new CompletableFuture<Void>();
    final var reindexRequest =
        createReindexRequestWithDefaults()
            .setSourceIndices(sourceIndexName)
            .setDestIndex(destinationIndexName)
            .setSourceQuery(termsQuery(idFieldName, processInstanceKeys));

    final var startTimer = Timer.start();

    ElasticsearchUtil.reindexAsync(archiverExecutor, reindexRequest, esClient)
        .thenAccept(
            ignore -> {
              final var reindexTimer = getArchiverReindexQueryTimer();
              startTimer.stop(reindexTimer);
              reindexFuture.complete(null);
            })
        .exceptionally(
            (e) -> {
              trackMetricForReindexFailures(processInstanceKeys, e);
              reindexFuture.completeExceptionally(e);
              return null;
            });
    return reindexFuture;
  }

  @VisibleForTesting
  CompletableFuture<ArchiveByIdTaskSupplier.ArchiveDocIdsBatch<Object>> getArchiveDocIdsBatch(
      final String sourceIndexName,
      final Map<String, List<Object>> keysByField,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters,
      final int batchSize,
      final List<Object> searchAfter,
      final Executor executor) {
    final var boolQ = boolQuery();
    keysByField.forEach((field, vals) -> boolQ.filter(termsQuery(field, vals)));
    inclusionFilters.forEach((field, val) -> boolQ.filter(termQuery(field, val)));
    exclusionFilters.forEach((field, val) -> boolQ.mustNot(termQuery(field, val)));

    final var searchSource =
        new SearchSourceBuilder()
            .trackTotalHits(false)
            .query(constantScoreQuery(boolQ))
            .sort("id", SortOrder.ASC)
            .size(batchSize)
            .fetchSource(false);
    if (!searchAfter.isEmpty()) {
      searchSource.searchAfter(searchAfter.toArray());
    }

    final SearchRequest searchRequest =
        new SearchRequest(sourceIndexName)
            .source(searchSource)
            .allowPartialSearchResults(false)
            .requestCache(false);

    LOGGER.trace(
        "Getting archive doc IDs batch from index '{}' with query '{}'",
        sourceIndexName,
        searchSource.query());

    final var timer = Timer.start();
    return ElasticsearchUtil.searchAsync(searchRequest, executor, esClient)
        .whenCompleteAsync(
            (ignored, error) -> timer.stop(getArchiverDocIdsBatchQueryTimer()), executor)
        .thenApply(
            response -> {
              final var hits = response.getHits().getHits();
              if (hits.length == 0) {
                return ArchiveByIdTaskSupplier.ArchiveDocIdsBatch.empty();
              }
              return ArchiveByIdTaskSupplier.ArchiveDocIdsBatch.from(
                  Arrays.stream(hits)
                      .map(
                          h -> {
                            final DocumentField rf = h.getMetadataFields().get("_routing");
                            return new IdWithRouting(h.getId(), rf != null ? rf.getValue() : null);
                          })
                      .toList(),
                  Arrays.asList(hits[hits.length - 1].getSortValues()));
            });
  }

  private Timer getArchiverDocIdsBatchQueryTimer() {
    return metrics.getHistogram(
        Metrics.TIMER_NAME_ARCHIVER_REQUEST_DURATION, Metrics.TAG_KEY_TYPE, "search");
  }

  private CompletableFuture<Long> reindexDocumentsById(
      final String sourceIndexName,
      final String destinationIndexName,
      final List<IdWithRouting> docs,
      final Executor executor) {
    final var docIds = docs.stream().map(IdWithRouting::id).toArray(String[]::new);
    final var reindexRequest =
        createReindexRequestWithDefaults()
            .setSourceIndices(sourceIndexName)
            .setDestIndex(destinationIndexName)
            .setSourceQuery(idsQuery().addIds(docIds));

    final var timer = Timer.start();
    return ElasticsearchUtil.reindexAsync(archiverExecutor, reindexRequest, esClient)
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
      final String sourceIndex, final BulkByScrollResponse response) {
    if (response.isTimedOut()) {
      throw new IllegalStateException("Reindex request from %s timed out".formatted(sourceIndex));
    }
    final var failures = response.getBulkFailures().size() + response.getSearchFailures().size();
    if (failures > 0) {
      throw new IllegalStateException(
          "Reindex request from %s index completed with %d failures"
              .formatted(sourceIndex, failures));
    }
  }

  private static long getReindexedDocumentsCount(final BulkByScrollResponse response) {
    return response.getCreated() + response.getUpdated();
  }

  CompletableFuture<Long> deleteDocumentsById(
      final String sourceIndexName, final List<IdWithRouting> docs, final Executor executor) {
    final var bulkRequest = new BulkRequest();
    docs.forEach(
        doc ->
            bulkRequest.add(new DeleteRequest(sourceIndexName, doc.id()).routing(doc.routing())));

    final var future = new CompletableFuture<BulkResponse>();
    final var timer = Timer.start();
    esClient.bulkAsync(
        bulkRequest,
        RequestOptions.DEFAULT,
        ActionListener.wrap(future::complete, future::completeExceptionally));
    return future
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
  }

  private long getDeletedDocCount(final String sourceIndex, final BulkResponse response) {
    if (response.hasFailures()) {
      final long errorCount =
          Arrays.stream(response.getItems()).filter(BulkItemResponse::isFailed).count();
      throw new IllegalStateException(
          "Deleting reindexed documents from %s index completed with %d failures"
              .formatted(sourceIndex, errorCount));
    }

    // only count DELETE bulk operation where result was `deleted`
    return Arrays.stream(response.getItems())
        .filter(i -> DELETED.equals(i.getResponse().getResult()))
        .count();
  }

  private SearchRequest createFinishedBatchOperationsSearchRequest(final AggregationBuilder agg) {
    final QueryBuilder endDateQ =
        rangeQuery(BatchOperationTemplate.END_DATE)
            .lte(operateProperties.getArchiver().getArchivingTimepoint());
    final ConstantScoreQueryBuilder q = constantScoreQuery(endDateQ);

    final SearchRequest searchRequest =
        new SearchRequest(batchOperationTemplate.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .aggregation(agg)
                    .fetchSource(false)
                    .size(0)
                    .sort(BatchOperationTemplate.END_DATE, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug(
        "Finished batch operations for archiving request: \n{}\n and aggregation: \n{}",
        q.toString(),
        agg.toString());
    return searchRequest;
  }

  private AggregationBuilder createFinishedBatchOperationsAggregation(
      final String datesAggName, final String instancesAggName) {
    return dateHistogram(datesAggName)
        .field(BatchOperationTemplate.END_DATE)
        .calendarInterval(
            new DateHistogramInterval(operateProperties.getArchiver().getRolloverInterval()))
        .format(operateProperties.getArchiver().getElsRolloverDateFormat())
        .keyed(true) // get result as a map (not an array)
        // we want to get only one bucket at a time
        .subAggregation(
            bucketSort("datesSortedAgg", Arrays.asList(new FieldSortBuilder("_key"))).size(1))
        // we need process instance ids, also taking into account batch size
        .subAggregation(
            topHits(instancesAggName)
                .size(operateProperties.getArchiver().getRolloverBatchSize())
                .sort(BatchOperationTemplate.ID, SortOrder.ASC)
                .fetchSource(BatchOperationTemplate.ID, null));
  }

  private Either<Throwable, ArchiveBatch> handleSearchResponse(
      final SearchResponse searchResponse,
      final Throwable error,
      final Function<Throwable, String> errorMessage,
      final Function<SearchResponse, ArchiveBatch> batchExtractor) {
    if (error != null) {
      return Either.left(new OperateRuntimeException(errorMessage.apply(error), error));
    }
    return Either.right(batchExtractor.apply(searchResponse));
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }

  private SearchRequest createFinishedInstancesSearchRequest(final List<Integer> partitionIds) {
    final QueryBuilder endDateQ =
        rangeQuery(ListViewTemplate.END_DATE)
            .lte(operateProperties.getArchiver().getArchivingTimepoint());
    final TermQueryBuilder isProcessInstanceQ =
        termQuery(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
    final TermsQueryBuilder partitionQ = termsQuery(ListViewTemplate.PARTITION_ID, partitionIds);
    final ConstantScoreQueryBuilder q =
        constantScoreQuery(ElasticsearchUtil.joinWithAnd(endDateQ, isProcessInstanceQ, partitionQ));

    final SearchRequest searchRequest =
        new SearchRequest(processInstanceTemplate.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .fetchSource(false)
                    .docValueField(
                        ListViewTemplate.END_DATE,
                        operateProperties.getArchiver().getElsRolloverDateFormat())
                    .size(operateProperties.getArchiver().getRolloverBatchSize())
                    .sort(ListViewTemplate.END_DATE, SortOrder.ASC)
                    .aggregation(getTotalPendingCountAggregation(partitionIds)))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug("Finished process instances for archiving request: \n{}", q.toString());
    return searchRequest;
  }

  private AggregationBuilder createStandaloneDecisionInstancesAggregation(
      final String datesAggName, final String instancesAggName) {
    return dateHistogram(datesAggName)
        .field(DecisionInstanceTemplate.EVALUATION_DATE)
        .calendarInterval(
            new DateHistogramInterval(operateProperties.getArchiver().getRolloverInterval()))
        .format(operateProperties.getArchiver().getElsRolloverDateFormat())
        .keyed(true) // get result as a map (not an array)
        // we want to get only one bucket at a time
        .subAggregation(
            bucketSort("datesSortedAgg", Arrays.asList(new FieldSortBuilder("_key"))).size(1))
        // we need process instance ids, also taking into account batch size
        .subAggregation(
            topHits(instancesAggName)
                .size(operateProperties.getArchiver().getRolloverBatchSize())
                .sort(DecisionInstanceTemplate.ID, SortOrder.ASC)
                .fetchSource(DecisionInstanceTemplate.ID, null));
  }

  private SearchRequest createStandaloneDecisionInstancesSearchRequest(
      final AggregationBuilder agg, final List<Integer> partitionIds) {
    final QueryBuilder endDateQ =
        rangeQuery(DecisionInstanceTemplate.EVALUATION_DATE)
            .lte(operateProperties.getArchiver().getArchivingTimepoint());
    final TermsQueryBuilder partitionQ =
        termsQuery(DecisionInstanceTemplate.PARTITION_ID, partitionIds);
    // standalone decision instances have processInstanceKey = -1
    final TermQueryBuilder standaloneDecisionInstanceQ =
        termQuery(DecisionInstanceTemplate.PROCESS_INSTANCE_KEY, -1);

    final ConstantScoreQueryBuilder q =
        constantScoreQuery(
            ElasticsearchUtil.joinWithAnd(endDateQ, partitionQ, standaloneDecisionInstanceQ));

    final SearchRequest searchRequest =
        new SearchRequest(decisionInstanceTemplate.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .aggregation(agg)
                    .fetchSource(false)
                    .size(0)
                    .sort(DecisionInstanceTemplate.EVALUATION_DATE, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug(
        "Finished standalone decision evaluations for archiving request: \n{}\n and aggregation: \n{}",
        q,
        agg.toString());
    return searchRequest;
  }

  private ReindexRequest createReindexRequestWithDefaults() {
    final var reindexRequest =
        new ReindexRequest()
            .setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
            .setAbortOnVersionConflict(false)
            .setSlices(AUTO_SLICES);
    return reindexRequest;
  }

  private Timer getArchiverReindexQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY);
  }

  private Timer getArchiverDeleteQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY);
  }

  private DeleteByQueryRequest createDeleteByQueryRequestWithDefaults(final String index) {
    final var deleteRequest = new DeleteByQueryRequest(index);
    return applyDefaultSettings(deleteRequest);
  }

  private <T extends AbstractBulkByScrollRequest<T>> T applyDefaultSettings(final T request) {
    return request
        .setScroll(TimeValue.timeValueMillis(INTERNAL_SCROLL_KEEP_ALIVE_MS))
        .setAbortOnVersionConflict(false)
        .setSlices(AUTO_SLICES);
  }

  private void trackMetricForReindexFailures(
      final List<Object> processInstanceKeys, final Throwable e) {
    LOGGER.error(
        "Failed while trying to reindex documents during the archival process for the following process instance keys [{}]",
        processInstanceKeys);

    metrics.recordCounts(
        Metrics.COUNTER_NAME_REINDEX_FAILURES,
        1,
        "exception",
        e.getCause().getClass().getSimpleName());
  }

  private void trackMetricForDeleteFailures(
      final List<Object> processInstanceKeys, final Throwable e) {

    LOGGER.error(
        "Failed while trying to delete documents during the archival process for the following process instance keys [{}]",
        processInstanceKeys);

    metrics.recordCounts(
        Metrics.COUNTER_NAME_DELETE_FAILURES,
        1,
        "exception",
        e.getCause().getClass().getSimpleName());
  }

  private Either<Throwable, Long> handleResponse(
      final BulkByScrollResponse response,
      final Throwable error,
      final String sourceIndexName,
      final String operation) {
    if (error != null) {
      final var message =
          String.format(
              "Exception occurred while performing operation %s on source index %s. The error was: %s",
              operation, sourceIndexName, error.getMessage());
      return Either.left(new OperateRuntimeException(message, error));
    }

    final var bulkFailures = response.getBulkFailures();
    if (!bulkFailures.isEmpty()) {
      LOGGER.error(
          "Failures occurred when performing operation: {} on source index {}. See details below.",
          operation,
          sourceIndexName);
      bulkFailures.forEach(f -> LOGGER.error(f.toString()));
      return Either.left(new ArchiverException(String.format("Operation %s failed", operation)));
    }

    LOGGER.debug(
        "Operation {} succeeded on source index {}. Response: {}",
        operation,
        sourceIndexName,
        response);
    return Either.right(response.getTotal());
  }
}
