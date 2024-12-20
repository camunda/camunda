/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static io.camunda.exporter.tasks.util.ElasticsearchUtil.collectBulkErrors;
import static io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.webapps.schema.entities.operation.OperationState.COMPLETED;
import static io.camunda.webapps.schema.entities.operation.OperationState.FAILED;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.json.JsonData;
import io.camunda.exporter.tasks.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ElasticsearchBatchOperationUpdateRepository implements BatchOperationUpdateRepository {

  private static final String BATCH_OPERATION_IDAGG_NAME = "batchOperationId";
  private static final Integer RETRY_COUNT = 3;
  private final ElasticsearchAsyncClient client;
  private final Executor executor;
  private final String batchOperationIndex;
  private final String operationIndex;
  private final Logger logger;

  public ElasticsearchBatchOperationUpdateRepository(
      final ElasticsearchAsyncClient client,
      final Executor executor,
      final String batchOperationIndex,
      final String operationIndex,
      final Logger logger) {
    this.client = client;
    this.executor = executor;
    this.batchOperationIndex = batchOperationIndex;
    this.operationIndex = operationIndex;
    this.logger = logger;
  }

  @Override
  public Collection<String> getNotFinishedBatchOperations() {
    final var request =
        new SearchRequest.Builder()
            .index(batchOperationIndex)
            .query(q -> q.bool(b -> b.mustNot(m -> m.exists(e -> e.field(END_DATE)))));
    return ElasticsearchUtil.fetchUnboundedDocumentCollection(
            client, executor, logger, request, BatchOperationEntity.class, hit -> hit.id())
        .toCompletableFuture()
        .join();
  }

  @Override
  public List<OperationsAggData> getFinishedOperationsCount(final List<String> batchOperationIds) {
    if (batchOperationIds == null || batchOperationIds.isEmpty()) {
      return List.of();
    }
    final var batchOperationIdsValues = batchOperationIds.stream().map(FieldValue::of).toList();
    final var completedStatesValues =
        List.of(COMPLETED, FAILED).stream().map(FieldValue::of).toList();
    final var batchOperationIdsQ =
        QueryBuilders.bool(
            b ->
                b.must(
                        m ->
                            m.terms(
                                t ->
                                    t.field(OperationTemplate.BATCH_OPERATION_ID)
                                        .terms(v -> v.value(batchOperationIdsValues))))
                    .must(
                        m ->
                            m.terms(
                                t ->
                                    t.field(OperationTemplate.STATE)
                                        .terms(v -> v.value(completedStatesValues)))));
    final var aggregation =
        Aggregation.of(
            a -> a.terms(t -> t.field(BATCH_OPERATION_ID).size(batchOperationIds.size())));

    final var request =
        new SearchRequest.Builder()
            .index(operationIndex)
            .query(batchOperationIdsQ)
            .size(0)
            .aggregations(BATCH_OPERATION_IDAGG_NAME, aggregation)
            .build();

    final SearchResponse<Void> response = client.search(request, Void.class).join();

    return response
        .aggregations()
        .get(BATCH_OPERATION_IDAGG_NAME)
        .sterms()
        .buckets()
        .array()
        .stream()
        .map(b -> new OperationsAggData(b.key().stringValue(), b.docCount()))
        .collect(Collectors.toList());
  }

  @Override
  public Integer bulkUpdate(final List<DocumentUpdate> documentUpdates) {
    final var updates = documentUpdates.stream().map(this::createUpdateOperation).toList();
    final var request =
        new BulkRequest.Builder().operations(updates).source(s -> s.fetch(false)).build();
    return client
        .bulk(request)
        .thenCompose(
            r -> {
              if (r.errors()) {
                return CompletableFuture.failedFuture(collectBulkErrors(r.items()));
              }
              return CompletableFuture.completedFuture(r.items().size());
            })
        .join();
  }

  private BulkOperation createUpdateOperation(final DocumentUpdate update) {
    final Map<String, Object> params =
        Map.of(
            "operationsFinishedCount",
            update.finishedOperationsCount(),
            "endDate",
            OffsetDateTime.now());
    return new UpdateOperation.Builder<>()
        .index(batchOperationIndex)
        .id(update.id())
        .retryOnConflict(RETRY_COUNT)
        .action(a -> a.script(getBatchOperationUpdateScript(params)))
        .build()
        ._toBulkOperation();
  }

  private Script getBatchOperationUpdateScript(final Map<String, Object> params) {
    final Map<String, JsonData> parameters =
        params.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue())));
    return new Script.Builder()
        .inline(
            s ->
                s.source(
                        "if (ctx._source.operationsTotalCount <= params.operationsFinishedCount) { "
                            + "   ctx._source.endDate = params.endDate; "
                            + "} "
                            + "ctx._source.operationsFinishedCount = params.operationsFinishedCount;")
                    .lang("painless")
                    .params(parameters))
        .build();
  }

  @Override
  public void close() throws Exception {}
}
