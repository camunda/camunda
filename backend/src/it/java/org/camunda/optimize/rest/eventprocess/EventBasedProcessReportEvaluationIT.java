/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;

public class EventBasedProcessReportEvaluationIT extends AbstractEventProcessIT {

  @Test
  public void reportsUsingEventBasedProcessCanBeEvaluated() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    importEngineEntities();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
        BPMN_END_EVENT_ID
      )
    );
    engineIntegrationExtension.finishAllRunningUserTasks();
    importEngineEntities();

    // when
    executeImportCycle();

    // then
    final List<EventProcessInstanceDto> processInstances = getEventProcessInstancesFromElasticsearch();
    assertThat(processInstances)
      .singleElement()
      .satisfies(processInstanceDto -> assertProcessInstance(
        processInstanceDto,
        processInstanceEngineDto.getBusinessKey(),
        Arrays.asList(BPMN_START_EVENT_ID, USER_TASK_ID_ONE, BPMN_END_EVENT_ID)
      ));

    // when a report that uses the definition for that instance is evaluated
    final EventProcessInstanceDto savedInstance = processInstances.get(0);
    ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setProcessDefinitionKey(savedInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(savedInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    final RawDataProcessReportResultDto result = reportClient.evaluateRawReport(processReportDataDto).getResult();

    // then the event process instance appears in the results
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getData()).singleElement().satisfies(
      resultInstance -> assertThat(resultInstance.getProcessInstanceId()).isEqualTo(savedInstance.getProcessInstanceId()));
  }

  @ParameterizedTest
  @MethodSource("flowNodeExecutionStatesAndExpectedCounts")
  public void reportsUsingEventBasedProcessCanBeEvaluatedUsingFlowNodeExecutionState(FlowNodeExecutionState state,
                                                                                     Double expectedStartCount,
                                                                                     Double expectedUserTaskCount,
                                                                                     Double expectedEndCount) {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    if (FlowNodeExecutionState.CANCELED.equals(state)) {
      engineIntegrationExtension.cancelActivityInstance(processInstanceEngineDto.getId(), USER_TASK_ID_ONE);
    } else if (!FlowNodeExecutionState.RUNNING.equals(state)) {
      engineIntegrationExtension.finishAllRunningUserTasks();
    }
    importEngineEntities();
    publishEventMappingUsingProcessInstanceCamundaEvents(
      processInstanceEngineDto,
      createMappingsForEventProcess(
        processInstanceEngineDto,
        BPMN_START_EVENT_ID,
        applyCamundaTaskStartEventSuffix(USER_TASK_ID_ONE),
        BPMN_END_EVENT_ID
      )
    );
    importEngineEntities();
    executeImportCycle();
    executeImportCycle();

    // when a report that uses the definition for that instance is evaluated
    final EventProcessInstanceDto savedInstance = getEventProcessInstancesFromElasticsearch().get(0);
    ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setProcessDefinitionKey(savedInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(savedInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    processReportDataDto.getConfiguration().setFlowNodeExecutionState(state);
    ReportMapResultDto result = reportClient.evaluateMapReport(processReportDataDto).getResult();

    // then the event process instance appears in the results
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getData()).hasSize(3).extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrder(
        Tuple.tuple(BPMN_START_EVENT_ID, expectedStartCount),
        Tuple.tuple(USER_TASK_ID_ONE, expectedUserTaskCount),
        Tuple.tuple(BPMN_END_EVENT_ID, expectedEndCount)
      );
  }

  private static Stream<Arguments> flowNodeExecutionStatesAndExpectedCounts() {
    return Stream.of(
      Arguments.of(FlowNodeExecutionState.RUNNING, null, 1., null),
      Arguments.of(FlowNodeExecutionState.COMPLETED, 1., 1., 1.),
      Arguments.of(FlowNodeExecutionState.CANCELED, null, 1., null),
      Arguments.of(FlowNodeExecutionState.ALL, 1., 1., 1.)
    );
  }

}
