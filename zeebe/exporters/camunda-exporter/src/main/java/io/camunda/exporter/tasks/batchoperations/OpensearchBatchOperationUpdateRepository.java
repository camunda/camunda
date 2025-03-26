/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.webapps.schema.entities.operation.OperationState.COMPLETED;
import static io.camunda.webapps.schema.entities.operation.OperationState.FAILED;

import io.camunda.exporter.tasks.util.OpensearchRepository;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;

public class OpensearchBatchOperationUpdateRepository extends OpensearchRepository
    implements BatchOperationUpdateRepository {

  private static final String BATCH_OPERATION_IDAGG_NAME = "batchOperationId";
  private static final Integer RETRY_COUNT = 3;
  private final String batchOperationIndex;
  private final String operationIndex;

  public OpensearchBatchOperationUpdateRepository(
      final OpenSearchAsyncClient client,
      final Executor executor,
      final String batchOperationIndex,
      final String operationIndex,
      final Logger logger) {
    super(client, executor, logger);
    this.batchOperationIndex = batchOperationIndex;
    this.operationIndex = operationIndex;
  }

  @Override
  public CompletionStage<Collection<String>> getNotFinishedBatchOperations() {
    final var request =
        new SearchRequest.Builder()
            .index(batchOperationIndex)
            .query(q -> q.bool(b -> b.mustNot(m -> m.exists(e -> e.field(END_DATE)))));
    return fetchUnboundedDocumentCollection(request, BatchOperationEntity.class, Hit::id);
  }

  @Override
  public CompletionStage<List<OperationsAggData>> getFinishedOperationsCount(
      final Collection<String> batchOperationIds) {
    if (batchOperationIds == null || batchOperationIds.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }
    final var batchOperationIdsValues = batchOperationIds.stream().map(FieldValue::of).toList();
    final var completedStatesValues =
        Stream.of(COMPLETED.name(), FAILED.name()).map(FieldValue::of).toList();

    final var batchOperationIdsQ =
        QueryBuilders.terms()
            .field(BATCH_OPERATION_ID)
            .terms(v -> v.value(batchOperationIdsValues))
            .build()
            .toQuery();

    final var statesQ =
        QueryBuilders.terms()
            .field(OperationTemplate.STATE)
            .terms(v -> v.value(completedStatesValues))
            .build()
            .toQuery();

    final var operationsQ =
        QueryBuilders.bool().must(batchOperationIdsQ, statesQ).build().toQuery();

    final var aggregation =
        Aggregation.of(
            a -> a.terms(t -> t.field(BATCH_OPERATION_ID).size(batchOperationIds.size())));

    final var request =
        new SearchRequest.Builder()
            .index(operationIndex)
            .query(operationsQ)
            .size(0)
            .aggregations(BATCH_OPERATION_IDAGG_NAME, aggregation)
            .build();
    try {
      return client.search(request, Void.class).thenApply(this::processAggregations);
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletionStage<Integer> bulkUpdate(final List<DocumentUpdate> documentUpdates) {
    if (documentUpdates == null || documentUpdates.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    final var updates = documentUpdates.stream().map(this::createUpdateOperation).toList();
    final var request =
        new BulkRequest.Builder().operations(updates).source(s -> s.fetch(false)).build();
    try {
      return client
          .bulk(request)
          .thenCompose(
              r -> {
                if (r.errors()) {
                  return CompletableFuture.failedFuture(collectBulkErrors(r.items()));
                }
                return CompletableFuture.completedFuture(r.items().size());
              });
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
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
        .script(getBatchOperationUpdateScript(params))
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

  private List<OperationsAggData> processAggregations(final SearchResponse<Void> response) {
    return response
        .aggregations()
        .get(BATCH_OPERATION_IDAGG_NAME)
        .sterms()
        .buckets()
        .array()
        .stream()
        .map(b -> new OperationsAggData(b.key(), b.docCount()))
        .collect(Collectors.toList());
  }
}
