/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessCsvExportServiceIT extends AbstractProcessDefinitionIT {

  private static final String FAKE = "FAKE";

  @ParameterizedTest
  @MethodSource("getParameters")
  public void reportCsvHasExpectedValue(ProcessReportDataDto currentReport, String expectedCSV) {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    importAllEngineEntitiesFromScratch();

    currentReport.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
    currentReport.setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv", "Etc/GMT-1");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    String actualContent = getResponseContentAsString(response);
    String stringExpected = getExpectedContentAsString(processInstance, expectedCSV);

    assertThat(actualContent).isEqualTo(stringExpected);
  }

  @Test
  public void durationIsSetCorrectlyEvenWhenNotSortingByDurationOnCsvExport() {
    // given
    OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    OffsetDateTime twoWeeksAgo = now.minusWeeks(2L);
    Long expectedDuration = now.toInstant().toEpochMilli() - twoWeeksAgo.toInstant().toEpochMilli();

    ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleOneUserTasksDefinition();
    ProcessInstanceEngineDto runningInstanceOneWeek = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(runningInstanceOneWeek.getId(), twoWeeksAgo);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto currentReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionEngineDto.getKey())
      .setProcessDefinitionVersion(processDefinitionEngineDto.getVersionAsString())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();

    ReportSortingDto sortingOrder = new ReportSortingDto();
    sortingOrder.setBy(ProcessInstanceIndex.START_DATE);
    String reportId = createAndStoreDefaultReportDefinitionWithSortByDuration(currentReport, sortingOrder);
    String expectedString = FileReaderUtil.readFileWithWindowsLineSeparator(
      "/csv/process/single/raw_process_data_duration_is_calculated_correctly.csv");
    expectedString = expectedString.replace("${PI_ID_1}", runningInstanceOneWeek.getId());
    expectedString = expectedString.replace("${PD_ID_1}", runningInstanceOneWeek.getDefinitionId());
    expectedString = expectedString.replace("${START_DATE_1}", twoWeeksAgo.toString());
    expectedString = expectedString.replace("${DURATION}", expectedDuration.toString());

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    String actualContent = getResponseContentAsString(response);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(actualContent).isEqualTo(expectedString);
  }

  @Test
  public void numberReportCsvExportWorksEvenWithNoData() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    final CanceledInstancesOnlyFilterDto filter = new CanceledInstancesOnlyFilterDto();
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      // We use a canceled instances filter to remove instance data
      .setFilter(filter)
      .build();

    importAllEngineEntitiesFromScratch();
    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(getResponseContentAsString(response)).isNotEmpty();
  }

  @MethodSource("getSortingParamsAndExpectedResults")
  @ParameterizedTest
  public void runningAndCompletedProcessInstancesSortByDuration(SortOrder order) {
    // given
    OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    OffsetDateTime oneDayAgo = now.minusDays(1L);
    OffsetDateTime twoDaysAgo = now.minusDays(2L);
    OffsetDateTime threeDaysAgo = now.minusDays(3L);
    OffsetDateTime oneWeekAgo = now.minusWeeks(1L);
    OffsetDateTime twoWeeksAgo = now.minusWeeks(2L);

    long completedInstanceOneWeekDuration = oneWeekAgo.toInstant().toEpochMilli() - twoWeeksAgo.toInstant()
      .toEpochMilli();
    long completedInstanceOneDayDuration = twoDaysAgo.toInstant().toEpochMilli() - threeDaysAgo.toInstant()
      .toEpochMilli();
    long runningInstanceOneDayDuration = now.toInstant().toEpochMilli() - oneDayAgo.toInstant().toEpochMilli();
    long runningInstanceTwoWeeksDuration = now.toInstant().toEpochMilli() - twoWeeksAgo.toInstant().toEpochMilli();

    ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleOneUserTasksDefinition();
    ProcessInstanceEngineDto completedInstanceOneWeek = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstanceOneWeek.getId());
    engineDatabaseExtension.changeProcessInstanceStartAndEndDate(
      completedInstanceOneWeek.getId(),
      twoWeeksAgo,
      oneWeekAgo
    );

    final ProcessInstanceEngineDto completedInstanceOneDay = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstanceOneDay.getId());
    engineDatabaseExtension.changeProcessInstanceStartAndEndDate(
      completedInstanceOneDay.getId(),
      threeDaysAgo,
      twoDaysAgo
    );

    final ProcessInstanceEngineDto runningInstanceOneDay = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(runningInstanceOneDay.getId(), oneDayAgo);

    final ProcessInstanceEngineDto runningInstanceTwoWeeks = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(runningInstanceTwoWeeks.getId(), twoWeeksAgo);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto currentReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionEngineDto.getKey())
      .setProcessDefinitionVersion(processDefinitionEngineDto.getVersionAsString())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();

    ReportSortingDto sortingOrder = new ReportSortingDto();
    sortingOrder.setOrder(order);
    sortingOrder.setBy(ProcessInstanceIndex.DURATION);
    String reportId = createAndStoreDefaultReportDefinitionWithSortByDuration(currentReport, sortingOrder);
    String expectedString = FileReaderUtil.readFileWithWindowsLineSeparator(
      "/csv/process/single/raw_data_sort_by_duration.csv");
    if (order == SortOrder.ASC) {
      expectedString = expectedString.replace("${PI_ID_1}", runningInstanceOneDay.getId());
      expectedString = expectedString.replace("${PD_ID_1}", runningInstanceOneDay.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_1}", Long.toString(runningInstanceOneDayDuration));
      expectedString = expectedString.replace("${PI_ID_2}", completedInstanceOneDay.getId());
      expectedString = expectedString.replace("${PD_ID_2}", completedInstanceOneDay.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_2}", Long.toString(completedInstanceOneDayDuration));
      expectedString = expectedString.replace("${PI_ID_3}", completedInstanceOneWeek.getId());
      expectedString = expectedString.replace("${PD_ID_3}", completedInstanceOneWeek.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_3}", Long.toString(completedInstanceOneWeekDuration));
      expectedString = expectedString.replace("${PI_ID_4}", runningInstanceTwoWeeks.getId());
      expectedString = expectedString.replace("${PD_ID_4}", runningInstanceTwoWeeks.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_4}", Long.toString(runningInstanceTwoWeeksDuration));
      expectedString = expectedString.replace("${START_DATE_1}", oneDayAgo.toString());
      expectedString = expectedString.replace("${START_DATE_2}", threeDaysAgo.toString());
      expectedString = expectedString.replace("${START_DATE_3}", twoWeeksAgo.toString());
      expectedString = expectedString.replace("${START_DATE_4}", twoWeeksAgo.toString());
      expectedString = expectedString.replace("${END_DATE_2}", twoDaysAgo.toString());
      expectedString = expectedString.replace("${END_DATE_3}", oneWeekAgo.toString());
      expectedString = expectedString.replace("\"${END_DATE_4}\"", "");
    } else {
      expectedString = expectedString.replace("${PI_ID_1}", runningInstanceTwoWeeks.getId());
      expectedString = expectedString.replace("${PD_ID_1}", runningInstanceTwoWeeks.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_1}", Long.toString(runningInstanceTwoWeeksDuration));
      expectedString = expectedString.replace("${PI_ID_2}", completedInstanceOneWeek.getId());
      expectedString = expectedString.replace("${PD_ID_2}", completedInstanceOneWeek.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_2}", Long.toString(completedInstanceOneWeekDuration));
      expectedString = expectedString.replace("${PI_ID_3}", runningInstanceOneDay.getId());
      expectedString = expectedString.replace("${PD_ID_3}", runningInstanceOneDay.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_3}", Long.toString(runningInstanceOneDayDuration));
      expectedString = expectedString.replace("${PI_ID_4}", completedInstanceOneDay.getId());
      expectedString = expectedString.replace("${PD_ID_4}", completedInstanceOneDay.getDefinitionId());
      expectedString = expectedString.replace("${DURATION_4}", Long.toString(completedInstanceOneDayDuration));
      expectedString = expectedString.replace("${START_DATE_1}", twoWeeksAgo.toString());
      expectedString = expectedString.replace("${START_DATE_2}", twoWeeksAgo.toString());
      expectedString = expectedString.replace("${START_DATE_3}", oneDayAgo.toString());
      expectedString = expectedString.replace("${START_DATE_4}", threeDaysAgo.toString());
      expectedString = expectedString.replace("${END_DATE_2}", oneWeekAgo.toString());
      expectedString = expectedString.replace("\"${END_DATE_3}\"", "");
      expectedString = expectedString.replace("${END_DATE_4}", twoDaysAgo.toString());
    }

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    String actualContent = getResponseContentAsString(response);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(actualContent).isEqualTo(expectedString);
  }

  private String getExpectedContentAsString(ProcessInstanceEngineDto processInstance, String expectedCSV) {
    String expectedString = FileReaderUtil.readFileWithWindowsLineSeparator(expectedCSV);
    expectedString = expectedString.replace("${PI_ID}", processInstance.getId());
    expectedString = expectedString.replace("${PD_ID}", processInstance.getDefinitionId());
    return expectedString;
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setId("something");
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner("something");
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createAndStoreDefaultReportDefinitionWithSortByDuration(ProcessReportDataDto reportData,
                                                                         ReportSortingDto order) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setId("something");
    singleProcessReportDefinitionDto.getData().getConfiguration().setSorting(order);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  @SneakyThrows
  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("1", "test");
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleProcessWithVariables(variables);

    OffsetDateTime shiftedStartDate = OffsetDateTime.parse("2018-02-26T14:20:00.000+01:00");
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceEngineDto.getId(), shiftedStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), shiftedStartDate);
    engineDatabaseExtension.changeActivityDuration(processInstanceEngineDto.getId(), START_EVENT, 0L);
    engineDatabaseExtension.changeActivityDuration(processInstanceEngineDto.getId(), END_EVENT, 0L);
    return processInstanceEngineDto;
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = getSimpleBpmnDiagram();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private static ProcessReportDataDto createRunningFlowNodeDurationGroupByFlowNodeTableReport() {
    final ProcessReportDataDto reportDataDto =
      TemplatedProcessReportDataBuilder
        .createReportData()
        .setProcessDefinitionKey(FAKE)
        .setProcessDefinitionVersion(FAKE)
        .setReportDataType(ProcessReportDataType.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE)
        .build();
    reportDataDto.setFilter(ProcessFilterBuilder.filter().runningFlowNodesOnly().filterLevel(FilterApplicationLevel.VIEW).add().buildList());
    return reportDataDto;
  }

  private static Stream<Arguments> getParameters() {
    return Stream.of(
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build(),
        "/csv/process/single/raw_process_data_grouped_by_none.csv",
        "Raw Data Grouped By None"
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
          .build(),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        "Process Instance Frequency Grouped By None"
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
          .build(),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        "Process Instance Frequency Grouped By None"
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
          .build(),
        "/csv/process/single/flownode_frequency_group_by_flownodes.csv",
        "Flow Node Frequency Grouped By Flow Node"
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
          .build(),
        "/csv/process/single/pi_duration_group_by_none.csv",
        "Process Instance Duration Grouped By None"
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE)
          .build(),
        "/csv/process/single/flownode_duration_group_by_flownodes.csv",
        "Flow Node Duration Grouped By Flow Node"
      ),
      Arguments.of(
        createRunningFlowNodeDurationGroupByFlowNodeTableReport(),
        "/csv/process/single/flownode_duration_group_by_flownodes_results.csv",
        "Flow Node Duration Grouped By Flow Node - Running and null duration"
      )
    );
  }

  private static Stream<Arguments> getSortingParamsAndExpectedResults() {
    return Stream.of(
      Arguments.of(SortOrder.ASC),
      Arguments.of(SortOrder.DESC)
    );
  }

}
