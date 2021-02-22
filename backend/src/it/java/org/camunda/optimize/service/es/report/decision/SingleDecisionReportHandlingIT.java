/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleDecisionReportHandlingIT extends AbstractIT {

  @Test
  public void updateDecisionReportWithGroupByInputVariableName() {
    // given
    String id = reportClient.createEmptySingleDecisionReport();

    final String variableName = "variableName";
    DecisionReportDataDto expectedReportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("ID")
      .setDecisionDefinitionVersion("1")
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId("id")
      .setVariableName(variableName)
      .build();

    SingleDecisionReportDefinitionRequestDto report = new SingleDecisionReportDefinitionRequestDto();
    report.setData(expectedReportData);

    // when
    reportClient.updateDecisionReport(id, report);
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size()).isEqualTo(1);
    SingleDecisionReportDefinitionRequestDto reportFromApi = (SingleDecisionReportDefinitionRequestDto) reports.get(0);
    final DecisionGroupByVariableValueDto value = (DecisionGroupByVariableValueDto)
      reportFromApi.getData().getGroupBy().getValue();
    assertThat(value.getName().isPresent()).isEqualTo(true);
    assertThat(value.getName().get()).isEqualTo(variableName);
  }

  @ParameterizedTest
  @EnumSource(DecisionReportDataType.class)
  public void evaluateReport_missingInstancesReturnsEmptyResult(DecisionReportDataType reportType) {
    // given
    final String reportId = deployDefinitionAndCreateReport(reportType);

    // when
    final DecisionReportResultDto result = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<DecisionReportResultDto>>() {
      }).getResult();

    // then
    assertEmptyResult(result);
  }

  private void assertEmptyResult(final DecisionReportResultDto result) {
    if (result instanceof ReportMapResultDto) {
      assertThat(((ReportMapResultDto) result).getFirstMeasureData()).isEmpty();
    } else if (result instanceof ReportHyperMapResultDto) {
      assertThat(((ReportHyperMapResultDto) result).getFirstMeasureData()).isEmpty();
    } else if (result instanceof NumberResultDto) {
      assertThat(((NumberResultDto) result).getFirstMeasureData()).isZero();
    } else if (result instanceof RawDataDecisionReportResultDto) {
      assertThat(((RawDataDecisionReportResultDto) result).getData()).isEmpty();
    }
  }

  private String deployDefinitionAndCreateReport(final DecisionReportDataType reportType) {
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();

    final DecisionReportDataDto expectedReportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion("1")
      .setReportDataType(reportType)
      .setVariableId("variableId")
      .setVariableName("variableName")
      .setVariableType(VariableType.STRING)
      .setDateInterval(AggregateByDateUnit.DAY)
      .build();
    final SingleDecisionReportDefinitionRequestDto report = new SingleDecisionReportDefinitionRequestDto();
    report.setData(expectedReportData);
    return reportClient.createSingleDecisionReport(report);
  }

  private List<ReportDefinitionDto> getAllPrivateReports() {
    return getAllPrivateReportsWithQueryParam(new HashMap<>());
  }

  private List<ReportDefinitionDto> getAllPrivateReportsWithQueryParam(Map<String, Object> queryParams) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .addQueryParams(queryParams)
      .executeAndReturnList(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }
}
