/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.ACTIVE_STATE;
// import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.COMPLETED_STATE;
// import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.util.BpmnModels;
// import java.util.List;
// import lombok.SneakyThrows;
// import org.camunda.bpm.model.bpmn.BpmnModelInstance;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class UserOperationLogImportIT extends AbstractImportIT {
//   private final BpmnModelInstance testModel = BpmnModels.getSingleUserTaskDiagram();
//
//   @SneakyThrows
//   @Test
//   public void importCanBeDisabled() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//
//     // when
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getConfiguredEngines()
//         .values()
//         .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
//     embeddedOptimizeExtension.reloadConfiguration();
//
//     engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstance.getId());
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, ACTIVE_STATE);
//     assertThat(embeddedOptimizeExtension.getImportSchedulerManager().getEngineImportSchedulers())
//         .hasSizeGreaterThan(0);
//     embeddedOptimizeExtension
//         .getImportSchedulerManager()
//         .getEngineImportSchedulers()
//         .forEach(
//             engineImportScheduler ->
//                 assertThat(engineImportScheduler.isScheduledToRun()).isFalse());
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_suspendProcessInstance_ByInstanceId() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, SUSPENDED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_unsuspendProcessInstance_ByInstanceId() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//     engineDatabaseExtension.changeProcessInstanceState(processInstance.getId(), SUSPENDED_STATE);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     engineIntegrationExtension.unsuspendProcessInstanceByInstanceId(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, ACTIVE_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_suspendProcessInstance_ByDefinitionId() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByDefinitionId(
//         processInstance.getDefinitionId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, SUSPENDED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_unsuspendProcessInstance_ByDefinitionId() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//     engineDatabaseExtension.changeProcessInstanceState(processInstance.getId(), SUSPENDED_STATE);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     engineIntegrationExtension.unsuspendProcessInstanceByDefinitionId(
//         processInstance.getDefinitionId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, ACTIVE_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_suspendProcessInstance_ByDefinitionKey() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByDefinitionKey(
//         processInstance.getProcessDefinitionKey());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, SUSPENDED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_unsuspendProcessInstance_ByDefinitionKey() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//     engineDatabaseExtension.changeProcessInstanceState(processInstance.getId(), SUSPENDED_STATE);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     engineIntegrationExtension.unsuspendProcessInstanceByDefinitionKey(
//         processInstance.getProcessDefinitionKey());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, ACTIVE_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_suspendProcessDefinition_ByDefinitionId() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//
//     // when
//     engineIntegrationExtension.suspendProcessDefinitionById(processInstance.getDefinitionId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, SUSPENDED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_unsuspendProcessDefinition_ByDefinitionId() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//     engineDatabaseExtension.changeProcessInstanceState(processInstance.getId(), SUSPENDED_STATE);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     engineIntegrationExtension.unsuspendProcessDefinitionById(processInstance.getDefinitionId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, ACTIVE_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_suspendProcessDefinition_ByDefinitionKey() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//
//     // when
//     engineIntegrationExtension.suspendProcessDefinitionByKey(
//         processInstance.getProcessDefinitionKey());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, SUSPENDED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_unsuspendProcessDefinition_ByDefinitionKey() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//     engineDatabaseExtension.changeProcessInstanceState(processInstance.getId(), SUSPENDED_STATE);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     engineIntegrationExtension.unsuspendProcessDefinitionByKey(
//         processInstance.getProcessDefinitionKey());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, ACTIVE_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_suspendProcessInstance_ViaBatch() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceViaBatch(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//     importAllEngineEntitiesFromLastIndex(); // import twice to ensure running instance import had
// a
//     // chance to rerun
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, SUSPENDED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void stateIsUpdated_unsuspendProcessInstance_ViaBatch() {
//     // given
//     final ProcessInstanceEngineDto processInstance = startAndImportProcessInstance();
//     engineDatabaseExtension.changeProcessInstanceState(processInstance.getId(), SUSPENDED_STATE);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     engineIntegrationExtension.unsuspendProcessInstanceViaBatch(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//     importAllEngineEntitiesFromLastIndex(); // import twice to ensure running instance import had
// a
//     // chance to rerun
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, ACTIVE_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void doNotOverrideCompletedState_suspendProcessInstance_ByInstanceId() {
//     // given
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.deployAndStartProcess(testModel);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, COMPLETED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void doNotOverrideCompletedState_suspendProcessInstance_ByDefinitionId() {
//     // given
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.deployAndStartProcess(testModel);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByDefinitionId(
//         processInstance.getDefinitionId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, COMPLETED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void doNotOverrideCompletedState_suspendProcessInstance_ByDefinitionKey() {
//     // given
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.deployAndStartProcess(testModel);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByDefinitionKey(
//         processInstance.getProcessDefinitionKey());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, COMPLETED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void doNotOverrideCompletedState_suspendProcessDefinition_ByDefinitionId() {
//     // given
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.deployAndStartProcess(testModel);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByDefinitionId(
//         processInstance.getDefinitionId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, COMPLETED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void doNotOverrideCompletedState_suspendProcessDefinition_ByDefinitionKey() {
//     // given
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.deployAndStartProcess(testModel);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceByDefinitionKey(
//         processInstance.getProcessDefinitionKey());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, COMPLETED_STATE);
//   }
//
//   @SneakyThrows
//   @Test
//   public void doNotOverrideCompletedState_suspendProcessInstance_ViaBatch() {
//     // given
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.deployAndStartProcess(testModel);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // when
//     engineIntegrationExtension.suspendProcessInstanceViaBatch(processInstance.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//
//     // then
//     assertInstanceHasState(allProcessInstances, COMPLETED_STATE);
//   }
//
//   private void assertInstanceHasState(
//       final List<ProcessInstanceDto> processInstanceList, final String expectedState) {
//     assertThat(processInstanceList)
//         .hasSize(1)
//         .extracting(ProcessInstanceDto::getState)
//         .containsOnly(expectedState);
//   }
//
//   private ProcessInstanceEngineDto startAndImportProcessInstance() {
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.deployAndStartProcess(testModel, "deployAndStartProcess");
//     importAllEngineEntitiesFromLastIndex();
//     return processInstance;
//   }
// }
