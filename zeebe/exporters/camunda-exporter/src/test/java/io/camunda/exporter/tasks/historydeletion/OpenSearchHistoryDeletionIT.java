/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.io.IOException;
import java.time.InstantSource;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Refresh;
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
        config.getHistoryDeletion(),
        InstantSource.system(),
        new TargetIndexLocator());
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

  @Override
  protected long countAuditLogCleanupEntries() throws IOException {
    client.indices().refresh(r -> r.index(auditLogCleanupIndex.getFullQualifiedName())).join();
    return client.count(c -> c.index(auditLogCleanupIndex.getFullQualifiedName())).join().count();
  }

  @Override
  protected void indexOperation(final String indexName, final String id) throws IOException {
    client
        .index(
            b ->
                b.index(indexName)
                    .id(id)
                    .document(Map.of("id", id, "state", OperationState.SCHEDULED.name()))
                    .refresh(Refresh.True))
        .join();
  }

  @Override
  protected String getOperationState(final String indexName, final String id) throws IOException {
    client.indices().refresh(r -> r.index(indexName)).join();
    final var response = client.get(g -> g.index(indexName).id(id), Map.class).join();
    return (String) response.source().get("state");
  }
}
