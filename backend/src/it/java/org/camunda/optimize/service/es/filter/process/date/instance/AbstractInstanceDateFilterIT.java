/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.instance;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;

public abstract class AbstractInstanceDateFilterIT extends AbstractFilterIT {

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
    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluationResult,
    int expectedPiCount) {

    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getDefinitionVersions()).contains(processInstance.getProcessDefinitionVersion());
    assertThat(resultDataDto.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
    assertThat(resultDataDto.getView()).isNotNull();
    final List<RawDataProcessInstanceDto> relativeDateResult = evaluationResult.getResult().getData();
    assertThat(relativeDateResult).isNotNull();
    assertThat(relativeDateResult).hasSize(expectedPiCount);

    if (expectedPiCount > 0) {
      RawDataProcessInstanceDto rawDataProcessInstanceDto = relativeDateResult.get(0);
      assertThat(rawDataProcessInstanceDto.getProcessInstanceId()).isEqualTo(processInstance.getId());
    }
  }

  protected AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> createAndEvaluateReportWithStartDateFilter(
    String processDefinitionKey,
    String processDefinitionVersion,
    DateUnit unit,
    Long value,
    boolean newToken,
    DateFilterType filterType
  ) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(RAW_DATA)
      .build();

    if (filterType.equals(DateFilterType.RELATIVE)) {
      reportData.setFilter(createRelativeStartDateFilter(unit, value));
    } else if (filterType.equals(DateFilterType.ROLLING)) {
      reportData.setFilter(createRollingStartDateFilter(unit, value));
    }

    return evaluateReport(reportData, newToken);
  }

  protected List<ProcessFilterDto<?>> createRollingStartDateFilter(final DateUnit unit, final Long value) {
    return ProcessFilterBuilder
      .filter()
      .rollingInstanceStartDate()
      .start(value, unit)
      .add()
      .buildList();
  }

  protected List<ProcessFilterDto<?>> createRelativeStartDateFilter(final DateUnit unit, final Long value) {
    return ProcessFilterBuilder
      .filter()
      .relativeInstanceStartDate()
      .start(value, unit)
      .add()
      .buildList();
  }

  protected AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> createAndEvaluateReportWithRollingEndDateFilter(
    String processDefinitionKey,
    String processDefinitionVersion,
    DateUnit unit,
    boolean newToken
  ) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(RAW_DATA)
      .build();
    List<ProcessFilterDto<?>> rollingDateFilter = ProcessFilterBuilder
      .filter()
      .rollingInstanceEndDate()
      .start(1L, unit)
      .add()
      .buildList();

    reportData.setFilter(rollingDateFilter);
    return evaluateReport(reportData, newToken);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateReport(ProcessReportDataDto reportData, boolean newToken) {
    if (newToken) {
      return evaluateReportWithNewToken(reportData);
    } else {
      return reportClient.evaluateRawReport(reportData);
    }
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluateReportWithNewToken(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withGivenAuthToken(embeddedOptimizeExtension.getNewAuthenticationToken())
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>>() {});
      // @formatter:on
  }

  protected static Stream<DateUnit> getRollingSupportedFilterUnits() {
    return Stream.of(
      DateUnit.MINUTES,
      DateUnit.DAYS,
      DateUnit.HOURS,
      DateUnit.WEEKS,
      DateUnit.MONTHS,
      DateUnit.YEARS
    );
  }

  protected static Stream<DateUnit> getRelativeSupportedFilterUnits() {
    return Stream.concat(
      Stream.of(DateUnit.QUARTERS),
      getRollingSupportedFilterUnits()
    );
  }

}
