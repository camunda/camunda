/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;
import org.camunda.optimize.upgrade.indices.UserTestIndex;
import org.camunda.optimize.upgrade.indices.UserTestUpdatedMappingIndex;
import org.camunda.optimize.upgrade.indices.UserTestWithTemplateIndex;
import org.camunda.optimize.upgrade.indices.UserTestWithTemplateUpdatedMappingIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.service.UpgradeStepLogService;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.DeleteDataStep;
import org.camunda.optimize.upgrade.steps.document.InsertDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.POST;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static org.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.mockserver.model.HttpRequest.request;

public abstract class AbstractUpgradeIT {

  protected static final MetadataIndex METADATA_INDEX = new MetadataIndex();

  protected static final String FROM_VERSION = "2.6.0";
  protected static final String INTERMEDIATE_VERSION = "2.6.1";
  protected static final String TO_VERSION = "2.7.0";

  protected static final IndexMappingCreator TEST_INDEX_V1 = new UserTestIndex(1);
  protected static final IndexMappingCreator TEST_INDEX_V2 = new UserTestIndex(2);
  protected static final IndexMappingCreator TEST_INDEX_WITH_UPDATED_MAPPING_V2 = new UserTestUpdatedMappingIndex();
  protected static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE_V1 = new UserTestWithTemplateIndex();
  protected static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2 =
    new UserTestWithTemplateUpdatedMappingIndex();

  protected ClientAndServer esMockServer;
  protected ObjectMapper objectMapper;
  protected OptimizeElasticsearchClient prefixAwareClient;
  protected OptimizeIndexNameService indexNameService;
  protected UpgradeExecutionDependencies upgradeDependencies;
  protected ElasticsearchMetadataService metadataService;
  protected ConfigurationService configurationService;
  protected UpgradeProcedure upgradeProcedure;

  @BeforeEach
  protected void setUp() throws Exception {
    this.configurationService = createDefaultConfiguration();
    final ElasticsearchConnectionNodeConfiguration elasticConfig =
      this.configurationService.getFirstElasticsearchConnectionNode();

    this.esMockServer = createElasticMock(elasticConfig);
    elasticConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
    elasticConfig.setHttpPort(IntegrationTestConfigurationUtil.getElasticsearchMockServerPort());

    setUpUpgradeDependenciesWithConfiguration(configurationService);
    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
    initSchema(Collections.singletonList(METADATA_INDEX));
    setMetadataVersion(FROM_VERSION);

    prefixAwareClient.setSnapshotInProgressRetryDelaySeconds(1);
  }

  protected void setUpUpgradeDependenciesWithConfiguration(ConfigurationService configurationService) {
    this.upgradeDependencies =
      UpgradeUtil.createUpgradeDependenciesWithAConfigurationService(configurationService);
    this.objectMapper = upgradeDependencies.getObjectMapper();
    this.prefixAwareClient = upgradeDependencies.getEsClient();
    this.indexNameService = upgradeDependencies.getIndexNameService();
    this.metadataService = upgradeDependencies.getMetadataService();
    this.upgradeProcedure = new UpgradeProcedure(
      prefixAwareClient,
      new UpgradeValidationService(),
      createSchemaUpgradeClient(upgradeDependencies),
      new UpgradeStepLogService()
    );
  }

  @AfterEach
  public void after() throws Exception {
    cleanAllDataFromElasticsearch();
    deleteEnvConfig();
    this.esMockServer.close();
  }

  protected void initSchema(List<IndexMappingCreator> mappingCreators) {
    final ElasticSearchSchemaManager elasticSearchSchemaManager = new ElasticSearchSchemaManager(
      metadataService, createDefaultConfiguration(), indexNameService, mappingCreators
    );
    elasticSearchSchemaManager.initializeSchema(prefixAwareClient);
  }

  protected void setMetadataVersion(String version) {
    metadataService.upsertMetadata(prefixAwareClient, version);
  }

  protected String getMetadataVersion() {
    return metadataService.getSchemaVersion(prefixAwareClient)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Could not obtain current schema version!"));
  }

  @SneakyThrows
  protected void createOptimizeIndexWithTypeAndVersion(DefaultIndexMappingCreator indexMapping, int version) {
    final String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    final String indexName = getVersionedIndexName(indexMapping.getIndexName(), version);
    final Settings indexSettings = createIndexSettings(indexMapping);

    CreateIndexRequest request = new CreateIndexRequest(indexName);
    request.alias(new Alias(aliasName));
    request.settings(indexSettings);
    request.mapping(indexMapping.getSource());
    indexMapping.setDynamic("false");
    prefixAwareClient.getHighLevelClient().indices().create(request, prefixAwareClient.requestOptions());
  }

  @SneakyThrows
  protected void executeBulk(final String bulkPayload) {
    final Request request = new Request(HttpPost.METHOD_NAME, "/_bulk");
    final HttpEntity entity = new NStringEntity(
      UpgradeUtil.readClasspathFileAsString(bulkPayload),
      ContentType.APPLICATION_JSON
    );
    request.setEntity(entity);
    prefixAwareClient.getLowLevelClient().performRequest(request);
    prefixAwareClient.refresh(new RefreshRequest("*"));
  }

  protected String getIndexNameWithVersion(final IndexMappingCreator testIndexV1) {
    return indexNameService.getOptimizeIndexNameWithVersion(testIndexV1);
  }

  protected String getVersionedIndexName(final String indexName, final int version) {
    return OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
      indexNameService.getOptimizeIndexAliasForIndex(indexName), String.valueOf(version)
    );
  }

  protected void cleanAllDataFromElasticsearch() {
    prefixAwareClient.deleteIndexByRawIndexNames("_all");
  }

  @SneakyThrows
  protected GetIndexResponse getIndicesForMapping(final IndexMappingCreator mapping) {
    return prefixAwareClient.getHighLevelClient().indices().get(
      new GetIndexRequest(indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(mapping)),
      prefixAwareClient.requestOptions()
    );
  }

  @SneakyThrows
  protected <T> Optional<T> getDocumentOfIndexByIdAs(final String indexName,
                                                     final String id,
                                                     final Class<T> valueType) {
    final GetResponse getResponse = prefixAwareClient.get(new GetRequest(indexName, id));
    return getResponse.isSourceEmpty()
      ? Optional.empty()
      : Optional.ofNullable(objectMapper.readValue(getResponse.getSourceAsString(), valueType));
  }

  protected <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> valueType) {
    prefixAwareClient.refresh(new RefreshRequest(indexName));
    final SearchHit[] searchHits = getAllDocumentsOfIndex(indexName);
    return Arrays
      .stream(searchHits)
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), valueType
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(toList());
  }

  @SneakyThrows
  protected SearchHit[] getAllDocumentsOfIndex(final String... indexNames) {
    final SearchResponse searchResponse =
      prefixAwareClient.search(new SearchRequest(indexNames).source(new SearchSourceBuilder().size(10000)));
    return searchResponse.getHits().getHits();
  }

  protected HttpRequest createUpdateLogUpsertRequest(final UpgradeStep upgradeStep) {
    final String indexNameWithVersion = indexNameService.getOptimizeIndexNameWithVersion(upgradeStep.getIndex());
    return request()
      .withPath(
        "/" + getLogIndexAlias() + "/_update/" + TO_VERSION + "_" + upgradeStep.getType() + "_" + indexNameWithVersion
      )
      .withMethod(POST);
  }

  protected HttpRequest createIndexDeleteRequest(final String versionedIndexName) {
    return request().withPath("/" + versionedIndexName).withMethod(DELETE);
  }

  private ClientAndServer createElasticMock(final ElasticsearchConnectionNodeConfiguration elasticConfig) {
    return MockServerUtil.createProxyMockServer(
      elasticConfig.getHost(),
      elasticConfig.getHttpPort(),
      IntegrationTestConfigurationUtil.getElasticsearchMockServerPort()
    );
  }

  private String getLogIndexAlias() {
    return indexNameService.getOptimizeIndexAliasForIndex(UpdateLogEntryIndex.INDEX_NAME);
  }

  private Settings createIndexSettings(IndexMappingCreator indexMappingCreator) {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService, indexMappingCreator);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  protected static InsertDataStep buildInsertTestIndexDataStep(final IndexMappingCreator index) {
    return new InsertDataStep(index, UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json"));
  }

  protected static UpgradeStep buildDeleteTestIndexDataStep(final IndexMappingCreator index) {
    return new DeleteDataStep(index, QueryBuilders.termQuery("username", "admin"));
  }

  protected static UpdateDataStep buildUpdateTestIndexDataStep(final IndexMappingCreator index) {
    return new UpdateDataStep(
      index, termQuery("username", "admin"), "ctx._source.password = ctx._source.password + \"1\""
    );
  }

  protected void insertTestDocuments(final int amount) throws IOException {
    final String indexName = TEST_INDEX_V1.getIndexName();
    final BulkRequest bulkRequest = new BulkRequest();
    for (int i = 0; i < amount; i++) {
      bulkRequest.add(
        new IndexRequest(indexName)
          .source(String.format("{\"password\" : \"admin\",\"username\" : \"admin%d\"}", i), XContentType.JSON)
      );
    }
    prefixAwareClient.bulk(bulkRequest);
    prefixAwareClient.refresh(new RefreshRequest(indexName));
  }

  public void deleteAllDocsInIndex(final IndexMappingCreator index) {
    final DeleteByQueryRequest request = new DeleteByQueryRequest(indexNameService.getOptimizeIndexAliasForIndex(index))
      .setQuery(matchAllQuery())
      .setRefresh(true);

    try {
      prefixAwareClient.getHighLevelClient().deleteByQuery(request, prefixAwareClient.requestOptions());
    } catch (IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
        "Could not delete data in index " + indexNameService.getOptimizeIndexAliasForIndex(index),
        e
      );
    }
  }


}
