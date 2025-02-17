/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestMethodOrder(OrderAnnotation.class)
final class OpensearchBatchOperationUpdateRepositoryIT extends BatchOperationUpdateRepositoryIT {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchBatchOperationUpdateRepositoryIT.class);

  private final OpenSearchAsyncClient client;

  public OpensearchBatchOperationUpdateRepositoryIT() {
    super(searchDB.osUrl(), false);
    final var connector = new OpensearchConnector(config.getConnect());
    client = connector.createAsyncClient();
  }

  @Override
  protected OpensearchBatchOperationUpdateRepository createRepository() {
    return new OpensearchBatchOperationUpdateRepository(
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
    } catch (final ExecutionException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
