/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static io.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.POST;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.mockserver.model.HttpRequest.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchIndexSettingsBuilder;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.test.it.extension.MockServerUtil;
import io.camunda.optimize.upgrade.indices.UserTestIndex;
import io.camunda.optimize.upgrade.indices.UserTestUpdatedMappingIndex;
import io.camunda.optimize.upgrade.indices.UserTestWithTemplateIndex;
import io.camunda.optimize.upgrade.indices.UserTestWithTemplateUpdatedMappingIndex;
import io.camunda.optimize.upgrade.main.UpgradeProcedure;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.service.UpgradeStepLogService;
import io.camunda.optimize.upgrade.service.UpgradeValidationService;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.document.DeleteDataStep;
import io.camunda.optimize.upgrade.steps.document.InsertDataStep;
import io.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import io.camunda.optimize.upgrade.util.UpgradeUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
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
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;

public abstract class AbstractUpgradeIT {

  protected static final MetadataIndexES METADATA_INDEX = new MetadataIndexES();

  protected static final String FROM_VERSION = "2.6.0";
  protected static final String INTERMEDIATE_VERSION = "2.6.1";
  protected static final String TO_VERSION = "2.7.0";

  protected static final IndexMappingCreator TEST_INDEX_V1 = new UserTestIndex(1);
  protected static final IndexMappingCreator TEST_INDEX_V2 = new UserTestIndex(2);
  protected static final IndexMappingCreator TEST_INDEX_WITH_UPDATED_MAPPING_V2 =
      new UserTestUpdatedMappingIndex();
  protected static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE_V1 =
      new UserTestWithTemplateIndex();
  protected static final IndexMappingCreator TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2 =
      new UserTestWithTemplateUpdatedMappingIndex();

  protected ClientAndServer dbMockServer;
  protected ObjectMapper objectMapper;
  protected OptimizeElasticsearchClient prefixAwareClient;
  protected OptimizeIndexNameService indexNameService;
  protected UpgradeExecutionDependencies upgradeDependencies;
  protected ElasticSearchMetadataService metadataService;
  protected ConfigurationService configurationService;
  protected UpgradeProcedure upgradeProcedure;

  protected static InsertDataStep buildInsertTestIndexDataStep(final IndexMappingCreator index) {
    return new InsertDataStep(
        index, UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json"));
  }

  protected static UpgradeStep buildDeleteTestIndexDataStep(final IndexMappingCreator index) {
    return new DeleteDataStep(index, QueryBuilders.termQuery("username", "admin"));
  }

  protected static UpdateDataStep buildUpdateTestIndexDataStep(final IndexMappingCreator index) {
    return new UpdateDataStep(
        index,
        termQuery("username", "admin"),
        "ctx._source.password = ctx._source.password + \"1\"");
  }

  @BeforeEach
  protected void setUp() throws Exception {
    configurationService = createDefaultConfiguration();
    final DatabaseConnectionNodeConfiguration elasticConfig =
        configurationService.getElasticSearchConfiguration().getFirstConnectionNode();

    dbMockServer = createElasticMock(elasticConfig);
    elasticConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
    elasticConfig.setHttpPort(IntegrationTestConfigurationUtil.getDatabaseMockServerPort());

    setUpUpgradeDependenciesWithConfiguration(configurationService);
    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
    initSchema(Collections.singletonList(METADATA_INDEX));
    setMetadataVersion(FROM_VERSION);

    prefixAwareClient.setSnapshotInProgressRetryDelaySeconds(1);
  }

  protected void setUpUpgradeDependenciesWithConfiguration(
      final ConfigurationService configurationService) {
    upgradeDependencies =
        UpgradeUtil.createUpgradeDependenciesWithAConfigurationService(configurationService);
    objectMapper = upgradeDependencies.objectMapper();
    prefixAwareClient = upgradeDependencies.esClient();
    indexNameService = upgradeDependencies.indexNameService();
    metadataService = upgradeDependencies.metadataService();
    upgradeProcedure =
        new UpgradeProcedure(
            prefixAwareClient,
            new UpgradeValidationService(),
            createSchemaUpgradeClient(upgradeDependencies),
            new UpgradeStepLogService());
  }

  @AfterEach
  public void after() throws Exception {
    cleanAllDataFromElasticsearch();
    deleteEnvConfig();
    dbMockServer.close();
  }

  protected void initSchema(final List<IndexMappingCreator<XContentBuilder>> mappingCreators) {
    final ElasticSearchSchemaManager elasticSearchSchemaManager =
        new ElasticSearchSchemaManager(
            metadataService, createDefaultConfiguration(), indexNameService, mappingCreators);
    elasticSearchSchemaManager.initializeSchema(prefixAwareClient);
  }

  protected String getMetadataVersion() {
    return metadataService
        .getSchemaVersion(prefixAwareClient)
        .orElseThrow(
            () -> new OptimizeIntegrationTestException("Could not obtain current schema version!"));
  }

  protected void setMetadataVersion(final String version) {
    metadataService.upsertMetadata(prefixAwareClient, version);
  }

  @SneakyThrows
  protected void createOptimizeIndexWithTypeAndVersion(
      final DefaultIndexMappingCreator indexMapping, final int version) {
    final String aliasName =
        indexNameService.getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    final String indexName = getVersionedIndexName(indexMapping.getIndexName(), version);
    final Settings indexSettings = createIndexSettings(indexMapping);

    final CreateIndexRequest request = new CreateIndexRequest(indexName);
    request.alias(new Alias(aliasName).writeIndex(true));
    request.settings(indexSettings);
    request.mapping(indexMapping.getSource());
    indexMapping.setDynamic("false");
    prefixAwareClient
        .getHighLevelClient()
        .indices()
        .create(request, prefixAwareClient.requestOptions());
  }

  @SneakyThrows
  protected void executeBulk(final String bulkPayload) {
    final Request request = new Request(HttpPost.METHOD_NAME, "/_bulk");
    final HttpEntity entity =
        new NStringEntity(
            UpgradeUtil.readClasspathFileAsString(bulkPayload), ContentType.APPLICATION_JSON);
    request.setEntity(entity);
    prefixAwareClient.getLowLevelClient().performRequest(request);
    prefixAwareClient.refresh(new RefreshRequest("*"));
  }

  protected String getIndexNameWithVersion(final IndexMappingCreator testIndexV1) {
    return indexNameService.getOptimizeIndexNameWithVersion(testIndexV1);
  }

  protected String getIndexNameWithVersion(final UpgradeStep upgradeStep) {
    if (upgradeStep instanceof DeleteIndexIfExistsStep) {
      return ((DeleteIndexIfExistsStep) upgradeStep).getVersionedIndexName();
    } else {
      return indexNameService.getOptimizeIndexNameWithVersion(upgradeStep.getIndex());
    }
  }

  protected String getVersionedIndexName(final String indexName, final int version) {
    return OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
        indexNameService.getOptimizeIndexAliasForIndex(indexName), String.valueOf(version));
  }

  protected void cleanAllDataFromElasticsearch() {
    prefixAwareClient.deleteIndexByRawIndexNames("_all");
  }

  @SneakyThrows
  protected GetIndexResponse getIndicesForMapping(final IndexMappingCreator mapping) {
    return prefixAwareClient
        .getHighLevelClient()
        .indices()
        .get(
            new GetIndexRequest(
                indexNameService.getOptimizeIndexNameWithVersionForAllIndicesOf(mapping)),
            prefixAwareClient.requestOptions());
  }

  @SneakyThrows
  protected <T> Optional<T> getDocumentOfIndexByIdAs(
      final String indexName, final String id, final Class<T> valueType) {
    final GetResponse getResponse = prefixAwareClient.get(new GetRequest(indexName, id));
    return getResponse.isSourceEmpty()
        ? Optional.empty()
        : Optional.ofNullable(objectMapper.readValue(getResponse.getSourceAsString(), valueType));
  }

  protected <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> valueType) {
    prefixAwareClient.refresh(new RefreshRequest(indexName));
    final SearchHit[] searchHits = getAllDocumentsOfIndex(indexName);
    return Arrays.stream(searchHits)
        .map(
            doc -> {
              try {
                return objectMapper.readValue(doc.getSourceAsString(), valueType);
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  @SneakyThrows
  protected SearchHit[] getAllDocumentsOfIndex(final String... indexNames) {
    final SearchResponse searchResponse =
        prefixAwareClient.search(
            new SearchRequest(indexNames).source(new SearchSourceBuilder().size(10000)));
    return searchResponse.getHits().getHits();
  }

  protected HttpRequest createUpdateLogUpsertRequest(final UpgradeStep upgradeStep) {
    final String indexNameWithVersion = getIndexNameWithVersion(upgradeStep);
    return request()
        .withPath(
            "/"
                + getLogIndexAlias()
                + "/_update/"
                + TO_VERSION
                + "_"
                + upgradeStep.getType()
                + "_"
                + indexNameWithVersion)
        .withMethod(POST);
  }

  protected HttpRequest createIndexDeleteRequest(final String versionedIndexName) {
    return request().withPath("/" + versionedIndexName).withMethod(DELETE);
  }

  protected ClientAndServer createElasticMock(
      final DatabaseConnectionNodeConfiguration elasticConfig) {
    return MockServerUtil.createProxyMockServer(
        elasticConfig.getHost(),
        elasticConfig.getHttpPort(),
        IntegrationTestConfigurationUtil.getDatabaseMockServerPort());
  }

  private String getLogIndexAlias() {
    return indexNameService.getOptimizeIndexAliasForIndex(
        DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME);
  }

  private Settings createIndexSettings(final IndexMappingCreator indexMappingCreator) {
    try {
      return ElasticSearchIndexSettingsBuilder.buildAllSettings(
          configurationService, indexMappingCreator);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not create index settings");
    }
  }

  protected void insertTestDocuments(final int amount) throws IOException {
    final String indexName = TEST_INDEX_V1.getIndexName();
    final BulkRequest bulkRequest = new BulkRequest();
    for (int i = 0; i < amount; i++) {
      bulkRequest.add(
          new IndexRequest(indexName)
              .source(
                  String.format("{\"password\" : \"admin\",\"username\" : \"admin%d\"}", i),
                  XContentType.JSON));
    }
    prefixAwareClient.bulk(bulkRequest);
    prefixAwareClient.refresh(new RefreshRequest(indexName));
  }

  public void deleteAllDocsInIndex(final IndexMappingCreator index) {
    final DeleteByQueryRequest request =
        new DeleteByQueryRequest(indexNameService.getOptimizeIndexAliasForIndex(index))
            .setQuery(matchAllQuery())
            .setRefresh(true);

    try {
      prefixAwareClient
          .getHighLevelClient()
          .deleteByQuery(request, prefixAwareClient.requestOptions());
    } catch (final IOException | ElasticsearchStatusException e) {
      throw new OptimizeIntegrationTestException(
          "Could not delete data in index " + indexNameService.getOptimizeIndexAliasForIndex(index),
          e);
    }
  }
}
