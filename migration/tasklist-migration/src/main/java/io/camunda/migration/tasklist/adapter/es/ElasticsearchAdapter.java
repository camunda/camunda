/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist.adapter.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.tasklist.TasklistMigrationProperties;
import io.camunda.migration.tasklist.adapter.Adapter;
import io.camunda.migration.tasklist.util.IndexUtil;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.tasklist.entities.ProcessEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchAdapter implements Adapter {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchAdapter.class);
  private final ElasticsearchClient client;
  private final TasklistMigrationProperties properties;
  private final String sourceIndexName;
  private final String targetIndexName;

  public ElasticsearchAdapter(
      final TasklistMigrationProperties properties, final ElasticsearchConnector connector) {
    this.properties = properties;
    targetIndexName = IndexUtil.getTargetIndexName(properties);
    sourceIndexName = IndexUtil.getSourceIndexName(properties);
    client = connector.createClient();
  }

  @Override
  public boolean migrate(
      final List<io.camunda.webapps.schema.entities.operate.ProcessEntity> entities) {
    try {
      /* Migrate Entities */
      final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
      entities.forEach(entity -> migrateEntity(entity, bulkRequest));
      BulkResponse response = client.bulk(bulkRequest.build());

      final List<String> idsToBeDeleted = getMigratedIds(response);

      if (idsToBeDeleted.isEmpty()) {
        return !response.errors();
      }
      /* Delete Entities */
      final BulkRequest deleteRequest = buildDeleteRequest(idsToBeDeleted);
      response = client.bulk(deleteRequest);
      if (response.errors()) {
        LOG.error("Failed to delete entities: {}", response.items());
      }
      return !response.errors();
    } catch (final IOException e) {
      throw new MigrationException("Tasklist migration step failed", e);
    }
  }

  @Override
  public List<ProcessEntity> nextBatch() {
    final SearchRequest searchRequest =
        SearchRequest.of(
            s ->
                s.index(sourceIndexName)
                    .size(properties.getBatchSize())
                    .query(Query.of(q -> q.matchAll(m -> m))));
    final SearchResponse<ProcessEntity> searchResponse;
    try {
      searchResponse = client.search(searchRequest, ProcessEntity.class);
    } catch (final IOException e) {
      LOG.error("Failed to acquire new migration batch", e);
      throw new MigrationException("Failed to acquire new migration batch", e);
    }

    return searchResponse.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  private List<String> getMigratedIds(final BulkResponse bulkResponse) {
    final List<String> idsToBeDeleted = new ArrayList<>();
    bulkResponse
        .items()
        .forEach(
            i -> {
              if (i.error() != null) {
                LOG.error("Failed to migrate entity with id: {}, reason: {}", i.id(), i.error());
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
                e ->
                    e.index(targetIndexName)
                        .id(entity.getId())
                        .action(act -> act.doc(getUpdateMap(entity)))));
  }

  private BulkRequest buildDeleteRequest(final List<String> ids) {
    final BulkRequest.Builder deleteRequest = new BulkRequest.Builder();
    ids.forEach(
        id -> deleteRequest.operations(op -> op.delete(d -> d.index(sourceIndexName).id(id))));
    return deleteRequest.build();
  }
}
