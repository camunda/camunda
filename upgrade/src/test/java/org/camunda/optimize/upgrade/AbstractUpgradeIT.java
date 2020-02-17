/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.camunda.optimize.upgrade.version27.EventIndexV1;
import org.camunda.optimize.upgrade.version27.EventProcessMappingIndexV1;
import org.camunda.optimize.upgrade.version27.EventProcessPublishStateIndexV1;
import org.camunda.optimize.upgrade.version27.EventSequenceCountIndexV1;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;

public abstract class AbstractUpgradeIT {

  protected static final String FROM_VERSION = "2.7.0";

  protected static final MetadataIndex METADATA_INDEX = new MetadataIndex();

  protected static final EventIndexV1 EVENT_INDEX_V1 = new EventIndexV1();
  protected static final EventSequenceCountIndexV1 EVENT_SEQUENCE_COUNT_INDEX_V1 = new EventSequenceCountIndexV1();
  protected static final EventProcessMappingIndexV1 EVENT_PROCESS_MAPPING_INDEX_V1 = new EventProcessMappingIndexV1();
  protected static final EventProcessPublishStateIndexV1 EVENT_PROCESS_PUBLISH_STATE_INDEX_V1 = new EventProcessPublishStateIndexV1();

  protected ObjectMapper objectMapper;
  protected OptimizeElasticsearchClient prefixAwareClient;
  protected OptimizeIndexNameService indexNameService;
  protected UpgradeExecutionDependencies upgradeDependencies;
  private ConfigurationService configurationService;
  private ElasticsearchMetadataService metadataService;

  @AfterEach
  public void after() throws Exception {
    cleanAllDataFromElasticsearch();
    deleteEnvConfig();
  }

  @BeforeEach
  protected void setUp() throws Exception {
    configurationService = createDefaultConfiguration();
    if (upgradeDependencies == null) {
      upgradeDependencies = UpgradeUtil.createUpgradeDependencies();
      objectMapper = upgradeDependencies.getObjectMapper();
      prefixAwareClient = upgradeDependencies.getPrefixAwareClient();
      indexNameService = upgradeDependencies.getIndexNameService();
      metadataService = upgradeDependencies.getMetadataService();
    }

    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
  }

  protected void initSchema(List<IndexMappingCreator> mappingCreators) {
    final ElasticSearchSchemaManager elasticSearchSchemaManager = new ElasticSearchSchemaManager(
      metadataService, createDefaultConfiguration(), indexNameService, mappingCreators, objectMapper
    );
    elasticSearchSchemaManager.initializeSchema(prefixAwareClient);
  }

  protected void setMetadataIndexVersion(String version) {
    metadataService.writeMetadata(prefixAwareClient, new MetadataDto(version));
  }

  protected void createOptimizeIndexWithTypeAndVersion(StrictIndexMappingCreator indexMapping,
                                                       int version) throws IOException {
    final String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    final String indexName = getVersionedIndexName(indexMapping.getIndexName(), version);
    final Settings indexSettings = createIndexSettings(indexMapping);

    CreateIndexRequest request = new CreateIndexRequest(indexName);
    request.alias(new Alias(aliasName));
    request.settings(indexSettings);
    indexMapping.setDynamicMappingsValue("false");
    prefixAwareClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);
  }

  protected void executeBulk(final String bulkPayload) throws IOException {
    final Request request = new Request(HttpPost.METHOD_NAME, "/_bulk");
    final HttpEntity entity = new NStringEntity(
      UpgradeUtil.readClasspathFileAsString(bulkPayload),
      ContentType.APPLICATION_JSON
    );
    request.setEntity(entity);
    prefixAwareClient.getLowLevelClient().performRequest(request);
    prefixAwareClient.getHighLevelClient().indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
  }

  protected String getVersionedIndexName(final String indexName, final int version) {
    return indexNameService.getOptimizeIndexNameForAliasAndVersion(
      indexNameService.getOptimizeIndexAliasForIndex(indexName),
      String.valueOf(version)
    );
  }

  private Settings createIndexSettings(IndexMappingCreator indexMappingCreator) {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService, indexMappingCreator);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  private void cleanAllDataFromElasticsearch() {
    try {
      prefixAwareClient.getHighLevelClient().indices().delete(new DeleteIndexRequest("_all"), RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new RuntimeException("Failed cleaning elasticsearch");
    }
  }

}
