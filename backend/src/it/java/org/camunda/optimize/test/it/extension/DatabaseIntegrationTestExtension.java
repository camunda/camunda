/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.es.EsEventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.DatabaseProfile;
import org.camunda.optimize.test.it.extension.db.DatabaseTestService;
import org.camunda.optimize.test.it.extension.db.ElasticsearchDatabaseTestService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_MAPPING_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.DatabaseConstants.TENANT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

@Slf4j
public class DatabaseIntegrationTestExtension implements BeforeEachCallback, AfterEachCallback {

  private final DatabaseTestService databaseTestService;

  public DatabaseIntegrationTestExtension() {
    this(true);
  }

  public DatabaseIntegrationTestExtension(final boolean haveToClean) {
    this(null, haveToClean);
  }

  public DatabaseIntegrationTestExtension(final String customIndexPrefix) {
    this(customIndexPrefix, true);
  }

  private DatabaseIntegrationTestExtension(final String customIndexPrefix,
                                           final boolean haveToClean) {
    if (DatabaseProfile.toProfile(IntegrationTestConfigurationUtil.getDatabaseProfile()).equals(DatabaseProfile.ELASTICSEARCH)) {
      this.databaseTestService = new ElasticsearchDatabaseTestService(
        customIndexPrefix, haveToClean
      );
    } else {
      // TODO Write a new OpenSearch extension
      throw new NotImplementedException("Cannot start Integration tests with the OpenSearch profile");
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
   * This class adds a document entry to the database. Thereby, the
   * entry is added to the optimize index and the given type under
   * the given id.
   * <p>
   * The object needs to be a POJO, which is then converted to json. Thus, the entry
   * results in every object member variable name is going to be mapped to the
   * field name in ES and every content of that variable is going to be the
   * content of the field.
   *
   * @param indexName where the entry is added.
   * @param id        under which the entry is added.
   * @param entry     a POJO specifying field names and their contents.
   */
  public void addEntryToDatabase(String indexName, String id, Object entry) {
    databaseTestService.addEntryToDatabase(indexName, id, entry);
  }

  public void addEntriesToDatabase(String indexName, Map<String, Object> idToEntryMap) {
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

  public Integer getCountOfCompletedInstances() {
    return databaseTestService.getCountOfCompletedInstances();
  }

  public Integer getCountOfCompletedInstancesWithIdsIn(final Set<Object> processInstanceIds) {
    return databaseTestService.getCountOfCompletedInstancesWithIdsIn(processInstanceIds);
  }

  public Integer getActivityCountForAllProcessInstances() {
    return databaseTestService.getActivityCountForAllProcessInstances();
  }

  public Integer getVariableInstanceCountForAllProcessInstances() {
    return databaseTestService.getVariableInstanceCountForAllProcessInstances();
  }

  public Integer getVariableInstanceCountForAllCompletedProcessInstances() {
    return databaseTestService.getVariableInstanceCountForAllCompletedProcessInstances();
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

  public void deleteExternalEventSequenceCountIndex() {
    databaseTestService.deleteExternalEventSequenceCountIndex();
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
    return databaseTestService.indexExists(indexOrAliasName);
  }

  public OptimizeElasticsearchClient getOptimizeElasticsearchClient() {
    return databaseTestService.getOptimizeElasticsearchClient();
  }

  public void cleanAndVerify() {
    databaseTestService.cleanAndVerifyDatabase();
  }

  public void disableCleanup() {
    databaseTestService.disableCleanup();
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions() {
    return getAllDocumentsOfIndexAs(DECISION_DEFINITION_INDEX_NAME, DecisionDefinitionOptimizeDto.class);
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return Stream.concat(
      getAllDocumentsOfIndexAs(PROCESS_DEFINITION_INDEX_NAME, ProcessDefinitionOptimizeDto.class).stream(),
      getAllDocumentsOfIndexAs(EVENT_PROCESS_DEFINITION_INDEX_NAME, ProcessDefinitionOptimizeDto.class).stream()
    ).toList();
  }

  public List<TenantDto> getAllTenants() {
    return getAllDocumentsOfIndexAs(TENANT_INDEX_NAME, TenantDto.class);
  }

  public List<EventDto> getAllStoredExternalEvents() {
    return getAllDocumentsOfIndexAs(EXTERNAL_EVENTS_INDEX_NAME, EventDto.class);
  }

  public List<DecisionInstanceDto> getAllDecisionInstances() {
    return getAllDocumentsOfIndexAs(DECISION_INSTANCE_MULTI_ALIAS, DecisionInstanceDto.class);
  }

  public List<ProcessInstanceDto> getAllProcessInstances() {
    return getAllDocumentsOfIndexAs(PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class);
  }

  @SneakyThrows
  public List<CamundaActivityEventDto> getAllStoredCamundaActivityEventsForDefinition(final String processDefinitionKey) {
    return getAllDocumentsOfIndexAs(
      CamundaActivityEventIndex.constructIndexName(processDefinitionKey), CamundaActivityEventDto.class
    );
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToDatabase(final String key) {
    return addEventProcessDefinitionDtoToDatabase(key, "eventProcess-" + key);
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToDatabase(final String key,
                                                                          final String name) {
    return addEventProcessDefinitionDtoToDatabase(
      key,
      name,
      null,
      Collections.singletonList(new IdentityDto(DEFAULT_USERNAME, IdentityType.USER))
    );
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToDatabase(final String key,
                                                                          final IdentityDto identityDto) {
    return addEventProcessDefinitionDtoToDatabase(
      key,
      "eventProcess-" + key,
      null,
      Collections.singletonList(identityDto)
    );
  }

  public EventProcessDefinitionDto addEventProcessDefinitionDtoToDatabase(final String key,
                                                                          final String name,
                                                                          final String version,
                                                                          final List<IdentityDto> identityDtos) {
    final List<EventProcessRoleRequestDto<IdentityDto>> roles = identityDtos.stream()
      .filter(Objects::nonNull)
      .map(identityDto -> new IdentityDto(identityDto.getId(), identityDto.getType()))
      .map(EventProcessRoleRequestDto::new)
      .collect(Collectors.toList());
    final EsEventProcessMappingDto eventProcessMappingDto = EsEventProcessMappingDto.builder()
      .id(key)
      .roles(roles)
      .build();
    addEntryToDatabase(EVENT_PROCESS_MAPPING_INDEX_NAME, eventProcessMappingDto.getId(), eventProcessMappingDto);

    final String versionValue = Optional.ofNullable(version).orElse("1");
    final EventProcessDefinitionDto eventProcessDefinitionDto = EventProcessDefinitionDto.eventProcessBuilder()
      .id(key + "-" + version)
      .key(key)
      .name(name)
      .version(versionValue)
      .bpmn20Xml(key + versionValue)
      .deleted(false)
      .onboarded(true)
      .flowNodeData(new ArrayList<>())
      .userTaskNames(Collections.emptyMap())
      .build();
    addEntryToDatabase(
      EVENT_PROCESS_DEFINITION_INDEX_NAME, eventProcessDefinitionDto.getId(), eventProcessDefinitionDto
    );
    return eventProcessDefinitionDto;
  }

  @SneakyThrows
  public OffsetDateTime getLastImportTimestampOfTimestampBasedImportIndex(final String dbType, final String engine) {
    return databaseTestService.getLastImportTimestampOfTimestampBasedImportIndex(dbType, engine);
  }

  @SneakyThrows
  public List<VariableUpdateInstanceDto> getAllStoredVariableUpdateInstanceDtos() {
    return getAllDocumentsOfIndexAs(
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "_*", VariableUpdateInstanceDto.class
    );
  }

  public void deleteAllExternalEventIndices() {
    databaseTestService.deleteAllExternalEventIndices();
  }

  @SneakyThrows
  public void deleteAllZeebeRecordsForPrefix(final String zeebeRecordPrefix) {
    databaseTestService.deleteAllZeebeRecordsForPrefix(zeebeRecordPrefix);
  }

  @SneakyThrows
  public void updateZeebeRecordsWithPositionForPrefix(final String zeebeRecordPrefix, final String indexName,
                                                      final long position, final String updateScript) {
    databaseTestService.updateZeebeRecordsWithPositionForPrefix(zeebeRecordPrefix, indexName, position, updateScript);
  }

  @SneakyThrows
  public void updateZeebeProcessRecordsOfBpmnElementTypeForPrefix(final String zeebeRecordPrefix,
                                                                  final BpmnElementType bpmnElementType,
                                                                  final String updateScript) {
    databaseTestService.updateZeebeRecordsOfBpmnElementTypeForPrefix(zeebeRecordPrefix, bpmnElementType, updateScript);
  }

  @SneakyThrows
  public void updateZeebeRecordsForPrefix(final String zeebeRecordPrefix, final String indexName, final String updateScript) {
    databaseTestService.updateZeebeRecordsForPrefix(zeebeRecordPrefix, indexName, updateScript);
  }

  @SneakyThrows
  public void updateUserTaskDurations(final String processInstanceId, final String processDefinitionKey,
                                      final long duration) {
    databaseTestService.updateUserTaskDurations(processInstanceId, processDefinitionKey, duration);
  }

  public Map<AggregationDto, Double> calculateExpectedValueGivenDurations(final Number... setDuration) {
    return databaseTestService.calculateExpectedValueGivenDurations(setDuration);
  }

}
