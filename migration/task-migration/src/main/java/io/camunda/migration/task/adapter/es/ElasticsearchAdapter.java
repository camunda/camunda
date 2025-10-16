/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter.es;

import static java.util.stream.Collectors.toMap;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.reindex.Destination;
import co.elastic.clients.elasticsearch.core.reindex.Source;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.ilm.DeleteAction;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.json.JsonData;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.migration.commons.storage.TasklistMigrationRepositoryIndex;
import io.camunda.migration.task.adapter.TaskEntityPair;
import io.camunda.migration.task.adapter.TaskLegacyIndex;
import io.camunda.migration.task.adapter.TaskMigrationAdapter;
import io.camunda.migration.task.adapter.TaskWithIndex;
import io.camunda.migration.task.util.MigrationUtils;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchAdapter implements TaskMigrationAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchAdapter.class);

  private final ElasticsearchClient client;
  private final MigrationConfiguration migrationConfiguration;
  private final RetentionConfiguration retentionConfiguration;
  private final TaskLegacyIndex legacyIndex;
  private final TaskTemplate destinationIndex;
  private final TasklistMigrationRepositoryIndex migrationIndex;
  private final RetryDecorator retryDecorator;
  private final TasklistImportPositionIndex importPositionIndex;

  public ElasticsearchAdapter(
      final MigrationConfiguration migrationConfiguration,
      final ConnectConfiguration connectConfiguration,
      final RetentionConfiguration retentionConfiguration) {
    this.migrationConfiguration = migrationConfiguration;
    client = new ElasticsearchConnector(connectConfiguration).createClient();
    retryDecorator =
        new RetryDecorator(migrationConfiguration.getRetry())
            .withRetryOnException(
                e -> e instanceof IOException || e instanceof ElasticsearchException);

    legacyIndex = new TaskLegacyIndex(connectConfiguration.getIndexPrefix(), true);
    destinationIndex = new TaskTemplate(connectConfiguration.getIndexPrefix(), true);
    migrationIndex =
        new TasklistMigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), true);
    importPositionIndex =
        new TasklistImportPositionIndex(connectConfiguration.getIndexPrefix(), true);
    this.retentionConfiguration = retentionConfiguration;
  }

  @Override
  public boolean migrationIndexExists() {
    try {
      return client.indices().exists(g -> g.index(migrationIndex.getFullQualifiedName())).value();
    } catch (final IOException e) {
      throw new MigrationException("Could not confirm the existence of the migration index", e);
    }
  }

  @Override
  public boolean migrationIsCompleted() {
    final var migrationStepContent = getLastMigratedContent();
    return MigrationUtils.isMigrationStepCompleted(migrationStepContent);
  }

  @Override
  public void markMigrationAsCompleted() {
    writeLastMigratedStep(MigrationUtils.getTaskMigrationCompletionStep());
  }

  @Override
  public List<String> getLegacyDatedIndices() {
    try {
      final var aliasIndices =
          retryDecorator.decorate(
              "Fetching legacy dated indices",
              () -> client.indices().getAlias(g -> g.name(legacyIndex.getAlias())),
              response -> !response.result().containsKey(legacyIndex.getFullQualifiedName()));
      return aliasIndices.result().keySet().stream()
          .filter(indexName -> !indexName.equals(legacyIndex.getFullQualifiedName()))
          .toList();
    } catch (final Exception e) {
      throw new MigrationException("Could not get the legacy dated indices", e);
    }
  }

  @Override
  public void reindexLegacyDatedIndex(final String legacyDatedIndex) throws MigrationException {
    final String newDatedIndex = MigrationUtils.generateNewIndexNameFromLegacy(legacyDatedIndex);
    final var documentsWereProcessed = reindex(legacyDatedIndex, newDatedIndex);
    if (documentsWereProcessed) {
      updateIndexAlias(newDatedIndex);
      setIndexLifecycle(newDatedIndex);
    } else {
      LOG.info(
          "No documents were reindexed from {} to {}. {} was not created.",
          legacyDatedIndex,
          newDatedIndex,
          newDatedIndex);
    }
  }

  @Override
  public void reindexLegacyMainIndex() throws MigrationException {
    reindex(legacyIndex.getFullQualifiedName(), destinationIndex.getFullQualifiedName());
    if (retentionConfiguration.isEnabled()) {
      applyLegacyIndexRetentionPolicy();
    }
  }

  @Override
  public void deleteLegacyIndex(final String legacyIndexToDelete) throws MigrationException {
    if (!legacyIndexToDelete.startsWith(legacyIndex.getFullQualifiedName())) {
      throw new MigrationException(
          "Cannot delete index: %s. It is not a legacy index.".formatted(legacyIndexToDelete));
    }
    try {
      client.indices().delete(DeleteIndexRequest.of(request -> request.index(legacyIndexToDelete)));
    } catch (final IOException e) {
      throw new MigrationException("Failed to delete index: " + legacyIndexToDelete, e);
    }
  }

  @Override
  public String getLastMigratedTaskId() throws MigrationException {
    final var migrationStepContent = getLastMigratedContent();
    return MigrationUtils.getTaskIdFromMigrationStepContent(migrationStepContent);
  }

  @Override
  public void writeLastMigratedTaskId(final String taskId) throws MigrationException {
    final ProcessorStep step = MigrationUtils.getTaskMigrationStepForTaskId(taskId);
    writeLastMigratedStep(step);
  }

  @Override
  public List<TaskEntityPair> nextBatch(final String lastMigratedTaskId) throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(destinationIndex.getAlias())
            .size(migrationConfiguration.getBatchSize())
            .sort(s -> s.field(f -> f.field(TaskTemplate.KEY).order(SortOrder.Asc)))
            .query(q -> q.bool(getBoolQueryForTasksToUpdate(lastMigratedTaskId)))
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

    final List<TaskWithIndex> tasksToUpdate =
        searchResponse.hits().hits().stream()
            .filter(Objects::nonNull)
            .map(
                hit -> {
                  final String index = hit.index();
                  final TaskEntity task = hit.source();
                  return new TaskWithIndex(index, task);
                })
            .toList();

    final var sourceIndexSearchRequest =
        new SearchRequest.Builder()
            .index(legacyIndex.getFullQualifiedName())
            .query(
                q ->
                    q.terms(
                        t ->
                            t.field(TaskTemplate.KEY) // The field to filter by
                                .terms(
                                    v ->
                                        v.value(
                                            tasksToUpdate.stream()
                                                .map(
                                                    taskWithIndex ->
                                                        FieldValue.of(
                                                            taskWithIndex.task().getKey()))
                                                .toList())) // The list of values to match
                        ))
            .build();

    final SearchResponse<TaskEntity> response;
    try {
      response = client.search(sourceIndexSearchRequest, TaskEntity.class);
    } catch (final IOException e) {
      throw new MigrationException("Failed to fetch original tasks for the batch update", e);
    }

    final var originalTasksByKey =
        response.hits().hits().stream()
            .map(Hit::source)
            .filter(Objects::nonNull)
            .collect(toMap(TaskEntity::getKey, task -> task));

    final List<TaskEntityPair> taskEntityPairs = new ArrayList<>();

    for (final var taskWithIndex : tasksToUpdate) {
      final var originalTask = originalTasksByKey.get(taskWithIndex.task().getKey());
      if (originalTask == null) {
        LOG.error(
            "Could not find original task for key: {}. Manual update is required",
            taskWithIndex.task().getKey());
      } else {
        taskEntityPairs.add(new TaskEntityPair(originalTask, taskWithIndex));
      }
    }

    return taskEntityPairs;
  }

  @Override
  public String updateAcrossAllIndices(final List<TaskWithIndex> tasksWithIndex)
      throws MigrationException {
    if (tasksWithIndex == null || tasksWithIndex.isEmpty()) {
      return null;
    }
    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    final var idList =
        tasksWithIndex.stream().map(taskWithIndex -> taskWithIndex.task().getId()).toList();
    tasksWithIndex.forEach(entity -> addEntityToBulkRequest(entity, bulkRequestBuilder));
    final BulkResponse response;
    try {
      final BulkRequest bulkRequest = bulkRequestBuilder.build();
      response = client.bulk(bulkRequest);
    } catch (final Exception e) {
      throw new MigrationException("Failed to migrate task entities %s".formatted(idList), e);
    }
    return getLastUpdatedTaskId(response.items());
  }

  @Override
  public Set<ImportPositionEntity> getImportPositions() throws MigrationException {
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
  public void applyRetentionOnLegacyRuntimeIndex() throws MigrationException {
    /// This is already handled by the {@link #applyPolicy()}
  }

  @Override
  public void blockArchiving() throws MigrationException {
    final var blockArchivingRequest =
        new PutMappingRequest.Builder()
            .index(importPositionIndex.getFullQualifiedName())
            .meta(SchemaManager.PI_ARCHIVING_BLOCKED_META_KEY, JsonData.of(true))
            .build();

    try {
      retryDecorator.decorate(
          "Blocking archiving",
          () -> client.indices().putMapping(blockArchivingRequest),
          res -> !res.acknowledged());
    } catch (final Exception e) {
      throw new MigrationException("Unable to block archiver", e);
    }
  }

  @Override
  public void resumeArchiving() throws MigrationException {
    final var resumeArchivingRequest =
        new PutMappingRequest.Builder()
            .index(importPositionIndex.getFullQualifiedName())
            .meta(SchemaManager.PI_ARCHIVING_BLOCKED_META_KEY, JsonData.of(false))
            .build();

    try {
      retryDecorator.decorate(
          "Resuming archiving",
          () -> client.indices().putMapping(resumeArchivingRequest),
          res -> !res.acknowledged());
    } catch (final Exception e) {
      throw new MigrationException("Unable to resume archiver", e);
    }
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  private void writeLastMigratedStep(final ProcessorStep step) throws MigrationException {
    final UpdateRequest<ProcessorStep, ProcessorStep> updateRequest =
        new UpdateRequest.Builder<ProcessorStep, ProcessorStep>()
            .index(migrationIndex.getFullQualifiedName())
            .id(TASK_MIGRATION_STEP_ID)
            .docAsUpsert(true)
            .doc(step)
            .refresh(Refresh.True)
            .upsert(step)
            .build();

    try {
      retryDecorator.decorate(
          "Update last migrated task",
          () -> client.update(updateRequest, ProcessorStep.class),
          res -> res.result() != Result.Created && res.result() != Result.Updated);
    } catch (final Exception e) {
      throw new MigrationException("Failed to update migrated task", e);
    }
  }

  private String getLastMigratedContent() throws MigrationException {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(migrationIndex.getFullQualifiedName())
            .size(1)
            .query(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                    m ->
                                        m.match(
                                            t ->
                                                t.field(TasklistMigrationRepositoryIndex.TYPE)
                                                    .query(TASK_MIGRATION_STEP_TYPE)))
                                .must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(TasklistMigrationRepositoryIndex.ID)
                                                    .value(TASK_MIGRATION_STEP_ID)))))
            .build();
    final SearchResponse<ProcessorStep> searchResponse;

    try {
      searchResponse =
          retryDecorator.decorate(
              "Fetching task migration step",
              () -> client.search(searchRequest, ProcessorStep.class),
              res -> res.timedOut() || Boolean.TRUE.equals(res.terminatedEarly()));
    } catch (final Exception e) {
      throw new MigrationException("Failed to fetch last migrated task", e);
    }

    return searchResponse.hits().hits().stream()
        .map(Hit::source)
        .filter(Objects::nonNull)
        .map(ProcessorStep::getContent)
        .findFirst()
        .orElse(null);
  }

  private BoolQuery getBoolQueryForTasksToUpdate(final String lastMigratedTaskId) {
    return BoolQuery.of(
        b ->
            b.must(
                    must ->
                        must.range(
                            m ->
                                m.term(
                                    n ->
                                        n.field("id")
                                            .gt(
                                                lastMigratedTaskId == null
                                                    ? "0"
                                                    : lastMigratedTaskId))))
                .mustNot(
                    mustNot ->
                        mustNot.exists(
                            ExistsQuery.of(
                                e -> e.field("creationTime") // Field "creationTime" should only be
                                // populated during creation
                                )))
                .must(
                    must ->
                        must.term(
                            term ->
                                term.field("join")
                                    .value(TaskJoinRelationshipType.TASK.getType()))));
  }

  private void setIndexLifecycle(final String newDatedIndex) throws MigrationException {
    if (!retentionConfiguration.isEnabled()) {
      return;
    }

    final var settingsRequest =
        new PutIndicesSettingsRequest.Builder()
            .settings(
                settings ->
                    settings.lifecycle(
                        lifecycle -> lifecycle.name(retentionConfiguration.getPolicyName())))
            .index(newDatedIndex)
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .build();
    try {
      client.indices().putSettings(settingsRequest);
    } catch (final IOException e) {
      throw new MigrationException("Failed to apply index lifecycle to index: " + newDatedIndex, e);
    }
  }

  private boolean reindex(final String source, final String destination) throws MigrationException {
    final ReindexRequest createMissingRequest =
        new ReindexRequest.Builder()
            .source(
                new Source.Builder()
                    .index(source)
                    .size(migrationConfiguration.getBatchSize())
                    .build())
            .dest(
                new Destination.Builder()
                    .index(destination)
                    .opType(OpType.Create) // only create missing docs
                    .build())
            .conflicts(Conflicts.Proceed) // ignore version conflicts
            .refresh(true)
            .build();

    try {
      final var reindexResponse = client.reindex(createMissingRequest);
      return reindexResponse != null
          && reindexResponse.total() != null
          && reindexResponse.total() > 0;
    } catch (final IOException e) {
      throw new MigrationException(e);
    }
  }

  private void updateIndexAlias(final String newDatedIndex) throws MigrationException {
    final UpdateAliasesRequest indicesAliasesRequest =
        UpdateAliasesRequest.of(
            u ->
                u.actions(
                    a ->
                        a.add(
                            t ->
                                t.index(newDatedIndex)
                                    .isWriteIndex(false)
                                    .aliases(destinationIndex.getAlias()))));

    try {
      client.indices().updateAliases(indicesAliasesRequest);
    } catch (final Exception e) {
      throw new MigrationException(
          "Failed to disable writes for new dated index: %s".formatted(newDatedIndex), e);
    }
  }

  private void addEntityToBulkRequest(
      final TaskWithIndex taskWithIndex, final BulkRequest.Builder bulkRequest) {
    bulkRequest.operations(
        op ->
            op.update(
                e ->
                    e.index(taskWithIndex.index())
                        .id(taskWithIndex.task().getId())
                        .action(
                            act -> act.doc(MigrationUtils.getUpdateMap(taskWithIndex.task())))));
  }

  private String getLastUpdatedTaskId(final List<BulkResponseItem> items) {
    final var sorted = items.stream().sorted(Comparator.comparing(BulkResponseItem::id)).toList();
    for (int i = 0; i < sorted.size(); i++) {
      if (sorted.get(i).error() != null) {
        return i == 0 ? null : Objects.requireNonNull(sorted.get(i - 1).id());
      }
    }
    return Objects.requireNonNull(sorted.getLast().id());
  }

  ///  Forked from io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient()

  private void applyLegacyIndexRetentionPolicy() {
    createPolicy();
    applyPolicy();
  }

  private void applyPolicy() {
    try {
      client
          .indices()
          .putSettings(
              r ->
                  r.index(legacyIndex.getFullQualifiedName())
                      .settings(
                          s ->
                              s.index(
                                  i ->
                                      i.lifecycle(
                                          l ->
                                              l.name(LEGACY_INDEX_RETENTION_POLICY_NAME)
                                                  .originationDate(System.currentTimeMillis())))));
    } catch (final IOException e) {
      final var message =
          "Failed to apply index lifecycle policy '%s' to '%s' index"
              .formatted(LEGACY_INDEX_RETENTION_POLICY_NAME, legacyIndex.getFullQualifiedName());
      LOG.error(message, e);
      throw new MigrationException(message, e);
    }
  }

  private void createPolicy() {
    final PutLifecycleRequest request = putLifecycleRequest();
    try {
      client.ilm().putLifecycle(request);
    } catch (final IOException e) {
      final var errMsg =
          String.format(
              "Index lifecycle policy [%s] failed to PUT", LEGACY_INDEX_RETENTION_POLICY_NAME);
      LOG.error(errMsg, e);
      throw new MigrationException(errMsg, e);
    }
  }

  private PutLifecycleRequest putLifecycleRequest() {
    return new PutLifecycleRequest.Builder()
        .name(LEGACY_INDEX_RETENTION_POLICY_NAME)
        .policy(
            policy ->
                policy.phases(
                    phase ->
                        phase.delete(
                            del ->
                                del.minAge(
                                        m ->
                                            m.time(
                                                migrationConfiguration
                                                    .getLegacyIndexRetentionAge()))
                                    .actions(a -> a.delete(DeleteAction.of(d -> d))))))
        .build();
  }
}
