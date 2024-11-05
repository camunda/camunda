/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist.adapter.os;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.tasklist.TasklistMigrationProperties;
import io.camunda.migration.tasklist.adapter.Adapter;
import io.camunda.migration.tasklist.util.IndexUtil;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.tasklist.entities.ProcessEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchAdapter implements Adapter {

  private static final Logger LOG = LoggerFactory.getLogger(OpensearchAdapter.class);
  private final TasklistMigrationProperties properties;
  private final OpenSearchClient client;
  private final String sourceIndexName;
  private final String targetIndexName;

  public OpensearchAdapter(
      final TasklistMigrationProperties properties, final OpensearchConnector connector) {
    this.properties = properties;
    targetIndexName = IndexUtil.getTargetIndexName(properties);
    sourceIndexName = IndexUtil.getSourceIndexName(properties);
    client = connector.createClient();
  }

  @Override
  public boolean migrate(
      final List<io.camunda.webapps.schema.entities.operate.ProcessEntity> entities) {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    entities.forEach(e -> migrateEntity(e, bulkRequest));

    try {
      final BulkResponse response = client.bulk(bulkRequest.build());
      final List<String> idsToBeDeleted = getMigratedIds(response);

      if (idsToBeDeleted.isEmpty()) {
        return !response.errors();
      }
      final BulkRequest deleteRequest = buildDeleteRequest(idsToBeDeleted);
      final BulkResponse deleteResponse = client.bulk(deleteRequest);
      if (deleteResponse.errors()) {
        LOG.error("Failed to delete entities: {}", deleteResponse);
      }
      return !deleteResponse.errors();
    } catch (final IOException e) {
      throw new MigrationException("", e);
    }
  }

  @Override
  public List<ProcessEntity> nextBatch() {
    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder().index(sourceIndexName).size(properties.getBatchSize());

    try {
      final var response = client.search(searchRequest.build(), ProcessEntity.class);

      return response.hits().hits().stream().map(Hit::source).toList();
    } catch (final IOException e) {
      throw new MigrationException("", e);
    }
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  private static List<String> getMigratedIds(final BulkResponse bulkResponse) {
    final List<String> idsToBeDeleted = new ArrayList<>();
    bulkResponse
        .items()
        .forEach(
            i -> {
              if (i.error() != null) {
                LOG.error(
                    "Failed to migrate entity with id: {}, reason: {}", i.id(), i.error().reason());
              } else {
                idsToBeDeleted.add(i.id());
              }
            });
    return idsToBeDeleted;
  }

  private void migrateEntity(
      final io.camunda.webapps.schema.entities.operate.ProcessEntity entity,
      final BulkRequest.Builder bulkRequest) {

    bulkRequest.operations(
        op ->
            op.update(
                e -> e.index(targetIndexName).id(entity.getId()).document(getUpdateMap(entity))));
  }

  private BulkRequest buildDeleteRequest(final List<String> ids) {
    final BulkRequest.Builder deleteRequest = new BulkRequest.Builder();
    ids.forEach(
        id -> deleteRequest.operations(op -> op.delete(d -> d.index(sourceIndexName).id(id))));
    return deleteRequest.build();
  }
}
