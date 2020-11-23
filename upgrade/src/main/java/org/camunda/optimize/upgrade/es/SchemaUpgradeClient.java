/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;

@Slf4j
public class SchemaUpgradeClient {
  private static final String TASKS_ENDPOINT = "_tasks";

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
                      final String destinationIndex) {
    this.reindex(sourceIndex, destinationIndex, null, Collections.emptyMap());
  }

  public void reindex(final String sourceIndexName,
                      final String destinationIndexName,
                      final String mappingScript,
                      final Map<String, Object> parameters) {
    log.debug(
      "Reindexing from index [{}] to [{}] using the mapping script [{}].",
      sourceIndexName, destinationIndexName, mappingScript
    );

    ReindexRequest reindexRequest = new ReindexRequest()
      .setSourceIndices(sourceIndexName)
      .setDestIndex(destinationIndexName)
      .setRefresh(true);

    if (mappingScript != null) {
      reindexRequest.setScript(
        createDefaultScriptWithSpecificDtoParams(mappingScript, parameters, objectMapper)
      );
    }

    String taskId;
    try {
      taskId = getHighLevelRestClient().submitReindexTask(reindexRequest, RequestOptions.DEFAULT).getTask();
    } catch (Exception e) {
      throw new UpgradeRuntimeException(
        String.format(
          "Error while trying to reindex data from index [%s] to [%s]!", sourceIndexName, destinationIndexName
        ),
        e
      );
    }
    waitUntilTaskIsFinished(taskId, destinationIndexName);
  }

  public <T> void upsert(final String index, final String id, final T documentDto) {
    try {
      elasticsearchClient.update(
        new UpdateRequest(index, id)
          .doc(objectMapper.writeValueAsString(documentDto), XContentType.JSON)
          .docAsUpsert(true),
        RequestOptions.DEFAULT
      );
    } catch (Exception e) {
      final String message = String.format("Could not upsert document with id %s to index %s.", id, index);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public <T> Optional<T> getDocumentByIdAs(final String index, final String id, final Class<T> resultType) {
    try {
      final GetResponse getResponse = elasticsearchClient.get(new GetRequest(index, id), RequestOptions.DEFAULT);
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
      return getHighLevelRestClient().indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
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

  public void createOrUpdateTemplateWithoutAliases(final IndexMappingCreator mappingCreator,
                                                   final String templateName) {
    schemaManager.createOrUpdateTemplateWithoutAliases(elasticsearchClient, mappingCreator, templateName);
  }

  public void createIndex(final IndexMappingCreator indexMapping) {
    schemaManager.createOptimizeIndex(elasticsearchClient, indexMapping);
  }

  public void initializeSchema() {
    schemaManager.initializeSchema(elasticsearchClient);
  }

  public void updateOptimizeVersion(final String fromVersion, final String toVersion) {
    log.info("Updating Optimize Elasticsearch data structure version tag from {} to {}.", fromVersion, toVersion);
    metadataService.upsertMetadata(elasticsearchClient, toVersion);
  }

  public void createIndexFromTemplate(final String indexNameWithSuffix) {
    CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexNameWithSuffix);
    try {
      getHighLevelRestClient().indices().create(createIndexRequest, RequestOptions.DEFAULT);
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

      getHighLevelRestClient().indices().updateAliases(indicesAliasesRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void addAlias(final String indexAlias, final String indexName, final boolean isWriteAlias) {
    log.debug("Adding alias [{}] to index [{}].", indexAlias, indexName);

    try {
      final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
      final AliasActions aliasAction = new AliasActions(AliasActions.Type.ADD)
        .index(indexName)
        .writeIndex(isWriteAlias)
        .alias(indexAlias);
      indicesAliasesRequest.addAliasAction(aliasAction);
      getHighLevelRestClient().indices().updateAliases(indicesAliasesRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public Map<String, Set<AliasMetadata>> getAliasMap(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return getHighLevelRestClient()
        .indices()
        .getAlias(aliasesRequest, RequestOptions.DEFAULT)
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
      getHighLevelRestClient().index(indexRequest, RequestOptions.DEFAULT);
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
      taskId = getHighLevelRestClient().submitUpdateByQueryTask(request, RequestOptions.DEFAULT).getTask();
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
      taskId = getHighLevelRestClient().submitDeleteByQueryTask(request, RequestOptions.DEFAULT).getTask();
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
          .getAlias(getAliasesRequest, RequestOptions.DEFAULT)
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

  private void waitUntilTaskIsFinished(final String taskId, final String destinationIndex) {
    boolean finished = false;
    int progress = -1;
    while (!finished) {
      try {
        final TaskResponse taskResponse = getTaskResponse(taskId);
        validateTaskResponse(taskResponse);

        int currentProgress = (int) (taskResponse.getProgress() * 100.0);
        if (currentProgress != progress) {
          final TaskResponse.Status taskStatus = taskResponse.getTaskStatus();
          progress = currentProgress;
          log.info(
            "Progress of task (id:{}) on index {}: {}% (total: {}, updated: {}, created: {}, deleted: {})",
            taskId,
            destinationIndex,
            progress,
            taskStatus.getTotal(),
            taskStatus.getUpdated(),
            taskStatus.getCreated(),
            taskStatus.getDeleted()
          );
        }
        finished = taskResponse.isCompleted();
        if (!finished) {
          Thread.sleep(1000);
        }
      } catch (InterruptedException e) {
        log.error("Waiting for Elasticsearch task (id:{}) completion was interrupted!", taskId, e);
        Thread.currentThread().interrupt();
      } catch (UpgradeRuntimeException e) {
        // upgrade exceptions are just forwarded
        throw e;
      } catch (Exception e) {
        throw new UpgradeRuntimeException(
          String.format("Error while trying to read Elasticsearch task (id:%s) progress!", taskId), e
        );
      }
    }
  }

  private TaskResponse getTaskResponse(final String taskId) throws IOException {
    final Response response = getHighLevelRestClient().getLowLevelClient()
      .performRequest(new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId));
    return objectMapper.readValue(response.getEntity().getContent(), TaskResponse.class);
  }

  private void validateTaskResponse(final TaskResponse taskResponse) {
    if (taskResponse.getError() != null) {
      log.error("An Elasticsearch task that is part of the upgrade failed: {}", taskResponse.getError());
      throw new UpgradeRuntimeException(taskResponse.getError().toString());
    }

    if (taskResponse.getResponseDetails() != null) {
      final List<Object> failures = taskResponse.getResponseDetails().getFailures();
      if (failures != null && !failures.isEmpty()) {
        log.error("An Elasticsearch task that is part of the upgrade contained failures: {}", failures);
        throw new UpgradeRuntimeException(failures.toString());
      }
    }
  }

}
