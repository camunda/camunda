/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static java.util.stream.Collectors.groupingBy;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Slices;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
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
import io.camunda.exporter.tasks.util.DateOfArchivedDocumentsUtil;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import javax.annotation.WillCloseWhenClosed;
import org.slf4j.Logger;

public final class ElasticsearchArchiverRepository extends ElasticsearchRepository
    implements ArchiverRepository {
  private static final String ALL_INDICES = "*";
  private static final String ALL_INDICES_PATTERN = ".*";
  // Matches version suffix pattern: -{major}.{minor}.{patch}_{suffix}
  // e.g. "-8.8.0_2025-02-23"
  private static final String VERSION_SUFFIX_PATTERN = "-\\d+\\.\\d+\\.\\d+_.+$";
  // Matches versioned index names with version suffixes: {name}{version-suffix}
  // e.g. "camunda-tenant-8.8.0_2025-02-23"
  private static final String VERSIONED_INDEX_PATTERN = ".+" + VERSION_SUFFIX_PATTERN;

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
  private String lastHistoricalArchiverDate = null;
  private final String zeebeIndexPrefix;

  public ElasticsearchArchiverRepository(
      final int partitionId,
      final HistoryConfiguration config,
      final RetentionConfiguration retention,
      final String indexPrefix,
      final String processInstanceIndex,
      final String batchOperationIndex,
      final String zeebeIndexPrefix,
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
    this.zeebeIndexPrefix = zeebeIndexPrefix;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
    final var searchRequest = createFinishedInstancesSearchRequest();

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            (response) -> createArchiveBatch(response, ListViewTemplate.END_DATE), executor);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
    final var searchRequest = createFinishedBatchOperationsSearchRequest();

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            (response) -> createArchiveBatch(response, BatchOperationTemplate.END_DATE), executor);
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
    final var indexWildcard = "^" + formattedPrefix + VERSIONED_INDEX_PATTERN;
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

  @Override
  public CompletableFuture<Integer> getCountOfProcessInstancesAwaitingArchival() {
    final var countRequest =
        CountRequest.of(
            cr ->
                cr.index(processInstanceIndex)
                    .query(
                        finishedProcessInstancesQuery(
                            config.getArchivingTimePoint(), partitionId)));

    return client.count(countRequest).thenApplyAsync(res -> Math.toIntExact(res.count()));
  }

  private Query finishedProcessInstancesQuery(
      final String archivingTimePoint, final int partitionId) {
    final var endDateQ =
        QueryBuilders.range(
            q -> q.date(d -> d.field(ListViewTemplate.END_DATE).lte(archivingTimePoint)));
    final var isProcessInstanceQ =
        QueryBuilders.term(
            q ->
                q.field(ListViewTemplate.JOIN_RELATION)
                    .value(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
    final var partitionQ =
        QueryBuilders.term(q -> q.field(ListViewTemplate.PARTITION_ID).value(partitionId));
    return QueryBuilders.bool(
        q -> q.filter(endDateQ).filter(isProcessInstanceQ).filter(partitionQ));
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

  private SearchRequest createFinishedInstancesSearchRequest() {
    return createSearchRequest(
        processInstanceIndex,
        finishedProcessInstancesQuery(config.getArchivingTimePoint(), partitionId),
        ListViewTemplate.END_DATE);
  }

  private CompletableFuture<Void> setIndexLifeCycleToMatchingIndices(
      final List<String> destinationIndexNames) {
    if (destinationIndexNames.isEmpty()) {
      logger.debug("No indices to set lifecycle policies for");
      return CompletableFuture.completedFuture(null);
    }

    final var defaultPolicy = retention.getPolicyName();
    final var formattedPrefix = AbstractIndexDescriptor.formatIndexPrefix(indexPrefix);

    final var policyToIndicesMap =
        destinationIndexNames.stream()
            .collect(
                groupingBy(
                    indexName -> {
                      // Start with default policy
                      String lastMatchingPolicy = defaultPolicy;
                      // Check for pattern matches - last match wins
                      for (final var policy : retention.getIndexPolicies()) {
                        for (final var indexPattern : policy.getIndices()) {
                          final var indexWithNamePattern =
                              "^" + formattedPrefix + indexPattern + VERSION_SUFFIX_PATTERN;
                          if (Pattern.compile(indexWithNamePattern).matcher(indexName).matches()) {
                            lastMatchingPolicy = policy.getPolicyName();
                          }
                        }
                      }

                      return lastMatchingPolicy;
                    }));

    // Create separate requests for each policy group
    final var requests =
        policyToIndicesMap.entrySet().stream()
            .map(
                entry -> {
                  final String policyName = entry.getKey();
                  final List<String> indices = entry.getValue();

                  logger.debug(
                      "Applying policy '{}' to {} indices: {}",
                      policyName,
                      indices.size(),
                      indices);

                  final var settingsRequest =
                      new PutIndicesSettingsRequest.Builder()
                          .settings(
                              settings ->
                                  settings.lifecycle(lifecycle -> lifecycle.name(policyName)))
                          .index(indices)
                          .allowNoIndices(true)
                          .ignoreUnavailable(true)
                          .build();

                  return client.indices().putSettings(settingsRequest);
                })
            .toList();

    // Execute all requests and combine results
    return CompletableFuture.allOf(requests.toArray(new CompletableFuture[0]))
        .thenApplyAsync(ignored -> null, executor);
  }

  private CompletableFuture<ArchiveBatch> createArchiveBatch(
      final SearchResponse<?> response, final String field) {
    final var hits = response.hits().hits();
    if (hits.isEmpty()) {
      return CompletableFuture.completedFuture(new ArchiveBatch(null, List.of()));
    }

    final String endDate = hits.getFirst().fields().get(field).toJson().asJsonArray().getString(0);

    final CompletableFuture<String> dateFuture =
        (lastHistoricalArchiverDate == null)
            ? DateOfArchivedDocumentsUtil.getLastHistoricalArchiverDate(
                fetchMatchingIndexes(ALL_INDICES_PATTERN), zeebeIndexPrefix)
            : CompletableFuture.completedFuture(lastHistoricalArchiverDate);

    return dateFuture.thenApply(
        date -> {
          lastHistoricalArchiverDate =
              DateOfArchivedDocumentsUtil.calculateDateOfArchiveIndexForBatch(
                  endDate, date, config.getRolloverInterval(), config.getElsRolloverDateFormat());

          final var ids =
              hits.stream()
                  .takeWhile(
                      hit ->
                          hit.fields()
                              .get(field)
                              .toJson()
                              .asJsonArray()
                              .getString(0)
                              .equals(endDate))
                  .map(Hit::id)
                  .toList();

          return new ArchiveBatch(lastHistoricalArchiverDate, ids);
        });
  }

  private TermsQuery buildIdTermsQuery(final String idFieldName, final List<String> idValues) {
    return QueryBuilders.terms()
        .field(idFieldName)
        .terms(terms -> terms.value(idValues.stream().map(FieldValue::of).toList()))
        .build();
  }

  private SearchRequest createFinishedBatchOperationsSearchRequest() {
    final var endDateQ =
        QueryBuilders.range(
            q ->
                q.date(
                    d ->
                        d.field(BatchOperationTemplate.END_DATE)
                            .lte(config.getArchivingTimePoint())));

    return createSearchRequest(batchOperationIndex, endDateQ, BatchOperationTemplate.END_DATE);
  }

  private SearchRequest createSearchRequest(
      final String indexName, final Query filterQuery, final String sortField) {
    logger.trace(
        "Create search request against index '{}', with filter '{}' and sortField '{}'",
        indexName,
        filterQuery.toString(),
        sortField);

    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .source(source -> source.fetch(false))
        .fields(fields -> fields.field(sortField).format(config.getElsRolloverDateFormat()))
        .query(query -> query.bool(q -> q.filter(filterQuery)))
        .sort(sort -> sort.field(field -> field.field(sortField).order(SortOrder.Asc)))
        .size(config.getRolloverBatchSize())
        .build();
  }
}
