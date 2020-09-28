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
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;

import static org.camunda.optimize.util.BpmnModels.getExternalTaskProcess;
import static org.camunda.optimize.util.BpmnModels.getTwoParallelExternalTaskProcess;

@AllArgsConstructor
@Builder
public class IncidentClient {

  private final EngineIntegrationExtension engineIntegrationExtension;

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithTenantAndWithOpenIncident(final String tenantId) {
    BpmnModelInstance incidentProcess = getExternalTaskProcess();
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(incidentProcess, tenantId);
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
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
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto startProcessInstanceWithDeletedResolvedIncidents() {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessInstanceWithTwoOpenIncidents();
    engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public ProcessInstanceEngineDto deployAndStartProcessInstanceWithTwoResolvedIncidents() {
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcessInstanceWithTwoOpenIncidents();
    engineIntegrationExtension.completeExternalTasks(processInstanceEngineDto.getId());
    return processInstanceEngineDto;
  }

  public void startProcessInstanceAndCreateOpenIncident(String processDefinitionId) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
  }

  public void startProcessInstanceAndCreateDeletedIncident(String processDefinitionId) {
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    engineIntegrationExtension.failExternalTasks(processInstanceEngineDto.getId());
    engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
  }

}
