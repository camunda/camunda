/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.search.connect.es.ElasticsearchConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ElasticsearchDeletionRepositoryIT extends DeletionRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchDeletionRepositoryIT.class);

  private final ElasticsearchAsyncClient client;

  ElasticsearchDeletionRepositoryIT() {
    super("http://" + searchDB.esUrl(), true);
    client = new ElasticsearchConnector(config.getConnect()).createAsyncClient();
  }

  @Override
  protected DeletionRepository createRepository() {
    return new ElasticsearchDeletionRepository(
        deleterConfig, client, Runnable::run, metrics, LOGGER);
  }
}
