/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ExecutingFlowNodeQueryFilterIT extends AbstractFilterIT {

  @Test
  public void filterByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    ProcessInstanceEngineDto secondInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executingFlowNodes = ProcessFilterBuilder
      .filter()
      .executingFlowNodes()
      .id(USER_TASK_ACTIVITY_ID)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executingFlowNodes);
    // then
    assertThat(result.getData().size(), is(1));
    assertThat(result.getData().get(0).getProcessInstanceId(), is(secondInstanceEngineDto.getId()));
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    ProcessInstanceEngineDto secondInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(secondInstanceEngineDto.getId());
    ProcessInstanceEngineDto thirdInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(thirdInstanceEngineDto.getId());
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executedFlowNodes = ProcessFilterBuilder
      .filter()
      .executingFlowNodes()
      .id(USER_TASK_ACTIVITY_ID_2)
      .add()
      .buildList();
    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executedFlowNodes);

    // then
    assertThat(result.getData().size(), is(2));
    assertThat(result.getData().stream().map(RawDataProcessInstanceDto::getProcessInstanceId).collect(Collectors.toList()),
               hasItems(secondInstanceEngineDto.getId(), thirdInstanceEngineDto.getId()));
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(instanceEngineDto.getId());
    ProcessInstanceEngineDto secondInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(secondInstanceEngineDto.getId());
    ProcessInstanceEngineDto thirdInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(thirdInstanceEngineDto.getId());
    ProcessInstanceEngineDto fourthInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> executingFlowNodes = ProcessFilterBuilder.filter()
      .executingFlowNodes()
      .ids(USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_ID_2)
      .add()
      .buildList();

    RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, executingFlowNodes);

    // then
    assertThat(result.getData().size(), is(3));
    assertThat(result.getData().stream().map(RawDataProcessInstanceDto::getProcessInstanceId).collect(Collectors.toList()),
               hasItems(secondInstanceEngineDto.getId(), thirdInstanceEngineDto.getId(), fourthInstanceEngineDto.getId()));
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition,
                                                                 List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData = createReportWithDefinition(processDefinition);
    reportData.setFilter(filter);
    return evaluateReportAndReturnResult(reportData);
  }

}
