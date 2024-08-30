/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus.OPEN;
import static io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus.RESOLVED;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_INCIDENT_INDEX_NAME;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static io.camunda.optimize.util.ZeebeBpmnModels.CATCH_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.createIncidentProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentDataDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;

public class ZeebeIncidentImportIT extends AbstractCCSMIT {

  @Test
  public void importZeebeIncidentData_openFailTaskIncident() {
    // given
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.failTask(SERVICE_TASK);

    // when
    waitUntilIncidentRecordWithProcessIdExported("someProcess");
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance -> {
              assertThat(savedInstance.getProcessInstanceId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessInstanceKey()));
              assertThat(savedInstance.getProcessDefinitionId())
                  .isEqualTo(String.valueOf(deployedInstance.getProcessDefinitionKey()));
              assertThat(savedInstance.getProcessDefinitionKey())
                  .isEqualTo(deployedInstance.getBpmnProcessId());
              assertThat(savedInstance.getProcessDefinitionVersion())
                  .isEqualTo(String.valueOf(deployedInstance.getVersion()));
              assertThat(savedInstance.getDataSource().getName())
                  .isEqualTo(getConfiguredZeebeName());
              assertThat(savedInstance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
              assertThat(savedInstance.getBusinessKey()).isNull();
              assertThat(savedInstance.getFlowNodeInstances()).isNotEmpty();
              assertThat(savedInstance.getVariables()).isEmpty();
              assertThat(savedInstance.getStartDate()).isNotNull();
              assertThat(savedInstance.getEndDate()).isNull();
              assertThat(savedInstance.getDuration()).isNull();
              assertThat(savedInstance.getIncidents())
                  .isNotEmpty()
                  .hasSize(1)
                  .containsExactly(
                      createIncident(savedInstance, deployedInstance, SERVICE_TASK, OPEN));
            });
  }

  @Test
  public void importZeebeIncidentData_throwErrorIncident() {
    // given
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.throwErrorIncident(SERVICE_TASK);

    // when
    waitUntilIncidentRecordWithProcessIdExported("someProcess");
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getIncidents())
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactly(
                        createIncident(savedInstance, deployedInstance, SERVICE_TASK, OPEN)));
  }

  @Test
  public void importZeebeIncidentData_missingVariableIncident() {
    // given
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createIncidentProcess("someProcess"));

    // when
    waitUntilIncidentRecordWithProcessIdExported("someProcess");
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getIncidents())
                    .isNotEmpty()
                    .hasSize(1)
                    .containsExactly(
                        createIncident(savedInstance, deployedInstance, CATCH_EVENT, OPEN)));
  }

  @Test
  public void importZeebeIncidentData_importResolvedIncidentInSameBatch() {
    // given
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.throwErrorIncident(SERVICE_TASK);
    waitUntilIncidentRecordWithProcessIdExported("someProcess");
    resolveIncident();
    waitUntilIncidentRecordsWithProcessIdExported(2, "someProcess");

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getIncidents())
                    .isNotEmpty()
                    .containsExactly(
                        createIncident(savedInstance, deployedInstance, SERVICE_TASK, RESOLVED)));
  }

  @Test
  public void importZeebeIncidentData_importResolvedIncidentInDifferentBatches() {
    // given
    final ProcessInstanceEvent deployedInstance =
        deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.throwErrorIncident(SERVICE_TASK);
    waitUntilIncidentRecordWithProcessIdExported("someProcess");

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getIncidents())
                    .isNotEmpty()
                    .containsExactly(
                        createIncident(savedInstance, deployedInstance, SERVICE_TASK, OPEN)));

    // when
    resolveIncident();
    waitUntilIncidentRecordsWithProcessIdExported(2, "someProcess");
    importAllZeebeEntitiesFromLastIndex();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            savedInstance ->
                assertThat(savedInstance.getIncidents())
                    .isNotEmpty()
                    .containsExactly(
                        createIncident(savedInstance, deployedInstance, SERVICE_TASK, RESOLVED)));
  }

  // Test backwards compatibility for default tenantID applied when importing records pre multi
  // tenancy introduction
  @DisabledIf("isZeebeVersionWithMultiTenancy")
  @Test
  public void importZeebeIncidentData_defaultTenantIdForRecordsWithoutTenantId() {
    // given a process deployed before zeebe implemented multi tenancy
    deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("someProcess"));
    zeebeExtension.failTask(SERVICE_TASK);
    waitUntilIncidentRecordWithProcessIdExported("someProcess");

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .flatExtracting(ProcessInstanceDto::getIncidents)
        .extracting(IncidentDto::getTenantId)
        .singleElement()
        .isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
  }

  @EnabledIf("isZeebeVersionWithMultiTenancy")
  @Test
  public void importZeebeIncidentData_tenantIdImported() {
    // given
    deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("aProcess"));
    zeebeExtension.failTask(SERVICE_TASK);
    waitUntilIncidentRecordsWithProcessIdExported(1, "aProcess");
    final String expectedTenantId = "testTenant";
    setTenantIdForExportedZeebeRecords(ZEEBE_INCIDENT_INDEX_NAME, expectedTenantId);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .flatExtracting(ProcessInstanceDto::getIncidents)
        .extracting(IncidentDto::getTenantId)
        .singleElement()
        .isEqualTo(expectedTenantId);
  }

  private void waitUntilIncidentRecordWithProcessIdExported(final String processId) {
    waitUntilIncidentRecordsWithProcessIdExported(1, processId);
  }

  private TermsQueryContainer getQueryForIncidentEvents() {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeProcessInstanceRecordDto.Fields.intent,
        List.of(IncidentIntent.CREATED.name(), IncidentIntent.RESOLVED.name()));
    return query;
  }

  private IncidentDto createIncident(
      final ProcessInstanceDto processInstanceDto,
      final ProcessInstanceEvent deployedInstance,
      final String activityId,
      final IncidentStatus incidentStatus) {
    final Map<IncidentIntent, List<ZeebeIncidentRecordDto>> incidentsForRecordByIntent =
        getZeebeExportedIncidentEventsByElementId().entrySet().stream()
            .flatMap(entry -> entry.getValue().stream())
            .collect(Collectors.groupingBy(ZeebeRecordDto::getIntent));
    final ZeebeIncidentRecordDto createdRecord =
        incidentsForRecordByIntent.get(IncidentIntent.CREATED).get(0);
    final ZeebeIncidentRecordDto resolvedRecord =
        Optional.ofNullable(incidentsForRecordByIntent.get(IncidentIntent.RESOLVED))
            .map(incidentRecords -> incidentRecords.get(0))
            .orElse(null);
    final IncidentDto incident = new IncidentDto();
    incident.setId(String.valueOf(createdRecord.getKey()));
    incident.setDefinitionKey(deployedInstance.getBpmnProcessId());
    incident.setDefinitionVersion(String.valueOf(deployedInstance.getVersion()));
    incident.setTenantId(ZEEBE_DEFAULT_TENANT_ID);
    incident.setProcessInstanceId(null);
    incident.setActivityId(
        String.valueOf(
            getFlowNodeIdFromProcessInstanceForActivity(processInstanceDto, activityId)));
    incident.setIncidentType(
        IncidentType.valueOfId(createdRecord.getValue().getErrorType().toString()));
    incident.setIncidentMessage(createdRecord.getValue().getErrorMessage());
    incident.setIncidentStatus(incidentStatus);
    final OffsetDateTime createTime =
        OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(createdRecord.getTimestamp()), ZoneId.systemDefault());
    incident.setCreateTime(createTime);
    final OffsetDateTime endTime =
        Optional.ofNullable(resolvedRecord)
            .map(
                record ->
                    OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(record.getTimestamp()), ZoneId.systemDefault()))
            .orElse(null);
    incident.setEndTime(endTime);
    Optional.ofNullable(endTime)
        .ifPresent(end -> incident.setDurationInMs(createTime.until(end, ChronoUnit.MILLIS)));
    return incident;
  }

  @SneakyThrows
  private Map<Long, List<ZeebeIncidentRecordDto>> getZeebeExportedIncidentEventsByElementId() {
    final String expectedIndex =
        zeebeExtension.getZeebeRecordPrefix() + "-" + DatabaseConstants.ZEEBE_INCIDENT_INDEX_NAME;
    return databaseIntegrationTestExtension
        .getZeebeExportedRecordsByQuery(
            expectedIndex, getQueryForIncidentEvents(), ZeebeIncidentRecordDto.class)
        .stream()
        .collect(Collectors.groupingBy(event -> event.getValue().getElementInstanceKey()));
  }

  private void resolveIncident() {
    final ZeebeIncidentRecordDto exportedIncident =
        getZeebeExportedIncidentEventsByElementId().values().stream()
            .flatMap(Collection::stream)
            .findFirst()
            .orElseThrow(
                () -> new OptimizeIntegrationTestException("Cannot find any exported incidents"));
    zeebeExtension.resolveIncident(exportedIncident.getKey());
  }

  private String getFlowNodeIdFromProcessInstanceForActivity(
      final ProcessInstanceDto processInstanceDto, final String activityId) {
    return getPropertyIdFromProcessInstanceForActivity(
        processInstanceDto, activityId, FlowNodeInstanceDto::getFlowNodeId);
  }

  private void waitUntilIncidentRecordsWithProcessIdExported(
      final long minRecordCount, final String processId) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeIncidentRecordDto.Fields.value + "." + ZeebeIncidentDataDto.Fields.bpmnProcessId,
        processId);
    waitUntilRecordMatchingQueryExported(
        minRecordCount, DatabaseConstants.ZEEBE_INCIDENT_INDEX_NAME, query);
  }
}
