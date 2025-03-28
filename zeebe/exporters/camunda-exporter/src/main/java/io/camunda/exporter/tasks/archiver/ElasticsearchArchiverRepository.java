/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Slices;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.reindex.Source;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import javax.annotation.WillCloseWhenClosed;
import org.slf4j.Logger;

public final class ElasticsearchArchiverRepository extends ElasticsearchRepository
    implements ArchiverRepository {
  private static final String DATES_AGG = "datesAgg";
  private static final String INSTANCES_AGG = "instancesAgg";
  private static final String DATES_SORTED_AGG = "datesSortedAgg";
  private static final String ALL_INDICES = "*";
  private static final String INDEX_WILDCARD = ".+-\\d+\\.\\d+\\.\\d+_.+$";

  private static final Time REINDEX_SCROLL_TIMEOUT = Time.of(t -> t.time("30s"));
  private static final Slices AUTO_SLICES =
      Slices.of(slices -> slices.computed(SlicesCalculation.Auto));

  private final int partitionId;
  private final HistoryConfiguration config;
  private final RetentionConfiguration retention;
  private final String indexPrefix;
  private final String processInstanceIndex;
  private final String batchOperationIndex;
  private final CamundaExporterMetrics metrics;

  private final CalendarInterval rolloverInterval;

  public ElasticsearchArchiverRepository(
      final int partitionId,
      final HistoryConfiguration config,
      final RetentionConfiguration retention,
      final String indexPrefix,
      final String processInstanceIndex,
      final String batchOperationIndex,
      @WillCloseWhenClosed final ElasticsearchAsyncClient client,
      final Executor executor,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.config = config;
    this.retention = retention;
    this.indexPrefix = indexPrefix;
    this.processInstanceIndex = processInstanceIndex;
    this.batchOperationIndex = batchOperationIndex;
    this.metrics = metrics;

    rolloverInterval = mapCalendarInterval(config.getRolloverInterval());
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
    final var aggregation =
        createFinishedEntityAggregation(ListViewTemplate.END_DATE, ListViewTemplate.ID);
    final var searchRequest = createFinishedInstancesSearchRequest(aggregation);

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(this::createArchiveBatch, executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
    final var aggregation =
        createFinishedEntityAggregation(BatchOperationTemplate.END_DATE, BatchOperationTemplate.ID);
    final var searchRequest = createFinishedBatchOperationsSearchRequest(aggregation);

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenApplyAsync(this::createArchiveBatch, executor);
  }

  @Override
  public CompletableFuture<Void> setIndexLifeCycle(final String... destinationIndexName) {
    if (!retention.isEnabled()) {
      return CompletableFuture.completedFuture(null);
    }

    return setIndexLifeCycleToMatchingIndices(List.of(destinationIndexName));
  }

  @Override
  public CompletableFuture<Void> setLifeCycleToAllIndexes() {
    if (!retention.isEnabled()) {
      return CompletableFuture.completedFuture(null);
    }
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);
    final var indexWildcard = "^" + formattedPrefix + INDEX_WILDCARD;
    return fetchMatchingIndexes(indexWildcard)
        .thenComposeAsync(this::setIndexLifeCycleToMatchingIndices, executor);
  }

  @Override
  public CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final TermsQuery termsQuery = buildIdTermsQuery(idFieldName, processInstanceKeys);
    final var request =
        new DeleteByQueryRequest.Builder()
            .index(sourceIndexName)
            .slices(AUTO_SLICES)
            .conflicts(Conflicts.Proceed)
            .query(q -> q.terms(termsQuery))
            .build();

    final var timer = Timer.start();
    return client
        .deleteByQuery(request)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverDelete(timer), executor)
        .thenApplyAsync(DeleteByQueryResponse::total, executor)
        .thenApplyAsync(ok -> null, executor);
  }

  @Override
  public CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final var source =
        new Source.Builder()
            .index(sourceIndexName)
            .query(q -> q.terms(buildIdTermsQuery(idFieldName, processInstanceKeys)))
            .build();
    final var request =
        new ReindexRequest.Builder()
            .source(source)
            .dest(dest -> dest.index(destinationIndexName))
            .conflicts(Conflicts.Proceed)
            .scroll(REINDEX_SCROLL_TIMEOUT)
            .slices(AUTO_SLICES)
            .build();

    final var timer = Timer.start();
    return client
        .reindex(request)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverReindex(timer), executor)
        .thenApplyAsync(ignored -> null, executor);
  }

  private CompletableFuture<List<String>> fetchMatchingIndexes(final String indexWildcard) {
    final Pattern indexNamePattern = Pattern.compile(indexWildcard);
    return client
        .indices()
        .get(new GetIndexRequest.Builder().index(ALL_INDICES).build())
        .thenApplyAsync(
            response ->
                response.result().keySet().stream()
                    .filter(indexName -> indexNamePattern.matcher(indexName).matches())
                    .toList(),
            executor);
  }

  private SearchRequest createFinishedInstancesSearchRequest(final Aggregation aggregation) {
    final var endDateQ =
        QueryBuilders.range(
            q ->
                q.date(
                    d -> d.field(ListViewTemplate.END_DATE).lte(config.getArchivingTimePoint())));
    final var isProcessInstanceQ =
        QueryBuilders.term(
            q ->
                q.field(ListViewTemplate.JOIN_RELATION)
                    .value(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
    final var partitionQ =
        QueryBuilders.term(q -> q.field(ListViewTemplate.PARTITION_ID).value(partitionId));
    final var combinedQuery =
        QueryBuilders.bool(q -> q.must(endDateQ, isProcessInstanceQ, partitionQ));

    return createSearchRequest(
        processInstanceIndex, combinedQuery, aggregation, ListViewTemplate.END_DATE);
  }

  private CompletableFuture<Void> setIndexLifeCycleToMatchingIndices(
      final List<String> destinationIndexNames) {
    if (destinationIndexNames.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    final var settingsRequest =
        new PutIndicesSettingsRequest.Builder()
            .settings(
                settings ->
                    settings.lifecycle(lifecycle -> lifecycle.name(retention.getPolicyName())))
            .index(destinationIndexNames)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .build();

    return client.indices().putSettings(settingsRequest).thenApplyAsync(ok -> null, executor);
  }

  private ArchiveBatch createArchiveBatch(final SearchResponse<?> search) {
    final var aggregate = search.aggregations().get(DATES_AGG);
    if (aggregate == null) {
      return null;
    }

    final List<DateHistogramBucket> buckets = aggregate.dateHistogram().buckets().array();
    if (buckets.isEmpty()) {
      return null;
    }

    final var bucket = buckets.getFirst();
    final var finishDate = bucket.keyAsString();
    final List<String> ids =
        bucket.aggregations().get(INSTANCES_AGG).topHits().hits().hits().stream()
            .map(Hit::id)
            .toList();
    return new ArchiveBatch(finishDate, ids);
  }

  private TermsQuery buildIdTermsQuery(final String idFieldName, final List<String> idValues) {
    return QueryBuilders.terms()
        .field(idFieldName)
        .terms(terms -> terms.value(idValues.stream().map(FieldValue::of).toList()))
        .build();
  }

  private CalendarInterval mapCalendarInterval(final String alias) {
    return Arrays.stream(CalendarInterval.values())
        .filter(c -> c.aliases() != null)
        .filter(c -> Arrays.binarySearch(c.aliases(), alias) >= 0)
        .findFirst()
        .orElseThrow();
  }

  private Aggregation createFinishedEntityAggregation(final String endDate, final String id) {
    final var dateAggregation =
        AggregationBuilders.dateHistogram()
            .field(endDate)
            .calendarInterval(rolloverInterval)
            .format(config.getElsRolloverDateFormat())
            .keyed(false) // get result as an array (not a map)
            .build();
    final var sortAggregation =
        AggregationBuilders.bucketSort()
            .sort(sort -> sort.field(b -> b.field("_key")))
            .size(1) // we want to get only one bucket at a time
            .build();
    final var instanceAggregation =
        AggregationBuilders.topHits()
            .size(config.getRolloverBatchSize())
            .sort(sort -> sort.field(b -> b.field(id).order(SortOrder.Asc)))
            .source(source -> source.filter(filter -> filter.includes(id)))
            .build();
    return new Aggregation.Builder()
        .dateHistogram(dateAggregation)
        .aggregations(DATES_SORTED_AGG, Aggregation.of(b -> b.bucketSort(sortAggregation)))
        .aggregations(INSTANCES_AGG, Aggregation.of(b -> b.topHits(instanceAggregation)))
        .build();
  }

  private SearchRequest createFinishedBatchOperationsSearchRequest(final Aggregation aggregation) {
    final var endDateQ =
        QueryBuilders.range(
            q ->
                q.date(
                    d ->
                        d.field(BatchOperationTemplate.END_DATE)
                            .lte(config.getArchivingTimePoint())));

    return createSearchRequest(
        batchOperationIndex, endDateQ, aggregation, BatchOperationTemplate.END_DATE);
  }

  private SearchRequest createSearchRequest(
      final String indexName,
      final Query filterQuery,
      final Aggregation aggregation,
      final String sortField) {
    logger.trace(
        "Finished entities for archiving request: \n{}\n and aggregation: \n{}",
        filterQuery.toString(),
        aggregation.toString());

    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .source(source -> source.fetch(false))
        .query(query -> query.constantScore(q -> q.filter(filterQuery)))
        .aggregations(DATES_AGG, aggregation)
        .sort(sort -> sort.field(field -> field.field(sortField).order(SortOrder.Asc)))
        .size(0)
        .build();
  }
}
