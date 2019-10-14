/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.IsNot.not;


public class ExportRestServiceIT {
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Test
  public void exportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCsvExportRequest("fake_id", "my_file.csv")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(307));
    Assert.assertThat(response.getLocation().getPath(), is("/login"));
  }

  @Test
  public void exportExistingRawProcessReportWithoutFilename() {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidRawProcessReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void exportExistingRawProcessReport() throws IOException {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidRawProcessReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    assertThat(getResponseContentAsString(response).length(), is(greaterThan(0)));
  }

  @Test
  public void exportExistingRawDecisionReport() throws IOException {
    //given
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineRule.deployAndStartDecisionDefinition();
    String reportId = createAndStoreDefaultValidRawDecisionReportDefinition(
      decisionDefinitionEngineDto.getKey(),
      String.valueOf(decisionDefinitionEngineDto.getVersion())
    );

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    assertThat(result.length, is(not(0)));
  }

  @Test
  public void exportExistingInvalidReport() {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultInvalidReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }


  @Test
  public void exportNotExistingReport() {
    // when
    Response response =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildCsvExportRequest("UFUK", "IGDE.csv")
        .execute();
    // then
    assertThat(response.getStatus(), is(404));
  }

  private String createAndStoreDefaultValidRawProcessReportDefinition(String processDefinitionKey,
                                                                      String processDefinitionVersion) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    return createAndStoreDefaultProcessReportDefinition(reportData);
  }

  private String createAndStoreDefaultValidRawDecisionReportDefinition(String decisionDefinitionKey,
                                                                       String decisionDefinitionVersion) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
    return createAndStoreDefaultDecisionReportDefinition(reportData);
  }

  private String createAndStoreDefaultInvalidReportDefinition(String processDefinitionKey,
                                                              String processDefinitionVersion) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setGroupBy(new NoneGroupByDto());
    reportData.setVisualization(ProcessVisualization.NUMBER);
    return createAndStoreDefaultProcessReportDefinition(reportData);
  }

  private String createAndStoreDefaultProcessReportDefinition(ProcessReportDataDto reportData) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setName("something");
    return createNewProcessReport(singleProcessReportDefinitionDto);
  }

  private String createAndStoreDefaultDecisionReportDefinition(DecisionReportDataDto decisionReportDataDto) {
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setData(decisionReportDataDto);
    singleDecisionReportDefinitionDto.setLastModifier("something");
    singleDecisionReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleDecisionReportDefinitionDto.setCreated(someDate);
    singleDecisionReportDefinitionDto.setLastModified(someDate);
    singleDecisionReportDefinitionDto.setOwner("something");
    return createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  private String createSingleDecisionReport(SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewProcessReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }
}