/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.settings.Settings;
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
import java.util.Set;

import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;
import static org.camunda.optimize.upgrade.util.MappingMetadataUtil.getAllMappings;

@Slf4j
public class SchemaUpgradeClient {
  private static final String TASKS_ENDPOINT = "_tasks";

  private final ElasticSearchSchemaManager schemaManager;
  private final ElasticsearchMetadataService metadataService;
  private final OptimizeElasticsearchClient elasticsearchClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  public SchemaUpgradeClient(final UpgradeExecutionDependencies upgradeDependencies) {
    this(
      new ElasticSearchSchemaManager(
        upgradeDependencies.getMetadataService(),
        upgradeDependencies.getConfigurationService(),
        upgradeDependencies.getIndexNameService(),
        getAllMappings(upgradeDependencies.getEsClient()),
        upgradeDependencies.getObjectMapper()
      ),
      upgradeDependencies.getMetadataService(),
      upgradeDependencies.getEsClient(),
      upgradeDependencies.getConfigurationService()
    );
  }

  public SchemaUpgradeClient(final ElasticSearchSchemaManager schemaManager,
                             final ElasticsearchMetadataService metadataService,
                             final OptimizeElasticsearchClient elasticsearchClient,
                             final ConfigurationService configurationService) {
    this.schemaManager = schemaManager;
    this.metadataService = metadataService;
    this.configurationService = configurationService;
    this.elasticsearchClient = elasticsearchClient;
    this.objectMapper = new ObjectMapperFactory(
      new OptimizeDateTimeFormatterFactory().getObject(), configurationService
    ).createOptimizeMapper();
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
        ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams(mappingScript, parameters, objectMapper)
      );
    }

    String taskId;
    try {
      taskId = getPlainRestClient().submitReindexTask(reindexRequest, RequestOptions.DEFAULT).getTask();
      if (taskId == null) {
        throw new UpgradeRuntimeException(String.format(
          "Could not start reindexing of data from index [%s] to [%s]!",
          sourceIndexName, destinationIndexName
        ));
      }
    } catch (Exception e) {
      throw new UpgradeRuntimeException(
        String.format(
          "Error while trying to reindex data from index [%s] to [%s]!", sourceIndexName, destinationIndexName
        ),
        e
      );
    }
    waitUntilReindexingTaskIsFinished(taskId, sourceIndexName, destinationIndexName);
  }

  public boolean indexExists(final String indexName) {
    log.debug("Checking if index exists [{}].", indexName);
    try {
      return getPlainRestClient().indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    } catch (Exception e) {
      String errorMessage = String.format("Could not validate whether index [%s] exists!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void deleteIndex(final String indexName) {
    try {
      elasticsearchClient.deleteIndexByRawIndexNames(indexName);
    } catch (Exception e) {
      String errorMessage = String.format("Could not delete index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void createOrUpdateTemplateWithoutAliases(final IndexMappingCreator mappingCreator,
                                                   final String templateName) {
    final String indexNameWithoutSuffix = getIndexNameService()
      .getOptimizeIndexNameWithVersionWithoutSuffix(mappingCreator);
    final Settings indexSettings = createIndexSettings(mappingCreator);

    log.debug("creating or updating template with name {}", indexNameWithoutSuffix);
    PutIndexTemplateRequest templateRequest = new PutIndexTemplateRequest(indexNameWithoutSuffix)
      .version(mappingCreator.getVersion())
      .mapping(mappingCreator.getSource())
      .settings(indexSettings)
      .patterns(Collections.singletonList(getIndexNameService()
                                            .getOptimizeIndexNameWithVersionWithWildcardSuffix(mappingCreator)));

    try {
      getPlainRestClient().indices().putTemplate(templateRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String message = String.format("Could not create template %s", templateName);
      throw new OptimizeRuntimeException(message, e);
    }
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
      getPlainRestClient().indices().create(createIndexRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String message = String.format("Could not create index %s from template.", indexNameWithSuffix);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void setAllAliasesToReadOnly(final String indexName, final Set<AliasMetaData> aliases) {
    log.debug("Setting all aliases pointing to {} to readonly.", indexName);

    final String[] aliasNames = aliases.stream().map(AliasMetaData::alias).toArray(String[]::new);
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

      getPlainRestClient().indices().updateAliases(indicesAliasesRequest, RequestOptions.DEFAULT);
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
      getPlainRestClient().indices().updateAliases(indicesAliasesRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String errorMessage = String.format("Could not add alias to index [%s]!", indexName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public Map<String, Set<AliasMetaData>> getAliasMap(final String aliasName) {
    GetAliasesRequest aliasesRequest = new GetAliasesRequest().aliases(aliasName);
    try {
      return getPlainRestClient()
        .indices()
        .getAlias(aliasesRequest, RequestOptions.DEFAULT)
        .getAliases();
    } catch (Exception e) {
      String message = String.format("Could not retrieve alias map for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public void insertDataByIndexName(final IndexMappingCreator indexMapping, final String data) {
    String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    log.debug("Inserting data to indexAlias [{}]. Data payload is [{}]", aliasName, data);
    try {
      final IndexRequest indexRequest = new IndexRequest(aliasName);
      indexRequest.source(data, XContentType.JSON);
      indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      getPlainRestClient().index(indexRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String errorMessage = String.format("Could not add data to indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void updateDataByIndexName(final String indexName,
                                    final QueryBuilder query,
                                    final String updateScript,
                                    final Map<String, Object> parameters) {
    String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexName);
    log.debug(
      "Updating data for indexAlias [{}] using script [{}] and query [{}].", aliasName, updateScript, query
    );

    try {
      UpdateByQueryRequest request = new UpdateByQueryRequest(aliasName);
      request.setRefresh(true);
      request.setQuery(query);
      request.setScript(ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams(
        updateScript,
        parameters,
        objectMapper
      ));
      getPlainRestClient().updateByQuery(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String errorMessage = String.format("Could not update data for indexAlias [%s]!", aliasName);
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void deleteDataByIndexName(final String indexName,
                                    final QueryBuilder query) {
    String aliasName = getIndexNameService().getOptimizeIndexAliasForIndex(indexName);
    log.debug(
      "Deleting data for indexAlias [{}] with query [{}].", aliasName, query
    );

    try {
      DeleteByQueryRequest request = new DeleteByQueryRequest(aliasName);
      request.setRefresh(true);
      request.setQuery(query);
      getPlainRestClient().deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      String errorMessage = String.format(
        "Could not delete data for indexAlias [%s] with query [%s]!",
        aliasName,
        query
      );
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  public void updateIndexDynamicSettingsAndMappings(final IndexMappingCreator indexMapping) {
    final String indexName = getIndexNameService().getOptimizeIndexNameWithVersionForAllIndicesOf(indexMapping);
    try {
      final Settings indexSettings = buildDynamicSettings(configurationService);
      final UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest();
      updateSettingsRequest.indices(indexName);
      updateSettingsRequest.settings(indexSettings);
      getPlainRestClient().indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index settings for index [%s].", indexName);
      throw new UpgradeRuntimeException(message, e);
    }

    try {
      final PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
      putMappingRequest.source(indexMapping.getSource());
      getPlainRestClient().indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String message = String.format("Could not update index mappings for index [%s].", indexName);
      throw new UpgradeRuntimeException(message, e);
    }
  }

  public Set<AliasMetaData> getAllAliasesForIndex(final String indexName) {
    GetAliasesRequest getAliasesRequest = new GetAliasesRequest().indices(indexName);
    try {
      return new HashSet<>(
        getPlainRestClient().indices()
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

  private RestHighLevelClient getPlainRestClient() {
    return elasticsearchClient.getHighLevelClient();
  }

  private void waitUntilReindexingTaskIsFinished(final String taskId,
                                                 final String sourceIndex,
                                                 final String destinationIndex) {
    boolean finished = false;
    int progress = -1;
    while (!finished) {
      try {
        final Response response = getPlainRestClient().getLowLevelClient()
          .performRequest(new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId));
        if (response.getStatusLine().getStatusCode() == javax.ws.rs.core.Response.Status.OK.getStatusCode()) {
          TaskResponse taskResponse = objectMapper.readValue(response.getEntity().getContent(), TaskResponse.class);
          if (taskResponse.getError() != null) {
            log.error(
              "A reindex batch that is part of the upgrade failed. Elasticsearch reported the following error: {}.",
              taskResponse.getError()
            );
            throw new UpgradeRuntimeException(taskResponse.getError().toString());
          }

          if (taskResponse.getResponseDetails() != null) {
            List<Object> failures = taskResponse.getResponseDetails().getFailures();
            if (failures != null && !failures.isEmpty()) {
              final String errorMessage = String.format(
                "A reindex batch that is part of the upgrade failed: %s", failures.toString()
              );
              log.error(errorMessage);
              throw new UpgradeRuntimeException(errorMessage);
            }
          }

          int currentProgress = (int) (taskResponse.getProgress() * 100.0);
          if (currentProgress != progress) {
            final TaskResponse.Status taskStatus = taskResponse.getTaskStatus();
            progress = currentProgress;
            log.info(
              "Reindexing from {} to {}, progress: {}%. Reindex status= total: {}, updated: {}, created: {}, deleted:" +
                " {}",
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
          log.error(
            "Failed retrieving progress of reindex task, statusCode: {}, will retry",
            response.getStatusLine().getStatusCode()
          );
        }

        if (!finished) {
          Thread.sleep(1000);
        }
      } catch (InterruptedException e) {
        log.warn("Waiting for reindex operation completion was interrupted!", e);
        Thread.currentThread().interrupt();
      } catch (UpgradeRuntimeException e) {
        throw e;
      } catch (Exception e) {
        String errorMessage = "Error while trying to read reindex progress!";
        throw new UpgradeRuntimeException(errorMessage, e);
      }
    }
  }

  private Settings createIndexSettings(IndexMappingCreator indexMappingCreator) {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService, indexMappingCreator);
    } catch (IOException e) {
      log.error("Could not create settings!", e);
      throw new UpgradeRuntimeException("Could not create index settings");
    }
  }

}
