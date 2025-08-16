/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter.es;

import static java.util.stream.Collectors.toList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.reindex.Destination;
import co.elastic.clients.elasticsearch.core.reindex.Source;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.task.adapter.MigrationRepositoryIndex;
import io.camunda.migration.task.adapter.TaskMigrationAdapter;
import io.camunda.migration.task.adapter.TaskMigrationStep;
import io.camunda.migration.task.config.TaskMigrationProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ElasticsearchAdapter implements TaskMigrationAdapter {

  private static final String MAIN_INDEX_NAME = "tasklist-task-8.5.0_"; // TODO: Move to interface
  private final ElasticsearchClient client;
  private final TaskMigrationProperties properties;
  private final TaskTemplate sourceIndex;
  private final TaskTemplate destinationIndex;
  private final TasklistImportPositionIndex importPositionIndex;
  private final RetryDecorator retryDecorator;
  private final MigrationRepositoryIndex migrationRepositoryIndex;

  public ElasticsearchAdapter(
      final TaskMigrationProperties properties, final ConnectConfiguration connectConfiguration) {
    this.properties = properties;
    sourceIndex = new TaskTemplate(connectConfiguration.getIndexPrefix(), true);
    destinationIndex =
        new TaskTemplate(
            connectConfiguration.getIndexPrefix(), false); // TODO: Use correct destination index
    client = new ElasticsearchConnector(connectConfiguration).createClient();
    importPositionIndex =
        new TasklistImportPositionIndex(connectConfiguration.getIndexPrefix(), true);
    retryDecorator =
        new RetryDecorator(properties.getRetry())
            .withRetryOnException(
                e -> e instanceof IOException || e instanceof ElasticsearchException);
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), true);
  }

  @Override
  public List<String> getDatedTaskIndices() {
    final var alias = "tasklist-task-8.5.0_alias"; // TODO: Move to interface

    try {
      final var aliasIndices = client.indices().getAlias(g -> g.name(alias));
      return aliasIndices.result().keySet().stream()
          .filter(indexName -> !indexName.equals(MAIN_INDEX_NAME))
          .toList();
    } catch (final IOException e) {
      throw new RuntimeException(e); // TODO: Handle this properly
    }
  }

  @Override
  public void reindexDatedIndex(final String source) throws MigrationException {
    final String destination = generateDestinationIndex(source);
    reindex(source, destination);
  }

  @Override
  public void reindexMainIndex() throws MigrationException {
    reindex(MAIN_INDEX_NAME, destinationIndex.getFullQualifiedName());
  }

  @Override
  public void deleteIndex(final String indexName) throws MigrationException {
    // TODO: Complete
  }

  @Override
  public String getLastMigratedTaskKey() throws MigrationException {
    final SearchRequest searchRequest =
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
                                                    .query(TASK_MIGRATION_STEP_TYPE)))
                                .must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(MigrationRepositoryIndex.ID)
                                                    .value(TASK_MIGRATION_STEP_ID)))))
            .build();
    final SearchResponse<TaskMigrationStep> searchResponse;

    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching last migrated task",
              () -> client.search(searchRequest, TaskMigrationStep.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch last migrated task", e);
    }

    return searchResponse.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .map(TaskMigrationStep::getContent)
        .findFirst()
        .orElse(null);
  }

  @Override
  public void writeLastMigratedEntity(final String taskKey) throws MigrationException {
    final TaskMigrationStep currentStep = taskMigrationStep(taskKey);
    final UpdateRequest<TaskMigrationStep, TaskMigrationStep> updateRequest =
        new UpdateRequest.Builder<TaskMigrationStep, TaskMigrationStep>()
            .index(destinationIndex.getFullQualifiedName())
            .id(TASK_MIGRATION_STEP_ID)
            .docAsUpsert(true)
            .doc(currentStep)
            .refresh(Refresh.True)
            .upsert(currentStep)
            .build();

    try {
      retryDecorator.decorate(
          "Update last migrated task",
          () -> client.update(updateRequest, TaskMigrationStep.class),
          res -> res.result() != Result.Created && res.result() != Result.Updated);
    } catch (final Exception e) {
      throw new MigrationException("Failed to update migrated task", e);
    }
  }

  @Override
  public List<TaskEntity> nextBatch(final String lastMigratedTaskKey) throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(sourceIndex.getFullQualifiedName())
            .size(properties.getBatchSize())
            .sort(s -> s.field(f -> f.field(TASK_KEY).order(SortOrder.Asc)))
            .query(
                q ->
                    q.range(
                        m ->
                            m.term(
                                n ->
                                    n.field(TASK_KEY)
                                        .gt(
                                            lastMigratedTaskKey == null
                                                ? ""
                                                : lastMigratedTaskKey))))
            .build();
    final SearchResponse<TaskEntity> searchResponse;
    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching next task batch",
              () -> client.search(searchRequest, TaskEntity.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch next task batch", e);
    }

    return searchResponse.hits().hits().stream().map(Hit::source).collect(toList());
  }

  @Override
  public String updateEntities(final List<TaskEntity> entities) throws MigrationException {
    if (entities == null || entities.isEmpty()) {
      return null; // TODO: Change to optional?
    }
    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    final var idList = entities.stream().map(TaskEntity::getId).toList();
    entities.forEach(entity -> addEntityToBulkRequest(entity, bulkRequestBuilder));
    final BulkResponse response;
    try {
      final BulkRequest bulkRequest = bulkRequestBuilder.build();
      response =
          retryDecorator.decorate(
              "Migrate task entities %s".formatted(idList),
              () -> client.bulk(bulkRequest),
              (res) ->
                  res == null
                      || res.items().isEmpty()
                      || res.items().stream().allMatch(i -> i.error() != null));
    } catch (final Exception e) {
      throw new MigrationException("Failed to migrate task entities %s".formatted(idList), e);
    }
    return getLastUpdatedTaskKey(response.items());
  }

  @Override
  public Set<ImportPositionEntity> readImportPosition() throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .size(100)
            .index(importPositionIndex.getFullQualifiedName())
            .query(
                q ->
                    q.wildcard(
                        w -> w.field(ImportPositionIndex.ID).value("*-" + TaskTemplate.INDEX_NAME)))
            .build();
    final SearchResponse<ImportPositionEntity> searchResponse;

    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching import position",
              () -> client.search(searchRequest, ImportPositionEntity.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch import position", e);
    }

    return searchResponse.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  private void reindex(final String source, final String destination) throws MigrationException {
    final ReindexRequest createMissingRequest =
        new ReindexRequest.Builder()
            .source(
                new Source.Builder()
                    .index(source)
                    .size(properties.getBatchSize()) // batch size
                    .build())
            .dest(
                new Destination.Builder()
                    .index(destination)
                    .opType(OpType.Create) // only create missing docs
                    .build())
            .conflicts(Conflicts.Proceed) // ignore version conflicts
            .refresh(true) // refresh after indexing
            .build();

    final ReindexResponse createResponse;
    try {
      createResponse = client.reindex(createMissingRequest);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    System.out.println("Step 1: Created missing docs: " + createResponse.created());
  }

  private void addEntityToBulkRequest(
      final TaskEntity entity, final BulkRequest.Builder bulkRequest) {
    bulkRequest.operations(
        op ->
            op.update(
                e ->
                    e.index(
                            destinationIndex
                                .getFullQualifiedName()) // TODO: Verify dest is the correct index
                        .id(entity.getId())
                        .action(act -> act.doc(getUpdateMap(entity)))));
  }

  private String getLastUpdatedTaskKey(final List<BulkResponseItem> items) {
    final var sorted = items.stream().sorted(Comparator.comparing(BulkResponseItem::id)).toList();
    for (int i = 0; i < sorted.size(); i++) {
      if (sorted.get(i).error() != null) {
        return i == 0 ? null : Objects.requireNonNull(sorted.get(i - 1).id());
      }
    }
    return Objects.requireNonNull(sorted.getLast().id());
  }
}
