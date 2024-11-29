/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import co.elastic.clients.json.JsonData;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.NoopIncidentUpdateRepository;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.api.ExporterException;
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
import org.slf4j.Logger;

public final class ElasticsearchIncidentUpdateRepository extends NoopIncidentUpdateRepository {
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
  private final ElasticsearchAsyncClient client;
  private final Executor executor;
  private final Logger logger;

  public ElasticsearchIncidentUpdateRepository(
      final int partitionId,
      final String pendingUpdateAlias,
      final String incidentAlias,
      final String listViewAlias,
      final String flowNodeAlias,
      final String operationAlias,
      final ElasticsearchAsyncClient client,
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

    return client
        .search(request, PendingIncidentUpdate.class)
        .thenApplyAsync(this::createPendingIncidentBatch, executor);
  }

  @Override
  public CompletionStage<Map<String, IncidentDocument>> getIncidentDocuments(
      final List<String> incidentIds) {
    final var request = createIncidentDocumentsRequest(incidentIds);

    return client
        .search(request, IncidentEntity.class)
        .thenApplyAsync(this::createIncidentDocuments, executor);
  }

  @Override
  public CompletionStage<Collection<Document>> getFlowNodesInListView(
      final List<String> flowNodeKeys) {
    final var idQ = QueryBuilders.ids(i -> i.values(flowNodeKeys));
    final var typeQ =
        QueryBuilders.term(
            t ->
                t.field(ListViewTemplate.JOIN_RELATION)
                    .value(ListViewTemplate.ACTIVITIES_JOIN_RELATION));
    return fetchUnboundedDocumentCollection(
        listViewAlias, QueryBuilders.bool(b -> b.must(idQ, typeQ)));
  }

  @Override
  public CompletionStage<Collection<Document>> getFlowNodeInstances(
      final List<String> flowNodeKeys) {
    final var query = QueryBuilders.ids(i -> i.values(flowNodeKeys));
    return fetchUnboundedDocumentCollection(flowNodeAlias, query);
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

    return client.count(request).thenApplyAsync(r -> r.count() > 0, executor);
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
  }

  @Override
  public CompletionStage<List<String>> analyzeTreePath(final String treePath) {
    final var request =
        new AnalyzeRequest.Builder()
            .field(ListViewTemplate.TREE_PATH)
            .index(listViewAlias)
            .text(treePath)
            .build();

    return client
        .indices()
        .analyze(request)
        .thenApplyAsync(
            response -> response.tokens().stream().map(AnalyzeToken::token).toList(), executor);
  }

  private CompletableFuture<Collection<Document>> fetchUnboundedDocumentCollection(
      final String indexAlias, final Query query) {
    final var request =
        new SearchRequest.Builder()
            .index(indexAlias)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .query(query)
            .source(s -> s.fetch(false))
            .scroll(SCROLL_KEEP_ALIVE)
            .size(SCROLL_PAGE_SIZE)
            .build();

    return client
        // pass Object as the document type, because we will not read the source anyway and we don't
        // want to couple ourselves to whatever the entity type really be
        .search(request, Object.class)
        .thenComposeAsync(
            r ->
                clearScrollOnComplete(
                    r.scrollId(),
                    scrollDocuments(r.hits().hits(), r.scrollId(), new ArrayList<>())),
            executor);
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
  }

  private CompletionStage<Collection<Document>> scrollDocuments(
      final List<Hit<Object>> hits, final String scrollId, final List<Document> accumulator) {
    if (hits.isEmpty()) {
      return CompletableFuture.completedFuture(accumulator);
    }

    hits.forEach(hit -> accumulator.add(new Document(hit.id(), hit.index())));

    return client
        .scroll(r -> r.scrollId(scrollId).scroll(SCROLL_KEEP_ALIVE), Object.class)
        .thenComposeAsync(r -> scrollDocuments(r.hits().hits(), r.scrollId(), accumulator));
  }

  private Query createProcessInstanceDeletedQuery(final long processInstanceKey) {
    final var piKeyQ =
        QueryBuilders.term(
            t -> t.field(OperationTemplate.PROCESS_INSTANCE_KEY).value(processInstanceKey));
    final var typeQ =
        QueryBuilders.term(
            t ->
                t.field(OperationTemplate.TYPE)
                    .value(OperationType.DELETE_PROCESS_INSTANCE.name()));
    final var stateQ =
        QueryBuilders.terms(
            t -> t.field(OperationTemplate.STATE).terms(f -> f.value(DELETED_OPERATION_STATES)));

    return QueryBuilders.bool(b -> b.must(piKeyQ, typeQ, stateQ));
  }

  private BulkOperation createUpdateOperation(final DocumentUpdate update) {
    return new UpdateOperation.Builder<>()
        .index(update.index())
        .id(update.id())
        .retryOnConflict(RETRY_COUNT)
        .action(a -> a.doc(update.doc()))
        .routing(update.routing())
        .build()
        ._toBulkOperation();
  }

  private SearchRequest createIncidentDocumentsRequest(final List<String> incidentIds) {
    final var idQ = QueryBuilders.ids(i -> i.values(incidentIds));
    final var partitionQ =
        QueryBuilders.term(t -> t.field(IncidentTemplate.PARTITION_ID).value(partitionId));
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
        QueryBuilders.range(
            r -> r.field(PostImporterQueueTemplate.POSITION).gt(JsonData.of(fromPosition)));
    final var typeQ =
        QueryBuilders.term(
            t ->
                t.field(PostImporterQueueTemplate.ACTION_TYPE)
                    .value(PostImporterActionType.INCIDENT.name()));
    final var partitionQ =
        QueryBuilders.term(t -> t.field(PostImporterQueueTemplate.PARTITION_ID).value(partitionId));
    return QueryBuilders.bool(b -> b.must(positionQ, typeQ, partitionQ));
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
