/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
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
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessCsvExportServiceIT extends AbstractIT {
  private static final String FAKE = "FAKE";

  @ParameterizedTest
  @MethodSource("getParameters")
  public void reportCsvHasExpectedValue(ProcessReportDataDto currentReport, String expectedCSV) {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    importAllEngineEntitiesFromScratch();

    currentReport.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
    currentReport.setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    String actualContent = getResponseContentAsString(response);
    String stringExpected = getExpectedContentAsString(processInstance, expectedCSV);

    assertThat(actualContent).isEqualTo(stringExpected);
  }

  @Test
  public void numberReportCsvExportWorksEvenWithNoData() {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      // We use a canceled instances filter to remove instance data
      .setFilter(new CanceledInstancesOnlyFilterDto())
      .build();

    importAllEngineEntitiesFromScratch();
    String reportId = createAndStoreDefaultReportDefinition(reportData);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(getResponseContentAsString(response)).isNotEmpty();
  }

  private String getExpectedContentAsString(ProcessInstanceEngineDto processInstance, String expectedCSV) {
    String expectedString = FileReaderUtil.readFileWithWindowsLineSeparator(expectedCSV);
    expectedString = expectedString.replace("${PI_ID}", processInstance.getId());
    expectedString = expectedString.replace("${PD_ID}", processInstance.getDefinitionId());
    return expectedString;
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
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

  @SneakyThrows
  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
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

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
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
        "/csv/process/single/flownode_duration_group_by_flownodes_no_values.csv",
        "Flow Node Duration Grouped By Flow Node - Running and null duration"
      )
    );
  }

}