/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.reimport.preparation;

import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_KEY_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.IMPORT_INDEX_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.tenant.CamundaPlatformTenantService.TENANT_NOT_DEFINED;
import static io.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.SpringDefaultC7ITConfig;
import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@Slf4j
public class ForceReimportIT extends AbstractEventProcessIT {

  public static final List<String> TENANTS = Collections.singletonList(null);

  @Test
  public void forceReimport_optimizeEntitiesUntouched() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployAndStartSimpleServiceTask();
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
        engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.createTenant("tenant1");
    final String collectionId =
        collectionClient.createNewCollectionWithProcessScope(processDefinitionEngineDto);
    collectionClient.addScopeEntryToCollection(
        collectionId, decisionDefinitionEngineDto.getKey(), DefinitionType.DECISION, TENANTS);
    final String reportId = createAndStoreNumberReport(collectionId, processDefinitionEngineDto);
    reportClient.createAndStoreDecisionReport(
        collectionId, decisionDefinitionEngineDto.getKey(), TENANTS);
    alertClient.createAlertForReport(reportId);
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    importAllEngineEntitiesFromScratch();

    final List<AuthorizedReportDefinitionResponseDto> allReports =
        collectionClient.getReportsForCollection(collectionId);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);
    final List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts();
    assertThat(allReports).hasSize(2);
    assertThat(allAlerts).hasSize(1);

    // when
    forceReimportOfEngineData();

    // then
    assertThat(collectionClient.getReportsForCollection(collectionId)).isEqualTo(allReports);
    assertThat(dashboardClient.getDashboard(dashboardId)).isEqualTo(dashboard);
    assertThat(alertClient.getAllAlerts())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("triggered")
        .isEqualTo(allAlerts);
  }

  @Test
  public void forceReimport_tenantDataCleanedUp() {
    // given
    engineIntegrationExtension.createTenant("tenant1");

    importAllEngineEntitiesFromScratch();

    final List<TenantDto> allTenants = databaseIntegrationTestExtension.getAllTenants();
    assertThat(allTenants).isNotEmpty();

    // when
    forceReimportOfEngineData();

    // then
    assertThat(hasNoEngineProcessData()).isTrue();

    // when
    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.reinitializeSchema();
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(databaseIntegrationTestExtension.getAllTenants()).isEqualTo(allTenants);
  }

  @Test
  public void forceReimport_processDataCleanedUp() {
    // given
    deployAndStartSimpleServiceTask();

    importAllEngineEntitiesFromScratch();
    runEventProcessing();

    final List<ProcessDefinitionOptimizeDto> allProcessDefinitions =
        databaseIntegrationTestExtension.getAllProcessDefinitions();
    final List<String> allProcessInstanceIds = getProcessInstanceIds();
    assertThat(allProcessDefinitions).isNotEmpty();
    assertThat(allProcessInstanceIds).isNotEmpty();

    // when
    forceReimportOfEngineData();

    // then
    assertThat(hasNoEngineProcessData()).isTrue();

    // when
    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.reinitializeSchema();
    importAllEngineEntitiesFromLastIndex();
    runEventProcessing();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessDefinitions())
        .isEqualTo(allProcessDefinitions);
    assertThat(getProcessInstanceIds()).isEqualTo(allProcessInstanceIds);
    assertThat(hasEngineProcessData()).isTrue();
  }

  @Test
  public void forceReimport_decisionDataCleanedUp() {
    // given
    engineIntegrationExtension.deployAndStartDecisionDefinition();

    importAllEngineEntitiesFromScratch();

    final List<DecisionDefinitionOptimizeDto> allDecisionDefinitions =
        databaseIntegrationTestExtension.getAllDecisionDefinitions();
    final List<String> allDecisionInstanceIds = getDecisionInstanceIds();
    assertThat(allDecisionDefinitions).isNotEmpty();
    assertThat(allDecisionInstanceIds).isNotEmpty();

    // when
    forceReimportOfEngineData();

    // then
    assertThat(hasNoEngineDecisionData()).isTrue();

    // when
    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.reinitializeSchema();
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(databaseIntegrationTestExtension.getAllDecisionDefinitions())
        .isEqualTo(allDecisionDefinitions);
    assertThat(getDecisionInstanceIds()).isEqualTo(allDecisionInstanceIds);
    assertThat(hasEngineDecisionData()).isTrue();
  }

  @Test
  public void forceReimport_eventProcessDataCleanedUp() {
    // given
    final String eventProcessMapping = createEventProcess();

    importAllEngineEntitiesFromScratch();
    runEventProcessing();
    publishEventProcess(eventProcessMapping);

    final List<String> allProcessDefinitionKeys = getAllProcessDefinitionKeys();
    final List<String> allProcessInstanceIds = getProcessInstanceIds();
    final List<EventProcessMappingDto> allEventProcessMappings =
        eventProcessClient.getAllEventProcessMappings();
    assertThat(allProcessDefinitionKeys).isNotEmpty();
    assertThat(allProcessInstanceIds).isNotEmpty();
    assertThat(allEventProcessMappings)
        .extracting(EventProcessMappingDto::getState)
        .containsExactly(EventProcessState.PUBLISHED);
    assertThat(hasExternalEventData()).isTrue();
    assertThat(hasPublishedEventProcessData()).isTrue();

    // when
    forceReimportOfEngineData();

    // then
    assertThat(getAllProcessDefinitionKeys()).isEmpty();
    assertThat(databaseIntegrationTestExtension.indexExists(PROCESS_INSTANCE_MULTI_ALIAS))
        .isFalse();
    assertThat(eventProcessClient.getAllEventProcessMappings())
        .isEqualTo(allEventProcessMappings)
        .extracting(EventProcessMappingDto::getState)
        .containsExactly(EventProcessState.MAPPED);
    assertThat(hasExternalEventData()).isTrue();
    assertThat(hasPublishedEventProcessData()).isFalse();

    // when
    embeddedOptimizeExtension.reloadConfiguration();
    embeddedOptimizeExtension.reinitializeSchema();
    importAllEngineEntitiesFromLastIndex();
    runEventProcessing();
    publishEventProcess(eventProcessMapping);

    // then
    assertThat(getAllProcessDefinitionKeys()).isEqualTo(allProcessDefinitionKeys);
    assertThat(getProcessInstanceIds()).isEqualTo(allProcessInstanceIds);
    assertThat(allEventProcessMappings)
        .extracting(EventProcessMappingDto::getState)
        .containsExactly(EventProcessState.PUBLISHED);
    assertThat(hasExternalEventData()).isTrue();
    assertThat(hasPublishedEventProcessData()).isTrue();
  }

  private String createEventProcess() {
    ingestTestEvent(STARTED_EVENT, OffsetDateTime.now(), "trace1");
    ingestTestEvent(FINISHED_EVENT, OffsetDateTime.now(), "trace1");
    return createSimpleEventProcessMapping(STARTED_EVENT, FINISHED_EVENT);
  }

  private String createAndStoreNumberReport(
      final String collectionId, final ProcessDefinitionEngineDto processDefinition) {
    return reportClient.createAndStoreProcessReport(
        collectionId,
        processDefinition.getKey(),
        Collections.singletonList(TENANT_NOT_DEFINED.getId()));
  }

  private List<String> getAllProcessDefinitionKeys() {
    return databaseIntegrationTestExtension.getAllProcessDefinitions().stream()
        .map(DefinitionOptimizeResponseDto::getKey)
        .collect(Collectors.toList());
  }

  private List<String> getProcessInstanceIds() {
    return databaseIntegrationTestExtension.getAllProcessInstances().stream()
        .map(ProcessInstanceDto::getProcessInstanceId)
        .collect(Collectors.toList());
  }

  private List<String> getDecisionInstanceIds() {
    return databaseIntegrationTestExtension.getAllDecisionInstances().stream()
        .map(DecisionInstanceDto::getDecisionInstanceId)
        .collect(Collectors.toList());
  }

  private boolean hasEngineProcessData() {
    return allIndexGroupsHaveData(getEngineProcessDataIndices());
  }

  private boolean hasEngineDecisionData() {
    return allIndexGroupsHaveData(getEngineDecisionDataIndices());
  }

  private boolean hasExternalEventData() {
    return allIndexGroupsHaveData(getExternalEventDataIndices());
  }

  private boolean hasPublishedEventProcessData() {
    return allIndexGroupsHaveData(getEventProcessDataIndices());
  }

  private boolean allIndexGroupsHaveData(final Set<List<String>> indexGroups) {
    final long groupsThatHaveData =
        indexGroups.stream()
            .filter(
                indexGroup ->
                    indexGroup.stream()
                        .anyMatch(
                            index ->
                                databaseIntegrationTestExtension.getDocumentCountOf(index) > 0L))
            .count();
    return indexGroups.size() == groupsThatHaveData;
  }

  private boolean hasNoEngineDecisionData() {
    return noIndexGroupHasData(getEngineDecisionDataIndices())
        && !indexExists(DECISION_INSTANCE_MULTI_ALIAS);
  }

  private boolean hasNoEngineProcessData() {
    return noIndexGroupHasData(getEngineProcessDataIndices())
        && !indexExists(PROCESS_INSTANCE_MULTI_ALIAS);
  }

  private boolean noIndexGroupHasData(final Set<List<String>> indexGroups) {
    final long groupsThatHaveNoData =
        indexGroups.stream()
            .filter(
                indexGroup ->
                    indexGroup.stream()
                        .allMatch(
                            index ->
                                databaseIntegrationTestExtension.getDocumentCountOf(index) == 0L))
            .count();
    return indexGroups.size() == groupsThatHaveNoData;
  }

  private Set<List<String>> getEventProcessDataIndices() {
    final Set<List<String>> indexGroups = new HashSet<>();
    indexGroups.add(Collections.singletonList(EVENT_PROCESS_DEFINITION_INDEX_NAME));
    indexGroups.add(Collections.singletonList(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*"));
    indexGroups.add(Collections.singletonList(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME));
    return indexGroups;
  }

  private Set<List<String>> getEngineProcessDataIndices() {
    final Set<List<String>> indexGroups = new HashSet<>();
    indexGroups.add(Collections.singletonList(TIMESTAMP_BASED_IMPORT_INDEX_NAME));
    indexGroups.add(Collections.singletonList(IMPORT_INDEX_INDEX_NAME));
    indexGroups.add(Collections.singletonList(PROCESS_DEFINITION_INDEX_NAME));
    indexGroups.add(Collections.singletonList(BUSINESS_KEY_INDEX_NAME));
    indexGroups.add(Collections.singletonList(VARIABLE_UPDATE_INSTANCE_INDEX_NAME));
    indexGroups.add(Collections.singletonList(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"));
    indexGroups.add(
        ImmutableList.of(
            EVENT_TRACE_STATE_INDEX_PREFIX + "*",
            "-" + EVENT_TRACE_STATE_INDEX_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX + "*"));
    indexGroups.add(
        ImmutableList.of(
            EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "*",
            "-" + EVENT_SEQUENCE_COUNT_INDEX_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX + "*"));
    return indexGroups;
  }

  private Set<List<String>> getEngineDecisionDataIndices() {
    final Set<List<String>> indexGroups = new HashSet<>();
    indexGroups.add(Collections.singletonList(TIMESTAMP_BASED_IMPORT_INDEX_NAME));
    indexGroups.add(Collections.singletonList(IMPORT_INDEX_INDEX_NAME));
    indexGroups.add(Collections.singletonList(DECISION_DEFINITION_INDEX_NAME));
    return indexGroups;
  }

  private Set<List<String>> getExternalEventDataIndices() {
    final Set<List<String>> indexGroups = new HashSet<>();
    indexGroups.add(Collections.singletonList(EXTERNAL_EVENTS_INDEX_NAME));
    indexGroups.add(Collections.singletonList(EVENT_TRACE_STATE_INDEX_PREFIX + "external*"));
    indexGroups.add(Collections.singletonList(EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "external*"));
    return indexGroups;
  }

  private boolean indexExists(final String indexAlias) {
    return embeddedOptimizeExtension
        .getDatabaseSchemaManager()
        .indexExists(embeddedOptimizeExtension.getOptimizeDatabaseClient(), indexAlias);
  }

  private void runEventProcessing() {
    embeddedOptimizeExtension.processEvents();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void forceReimportOfEngineData() {
    ReimportPreparation.main(new String[]{});
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTask() {
    final Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskWithVariables(
      final Map<String, Object> variables) {
    final BpmnModelInstance processModel = getSingleServiceTaskProcess();

    final ProcessDefinitionEngineDto processDefinitionEngineDto =
        engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId(), variables);
    return processDefinitionEngineDto;
  }

  @Import(SpringDefaultC7ITConfig.class)
  @TestConfiguration
  public class Configuration {

  }
}
