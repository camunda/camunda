/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByInputVariableDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.export.SingleDecisionReportDefinitionExportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty.FREQUENCY;
import static org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty.RAW_DATA;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;

public class DecisionReportExportIT extends AbstractIT {
  private static final String DEFINITION_KEY = "aKey";
  private static final String DEFINITION_VERSION = "1";

  @BeforeEach
  public void setUp() {
    // only superusers are authorized to export reports
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add("demo");
  }

  @ParameterizedTest
  @MethodSource("getTestReports")
  public void exportReportAsJsonFile(final DecisionReportDataDto reportData) {
    // given
    final String reportId = reportClient.createSingleDecisionReport(reportData);
    final SingleDecisionReportDefinitionExportDto expectedReportExportDto =
      new SingleDecisionReportDefinitionExportDto(reportClient.getSingleDecisionReportDefinitionDto(reportId));

    // when
    Response response = exportClient.exportReportAsJson(ReportType.DECISION, reportId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    final SingleDecisionReportDefinitionExportDto actualExportDto =
      response.readEntity(SingleDecisionReportDefinitionExportDto.class);

    assertThat(actualExportDto).usingRecursiveComparison().isEqualTo(expectedReportExportDto);
  }

  private static Stream<DecisionReportDataDto> getTestReports() {
    // A raw data report with custom table configs
    final DecisionReportDataDto rawReport = new DecisionReportDataDto();
    rawReport.setDecisionDefinitionKey(DEFINITION_KEY);
    rawReport.setDecisionDefinitionVersion(DEFINITION_VERSION);
    rawReport.setVisualization(DecisionVisualization.TABLE);
    final DecisionViewDto rawDataView = new DecisionViewDto();
    rawDataView.setProperty(RAW_DATA);
    rawReport.setView(rawDataView);

    rawReport.getConfiguration().getTableColumns().setIncludeNewVariables(false);
    rawReport.getConfiguration().getTableColumns().getExcludedColumns().add(DecisionInstanceDto.Fields.engine);

    // A groupBy variable report with filters and custom bucket config
    final DecisionReportDataDto groupByVarReport = new DecisionReportDataDto();
    groupByVarReport.setDecisionDefinitionKey(DEFINITION_KEY);
    groupByVarReport.setDecisionDefinitionVersion(DEFINITION_VERSION);
    final DecisionViewDto evalCountView = new DecisionViewDto();
    evalCountView.setProperty(FREQUENCY);
    groupByVarReport.setView(evalCountView);
    groupByVarReport.setVisualization(DecisionVisualization.BAR);
    final DecisionGroupByVariableValueDto variableValueDto = new DecisionGroupByVariableValueDto();
    variableValueDto.setId("testVariableID");
    variableValueDto.setName("testVariableName");
    variableValueDto.setType(VariableType.INTEGER);
    final DecisionGroupByInputVariableDto groupByDto = new DecisionGroupByInputVariableDto();
    groupByDto.setValue(variableValueDto);
    groupByVarReport.setGroupBy(new DecisionGroupByInputVariableDto());
    groupByVarReport.getFilter().add(createRollingEvaluationDateFilter(1L, DateFilterUnit.DAYS));

    groupByVarReport.getConfiguration().getCustomBucket().setActive(true);
    groupByVarReport.getConfiguration().getCustomBucket().setBaseline(500.0);
    groupByVarReport.getConfiguration().getCustomBucket().setBucketSize(15.0);


    return Stream.of(
      rawReport,
      groupByVarReport
    );
  }
}
