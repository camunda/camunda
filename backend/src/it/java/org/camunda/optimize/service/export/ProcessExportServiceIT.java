/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessExportServiceIT extends AbstractIT {

  private static final String START = "aStart";
  private static final String END = "anEnd";
  private static final String FAKE = "FAKE";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  @ParameterizedTest
  @MethodSource("getParameters")
  public void reportCsvHasExpectedValue(ProcessReportDataDto currentReport, String expectedCSV) throws Exception {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    currentReport.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
    currentReport.setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected = getExpectedContentAsString(processInstance, expectedCSV);

    assertThat(actualContent, is(stringExpected));
  }

  private String getExpectedContentAsString(ProcessInstanceEngineDto processInstance, String expectedCSV) {
    String expectedString = FileReaderUtil.readFileWithWindowsLineSeparator(expectedCSV);
    expectedString = expectedString.replace("${PI_ID}", processInstance.getId());
    expectedString = expectedString.replace("${PD_ID}", processInstance.getDefinitionId());
    return expectedString;
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setId("something");
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner("something");
    return createNewReport(singleProcessReportDefinitionDto);
  }

  private String createNewReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  @SneakyThrows
  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("1", "test");
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleProcessWithVariables(variables);

    OffsetDateTime shiftedStartDate = OffsetDateTime.parse("2018-02-26T14:20:00.000+01:00");
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceEngineDto.getId(), shiftedStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), shiftedStartDate);
    engineDatabaseExtension.changeActivityDuration(processInstanceEngineDto.getId(), START, 0L);
    engineDatabaseExtension.changeActivityDuration(processInstanceEngineDto.getId(), END, 0L);
    return processInstanceEngineDto;
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .endEvent(END)
      .done();
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
    reportDataDto.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.RUNNING);
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
        "Raw Data Grouped By None"),
      Arguments.of(
        TemplatedProcessReportDataBuilder
                     .createReportData()
                     .setProcessDefinitionKey(FAKE)
                     .setProcessDefinitionVersion(FAKE)
                     .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
                     .build(),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        "Process Instance Frequency Grouped By None"),
      Arguments.of(
        TemplatedProcessReportDataBuilder
                     .createReportData()
                     .setProcessDefinitionKey(FAKE)
                     .setProcessDefinitionVersion(FAKE)
                     .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
                     .build(),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        "Process Instance Frequency Grouped By None"),
      Arguments.of(
        TemplatedProcessReportDataBuilder
                     .createReportData()
                     .setProcessDefinitionKey(FAKE)
                     .setProcessDefinitionVersion(FAKE)
                     .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
                     .build(),
        "/csv/process/single/flownode_frequency_group_by_flownodes.csv",
        "Flow Node Frequency Grouped By Flow Node"),
      Arguments.of(
        TemplatedProcessReportDataBuilder
                     .createReportData()
                     .setProcessDefinitionKey(FAKE)
                     .setProcessDefinitionVersion(FAKE)
                     .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
                     .build(),
        "/csv/process/single/pi_duration_group_by_none.csv",
        "Process Instance Duration Grouped By None"),
      Arguments.of(
        TemplatedProcessReportDataBuilder
                     .createReportData()
                     .setProcessDefinitionKey(FAKE)
                     .setProcessDefinitionVersion(FAKE)
                     .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
                     .build(),
        "/csv/process/single/flownode_duration_group_by_flownodes.csv",
        "Flow Node Duration Grouped By Flow Node"),
      Arguments.of(createRunningFlowNodeDurationGroupByFlowNodeTableReport(),
                   "/csv/process/single/flownode_duration_group_by_flownodes_no_values.csv",
                   "Flow Node Duration Grouped By Flow Node - Running and null duration")
    );
  }

}