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
import org.camunda.optimize.dto.optimize.rest.report.EvaluationResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.DecisionReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

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

  private EngineIntegrationRule engineRule = new EngineIntegrationRule();
  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  public void decisionReportAcrossAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);
    // different version
    deployDecisionAndStartInstances(2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of(ALL_VERSIONS)
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      EvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getDecisionInstanceCount(), is(3L));
    }
  }

  @Test
  public void decisionReportAcrossMultipleVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(2);
    deployDecisionAndStartInstances(1);
    DecisionDefinitionEngineDto decisionDefinitionDto3 = deployDecisionAndStartInstances(3);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports = createAllPossibleDecisionReports(
      decisionDefinitionDto1.getKey(),
      ImmutableList.of(decisionDefinitionDto1.getVersionAsString(), decisionDefinitionDto3.getVersionAsString())
    );
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      EvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getDecisionInstanceCount(), is(5L));
    }
  }

  @Test
  public void decisionReportsWithLatestVersion() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(2);
    deployDecisionAndStartInstances(1);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<DecisionReportDataDto> allPossibleReports =
      createAllPossibleDecisionReports(decisionDefinitionDto1.getKey(), ImmutableList.of(LATEST_VERSION));
    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      EvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getDecisionInstanceCount(), is(1L));
    }

    deployDecisionAndStartInstances(4);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    for (DecisionReportDataDto report : allPossibleReports) {
      // when
      EvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getDecisionInstanceCount(), is(4L));
    }
  }

  @Test
  public void missingDefinitionVersionResultsIn500() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDecisionAndStartInstances(1);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

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
    DecisionDefinitionEngineDto definition = engineRule.deployDecisionDefinition();
    IntStream.range(0, nInstancesToStart).forEach(
      i -> engineRule.startDecisionInstance(definition.getId())
    );
    return definition;
  }

  private EvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> evaluateReport(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute(new TypeReference<EvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto>>() {
      });
  }

  private Response evaluateReportWithResponse(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
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
        .setDateInterval(GroupByDateUnit.DAY)
        .setReportDataType(reportDataType)
        .build();
      reports.add(reportData);
    }
    return reports;
  }
}
