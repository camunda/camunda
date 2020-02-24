/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionDefinitionVersionSelectionIT extends AbstractDecisionDefinitionIT {

  protected static final String INPUT_AMOUNT_ID = "clause1";
  private static final String VARIABLE_NAME = "amount";

  @Test
  public void decisionReportAcrossAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);
    // different version
    deployDecisionAndStartInstances(2);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of(ALL_VERSIONS)
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(
        report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(3L));
    }
  }

  @Test
  public void decisionReportAcrossMultipleVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(2);
    deployDecisionAndStartInstances(1);
    DecisionDefinitionEngineDto decisionDefinitionDto3 = deployDecisionAndStartInstances(3);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of(decisionDefinitionDto1.getVersionAsString(), decisionDefinitionDto3.getVersionAsString())
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(
        report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(5L));
    }
  }

  @Test
  public void decisionReportsWithLatestVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(2);
    deployDecisionAndStartInstances(1);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports =
      createAllPossibleDecisionReports(decisionDefinitionDto1.getKey(), ImmutableList.of(LATEST_VERSION));
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(
        report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(1L));
    }

    deployDecisionAndStartInstances(4);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(
        report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(4L));
    }
  }

  @Test
  public void missingDefinitionVersionReturnsEmptyResult() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of()
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      DecisionReportResultDto result = evaluateReport(report).getResult();

      // then
      assertThat(result.getInstanceCount()).isEqualTo(0);
    }
  }

  private DecisionDefinitionEngineDto deployDecisionAndStartInstances(int nInstancesToStart) {
    DecisionDefinitionEngineDto definition = engineIntegrationExtension.deployDecisionDefinition();
    IntStream.range(0, nInstancesToStart).forEach(
      i -> engineIntegrationExtension.startDecisionInstance(definition.getId())
    );
    return definition;
  }

  private AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> evaluateReport(DecisionReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute(new TypeReference<AuthorizedEvaluationResultDto<DecisionReportResultDto,
        SingleDecisionReportDefinitionDto>>() {
      });
  }

  private List<DecisionReportDataDto> createAllPossibleDecisionReports(String definitionKey,
                                                                       List<String> definitionVersions) {
    List<DecisionReportDataDto> reports = new ArrayList<>();
    for (DecisionReportDataType reportDataType : DecisionReportDataType.values()) {
      DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
        .setDecisionDefinitionKey(definitionKey)
        .setDecisionDefinitionVersions(definitionVersions)
        .setVariableId(INPUT_AMOUNT_ID)
        .setVariableName(VARIABLE_NAME)
        .setVariableType(VariableType.DOUBLE)
        .setDateInterval(GroupByDateUnit.DAY)
        .setReportDataType(reportDataType)
        .build();
      reports.add(reportData);
    }
    return reports;
  }
}
