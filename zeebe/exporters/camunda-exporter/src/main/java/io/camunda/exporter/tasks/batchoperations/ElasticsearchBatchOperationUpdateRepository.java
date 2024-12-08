/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate.END_DATE;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.exporter.tasks.util.ElasticsearchUtil;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ElasticsearchBatchOperationUpdateRepository implements BatchOperationUpdateRepository {

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
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public Integer bulkUpdate(final List<DocumentUpdate> documentUpdates) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public void close() throws Exception {}
}
