/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyString;


public class CombinedProcessExportServiceIT {

  private static final String START = "aStart";
  private static final String END = "anEnd";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(engineIntegrationExtensionRule.getEngineName());

  @Test
  public void combinedMapReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_flow_node_frequency_group_by_flow_node.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedDurationMapReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    engineDatabaseExtensionRule.changeActivityDuration(processInstance1.getId(), 0);
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    engineDatabaseExtensionRule.changeActivityDuration(processInstance2.getId(), 0);
    String singleReportId1 = createNewSingleDurationMapReport(processInstance1);
    String singleReportId2 = createNewSingleDurationMapReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_flow_node_duration_group_by_flow_node.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void theOrderOfTheReportsDoesMatter() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId2, singleReportId1);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_flow_node_frequency_group_by_flow_node_different_order.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedNumberReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleNumberReport(processInstance1);
    String singleReportId2 = createNewSingleNumberReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_pi_frequency_group_by_none.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedDurationNumberReportHasExpectedValue() throws Exception {
    //given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final OffsetDateTime endDate = startDate.plus(1, ChronoUnit.MILLIS);
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstance1.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstance1.getId(), endDate);
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstance2.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstance2.getId(), endDate);
    String singleReportId1 = createNewSingleDurationNumberReport(processInstance1);
    String singleReportId2 = createNewSingleDurationNumberReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_pi_duration_group_by_none.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithUnevaluatableReportProducesEmptyResult() throws Exception {
    //given
    String singleReportId1 = createNewSingleReport(new SingleProcessReportDefinitionDto());
    String combinedReportId = createNewCombinedReport(singleReportId1);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_empty_report.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithoutReportsProducesEmptyResult() throws IOException {
    //given
    String combinedReportId = createNewCombinedReport();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    String actualContent = getResponseContentAsString(response);
    assertThat(actualContent.trim(), isEmptyString());
  }

  private void updateCombinedProcessReport(String id, CombinedReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewCombinedReport() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    return createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto processInstanceDurationGroupByNone = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .build();
    return createNewSingleMapReport(processInstanceDurationGroupByNone);
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setName("FooName");
    singleProcessReportDefinitionDto.setData(data);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleDurationNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto processInstanceDurationGroupByNone = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(processInstanceDurationGroupByNone);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto report) {
    String reportId = createNewCombinedReport();
    updateCombinedProcessReport(reportId, report);
    return reportId;
  }

  private String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportIds));
    return createNewCombinedReport(report);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith5FlowNodes() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .serviceTask("ServiceTask1")
        .camundaExpression("${true}")
      .serviceTask("ServiceTask2")
        .camundaExpression("${true}")
      .serviceTask("ServiceTask3")
        .camundaExpression("${true}")
      .endEvent(END)
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith2FlowNodes() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .endEvent(END)
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployAndStartProcess(processModel);
  }
}
