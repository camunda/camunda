/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyString;


public class CombinedProcessExportServiceIT {

  private static final String START = "aStart";
  private static final String END = "anEnd";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  public void combinedMapReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_flow_node_frequency_group_by_flow_node.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedDurationMapReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    engineDatabaseRule.changeActivityDuration(processInstance1.getId(), 0);
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    engineDatabaseRule.changeActivityDuration(processInstance2.getId(), 0);
    String singleReportId1 = createNewSingleDurationMapReport(processInstance1);
    String singleReportId2 = createNewSingleDurationMapReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_flow_node_duration_group_by_flow_node.csv");

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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString(
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_pi_frequency_group_by_none.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedDurationNumberReportHasExpectedValue() throws Exception {
    //given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final OffsetDateTime endDate = startDate.plus(1, ChronoUnit.MILLIS);
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstance1.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstance1.getId(), endDate);
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    engineDatabaseRule.changeProcessInstanceStartDate(processInstance2.getId(), startDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstance2.getId(), endDate);
    String singleReportId1 = createNewSingleDurationNumberReport(processInstance1);
    String singleReportId2 = createNewSingleDurationNumberReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_pi_duration_group_by_none.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithUnevaluatableReportProducesEmptyResult() throws Exception {
    //given
    String singleReportId1 = createNewSingleReport();
    String combinedReportId = createNewCombinedReport(singleReportId1);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_empty_report.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithoutReportsProducesEmptyResult() throws IOException {
    //given
    String combinedReportId = createNewCombinedReport();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(combinedReportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    String actualContent = getActualContentAsString(response);
    assertThat(actualContent.trim(), isEmptyString());
  }

  private String getActualContentAsString(Response response) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    return new String(result);
  }

  private String getExpectedContentAsString(String pathToExpectedCSV) throws IOException {
    Path path = Paths.get(this.getClass().getResource(pathToExpectedCSV).getPath());
    byte[] expectedContent = Files.readAllBytes(path);
    return new String(expectedContent);
  }

  private void updateSingleProcessReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private void updateCombinedProcessReport(String id, CombinedReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewCombinedReport() {
    return embeddedOptimizeRule
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
    String singleReportId = createNewSingleReport();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setName("FooName");
    definitionDto.setData(data);
    updateSingleProcessReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateSingleProcessReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleDurationNumberReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto processInstanceDurationGroupByNone = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(processInstanceDurationGroupByNone);
    updateSingleProcessReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
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
    return engineRule.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith2FlowNodes() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .endEvent(END)
      .done();
    // @formatter:on
    return engineRule.deployAndStartProcess(processModel);
  }
}
