/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist.adapter.os;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.tasklist.MigrationRepositoryIndex;
import io.camunda.migration.tasklist.TasklistMigrationProperties;
import io.camunda.migration.tasklist.adapter.Adapter;
import io.camunda.operate.schema.migration.AbstractStep;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchAdapter implements Adapter {

  private static final Logger LOG = LoggerFactory.getLogger(OpensearchAdapter.class);
  private final TasklistMigrationProperties properties;
  private final OpenSearchClient client;
  private final MigrationRepositoryIndex migrationRepositoryIndex;
  private final ProcessIndex processIndex;

  public OpensearchAdapter(
      final TasklistMigrationProperties properties, final OpensearchConnector connector) {
    this.properties = properties;
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(properties.getConnect().getIndexPrefix(), false);
    processIndex = new ProcessIndex(properties.getConnect().getIndexPrefix(), false);
    client = connector.createClient();
  }

  @Override
  public String migrate(final List<ProcessEntity> entities) throws MigrationException {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    entities.forEach(e -> migrateEntity(e, bulkRequest));

    try {
      final BulkResponse response = client.bulk(bulkRequest.build());
      return lastUpdatedProcessDefinition(response.items());
    } catch (final IOException e) {
      throw new MigrationException("Tasklist migration step failed", e);
    }
  }

  @Override
  public List<ProcessEntity> nextBatch(final String processDefinitionKey) {
    final SearchRequest request =
        new SearchRequest.Builder()
            .index(processIndex.getFullQualifiedName())
            .size(properties.getBatchSize())
            .sort(s -> s.field(f -> f.field(PROCESS_DEFINITION_KEY).order(SortOrder.Asc)))
            .query(
                q ->
                    q.range(
                        r ->
                            r.field(PROCESS_DEFINITION_KEY)
                                .gt(
                                    JsonData.of(
                                        processDefinitionKey == null ? "" : processDefinitionKey))))
            .build();

    final SearchResponse<ProcessEntity> searchResponse;
    try {
      searchResponse = client.search(request, ProcessEntity.class);
      return searchResponse.hits().hits().stream().map(Hit::source).toList();
    } catch (final IOException | OpenSearchException e) {
      throw new MigrationException("Failed to fetch next batch", e);
    }
  }

  @Override
  public String readLastMigratedEntity() throws MigrationException {
    final SearchRequest request =
        new SearchRequest.Builder()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .size(1)
            .query(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                    m ->
                                        m.match(
                                            t ->
                                                t.field(MigrationRepositoryIndex.TYPE)
                                                    .query(FieldValue.of(PROCESSOR_STEP_TYPE))))
                                .must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(MigrationRepositoryIndex.ID)
                                                    .value(FieldValue.of(PROCESSOR_STEP_ID))))))
            .build();

    final SearchResponse<ProcessorStep> searchResponse;
    try {
      searchResponse = client.search(request, ProcessorStep.class);
      return searchResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .map(AbstractStep::getContent)
          .findFirst()
          .orElse(null);
    } catch (final IOException | OpenSearchException e) {
      throw new MigrationException("Failed to fetch next batch", e);
    }
  }

  @Override
  public void writeLastMigratedEntity(final String processDefinitionKey) throws MigrationException {
    final UpdateRequest<ProcessorStep, ProcessorStep> updateRequest =
        new UpdateRequest.Builder<ProcessorStep, ProcessorStep>()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .id(PROCESSOR_STEP_ID)
            .docAsUpsert(true)
            .doc(upsertProcessorStep(processDefinitionKey))
            .build();

    try {
      client.update(updateRequest, ProcessorStep.class);
    } catch (final IOException e) {
      throw new MigrationException("Failed to write last migrated entity", e);
    }
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  private void migrateEntity(final ProcessEntity entity, final BulkRequest.Builder bulkRequest) {

    bulkRequest.operations(
        op ->
            op.update(
                e ->
                    e.index(processIndex.getFullQualifiedName())
                        .id(entity.getId())
                        .document(getUpdateMap(entity))));
  }

  private String lastUpdatedProcessDefinition(final List<BulkResponseItem> items) {
    final var sorted = items.stream().sorted(Comparator.comparing(BulkResponseItem::id)).toList();
    for (int i = 0; i < sorted.size(); i++) {
      if (sorted.get(i).error() != null) {
        return i > 0
            ? Objects.requireNonNull(sorted.get(i - 1).id())
            : Objects.requireNonNull(sorted.get(i).id());
      }
    }

    return Objects.requireNonNull(sorted.getLast().id());
  }
}
