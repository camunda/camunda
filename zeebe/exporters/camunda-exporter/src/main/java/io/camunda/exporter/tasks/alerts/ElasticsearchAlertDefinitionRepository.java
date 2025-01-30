/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.alerts;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.exporter.tasks.util.ElasticsearchRepository;
import io.camunda.webapps.schema.entities.AlertDefinitionEntity;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ElasticsearchAlertDefinitionRepository extends ElasticsearchRepository
    implements AlertDefinitionRepository {

  private final String index;

  public ElasticsearchAlertDefinitionRepository(
      final String index,
      final ElasticsearchAsyncClient client,
      final Executor executor,
      final Logger logger) {
    super(client, executor, logger);
    this.index = index;
  }

  @Override
  public List<AlertDefinitionEntity> getAll() {
    final var request = new SearchRequest.Builder().index(index);
    return fetchUnboundedDocumentCollection(request, AlertDefinitionEntity.class, Hit::source)
        .toCompletableFuture()
        .join()
        .stream()
        .toList();
  }

  @Override
  public void close() throws Exception {
    client._transport().close();
  }
}
