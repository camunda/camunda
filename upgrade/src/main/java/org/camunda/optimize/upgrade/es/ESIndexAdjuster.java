/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DEFAULT_INDEX_TYPE;

public class ESIndexAdjuster {

  private static final Logger logger = LoggerFactory.getLogger(ESIndexAdjuster.class);

  private static final String TASKS_ENDPOINT = "_tasks";

  private final RestHighLevelClient restClient;
  @Getter
  private final OptimizeIndexNameService indexNameService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ConfigurationService configurationService;

  public ESIndexAdjuster(final RestHighLevelClient restClient,
                         final OptimizeIndexNameService indexNameService,
                         final ConfigurationService configurationService) {
    this.indexNameService = indexNameService;
    this.configurationService = configurationService;
    this.restClient = restClient;
  }

  public void reindex(final String sourceIndex,
                      final String destinationIndex) {
    this.reindex(
      sourceIndex,
      destinationIndex,
      null,
      null
    );
  }

  public void deleteIndex(final String indexName) {
    logger.debug("Deleting index [{}].", indexName);
    try {
      restClient.indices().delete(new DeleteIndexRequest(indexName), RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not delete index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void reindex(final String sourceIndexName,
                      final String destinationIndexName,
                      final String sourceType,
                      final String mappingScript) {
    logger.debug(
      "Reindexing from index [{}] to [{}] using the mapping script [{}].",
      sourceIndexName,
      destinationIndexName,
      mappingScript
    );

    Set<String> sourceTypes = new HashSet<>();
    sourceTypes.add(DEFAULT_INDEX_TYPE);
    Optional.ofNullable(sourceType).ifPresent(sourceTypes::add);

    ReindexRequest reindexRequest = new ReindexRequest()
      .setSourceIndices(sourceIndexName)
      .setSourceDocTypes(sourceTypes.toArray(new String[sourceTypes.size()]))
      .setDestIndex(destinationIndexName)
      .setDestDocType(DEFAULT_INDEX_TYPE)
      .setRefresh(true);

    if (mappingScript != null) {
      reindexRequest.setScript(new Script(mappingScript));
    }

    String taskId;
    try {
      taskId = restClient.submitReindexTask(reindexRequest, RequestOptions.DEFAULT).getTask();
      if (taskId == null) {
        throw new UpgradeRuntimeException(String.format(
          "Could not start reindexing of data from index [%s] to [%s]!",
          sourceIndexName,
          destinationIndexName
        ));
      }
    } catch (IOException e) {
      throw new UpgradeRuntimeException(String.format(
        "Error while trying to reindex data from index [%s] to [%s]!",
        sourceIndexName,
        destinationIndexName
      ), e);
    }
    waitUntilReindexingTaskIsFinished(taskId, sourceIndexName, destinationIndexName);
  }

  private void waitUntilReindexingTaskIsFinished(final String taskId,
                                                 final String sourceIndex,
                                                 final String destinationIndex) {
    boolean finished = false;
    int progress = -1;
    while (!finished) {
      try {
        final Response response = restClient.getLowLevelClient()
          .performRequest(new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId));
        if (response.getStatusLine().getStatusCode() == 200) {
          TaskResponse taskResponse = objectMapper.readValue(response.getEntity().getContent(), TaskResponse.class);
          if (taskResponse.getError() != null) {
            logger.error(
              "A reindex batch that is part of the upgrade failed. Elasticsearch reported the following error: {}.",
              taskResponse.getError().toString()
            );
            logger.error("The upgrade will be aborted. Please restore your Elasticsearch backup and try again.");
            throw new UpgradeRuntimeException(taskResponse.getError().toString());
          }

          int currentProgress = new Double(taskResponse.getProgress() * 100.0).intValue();
          if (currentProgress != progress) {
            final TaskResponse.Status taskStatus = taskResponse.getTaskStatus();
            progress = currentProgress;
            logger.info(
              "Reindexing from {} to {}, progress: {}%. Reindex status= total: {}, updated: {}, created: {}, deleted: {}",
              sourceIndex,
              destinationIndex,
              progress,
              taskStatus.getTotal(),
              taskStatus.getUpdated(),
              taskStatus.getCreated(),
              taskStatus.getDeleted()
            );
          }
          finished = taskResponse.isCompleted();
        } else {
          logger.error(
            "Failed retrieving progress of reindex task, statusCode: {}, will retry",
            response.getStatusLine().getStatusCode()
          );
        }

        if (!finished) {
          Thread.sleep(1000);
        }
      } catch (IOException | InterruptedException e) {
        String errorMessage = "Error while trying to read reindex progress!";
        throw new UpgradeRuntimeException(errorMessage, e);
      }
    }
  }

  public void createIndex(final IndexMappingCreator indexMapping) {
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(indexMapping);
    logger.debug(
      "Creating index [{}] and mapping [{}].",
      indexName, Strings.toString(indexMapping.getSource())
    );

    final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
    createIndexRequest.mapping(DEFAULT_INDEX_TYPE, indexMapping.getSource());
    createIndexRequest.settings(createIndexSettings());

    try {
      restClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not create index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  private Settings createIndexSettings() {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService);
    } catch (IOException e) {
      logger.error("Could not create settings!", e);
      throw new UpgradeRuntimeException("Could not create index settings");
    }
  }

  public void addAlias(final IndexMappingCreator mapping) {
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(mapping);
    logger.debug("Adding alias [{}] to index [{}].", indexAlias, indexName);

    try {
      final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
      final AliasActions aliasAction = new AliasActions(AliasActions.Type.ADD)
        .index(indexName)
        .alias(indexAlias);
      indicesAliasesRequest.addAliasAction(aliasAction);
      restClient.indices().updateAliases(indicesAliasesRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void insertDataByIndexName(final IndexMappingCreator indexMapping, final String data) {
    String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    logger.debug("Inserting data to indexAlias [{}]. Data payload is [{}]", aliasName, data);
    try {
      final IndexRequest indexRequest = new IndexRequest(aliasName);
      indexRequest.source(data, XContentType.JSON);
      indexRequest.type(DEFAULT_INDEX_TYPE);
      indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      restClient.index(indexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not add data to indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void updateDataByIndexName(final String indexName,
                                    final QueryBuilder query,
                                    final String updateScript,
                                    final Map<String, Object> parameters) {
    String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    logger.debug(
      "Updating data for indexAlias [{}] using script [{}] and query [{}].", aliasName, updateScript, query.toString()
    );

    try {
      UpdateByQueryRequest request = new UpdateByQueryRequest(aliasName);
      request.setRefresh(true);
      request.setQuery(query);
      request.setScript(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        updateScript,
        Optional.ofNullable(parameters).orElse(Collections.emptyMap())
      ));
      restClient.updateByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not update data for indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void deleteDataByIndexName(final String indexName,
                                    final QueryBuilder query) {
    String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    logger.debug(
      "Deleting data for indexAlias [{}] with query [{}].", aliasName, query.toString()
    );

    try {
      DeleteByQueryRequest request = new DeleteByQueryRequest(aliasName);
      request.setRefresh(true);
      request.setQuery(query);
      request.setDocTypes(DEFAULT_INDEX_TYPE);
      restClient.deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not delete data for indexAlias [%s] with query [%s]!", aliasName, query);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void updateIndexDynamicSettingsAndMappings(IndexMappingCreator indexMapping) {
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(indexMapping);
    try {
      final Settings indexSettings = buildDynamicSettings(configurationService);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
      updateSettingsRequest.indices(indexName);
      updateSettingsRequest.settings(indexSettings);
      restClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index settings for index [%s].", indexMapping.getIndexName());
      throw new UpgradeRuntimeException(message, e);
    }

    try {
      final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
      putMappingRequest.type(DEFAULT_INDEX_TYPE).source(indexMapping.getSource());
      restClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index mappings for index [%s].", indexMapping.getIndexName());
      throw new UpgradeRuntimeException(message, e);
    }
  }
}
