/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deleter;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Slices;
import co.elastic.clients.elasticsearch._types.SlicesCalculation;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.DeleteHistoryIndex;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.WillCloseWhenClosed;
import org.slf4j.Logger;

public class ElasticsearchDeleterRepository extends ElasticsearchRepository
    implements DeleterRepository {
  private static final Time REINDEX_SCROLL_TIMEOUT = Time.of(t -> t.time("30s"));
  private static final Slices AUTO_SLICES =
      Slices.of(slices -> slices.computed(SlicesCalculation.Auto));
  private final int partitionId;
  private final HistoryConfiguration config;
  private final IndexDescriptor indexDescriptor;
  private final CamundaExporterMetrics metrics;
  private final String lastHistoricalArchiverDate = null;
  private final Cache<String, String> lifeCyclePolicyApplied =
      Caffeine.newBuilder().maximumSize(200).build();

  public ElasticsearchDeleterRepository(
      final int partitionId,
      final HistoryConfiguration config,
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final ElasticsearchAsyncClient client,
      final Executor executor,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.config = config;
    indexDescriptor =
        resourceProvider.getIndexDescriptors().stream()
            .filter(DeleteHistoryIndex.class::isInstance)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No DeleteHistoryIndex descriptor found"));

    this.metrics = metrics;
  }

  @Override
  public CompletableFuture<Boolean> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys) {
    final var deleteRequest =
        createDeleteRequest(sourceIndexName, idFieldName, processInstanceKeys);
    return client
        .deleteByQuery(deleteRequest)
        .thenComposeAsync(
            (response) -> {
              if (!response.failures().isEmpty()) {
                return CompletableFuture.completedFuture(false);
              }

              return CompletableFuture.completedFuture(true);
            });
  }

  @Override
  public CompletableFuture<DeleteBatch> getNextBatch() {
    final var searchRequest = createSearchRequest();

    final var timer = Timer.start();
    return client
        .search(searchRequest, Object.class)
        // TODO fix metrics for deletion
        .whenCompleteAsync((ignored, error) -> metrics.measureArchiverSearch(timer), executor)
        .thenComposeAsync(
            (response) -> {
              final var hits = response.hits().hits();
              if (hits.isEmpty()) {
                return CompletableFuture.completedFuture(new DeleteBatch(null, List.of()));
              }

              final var ids = hits.stream().map(Hit::id).toList();

              return CompletableFuture.completedFuture(new DeleteBatch("todo", ids));
            },
            executor);
  }

  private DeleteByQueryRequest createDeleteRequest(
      final String indexName, final String idFieldName, final List<String> ids) {
    // TODO add multiple indices. It probably won't work, but it seems the API supports it.
    return new DeleteByQueryRequest.Builder()
        .index(indexName)
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .query(
            q ->
                q.terms(
                    t ->
                        t.field(idFieldName)
                            .terms(v -> v.value(ids.stream().map(FieldValue::of).toList()))))
        .build();
  }

  private SearchRequest createSearchRequest() {
    return createSearchRequest(
        indexDescriptor.getFullQualifiedName() // TODO change to our delete index
        );
  }

  private SearchRequest createSearchRequest(final String indexName) {
    // TODO add sorting
    logger.trace("Create search request against index '{}'", indexName);

    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .source(source -> source.fetch(false))
        .size(config.getRolloverBatchSize())
        .build();
  }
}
