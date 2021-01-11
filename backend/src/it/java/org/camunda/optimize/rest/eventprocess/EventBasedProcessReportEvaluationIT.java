/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
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

  @ParameterizedTest(name = "using filter class {0}")
  @MethodSource("flowNodeStatusFiltersAndExpectedResults")
  public void reportsUsingEventBasedProcessCanBeEvaluatedUsingFlowNodeStatusFilters(Class<?> filterType,
                                                                                    List<ProcessFilterDto<?>> filters,
                                                                                    List<Tuple> expectedResults) {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartProcess();
    if (CanceledFlowNodesOnlyFilterDto.class.equals(filterType)) {
      engineIntegrationExtension.cancelActivityInstance(processInstanceEngineDto.getId(), USER_TASK_ID_ONE);
    } else if (!RunningFlowNodesOnlyFilterDto.class.equals(filterType)) {
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
    processReportDataDto.setFilter(filters);
    ReportMapResultDto result = reportClient.evaluateMapReport(processReportDataDto).getResult();

    // then the event process instance appears in the results
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getData()).hasSize(expectedResults.size())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResults);
  }

  private static Stream<Arguments> flowNodeStatusFiltersAndExpectedResults() {
    return Stream.of(
      Arguments.of(
        RunningFlowNodesOnlyFilterDto.class,
        ProcessFilterBuilder.filter().runningFlowNodesOnly().filterLevel(VIEW).add().buildList(),
        Collections.singletonList(Tuple.tuple(USER_TASK_ID_ONE, 1.))
      ),
      Arguments.of(
        CompletedOrCanceledFlowNodesOnlyFilterDto.class,
        ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().filterLevel(VIEW).add().buildList(),
        Arrays.asList(
          Tuple.tuple(BPMN_START_EVENT_ID, 1.),
          Tuple.tuple(USER_TASK_ID_ONE, 1.),
          Tuple.tuple(BPMN_END_EVENT_ID, 1.)
        )
      ),
      Arguments.of(
        CanceledFlowNodesOnlyFilterDto.class,
        ProcessFilterBuilder.filter().canceledFlowNodesOnly().filterLevel(VIEW).add().buildList(),
        Collections.singletonList(Tuple.tuple(USER_TASK_ID_ONE, 1.))
      )
    );
  }

}
