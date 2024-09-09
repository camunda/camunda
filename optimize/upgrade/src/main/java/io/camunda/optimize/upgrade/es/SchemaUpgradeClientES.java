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
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksResponse;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.tasks.TaskInfo;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;

@Getter
@Slf4j
public class SchemaUpgradeClientES
    extends SchemaUpgradeClient<OptimizeElasticsearchClient, XContentBuilder> {
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
      final IndexMappingCreator<XContentBuilder> sourceIndex,
      final IndexMappingCreator<XContentBuilder> targetIndex,
      final DatabaseQueryWrapper queryWrapper,
      final String mappingScript) {
    final AbstractQueryBuilder<?> sourceDocumentFilterQuery = queryWrapper.esQuery();
    final ReindexRequest reindexRequest =
        createReindexRequest(
            getIndexNameService().getOptimizeIndexNameWithVersion(sourceIndex),
            getIndexNameService().getOptimizeIndexNameWithVersion(targetIndex));
    reindexRequest.setSourceQuery(sourceDocumentFilterQuery);

    if (mappingScript != null) {
      reindexRequest.setScript(createDefaultScript(mappingScript));
    }

    String reindexTaskId = submitReindexTask(reindexRequest);
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
      final ReindexRequest reindexRequest = createReindexRequest(sourceIndex, targetIndex);
      if (mappingScript != null) {
        reindexRequest.setScript(
            createDefaultScriptWithSpecificDtoParams(mappingScript, parameters, objectMapper));
      }
      waitForOrSubmitNewTask(
          "reindex from " + sourceIndex + " to " + targetIndex,
          reindexRequest,
          String -> getPendingReindexTask(reindexRequest),
          String -> submitReindexTask(reindexRequest));
    }
  }

  @Override
  public <T> void upsert(final String index, final String id, final T documentDto) {
    try {
      databaseClient.update(
          new UpdateRequest(index, id)
              .doc(objectMapper.writeValueAsString(documentDto), XContentType.JSON)
              .docAsUpsert(true));
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
      final GetResponse getResponse = databaseClient.get(new GetRequest(index, id));
      return getResponse.isSourceEmpty()
          ? Optional.empty()
          : Optional.ofNullable(
              objectMapper.readValue(getResponse.getSourceAsString(), resultType));
    } catch (Exception e) {
      final String message =
          String.format("Could not get document with id %s from index %s.", id, index);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public List<UpgradeStepLogEntryDto> getAppliedUpdateStepsForTargetVersion(
      final String targetOptimizeVersion) {
    SearchResponse searchResponse;
    try {
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(boolQuery().must(termQuery(Fields.optimizeVersion, targetOptimizeVersion)))
              .size(LIST_FETCH_LIMIT);
      searchResponse =
          databaseClient.search(
              new SearchRequest(UPDATE_LOG_ENTRY_INDEX_NAME).source(searchSourceBuilder));
    } catch (IOException e) {
      String reason =
          String.format(
              "Was not able to fetch completed update steps for target version %s",
              targetOptimizeVersion);
      log.error(reason, e);
      throw new UpgradeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), UpgradeStepLogEntryDto.class, objectMapper);
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
    CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexNameWithSuffix);
    try {
      databaseClient.createIndex(createIndexRequest);
    } catch (ElasticsearchStatusException e) {
      if (e.status() == RestStatus.BAD_REQUEST
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
      final IndexMappingCreator<XContentBuilder> indexMapping,
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
      final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
      final AliasActions aliasAction =
          new AliasActions(AliasActions.Type.ADD)
              .index(completeIndexName)
              .writeIndex(isWriteAlias)
              .aliases(indexAliases.toArray(new String[0]));
      indicesAliasesRequest.addAliasAction(aliasAction);
      getHighLevelRestClient()
          .indices()
          .updateAliases(indicesAliasesRequest, databaseClient.requestOptions());
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", completeIndexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateDataByIndexName(
      IndexMappingCreator<XContentBuilder> indexMapping,
      DatabaseQueryWrapper queryWrapper,
      String updateScript,
      Map<String, Object> parameters) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug(
        "Updating data on [{}] using script [{}] and query [{}].",
        aliasName,
        updateScript,
        queryWrapper.esQuery());
    final UpdateByQueryRequest request = new UpdateByQueryRequest(aliasName);
    request.setRefresh(true);
    request.setQuery(queryWrapper.esQuery());
    request.setScript(
        createDefaultScriptWithSpecificDtoParams(updateScript, parameters, objectMapper));

    waitForOrSubmitNewTask(
        "updateBy" + aliasName,
        request,
        String -> getPendingUpdateTask(request),
        String -> submitUpdateTask(request));
  }

  public Map<String, Set<AliasMetadata>> getAliasMap(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return getHighLevelRestClient()
          .indices()
          .getAlias(aliasesRequest, databaseClient.requestOptions())
          .getAliases();
    } catch (Exception e) {
      String message = String.format("Could not retrieve alias map for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void insertDataByIndexName(final IndexMappingCreator indexMapping, final String data) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug("Inserting data to indexAlias [{}]. Data payload is [{}]", aliasName, data);
    try {
      final IndexRequest indexRequest = new IndexRequest(aliasName);
      indexRequest.source(data, XContentType.JSON);
      indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      getHighLevelRestClient().index(indexRequest, databaseClient.requestOptions());
    } catch (Exception e) {
      String errorMessage = String.format("Could not add data to indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteDataByIndexName(
      final IndexMappingCreator<XContentBuilder> indexMapping,
      final DatabaseQueryWrapper queryWrapper) {
    final AbstractQueryBuilder<?> query = queryWrapper.esQuery();
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug("Deleting data on [{}] with query [{}].", aliasName, query);

    final DeleteByQueryRequest request = new DeleteByQueryRequest(aliasName);
    request.setRefresh(true);
    request.setQuery(query);

    waitForOrSubmitNewTask(
        "deleteBy" + aliasName,
        request,
        String -> getPendingDeleteTask(request),
        String -> submitDeleteTask(request));
  }

  @Override
  public void updateIndexDynamicSettingsAndMappings(final IndexMappingCreator indexMapping) {
    schemaManager.updateDynamicSettingsAndMappings(databaseClient, indexMapping);
  }

  public Set<AliasMetadata> getAllAliasesForIndex(final String indexName) {
    GetAliasesRequest getAliasesRequest = new GetAliasesRequest().indices(indexName);
    try {
      return new HashSet<>(
          getHighLevelRestClient()
              .indices()
              .getAlias(getAliasesRequest, databaseClient.requestOptions())
              .getAliases()
              .getOrDefault(indexName, Sets.newHashSet()));
    } catch (Exception e) {
      String message = String.format("Could not retrieve existing aliases for {%s}.", indexName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private RestHighLevelClient getHighLevelRestClient() {
    return databaseClient.getHighLevelClient();
  }

  private boolean areDocCountsEqual(final String sourceIndex, final String targetIndex) {
    try {
      final long sourceIndexDocCount =
          databaseClient.countWithoutPrefix(new CountRequest(sourceIndex));
      final long targetIndexDocCount =
          databaseClient.countWithoutPrefix(new CountRequest(targetIndex));
      return sourceIndexDocCount == targetIndexDocCount;
    } catch (Exception e) {
      final String errorMessage =
          String.format(
              "Could not compare doc counts of index [%s] and [%s].", sourceIndex, targetIndex);
      log.warn(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private <T extends AbstractBulkByScrollRequest<T>> void waitForOrSubmitNewTask(
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
      taskId = pendingTask.get().getTaskId().toString();
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

  private <T extends AbstractBulkByScrollRequest<T>> Optional<TaskInfo> getPendingTask(
      final T request, final String taskAction) {
    try {
      final ListTasksResponse tasksResponse =
          databaseClient.getTaskList(
              new ListTasksRequest().setDetailed(true).setActions(taskAction));
      return tasksResponse.getTasks().stream()
          .filter(
              taskInfo ->
                  taskInfo.getDescription() != null
                      && areTaskAndRequestDescriptionsEqual(
                          taskInfo.getDescription(), request.getDescription()))
          .findAny();
    } catch (Exception e) {
      log.warn(
          "Could not get pending task for description matching [{}].", request.getDescription());
      return Optional.empty();
    }
  }

  private static ReindexRequest createReindexRequest(
      final String sourceIndexName, final String targetIndexName) {
    return new ReindexRequest()
        .setSourceIndices(sourceIndexName)
        .setDestIndex(targetIndexName)
        .setRefresh(true);
  }

  private String submitReindexTask(final ReindexRequest reindexRequest) {
    try {
      return databaseClient.submitReindexTask(reindexRequest).getTask();
    } catch (IOException ex) {
      throw new UpgradeRuntimeException("Could not submit reindex task");
    }
  }

  private String submitUpdateTask(final UpdateByQueryRequest request) {
    try {
      return databaseClient.submitUpdateTask(request).getTask();
    } catch (IOException ex) {
      throw new UpgradeRuntimeException("Could not submit update task");
    }
  }

  private String submitDeleteTask(final DeleteByQueryRequest request) {
    try {
      return databaseClient.submitDeleteTask(request).getTask();
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
      final IndexMappingCreator<XContentBuilder> index,
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

      final Set<AliasMetadata> existingAliases = getAllAliasesForIndex(sourceIndex);
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
      final IndexMappingCreator<XContentBuilder> index,
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
      final Set<AliasMetadata> existingAliases = getAllAliasesForIndex(sourceIndexName);
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

  private String getIndexAlias(final IndexMappingCreator<XContentBuilder> index) {
    return getIndexNameService().getOptimizeIndexAliasForIndex(index.getIndexName());
  }

  private String getSourceIndexOrTemplateName(
      final IndexMappingCreator<XContentBuilder> index, final String indexAlias) {
    return getOptimizeIndexOrTemplateNameForAliasAndVersion(
        indexAlias, String.valueOf(index.getVersion() - 1));
  }

  private void applyAliasesToIndex(final String indexName, final Set<AliasMetadata> aliases) {
    for (AliasMetadata alias : aliases) {
      addAlias(
          alias.getAlias(),
          indexName,
          // defaulting to true if this flag is not set but only one index exists
          Optional.ofNullable(alias.writeIndex()).orElse(aliases.size() == 1));
    }
  }

  private void applyAdditionalReadOnlyAliasesToIndex(
      final Set<String> additionalReadAliases, final String indexName) {
    for (String alias : additionalReadAliases) {
      addAlias(getIndexNameService().getOptimizeIndexAliasForIndex(alias), indexName, false);
    }
  }

  private void setAllAliasesToReadOnly(final String indexName, final Set<AliasMetadata> aliases) {
    log.debug("Setting all aliases pointing to {} to readonly.", indexName);

    final String[] aliasNames = aliases.stream().map(AliasMetadata::alias).toArray(String[]::new);
    try {
      final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
      final AliasActions removeAllAliasesAction =
          new AliasActions(AliasActions.Type.REMOVE).index(indexName).aliases(aliasNames);
      final AliasActions addReadOnlyAliasAction =
          new AliasActions(AliasActions.Type.ADD)
              .index(indexName)
              .writeIndex(false)
              .aliases(aliasNames);
      indicesAliasesRequest.addAliasAction(removeAllAliasesAction);
      indicesAliasesRequest.addAliasAction(addReadOnlyAliasAction);

      getHighLevelRestClient()
          .indices()
          .updateAliases(indicesAliasesRequest, databaseClient.requestOptions());
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }
}
