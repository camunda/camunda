/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MixedFilterIT extends AbstractFilterIT {

  @Test
  public void applyCombinationOfFiltersForFinishedInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

      // this is the process instance that should be filtered
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    final String expectedInstanceId = instanceEngineDto.getId();
    engineIntegrationExtension.finishAllRunningUserTasks(expectedInstanceId);

      // wrong not executed flow node
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);

    // wrong variable
    variables.put("var", "anotherValue");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());

    // wrong date
    Thread.sleep(1000L);
    variables.put("var", "value");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    OffsetDateTime start = engineIntegrationExtension.getHistoricProcessInstance(instanceEngineDto.getId()).getStartTime();
    OffsetDateTime end = engineIntegrationExtension.getHistoricProcessInstance(instanceEngineDto.getId()).getEndTime();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filterList = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id(USER_TASK_ACTIVITY_ID)
      .add()
      .variable()
      .stringType()
      .values(Collections.singletonList("value"))
      .name("var")
      .operator(IN)
      .add()
      .fixedStartDate()
      .start(null)
      .end(start.minusSeconds(1L))
      .add()
      .fixedEndDate()
      .start(null)
      .end(end.minusSeconds(1L))
      .add()
      .buildList();

    RawDataProcessReportResultDto rawDataReportResultDto = evaluateReportWithFilter(processDefinition, filterList);

    // then
    assertThat(rawDataReportResultDto.getData().size(), is(1));
    assertThat(rawDataReportResultDto.getData().get(0).getProcessInstanceId(), is(expectedInstanceId));
  }

  @Test
  public void applyCombinationOfFiltersForInProgressInstance() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value");

    // this is the process instance that should be filtered
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    final String expectedInstanceId = instanceEngineDto.getId();

    // wrong variable
    variables.put("var", "anotherValue");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());

    // wrong date
    Thread.sleep(1000L);
    variables.put("var", "value");
    instanceEngineDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    engineIntegrationExtension.finishAllRunningUserTasks(instanceEngineDto.getId());
    OffsetDateTime start = engineIntegrationExtension.getHistoricProcessInstance(instanceEngineDto.getId()).getStartTime();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessFilterDto> filterList = ProcessFilterBuilder
      .filter()
      .executingFlowNodes()
      .id(USER_TASK_ACTIVITY_ID)
      .add()
      .variable()
      .stringType()
      .values(Collections.singletonList("value"))
      .name("var")
      .operator(IN)
      .add()
      .fixedStartDate()
      .start(null)
      .end(start.minusSeconds(1L))
      .add()
      .buildList();

    RawDataProcessReportResultDto rawDataReportResultDto = evaluateReportWithFilter(processDefinition, filterList);

    // then
    assertThat(rawDataReportResultDto.getData().size(), is(1));
    assertThat(rawDataReportResultDto.getData().get(0).getProcessInstanceId(), is(expectedInstanceId));
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition, List<ProcessFilterDto> filter) {
    ProcessReportDataDto reportData =
      createReportWithCompletedInstancesFilter(processDefinition);
    reportData.setFilter(filter);
    return evaluateReportWithRawDataResult(reportData).getResult();
  }

  private ProcessReportDataDto createReportWithCompletedInstancesFilter(ProcessDefinitionEngineDto processDefinition) {
    ProcessReportDataDto processReportDataDto = createReportWithDefinition(processDefinition);
    processReportDataDto.setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList());
    return processReportDataDto;
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .userTask(USER_TASK_ACTIVITY_ID)
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

}
