/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksResponse;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.TaskInfo;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_ALREADY_EXISTS_EXCEPTION_TYPE;

@Slf4j
public class SchemaUpgradeClient {
  private final ElasticSearchSchemaManager schemaManager;
  private final ElasticsearchMetadataService metadataService;
  private final OptimizeElasticsearchClient elasticsearchClient;
  @Getter
  private final ObjectMapper objectMapper;

  public SchemaUpgradeClient(final ElasticSearchSchemaManager schemaManager,
                             final ElasticsearchMetadataService metadataService,
                             final OptimizeElasticsearchClient elasticsearchClient,
                             final ObjectMapper objectMapper) {
    this.schemaManager = schemaManager;
    this.metadataService = metadataService;
    this.elasticsearchClient = elasticsearchClient;
    this.objectMapper = objectMapper;
  }

  public Optional<String> getSchemaVersion() {
    return metadataService.getSchemaVersion(elasticsearchClient);
  }

  public void reindex(final String sourceIndex,
                      final String targetIndex) {
    this.reindex(sourceIndex, targetIndex, null, Collections.emptyMap());
  }

  public void reindex(final IndexMappingCreator sourceIndex,
                      final IndexMappingCreator targetIndex,
                      final QueryBuilder sourceDocumentFilterQuery,
                      final String mappingScript) {
    final ReindexRequest reindexRequest = new ReindexRequest()
      .setSourceIndices(getIndexNameService().getOptimizeIndexNameWithVersion(sourceIndex))
      .setDestIndex(getIndexNameService().getOptimizeIndexNameWithVersion(targetIndex))
      .setSourceQuery(sourceDocumentFilterQuery)
      .setRefresh(true);

    if (mappingScript != null) {
      reindexRequest.setScript(createDefaultScript(mappingScript));
    }

    String reindexTaskId;

    try {
      reindexTaskId = submitReindexTask(reindexRequest);
    } catch (IOException e) {
      throw new UpgradeRuntimeException(
        String.format(
          "Error while trying to reindex data from index [%s] to [%s] with filterQuery.",
          sourceIndex,
          targetIndex
        ),
        e
      );
    }

    waitUntilTaskIsFinished(reindexTaskId, targetIndex.getIndexName());
  }

  public void reindex(final String sourceIndex,
                      final String targetIndex,
                      final String mappingScript,
                      final Map<String, Object> parameters) {
    log.debug(
      "Reindexing from index [{}] to [{}] using the mapping script [{}].", sourceIndex, targetIndex, mappingScript
    );

    if (areDocCountsEqual(sourceIndex, targetIndex)) {
      log.info(
        "Found that index [{}] already contains the same amount of documents as [{}], will skip reindex.",
        targetIndex, sourceIndex
      );
    } else {
      String reindexTaskId;
      // if the reindex wasn't completed previously, try to get the pending task to resume waiting for
      final Optional<TaskInfo> pendingReindexTask = getPendingReindexTask(sourceIndex, targetIndex);
      if (pendingReindexTask.isPresent()) {
        reindexTaskId = pendingReindexTask.get().getTaskId().toString();
        log.info(
          "Found pending reindex task with id [{}] from index [{}] to [{}], will wait for it to finish.",
          reindexTaskId, sourceIndex, targetIndex
        );
      } else {
        reindexTaskId = submitReindexTask(sourceIndex, targetIndex, mappingScript, parameters);
      }
      waitUntilTaskIsFinished(reindexTaskId, targetIndex);
    }
  }

  public <T> void upsert(final String index, final String id, final T documentDto) {
    try {
      elasticsearchClient.update(
        new UpdateRequest(index, id)
          .doc(objectMapper.writeValueAsString(documentDto), XContentType.JSON)
          .docAsUpsert(true)
      );
    } catch (Exception e) {
      final String message = String.format("Could not upsert document with id %s to index %s.", id, index);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public <T> Optional<T> getDocumentByIdAs(final String index, final String id, final Class<T> resultType) {
    try {
      final GetResponse getResponse = elasticsearchClient.get(new GetRequest(index, id));
      return getResponse.isSourceEmpty()
        ? Optional.empty()
        : Optional.ofNullable(objectMapper.readValue(getResponse.getSourceAsString(), resultType));
    } catch (Exception e) {
      final String message = String.format("Could not get document with id %s from index %s.", id, index);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public boolean indexExists(final String indexName) {
    log.debug("Checking if index exists [{}].", indexName);
    try {
      return elasticsearchClient.exists(indexName);
    } catch (Exception e) {
      String errorMessage = String.format("Could not validate whether index [%s] exists!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void deleteIndexIfExists(final String indexName) {
    if (indexExists(indexName)) {
      try {
        elasticsearchClient.deleteIndexByRawIndexNames(indexName);
      } catch (Exception e) {
        String errorMessage = String.format("Could not delete index [%s]!", indexName);
        throw new UpgradeRuntimeException(errorMessage, e);
      }
    }
  }

  public boolean indexTemplateExists(final String indexTemplateName) {
    log.debug("Checking if index template exists [{}].", indexTemplateName);
    try {
      return elasticsearchClient.templateExists(indexTemplateName);
    } catch (Exception e) {
      String errorMessage = String.format("Could not validate whether index template [%s] exists!", indexTemplateName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void deleteTemplateIfExists(final String indexTemplateName) {
    if (indexTemplateExists(indexTemplateName)) {
      try {
        elasticsearchClient.deleteIndexTemplateByIndexTemplateName(indexTemplateName);
      } catch (Exception e) {
        String errorMessage = String.format("Could not delete index template [%s]!", indexTemplateName);
        throw new UpgradeRuntimeException(errorMessage, e);
      }
    }
  }

  public void createOrUpdateTemplateWithoutAliases(final IndexMappingCreator mappingCreator) {
    schemaManager.createOrUpdateTemplateWithoutAliases(elasticsearchClient, mappingCreator);
  }

  public void createOrUpdateIndex(final IndexMappingCreator indexMapping) {
    schemaManager.createOrUpdateOptimizeIndex(elasticsearchClient, indexMapping);
  }

  public void createOrUpdateIndex(final IndexMappingCreator indexMapping,
                                  final Set<String> readOnlyAliases) {
    schemaManager.createOrUpdateOptimizeIndex(elasticsearchClient, indexMapping, readOnlyAliases);
  }

  public void initializeSchema() {
    schemaManager.initializeSchema(elasticsearchClient);
  }

  public void updateOptimizeVersion(final UpgradePlan upgradePlan) {
    if (!upgradePlan.isSilentUpgrade()) {
      log.info(
        "Updating Optimize Elasticsearch data structure version tag from {} to {}.",
        upgradePlan.getFromVersion().toString(),
        upgradePlan.getToVersion().toString()
      );
    }
    metadataService.upsertMetadata(elasticsearchClient, upgradePlan.getToVersion().toString());
  }

  public void createIndexFromTemplate(final String indexNameWithSuffix) {
    CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexNameWithSuffix);
    try {
      elasticsearchClient.createIndex(createIndexRequest);
    } catch (ElasticsearchStatusException e) {
      if (e.status() == RestStatus.BAD_REQUEST && e.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_TYPE)) {
        log.debug("Index {} from template already exists.", indexNameWithSuffix);
      } else {
        throw e;
      }
    } catch (Exception e) {
      String message = String.format("Could not create index %s from template.", indexNameWithSuffix);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void setAllAliasesToReadOnly(final String indexName, final Set<AliasMetadata> aliases) {
    log.debug("Setting all aliases pointing to {} to readonly.", indexName);

    final String[] aliasNames = aliases.stream().map(AliasMetadata::alias).toArray(String[]::new);
    try {
      final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
      final AliasActions removeAllAliasesAction = new AliasActions(AliasActions.Type.REMOVE)
        .index(indexName)
        .aliases(aliasNames);
      final AliasActions addReadOnlyAliasAction = new AliasActions(AliasActions.Type.ADD)
        .index(indexName)
        .writeIndex(false)
        .aliases(aliasNames);
      indicesAliasesRequest.addAliasAction(removeAllAliasesAction);
      indicesAliasesRequest.addAliasAction(addReadOnlyAliasAction);

      getHighLevelRestClient().indices().updateAliases(indicesAliasesRequest, elasticsearchClient.requestOptions());
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void addAlias(final String indexAlias, final String completeIndexName, final boolean isWriteAlias) {
    addAliases(Collections.singleton(indexAlias), completeIndexName, isWriteAlias);
  }

  public void addAliases(final Set<String> indexAliases, final String completeIndexName, final boolean isWriteAlias) {
    log.debug("Adding aliases [{}] to index [{}].", indexAliases, completeIndexName);

    try {
      final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
      final AliasActions aliasAction = new AliasActions(AliasActions.Type.ADD)
        .index(completeIndexName)
        .writeIndex(isWriteAlias)
        .aliases(indexAliases.toArray(new String[0]));
      indicesAliasesRequest.addAliasAction(aliasAction);
      getHighLevelRestClient().indices().updateAliases(indicesAliasesRequest, elasticsearchClient.requestOptions());
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", completeIndexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public Map<String, Set<AliasMetadata>> getAliasMap(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return getHighLevelRestClient()
        .indices()
        .getAlias(aliasesRequest, elasticsearchClient.requestOptions())
        .getAliases();
    } catch (Exception e) {
      String message = String.format("Could not retrieve alias map for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void insertDataByIndexName(final IndexMappingCreator indexMapping, final String data) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug("Inserting data to indexAlias [{}]. Data payload is [{}]", aliasName, data);
    try {
      final IndexRequest indexRequest = new IndexRequest(aliasName);
      indexRequest.source(data, XContentType.JSON);
      indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      getHighLevelRestClient().index(indexRequest, elasticsearchClient.requestOptions());
    } catch (Exception e) {
      String errorMessage = String.format("Could not add data to indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void updateDataByIndexName(final IndexMappingCreator indexMapping,
                                    final QueryBuilder query,
                                    final String updateScript,
                                    final Map<String, Object> parameters) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug("Updating data on [{}] using script [{}] and query [{}].", aliasName, updateScript, query);

    final UpdateByQueryRequest request = new UpdateByQueryRequest(aliasName);
    request.setRefresh(true);
    request.setQuery(query);
    request.setScript(createDefaultScriptWithSpecificDtoParams(updateScript, parameters, objectMapper));
    final String taskId;
    try {
      taskId = elasticsearchClient.submitUpdateTask(request).getTask();
    } catch (IOException e) {
      final String errorMessage = String.format(
        "Could not create updateBy task for [%s] with query [%s]!",
        aliasName,
        query
      );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
    waitUntilTaskIsFinished(taskId, aliasName);
  }

  public void deleteDataByIndexName(final IndexMappingCreator indexMapping,
                                    final QueryBuilder query) {
    final String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping);
    log.debug("Deleting data on [{}] with query [{}].", aliasName, query);

    final DeleteByQueryRequest request = new DeleteByQueryRequest(aliasName);
    request.setRefresh(true);
    request.setQuery(query);
    final String taskId;
    try {
      taskId = elasticsearchClient.submitDeleteTask(request).getTask();
    } catch (Exception e) {
      final String errorMessage = String.format(
        "Could not create deleteBy task for [%s] with query [%s]!", aliasName, query
      );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
    waitUntilTaskIsFinished(taskId, aliasName);
  }

  public void updateIndexDynamicSettingsAndMappings(final IndexMappingCreator indexMapping) {
    schemaManager.updateDynamicSettingsAndMappings(elasticsearchClient, indexMapping);
  }

  public Set<AliasMetadata> getAllAliasesForIndex(final String indexName) {
    GetAliasesRequest getAliasesRequest = new GetAliasesRequest().indices(indexName);
    try {
      return new HashSet<>(
        getHighLevelRestClient().indices()
          .getAlias(getAliasesRequest, elasticsearchClient.requestOptions())
          .getAliases()
          .getOrDefault(indexName, Sets.newHashSet())
      );
    } catch (Exception e) {
      String message = String.format("Could not retrieve existing aliases for {%s}.", indexName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public OptimizeIndexNameService getIndexNameService() {
    return elasticsearchClient.getIndexNameService();
  }

  private RestHighLevelClient getHighLevelRestClient() {
    return elasticsearchClient.getHighLevelClient();
  }

  private boolean areDocCountsEqual(final String sourceIndex, final String targetIndex) {
    try {
      final long sourceIndexDocCount = elasticsearchClient.countWithoutPrefix(new CountRequest(sourceIndex));
      final long targetIndexDocCount = elasticsearchClient.countWithoutPrefix(new CountRequest(targetIndex));
      return sourceIndexDocCount == targetIndexDocCount;
    } catch (Exception e) {
      log.warn(
        "Could not compare doc counts of index [{}] and [{}], assuming them to be not equal.", sourceIndex, targetIndex
      );
      return false;
    }
  }

  private Optional<TaskInfo> getPendingReindexTask(final String sourceIndex,
                                                   final String targetIndex) {

    try {
      final ListTasksResponse tasksResponse = elasticsearchClient.getTaskList(
        new ListTasksRequest().setDetailed(true).setActions("indices:data/write" + "/reindex"));
      return tasksResponse.getTasks().stream()
        .filter(taskInfo ->
                  taskInfo.getDescription() != null
                    && taskInfo.getDescription().contains(sourceIndex)
                    && taskInfo.getDescription().contains(targetIndex)
        ).findAny();
    } catch (Exception e) {
      log.warn(
        "Could not get pending reindex task from index [{}] to [{}], assuming there are none.", sourceIndex, targetIndex
      );
      return Optional.empty();
    }
  }

  private String submitReindexTask(final String sourceIndexName,
                                   final String targetIndexName,
                                   final String mappingScript,
                                   final Map<String, Object> parameters) {
    final ReindexRequest reindexRequest = new ReindexRequest()
      .setSourceIndices(sourceIndexName)
      .setDestIndex(targetIndexName)
      .setRefresh(true);

    if (mappingScript != null) {
      reindexRequest.setScript(
        createDefaultScriptWithSpecificDtoParams(mappingScript, parameters, objectMapper)
      );
    }

    String taskId;
    try {
      taskId = submitReindexTask(reindexRequest);
    } catch (Exception e) {
      throw new UpgradeRuntimeException(
        String.format(
          "Error while trying to reindex data from index [%s] to [%s]!", sourceIndexName, targetIndexName
        ),
        e
      );
    }
    return taskId;
  }

  private String submitReindexTask(final ReindexRequest reindexRequest) throws IOException {
    return elasticsearchClient.submitReindexTask(reindexRequest).getTask();
  }

  private void waitUntilTaskIsFinished(final String taskId,
                                       final String destinationIndex) {
    try {
      ElasticsearchWriterUtil.waitUntilTaskIsFinished(elasticsearchClient, taskId, destinationIndex);
    } catch (OptimizeRuntimeException e) {
      throw new UpgradeRuntimeException(e.getCause().getMessage(), e);
    }
  }

}
