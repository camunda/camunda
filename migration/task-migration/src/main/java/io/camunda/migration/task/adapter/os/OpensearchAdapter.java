/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter.os;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.camunda.search.connect.os.OpensearchConnector;
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
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpType;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.reindex.Destination;
import org.opensearch.client.opensearch.core.reindex.Source;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchAdapter implements TaskMigrationAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(OpensearchAdapter.class);
  private static final String LEGACY_RUNTIME_POLICY =
      "/opensearch-legacy-runtime-index-policy.json";
  private static final String ISM_POLICIES_ENDPOINT = "_plugins/_ism/policies";
  private final OpenSearchClient client;
  private final OpenSearchGenericClient genericClient;
  private final MigrationConfiguration migrationConfiguration;
  private final RetentionConfiguration retentionConfiguration;
  private final TaskLegacyIndex legacyIndex;
  private final TaskTemplate destinationIndex;
  private final TasklistMigrationRepositoryIndex migrationIndex;
  private final RetryDecorator retryDecorator;
  private final TasklistImportPositionIndex importPositionIndex;

  public OpensearchAdapter(
      final MigrationConfiguration migrationConfiguration,
      final ConnectConfiguration connectConfiguration,
      final RetentionConfiguration retentionConfiguration) {
    this.migrationConfiguration = migrationConfiguration;
    client = new OpensearchConnector(connectConfiguration).createClient();
    genericClient = new OpenSearchGenericClient(client._transport(), client._transportOptions());
    retryDecorator =
        new RetryDecorator(migrationConfiguration.getRetry())
            .withRetryOnException(
                e -> e instanceof IOException || e instanceof OpenSearchException);

    legacyIndex = new TaskLegacyIndex(connectConfiguration.getIndexPrefix(), false);
    destinationIndex = new TaskTemplate(connectConfiguration.getIndexPrefix(), false);
    migrationIndex =
        new TasklistMigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), false);
    importPositionIndex =
        new TasklistImportPositionIndex(connectConfiguration.getIndexPrefix(), false);
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
    reindex(legacyDatedIndex, newDatedIndex);
    updateIndexAlias(newDatedIndex);
    setIndexLifecycle(newDatedIndex);
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
  public void deleteLegacyMainIndex() throws MigrationException {
    deleteLegacyIndex(legacyIndex.getFullQualifiedName());
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
  public String updateAcrossAllIndices(final List<TaskWithIndex> tasks) throws MigrationException {
    if (tasks == null || tasks.isEmpty()) {
      return null;
    }
    final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
    final var idList = tasks.stream().map(taskWithIndex -> taskWithIndex.task().getId()).toList();
    tasks.forEach(entity -> addEntityToBulkRequest(entity, bulkRequestBuilder));
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
                                                    .query(
                                                        FieldValue.of(TASK_MIGRATION_STEP_TYPE))))
                                .must(
                                    m ->
                                        m.term(
                                            t ->
                                                t.field(TasklistMigrationRepositoryIndex.ID)
                                                    .value(
                                                        FieldValue.of(TASK_MIGRATION_STEP_ID))))))
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
                            r ->
                                r.field("id")
                                    .gt(
                                        JsonData.of(
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
                                    .value(
                                        FieldValue.of(TaskJoinRelationshipType.TASK.getType())))));
  }

  private void reindex(final String source, final String destination) throws MigrationException {
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
      client.reindex(createMissingRequest);
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
          "Failed to disable writes for legacy index: %s".formatted(newDatedIndex), e);
    }
  }

  private void setIndexLifecycle(final String newDatedIndex) throws MigrationException {
    if (!retentionConfiguration.isEnabled()) {
      return;
    }

    final AddPolicyRequestBody value =
        new AddPolicyRequestBody(retentionConfiguration.getPolicyName());
    final var request =
        Requests.builder().method("POST").endpoint("_plugins/_ism/add/" + newDatedIndex);
    try {
      genericClient.execute(request.json(value, genericClient._transport().jsonpMapper()).build());
    } catch (final Exception e) {
      throw new MigrationException("Failed to apply index lifecycle to index: " + newDatedIndex, e);
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
                        .document(MigrationUtils.getUpdateMap(taskWithIndex.task()))));
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

  /// Forked from io.camunda.search.schema.opensearch.OpensearchEngineClient

  private void applyLegacyIndexRetentionPolicy() {
    createPolicy();
    applyPolicy();
  }

  private void createPolicy() {
    final var indexAge = getIndexCreationTime();
    final var absoluteIndexRetentionAge =
        evaluateAbsoluteLegacyIndexRetentionAge(
            indexAge, migrationConfiguration.getLegacyIndexRetentionAge());
    final var request = createRuntimeIndexPolicyRequest(absoluteIndexRetentionAge);
    final String policyName = LEGACY_INDEX_RETENTION_POLICY_NAME;
    try (final var response = client.generic().execute(request)) {
      if (response.getStatus() / 100 != 2) {
        throw new MigrationException(
            String.format(
                "Creating index state management policy [%s] with min_deletion_age [%s] failed. Http response = [%s]",
                policyName, absoluteIndexRetentionAge, response.getBody().get().bodyAsString()));
      }

    } catch (final IOException | OpenSearchException exception) {
      final String exceptionMessage = exception.getMessage();
      if (exceptionMessage.contains("already exists")) {
        LOG.warn(
            "Expected to create ISM policy with name '{}', but failed with: '{}'.",
            policyName,
            exceptionMessage);
        return;
      }
      final var errMsg =
          String.format("Failed to create index state management policy [%s]", policyName);
      throw new MigrationException(errMsg, exception);
    }
  }

  private long getIndexCreationTime() throws MigrationException {
    final var indexName = legacyIndex.getFullQualifiedName();
    final GetIndicesSettingsRequest request =
        new GetIndicesSettingsRequest.Builder()
            .index(indexName)
            .name("index.creation_date")
            .build();

    final GetIndicesSettingsResponse response;
    try {
      response = client.indices().getSettings(request);
    } catch (final IOException e) {
      throw new MigrationException(e);
    }

    // Get the creation date (stored as milliseconds since epoch)
    final var creationDateString = response.get(indexName).settings().index().creationDate();

    return Long.parseLong(creationDateString);
  }

  private String evaluateAbsoluteLegacyIndexRetentionAge(
      final Long indexAge, final String futureRetention) {
    if (indexAge == null || futureRetention == null || futureRetention.isEmpty()) {
      return futureRetention;
    }

    final long currentTime = System.currentTimeMillis();
    final long elapsedTime = Math.abs(currentTime - indexAge);

    // Extract the numeric value and unit from futureRetention
    final String unit = futureRetention.substring(futureRetention.length() - 1);
    final int futureValue =
        Integer.parseInt(futureRetention.substring(0, futureRetention.length() - 1));

    final long elapsedInUnit;
    switch (unit) {
      case "s":
        elapsedInUnit = elapsedTime / 1000;
        break;
      case "m":
        elapsedInUnit = elapsedTime / (1000 * 60);
        break;
      case "h":
        elapsedInUnit = elapsedTime / (1000 * 60 * 60);
        break;
      case "d":
        elapsedInUnit = elapsedTime / (1000 * 60 * 60 * 24);
        break;
      default:
        // If unknown unit, return original futureRetention
        return futureRetention;
    }

    final long totalRetention = elapsedInUnit + futureValue;
    return totalRetention + unit;
  }

  private Request createRuntimeIndexPolicyRequest(final String retentionAge) {
    final ObjectMapper objectMapper = new ObjectMapper();
    try (final var policyJson = getClass().getResourceAsStream(LEGACY_RUNTIME_POLICY)) {
      final var jsonMap = objectMapper.readTree(policyJson);
      final var conditions =
          (ObjectNode)
              jsonMap
                  .path("policy")
                  .path("states")
                  .path(0)
                  .path("transitions")
                  .path(0)
                  .path("conditions");
      conditions.put("min_index_age", retentionAge);

      final var policy = objectMapper.writeValueAsBytes(jsonMap);

      final var builder =
          Requests.builder()
              .method("PUT")
              .endpoint(getPolicyEndpoint())
              .body(Body.from(policy, "application/json"));

      return builder.build();
    } catch (final IOException e) {
      throw new MigrationException("Failed to deserialize policy file " + LEGACY_RUNTIME_POLICY, e);
    }
  }

  private String getPolicyEndpoint() {
    return String.format("%s/%s", ISM_POLICIES_ENDPOINT, LEGACY_INDEX_RETENTION_POLICY_NAME);
  }

  private void applyPolicy() {
    final ObjectMapper objectMapper = new ObjectMapper();
    final var requestEntity = new AddPolicyRequestBody(LEGACY_INDEX_RETENTION_POLICY_NAME);
    try {
      final var requestBody = objectMapper.writeValueAsBytes(requestEntity);
      final var request =
          Requests.builder()
              .method("POST")
              .body(Body.from(requestBody, "application/json"))
              .endpoint("/_plugins/_ism/add/" + legacyIndex.getFullQualifiedName())
              .build();

      final var response = client.generic().execute(request);
      if (response.getStatus() / 100 != 2) {
        throw new MigrationException(
            String.format(
                "Applying index state management policy [%s] to index [%s] failed. Http response = [%s]",
                LEGACY_INDEX_RETENTION_POLICY_NAME,
                legacyIndex.getFullQualifiedName(),
                response.getBody().map(Body::bodyAsString)));
      }
    } catch (final IOException e) {
      throw new MigrationException(e);
    }
  }

  private record AddPolicyRequestBody(@JsonProperty("policy_id") String policyId) {}
}
