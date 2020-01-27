/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public abstract class AbstractRelativeDateFilterIT extends AbstractFilterIT {

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
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void assertResults(
    ProcessInstanceEngineDto processInstance,
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult,
    int expectedPiCount) {

    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    final List<RawDataProcessInstanceDto> resultData = evaluationResult.getResult().getData();
    assertThat(resultData, is(notNullValue()));
    assertThat("relative date result size", resultData.size(), is(expectedPiCount));

    if (expectedPiCount > 0) {
      RawDataProcessInstanceDto rawDataProcessInstanceDto = resultData.get(0);
      assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    }
  }

  protected AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> createAndEvaluateReportWithRelativeStartDateFilter(
    String processDefinitionKey,
    String processDefinitionVersion,
    DateFilterUnit unit,
    boolean newToken
  ) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(RAW_DATA)
      .build();
    List<ProcessFilterDto> relativeDateFilter = createRelativeStartDateFilter(unit);

    reportData.setFilter(relativeDateFilter);
    return evaluateReport(reportData, newToken);
  }

  protected List<ProcessFilterDto> createRelativeStartDateFilter(final DateFilterUnit unit) {
    return ProcessFilterBuilder
        .filter()
        .relativeStartDate()
        .start(1L, unit)
        .add()
        .buildList();
  }

  protected AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> createAndEvaluateReportWithRelativeEndDateFilter(
    String processDefinitionKey,
    String processDefinitionVersion,
    DateFilterUnit unit,
    boolean newToken
  ) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(RAW_DATA)
      .build();
    List<ProcessFilterDto> relativeDateFilter = ProcessFilterBuilder
      .filter()
      .relativeEndDate()
      .start(1L, unit)
      .add()
      .buildList();

    reportData.setFilter(relativeDateFilter);
    return evaluateReport(reportData, newToken);
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReport(ProcessReportDataDto reportData, boolean newToken) {
    if (newToken) {
      return evaluateReportWithNewToken(reportData);
    } else {
      return evaluateReportWithRawDataResult(reportData);
    }
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReportWithNewToken(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withGivenAuthToken(embeddedOptimizeExtension.getNewAuthenticationToken())
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

}
