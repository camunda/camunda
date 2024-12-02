/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.api.ExporterException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceFilter;
import org.opensearch.client.opensearch.indices.AnalyzeRequest;
import org.opensearch.client.opensearch.indices.analyze.AnalyzeToken;
import org.slf4j.Logger;

public final class OpenSearchIncidentUpdateRepository implements IncidentUpdateRepository {
  private static final int RETRY_COUNT = 3;
  private static final List<FieldValue> DELETED_OPERATION_STATES =
      List.of(
          FieldValue.of(OperationState.SENT.name()),
          FieldValue.of(OperationState.COMPLETED.name()));
  private static final Time SCROLL_KEEP_ALIVE = Time.of(t -> t.time("1m"));
  private static final int SCROLL_PAGE_SIZE = 100;

  private final int partitionId;
  private final String pendingUpdateAlias;
  private final String incidentAlias;
  private final String listViewAlias;
  private final String flowNodeAlias;
  private final String operationAlias;
  private final OpenSearchAsyncClient client;
  private final Executor executor;
  private final Logger logger;

  public OpenSearchIncidentUpdateRepository(
      final int partitionId,
      final String pendingUpdateAlias,
      final String incidentAlias,
      final String listViewAlias,
      final String flowNodeAlias,
      final String operationAlias,
      final OpenSearchAsyncClient client,
      final Executor executor,
      final Logger logger) {
    this.partitionId = partitionId;
    this.pendingUpdateAlias = pendingUpdateAlias;
    this.incidentAlias = incidentAlias;
    this.listViewAlias = listViewAlias;
    this.flowNodeAlias = flowNodeAlias;
    this.operationAlias = operationAlias;
    this.client = client;
    this.executor = executor;
    this.logger = logger;
  }

  @Override
  public CompletionStage<PendingIncidentUpdateBatch> getPendingIncidentsBatch(
      final long fromPosition, final int size) {
    final var query = createPendingIncidentsBatchQuery(fromPosition);
    final var request = createPendingIncidentsBatchRequest(size, query);

    try {
      return client
          .search(request, PendingIncidentUpdate.class)
          .thenApplyAsync(this::createPendingIncidentBatch, executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletionStage<Map<String, IncidentDocument>> getIncidentDocuments(
      final List<String> incidentIds) {
    final var request = createIncidentDocumentsRequest(incidentIds);

    try {
      return client
          .search(request, IncidentEntity.class)
          .thenApplyAsync(this::createIncidentDocuments, executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletionStage<Collection<Document>> getFlowNodesInListView(
      final List<String> flowNodeKeys) {
    final var idQ = QueryBuilders.ids().values(flowNodeKeys).build().toQuery();
    final var typeQ =
        QueryBuilders.term()
            .field(ListViewTemplate.JOIN_RELATION)
            .value(v -> v.stringValue(ListViewTemplate.ACTIVITIES_JOIN_RELATION))
            .build()
            .toQuery();
    final var request =
        new SearchRequest.Builder()
            .index(listViewAlias)
            .query(q -> q.bool(b -> b.must(idQ, typeQ)))
            .source(s -> s.fetch(false));

    return fetchUnboundedDocumentCollection(request, hit -> new Document(hit.id(), hit.index()));
  }

  @Override
  public CompletionStage<Collection<Document>> getFlowNodeInstances(
      final List<String> flowNodeKeys) {
    final var query = QueryBuilders.ids().values(flowNodeKeys).build().toQuery();
    final var request =
        new SearchRequest.Builder().index(flowNodeAlias).query(query).source(s -> s.fetch(false));

    return fetchUnboundedDocumentCollection(request, hit -> new Document(hit.id(), hit.index()));
  }

  @Override
  public CompletionStage<Collection<ProcessInstanceDocument>> getProcessInstances(
      final List<String> processInstanceIds) {
    final var idQ = QueryBuilders.ids().values(processInstanceIds).build().toQuery();
    final var typeQ =
        QueryBuilders.term()
            .field(ListViewTemplate.JOIN_RELATION)
            .value(v -> v.stringValue(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION))
            .build()
            .toQuery();
    final var request =
        new SearchRequest.Builder()
            .index(listViewAlias)
            .source(s -> s.filter(f -> f.includes(ListViewTemplate.TREE_PATH)))
            .query(q -> q.bool(b -> b.must(idQ, typeQ)));

    return fetchUnboundedDocumentCollection(
        request,
        ProcessInstanceForListViewEntity.class,
        hit ->
            new ProcessInstanceDocument(
                hit.id(), hit.index(), Long.parseLong(hit.id()), hit.source().getTreePath()));
  }

  @Override
  public CompletionStage<Boolean> wasProcessInstanceDeleted(final long processInstanceKey) {
    final var query = createProcessInstanceDeletedQuery(processInstanceKey);
    final var request =
        new CountRequest.Builder()
            .index(operationAlias)
            .query(query)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .build();

    try {
      return client.count(request).thenApplyAsync(r -> r.count() > 0, executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletionStage<Integer> bulkUpdate(final IncidentBulkUpdate bulk) {
    final var updates = bulk.stream().map(this::createUpdateOperation).toList();
    final var request =
        new BulkRequest.Builder()
            .operations(updates)
            .source(s -> s.fetch(false))
            .refresh(Refresh.WaitFor)
            .build();

    try {
      return client
          .bulk(request)
          .thenComposeAsync(
              r -> {
                if (r.errors()) {
                  return CompletableFuture.failedFuture(collectBulkErrors(r.items()));
                }

                return CompletableFuture.completedFuture(r.items().size());
              },
              executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletionStage<List<String>> analyzeTreePath(final String treePath) {
    final var request =
        new AnalyzeRequest.Builder()
            .field(ListViewTemplate.TREE_PATH)
            .index(listViewAlias)
            .text(treePath)
            .build();

    try {
      return client
          .indices()
          .analyze(request)
          .thenApplyAsync(
              response -> response.tokens().stream().map(AnalyzeToken::token).toList(), executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletionStage<Collection<ActiveIncident>> getActiveIncidentsByTreePaths(
      final Collection<String> treePathTerms) {
    final var treePathValues = treePathTerms.stream().map(FieldValue::of).toList();
    final var pathQ =
        QueryBuilders.terms()
            .field(IncidentTemplate.TREE_PATH)
            .terms(v -> v.value(treePathValues))
            .build()
            .toQuery();
    final var stateQ =
        QueryBuilders.term()
            .field(IncidentTemplate.STATE)
            .value(v -> v.stringValue(IncidentState.ACTIVE.name()))
            .build()
            .toQuery();
    final var request =
        new SearchRequest.Builder()
            .query(q -> q.bool(b -> b.must(pathQ, stateQ)))
            .source(s -> s.filter(f -> f.includes(IncidentTemplate.TREE_PATH)))
            .index(incidentAlias);

    return fetchUnboundedDocumentCollection(
        request, IncidentEntity.class, h -> new ActiveIncident(h.id(), h.source().getTreePath()));
  }

  /**
   * Variant of {@link #fetchUnboundedDocumentCollection(SearchRequest.Builder, Class, Function)} to
   * use when you don't care about the source document, meaning you won't be using any
   * deserialization functionality.
   */
  private <T> CompletionStage<Collection<T>> fetchUnboundedDocumentCollection(
      final SearchRequest.Builder requestBuilder, final Function<Hit<Object>, T> transformer) {
    return fetchUnboundedDocumentCollection(requestBuilder, Object.class, transformer);
  }

  private <TDocument, TResult>
      CompletionStage<Collection<TResult>> fetchUnboundedDocumentCollection(
          final SearchRequest.Builder requestBuilder,
          final Class<TDocument> type,
          final Function<Hit<TDocument>, TResult> transformer) {
    final var request =
        requestBuilder
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .scroll(SCROLL_KEEP_ALIVE)
            .size(SCROLL_PAGE_SIZE)
            .build();

    try {
      return client
          .search(request, type)
          .thenComposeAsync(
              r -> {
                try {
                  return clearScrollOnComplete(
                      r.scrollId(),
                      scrollDocuments(
                          r.hits().hits(), r.scrollId(), new ArrayList<>(), transformer, type));
                } catch (final Exception e) {
                  // scrollDocuments may fail, in which case we still want to clear the scroll
                  // anyway
                  // we don't need to do this later on however, since at this point our async
                  // pipeline
                  // is set up already to clear it
                  return clearScroll(r.scrollId(), null, e);
                }
              },
              executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private <T> CompletionStage<T> clearScrollOnComplete(
      final String scrollId, final CompletionStage<T> scrollOperation) {
    return scrollOperation
        // we combine `handleAsync` and `thenComposeAsync` to emulate the behavior of a try/finally
        // so we always clear the scroll even if the future is already failed
        .handleAsync((result, error) -> clearScroll(scrollId, result, error), executor)
        .thenComposeAsync(Function.identity(), executor);
  }

  private <T> CompletableFuture<T> clearScroll(
      final String scrollId, final T result, final Throwable error) {
    final var request = new ClearScrollRequest.Builder().scrollId(scrollId).build();
    final CompletionStage<T> endResult =
        error != null
            ? CompletableFuture.failedFuture(error)
            : CompletableFuture.completedFuture(result);
    try {
      return client
          .clearScroll(request)
          .exceptionallyAsync(
              clearError -> {
                logger.warn(
                    """
                        Failed to clear scroll context; this could eventually lead to \
                        increased resource usage in Elastic""",
                    clearError);

                return null;
              },
              executor)
          .thenComposeAsync(ignored -> endResult);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private <TResult, TDocument> CompletionStage<Collection<TDocument>> scrollDocuments(
      final List<Hit<TResult>> hits,
      final String scrollId,
      final List<TDocument> accumulator,
      final Function<Hit<TResult>, TDocument> transformer,
      final Class<TResult> type) {
    if (hits.isEmpty()) {
      return CompletableFuture.completedFuture(accumulator);
    }

    for (final var hit : hits) {
      accumulator.add(transformer.apply(hit));
    }

    try {
      return client
          .scroll(r -> r.scrollId(scrollId).scroll(SCROLL_KEEP_ALIVE), type)
          .thenComposeAsync(
              r -> scrollDocuments(r.hits().hits(), r.scrollId(), accumulator, transformer, type));
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private Query createProcessInstanceDeletedQuery(final long processInstanceKey) {
    final var piKeyQ =
        QueryBuilders.term()
            .field(OperationTemplate.PROCESS_INSTANCE_KEY)
            .value(v -> v.longValue(processInstanceKey))
            .build()
            .toQuery();
    final var typeQ =
        QueryBuilders.term()
            .field(OperationTemplate.TYPE)
            .value(v -> v.stringValue(OperationType.DELETE_PROCESS_INSTANCE.name()))
            .build()
            .toQuery();
    final var stateQ =
        QueryBuilders.terms()
            .field(OperationTemplate.STATE)
            .terms(f -> f.value(DELETED_OPERATION_STATES))
            .build()
            .toQuery();

    return QueryBuilders.bool().must(piKeyQ, typeQ, stateQ).build().toQuery();
  }

  private BulkOperation createUpdateOperation(final DocumentUpdate update) {
    return new UpdateOperation.Builder<>()
        .index(update.index())
        .id(update.id())
        .retryOnConflict(RETRY_COUNT)
        .document(update.doc())
        .routing(update.routing())
        .build()
        ._toBulkOperation();
  }

  private SearchRequest createIncidentDocumentsRequest(final List<String> incidentIds) {
    final var idQ = QueryBuilders.ids().values(incidentIds).build().toQuery();
    final var partitionQ =
        QueryBuilders.term()
            .field(IncidentTemplate.PARTITION_ID)
            .value(v -> v.longValue(partitionId))
            .build()
            .toQuery();

    return new SearchRequest.Builder()
        .index(incidentAlias)
        .query(q -> q.bool(b -> b.must(idQ, partitionQ)))
        .allowNoIndices(true)
        .ignoreUnavailable(true)
        .sort(s -> s.field(f -> f.field(IncidentTemplate.KEY)))
        .build();
  }

  private Map<String, IncidentDocument> createIncidentDocuments(
      final SearchResponse<IncidentEntity> response) {
    final Map<String, IncidentDocument> documents = new HashMap<>();
    for (final var hit : response.hits().hits()) {
      documents.put(hit.id(), new IncidentDocument(hit.id(), hit.index(), hit.source()));
    }

    return documents;
  }

  private SearchRequest createPendingIncidentsBatchRequest(final int size, final Query query) {
    final var sourceFilter =
        new SourceFilter.Builder()
            .includes(
                PostImporterQueueTemplate.KEY,
                PostImporterQueueTemplate.POSITION,
                PostImporterQueueTemplate.INTENT)
            .build();

    return new SearchRequest.Builder()
        .index(pendingUpdateAlias)
        .query(query)
        .ignoreUnavailable(true)
        .allowNoIndices(true)
        .source(s -> s.filter(sourceFilter))
        .sort(s -> s.field(f -> f.field(PostImporterQueueTemplate.POSITION).order(SortOrder.Asc)))
        .size(size)
        .build();
  }

  private Query createPendingIncidentsBatchQuery(final long fromPosition) {
    final var positionQ =
        QueryBuilders.range()
            .field(PostImporterQueueTemplate.POSITION)
            .gt(JsonData.of(fromPosition))
            .build()
            .toQuery();
    final var typeQ =
        QueryBuilders.term()
            .field(PostImporterQueueTemplate.ACTION_TYPE)
            .value(FieldValue.of(PostImporterActionType.INCIDENT.name()))
            .build()
            .toQuery();
    final var partitionQ =
        QueryBuilders.term()
            .field(PostImporterQueueTemplate.PARTITION_ID)
            .value(v -> v.longValue(partitionId))
            .build()
            .toQuery();
    return QueryBuilders.bool().must(positionQ, typeQ, partitionQ).build().toQuery();
  }

  private PendingIncidentUpdateBatch createPendingIncidentBatch(
      final SearchResponse<PendingIncidentUpdate> response) {
    final var hits = response.hits().hits();
    final Map<Long, IncidentState> incidents = new HashMap<>();

    // if there are multiple updates for a given incident, keep the latest one only; assuming no
    // bugs in the engine, this means it can only be CREATED -> RESOLVED. Since our search results
    // are sorted by position, we know the latest will necessarily be the actual "latest" (as in,
    // wall clock) result
    final var highestPosition = hits.isEmpty() ? -1L : hits.getLast().source().position();
    for (final var hit : hits) {
      final var entity = hit.source();
      final var newState = IncidentState.createFrom(entity.intent());
      incidents.put(entity.key(), newState);
    }

    return new PendingIncidentUpdateBatch(highestPosition, incidents);
  }

  private Throwable collectBulkErrors(final List<BulkResponseItem> items) {
    final var collectedErrors = new ArrayList<String>();
    items.stream()
        .flatMap(item -> Optional.ofNullable(item.error()).stream())
        .collect(Collectors.groupingBy(ErrorCause::type))
        .forEach(
            (type, errors) ->
                collectedErrors.add(
                    String.format(
                        "Failed to update %d item(s) of bulk update [type: %s, reason: %s]",
                        errors.size(), type, errors.getFirst().reason())));

    return new ExporterException("Failed to flush bulk request: " + collectedErrors);
  }

  private record PendingIncidentUpdate(long key, long position, String intent) {}
}
