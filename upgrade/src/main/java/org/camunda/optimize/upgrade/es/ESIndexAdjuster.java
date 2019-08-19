/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.Getter;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.wrapper.DestinationWrapper;
import org.camunda.optimize.upgrade.wrapper.ReindexPayload;
import org.camunda.optimize.upgrade.wrapper.ScriptWrapper;
import org.camunda.optimize.upgrade.wrapper.SourceWrapper;
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
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;

public class ESIndexAdjuster {

  private static final Logger logger = LoggerFactory.getLogger(ESIndexAdjuster.class);

  private static final String TASKS_ENDPOINT = "_tasks";
  private static final String REINDEX_OPERATION = "reindex";
  private static final String MAPPING_OPERATION = "/_mapping";
  private static final int ONE_SECOND = 1000;

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
                      final String destinationIndex,
                      final String sourceType,
                      final String destType) {
    this.reindex(
      sourceIndex,
      destinationIndex,
      sourceType,
      destType,
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

  public String getIndexMappings(final String indexName) {
    logger.debug("Retrieve index mapping for index [{}].", indexName);
    try {
      // we need to perform this request manually since Elasticsearch 6.5 automatically
      // adds "master_timeout" parameter to the get mapping request which is not
      // recognized prior to 6.4 and throws an error. As soon as we don't support 6.3 or
      // older those lines can be replaced with the high rest client equivalent.
      Response response = restClient.getLowLevelClient().performRequest(
        new Request(HttpGet.METHOD_NAME, "/" + indexName + MAPPING_OPERATION)
      );
      String mappingWithIndexName = EntityUtils.toString(response.getEntity());
      return extractMappings(indexName, mappingWithIndexName);
    } catch (IOException e) {
      String errorMessage =
        String.format(
          "Could not retrieve index mapping from [%s]!",
          indexName
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  private String extractMappings(String indexName, String mappingWithIndexName) throws JsonProcessingException {
    Map read = JsonPath.parse(mappingWithIndexName).read("$." + indexName);
    return objectMapper.writeValueAsString(read);
  }

  public void reindex(final String sourceIndex,
                      final String destinationIndex,
                      final String sourceType,
                      final String destType,
                      final String mappingScript) {
    logger.debug(
      "Reindexing from index [{}] to [{}] using the mapping script [{}].",
      sourceIndex,
      destinationIndex,
      mappingScript
    );

    ReindexPayload toSend = new ReindexPayload();
    toSend.setSource(new SourceWrapper(sourceIndex, sourceType));
    toSend.setDest(new DestinationWrapper(destinationIndex, destType));
    if (mappingScript != null) {
      toSend.setScript(new ScriptWrapper(mappingScript));
    }
    ObjectMapper om = new ObjectMapper();
    om.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    ReindexTaskResponse reindexTaskResponse;
    try {
      HttpEntity entity = new NStringEntity(om.writeValueAsString(toSend), ContentType.APPLICATION_JSON);
      final Request request = new Request(HttpPost.METHOD_NAME, "/" + getReindexEndpoint());
      request.setEntity(entity);
      request.addParameter("refresh", "true");
      request.addParameter("wait_for_completion", "false");

      final Response response = restClient.getLowLevelClient().performRequest(request);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new UpgradeRuntimeException("Failed to start reindex from " + sourceIndex + " to " + destinationIndex);
      }
      reindexTaskResponse = objectMapper.readValue(response.getEntity().getContent(), ReindexTaskResponse.class);
    } catch (IOException e) {
      String errorMessage =
        String.format(
          "Could not reindex data from index [%s] to [%s]!",
          sourceIndex,
          destinationIndex
        );
      throw new UpgradeRuntimeException(errorMessage, e);
    }

    if (reindexTaskResponse.getTaskId() != null) {
      waitUntilReindexingIsFinished(reindexTaskResponse.getTaskId(), sourceIndex, destinationIndex);
    } else {
      String errorMessage =
        String.format(
          "Could not reindex data from index [%s] to [%s]! Reindex request was not successful!",
          sourceIndex,
          destinationIndex
        );
      throw new UpgradeRuntimeException(errorMessage);
    }
  }

  private void waitUntilReindexingIsFinished(final String taskId,
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
            progress = currentProgress;
            logger.info(
              "Reindexing from {} to {}, progress: {}%",
              sourceIndex,
              destinationIndex,
              progress
            );
          }

          finished = taskResponse.isDone();
        } else {
          logger.error(
            "Failed retrieving progress of reindex task, statusCode: {}, will retry",
            response.getStatusLine().getStatusCode()
          );
        }

        if (!finished) {
          Thread.sleep(ONE_SECOND);
        }
      } catch (IOException | InterruptedException e) {
        String errorMessage = "Error while trying to read reindex progress!";
        throw new UpgradeRuntimeException(errorMessage, e);
      }
    }
  }

  private String getReindexEndpoint() {
    return "_" + REINDEX_OPERATION;
  }

  public void createIndex(final IndexMappingCreator mapping) {
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForTypeMapping(mapping);
    logger.debug(
      "Creating index [{}] and mapping [{}].",
      indexName, Strings.toString(mapping.getSource())
    );

    final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
    createIndexRequest.mapping(mapping.getIndexName(), mapping.getSource());
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
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForTypeMapping(mapping);
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

  public void insertDataByTypeName(final IndexMappingCreator type, final String data) {
    String aliasName = indexNameService.getOptimizeIndexAliasForIndex(type.getIndexName());
    logger.debug("Inserting data to indexAlias [{}]. Data payload is [{}]", aliasName, data);
    try {
      final IndexRequest indexRequest = new IndexRequest(aliasName, type.getIndexName());
      indexRequest.source(data, XContentType.JSON);
      indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      restClient.index(indexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String errorMessage = String.format("Could not add data to indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void updateDataByTypeName(final String indexName,
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

  public void updateIndexDynamicSettingsAndMappings(IndexMappingCreator index) {
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForTypeMapping(index);
    try {
      final Settings indexSettings = buildDynamicSettings(configurationService);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
      updateSettingsRequest.indices(indexName);
      updateSettingsRequest.settings(indexSettings);
      restClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index settings for type [%s].", index.getIndexName());
      throw new UpgradeRuntimeException(message, e);
    }

    try {
      final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
      putMappingRequest.type(index.getIndexName()).source(index.getSource());
      restClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index mappings for type [%s].", index.getIndexName());
      throw new UpgradeRuntimeException(message, e);
    }
  }
}
