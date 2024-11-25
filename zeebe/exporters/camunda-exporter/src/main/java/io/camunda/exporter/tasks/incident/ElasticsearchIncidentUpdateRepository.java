/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import co.elastic.clients.json.JsonData;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.NoopIncidentUpdateRepository;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class ElasticsearchIncidentUpdateRepository extends NoopIncidentUpdateRepository {

  private final int partitionId;
  private final String pendingUpdateAlias;
  private final String incidentAlias;
  private final String listViewAlias;
  private final String flowNodeAlias;
  private final ElasticsearchAsyncClient client;
  private final Executor executor;

  public ElasticsearchIncidentUpdateRepository(
      final int partitionId,
      final String pendingUpdateAlias,
      final String incidentAlias,
      final String listViewAlias,
      final String flowNodeAlias,
      final ElasticsearchAsyncClient client,
      final Executor executor) {
    this.partitionId = partitionId;
    this.pendingUpdateAlias = pendingUpdateAlias;
    this.incidentAlias = incidentAlias;
    this.listViewAlias = listViewAlias;
    this.flowNodeAlias = flowNodeAlias;
    this.client = client;
    this.executor = executor;
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
            ._toQuery();
    final var typeQ =
        QueryBuilders.term()
            .field(PostImporterQueueTemplate.ACTION_TYPE)
            .value(f -> f.anyValue(JsonData.of(PostImporterActionType.INCIDENT)))
            .build()
            ._toQuery();
    final var partitionQ =
        QueryBuilders.term()
            .field(PostImporterQueueTemplate.PARTITION_ID)
            .value(partitionId)
            .build()
            ._toQuery();
    return QueryBuilders.bool().must(positionQ, typeQ, partitionQ).build()._toQuery();
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
