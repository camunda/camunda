/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.es;

import static io.camunda.optimize.service.db.DatabaseConstants.ELASTICSEARCH_TASK_DESCRIPTION_DOC_SUFFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_ALREADY_EXISTS_EXCEPTION_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.getTaskResponse;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.validateTaskResponse;
import static io.camunda.optimize.service.db.schema.OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.RequestBase;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.indices.AliasDefinition;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import co.elastic.clients.elasticsearch.tasks.ListRequest;
import co.elastic.clients.elasticsearch.tasks.ListResponse;
import co.elastic.clients.elasticsearch.tasks.NodeTasks;
import co.elastic.clients.elasticsearch.tasks.TaskInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto.Fields;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class SchemaUpgradeClientES
    extends SchemaUpgradeClient<OptimizeElasticsearchClient, IndexSettings.Builder> {
  private final ObjectMapper objectMapper;

  public SchemaUpgradeClientES(
      final ElasticSearchSchemaManager schemaManager,
      final ElasticSearchMetadataService metadataService,
      final OptimizeElasticsearchClient elasticsearchClient,
      final ObjectMapper objectMapper) {
    super(schemaManager, metadataService, DatabaseType.ELASTICSEARCH, elasticsearchClient);
    this.objectMapper = objectMapper;
  }

  @Override
  public void reindex(final String sourceIndex, final String targetIndex) {
    reindex(sourceIndex, targetIndex, null, Collections.emptyMap());
  }

  @Override
  public void reindex(
      final IndexMappingCreator<IndexSettings.Builder> sourceIndex,
      final IndexMappingCreator<IndexSettings.Builder> targetIndex,
      final DatabaseQueryWrapper queryWrapper,
      final String mappingScript) {
    final Query sourceDocumentFilterQuery = queryWrapper.esQuery();
    final ReindexRequest.Builder reindexRequestBuilder =
        createReindexRequest(
            getIndexNameService().getOptimizeIndexNameWithVersion(sourceIndex),
            getIndexNameService().getOptimizeIndexNameWithVersion(targetIndex),
            sourceDocumentFilterQuery);

    if (mappingScript != null) {
      reindexRequestBuilder.script(createDefaultScript(mappingScript));
    }

    final String reindexTaskId = submitReindexTask(reindexRequestBuilder.build());
    waitUntilTaskIsFinished(reindexTaskId, targetIndex.getIndexName());
  }

  @Override
  public void reindex(
      final String sourceIndex,
      final String targetIndex,
      final String mappingScript,
      final Map<String, Object> parameters) {
    log.debug(
        "Reindexing from index [{}] to [{}] using the mapping script [{}].",
        sourceIndex,
        targetIndex,
        mappingScript);
    if (areDocCountsEqual(sourceIndex, targetIndex)) {
      log.info(
          "Found that index [{}] already contains the same amount of documents as [{}], will skip reindex.",
          targetIndex,
          sourceIndex);
    } else {
      final Supplier<ReindexRequest> reindexRequestSupplier =
          () -> {
            final ReindexRequest.Builder reindexRequestBuilder =
                createReindexRequest(sourceIndex, targetIndex, null).waitForCompletion(false);
            if (mappingScript != null) {
              reindexRequestBuilder.script(
                  createDefaultScriptWithSpecificDtoParams(mappingScript, parameters));
            }
            return reindexRequestBuilder.build();
          };
      waitForOrSubmitNewTask(
          "reindex from " + sourceIndex + " to " + targetIndex,
          reindexRequestSupplier.get(),
          String -> getPendingReindexTask(reindexRequestSupplier.get()),
          String -> submitReindexTask(reindexRequestSupplier.get()));
    }
  }

  @Override
  public <T> void upsert(final String index, final String id, final T documentDto) {
    try {
      databaseClient.update(
          OptimizeUpdateRequestBuilderES.of(
              u ->
                  u.optimizeIndex(databaseClient, index).id(id).doc(documentDto).docAsUpsert(true)),
          Object.class);
    } catch (Exception e) {
      final String message =
          String.format("Could not upsert document with id %s to index %s.", id, index);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public <T> Optional<T> getDocumentByIdAs(
      final String index, final String id, final Class<T> resultType) {
    try {
      final GetResponse<T> getResponse =
          databaseClient.get(GetRequest.of(g -> g.index(index).id(id)), resultType);
      return getResponse.found() ? Optional.empty() : Optional.ofNullable(getResponse.source());
    } catch (Exception e) {
      final String message =
          String.format("Could not get document with id %s from index %s.", id, index);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public List<UpgradeStepLogEntryDto> getAppliedUpdateStepsForTargetVersion(
      final String targetOptimizeVersion) {
    SearchResponse<UpgradeStepLogEntryDto> searchResponse;
    try {
      searchResponse =
          databaseClient.search(
              OptimizeSearchRequestBuilderES.of(
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
                                                                  .value(targetOptimizeVersion))))))
                          .size(LIST_FETCH_LIMIT)),
              UpgradeStepLogEntryDto.class);
    } catch (IOException e) {
      String reason =
          String.format(
              "Was not able to fetch completed update steps for target version %s",
              targetOptimizeVersion);
      log.error(reason, e);
      throw new UpgradeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), UpgradeStepLogEntryDto.class, objectMapper);
  }

  @Override
  public boolean indexExists(final String indexName) {
    log.debug("Checking if index exists [{}].", indexName);
    try {
      return databaseClient.exists(indexName);
    } catch (Exception e) {
      String errorMessage =
          String.format("Could not validate whether index [%s] exists!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteIndexIfExists(final String indexName) {
    if (indexExists(indexName)) {
      try {
        databaseClient.deleteIndexByRawIndexNames(indexName);
      } catch (Exception e) {
        String errorMessage = String.format("Could not delete index [%s]!", indexName);
        throw new UpgradeRuntimeException(errorMessage, e);
      }
    }
  }

  @Override
  public boolean indexTemplateExists(final String indexTemplateName) {
    log.debug("Checking if index template exists [{}].", indexTemplateName);
    try {
      return databaseClient.templateExists(indexTemplateName);
    } catch (Exception e) {
      String errorMessage =
          String.format(
              "Could not validate whether index template [%s] exists!", indexTemplateName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteTemplateIfExists(final String indexTemplateName) {
    if (indexTemplateExists(indexTemplateName)) {
      try {
        log.debug("Deleting index template [{}]", indexTemplateName);
        databaseClient.deleteIndexTemplateByIndexTemplateName(indexTemplateName);
      } catch (Exception e) {
        String errorMessage =
            String.format("Could not delete index template [%s]!", indexTemplateName);
        throw new UpgradeRuntimeException(errorMessage, e);
      }
    } else {
      log.debug(
          "Index template [{}] does not exist and will therefore not be deleted.",
          indexTemplateName);
    }
  }

  @Override
  public void createIndexFromTemplate(final String indexNameWithSuffix) {
    CreateIndexRequest createIndexRequest =
        CreateIndexRequest.of(c -> c.index(indexNameWithSuffix));
    try {
      databaseClient.createIndex(createIndexRequest);
    } catch (ElasticsearchException e) {
      if (e.status() == BAD_REQUEST.code()
          && e.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_TYPE)) {
        log.debug("Index {} from template already exists.", indexNameWithSuffix);
      } else {
        throw e;
      }
    } catch (Exception e) {
      String message =
          String.format("Could not create index %s from template.", indexNameWithSuffix);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void addAlias(
      final String indexAlias, final String completeIndexName, final boolean isWriteAlias) {
    addAliases(Collections.singleton(indexAlias), completeIndexName, isWriteAlias);
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

  @Override
  public void addAliases(
      final Set<String> indexAliases, final String completeIndexName, final boolean isWriteAlias) {
    log.debug("Adding aliases [{}] to index [{}].", indexAliases, completeIndexName);

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
      getElasticsearchClient().indices().updateAliases(indicesAliasesRequest);
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", completeIndexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateDataByIndexName(
      IndexMappingCreator<IndexSettings.Builder> indexMapping,
      DatabaseQueryWrapper queryWrapper,
      String updateScript,
      Map<String, Object> parameters) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug(
        "Updating data on [{}] using script [{}] and query [{}].",
        aliasName,
        updateScript,
        queryWrapper.esQuery());

    Supplier<UpdateByQueryRequest> updateByQueryRequestSupplier =
        () ->
            UpdateByQueryRequest.of(
                u ->
                    u.index(aliasName)
                        .refresh(true)
                        .waitForCompletion(false)
                        .query(queryWrapper.esQuery())
                        .script(
                            createDefaultScriptWithSpecificDtoParams(updateScript, parameters)));
    waitForOrSubmitNewTask(
        "updateBy" + aliasName,
        updateByQueryRequestSupplier.get(),
        String -> getPendingUpdateTask(updateByQueryRequestSupplier.get()),
        String -> submitUpdateTask(updateByQueryRequestSupplier.get()));
  }

  @Override
  public void insertDataByIndexName(final IndexMappingCreator indexMapping, final String data) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug("Inserting data to indexAlias [{}]. Data payload is [{}]", aliasName, data);
    try {
      getElasticsearchClient()
          .index(
              IndexRequest.of(
                  i -> {
                    try {
                      return i.index(aliasName)
                          .document(getObjectMapper().readValue(data, Map.class))
                          .refresh(Refresh.True);
                    } catch (JsonProcessingException e) {
                      throw new RuntimeException(e);
                    }
                  }));
    } catch (Exception e) {
      String errorMessage = String.format("Could not add data to indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteDataByIndexName(
      final IndexMappingCreator<IndexSettings.Builder> indexMapping,
      final DatabaseQueryWrapper queryWrapper) {
    final Query query = queryWrapper.esQuery();
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug("Deleting data on [{}] with query [{}].", aliasName, query);

    final Supplier<DeleteByQueryRequest> deleteByQueryRequestSupplier =
        () ->
            DeleteByQueryRequest.of(
                d -> d.index(aliasName).waitForCompletion(false).refresh(true).query(query));

    waitForOrSubmitNewTask(
        "deleteBy" + aliasName,
        deleteByQueryRequestSupplier.get(),
        String -> getPendingDeleteTask(deleteByQueryRequestSupplier.get()),
        String -> submitDeleteTask(deleteByQueryRequestSupplier.get()));
  }

  @Override
  public void updateIndexDynamicSettingsAndMappings(final IndexMappingCreator indexMapping) {
    schemaManager.updateDynamicSettingsAndMappings(databaseClient, indexMapping);
  }

  public IndexAliases getAllAliasesForIndex(final String indexName) {
    final GetAliasRequest getAliasesRequest = GetAliasRequest.of(g -> g.index(indexName));
    try {
      return getElasticsearchClient()
          .indices()
          .getAlias(getAliasesRequest)
          .result()
          .getOrDefault(indexName, IndexAliases.of(i -> i.aliases(Map.of())));
    } catch (Exception e) {
      String message = String.format("Could not retrieve existing aliases for {%s}.", indexName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private ElasticsearchClient getElasticsearchClient() {
    return databaseClient.esWithTransportOptions();
  }

  private boolean areDocCountsEqual(final String sourceIndex, final String targetIndex) {
    try {
      final long sourceIndexDocCount =
          databaseClient.countWithoutPrefix(CountRequest.of(c -> c.index(sourceIndex)));
      final long targetIndexDocCount =
          databaseClient.countWithoutPrefix(CountRequest.of(c -> c.index(targetIndex)));
      return sourceIndexDocCount == targetIndexDocCount;
    } catch (Exception e) {
      final String errorMessage =
          String.format(
              "Could not compare doc counts of index [%s] and [%s].", sourceIndex, targetIndex);
      log.warn(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private <T extends RequestBase> void waitForOrSubmitNewTask(
      final String identifier,
      final T request,
      final Function<T, Optional<TaskInfo>> getPendingTaskFunction,
      final Function<T, String> submitNewTaskFunction) {
    String taskId;
    // if the task wasn't completed previously, try to get the pending task to resume waiting for
    final Optional<TaskInfo> pendingTask = getPendingTaskFunction.apply(request);
    if (pendingTask.isEmpty()) {
      taskId = submitNewTaskFunction.apply(request);
    } else {
      final String node = pendingTask.get().node();
      taskId = (node != null ? pendingTask.get().node() + ":" : "") + pendingTask.get().id();
      try {
        validateStatusOfPendingTask(taskId);
        log.info("Found pending task with id {}, will wait for it to finish.", taskId);
      } catch (UpgradeRuntimeException ex) {
        log.info(
            "Pending task is not completable, submitting new task for identifier {}", identifier);
        taskId = submitNewTaskFunction.apply(request);
      } catch (IOException e) {
        throw new UpgradeRuntimeException(
            String.format(
                "Could not check status of pending task with id %s for identifier %s",
                taskId, identifier));
      }
    }
    waitUntilTaskIsFinished(taskId, identifier);
  }

  private Optional<TaskInfo> getPendingReindexTask(final ReindexRequest reindexRequest) {
    return getPendingTask(reindexRequest, "indices:data/write/reindex");
  }

  private Optional<TaskInfo> getPendingUpdateTask(final UpdateByQueryRequest updateByQueryRequest) {
    return getPendingTask(updateByQueryRequest, "indices:data/write/update/byquery");
  }

  private Optional<TaskInfo> getPendingDeleteTask(final DeleteByQueryRequest deleteByQueryRequest) {
    return getPendingTask(deleteByQueryRequest, "indices:data/write/delete/byquery");
  }

  private <T extends RequestBase> Optional<TaskInfo> getPendingTask(
      final T request, final String taskAction) {
    try {
      final ListResponse tasksResponse =
          databaseClient.getTaskList(
              ListRequest.of(l -> l.detailed(true).waitForCompletion(false).actions(taskAction)));

      if (tasksResponse.tasks() == null) {
        for (NodeTasks value : tasksResponse.nodes().values()) {
          if (request instanceof ReindexRequest reindexRequest) {
            return value.tasks().values().stream()
                .filter(
                    taskInfo ->
                        taskInfo.description() != null
                            && areTaskAndRequestDescriptionsEqual(
                                taskInfo.description(),
                                "reindex from "
                                    + reindexRequest.source().index()
                                    + " to ["
                                    + reindexRequest.dest().index()
                                    + "]"))
                .findAny();
          }
        }
      }
      return tasksResponse.tasks().flat().stream()
          .filter(
              taskInfo ->
                  taskInfo.description() != null
                      && areTaskAndRequestDescriptionsEqual(
                          taskInfo.description(), request.toString()))
          .findAny();
    } catch (Exception e) {
      log.warn("Could not get pending task for description matching [{}].", request.toString());
      return Optional.empty();
    }
  }

  private static ReindexRequest.Builder createReindexRequest(
      final String sourceIndexName, final String targetIndexName, final Query query) {
    final ReindexRequest.Builder builder = new ReindexRequest.Builder();
    builder
        .source(s -> s.index(sourceIndexName).query(query))
        .dest(d -> d.index(targetIndexName))
        .refresh(true);
    return builder;
  }

  private String submitReindexTask(final ReindexRequest reindexRequest) {
    try {
      return databaseClient.submitReindexTask(reindexRequest).task();
    } catch (IOException ex) {
      throw new UpgradeRuntimeException("Could not submit reindex task");
    }
  }

  private String submitUpdateTask(final UpdateByQueryRequest request) {
    try {
      return databaseClient.submitUpdateTask(request).task();
    } catch (IOException ex) {
      throw new UpgradeRuntimeException("Could not submit update task");
    }
  }

  private String submitDeleteTask(final DeleteByQueryRequest request) {
    try {
      return databaseClient.submitDeleteTask(request).task();
    } catch (IOException ex) {
      throw new UpgradeRuntimeException("Could not submit delete task");
    }
  }

  private void waitUntilTaskIsFinished(final String taskId, final String taskIdentifier) {
    try {
      ElasticsearchWriterUtil.waitUntilTaskIsFinished(databaseClient, taskId, taskIdentifier);
    } catch (OptimizeRuntimeException e) {
      throw new UpgradeRuntimeException(e.getCause().getMessage(), e);
    }
  }

  private void validateStatusOfPendingTask(final String reindexTaskId)
      throws UpgradeRuntimeException, IOException {
    try {
      validateTaskResponse(getTaskResponse(databaseClient, reindexTaskId));
    } catch (OptimizeRuntimeException ex) {
      throw new UpgradeRuntimeException(
          String.format(
              "Found pending task with id %s, but it is not in a completable state", reindexTaskId),
          ex);
    }
  }

  private boolean areTaskAndRequestDescriptionsEqual(
      String taskDescription, String requestDescription) {
    return getDescriptionStringWithoutSuffix(taskDescription)
        .equals(getDescriptionStringWithoutSuffix(requestDescription));
  }

  private String getDescriptionStringWithoutSuffix(final String descriptionString) {
    if (descriptionString.endsWith(ELASTICSEARCH_TASK_DESCRIPTION_DOC_SUFFIX)) {
      return descriptionString.substring(
          0, descriptionString.length() - ELASTICSEARCH_TASK_DESCRIPTION_DOC_SUFFIX.length());
    }
    return descriptionString;
  }

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
      log.info(
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

  private String getIndexAlias(final IndexMappingCreator<IndexSettings.Builder> index) {
    return getIndexNameService().getOptimizeIndexAliasForIndex(index.getIndexName());
  }

  private String getSourceIndexOrTemplateName(
      final IndexMappingCreator<IndexSettings.Builder> index, final String indexAlias) {
    return getOptimizeIndexOrTemplateNameForAliasAndVersion(
        indexAlias, String.valueOf(index.getVersion() - 1));
  }

  private void applyAliasesToIndex(final String indexName, final IndexAliases aliases) {
    for (Map.Entry<String, AliasDefinition> alias : aliases.aliases().entrySet()) {
      addAlias(
          alias.getKey(),
          indexName,
          // defaulting to true if this flag is not set but only one index exists
          Optional.ofNullable(alias.getValue().isWriteIndex())
              .orElse(aliases.aliases().size() == 1));
    }
  }

  private void applyAdditionalReadOnlyAliasesToIndex(
      final Set<String> additionalReadAliases, final String indexName) {
    for (String alias : additionalReadAliases) {
      addAlias(getIndexNameService().getOptimizeIndexAliasForIndex(alias), indexName, false);
    }
  }

  private void setAllAliasesToReadOnly(final String indexName, final IndexAliases aliases) {
    log.debug("Setting all aliases pointing to {} to readonly.", indexName);

    final List<String> list = aliases.aliases().keySet().stream().toList();
    try {
      final UpdateAliasesRequest indicesAliasesRequest =
          UpdateAliasesRequest.of(
              u ->
                  u.actions(Action.of(a -> a.remove(r -> r.index(indexName).aliases(list))))
                      .actions(
                          Action.of(
                              a ->
                                  a.add(
                                      q -> q.index(indexName).isWriteIndex(false).aliases(list)))));
      getElasticsearchClient().indices().updateAliases(indicesAliasesRequest);
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }
}
