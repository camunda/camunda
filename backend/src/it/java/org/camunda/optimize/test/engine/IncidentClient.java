/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

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
    createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
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
    createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
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
    // start instance with unique businessKey to avoid side effects when businessKey is used as identifier when
    // creating incidents
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId, "businessKey" + UUID.randomUUID());
    createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceAndCreateResolvedIncident(String processDefinitionId) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      startProcessInstanceAndCreateOpenIncident(processDefinitionId);
    engineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceAndCreateDeletedIncident(String processDefinitionId) {
    // start instance with unique businessKey to avoid side effects when businessKey is used as identifier when
    // creating incidents
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId, "businessKey" + UUID.randomUUID());
    createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
    engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceWithoutIncident(String processDefinitionId) {
    return engineIntegrationExtension.startProcessInstance(processDefinitionId);
  }

  public ProcessInstanceEngineDto startProcessInstanceWithCustomIncident(final String processDefinitionId,
                                                                         final String customIncidentType) {
    // start instance with unique businessKey to avoid side effects when businessKey is used as identifier when
    // creating incidents
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId, "businessKey" + UUID.randomUUID());
    engineIntegrationExtension.createIncident(processInstanceEngineDto.getId(), customIncidentType);
    return processInstanceEngineDto;
  }

  public void createOpenIncidentForInstancesWithBusinessKey(final String businessKey) {
    // Note: To create an incident, first tasks are locked and then they are failed.
    // The businessKey is required to lock only the tasks of the workers with that businessKey (the instanceId
    // cannot be used for this). Otherwise, all tasks are locked and no further incidents can be created for other
    // instances.
    engineIntegrationExtension.failExternalTasks(businessKey);
  }

  public void resolveOpenIncidents(final String processInstanceId) {
    engineIntegrationExtension.completeExternalTasks(processInstanceId);
  }

}
