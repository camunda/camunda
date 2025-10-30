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
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.index.reindex.AbstractBulkByScrollRequest.AUTO_SLICES;
import static org.elasticsearch.search.aggregations.AggregationBuilders.dateHistogram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;
import static org.elasticsearch.search.aggregations.PipelineAggregatorBuilders.bucketSort;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.Either;
import io.camunda.operate.util.ElasticsearchUtil;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
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
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
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

  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS =
      30000; // this scroll timeout value is used for reindex and delete queries
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepository.class);
  private static final int UPDATE_RETRY_COUNT = 3;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private ListViewTemplate processInstanceTemplate;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;
  @Autowired private OperateProperties operateProperties;
  @Autowired private Metrics metrics;
  @Autowired private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

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

  private CompletableFuture<SearchResponse> sendSearchRequest(final SearchRequest searchRequest) {
    return ElasticsearchUtil.searchAsync(searchRequest, archiverExecutor, esClient);
  }

  private CompletableFuture<ArchiveBatch> searchAsync(
      final SearchRequest searchRequest, final Function<Throwable, String> errorMessage) {
    final var batchFuture = new CompletableFuture<ArchiveBatch>();

    final var startTimer = Timer.start();
    sendSearchRequest(searchRequest)
        .whenComplete(
            (response, e) -> {
              final var timer = getArchiverQueryTimer();
              startTimer.stop(timer);

              final var result = handleSearchResponse(response, e, errorMessage);
              result.ifRightOrLeft(batchFuture::complete, batchFuture::completeExceptionally);
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
    final var aggregation = createFinishedInstancesAggregation(DATES_AGG, INSTANCES_AGG);
    final var searchRequest = createFinishedInstancesSearchRequest(aggregation, partitionIds);
    final Function<Throwable, String> errorMessage =
        t ->
            format(
                "Exception occurred, while obtaining finished batch operations: %s",
                t.getMessage());
    return searchAsync(searchRequest, errorMessage);
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
  public void setIndexLifeCycle(final String destinationIndexName) {
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
      final Function<Throwable, String> errorMessage) {
    if (error != null) {
      return Either.left(new OperateRuntimeException(errorMessage.apply(error), error));
    }

    final var batch = createArchiveBatch(searchResponse, DATES_AGG, INSTANCES_AGG);
    return Either.right(batch);
  }

  private Timer getArchiverQueryTimer() {
    return metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY);
  }

  private SearchRequest createFinishedInstancesSearchRequest(
      final AggregationBuilder agg, final List<Integer> partitionIds) {
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
                    .aggregation(agg)
                    .fetchSource(false)
                    .size(0)
                    .sort(ListViewTemplate.END_DATE, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug(
        "Finished process instances for archiving request: \n{}\n and aggregation: \n{}",
        q.toString(),
        agg.toString());
    return searchRequest;
  }

  private AggregationBuilder createFinishedInstancesAggregation(
      final String datesAggName, final String instancesAggName) {
    return dateHistogram(datesAggName)
        .field(ListViewTemplate.END_DATE)
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
                .sort(ListViewTemplate.ID, SortOrder.ASC)
                .fetchSource(ListViewTemplate.ID, null));
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
