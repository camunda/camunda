/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.exporter.common.historydeletion.HistoryDeletionConfiguration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
  private final HistoryDeletionConfiguration config;

  public ElasticsearchHistoryDeletionRepository(
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final ElasticsearchAsyncClient client,
      final Executor executor,
      final Logger logger,
      final int partitionId,
      final HistoryDeletionConfiguration config) {
    super(client, executor, logger);
    indexDescriptor =
        resourceProvider.getIndexDescriptors().stream()
            .filter(HistoryDeletionIndex.class::isInstance)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No HistoryDeletionIndex descriptor found"));
    this.partitionId = partitionId;
    this.config = config;
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
                return CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of()));
              }
              final var deletionEntities = hits.stream().map(Hit::source).toList();
              return CompletableFuture.completedFuture(new HistoryDeletionBatch(deletionEntities));
            },
            executor);
  }

  @Override
  public CompletableFuture<List<Long>> deleteDocumentsByField(
      final String sourceIndexName, final String idFieldName, final List<Long> fieldValues) {
    final var deleteRequest =
        new DeleteByQueryRequest.Builder()
            .index(sourceIndexName)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .waitForCompletion(true)
            .conflicts(Conflicts.Proceed)
            .query(
                q ->
                    q.terms(
                        t ->
                            t.field(idFieldName)
                                .terms(
                                    v ->
                                        v.value(
                                            fieldValues.stream().map(FieldValue::of).toList()))))
            .build();

    return client
        .deleteByQuery(deleteRequest)
        .thenComposeAsync(
            (response) -> {
              if (!response.failures().isEmpty()) {
                final var errorMessage =
                    "Deleting documents from index '%s' by field '%s' failed with failures: %s"
                        .formatted(sourceIndexName, idFieldName, response.failures());
                logger.error(errorMessage);
                return CompletableFuture.failedFuture(new RuntimeException(errorMessage));
              }

              if (response.versionConflicts() != null && response.versionConflicts() > 0) {
                final var debugMessage =
                    "Deleting documents from index '%s' by field '%s' encountered '%d' conflicts"
                        .formatted(sourceIndexName, idFieldName, response.versionConflicts());
                logger.debug(debugMessage);
                return CompletableFuture.failedFuture(new RuntimeException(debugMessage));
              }

              logger.debug(
                  "Deleted {} documents from index '{}' by field '{}' with values: {}",
                  response.total(),
                  sourceIndexName,
                  idFieldName,
                  fieldValues);
              return CompletableFuture.completedFuture(fieldValues);
            },
            executor);
  }

  @Override
  public CompletableFuture<Integer> deleteDocumentsById(
      final String sourceIndexName, final List<String> ids) {
    final var bulkRequestBuilder = new BulkRequest.Builder();

    ids.forEach(
        id -> bulkRequestBuilder.operations(op -> op.delete(d -> d.index(sourceIndexName).id(id))));

    return client
        .bulk(bulkRequestBuilder.build())
        .thenComposeAsync(
            response -> {
              if (response.errors()) {
                final var errorMessage =
                    "Bulk deleting documents from index '%s' by ids '%s' failed with errors: %s"
                        .formatted(sourceIndexName, ids, response.items());
                logger.error(errorMessage);
                return CompletableFuture.failedFuture(new RuntimeException(errorMessage));
              }
              final var deleted = response.items().size();
              return CompletableFuture.completedFuture(deleted);
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
        .size(config.getQueueBatchSize())
        .sort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)))
        .query(q -> q.term(t -> t.field(HistoryDeletionIndex.PARTITION_ID).value(partitionId)))
        .build();
  }
}
