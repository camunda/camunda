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
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DashboardShareIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.LicenseIndex;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ReportShareIndex;
import org.camunda.optimize.service.es.schema.index.TenantIndex;
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;

public abstract class AbstractUpgradeIT {

  protected static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  protected static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  protected static final ImportIndexIndex IMPORT_INDEX_INDEX = new ImportIndexIndex();
  protected static final DecisionInstanceIndex DECISION_INSTANCE_INDEX = new DecisionInstanceIndex();
  protected static final CollectionIndex COLLECTION_INDEX = new CollectionIndex();
  protected static final TimestampBasedImportIndex TIMESTAMP_BASED_IMPORT_INDEX = new TimestampBasedImportIndex();
  protected static final AlertIndex ALERT_INDEX = new AlertIndex();
  protected static final DecisionDefinitionIndex DECISION_DEFINITION_INDEX = new DecisionDefinitionIndex();
  protected static final ProcessInstanceIndex PROCESS_INSTANCE_INDEX = new ProcessInstanceIndex();
  protected static final MetadataIndex METADATA_INDEX = new MetadataIndex();
  protected static final TerminatedUserSessionIndex TERMINATED_USER_SESSION_INDEX = new TerminatedUserSessionIndex();
  protected static final ProcessDefinitionIndex PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndex();
  protected static final CombinedReportIndex COMBINED_REPORT_INDEX = new CombinedReportIndex();
  protected static final ReportShareIndex REPORT_SHARE_INDEX = new ReportShareIndex();
  protected static final LicenseIndex LICENSE_INDEX = new LicenseIndex();
  protected static final TenantIndex TENANT_INDEX = new TenantIndex();
  protected static final DashboardIndex DASHBOARD_INDEX = new DashboardIndex();
  protected static final DashboardShareIndex DASHBOARD_SHARE_INDEX = new DashboardShareIndex();

  protected static final List<StrictIndexMappingCreator> ALL_INDICES = Arrays.asList(
    METADATA_INDEX,
    SINGLE_DECISION_REPORT_INDEX,
    SINGLE_PROCESS_REPORT_INDEX,
    IMPORT_INDEX_INDEX,
    DECISION_INSTANCE_INDEX,
    COLLECTION_INDEX,
    TIMESTAMP_BASED_IMPORT_INDEX,
    ALERT_INDEX,
    DECISION_DEFINITION_INDEX,
    PROCESS_INSTANCE_INDEX,
    TERMINATED_USER_SESSION_INDEX,
    PROCESS_DEFINITION_INDEX,
    COMBINED_REPORT_INDEX,
    REPORT_SHARE_INDEX,
    LICENSE_INDEX,
    TENANT_INDEX,
    DASHBOARD_INDEX,
    DASHBOARD_SHARE_INDEX
  );

  protected static final String FROM_VERSION = "2.6.0";

  protected ObjectMapper objectMapper;
  protected OptimizeElasticsearchClient prefixAwareClient;
  protected OptimizeIndexNameService indexNameService;
  protected UpgradeExecutionDependencies upgradeDependencies;
  protected ConfigurationService configurationService;
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

  protected void setMetadataIndexVersionWithType(String version, String type) {
    metadataService.writeMetadata(prefixAwareClient, new MetadataDto(version), type);
  }

  protected void setMetadataIndexVersion(String version) {
    metadataService.writeMetadata(prefixAwareClient, new MetadataDto(version));
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

  protected void createAndPopulateOptimizeIndexWithTypeAndVersion(StrictIndexMappingCreator indexMapping,
                                                                  String type,
                                                                  int version) throws IOException {
    final String aliasName = indexNameService.getOptimizeIndexAliasForIndex(indexMapping.getIndexName());
    final String indexName =
      indexNameService.getOptimizeIndexNameForAliasAndVersion(indexNameService.getOptimizeIndexAliasForIndex(
        indexMapping.getIndexName()), String.valueOf(version));
    final Settings indexSettings = createIndexSettings();

    CreateIndexRequest request = new CreateIndexRequest(indexName);
    request.alias(new Alias(aliasName));
    request.settings(indexSettings);
    indexMapping.setDynamicMappingsValue("false");
    request.mapping(type, indexMapping.getSource());
    prefixAwareClient.getHighLevelClient().indices().create(request, RequestOptions.DEFAULT);

    final IndexRequest indexRequest = new IndexRequest(indexName, type);
    indexRequest.source(indexMapping.getSource());
    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    prefixAwareClient.getHighLevelClient().index(indexRequest, RequestOptions.DEFAULT);
  }

  private Settings createIndexSettings() {
    try {
      return IndexSettingsBuilder.buildAllSettings(configurationService);
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
