/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
public class ElasticsearchHistoryDeletionRepositoryIT extends HistoryDeletionRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchHistoryDeletionRepositoryIT.class);
  private final ElasticsearchAsyncClient client;

  public ElasticsearchHistoryDeletionRepositoryIT() {
    super("http://" + searchDB.esUrl(), true);
    final var connector = new ElasticsearchConnector(config.getConnect());
    client = connector.createAsyncClient();
  }

  @Override
  protected HistoryDeletionRepository createRepository(
      final String indexPrefix, final int partitionId) {
    return new ElasticsearchHistoryDeletionRepository(
        new TestExporterResourceProvider(indexPrefix, true),
        client,
        Runnable::run,
        LOGGER,
        partitionId,
        config.getHistoryDeletion());
  }

  @Override
  protected void index(final HistoryDeletionEntity entity) {
    client
        .index(
            b ->
                b.index(historyDeletionIndex.getFullQualifiedName())
                    .document(entity)
                    .id(entity.getId()))
        .join();
    client.indices().refresh(r -> r.index(historyDeletionIndex.getFullQualifiedName())).join();
  }
}
