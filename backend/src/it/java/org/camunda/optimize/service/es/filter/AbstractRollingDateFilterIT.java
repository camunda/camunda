/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


public abstract class AbstractRollingDateFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(engineDatabaseRule)
    .around(embeddedOptimizeRule);

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
       .serviceTask()
       .camundaExpression("${true}")
       .userTask()
      .endEvent()
      .done();
    // @formatter:on
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void assertResults(
    ProcessInstanceEngineDto processInstance,
    ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult,
    int expectedPiCount) {

    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getFirstProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    final List<RawDataProcessInstanceDto> resultData = evaluationResult.getResult().getData();
    assertThat(resultData, is(notNullValue()));
    assertThat("rolling date result size", resultData.size(), is(expectedPiCount));

    if (expectedPiCount > 0) {
      RawDataProcessInstanceDto rawDataProcessInstanceDto = resultData.get(0);
      assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    }
  }

  protected ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> createAndEvaluateReportWithRollingStartDateFilter(
    String processDefinitionKey,
    String processDefinitionVersion,
    RelativeDateFilterUnit unit,
    boolean newToken
  ) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(RAW_DATA)
      .build();
    List<ProcessFilterDto> rollingDateFilter = ProcessFilterBuilder
      .filter()
      .relativeStartDate()
      .start(1L, unit)
      .add()
      .buildList();

    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> createAndEvaluateReportWithRollingEndDateFilter(
    String processDefinitionKey,
    String processDefinitionVersion,
    RelativeDateFilterUnit unit,
    boolean newToken
  ) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(RAW_DATA)
      .build();
    List<ProcessFilterDto> rollingDateFilter = ProcessFilterBuilder
      .filter()
      .relativeEndDate()
      .start(1L, unit)
      .add()
      .buildList();

    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReport(ProcessReportDataDto reportData, boolean newToken) {
    if (newToken) {
      return evaluateReportWithNewToken(reportData);
    } else {
      return evaluateReport(reportData);
    }
  }

  protected ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  private ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReportWithNewToken(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withGivenAuthToken(embeddedOptimizeRule.getNewAuthenticationToken())
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  protected Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

}
