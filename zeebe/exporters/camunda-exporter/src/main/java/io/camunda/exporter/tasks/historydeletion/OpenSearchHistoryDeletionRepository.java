/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.tasks.util.OpensearchRepository;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.WillCloseWhenClosed;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;

/**
 * OpenSearch implementation of {@link HistoryDeletionRepository} that queries the history-deletion
 * index for resources marked for deletion, filtering by partition ID.
 */
public class OpenSearchHistoryDeletionRepository extends OpensearchRepository
    implements HistoryDeletionRepository {

  private final IndexDescriptor indexDescriptor;
  private final int partitionId;

  public OpenSearchHistoryDeletionRepository(
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final OpenSearchAsyncClient client,
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

    return sendRequestAsync(() -> client.search(searchRequest, HistoryDeletionEntity.class))
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
        .query(
            q ->
                q.term(
                    t ->
                        t.field(HistoryDeletionIndex.PARTITION_ID)
                            .value(FieldValue.of(partitionId))))
        .build();
  }

  private <T> CompletableFuture<T> sendRequestAsync(final RequestSender<T> sender) {
    try {
      return sender.sendRequest();
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(
          new ExporterException(
              "Failed to send request, likely because we failed to parse the request", e));
    }
  }

  @FunctionalInterface
  private interface RequestSender<T> {
    CompletableFuture<T> sendRequest() throws IOException;
  }
}
