/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.WillCloseWhenClosed;
import org.slf4j.Logger;

/**
 * Elasticsearch implementation of {@link HistoryDeletionRepository} that queries the
 * history-deletion index for resources marked for deletion, filtering by partition ID.
 */
public class ElasticsearchHistoryDeletionRepository extends ElasticsearchRepository
    implements HistoryDeletionRepository {

  private final IndexDescriptor indexDescriptor;
  private final int partitionId;

  public ElasticsearchHistoryDeletionRepository(
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final ElasticsearchAsyncClient client,
      final Executor executor,
      final Logger logger,
      final int partitionId) {
    super(client, executor, logger);
    indexDescriptor =
        resourceProvider.getIndexDescriptors().stream()
            .filter(HistoryDeletionIndex.class::isInstance)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No HistoryDeletionIndex descriptor found"));
    this.partitionId = partitionId;
  }

  @Override
  public CompletableFuture<HistoryDeletionBatch> getNextBatch() {
    final var searchRequest = createSearchRequest();

    return client
        .search(searchRequest, HistoryDeletionEntity.class)
        .thenComposeAsync(
            (response) -> {
              final var hits = response.hits().hits();
              if (hits.isEmpty()) {
                return CompletableFuture.completedFuture(new HistoryDeletionBatch(Map.of()));
              }
              final var ids =
                  hits.stream()
                      .collect(
                          Collectors.toMap(
                              Hit::id,
                              hit -> Objects.requireNonNull(hit.source()).getResourceType()));
              return CompletableFuture.completedFuture(new HistoryDeletionBatch(ids));
            },
            executor);
  }

  private SearchRequest createSearchRequest() {
    return createSearchRequest(indexDescriptor.getFullQualifiedName());
  }

  private SearchRequest createSearchRequest(final String indexName) {
    logger.trace("Create search request against index '{}'", indexName);

    return new SearchRequest.Builder()
        .index(indexName)
        .requestCache(false)
        .size(100) // TODO add configurable values
        .sort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)))
        .query(q -> q.term(t -> t.field(HistoryDeletionIndex.PARTITION_ID).value(partitionId)))
        .build();
  }
}
