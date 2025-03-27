/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.webapps.schema.entities.post.PostImporterActionType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import javax.annotation.WillCloseWhenClosed;
import org.slf4j.Logger;

public final class ElasticsearchIncidentUpdateRepository extends ElasticsearchRepository
    implements IncidentUpdateRepository {
  private static final int RETRY_COUNT = 3;
  private static final List<FieldValue> DELETED_OPERATION_STATES =
      List.of(
          FieldValue.of(OperationState.SENT.name()),
          FieldValue.of(OperationState.COMPLETED.name()));

  private final int partitionId;
  private final String pendingUpdateAlias;
  private final String incidentAlias;
  private final String listViewAlias;
  private final String listViewFullQualifiedName;
  private final String flowNodeAlias;
  private final String operationAlias;

  public ElasticsearchIncidentUpdateRepository(
      final int partitionId,
      final String pendingUpdateAlias,
      final String incidentAlias,
      final String listViewAlias,
      final String listViewFullQualifiedName,
      final String flowNodeAlias,
      final String operationAlias,
      @WillCloseWhenClosed final ElasticsearchAsyncClient client,
      final Executor executor,
      final Logger logger) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.pendingUpdateAlias = pendingUpdateAlias;
    this.incidentAlias = incidentAlias;
    this.listViewAlias = listViewAlias;
    this.listViewFullQualifiedName = listViewFullQualifiedName;
    this.flowNodeAlias = flowNodeAlias;
    this.operationAlias = operationAlias;
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
    final var query = QueryBuilders.ids(i -> i.values(flowNodeKeys));
    final var request =
        new SearchRequest.Builder().index(flowNodeAlias).query(query).source(s -> s.fetch(false));

    return fetchUnboundedDocumentCollection(request, hit -> new Document(hit.id(), hit.index()));
  }

  @Override
  public CompletionStage<Collection<ProcessInstanceDocument>> getProcessInstances(
      final List<String> processInstanceIds) {
    final var idQ = QueryBuilders.ids(i -> i.values(processInstanceIds));
    final var typeQ =
        QueryBuilders.term(
            t ->
                t.field(ListViewTemplate.JOIN_RELATION)
                    .value(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
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
            .index(listViewFullQualifiedName)
            .text(treePath)
            .build();

    return client
        .indices()
        .analyze(request)
        .thenApplyAsync(
            response -> response.tokens().stream().map(AnalyzeToken::token).toList(), executor);
  }

  @Override
  public CompletionStage<Collection<ActiveIncident>> getActiveIncidentsByTreePaths(
      final Collection<String> treePathTerms) {
    final var treePathValues = treePathTerms.stream().map(FieldValue::of).toList();
    final var pathQ =
        QueryBuilders.terms(
            t -> t.field(IncidentTemplate.TREE_PATH).terms(v -> v.value(treePathValues)));
    final var stateQ =
        QueryBuilders.term(t -> t.field(IncidentTemplate.STATE).value(IncidentState.ACTIVE.name()));
    final var request =
        new SearchRequest.Builder()
            .query(q -> q.bool(b -> b.must(pathQ, stateQ)))
            .source(s -> s.filter(f -> f.includes(IncidentTemplate.TREE_PATH)))
            .index(incidentAlias);

    return fetchUnboundedDocumentCollection(
        request, IncidentEntity.class, h -> new ActiveIncident(h.id(), h.source().getTreePath()));
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
        .size(incidentIds.size())
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
            r ->
                r.number(
                    n -> n.field(PostImporterQueueTemplate.POSITION).gt((double) fromPosition)));
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

  private record PendingIncidentUpdate(long key, long position, String intent) {}
}
