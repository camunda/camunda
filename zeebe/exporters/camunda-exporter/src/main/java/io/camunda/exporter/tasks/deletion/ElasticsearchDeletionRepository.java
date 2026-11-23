/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.exporter.tasks.util.SearchAfter;
import io.camunda.exporter.tasks.util.SearchAfterES;
import io.camunda.search.schema.config.DeleterConfiguration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ElasticsearchDeletionRepository extends ElasticsearchRepository
    implements DeletionRepository {
  private final DeleteByIdPipeline pipeline;

  public ElasticsearchDeletionRepository(
      final DeleterConfiguration config,
      final ElasticsearchAsyncClient client,
      final Executor executor,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    super(client, executor, logger);

    pipeline = new DeleteByIdPipeline(config, this, metrics, logger);
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
        new SearchRequest.Builder()
            .trackTotalHits(TrackHits.of(t -> t.enabled(false)))
            .index(indexName)
            .requestCache(false)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .allowPartialSearchResults(false)
            .query(query)
            .size(batchSize)
            .source(s -> s.fetch(false))
            .sort(SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Asc))));

    if (searchAfter != null) {
      ((SearchAfterES) searchAfter).apply(requestBuilder);
    }

    return client
        .search(requestBuilder.build())
        .thenApply(
            response -> {
              final List<Hit<Void>> hits = response.hits().hits();
              if (hits.isEmpty()) {
                return DocumentBatch.empty();
              }
              return DocumentBatch.from(
                  hits.stream().map(h -> new DocToDelete(h.id(), h.routing(), h.index())).toList(),
                  new SearchAfterES(hits.getLast().sort()));
            });
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
                    new BulkOperation.Builder()
                        .delete(del -> del.id(d.id()).routing(d.routing()).index(d.index()))
                        .build())
            .toList();

    final var request = new BulkRequest.Builder().operations(operations).build();

    return client
        .bulk(request)
        .thenApplyAsync(this::getDeletedDocCount, executor)
        .thenApply(Math::toIntExact);
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
