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
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.createIndexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteByQueryRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getIndexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.reindexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.time;
import static io.camunda.operate.util.FutureHelper.withTimer;
import static java.lang.String.format;
import static org.opensearch.client.opensearch._types.SortOrder.Asc;

import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchArchiverRepository implements ArchiverRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchArchiverRepository.class);
  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private ListViewTemplate processInstanceTemplate;
  @Autowired private OperateProperties operateProperties;
  @Autowired private Metrics metrics;

  private <R> ArchiveBatch createArchiveBatch(
      final SearchResponse<R> searchResponse,
      final String datesAggName,
      final String instancesAggName) {
    final var buckets =
        searchResponse.aggregations().get(datesAggName).dateHistogram().buckets().keyed();
    if (buckets.size() > 0) {
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
      final Function<Exception, String> errorMessage) {
    return withTimer(
            metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_QUERY),
            () ->
                richOpenSearchClient
                    .async()
                    .doc()
                    .search(searchRequestBuilder, Object.class, errorMessage))
        .thenApply(response -> createArchiveBatch(response, DATES_AGG, INSTANCES_AGG));
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
        e -> "Failed to search in " + batchOperationTemplate.getFullQualifiedName());
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
            .waitForCompletion(false)
            .slices(getAutoSlices())
            .conflicts(Conflicts.Proceed);

    return withTimer(
        metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_DELETE_QUERY),
        () ->
            richOpenSearchClient
                .async()
                .doc()
                .delete(
                    deleteByQueryRequestBuilder,
                    e -> "Failed to delete asynchronously from " + sourceIndexName)
                .thenAccept(
                    response ->
                        richOpenSearchClient
                            .async()
                            .task()
                            .totalImpactedByTask(response.task(), archiverExecutor)));
  }

  @Override
  public CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys) {
    if (!richOpenSearchClient.index().indexExists(destinationIndexName)) {
      createIndexAs(sourceIndexName, destinationIndexName);
    }

    final String errorMessage =
        format(
            "Failed to reindex asynchronously from %s to %s!",
            sourceIndexName, destinationIndexName);
    final Query sourceQuery =
        stringTerms(idFieldName, processInstanceKeys.stream().map(Object::toString).toList());
    final var reindexRequest =
        reindexRequestBuilder(sourceIndexName, sourceQuery, destinationIndexName)
            .waitForCompletion(false)
            .scroll(time(OpenSearchDocumentOperations.INTERNAL_SCROLL_KEEP_ALIVE_MS))
            .slices(getAutoSlices())
            .conflicts(Conflicts.Proceed);

    return withTimer(
        metrics.getTimer(Metrics.TIMER_NAME_ARCHIVER_REINDEX_QUERY),
        () ->
            richOpenSearchClient
                .async()
                .index()
                .reindex(reindexRequest, e -> errorMessage)
                .thenAccept(
                    response ->
                        richOpenSearchClient
                            .async()
                            .task()
                            .totalImpactedByTask(response.task(), archiverExecutor)));
  }

  private long getAutoSlices() {
    return operateProperties.getOpensearch().getNumberOfShards();
  }

  private void createIndexAs(final String sourceIndexName, final String destinationIndexName) {
    final var srcIndex =
        richOpenSearchClient
            .index()
            .get(getIndexRequestBuilder(sourceIndexName))
            .get(sourceIndexName);
    richOpenSearchClient
        .index()
        .createIndexWithRetries(createIndexRequestBuilder(destinationIndexName, srcIndex).build());
  }
}
