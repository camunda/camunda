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
// import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
// import static io.camunda.optimize.util.BpmnModels.END_EVENT;
// import static io.camunda.optimize.util.BpmnModels.SERVICE_TASK;
// import static io.camunda.optimize.util.BpmnModels.START_EVENT;
// import static io.camunda.optimize.util.BpmnModels.USER_TASK_1;
// import static io.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.groups.Tuple.tuple;
//
// import io.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
// import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
// import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
// import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
// import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
// import io.camunda.optimize.service.util.configuration.engine.DefaultTenant;
// import io.camunda.optimize.util.BpmnModels;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import lombok.SneakyThrows;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
//
// @Tag(OPENSEARCH_PASSING)
// public class EngineActivityImportIT extends AbstractImportIT {
//
//   private static final Set<String> PROCESS_INSTANCE_NULLABLE_FIELDS =
//       Collections.singleton(ProcessInstanceIndex.TENANT_ID);
//
//   @Test
//   public void definitionDataIsPresentOnFlowNodesAfterActivityImport() {
//     // given
//     deployAndStartUserTaskProcess();
//     final String tenantId = "tenant1";
//     engineIntegrationExtension.createTenant(tenantId);
//     engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSimpleBpmnDiagram(),
// tenantId);
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     final List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances)
//         .hasSize(2)
//         .allSatisfy(
//             processInstanceDto ->
//                 assertThat(processInstanceDto.getFlowNodeInstances())
//                     .allSatisfy(
//                         flowNodeInstanceDto -> {
//                           assertThat(flowNodeInstanceDto.getDefinitionKey())
//                               .isEqualTo(processInstanceDto.getProcessDefinitionKey());
//                           assertThat(flowNodeInstanceDto.getDefinitionVersion())
//                               .isEqualTo(processInstanceDto.getProcessDefinitionVersion());
//                           assertThat(flowNodeInstanceDto.getTenantId())
//                               .isEqualTo(processInstanceDto.getTenantId());
//                         }));
//   }
//
//   @Test
//   public void defaultTenantIsUsedOnActivityImport() {
//     // given
//     final String defaultTenant = "lobster";
//     embeddedOptimizeExtension
//         .getDefaultEngineConfiguration()
//         .setDefaultTenant(new DefaultTenant(defaultTenant, defaultTenant));
//     deployAndStartUserTaskProcess();
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     final List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances)
//         .singleElement()
//         .extracting(ProcessInstanceDto::getFlowNodeInstances)
//         .satisfies(
//             flowNodes ->
//                 assertThat(flowNodes)
//                     .allSatisfy(
//                         flowNode ->
// assertThat(flowNode.getTenantId()).isEqualTo(defaultTenant)));
//   }
//
//   @Test
//   public void canceledFlowNodesAreImportedWithCorrectValue() {
//     // given
//     final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcess();
//     engineIntegrationExtension.cancelActivityInstance(
//         processInstanceEngineDto.getId(), USER_TASK_1);
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertAllEntriesInElasticsearchHaveAllData(
//         PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class,
// PROCESS_INSTANCE_NULLABLE_FIELDS);
//     final List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances).hasSize(1);
//     assertThat(allProcessInstances.get(0).getFlowNodeInstances())
//         .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
//         .containsExactlyInAnyOrder(tuple(START_EVENT, false), tuple(USER_TASK_1, true));
//   }
//
//   @Test
//   public void canceledFlowNodesAreUpdatedIfAlreadyImported() {
//     // given
//     final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcess();
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances).hasSize(1);
//     assertThat(allProcessInstances.get(0).getFlowNodeInstances())
//         .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
//         .containsExactlyInAnyOrder(tuple(START_EVENT, false), tuple(USER_TASK_1, false));
//
//     // when
//     engineIntegrationExtension.cancelActivityInstance(
//         processInstanceEngineDto.getId(), USER_TASK_1);
//     importAllEngineEntitiesFromLastIndex();
//
//     // then
//     assertAllEntriesInElasticsearchHaveAllData(
//         PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class,
// PROCESS_INSTANCE_NULLABLE_FIELDS);
//     allProcessInstances = databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances).hasSize(1);
//     assertThat(allProcessInstances.get(0).getFlowNodeInstances())
//         .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
//         .containsExactlyInAnyOrder(tuple(START_EVENT, false), tuple(USER_TASK_1, true));
//   }
//
//   @Test
//   public void canceledFlowNodesByCanceledProcessInstanceAreUpdatedIfAlreadyImported() {
//     // given
//     final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcess();
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances).hasSize(1);
//     assertThat(allProcessInstances.get(0).getFlowNodeInstances())
//         .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
//         .containsExactlyInAnyOrder(tuple(START_EVENT, false), tuple(USER_TASK_1, false));
//
//     // when the process instance is canceled
//     engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // then
//     assertAllEntriesInElasticsearchHaveAllData(
//         PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class,
// PROCESS_INSTANCE_NULLABLE_FIELDS);
//     allProcessInstances = databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances).hasSize(1);
//     assertThat(allProcessInstances.get(0).getFlowNodeInstances())
//         .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
//         .containsExactlyInAnyOrder(tuple(START_EVENT, false), tuple(USER_TASK_1, true));
//   }
//
//   @Test
//   public void flowNodesWithoutProcessDefinitionKeyCanBeImported() {
//     // given
//     deployAndStartSimpleServiceTaskProcess();
//     engineDatabaseExtension.removeProcessDefinitionKeyFromAllHistoricFlowNodes();
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     final List<ProcessInstanceDto> allProcessInstances =
//         databaseIntegrationTestExtension.getAllProcessInstances();
//     assertThat(allProcessInstances).hasSize(1);
//     assertThat(allProcessInstances.get(0).getFlowNodeInstances())
//         .extracting(FlowNodeInstanceDto::getFlowNodeId)
//         .containsExactlyInAnyOrder(START_EVENT, SERVICE_TASK, END_EVENT);
//   }
//
//   @Test
//   public void runningActivitiesAreNotSkippedDuringImport() {
//     // given
//     deployAndStartUserTaskProcess();
//     deployAndStartSimpleServiceTaskProcess();
//
//     // when
//     engineIntegrationExtension.finishAllRunningUserTasks();
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class))
//         .allSatisfy(
//             processInstance -> assertThat(processInstance.getFlowNodeInstances()).hasSize(3));
//   }
//
//   @Test
//   @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
//   // Dependent on implementation of Reporting functionality for OpenSearch
//   @EnabledIfSystemProperty(named = "CAMUNDA_OPTIMIZE_DATABASE", matches = "elasticsearch")
//   public void deletionOfProcessInstancesDoesNotDistortActivityInstanceImport() {
//     // given
//     final Map<String, Object> variables = new HashMap<>();
//     variables.put("aVariable", "aStringVariable");
//     final ProcessInstanceEngineDto firstProcInst =
//         createImportAndDeleteTwoProcessInstancesWithVariables(variables);
//
//     // when
//     variables.put("secondVar", "foo");
//     engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId(), variables);
//     variables.put("thirdVar", "bar");
//     engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId(), variables);
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     ProcessToQueryDto processToQuery = new ProcessToQueryDto();
//     processToQuery.setProcessDefinitionKey(firstProcInst.getProcessDefinitionKey());
//     processToQuery.setProcessDefinitionVersion(firstProcInst.getProcessDefinitionVersion());
//
//     ProcessVariableNameRequestDto variableRequestDto =
//         new ProcessVariableNameRequestDto(List.of(processToQuery));
//
//     final List<ProcessVariableNameResponseDto> variablesResponseDtos =
//         variablesClient.getProcessVariableNames(variableRequestDto);
//
//     assertThat(variablesResponseDtos).hasSize(3);
//   }
//
//   @Test
//   public void importRunningAndCompletedHistoricActivityInstances() {
//     // given
//     engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class))
//         .singleElement()
//         .satisfies(
//             processInstance -> assertThat(processInstance.getFlowNodeInstances()).hasSize(2));
//   }
//
//   @Test
//   public void completedActivitiesOverwriteRunningActivities() {
//     // given
//     engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());
//
//     // when
//     importAllEngineEntitiesFromScratch();
//     engineIntegrationExtension.finishAllRunningUserTasks();
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class))
//         .singleElement()
//         .satisfies(
//             processInstance ->
//                 assertThat(processInstance.getFlowNodeInstances())
//                     .allSatisfy(
//                         flowNodeInstance ->
// assertThat(flowNodeInstance.getEndDate()).isNotNull()));
//   }
//
//   @Test
//   public void runningActivitiesDoNotOverwriteCompletedActivities() {
//     // given
//     engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final HistoricActivityInstanceEngineDto startEvent =
//         engineIntegrationExtension.getHistoricActivityInstances().stream()
//             .filter(a -> START_EVENT.equals(a.getActivityName()))
//             .findFirst()
//             .get();
//     startEvent.setEndTime(null);
//     startEvent.setDurationInMillis(null);
//
// embeddedOptimizeExtension.importRunningActivityInstance(Collections.singletonList(startEvent));
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertThat(
//             databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//                 PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class))
//         .singleElement()
//         .satisfies(
//             processInstance ->
//                 assertThat(processInstance.getFlowNodeInstances())
//                     .allSatisfy(
//                         flowNodeInstance ->
// assertThat(flowNodeInstance.getEndDate()).isNotNull()));
//   }
//
//   @Test
//   public void afterRestartOfOptimizeOnlyNewActivitiesAreImported() {
//     // given
//     startAndUseNewOptimizeInstance();
//     deployAndStartSimpleServiceTaskProcess();
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     startAndUseNewOptimizeInstance();
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(getImportedActivityCount()).isEqualTo(3L);
//   }
//
//   private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(
//       final Map<String, Object> variables) {
//     final ProcessInstanceEngineDto firstProcInst =
//         deployAndStartSimpleServiceProcessTaskWithVariables(variables);
//     final ProcessInstanceEngineDto secondProcInst =
//         engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId(),
// variables);
//     importAllEngineEntitiesFromScratch();
//     engineIntegrationExtension.deleteHistoricProcessInstance(firstProcInst.getId());
//     engineIntegrationExtension.deleteHistoricProcessInstance(secondProcInst.getId());
//     return firstProcInst;
//   }
//
//   @SneakyThrows
//   private Long getImportedActivityCount() {
//     return databaseIntegrationTestExtension.getImportedActivityCount();
//   }
// }
