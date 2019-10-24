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
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class DecisionDefinitionVersionSelectionIT extends AbstractDecisionDefinitionIT {

  protected static final String INPUT_AMOUNT_ID = "clause1";
  private static final String VARIABLE_NAME = "amount";

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
  public void decisionReportAcrossAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);
    // different version
    deployDecisionAndStartInstances(2);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

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

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(
        report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(4L));
    }
  }

  @Test
  public void missingDefinitionVersionResultsIn500() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of()
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      Response response = evaluateReportWithResponse(report);

      // then
      assertThat(response.getStatus(), is(500));
    }
  }

  private DecisionDefinitionEngineDto deployDecisionAndStartInstances(int nInstancesToStart) {
    DecisionDefinitionEngineDto definition = engineIntegrationExtensionRule.deployDecisionDefinition();
    IntStream.range(0, nInstancesToStart).forEach(
      i -> engineIntegrationExtensionRule.startDecisionInstance(definition.getId())
    );
    return definition;
  }

  private AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> evaluateReport(DecisionReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute(new TypeReference<AuthorizedEvaluationResultDto<DecisionReportResultDto,
        SingleDecisionReportDefinitionDto>>() {
      });
  }

  private Response evaluateReportWithResponse(DecisionReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
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
