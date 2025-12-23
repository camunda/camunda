/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchHistoryDeletionIT extends HistoryDeletionRepositoryIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchHistoryDeletionIT.class);
  private final OpenSearchAsyncClient client;

  public OpenSearchHistoryDeletionIT() {
    super(searchDB.osUrl(), false);
    final var connector = new OpensearchConnector(config.getConnect());
    client = connector.createAsyncClient();
  }

  @Override
  protected HistoryDeletionRepository createRepository(
      final String indexPrefix, final int partitionId) {
    return new OpenSearchHistoryDeletionRepository(
        new TestExporterResourceProvider(indexPrefix, false),
        client,
        Runnable::run,
        LOGGER,
        partitionId,
        config.getHistoryDeletion());
  }

  @Override
  protected void index(final HistoryDeletionEntity entity) throws IOException {
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
