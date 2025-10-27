/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static io.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static io.camunda.optimize.upgrade.db.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
import static org.mockserver.model.HttpRequest.request;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.POST;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.DatabaseQueryWrapper;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.MetadataIndexOS;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.MetadataIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.test.it.extension.DatabaseIntegrationTestExtension;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.test.it.extension.MockServerUtil;
import io.camunda.optimize.upgrade.db.IndexLookupUtilIncludingTestIndices;
import io.camunda.optimize.upgrade.db.index.UpdateLogEntryIndex;
import io.camunda.optimize.upgrade.es.indices.RenameFieldTestIndexES;
import io.camunda.optimize.upgrade.es.indices.UserTestIndexES;
import io.camunda.optimize.upgrade.es.indices.UserTestUpdatedMappingIndexES;
import io.camunda.optimize.upgrade.es.indices.UserTestWithTemplateIndexES;
import io.camunda.optimize.upgrade.es.indices.UserTestWithTemplateUpdatedMappingIndexES;
import io.camunda.optimize.upgrade.main.UpgradeProcedure;
import io.camunda.optimize.upgrade.os.indices.RenameFieldTestIndexOS;
import io.camunda.optimize.upgrade.os.indices.UserTestIndexOS;
import io.camunda.optimize.upgrade.os.indices.UserTestUpdatedMappingIndexOS;
import io.camunda.optimize.upgrade.os.indices.UserTestWithTemplateIndexOS;
import io.camunda.optimize.upgrade.os.indices.UserTestWithTemplateUpdatedMappingIndexOS;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;

public abstract class AbstractUpgradeIT {

  @RegisterExtension
  @Order(1)
  public static DatabaseIntegrationTestExtension databaseIntegrationTestExtension =
      new DatabaseIntegrationTestExtension(getDatabaseTypeFromEnvVar());

  protected static MetadataIndex metadataIndex;
  protected static final String FROM_VERSION = "2.6.0";
  protected static final String INTERMEDIATE_VERSION = "2.6.1";
  protected static final String TO_VERSION = "2.7.0";
  protected static IndexMappingCreator testIndexV1;
  protected static IndexMappingCreator testIndexV2;
  protected static IndexMappingCreator testIndexWithUpdatedMappingV2;
  protected static IndexMappingCreator testIndexWithTemplateV1;
  protected static IndexMappingCreator testIndexWithTemplateUpdatedMappingV2;
  protected static IndexMappingCreator testIndexRenameField;
  protected ClientAndServer dbMockServer;
  protected ObjectMapper objectMapper;
  protected UpgradeExecutionDependencies upgradeDependencies;
  protected DatabaseMetadataService metadataService;
  protected ConfigurationService configurationService;
  protected UpgradeProcedure upgradeProcedure;

  public static boolean isElasticSearchUpgrade() {
    return databaseIntegrationTestExtension.getDatabaseVendor().equals(DatabaseType.ELASTICSEARCH);
  }

  @BeforeAll
  public static void beforeAll() {
    instantiateProperIndices();
  }

  public static DatabaseType getDatabaseTypeFromEnvVar() {
    final Optional<String> envVarValue =
        Optional.ofNullable(System.getenv(CAMUNDA_OPTIMIZE_DATABASE));
    return envVarValue
        .map(db -> DatabaseType.valueOf(db.toUpperCase(Locale.ROOT)))
        .orElse(DatabaseType.ELASTICSEARCH);
  }

  protected static InsertDataStep buildInsertTestIndexDataStep(final IndexMappingCreator index) {
    return applyLookupSkip(
        new InsertDataStep(
            index, UpgradeUtil.readClasspathFileAsString("steps/insert_data/test_data.json")));
  }

  protected static UpgradeStep buildDeleteTestIndexDataStep(final IndexMappingCreator index) {
    return applyLookupSkip(
        new DeleteDataStep(
            index,
            new DatabaseQueryWrapper(
                Query.of(q -> q.term(t -> t.field("username").value("admin"))),
                QueryDSL.term("username", "admin"))));
  }

  protected static UpdateDataStep buildUpdateTestIndexDataStep(final IndexMappingCreator index) {
    return applyLookupSkip(
        new UpdateDataStep(
            index,
            new DatabaseQueryWrapper(
                Query.of(q -> q.term(t -> t.field("username").value("admin"))),
                QueryDSL.term("username", "admin")),
            "ctx._source.password = ctx._source.password + \"1\""));
  }

  protected static <T extends UpgradeStep> T applyLookupSkip(final T upgradeStep) {
    upgradeStep.setSkipIndexConversion(true);
    return upgradeStep;
  }

  @BeforeEach
  protected void setUp() throws Exception {
    configurationService = createDefaultConfiguration();
    final DatabaseConnectionNodeConfiguration dbConfig =
        isElasticSearchUpgrade()
            ? configurationService.getElasticSearchConfiguration().getFirstConnectionNode()
            : configurationService.getOpenSearchConfiguration().getFirstConnectionNode();

    dbMockServer = databaseIntegrationTestExtension.useDbMockServer();
    dbConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
    dbConfig.setHttpPort(IntegrationTestConfigurationUtil.getDatabaseMockServerPort());

    setUpUpgradeDependenciesWithConfiguration(configurationService);
    // To speed up testing
    getPrefixAwareClient().setSnapshotInProgressRetryDelaySeconds(2);
    cleanAllDataFromDatabase();
    createEmptyEnvConfig();
    initSchema(Collections.singletonList(metadataIndex));
    setMetadataVersion(FROM_VERSION);
  }

  private static void instantiateProperIndices() {
    if (!isElasticSearchUpgrade()) {
      metadataIndex = new MetadataIndexOS();
      testIndexV1 = new UserTestIndexOS(1);
      testIndexV2 = new UserTestIndexOS(2);
      testIndexWithUpdatedMappingV2 = new UserTestUpdatedMappingIndexOS();
      testIndexWithTemplateV1 = new UserTestWithTemplateIndexOS();
      testIndexWithTemplateUpdatedMappingV2 = new UserTestWithTemplateUpdatedMappingIndexOS();
      testIndexRenameField = new RenameFieldTestIndexOS();
    } else {
      metadataIndex = new MetadataIndexES();
      testIndexV1 = new UserTestIndexES(1);
      testIndexV2 = new UserTestIndexES(2);
      testIndexWithUpdatedMappingV2 = new UserTestUpdatedMappingIndexES();
      testIndexWithTemplateV1 = new UserTestWithTemplateIndexES();
      testIndexWithTemplateUpdatedMappingV2 = new UserTestWithTemplateUpdatedMappingIndexES();
      testIndexRenameField = new RenameFieldTestIndexES();
    }
  }

  protected void setUpUpgradeDependenciesWithConfiguration(
      final ConfigurationService configurationService) {
    upgradeDependencies =
        UpgradeUtil.createUpgradeDependenciesWithAConfigurationService(
            databaseIntegrationTestExtension.getDatabaseVendor(), configurationService);
    objectMapper = upgradeDependencies.objectMapper();
    metadataService = upgradeDependencies.metadataService();
    upgradeProcedure =
        new UpgradeProcedure(
            getPrefixAwareClient(),
            new UpgradeValidationService(),
            createSchemaUpgradeClient(upgradeDependencies),
            new UpgradeStepLogService());
  }

  @AfterEach
  public void after() throws Exception {
    cleanAllDataFromDatabase();
    deleteEnvConfig();
  }

  protected void initSchema(
      final List<IndexMappingCreator<IndexSettings.Builder>> mappingCreators) {
    final DatabaseSchemaManager schemaManager;
    if (!isElasticSearchUpgrade()) {
      schemaManager =
          new OpenSearchSchemaManager(
              (OpenSearchMetadataService) metadataService,
              createDefaultConfiguration(),
              getIndexNameService(),
              mappingCreators.stream()
                  .map(
                      index ->
                          (IndexMappingCreator<
                                  org.opensearch.client.opensearch.indices.IndexSettings.Builder>)
                              IndexLookupUtilIncludingTestIndices.convertIndexForDatabase(
                                  index, DatabaseType.OPENSEARCH))
                  .toList());

    } else {
      schemaManager =
          new ElasticSearchSchemaManager(
              (ElasticSearchMetadataService) metadataService,
              createDefaultConfiguration(),
              getIndexNameService(),
              mappingCreators);
    }
    databaseIntegrationTestExtension.initSchema(schemaManager);
  }

  protected String getMetadataVersion() {
    try {
      return (String)
          metadataService
              .getSchemaVersion(getPrefixAwareClient())
              .orElseThrow(
                  () ->
                      new OptimizeIntegrationTestException(
                          "Could not obtain current schema version!"));
    } catch (final Throwable e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected void setMetadataVersion(final String version) {
    metadataService.upsertMetadata(getPrefixAwareClient(), version);
  }

  protected String getIndexNameWithVersion(final UpgradeStep upgradeStep) {
    if (upgradeStep instanceof DeleteIndexIfExistsStep) {
      return ((DeleteIndexIfExistsStep) upgradeStep).getVersionedIndexName();
    } else {
      return getIndexNameService().getOptimizeIndexNameWithVersion(upgradeStep.getIndex());
    }
  }

  protected void createOptimizeIndexWithTypeAndVersion(
      final DefaultIndexMappingCreator indexMapping, final int version) {
    final String aliasName =
        getIndexNameService().getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    final String indexName = getVersionedIndexName(indexMapping.getIndexName(), version);
    try {
      databaseIntegrationTestExtension.createIndex(indexName, aliasName, indexMapping);
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected void executeBulk(final String bulkPayloadFilePath) {
    final String bulkPayload = UpgradeUtil.readClasspathFileAsString(bulkPayloadFilePath);
    try {
      databaseIntegrationTestExtension.performLowLevelBulkRequest(
          HttpPost.METHOD_NAME, "/_bulk", bulkPayload);
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
    getPrefixAwareClient().refresh("*");
  }

  protected String getIndexNameWithVersion(final IndexMappingCreator testIndexV1) {
    return getIndexNameService().getOptimizeIndexNameWithVersion(testIndexV1);
  }

  protected String getVersionedIndexName(final String indexName, final int version) {
    return OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
        getIndexNameService().getOptimizeIndexAliasForIndex(indexName), String.valueOf(version));
  }

  protected void cleanAllDataFromDatabase() {
    getPrefixAwareClient().deleteAllIndexes();
  }

  protected Set<String> getIndicesForMapping(final IndexMappingCreator mapping) {
    try {
      return getPrefixAwareClient()
          .getAllIndicesForAlias(getIndexNameService().getOptimizeIndexAliasForIndex(mapping));
    } catch (final IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  protected <T> Optional<T> getDocumentOfIndexByIdAs(
      final String indexName, final String id, final Class<T> valueType) {
    return databaseIntegrationTestExtension.getDatabaseEntryById(indexName, id, valueType);
  }

  protected <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> valueType) {
    getPrefixAwareClient().refresh(indexName);
    return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(indexName, valueType);
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
        .withMethod(POST.name());
  }

  protected HttpRequest createIndexDeleteRequest(final String versionedIndexName) {
    return request().withPath("/" + versionedIndexName).withMethod(DELETE.name());
  }

  private String getLogIndexAlias() {
    return getIndexNameService().getOptimizeIndexAliasForIndex(UpdateLogEntryIndex.INDEX_NAME);
  }

  protected void insertTestDocuments(final int amount) throws IOException {
    final String indexName = testIndexV1.getIndexName();
    databaseIntegrationTestExtension.insertTestDocuments(
        amount,
        indexName,
        """
            {"password" : "admin","username" : "admin%d"}
            """);
  }

  public void deleteAllDocsInIndex(final IndexMappingCreator index) {
    databaseIntegrationTestExtension.deleteAllDocumentsInIndex(
        getIndexNameService().getOptimizeIndexAliasForIndex(index));
  }

  protected DatabaseClient getPrefixAwareClient() {
    return upgradeDependencies.databaseClient();
  }

  protected OptimizeIndexNameService getIndexNameService() {
    return upgradeDependencies.indexNameService();
  }
}
