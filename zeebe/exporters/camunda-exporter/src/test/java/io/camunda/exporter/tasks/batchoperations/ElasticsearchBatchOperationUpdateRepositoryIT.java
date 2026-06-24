/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
final class ElasticsearchBatchOperationUpdateRepositoryIT extends BatchOperationUpdateRepositoryIT {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchBatchOperationUpdateRepositoryIT.class);

  private final ElasticsearchAsyncClient client;

  public ElasticsearchBatchOperationUpdateRepositoryIT() {
    super("http://" + searchDB.esUrl(), true);
    final var connector = new ElasticsearchConnector(config.getConnect());
    client = connector.createAsyncClient();
  }

  @Override
  protected ElasticsearchBatchOperationUpdateRepository createRepository() {
    return new ElasticsearchBatchOperationUpdateRepository(
        client,
        Runnable::run,
        batchOperationTemplate.getFullQualifiedName(),
        operationTemplate.getFullQualifiedName(),
        LOGGER);
  }

  @Override
  protected BatchOperationEntity getBatchOperationEntity(final String id) {
    try {
      return client
          .get(
              r -> r.index(batchOperationTemplate.getFullQualifiedName()).id(id),
              BatchOperationEntity.class)
          .get()
          .source();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (final ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
