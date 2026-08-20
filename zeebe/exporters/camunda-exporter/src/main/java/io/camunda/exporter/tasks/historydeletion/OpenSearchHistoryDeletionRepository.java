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
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.common.historydeletion.HistoryDeletionConfiguration;
import java.io.IOException;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import javax.annotation.WillCloseWhenClosed;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;

/**
 * OpenSearch implementation of {@link HistoryDeletionRepository} that queries the history-deletion
 * index for resources marked for deletion, filtering by partition ID.
 */
public class OpenSearchHistoryDeletionRepository extends OpensearchRepository
    implements HistoryDeletionRepository {

  private static final String DOCUMENT_MISSING_ERROR_TYPE = "document_missing_exception";
  private final IndexDescriptor indexDescriptor;
  private final IndexDescriptor auditLogCleanupIndex;
  private final int partitionId;
  private final HistoryDeletionConfiguration config;
  private final InstantSource clock;
  private final OperationTemplate operationIndexTemplateDescriptor;

  public OpenSearchHistoryDeletionRepository(
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final OpenSearchAsyncClient client,
      final Executor executor,
      final Logger logger,
      final int partitionId,
      final HistoryDeletionConfiguration config,
      final InstantSource clock) {
    super(client, executor, logger);
    indexDescriptor =
        resourceProvider.getIndexDescriptors().stream()
            .filter(HistoryDeletionIndex.class::isInstance)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No HistoryDeletionIndex descriptor found"));
    auditLogCleanupIndex =
        resourceProvider.getIndexDescriptors().stream()
            .filter(AuditLogCleanupIndex.class::isInstance)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No AuditLogCleanupIndex descriptor found"));
    operationIndexTemplateDescriptor =
        resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class);
    this.partitionId = partitionId;
    this.config = config;
    this.clock = clock;
  }

  @Override
  public CompletableFuture<HistoryDeletionBatch> getNextBatch() {
    final var searchRequest = createSearchRequest();

    return sendRequestAsync(() -> client.search(searchRequest, HistoryDeletionEntity.class))
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

    return sendRequestAsync(
        () ->
            client
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
                                .formatted(
                                    sourceIndexName, idFieldName, response.versionConflicts());
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
                    executor));
  }

  @Override
  public CompletableFuture<Integer> deleteDocumentsById(
      final String sourceIndexName, final List<String> ids) {
    final var bulkRequestBuilder = new BulkRequest.Builder();

    ids.forEach(
        id -> bulkRequestBuilder.operations(op -> op.delete(d -> d.index(sourceIndexName).id(id))));

    return sendRequestAsync(
        () ->
            client
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
                      logger.debug(
                          "Deleted {} documents from index '{}' by id with values: {}",
                          deleted,
                          sourceIndexName,
                          ids);
                      return CompletableFuture.completedFuture(deleted);
                    },
                    executor));
  }

  @Override
  public CompletionStage<Void> createAuditLogCleanupEntries(
      final List<HistoryDeletionEntity> historyDeletionEntities,
      final Set<String> deletedResources) {
    if (deletedResources.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    final var entries =
        AuditLogCleanupTransformer.buildAuditLogCleanupEntries(
            historyDeletionEntities, deletedResources);
    final var targetIndexName = auditLogCleanupIndex.getFullQualifiedName();
    final var bulkRequestBuilder = new BulkRequest.Builder();

    entries.forEach(
        entry ->
            bulkRequestBuilder.operations(
                op -> op.index(i -> i.index(targetIndexName).id(entry.getId()).document(entry))));

    return sendRequestAsync(
        () ->
            client
                .bulk(bulkRequestBuilder.build())
                .thenComposeAsync(
                    response -> {
                      if (response.errors()) {
                        final var errorMessage =
                            "Bulk indexing audit log cleanup entries to index '%s' failed with errors: %s"
                                .formatted(targetIndexName, response.items());
                        logger.error(errorMessage);
                        return CompletableFuture.failedFuture(new RuntimeException(errorMessage));
                      }
                      logger.debug(
                          "Indexed {} audit log cleanup entries to index '{}'",
                          entries.size(),
                          targetIndexName);
                      return CompletableFuture.completedFuture(null);
                    },
                    executor));
  }

  @Override
  public CompletableFuture<List<String>> completeOperations(final List<String> ids) {
    final var bulkRequestBuilder = new BulkRequest.Builder();

    final var fieldsToUpdate =
        Map.of(
            OperationTemplate.STATE,
            OperationState.COMPLETED,
            OperationTemplate.COMPLETED_DATE,
            OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

    ids.forEach(
        id ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        u ->
                            u.index(operationIndexTemplateDescriptor.getFullQualifiedName())
                                .id(id)
                                .document(fieldsToUpdate)
                                .retryOnConflict(3))));

    return sendRequestAsync(
        () ->
            client
                .bulk(bulkRequestBuilder.build())
                .thenComposeAsync(response -> handleBulkResponseErrors(response, ids), executor));
  }

  /**
   * Handles errors from bulk update operations, filtering out document_missing_exception errors
   * that occur when operations are already archived. We cannot update these as we don't know what
   * index they are in.
   *
   * @param response the bulk response from Opensearch
   * @param ids the list of operation IDs that were updated
   * @return a CompletableFuture containing the list of IDs if successful, or a failed future if
   *     there are actual errors
   */
  private CompletableFuture<List<String>> handleBulkResponseErrors(
      final org.opensearch.client.opensearch.core.BulkResponse response, final List<String> ids) {
    if (response.errors()) {
      // Filter out errors from archived operations (document_missing_exception)
      // These operations are already archived and will be cleaned up by retention
      final var actualErrors =
          response.items().stream()
              .filter(item -> item.error() != null)
              .filter(item -> !item.error().type().equals(DOCUMENT_MISSING_ERROR_TYPE))
              .toList();

      if (!actualErrors.isEmpty()) {
        final var errorMessage =
            "Bulk updating operations by ids '%s' failed with errors: %s"
                .formatted(ids, actualErrors);
        logger.error(errorMessage);
        return CompletableFuture.failedFuture(new RuntimeException(errorMessage));
      }
      // All errors were document_missing_exception, log as debug and continue
      logger.debug(
          "Bulk updating operations completed with {} document_missing_exception errors (archived operations)",
          response.items().stream().filter(item -> item.error() != null).count());
    }
    return CompletableFuture.completedFuture(ids);
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
