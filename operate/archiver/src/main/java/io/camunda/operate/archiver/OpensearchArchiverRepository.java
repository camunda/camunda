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

import io.camunda.operate.Metrics;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchArchiverRepository implements ArchiverRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchArchiverRepository.class);
  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("openSearchAsyncClient")
  protected OpenSearchAsyncClient osAsyncClient;

  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private ListViewTemplate processInstanceTemplate;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;
  @Autowired private OperateProperties operateProperties;
  @Autowired private Metrics metrics;

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
  public void setIndexLifeCycle(final String destinationIndexName) {
    try {
      if (operateProperties.getArchiver().isIlmEnabled()
          && richOpenSearchClient.index().indexExists(destinationIndexName)) {
        richOpenSearchClient
            .ism()
            .addPolicyToIndex(destinationIndexName, OPERATE_DELETE_ARCHIVED_INDICES);
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
