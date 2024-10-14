/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.it.extension.db.DatabaseTestService;
import io.camunda.optimize.test.it.extension.db.ElasticsearchDatabaseTestService;
import io.camunda.optimize.test.it.extension.db.OpenSearchDatabaseTestService;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;

@Slf4j
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

  public DatabaseIntegrationTestExtension(final String customIndexPrefix) {
    this(customIndexPrefix, true);
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

  public ObjectMapper getObjectMapper() {
    return databaseTestService.getObjectMapper();
  }

  public void refreshAllOptimizeIndices() {
    databaseTestService.refreshAllOptimizeIndices();
  }

  /**
   * This class adds a document entry to the database. Thereby, the entry is added to the optimize
   * index and the given type under the given id.
   *
   * <p>The object needs to be a POJO, which is then converted to json. Thus, the entry results in
   * every object member variable name is going to be mapped to the field name in ES and every
   * content of that variable is going to be the content of the field.
   *
   * @param indexName where the entry is added.
   * @param id under which the entry is added.
   * @param entry a POJO specifying field names and their contents.
   */
  public void addEntryToDatabase(final String indexName, final String id, final Object entry) {
    databaseTestService.addEntryToDatabase(indexName, id, entry);
  }

  public void addEntriesToDatabase(final String indexName, final Map<String, Object> idToEntryMap) {
    databaseTestService.addEntriesToDatabase(indexName, idToEntryMap);
  }

  public <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type) {
    return databaseTestService.getAllDocumentsOfIndexAs(indexName, type);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return databaseTestService.getDatabaseClient().getIndexNameService();
  }

  public Integer getDocumentCountOf(final String indexName) {
    return databaseTestService.getDocumentCountOf(indexName);
  }

  public void deleteAllOptimizeData() {
    databaseTestService.deleteAllOptimizeData();
  }

  @SneakyThrows
  public void deleteAllDecisionInstanceIndices() {
    databaseTestService.deleteAllIndicesContainingTerm(DECISION_INSTANCE_INDEX_PREFIX);
  }

  @SneakyThrows
  public void deleteAllProcessInstanceIndices() {
    databaseTestService.deleteAllIndicesContainingTerm(PROCESS_INSTANCE_INDEX_PREFIX);
  }

  public void deleteAllSingleProcessReports() {
    databaseTestService.deleteAllSingleProcessReports();
  }

  public void deleteTerminatedSessionsIndex() {
    databaseTestService.deleteTerminatedSessionsIndex();
  }

  public void deleteAllVariableUpdateInstanceIndices() {
    databaseTestService.deleteAllVariableUpdateInstanceIndices();
  }

  public void deleteAllExternalVariableIndices() {
    databaseTestService.deleteAllExternalVariableIndices();
  }

  public boolean indexExists(final String indexOrAliasName) {
    return databaseTestService.indexExistsCheckWithApplyingOptimizePrefix(indexOrAliasName);
  }

  public void cleanAndVerify() {
    databaseTestService.cleanAndVerifyDatabase();
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions() {
    return getAllDocumentsOfIndexAs(
        DECISION_DEFINITION_INDEX_NAME, DecisionDefinitionOptimizeDto.class);
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return getAllDocumentsOfIndexAs(
        PROCESS_DEFINITION_INDEX_NAME, ProcessDefinitionOptimizeDto.class);
  }

  public List<DecisionInstanceDto> getAllDecisionInstances() {
    return getAllDocumentsOfIndexAs(DECISION_INSTANCE_MULTI_ALIAS, DecisionInstanceDto.class);
  }

  public List<ProcessInstanceDto> getAllProcessInstances() {
    return getAllDocumentsOfIndexAs(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class);
  }

  @SneakyThrows
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(
      final String dbType, final String engine) {
    return databaseTestService.getLastImportTimestampOfTimestampBasedImportIndex(dbType, engine);
  }

  @SneakyThrows
  public List<VariableUpdateInstanceDto> getAllStoredVariableUpdateInstanceDtos() {
    return getAllDocumentsOfIndexAs(
        VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "_*", VariableUpdateInstanceDto.class);
  }

  @SneakyThrows
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    databaseTestService.deleteAllZeebeRecordsForPrefix(zeebeRecordPrefix);
  }

  @SneakyThrows
  public void deleteAllOtherZeebeRecordsWithPrefix(
      final String zeebeRecordPrefix, final String recordsToKeep) {
    databaseTestService.deleteAllOtherZeebeRecordsWithPrefix(zeebeRecordPrefix, recordsToKeep);
  }

  @SneakyThrows
  public void updateZeebeRecordsWithPositionForPrefix(
      final String zeebeRecordPrefix,
      final String indexName,
      final long position,
      final String updateScript) {
    databaseTestService.updateZeebeRecordsWithPositionForPrefix(
        zeebeRecordPrefix, indexName, position, updateScript);
  }

  @SneakyThrows
  public void updateZeebeProcessRecordsOfBpmnElementTypeForPrefix(
      final String zeebeRecordPrefix,
      final BpmnElementType bpmnElementType,
      final String updateScript) {
    databaseTestService.updateZeebeRecordsOfBpmnElementTypeForPrefix(
        zeebeRecordPrefix, bpmnElementType, updateScript);
  }

  @SneakyThrows
  public void updateZeebeRecordsForPrefix(
      final String zeebeRecordPrefix, final String indexName, final String updateScript) {
    databaseTestService.updateZeebeRecordsForPrefix(zeebeRecordPrefix, indexName, updateScript);
  }

  @SneakyThrows
  public void updateUserTaskDurations(
      final String processInstanceId, final String processDefinitionKey, final long duration) {
    databaseTestService.updateUserTaskDurations(processInstanceId, processDefinitionKey, duration);
  }

  public Map<AggregationDto, Double> calculateExpectedValueGivenDurations(
      final Number... setDuration) {
    return databaseTestService.calculateExpectedValueGivenDurations(setDuration);
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

  public List<ProcessInstanceDto> getProcessInstancesById(final List<String> instanceIds) {
    return databaseTestService.getProcessInstancesById(instanceIds);
  }

  public List<DecisionInstanceDto> getDecisionInstancesById(final List<String> instanceIds) {
    return databaseTestService.getDecisionInstancesById(instanceIds);
  }

  public <T> Optional<T> getDatabaseEntryById(
      final String indexName, final String entryId, final Class<T> type) {
    return databaseTestService.getDatabaseEntryById(indexName, entryId, type);
  }

  public void deleteProcessInstancesFromIndex(final String indexName, final String id) {
    databaseTestService.deleteProcessInstancesFromIndex(indexName, id);
  }

  public String getDatabaseVersion() {
    return databaseTestService.getDatabaseVersion();
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

  public int getNestedDocumentLimit(final ConfigurationService configurationService) {
    return databaseTestService.getNestedDocumentsLimit(configurationService);
  }

  public void createIndex(
      final String optimizeIndexNameWithVersion, final String optimizeIndexAliasForIndex)
      throws IOException {
    databaseTestService.createIndex(optimizeIndexNameWithVersion, optimizeIndexAliasForIndex);
  }

  public Optional<MetadataDto> readMetadata() {
    return databaseTestService.readMetadata();
  }

  public void createMissingIndices(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder,
      final Set<String> aliases,
      final Set<String> aKey) {
    databaseTestService.createMissingIndices(indexMappingCreatorBuilder, aliases, aKey);
  }

  public String[] getImportIndices() {
    return databaseTestService.getImportIndices().stream()
        .map(getIndexNameService()::getOptimizeIndexAliasForIndex)
        .toArray(String[]::new);
  }

  public void setActivityStartDatesToNull(final String processDefinitionKey) {
    final ScriptData scriptData =
        new ScriptData(
            Map.of(),
            "for (flowNodeInstance in ctx._source.flowNodeInstances) { flowNodeInstance.startDate = null }");
    databaseTestService.setActivityStartDatesToNull(processDefinitionKey, scriptData);
  }

  public void setUserTaskDurationToNull(
      final String processInstanceId, final String durationFieldName) {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("flowNodesField", FLOW_NODE_INSTANCES)
                .put("durationFieldName", durationFieldName)
                .build());

    // @formatter:off
    final String setDurationToNull =
        substitutor.replace(
            "for(flowNode in ctx._source.${flowNodesField}) {"
                + "flowNode.${durationFieldName} = null;"
                + "}");
    // @formatter:on

    final ScriptData updateScript = new ScriptData(Collections.emptyMap(), setDurationToNull);
    databaseTestService.setUserTaskDurationToNull(
        processInstanceId, durationFieldName, updateScript);
  }

  public Long getImportedActivityCount() {
    return databaseTestService.getImportedActivityCount();
  }

  public void createIndex(
      final String optimizeIndexNameWithVersion,
      final String optimizeIndexAliasForIndex,
      DefaultIndexMappingCreator mapping)
      throws IOException {
    createIndex(optimizeIndexNameWithVersion, optimizeIndexAliasForIndex, mapping, true);
  }

  public List<String> getAllIndicesWithWriteAlias(final String indexName) {
    final String aliasNameWithPrefix =
        getIndexNameService().getOptimizeIndexAliasForIndex(indexName);
    return databaseTestService.getAllIndicesWithWriteAlias(aliasNameWithPrefix);
  }

  public void deleteAllDocumentsInIndex(String optimizeIndexAliasForIndex) {
    databaseTestService.deleteAllDocumentsInIndex(optimizeIndexAliasForIndex);
  }

  public void insertTestDocuments(int amount, String indexName, String documentContentAsJson)
      throws IOException {
    databaseTestService.insertTestDocuments(amount, indexName, documentContentAsJson);
  }

  public void performLowLevelBulkRequest(String methodName, String endpoint, String bulkPayload)
      throws IOException {
    databaseTestService.performLowLevelBulkRequest(methodName, endpoint, bulkPayload);
  }

  public void initSchema(final DatabaseSchemaManager schemaManager) {
    databaseTestService.initSchema(schemaManager);
  }

  public Map<String, ?> getMappingFields(final String indexName) throws IOException {
    return databaseTestService.getMappingFields(indexName);
  }

  public boolean indexExists(String versionedIndexName, Boolean addMappingFeatures) {
    return databaseTestService.indexExists(versionedIndexName, addMappingFeatures);
  }

  public void createIndex(
      final String optimizeIndexNameWithVersion,
      final String optimizeIndexAliasForIndex,
      DefaultIndexMappingCreator mapping,
      Boolean isWriteIndex)
      throws IOException {
    createIndex(
        optimizeIndexNameWithVersion, Map.of(optimizeIndexAliasForIndex, isWriteIndex), mapping);
  }

  public void createIndex(
      final String optimizeIndexNameWithVersion,
      final Map<String, Boolean> aliases,
      DefaultIndexMappingCreator mapping)
      throws IOException {
    databaseTestService.createIndex(optimizeIndexNameWithVersion, aliases, mapping);
  }

  public boolean templateExists(String optimizeIndexTemplateNameWithVersion) throws IOException {
    return databaseTestService.templateExists(optimizeIndexTemplateNameWithVersion);
  }

  public boolean isAliasReadOnly(String readOnlyAliasForIndex) throws IOException {
    return databaseTestService.isAliasReadOnly(readOnlyAliasForIndex);
  }
}
