/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.exporter.tasks.util.OpensearchRepository;
import io.camunda.exporter.tasks.util.SearchAfter;
import io.camunda.exporter.tasks.util.SearchAfterOS;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.WillCloseWhenClosed;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;

public class OpensearchDeletionRepository extends OpensearchRepository
    implements DeletionRepository {
  private final DeleteByIdPipeline pipeline;
  private final int partitionId;
  private final HistoryConfiguration config;
  private final IndexTemplateDescriptor listViewTemplate;

  public OpensearchDeletionRepository(
      final int partitionId,
      final HistoryConfiguration config,
      final ExporterResourceProvider resourceProvider,
      @WillCloseWhenClosed final OpenSearchAsyncClient client,
      final Executor executor,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.config = config;
    listViewTemplate = resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);

    pipeline = new DeleteByIdPipeline(config.getRetention().getDeleter(), this, metrics, logger);
  }

  @Override
  public CompletableFuture<ProcessInstanceDeletionBatch> getProcessInstancesNextBatch() {
    final var request =
        new Builder()
            .trackTotalHits(t -> t.enabled(false))
            .index(listViewTemplate.getOrdinalIndexPattern())
            .requestCache(false)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .query(
                finishedProcessInstancesQuery(
                    "now-" + config.getRetention().getMinimumAge(),
                    partitionId,
                    config.getProcessInstanceRetentionMode()))
            .size(config.getRetention().getDeleter().getEntityBatchSize())
            .source(
                s ->
                    s.filter(
                        f ->
                            f.includes(
                                List.of(
                                    ListViewTemplate.KEY,
                                    ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY,
                                    ListViewTemplate.STORAGE_ORDINAL))))
            .sort(
                // a batch only covers one storage ordinal, so sort by it first to fill batches;
                // documents without one are treated as being in the main index
                SortOptions.of(
                    sort ->
                        sort.field(
                            field ->
                                field
                                    .field(ListViewTemplate.STORAGE_ORDINAL)
                                    .order(SortOrder.Asc)
                                    .missing(FieldValue.of(StorageOrdinalRelated.MAIN_INDEX)))),
                SortOptions.of(
                    sort ->
                        sort.field(
                            field -> field.field(ListViewTemplate.END_DATE).order(SortOrder.Asc))))
            .build();

    try {
      return client
          .search(request, ProcessInstanceForListViewEntity.class)
          .thenApplyAsync(
              response ->
                  ProcessInstanceDeletionBatch.from(
                      response.hits().hits().stream().map(Hit::source).toList(),
                      config.getProcessInstanceRetentionMode()),
              executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<Void> deleteDocumentsById(
      final String indexName, final FilterByKeysQuery<String> filter, final Executor executor) {
    return pipeline.deleteDocumentsById(indexName, filter, executor);
  }

  @Override
  public CompletableFuture<DocumentBatch<DocToDelete, SearchAfter>> searchDocumentsToDelete(
      final String indexName,
      final FilterByKeysQuery<?> filter,
      final SearchAfter searchAfter,
      final int batchSize) {
    final Query query = toQuery(filter);
    final Builder requestBuilder =
        new Builder()
            .trackTotalHits(t -> t.enabled(false))
            .index(indexName)
            .requestCache(false)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .allowPartialSearchResults(false)
            .query(query)
            .size(batchSize)
            .source(s -> s.fetch(false))
            .sort(sort -> sort.field(field -> field.field("id").order(SortOrder.Asc)));

    if (searchAfter != null) {
      ((SearchAfterOS) searchAfter).apply(requestBuilder);
    }

    try {
      return client
          .search(requestBuilder.build(), Object.class)
          .thenApply(
              response -> {
                final List<Hit<Object>> hits = response.hits().hits();
                if (hits.isEmpty()) {
                  return DocumentBatch.empty();
                }

                return DocumentBatch.from(
                    hits.stream()
                        .map(h -> new DocToDelete(h.id(), h.routing(), h.index()))
                        .toList(),
                    new SearchAfterOS(hits.getLast().sort()));
              });
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<Integer> deleteDocBatch(
      final DocumentBatch<DocToDelete, SearchAfter> batch) {
    if (batch.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }

    final var docs = batch.documents();
    final var operations =
        docs.stream()
            .map(
                d ->
                    BulkOperation.of(
                        b -> b.delete(del -> del.id(d.id()).routing(d.routing()).index(d.index()))))
            .toList();

    final BulkRequest request = BulkRequest.of(b -> b.operations(operations));

    try {
      return client
          .bulk(request)
          .thenApplyAsync(this::getDeletedDocCount, executor)
          .thenApply(Math::toIntExact);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private long getDeletedDocCount(final BulkResponse response) {
    if (response.errors()) {
      final var indexes =
          response.items().stream().map(BulkResponseItem::index).collect(Collectors.toSet());
      final long errorCount =
          response.items().stream().filter(item -> item.error() != null).count();
      throw new IllegalStateException(
          "There were %d errors deleting %d docs from %s"
              .formatted(errorCount, response.items().size(), indexes));
    }

    // only count DELETE bulk operation where result was `deleted`
    return response.items().stream().filter(i -> "deleted".equals(i.result())).count();
  }
}
