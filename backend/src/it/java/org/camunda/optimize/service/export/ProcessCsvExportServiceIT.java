/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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
  public void rawDataReportWithVariableWithMultipleValues() {
    // given
    final VariableDto listVar = variablesClient.createListJsonObjectVariableDto(List.of("test1", "test2"));
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("1", listVar);
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    OffsetDateTime shiftedStartDate = OffsetDateTime.parse("2018-02-26T14:20:00.000+01:00");
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), shiftedStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), shiftedStartDate);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), START_EVENT, 0L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), END_EVENT, 0L);
    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto report = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();

    final String reportId = createAndStoreDefaultReportDefinition(report);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv", "Etc/GMT-1");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    final String actualContent = getResponseContentAsString(response);
    final String stringExpected = getExpectedContentAsString(
      processInstance,
      "/csv/process/single/raw_process_grouped_by_none_multi_value_variable.csv"
    );

    assertThat(actualContent).isEqualTo(stringExpected);
  }

  @Disabled("Disabled until we fix the time shift issue.")
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

  @Disabled("Disabled until we fix the time shift issue.")
  @MethodSource("getSortingParamsAndExpectedResults")
  @ParameterizedTest
  public void runningAndCompletedProcessInstancesSortByDuration(SortOrder order) {
    // given
    OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    OffsetDateTime twoDaysAgo = now.minusDays(2L);
    OffsetDateTime threeDaysAgo = now.minusDays(3L);
    OffsetDateTime oneWeekAgo = now.minusWeeks(1L);
    OffsetDateTime twoWeeksAgo = now.minusWeeks(2L);

    long completedInstanceOneWeekDuration = oneWeekAgo.toInstant().toEpochMilli() -
      twoWeeksAgo.toInstant().toEpochMilli();
    long completedInstanceOneDayDuration = twoDaysAgo.toInstant().toEpochMilli() -
      threeDaysAgo.toInstant().toEpochMilli();
    long runningInstanceTwoDaysDuration = now.toInstant().toEpochMilli() - twoDaysAgo.toInstant().toEpochMilli();
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

    final ProcessInstanceEngineDto runningInstanceTwoDays = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(runningInstanceTwoDays.getId(), twoDaysAgo);

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
      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        1,
        completedInstanceOneDay,
        threeDaysAgo,
        twoDaysAgo,
        completedInstanceOneDayDuration
      );

      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        2,
        runningInstanceTwoDays,
        twoDaysAgo,
        null,
        runningInstanceTwoDaysDuration
      );

      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        3,
        completedInstanceOneWeek,
        twoWeeksAgo,
        oneWeekAgo,
        completedInstanceOneWeekDuration
      );

      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        4,
        runningInstanceTwoWeeks,
        twoWeeksAgo,
        null,
        runningInstanceTwoWeeksDuration
      );

    } else {
      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        1,
        runningInstanceTwoWeeks,
        twoWeeksAgo,
        null,
        runningInstanceTwoWeeksDuration
      );

      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        2,
        completedInstanceOneWeek,
        twoWeeksAgo,
        oneWeekAgo,
        completedInstanceOneWeekDuration
      );

      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        3,
        runningInstanceTwoDays,
        twoDaysAgo,
        null,
        runningInstanceTwoDaysDuration
      );

      expectedString = replaceExpectedValuesForInstance(
        expectedString,
        4,
        completedInstanceOneDay,
        threeDaysAgo,
        twoDaysAgo,
        completedInstanceOneDayDuration
      );
    }

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    String actualContent = getResponseContentAsString(response);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(actualContent).isEqualTo(expectedString);
  }

  @ParameterizedTest
  @MethodSource("getParametersForCustomDelimiter")
  public void csvExportWorksWithCustomDelimiter(ProcessReportDataDto currentReport, String expectedCSV,
                                                char customDelimiter) {
    // given
    embeddedOptimizeExtension.getConfigurationService().setExportCsvDelimiter(customDelimiter);
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
  public void reportExcludesRawObjectVariableColumn() {
    // given
    final Map<String, Object> objectVarMap = new HashMap<>();
    objectVarMap.put("firstName", "Kermit");
    objectVarMap.put("age", 50);
    final VariableDto objectVar = variablesClient.createMapJsonObjectVariableDto(objectVarMap);
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("objectVar", objectVar);
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    final OffsetDateTime shiftedStartDate = OffsetDateTime.parse("2018-02-26T14:20:00.000+01:00");
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), shiftedStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), shiftedStartDate);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), START_EVENT, 0L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstance.getId(), END_EVENT, 0L);
    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto report = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();

    final String reportId = createAndStoreDefaultReportDefinition(report);

    // when
    final Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv", "Etc/GMT-1");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    final String actualContent = getResponseContentAsString(response);
    final String stringExpected = getExpectedContentAsString(
      processInstance,
      "/csv/process/single/raw_process_data_object_variable.csv"
    );

    assertThat(actualContent).isEqualTo(stringExpected);
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
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceEngineDto.getId(), START_EVENT, 0L);
    engineDatabaseExtension.changeFlowNodeTotalDuration(processInstanceEngineDto.getId(), END_EVENT, 0L);
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
        .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
        .build();
    reportDataDto.setFilter(ProcessFilterBuilder.filter()
                              .runningFlowNodesOnly()
                              .filterLevel(FilterApplicationLevel.VIEW)
                              .add()
                              .buildList());
    return reportDataDto;
  }

  private String replaceExpectedValuesForInstance(String expectedString, int rowNum,
                                                  ProcessInstanceEngineDto processInstance, OffsetDateTime startDate,
                                                  OffsetDateTime endDate, Long duration) {
    expectedString = expectedString.replace(
      "${PI_ID_" + rowNum + "}",
      "\"" + processInstance.getId() + "\""
    );
    expectedString = expectedString.replace(
      "${PD_ID_" + rowNum + "}",
      "\"" + processInstance.getDefinitionId() + "\""
    );
    expectedString = expectedString.replace("${START_DATE_" + rowNum + "}", "\"" + String.valueOf(startDate) + "\"");
    expectedString = expectedString.replace("${DURATION_" + rowNum + "}", "\"" + String.valueOf(duration) + "\"");
    expectedString = expectedString.replace(
      "${END_DATE_" + rowNum + "}",
      endDate == null ? "" : "\"" + endDate + "\""
    );
    return expectedString;
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
          .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
          .build(),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        "Process Instance Frequency Grouped By None"
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
          .build(),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        "Process Instance Frequency Grouped By None"
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
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
          .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
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

  private static Stream<Arguments> getParametersForCustomDelimiter() {
    return Stream.of(
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build(),
        "/csv/process/single/raw_process_data_grouped_by_none_semicolon_delimiter.csv",
        ';'
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
          .build(),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        ';'
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
          .build(),
        "/csv/process/single/flownode_frequency_group_by_flownodes_semicolon_delimiter.csv",
        ';'
      ),
      Arguments.of(
        createRunningFlowNodeDurationGroupByFlowNodeTableReport(),
        "/csv/process/single/flownode_duration_group_by_flownodes_results_semicolon_delimiter.csv",
        ';'
      ),
      Arguments.of(
        TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(FAKE)
          .setProcessDefinitionVersion(FAKE)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build(),
        "/csv/process/single/raw_process_data_grouped_by_none_tabs_delimiter.csv",
        '\t'
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
