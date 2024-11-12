/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.os;

import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_ALREADY_EXISTS_EXCEPTION_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME;
import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScript;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.builders.OptimizeSearchRequestOS;
import io.camunda.optimize.service.db.os.builders.OptimizeUpdateRequestOS;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.client.dsl.RequestDSL;
import io.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.repository.os.TaskRepositoryOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto.Fields;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TrackHits;
import org.opensearch.client.opensearch.indices.AliasDefinition;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsTemplateRequest;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettings.Builder;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.indices.update_aliases.Action;
import org.opensearch.client.opensearch.tasks.Info;
import org.opensearch.client.opensearch.tasks.ListRequest;
import org.opensearch.client.opensearch.tasks.ListResponse;
import org.opensearch.client.opensearch.tasks.State;
import org.opensearch.client.opensearch.tasks.TaskExecutingNode;
import org.slf4j.Logger;

public class SchemaUpgradeClientOS extends SchemaUpgradeClient<OptimizeOpenSearchClient, Builder> {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SchemaUpgradeClientOS.class);
  private final ObjectMapper objectMapper;

  public SchemaUpgradeClientOS(
      final OpenSearchSchemaManager schemaManager,
      final OpenSearchMetadataService metadataService,
      final OptimizeOpenSearchClient opensearchClient,
      final ObjectMapper objectMapper) {
    super(
        schemaManager,
        metadataService,
        DatabaseType.OPENSEARCH,
        opensearchClient,
        new TaskRepositoryOS(opensearchClient));
    this.objectMapper = objectMapper;
  }

  @Override
  public List<UpgradeStepLogEntryDto> getAppliedUpdateStepsForTargetVersion(
      final String targetOptimizeVersion) {

    final SearchResponse<UpgradeStepLogEntryDto> searchResponse;
    try {
      searchResponse =
          databaseClient
              .getOpenSearchClient()
              .search(
                  OptimizeSearchRequestOS.of(
                      s ->
                          s.optimizeIndex(databaseClient, UPDATE_LOG_ENTRY_INDEX_NAME)
                              .query(
                                  Query.of(
                                      q ->
                                          q.bool(
                                              b ->
                                                  b.must(
                                                      m ->
                                                          m.term(
                                                              t ->
                                                                  t.field(Fields.optimizeVersion)
                                                                      .value(
                                                                          FieldValue.of(
                                                                              targetOptimizeVersion)))))))
                              .size(LIST_FETCH_LIMIT)),
                  UpgradeStepLogEntryDto.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to fetch completed update steps for target version %s",
              targetOptimizeVersion);
      LOG.error(reason, e);
      throw new UpgradeRuntimeException(reason, e);
    }

    return searchResponse.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
  }

  @Override
  public void reindex(
      final IndexMappingCreator<IndexSettings.Builder> sourceIndex,
      final IndexMappingCreator<Builder> targetIndex,
      final DatabaseQueryWrapper queryWrapper,
      final String mappingScript) {
    final Query sourceDocumentFilterQuery = queryWrapper.osQuery();
    final ReindexRequest.Builder reindexRequest =
        createReindexRequest(
            getIndexNameService().getOptimizeIndexNameWithVersion(sourceIndex),
            getIndexNameService().getOptimizeIndexNameWithVersion(targetIndex),
            sourceDocumentFilterQuery);

    if (mappingScript != null) {
      reindexRequest.script(createDefaultScript(mappingScript));
    }

    final String reindexTaskId = submitReindexTask(reindexRequest.build());
    waitUntilTaskIsFinished(reindexTaskId, targetIndex.getIndexName());
  }

  @Override
  public void reindex(
      final String sourceIndex,
      final String targetIndex,
      final String mappingScript,
      final Map<String, Object> parameters) {
    LOG.debug(
        "Reindexing from index [{}] to [{}] using the mapping script [{}].",
        sourceIndex,
        targetIndex,
        mappingScript);
    if (areDocCountsEqual(sourceIndex, targetIndex)) {
      LOG.info(
          "Found that index [{}] already contains the same amount of documents as [{}], will skip reindex.",
          targetIndex,
          sourceIndex);
    } else {
      final Supplier<ReindexRequest> supplier =
          () -> {
            final ReindexRequest.Builder reindexRequest =
                createReindexRequest(sourceIndex, targetIndex, null).waitForCompletion(false);
            if (mappingScript != null) {
              reindexRequest.script(QueryDSL.script(mappingScript, parameters));
            }
            return reindexRequest.build();
          };
      waitForOrSubmitNewTask(
          createReIndexRequestDescription(List.of(sourceIndex), targetIndex),
          supplier.get(),
          String -> getPendingReindexTask(supplier.get()),
          String -> submitReindexTask(supplier.get()));
    }
  }

  @Override
  public <T> void upsert(final String index, final String id, final T documentDto) {
    try {
      databaseClient
          .getOpenSearchClient()
          .update(
              OptimizeUpdateRequestOS.of(
                  u ->
                      u.optimizeIndex(databaseClient, index)
                          .id(id)
                          .doc(documentDto)
                          .docAsUpsert(true)),
              Object.class);
    } catch (final Exception e) {
      final String message =
          String.format("Could not upsert document with id %s to index %s.", id, index);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public <T> Optional<T> getDocumentByIdAs(
      final String index, final String id, final Class<T> resultType) {
    final SearchRequest.Builder searchReqBuilder =
        RequestDSL.searchRequestBuilder()
            .index(List.of(index))
            .query(QueryDSL.ids(id))
            .trackTotalHits(new TrackHits.Builder().enabled(true).build())
            .size(100);

    final String errorMessage = "Was not able to retrieve all documents for indices";
    final SearchResponse<T> searchResponse =
        databaseClient.search(searchReqBuilder, resultType, errorMessage);
    return searchResponse.hits().hits().stream().map(Hit::source).findFirst();
  }

  @Override
  public boolean indexTemplateExists(final String indexTemplateName) {
    LOG.debug("Checking if index template exists [{}].", indexTemplateName);
    final ExistsTemplateRequest.Builder request =
        new ExistsTemplateRequest.Builder()
            .name(databaseClient.applyIndexPrefixes(indexTemplateName));
    try {
      return databaseClient.getOpenSearchClient().indices().existsTemplate(request.build()).value();
    } catch (final Exception e) {
      throw new UpgradeRuntimeException(
          String.format(
              "Could not validate whether index template [%s] exists!", indexTemplateName),
          e);
    }
  }

  @Override
  public void deleteTemplateIfExists(final String indexTemplateName) {
    if (indexTemplateExists(indexTemplateName)) {
      try {
        databaseClient.deleteIndexTemplateByIndexTemplateName(indexTemplateName);
      } catch (final Exception e) {
        throw new UpgradeRuntimeException(
            String.format("Could not delete index template [%s]!", indexTemplateName), e);
      }
    }
  }

  @Override
  public void createIndexFromTemplate(final String indexNameWithSuffix) {
    final CreateIndexRequest createIndexRequest =
        CreateIndexRequest.of(c -> c.index(indexNameWithSuffix));
    try {
      databaseClient.createIndex(createIndexRequest);
    } catch (final OpenSearchException e) {
      if (e.status() == BAD_REQUEST.code()
          && e.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_TYPE)) {
        LOG.debug("Index {} from template already exists.", indexNameWithSuffix);
      } else {
        throw e;
      }
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          String.format("Could not create index %s from template.", indexNameWithSuffix), e);
    }
  }

  @Override
  public void addAliases(
      final Set<String> indexAliases, final String completeIndexName, final boolean isWriteAlias) {
    LOG.debug("Adding aliases [{}] to index [{}].", indexAliases, completeIndexName);

    try {
      final UpdateAliasesRequest indicesAliasesRequest =
          UpdateAliasesRequest.of(
              u ->
                  u.actions(
                      a ->
                          a.add(
                              t ->
                                  t.index(completeIndexName)
                                      .isWriteIndex(isWriteAlias)
                                      .aliases(indexAliases.stream().toList()))));
      databaseClient.getOpenSearchClient().indices().updateAliases(indicesAliasesRequest);
    } catch (final Exception e) {
      throw new UpgradeRuntimeException(
          String.format("Could not add alias to index [%s]!", completeIndexName), e);
    }
  }

  @Override
  public void insertDataByIndexName(final IndexMappingCreator indexMapping, final String data) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    LOG.debug("Inserting data to indexAlias [{}]. Data payload is [{}]", aliasName, data);
    try {
      databaseClient
          .getOpenSearchClient()
          .index(
              IndexRequest.of(
                  i -> {
                    try {
                      return i.index(aliasName)
                          .document(getObjectMapper().readValue(data, Map.class))
                          .refresh(Refresh.True);
                    } catch (final JsonProcessingException e) {
                      throw new RuntimeException(e);
                    }
                  }));
    } catch (final Exception e) {
      throw new UpgradeRuntimeException(
          String.format("Could not add data to indexAlias [%s]!", aliasName), e);
    }
  }

  @Override
  public void updateDataByIndexName(
      final IndexMappingCreator<IndexSettings.Builder> indexMapping,
      final DatabaseQueryWrapper queryWrapper,
      final String updateScript,
      final Map<String, Object> parameters) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    LOG.debug(
        "Updating data on [{}] using script [{}] and query [{}].",
        aliasName,
        updateScript,
        queryWrapper.osQuery());

    final Supplier<UpdateByQueryRequest> supplier =
        () ->
            UpdateByQueryRequest.of(
                u ->
                    u.index(aliasName)
                        .refresh(true)
                        .waitForCompletion(false)
                        .query(queryWrapper.osQuery())
                        .script(QueryDSL.script(updateScript, parameters)));
    waitForOrSubmitNewTask(
        "updateBy" + aliasName,
        supplier.get(),
        string -> getPendingUpdateTask(supplier.get()),
        string -> submitUpdateTask(supplier.get()));
  }

  @Override
  public void deleteDataByIndexName(
      final IndexMappingCreator<IndexSettings.Builder> indexMapping,
      final DatabaseQueryWrapper queryWrapper) {
    final Query query = queryWrapper.osQuery();
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    LOG.debug("Deleting data on [{}] with query [{}].", aliasName, query);

    final Supplier<DeleteByQueryRequest> deleteByQueryRequestSupplier =
        () ->
            DeleteByQueryRequest.of(
                d -> d.index(aliasName).waitForCompletion(false).refresh(true).query(query));

    waitForOrSubmitNewTask(
        "deleteBy" + aliasName,
        deleteByQueryRequestSupplier.get(),
        string -> getPendingDeleteTask(deleteByQueryRequestSupplier.get()),
        string -> submitDeleteTask(deleteByQueryRequestSupplier.get()));
  }

  @Override
  public void updateIndex(
      final IndexMappingCreator<IndexSettings.Builder> indexMapping,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases) {
    if (indexMapping.isCreateFromTemplate()) {
      updateIndexTemplateAndAssociatedIndexes(
          indexMapping, mappingScript, parameters, additionalReadAliases);
    } else {
      migrateSingleIndex(indexMapping, mappingScript, parameters, additionalReadAliases);
    }
  }

  public IndexAliases getAllAliasesForIndex(final String indexName) {
    try {
      return databaseClient
          .getOpenSearchClient()
          .indices()
          .getAlias(GetAliasRequest.of(g -> g.index(indexName)))
          .result()
          .getOrDefault(indexName, IndexAliases.of(i -> i.aliases(Map.of())));
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(
          String.format("Could not retrieve existing aliases for {%s}.", indexName), e);
    }
  }

  private <T extends RequestBase> void waitForOrSubmitNewTask(
      final String identifier,
      final T request,
      final Function<T, Optional<Info>> getPendingTaskFunction,
      final Function<T, String> submitNewTaskFunction) {
    String taskId;
    // if the task wasn't completed previously, try to get the pending task to resume waiting for
    final Optional<Info> pendingTask = getPendingTaskFunction.apply(request);
    if (pendingTask.isEmpty()) {
      taskId = submitNewTaskFunction.apply(request);
    } else {
      final String node = pendingTask.get().node();
      taskId = (node != null ? pendingTask.get().node() + ":" : "") + pendingTask.get().id();
      try {
        validateStatusOfPendingTask(taskId);
        LOG.info("Found pending task with id {}, will wait for it to finish.", taskId);
      } catch (final UpgradeRuntimeException ex) {
        LOG.info(
            "Pending task is not completable, submitting new task for identifier {}", identifier);
        taskId = submitNewTaskFunction.apply(request);
      } catch (final IOException e) {
        throw new UpgradeRuntimeException(
            String.format(
                "Could not check status of pending task with id %s for identifier %s",
                taskId, identifier));
      }
    }
    waitUntilTaskIsFinished(taskId, identifier);
  }

  // TODO #12813 the getPending... methods below can be promoted to the parent class with some
  //  mild refactoring
  private Optional<Info> getPendingReindexTask(final ReindexRequest reindexRequest) {
    return getPendingTask(reindexRequest, "indices:data/write/reindex");
  }

  private Optional<Info> getPendingUpdateTask(final UpdateByQueryRequest updateByQueryRequest) {
    return getPendingTask(updateByQueryRequest, "indices:data/write/update/byquery");
  }

  private Optional<Info> getPendingDeleteTask(final DeleteByQueryRequest deleteByQueryRequest) {
    return getPendingTask(deleteByQueryRequest, "indices:data/write/delete/byquery");
  }

  private <T extends RequestBase> Optional<Info> getPendingTask(
      final T request, final String taskAction) {
    try {
      final ListResponse tasksResponse =
          databaseClient.getTaskList(
              ListRequest.of(l -> l.detailed(true).waitForCompletion(false).actions(taskAction)));

      if (tasksResponse.tasks() == null || tasksResponse.tasks().isEmpty()) {
        for (final TaskExecutingNode value : tasksResponse.nodes().values()) {
          if (request instanceof final ReindexRequest reindexRequest) {
            return value.tasks().values().stream()
                .filter(
                    taskInfo ->
                        taskInfo.description() != null
                            && areTaskAndRequestDescriptionsEqual(
                                taskInfo.description(),
                                createReIndexRequestDescription(
                                    reindexRequest.source().index(),
                                    reindexRequest.dest().index())))
                .findAny()
                .map(SchemaUpgradeClientOS::convertStateToInfo);
          }
        }
        LOG.debug("No pending task found for description matching [{}].", request.toString());
        return Optional.empty();
      }
      final String matchingDescription;
      if (request instanceof final ReindexRequest reindexRequest) {
        matchingDescription =
            createReIndexRequestDescription(
                reindexRequest.source().index(), reindexRequest.dest().index());
      } else {
        matchingDescription = request.toString();
      }
      return tasksResponse.tasks().values().stream()
          .filter(
              taskInfo ->
                  taskInfo.description() != null
                      && areTaskAndRequestDescriptionsEqual(
                          taskInfo.description(), matchingDescription))
          .findAny();
    } catch (final Exception e) {
      LOG.warn("Could not get pending task for description matching [{}].", request.toString());
      return Optional.empty();
    }
  }

  private static ReindexRequest.Builder createReindexRequest(
      final String sourceIndexName, final String targetIndexName, final Query query) {
    return new ReindexRequest.Builder()
        .source(s -> s.index(sourceIndexName).query(query))
        .dest(d -> d.index(targetIndexName))
        .refresh(true);
  }

  private String submitReindexTask(final ReindexRequest reindexRequest) {
    try {
      return databaseClient.submitReindexTask(reindexRequest).task();
    } catch (final IOException ex) {
      throw new UpgradeRuntimeException("Could not submit reindex task");
    }
  }

  private String submitUpdateTask(final UpdateByQueryRequest request) {
    try {
      return databaseClient.submitUpdateTask(request).task();
    } catch (final IOException ex) {
      throw new UpgradeRuntimeException("Could not submit update task");
    }
  }

  private String submitDeleteTask(final DeleteByQueryRequest request) {
    try {
      return databaseClient.submitDeleteTask(request).task();
    } catch (final IOException ex) {
      throw new UpgradeRuntimeException("Could not submit delete task");
    }
  }

  // TODO #12813 this method and the methods below can be promoted to the parent class with some
  //  mild refactoring
  private void updateIndexTemplateAndAssociatedIndexes(
      final IndexMappingCreator<IndexSettings.Builder> index,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases) {
    final String indexAlias = getIndexAlias(index);
    final String sourceTemplateName = getSourceIndexOrTemplateName(index, indexAlias);
    // create new template & indices and reindex data to it
    createOrUpdateTemplateWithoutAliases(index);
    final Set<String> indexAliases = getAliases(indexAlias);
    // this ensures the migration happens in a consistent order
    final List<String> sortedIndices =
        indexAliases.stream()
            // we are only interested in indices based on the source template
            // in resumed update scenarios this could also contain indices based on the
            // targetTemplateName already
            // which we don't need to care about
            .filter(indexName -> indexName.contains(sourceTemplateName))
            .sorted()
            .toList();
    for (final String sourceIndex : sortedIndices) {
      final String suffix;
      final Matcher suffixMatcher = indexSuffixPattern.matcher(sourceIndex);
      if (suffixMatcher.find()) {
        // sourceIndex is already suffixed
        suffix = sourceIndex.substring(sourceIndex.lastIndexOf("-"));
      } else {
        // sourceIndex is not yet suffixed, use default suffix
        suffix = index.getIndexNameInitialSuffix();
      }

      final String targetIndexName =
          getIndexNameService().getOptimizeIndexTemplateNameWithVersion(index) + suffix;

      final IndexAliases existingAliases = getAllAliasesForIndex(sourceIndex);
      setAllAliasesToReadOnly(sourceIndex, existingAliases);
      createIndexFromTemplate(targetIndexName);
      reindex(sourceIndex, targetIndexName, mappingScript, parameters);
      applyAliasesToIndex(targetIndexName, existingAliases);
      applyAdditionalReadOnlyAliasesToIndex(additionalReadAliases, targetIndexName);
      // for rolled over indices only the last one is eligible as writeIndex
      if (sortedIndices.indexOf(sourceIndex) == sortedIndices.size() - 1) {
        // in case of retries it might happen that the default write index flag is overwritten as
        // the source index
        // was already set to be a read-only index for all associated indices
        addAlias(indexAlias, targetIndexName, true);
      }
      deleteIndexIfExists(sourceIndex);
      deleteTemplateIfExists(sourceTemplateName);
    }
  }

  private void migrateSingleIndex(
      final IndexMappingCreator<IndexSettings.Builder> index,
      final String mappingScript,
      final Map<String, Object> parameters,
      final Set<String> additionalReadAliases) {
    final String indexAlias = getIndexAlias(index);
    final String sourceIndexName = getSourceIndexOrTemplateName(index, indexAlias);
    final String targetIndexName = getIndexNameService().getOptimizeIndexNameWithVersion(index);
    if (!indexExists(sourceIndexName)) {
      // if the expected source index is not available anymore there are only two possibilities:
      // 1. it never existed (unexpected edge-case)
      // 2. a previous upgrade run completed this step already
      // in both cases we can try to create/update the target index in a fail-safe way
      LOG.info(
          "Source index {} was not found, will just create/update the new index {}.",
          sourceIndexName,
          targetIndexName);
      createOrUpdateIndex(index);
    } else {
      // create new index and reindex data to it
      final IndexAliases existingAliases = getAllAliasesForIndex(sourceIndexName);
      setAllAliasesToReadOnly(sourceIndexName, existingAliases);
      createOrUpdateIndex(index);
      reindex(sourceIndexName, targetIndexName, mappingScript, parameters);
      applyAliasesToIndex(targetIndexName, existingAliases);
      applyAdditionalReadOnlyAliasesToIndex(additionalReadAliases, targetIndexName);
      // in case of retries it might happen that the default write index flag is overwritten as the
      // source index
      // was already set to be a read-only index for all associated indices
      addAlias(indexAlias, targetIndexName, true);
      deleteIndexIfExists(sourceIndexName);
    }
  }

  private void applyAliasesToIndex(final String indexName, final IndexAliases aliases) {
    for (final Map.Entry<String, AliasDefinition> alias : aliases.aliases().entrySet()) {
      addAlias(
          alias.getKey(),
          indexName,
          // defaulting to true if this flag is not set but only one index exists
          Optional.ofNullable(alias.getValue().isWriteIndex())
              .orElse(aliases.aliases().size() == 1));
    }
  }

  private void setAllAliasesToReadOnly(final String indexName, final IndexAliases aliases) {
    LOG.debug("Setting all aliases pointing to {} to readonly.", indexName);

    final List<String> list = aliases.aliases().keySet().stream().toList();
    try {
      if (!list.isEmpty()) {
        final UpdateAliasesRequest indicesAliasesRequest =
            UpdateAliasesRequest.of(
                u ->
                    u.actions(Action.of(a -> a.remove(r -> r.index(indexName).aliases(list))))
                        .actions(
                            Action.of(
                                a ->
                                    a.add(
                                        q ->
                                            q.index(indexName)
                                                .isWriteIndex(false)
                                                .aliases(list)))));
        databaseClient.getOpenSearchClient().indices().updateAliases(indicesAliasesRequest);
      }
    } catch (final Exception e) {
      final String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  private static Info convertStateToInfo(final State state) {
    if (state == null) {
      return null;
    }

    return new Info.Builder()
        .action(state.action())
        .cancellable(state.cancellable())
        .description(state.description())
        .headers(state.headers())
        .id(state.id())
        .node(state.node())
        .runningTimeInNanos(state.runningTimeInNanos())
        .startTimeInMillis(state.startTimeInMillis())
        .status(state.status())
        .type(state.type())
        .parentTaskId(state.parentTaskId())
        .build();
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
