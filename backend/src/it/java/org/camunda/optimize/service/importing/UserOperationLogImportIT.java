/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.COMPLETED_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;

public class UserOperationLogImportIT extends AbstractImportIT {
  private final BpmnModelInstance testModel = Bpmn.createExecutableProcess()
    .name("aModel")
    .startEvent()
    .userTask()
    .endEvent()
    .done();

  @SneakyThrows
  @Test
  public void importCanBeDisabled() {
    // given
    final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();

    // when
    embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines().values()
      .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
    embeddedOptimizeExtension.reloadConfiguration();

    engineIntegrationExtension.suspendProcessInstance(processInstance.getId());

    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertThat(allProcessInstances.size()).isEqualTo(1);
    assertThat(allProcessInstances.get(0).getState()).isEqualTo(ACTIVE_STATE);
    assertThat(embeddedOptimizeExtension.getImportSchedulerFactory().getImportSchedulers().size()).isGreaterThan(0);
    embeddedOptimizeExtension.getImportSchedulerFactory().getImportSchedulers()
      .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isScheduledToRun()).isFalse());
  }

  @SneakyThrows
  @Test
  public void stateIsUpdated_suspendProcessInstance() {
    // given
    final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();

    // when
    engineIntegrationExtension.suspendProcessInstance(processInstance.getId());
    importAndRefresh();

    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertThat(allProcessInstances.size()).isEqualTo(1);
    assertThat(allProcessInstances.get(0).getState()).isEqualTo(SUSPENDED_STATE);
  }

  @SneakyThrows
  @Test
  public void stateIsUpdated_unsuspendProcessInstance() {
    // given
    final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
    engineDatabaseExtension.changeProcessInstanceState(
      processInstance.getId(),
      SUSPENDED_STATE
    );

    // when
    engineIntegrationExtension.unsuspendProcessInstance(processInstance.getId());
    importAndRefresh();

    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertThat(allProcessInstances.size()).isEqualTo(1);
    assertThat(allProcessInstances.get(0).getState()).isEqualTo(ACTIVE_STATE);
  }

  @SneakyThrows
  @Test
  public void stateIsUpdated_suspendProcessDefinition() {
    // given
    final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();

    // when
    engineIntegrationExtension.suspendProcessDefinition(processInstance.getDefinitionId());
    importAndRefresh();

    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertThat(allProcessInstances.size()).isEqualTo(1);
    assertThat(allProcessInstances.get(0).getState()).isEqualTo(SUSPENDED_STATE);
  }

  @SneakyThrows
  @Test
  public void stateIsUpdated_unsuspendProcessDefinition() {
    // given
    final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
    engineDatabaseExtension.changeProcessInstanceState(
      processInstance.getId(),
      SUSPENDED_STATE
    );

    // when
    engineIntegrationExtension.unsuspendProcessDefinition(processInstance.getDefinitionId());
    importAndRefresh();

    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertThat(allProcessInstances.size()).isEqualTo(1);
    assertThat(allProcessInstances.get(0).getState()).isEqualTo(ACTIVE_STATE);
  }

  @SneakyThrows
  @Test
  public void doNotOverrideCompletedState() {
    // given
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.deployAndStartProcess(testModel);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    importAndRefresh();

    // when
    engineIntegrationExtension.unsuspendProcessInstance(processInstance.getId());
    importAndRefresh();

    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();

    // then
    assertThat(allProcessInstances.size()).isEqualTo(1);
    assertThat(allProcessInstances.get(0).getState()).isEqualTo(COMPLETED_STATE);
  }

  private ProcessInstanceEngineDto startAndImportProcessInstance() {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.deployAndStartProcess(testModel);
    importAndRefresh();
    return processInstance;
  }

  private void importAndRefresh() {
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }
}
