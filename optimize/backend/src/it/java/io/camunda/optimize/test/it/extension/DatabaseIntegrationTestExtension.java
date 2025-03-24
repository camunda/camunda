/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.it.extension.db.DatabaseTestService;
import io.camunda.optimize.test.it.extension.db.ElasticsearchDatabaseTestService;
import io.camunda.optimize.test.it.extension.db.OpenSearchDatabaseTestService;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;

public class DatabaseIntegrationTestExtension implements BeforeEachCallback, AfterEachCallback {

  private final DatabaseTestService databaseTestService;

  public DatabaseIntegrationTestExtension(final DatabaseType databaseType) {
    this(null, true, databaseType);
  }

  public DatabaseIntegrationTestExtension() {
    this(true);
  }

  public DatabaseIntegrationTestExtension(final boolean haveToClean) {
    this(null, haveToClean);
  }

  private DatabaseIntegrationTestExtension(
      final String customIndexPrefix, final boolean haveToClean) {
    this(customIndexPrefix, haveToClean, IntegrationTestConfigurationUtil.getDatabaseType());
  }

  private DatabaseIntegrationTestExtension(
      final String customIndexPrefix, final boolean haveToClean, final DatabaseType databaseType) {
    if (databaseType == null || databaseType.equals(DatabaseType.ELASTICSEARCH)) {
      databaseTestService = new ElasticsearchDatabaseTestService(customIndexPrefix, haveToClean);
    } else {
      databaseTestService = new OpenSearchDatabaseTestService(customIndexPrefix, haveToClean);
    }
  }

  public void cleanSnapshots(final String snapshotRepositoryName) {
    databaseTestService.cleanSnapshots(snapshotRepositoryName);
  }

  public void createRepoSnapshot(final String snapshotRepositoryName) {
    databaseTestService.createRepoSnapshot(snapshotRepositoryName);
  }

  public void createSnapshot(
      final String snapshotRepositoryName, final String snapshotName, final String[] indexNames) {
    databaseTestService.createSnapshot(snapshotRepositoryName, snapshotName, indexNames);
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    databaseTestService.beforeEach();
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    databaseTestService.afterEach();
  }

  public ClientAndServer useDbMockServer() {
    return databaseTestService.useDBMockServer();
  }

  public void refreshAllOptimizeIndices() {
    databaseTestService.refreshAllOptimizeIndices();
  }

  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    return databaseTestService.getAllDocumentsOfIndexAs(indexName, type);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return databaseTestService.getDatabaseClient().getIndexNameService();
  }

  public boolean indexExists(final String indexOrAliasName) {
    return databaseTestService.indexExistsCheckWithApplyingOptimizePrefix(indexOrAliasName);
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME, ProcessDefinitionOptimizeDto.class);
  }

  public List<ProcessInstanceDto> getAllProcessInstances() {
    return getAllDocumentsOfIndexAs(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class);
  }

  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    databaseTestService.deleteAllZeebeRecordsForPrefix(zeebeRecordPrefix);
  }

  public void deleteAllOtherZeebeRecordsWithPrefix(
      final String zeebeRecordPrefix, final String recordsToKeep) {
    databaseTestService.deleteAllOtherZeebeRecordsWithPrefix(zeebeRecordPrefix, recordsToKeep);
  }

  public void updateZeebeRecordsWithPositionForPrefix(
      final String zeebeRecordPrefix,
      final String indexName,
      final long position,
      final String updateScript) {
    databaseTestService.updateZeebeRecordsWithPositionForPrefix(
        zeebeRecordPrefix, indexName, position, updateScript);
  }

  public void updateZeebeProcessRecordsOfBpmnElementTypeForPrefix(
      final String zeebeRecordPrefix,
      final BpmnElementType bpmnElementType,
      final String updateScript) {
    databaseTestService.updateZeebeRecordsOfBpmnElementTypeForPrefix(
        zeebeRecordPrefix, bpmnElementType, updateScript);
  }

  public void updateZeebeRecordsForPrefix(
      final String zeebeRecordPrefix, final String indexName, final String updateScript) {
    databaseTestService.updateZeebeRecordsForPrefix(zeebeRecordPrefix, indexName, updateScript);
  }

  public void update(final String indexName, final String entityId, final ScriptData script) {
    databaseTestService.getDatabaseClient().update(indexName, entityId, script);
  }

  public long countRecordsByQuery(final TermsQueryContainer queryContainer, final String index) {
    return databaseTestService.countRecordsByQuery(queryContainer, index);
  }

  public List<String> getAllIndicesWithReadOnlyAlias(
      final String externalProcessVariableIndexName) {
    final String aliasNameWithPrefix =
        getIndexNameService().getOptimizeIndexAliasForIndex(externalProcessVariableIndexName);
    return databaseTestService.getAllIndicesWithReadOnlyAlias(aliasNameWithPrefix);
  }

  public <T> List<T> getZeebeExportedRecordsByQuery(
      final String exportIndex, final TermsQueryContainer query, final Class<T> zeebeRecordClass) {
    return databaseTestService.getZeebeExportedRecordsByQuery(exportIndex, query, zeebeRecordClass);
  }

  public boolean zeebeIndexExists(final String expectedIndex) {
    return databaseTestService.indexExistsCheckWithoutApplyingOptimizePrefix(expectedIndex);
  }

  public <T> Optional<T> getDatabaseEntryById(
      final String indexName, final String entryId, final Class<T> type) {
    return databaseTestService.getDatabaseEntryById(indexName, entryId, type);
  }

  public DatabaseType getDatabaseVendor() {
    return databaseTestService.getDatabaseVendor();
  }

  public void updateProcessInstanceNestedDocLimit(
      final String processDefinitionKey,
      final int nestedDocLimit,
      final ConfigurationService configurationService) {
    databaseTestService.updateProcessInstanceNestedDocLimit(
        processDefinitionKey, nestedDocLimit, configurationService);
  }

  public void createIndex(
      final String optimizeIndexNameWithVersion,
      final String optimizeIndexAliasForIndex,
      final DefaultIndexMappingCreator mapping)
      throws IOException {
    createIndex(optimizeIndexNameWithVersion, optimizeIndexAliasForIndex, mapping, true);
  }

  public List<String> getAllIndicesWithWriteAlias(final String indexName) {
    final String aliasNameWithPrefix =
        getIndexNameService().getOptimizeIndexAliasForIndex(indexName);
    return databaseTestService.getAllIndicesWithWriteAlias(aliasNameWithPrefix);
  }

  public void deleteAllDocumentsInIndex(final String optimizeIndexAliasForIndex) {
    databaseTestService.deleteAllDocumentsInIndex(optimizeIndexAliasForIndex);
  }

  public void insertTestDocuments(
      final int amount, final String indexName, final String documentContentAsJson)
      throws IOException {
    databaseTestService.insertTestDocuments(amount, indexName, documentContentAsJson);
  }

  public void performLowLevelBulkRequest(
      final String methodName, final String endpoint, final String bulkPayload) throws IOException {
    databaseTestService.performLowLevelBulkRequest(methodName, endpoint, bulkPayload);
  }

  public void initSchema(final DatabaseSchemaManager schemaManager) {
    databaseTestService.initSchema(schemaManager);
  }

  public Map<String, ?> getMappingFields(final String indexName) throws IOException {
    return databaseTestService.getMappingFields(indexName);
  }

  public boolean indexExists(final String versionedIndexName, final Boolean addMappingFeatures) {
    return databaseTestService.indexExists(versionedIndexName, addMappingFeatures);
  }

  public void createIndex(
      final String optimizeIndexNameWithVersion,
      final String optimizeIndexAliasForIndex,
      final DefaultIndexMappingCreator mapping,
      final Boolean isWriteIndex)
      throws IOException {
    createIndex(
        optimizeIndexNameWithVersion, Map.of(optimizeIndexAliasForIndex, isWriteIndex), mapping);
  }

  public void createIndex(
      final String optimizeIndexNameWithVersion,
      final Map<String, Boolean> aliases,
      final DefaultIndexMappingCreator mapping)
      throws IOException {
    databaseTestService.createIndex(optimizeIndexNameWithVersion, aliases, mapping);
  }

  public boolean templateExists(final String optimizeIndexTemplateNameWithVersion)
      throws IOException {
    return databaseTestService.templateExists(optimizeIndexTemplateNameWithVersion);
  }

  public boolean isAliasReadOnly(final String readOnlyAliasForIndex) throws IOException {
    return databaseTestService.isAliasReadOnly(readOnlyAliasForIndex);
  }

  public String[] getIndexNames() {
    return databaseTestService.getIndexNames();
  }
}
