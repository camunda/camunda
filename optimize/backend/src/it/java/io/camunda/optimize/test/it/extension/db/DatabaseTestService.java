/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension.db;

import static io.camunda.optimize.ApplicationContextProvider.getBean;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.MetadataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.db.schema.index.VariableUpdateInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import io.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import io.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.test.it.extension.MockServerUtil;
import io.camunda.optimize.test.repository.TestIndexRepository;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.text.StringSubstitutor;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

public abstract class DatabaseTestService {

  protected static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DatabaseTestService.class);
  protected final String customIndexPrefix;
  protected boolean haveToClean;
  private TestIndexRepository testIndexRepository;

  protected DatabaseTestService(final String customIndexPrefix, final boolean haveToClean) {
    this.customIndexPrefix = customIndexPrefix;
    this.haveToClean = haveToClean;
  }

  protected static ClientAndServer initMockServer(
      final DatabaseConnectionNodeConfiguration dbConfig) {
    LOG.info(
        "Setting up DB MockServer on port {}",
        IntegrationTestConfigurationUtil.getDatabaseMockServerPort());
    return MockServerUtil.createProxyMockServer(
        dbConfig.getHost(),
        dbConfig.getHttpPort(),
        IntegrationTestConfigurationUtil.getDatabaseMockServerPort());
  }

  private static ObjectMapper createObjectMapper() {
    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(
        OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(
        OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));

    return Jackson2ObjectMapperBuilder.json()
        .modules(javaTimeModule)
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
        .build();
  }

  public ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }

  public abstract void beforeEach();

  public abstract void afterEach();

  public abstract ClientAndServer useDBMockServer();

  public abstract void refreshAllOptimizeIndices();

  public abstract void addEntryToDatabase(String indexName, String id, Object entry);

  public abstract void addEntriesToDatabase(String indexName, Map<String, Object> idToEntryMap);

  public abstract <T> List<T> getAllDocumentsOfIndexAs(final String indexName, final Class<T> type);

  public abstract DatabaseClient getDatabaseClient();

  public abstract Integer getDocumentCountOf(final String indexName);

  public abstract void deleteAllOptimizeData();

  public abstract void deleteAllIndicesContainingTerm(final String indexTerm);

  public abstract void deleteAllSingleProcessReports();

  public abstract void deleteTerminatedSessionsIndex();

  public abstract void deleteAllVariableUpdateInstanceIndices();

  public abstract void deleteAllExternalVariableIndices();

  public abstract void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix);

  public abstract void deleteAllOtherZeebeRecordsWithPrefix(
      final String zeebeRecordPrefix, final String recordsToKeep);

  public abstract void updateZeebeRecordsForPrefix(
      final String zeebeRecordPrefix, final String indexName, final String updateScript);

  public abstract void updateZeebeRecordsWithPositionForPrefix(
      final String zeebeRecordPrefix,
      final String indexName,
      final long position,
      final String updateScript);

  public abstract void updateZeebeRecordsOfBpmnElementTypeForPrefix(
      final String zeebeRecordPrefix,
      final BpmnElementType bpmnElementType,
      final String updateScript);

  public abstract void updateUserTaskDurations(
      final String processInstanceId, final String processDefinitionKey, final long duration);

  public abstract boolean indexExistsCheckWithApplyingOptimizePrefix(final String indexOrAliasName);

  public abstract boolean indexExistsCheckWithoutApplyingOptimizePrefix(final String indexName);

  public abstract OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(
      final String dbType, final String engine);

  public abstract Map<AggregationDto, Double> calculateExpectedValueGivenDurations(
      final Number... setDuration);

  public abstract long countRecordsByQuery(
      final TermsQueryContainer queryContainer, final String expectedIndex);

  public abstract <T> List<T> getZeebeExportedRecordsByQuery(
      final String exportIndex,
      final TermsQueryContainer queryForZeebeRecords,
      final Class<T> zeebeRecordClass);

  public abstract void deleteProcessInstancesFromIndex(final String indexName, final String id);

  public abstract DatabaseType getDatabaseVendor();

  public abstract void createSnapshot(
      final String snapshotRepositoryName, final String snapshotName, final String[] indexNames);

  public abstract void createRepoSnapshot(final String snapshotRepositoryName);

  public abstract void cleanSnapshots(final String snapshotRepositoryName);

  public abstract List<String> getImportIndices();

  protected abstract <T extends OptimizeDto> List<T> getInstancesById(
      final String indexName,
      final List<String> instanceIds,
      final String idField,
      final Class<T> type);

  public abstract <T> Optional<T> getDatabaseEntryById(
      final String indexName, final String entryId, final Class<T> type);

  public abstract String getDatabaseVersion();

  public abstract int getNestedDocumentsLimit(final ConfigurationService configurationService);

  public abstract void setNestedDocumentsLimit(
      final ConfigurationService configurationService, int nestedDocumentsLimit);

  public abstract void updateProcessInstanceNestedDocLimit(
      String processDefinitionKey,
      int nestedDocLimit,
      final ConfigurationService configurationService);

  public abstract void createIndex(
      String optimizeIndexNameWithVersion, String optimizeIndexAliasForIndex) throws IOException;

  public abstract void createIndex(
      String optimizeIndexNameWithVersion,
      Map<String, Boolean> aliases,
      DefaultIndexMappingCreator mapping)
      throws IOException;

  public abstract Optional<MetadataDto> readMetadata();

  public void cleanAndVerifyDatabase() {
    try {
      refreshAllOptimizeIndices();
      deleteAllOptimizeData();
    } catch (final Exception e) {
      // nothing to do
    }
  }

  public List<ProcessInstanceDto> getProcessInstancesById(final List<String> instanceIds) {
    return getProcessInstancesById(
        PROCESS_INSTANCE_MULTI_ALIAS, instanceIds, ProcessInstanceDto.class);
  }

  public <T extends ProcessInstanceDto> List<T> getProcessInstancesById(
      final String indexName, final List<String> instanceIds, final Class<T> type) {
    return getInstancesById(indexName, instanceIds, ProcessInstanceIndex.PROCESS_INSTANCE_ID, type);
  }

  public List<DecisionInstanceDto> getDecisionInstancesById(final List<String> instanceIds) {
    return getDecisionInstancesById(
        DECISION_INSTANCE_MULTI_ALIAS, instanceIds, DecisionInstanceDto.class);
  }

  protected void setTestIndexRepository(final TestIndexRepository testIndexRepository) {
    this.testIndexRepository = testIndexRepository;
  }

  public <T extends DecisionInstanceDto> List<T> getDecisionInstancesById(
      final String indexName, final List<String> instanceIds, final Class<T> type) {
    return getInstancesById(
        indexName, instanceIds, DecisionInstanceIndex.DECISION_INSTANCE_ID, type);
  }

  protected String buildUpdateScript(final long duration) {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("flowNodesField", FLOW_NODE_INSTANCES)
                .put("flowNodeTypeField", FLOW_NODE_TYPE)
                .put("totalDurationField", FLOW_NODE_TOTAL_DURATION)
                .put("idleDurationField", USER_TASK_IDLE_DURATION)
                .put("workDurationField", USER_TASK_WORK_DURATION)
                .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
                .put("newDuration", String.valueOf(duration))
                .build());
    // @formatter:off
    final String updateScript =
        substitutor.replace(
            "for (def flowNode : ctx._source.${flowNodesField}) {"
                + "if (flowNode.${flowNodeTypeField}.equals(\"${userTaskFlowNodeType}\")) {"
                + "flowNode.${totalDurationField} = ${newDuration};"
                + "flowNode.${workDurationField} = ${newDuration};"
                + "flowNode.${idleDurationField} = ${newDuration};"
                + "}"
                + "}");
    // @formatter:on
    return updateScript;
  }

  public void createMissingIndices(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder,
      final Set<String> aliases,
      final Set<String> aKey) {
    getBean(IndexRepository.class).createMissingIndices(indexMappingCreatorBuilder, aliases, aKey);
  }

  public abstract void setActivityStartDatesToNull(String processDefinitionKey, ScriptData script);

  public abstract void setUserTaskDurationToNull(
      String processInstanceId, String durationFieldName, ScriptData script);

  public abstract Long getImportedActivityCount();

  public abstract List<String> getAllIndicesWithWriteAlias(String aliasNameWithPrefix);

  public abstract VariableUpdateInstanceIndex getVariableUpdateInstanceIndex();

  public abstract void deleteAllDocumentsInIndex(String optimizeIndexAliasForIndex);

  public abstract void insertTestDocuments(int amount, String indexName, String jsonDocument)
      throws IOException;

  public abstract void performLowLevelBulkRequest(
      String methodName, String endpoint, String bulkPayload) throws IOException;

  public abstract void initSchema(final DatabaseSchemaManager schemaManager);

  public abstract Map<String, ? extends Object> getMappingFields(final String indexName)
      throws IOException;

  public abstract boolean indexExists(String versionedIndexName, Boolean addMappingFeatures);

  public abstract boolean templateExists(String optimizeIndexTemplateNameWithVersion)
      throws IOException;

  public abstract boolean isAliasReadOnly(String readOnlyAliasForIndex) throws IOException;

  public abstract List<String> getAllIndicesWithReadOnlyAlias(String aliasNameWithPrefix);

  public abstract String[] getIndexNames();
}
