/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;

import java.time.OffsetDateTime;

import static org.camunda.optimize.util.BpmnModels.getExternalTaskProcess;
import static org.camunda.optimize.util.BpmnModels.getTwoParallelExternalTaskProcess;

@AllArgsConstructor
@Builder
public class IncidentClient {

  private final EngineIntegrationExtension engineIntegrationExtension;
  private final EngineDatabaseExtension engineDatabaseExtension;


  public void changeIncidentCreationDate(final String processInstanceId,
                                         final OffsetDateTime creationDate) {
    engineDatabaseExtension.changeIncidentCreationDate(processInstanceId, creationDate);
  }

  public void changeIncidentCreationAndEndDateIfPresent(final String processInstanceId,
                                                        final OffsetDateTime creationDate,
                                                        final OffsetDateTime endDate) {
    engineDatabaseExtension.changeIncidentCreationAndEndDateIfPresent(processInstanceId, creationDate, endDate);
  }

  public String deployProcessAndReturnId(final BpmnModelInstance process) {
    return engineIntegrationExtension.deployProcessAndGetId(process);
  }

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithTenantAndWithOpenIncident(final String tenantId) {
    BpmnModelInstance incidentProcess = getExternalTaskProcess();
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(incidentProcess, tenantId);
    createOpenIncident(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithOpenIncident() {
    return deployAndStartProcessInstanceWithTenantAndWithOpenIncident(null);
  }

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithResolvedIncident() {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessInstanceWithOpenIncident();
    engineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public void deployAndStartProcessInstanceWithDeletedIncident() {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessInstanceWithOpenIncident();
    engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
  }

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithTwoOpenIncidents() {
    BpmnModelInstance incidentProcess = getTwoParallelExternalTaskProcess();
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(incidentProcess);
    createOpenIncident(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithDeletedResolvedIncidents() {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessInstanceWithTwoOpenIncidents();
    engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithTwoResolvedIncidents() {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessInstanceWithTwoOpenIncidents();
    engineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceAndCreateOpenIncident(String processDefinitionId) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    createOpenIncident(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceAndCreateResolvedIncident(String processDefinitionId) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startProcessInstanceAndCreateOpenIncident(processDefinitionId);
    engineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceAndCreateDeletedIncident(String processDefinitionId) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    createOpenIncident(processInstanceEngineDto.getId());
    engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceWithoutIncident(String processDefinitionId) {
    return engineIntegrationExtension.startProcessInstance(processDefinitionId);
  }

  public ProcessInstanceEngineDto startProcessInstanceWithCustomIncident(final String processDefinitionId,
                                                                         final String customIncidentType) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    engineIntegrationExtension.createIncident(processInstanceEngineDto.getId(), customIncidentType);
    return processInstanceEngineDto;
  }

  public void createOpenIncident(final String processInstanceId) {
    engineIntegrationExtension.failExternalTasks(processInstanceId);
  }

}
