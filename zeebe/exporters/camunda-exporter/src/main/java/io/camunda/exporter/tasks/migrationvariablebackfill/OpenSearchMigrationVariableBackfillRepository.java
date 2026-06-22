/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.migrationvariablebackfill;

import io.camunda.exporter.tasks.util.OpensearchRepository;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.usertask.TaskVariableEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.WillCloseWhenClosed;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.slf4j.Logger;

public final class OpenSearchMigrationVariableBackfillRepository extends OpensearchRepository
    implements MigrationVariableBackfillRepository {

  private static final int RETRY_COUNT = 3;

  private final int partitionId;
  private final String postImporterQueueAlias;
  private final String variableAlias;
  private final String taskIndexName;

  public OpenSearchMigrationVariableBackfillRepository(
      final int partitionId,
      final String postImporterQueueAlias,
      final String variableAlias,
      final String taskIndexName,
      @WillCloseWhenClosed final OpenSearchAsyncClient client,
      final Executor executor,
      final Logger logger) {
    super(client, executor, logger);
    this.partitionId = partitionId;
    this.postImporterQueueAlias = postImporterQueueAlias;
    this.variableAlias = variableAlias;
    this.taskIndexName = taskIndexName;
  }

  @Override
  public CompletionStage<MigrationVariableBackfillRepository.PendingBackfillBatch>
      getPendingBackfillBatch(final long fromPosition, final int size) {
    final var query = createPendingBackfillQuery(fromPosition);
    final var request =
        new SearchRequest.Builder()
            .index(postImporterQueueAlias)
            .query(query)
            .ignoreUnavailable(true)
            .allowNoIndices(true)
            .source(
                s ->
                    s.filter(
                        f ->
                            f.includes(
                                PostImporterQueueTemplate.PROCESS_INSTANCE_KEY,
                                PostImporterQueueTemplate.POSITION)))
            .sort(
                s -> s.field(f -> f.field(PostImporterQueueTemplate.POSITION).order(SortOrder.Asc)))
            .size(size)
            .build();

    try {
      return client
          .search(request, PendingBackfillEntry.class)
          .thenApplyAsync(this::toPendingBackfillBatch, executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletionStage<List<VariableEntity>> getVariablesByProcessInstanceKey(
      final long processInstanceKey) {
    final var query =
        QueryBuilders.term()
            .field(VariableTemplate.PROCESS_INSTANCE_KEY)
            .value(FieldValue.of(processInstanceKey))
            .build()
            .toQuery();
    final var requestBuilder = new SearchRequest.Builder().index(variableAlias).query(query);

    return fetchUnboundedDocumentCollection(
            requestBuilder, VariableEntity.class, hit -> hit.source())
        .thenApplyAsync(c -> new ArrayList<>(c), executor);
  }

  @Override
  public CompletionStage<Void> bulkUpsertTaskVariables(final List<TaskVariableEntity> variables) {
    if (variables.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    final var operations =
        variables.stream().map(this::createUpsertOperation).collect(Collectors.toList());
    final var request =
        new BulkRequest.Builder()
            .operations(operations)
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
                return CompletableFuture.completedFuture(null);
              },
              executor);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private BulkOperation createUpsertOperation(final TaskVariableEntity variable) {
    final Map<String, Object> updateFields =
        Map.of(
            TaskTemplate.VARIABLE_VALUE, variable.getValue(),
            TaskTemplate.IS_TRUNCATED, variable.getIsTruncated());
    return BulkOperation.of(
        o ->
            o.update(
                u ->
                    u.index(taskIndexName)
                        .id(variable.getId())
                        .routing(String.valueOf(variable.getProcessInstanceId()))
                        .retryOnConflict(RETRY_COUNT)
                        .document(updateFields)
                        .upsert(variable)));
  }

  private Query createPendingBackfillQuery(final long fromPosition) {
    final var positionQ =
        QueryBuilders.range()
            .field(PostImporterQueueTemplate.POSITION)
            .gt(JsonData.of(fromPosition))
            .build()
            .toQuery();
    final var typeQ =
        QueryBuilders.term()
            .field(PostImporterQueueTemplate.ACTION_TYPE)
            .value(FieldValue.of(PostImporterActionType.PROCESS_INSTANCE_MIGRATION.name()))
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

  private MigrationVariableBackfillRepository.PendingBackfillBatch toPendingBackfillBatch(
      final org.opensearch.client.opensearch.core.SearchResponse<PendingBackfillEntry> response) {
    final var hits = response.hits().hits();
    if (hits.isEmpty()) {
      return new MigrationVariableBackfillRepository.PendingBackfillBatch(-1L, List.of());
    }
    final var highestPosition = hits.getLast().source().position();
    final var processInstanceKeys =
        hits.stream().map(h -> h.source().processInstanceKey()).collect(Collectors.toList());
    return new MigrationVariableBackfillRepository.PendingBackfillBatch(
        highestPosition, processInstanceKeys);
  }

  private record PendingBackfillEntry(long processInstanceKey, long position) {}
}
