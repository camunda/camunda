/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.process;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.util.BpmnModels.USER_TASK_1;
// import static io.camunda.optimize.util.BpmnModels.USER_TASK_2;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class ExecutingFlowNodeQueryFilterIT extends AbstractFilterIT {
//
//   @Test
//   public void filterByOneFlowNode() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto instanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
//     ProcessInstanceEngineDto secondInstanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> executingFlowNodes =
//         ProcessFilterBuilder.filter().executingFlowNodes().id(USER_TASK_1).add().buildList();
//
//     ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
//         evaluateReportWithFilter(processDefinition, executingFlowNodes);
//     // then
//     assertThat(result.getData())
//         .singleElement()
//         .satisfies(
//             data ->
//
// assertThat(data.getProcessInstanceId()).isEqualTo(secondInstanceEngineDto.getId()));
//   }
//
//   @Test
//   public void filterMultipleProcessInstancesByOneFlowNode() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto instanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
//     ProcessInstanceEngineDto secondInstanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(secondInstanceEngineDto.getId());
//     ProcessInstanceEngineDto thirdInstanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(thirdInstanceEngineDto.getId());
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> executedFlowNodes =
//         ProcessFilterBuilder.filter().executingFlowNodes().id(USER_TASK_2).add().buildList();
//     ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
//         evaluateReportWithFilter(processDefinition, executedFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(2);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4);
//     assertThat(result.getData())
//         .hasSize(2)
//         .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
//         .containsExactlyInAnyOrder(secondInstanceEngineDto.getId(),
// thirdInstanceEngineDto.getId());
//   }
//
//   @Test
//   public void filterByMultipleAndCombinedFlowNodes() {
//     // given
//     ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
//     ProcessInstanceEngineDto instanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
//     ProcessInstanceEngineDto secondInstanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(secondInstanceEngineDto.getId());
//     ProcessInstanceEngineDto thirdInstanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     engineIntegrationExtension.finishAllRunningUserTasks(thirdInstanceEngineDto.getId());
//     ProcessInstanceEngineDto fourthInstanceEngineDto =
//         engineIntegrationExtension.startProcessInstance(processDefinition.getId());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     List<ProcessFilterDto<?>> executingFlowNodes =
//         ProcessFilterBuilder.filter()
//             .executingFlowNodes()
//             .ids(USER_TASK_1, USER_TASK_2)
//             .add()
//             .buildList();
//
//     ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
//         evaluateReportWithFilter(processDefinition, executingFlowNodes);
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(3);
//     assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(4);
//     assertThat(result.getData())
//         .hasSize(3)
//         .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
//         .containsExactlyInAnyOrder(
//             secondInstanceEngineDto.getId(),
//             thirdInstanceEngineDto.getId(),
//             fourthInstanceEngineDto.getId());
//   }
// }
