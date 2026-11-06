/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.search.connect.os.OpensearchConnector;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpensearchDeletionRepositoryIT extends DeletionRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchDeletionRepositoryIT.class);

  private final OpenSearchAsyncClient client;

  OpensearchDeletionRepositoryIT() {
    super(searchDB.osUrl(), false);
    final var connector = new OpensearchConnector(config.getConnect());
    client = connector.createAsyncClient();
  }

  @Override
  protected DeletionRepository createRepository() {
    return new OpensearchDeletionRepository(deleterConfig, client, Runnable::run, metrics, LOGGER);
  }
}
