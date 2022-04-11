/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_AMOUNT;

public class DecisionDefinitionVersionSelectionIT extends AbstractDecisionDefinitionIT {

  @Test
  public void decisionReportAcrossAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);
    // different version
    deployDecisionAndStartInstances(2);

    importAllEngineEntitiesFromScratch();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of(ALL_VERSIONS)
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedSingleReportEvaluationResponseDto<?, SingleDecisionReportDefinitionRequestDto> result =
        reportClient.evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount()).isEqualTo(3L);
    }
  }

  @Test
  public void decisionReportAcrossMultipleVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(2);
    deployDecisionAndStartInstances(1);
    DecisionDefinitionEngineDto decisionDefinitionDto3 = deployDecisionAndStartInstances(3);

    importAllEngineEntitiesFromScratch();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of(decisionDefinitionDto1.getVersionAsString(), decisionDefinitionDto3.getVersionAsString())
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedSingleReportEvaluationResponseDto<?, SingleDecisionReportDefinitionRequestDto> result =
        reportClient.evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount()).isEqualTo(5L);
    }
  }

  @Test
  public void decisionReportsWithLatestVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(2);
    deployDecisionAndStartInstances(1);

    importAllEngineEntitiesFromScratch();

    List<DecisionReportDataDto> allPossibleReports =
      createAllPossibleDecisionReports(decisionDefinitionDto1.getKey(), ImmutableList.of(LATEST_VERSION));
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedSingleReportEvaluationResponseDto<?, SingleDecisionReportDefinitionRequestDto> result =
        reportClient.evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount()).isEqualTo(1L);
    }

    deployDecisionAndStartInstances(4);

    importAllEngineEntitiesFromScratch();

    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedSingleReportEvaluationResponseDto<?, SingleDecisionReportDefinitionRequestDto> result =
        reportClient.evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount()).isEqualTo(4L);
    }
  }

  @Test
  public void missingDefinitionVersionReturnsEmptyResult() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);

    importAllEngineEntitiesFromScratch();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of()
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      ReportResultResponseDto<Object> result = reportClient.evaluateReport(report).getResult();

      // then
      assertThat(result.getInstanceCount()).isZero();
    }
  }

  private DecisionDefinitionEngineDto deployDecisionAndStartInstances(int nInstancesToStart) {
    DecisionDefinitionEngineDto definition = engineIntegrationExtension.deployDecisionDefinition();
    IntStream.range(0, nInstancesToStart).forEach(
      i -> engineIntegrationExtension.startDecisionInstance(definition.getId())
    );
    return definition;
  }

  private List<DecisionReportDataDto> createAllPossibleDecisionReports(String definitionKey,
                                                                       List<String> definitionVersions) {
    List<DecisionReportDataDto> reports = new ArrayList<>();
    for (DecisionReportDataType reportDataType : DecisionReportDataType.values()) {
      DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
        .setDecisionDefinitionKey(definitionKey)
        .setDecisionDefinitionVersions(definitionVersions)
        .setVariableId(INPUT_AMOUNT_ID)
        .setVariableName(INPUT_VARIABLE_AMOUNT)
        .setVariableType(VariableType.DOUBLE)
        .setDateInterval(AggregateByDateUnit.DAY)
        .setReportDataType(reportDataType)
        .build();
      reports.add(reportData);
    }
    return reports;
  }
}
