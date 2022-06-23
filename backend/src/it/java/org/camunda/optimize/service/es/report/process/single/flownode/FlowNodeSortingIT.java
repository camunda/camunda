/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;

public class FlowNodeSortingIT extends AbstractProcessDefinitionIT {

  private static final String LABEL_SUFFIX = "_label";
  private static final String TEST_ACTIVITY = "testActivity";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";
  private static final String USER_TASK = "userTask";

  @Test
  public void customOrderOnResultLabelForFrequencyReports() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithTwoTasksAndLabels();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processInstanceDto);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(4L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getLabel)
      .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void customOrderOnResultLabelForDurationReports() {
    // given
    final ProcessInstanceEngineDto processDefinition = deployProcessWithTwoTasksAndLabels();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.ASC));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(getExecutedFlowNodeDuration(result)).isEqualTo(4L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getLabel)
      .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void ifNameIsNotAvailableKeyIsUsedAsLabel() {
    // given
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("startKey")
        .name("startName")
      .serviceTask("task1Key")
        .name("task1Name")
        .camundaExpression("${true}")
      .serviceTask("task2Key")
        .name("")
        .camundaExpression("${true}")
      .endEvent("endKey")
        .name(null)
      .done();
    // @formatter:on
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.deployAndStartProcess(modelInstance);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processInstanceDto);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(4L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getLabel)
      .containsExactly("endKey", "startName", "task1Name", "task2Key");
  }

  @Test
  public void labelSortingIsCaseInsensitive() {
    // given
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
        .name("ax")
      .serviceTask(TEST_ACTIVITY)
        .name("fooBar1")
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .name("Ac")
        .camundaExpression("${true}")
      .endEvent("end")
        .name("foobar2")
      .done();
    // @formatter:on
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.deployAndStartProcess(modelInstance);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processInstanceDto);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(4L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getLabel)
      .containsExactly("Ac", "ax", "fooBar1", "foobar2");
  }

  @Test
  public void keySortingIsCaseInsensitive() {
    // given
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("ax")
      .serviceTask("fooBar1")
        .camundaExpression("${true}")
      .serviceTask("Ac")
        .camundaExpression("${true}")
      .endEvent("foobar2")
      .done();
    // @formatter:on
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.deployAndStartProcess(modelInstance);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processInstanceDto);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(4L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getLabel)
      .containsExactly("Ac", "ax", "fooBar1", "foobar2");
  }

  @Test
  public void descendingOrderOnResultValueForDurationReportsWithNullsLast() {
    testExpectedResultValueOrderForDurationReports(Comparator.nullsLast(Comparator.reverseOrder()), SortOrder.DESC);
  }

  @Test
  public void ascendingOnResultValueForDurationReportsWithNullsLast() {
    testExpectedResultValueOrderForDurationReports(Comparator.nullsLast(Comparator.naturalOrder()), SortOrder.ASC);
  }

  private void testExpectedResultValueOrderForDurationReports(final Comparator<Double> expectedOrderComparator,
                                                              final SortOrder sortOrder) {
    // given
    final ProcessInstanceEngineDto processDefinition = deployProcessWithServiceAndUserTask();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, sortOrder));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    // end activity not executed due running userTask
    assertThat(getExecutedFlowNodeDuration(result)).isEqualTo(3L);
    assertThat(result.getFirstMeasureData())
      .hasSize(4)
      .extracting(MapResultEntryDto::getValue)
      .isSortedAccordingTo(expectedOrderComparator);
  }

  private ProcessReportDataDto createReport(ProcessInstanceEngineDto processInstanceDto) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
  }

  private ProcessInstanceEngineDto deployProcessWithTwoTasksAndLabels() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
        .name("start" + LABEL_SUFFIX)
      .serviceTask(TEST_ACTIVITY)
        .name(TEST_ACTIVITY + LABEL_SUFFIX)
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .name(TEST_ACTIVITY_2 + LABEL_SUFFIX)
        .camundaExpression("${true}")
      .endEvent("end")
        .name(null)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(modelInstance);
  }

  private ProcessInstanceEngineDto deployProcessWithServiceAndUserTask() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
        .name("start" + LABEL_SUFFIX)
      .serviceTask(TEST_ACTIVITY)
        .name(TEST_ACTIVITY + LABEL_SUFFIX)
        .camundaExpression("${true}")
      .userTask(USER_TASK)
        .name(USER_TASK + LABEL_SUFFIX)
      .endEvent("end")
        .name(null)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(modelInstance);
  }

  private ProcessReportDataDto getAverageFlowNodeDurationGroupByFlowNodeReport(ProcessInstanceEngineDto processDefinition) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processDefinition.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .build();
  }

  private long getExecutedFlowNodeCount(ReportResultResponseDto<List<MapResultEntryDto>> resultList) {
    return resultList.getFirstMeasureData().stream().filter(result -> result.getValue() > 0).count();
  }

  private long getExecutedFlowNodeDuration(ReportResultResponseDto<List<MapResultEntryDto>> resultList) {
    return resultList.getFirstMeasureData()
      .stream()
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .count();
  }
}
