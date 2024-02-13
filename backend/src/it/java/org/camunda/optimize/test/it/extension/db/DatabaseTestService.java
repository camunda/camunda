/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension.db;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableMap;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.elasticsearch.DatabaseConnectionNodeConfiguration;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.test.repository.TestIndexRepository;
import org.mockserver.integration.ClientAndServer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@Slf4j
public abstract class DatabaseTestService {

  protected static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

  protected boolean haveToClean;
  protected final String customIndexPrefix;
  private TestIndexRepository testIndexRepository;

  protected DatabaseTestService(final String customIndexPrefix,
                                final boolean haveToClean) {
    this.customIndexPrefix = customIndexPrefix;
    this.haveToClean = haveToClean;
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

  // TODO OPT-7455 - remove this coupling to the ES client from the abstract test service
  public abstract OptimizeElasticsearchClient getOptimizeElasticsearchClient();

  public abstract Integer getDocumentCountOf(final String indexName);

  public abstract Integer getCountOfCompletedInstances();

  public abstract Integer getCountOfCompletedInstancesWithIdsIn(final Set<Object> processInstanceIds);

  public abstract Integer getActivityCountForAllProcessInstances();

  public abstract Integer getVariableInstanceCountForAllProcessInstances();

  public abstract Integer getVariableInstanceCountForAllCompletedProcessInstances();

  public abstract void deleteAllOptimizeData();

  public abstract void deleteAllIndicesContainingTerm(final String indexTerm);

  public abstract void deleteAllSingleProcessReports();

  public abstract void deleteExternalEventSequenceCountIndex();

  public abstract void deleteAllExternalEventIndices();

  public abstract void deleteTerminatedSessionsIndex();

  public abstract void deleteAllVariableUpdateInstanceIndices();

  public abstract void deleteAllExternalVariableIndices();

  public abstract void deleteIndicesStartingWithPrefix(final String term);

  public abstract void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix);

  public abstract void deleteAllOtherZeebeRecordsWithPrefix(final String zeebeRecordPrefix, final String recordsToKeep);

  public abstract void updateZeebeRecordsForPrefix(final String zeebeRecordPrefix, final String indexName,
                                                   final String updateScript);

  public abstract void updateZeebeRecordsWithPositionForPrefix(final String zeebeRecordPrefix, final String indexName,
                                                               final long position, final String updateScript);

  public abstract void updateZeebeRecordsOfBpmnElementTypeForPrefix(final String zeebeRecordPrefix,
                                                                    final BpmnElementType bpmnElementType,
                                                                    final String updateScript);

  public abstract void updateUserTaskDurations(final String processInstanceId, final String processDefinitionKey,
                                               final long duration);

  public abstract boolean indexExists(final String indexOrAliasName);

  public abstract OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(final String dbType, final String engine);

  public abstract Map<AggregationDto, Double> calculateExpectedValueGivenDurations(final Number... setDuration);

  public void cleanAndVerifyDatabase() {
    try {
      refreshAllOptimizeIndices();
      deleteAllOptimizeData();
      deleteAllEventProcessInstanceIndices();
      deleteCamundaEventIndicesAndEventCountsAndTraces();
      deleteAllProcessInstanceArchiveIndices();
    } catch (Exception e) {
      //nothing to do
      log.error("can't clean optimize indexes", e);
    }
  }

  public void disableCleanup() {
    this.haveToClean = false;
  }

  public TestIndexRepository getTestIndexRepository() {
    return testIndexRepository;
  }

  protected void setTestIndexRepository(final TestIndexRepository testIndexRepository) {
    this.testIndexRepository = testIndexRepository;
  }

  protected String buildUpdateScript(final long duration) {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodesField", FLOW_NODE_INSTANCES)
        .put("flowNodeTypeField", FLOW_NODE_TYPE)
        .put("totalDurationField", FLOW_NODE_TOTAL_DURATION)
        .put("idleDurationField", USER_TASK_IDLE_DURATION)
        .put("workDurationField", USER_TASK_WORK_DURATION)
        .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
        .put("newDuration", String.valueOf(duration))
        .build()
    );
    // @formatter:off
    final String updateScript = substitutor.replace(
      "for (def flowNode : ctx._source.${flowNodesField}) {" +
        "if (flowNode.${flowNodeTypeField}.equals(\"${userTaskFlowNodeType}\")) {" +
        "flowNode.${totalDurationField} = ${newDuration};" +
        "flowNode.${workDurationField} = ${newDuration};" +
        "flowNode.${idleDurationField} = ${newDuration};" +
        "}" +
        "}"
    );
    // @formatter:on
    return updateScript;
  }

  protected void deleteCamundaEventIndicesAndEventCountsAndTraces() {
    deleteIndicesStartingWithPrefix(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX);
    deleteIndicesStartingWithPrefix(EVENT_SEQUENCE_COUNT_INDEX_PREFIX);
    deleteIndicesStartingWithPrefix(EVENT_TRACE_STATE_INDEX_PREFIX);
  }

  protected void deleteAllProcessInstanceArchiveIndices() {
    deleteIndicesStartingWithPrefix(PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX);
  }

  protected void deleteAllEventProcessInstanceIndices() {
    deleteIndicesStartingWithPrefix(EVENT_PROCESS_INSTANCE_INDEX_PREFIX);
  }

  private static ObjectMapper createObjectMapper() {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));

    return Jackson2ObjectMapperBuilder
      .json()
      .modules(javaTimeModule)
      .featuresToDisable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
      )
      .featuresToEnable(
        JsonParser.Feature.ALLOW_COMMENTS,
        SerializationFeature.INDENT_OUTPUT
      )
      .build();
  }

  protected static ClientAndServer initMockServer(final DatabaseConnectionNodeConfiguration dbConfig) {
    log.info("Setting up DB MockServer on port {}", IntegrationTestConfigurationUtil.getDatabaseMockServerPort());
    return MockServerUtil.createProxyMockServer(
      dbConfig.getHost(),
      dbConfig.getHttpPort(),
      IntegrationTestConfigurationUtil.getDatabaseMockServerPort()
    );
  }

}
