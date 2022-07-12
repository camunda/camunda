/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.config;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.test.engine.OutlierDistributionClient;
import org.camunda.optimize.service.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;
import static org.camunda.optimize.util.BpmnModels.SPLITTING_GATEWAY_ID;

public class ChangingEngineAliasInConfigIT extends AbstractMultiEngineIT {

  @Test
  public void changeEngineAlias_allDefinitionsCanBeImportedForNewAlias() {
    // given
    deployAndStartProcessDefinitionForAllEngines();
    deployAndStartDecisionDefinitionForAllEngines();

    // when
    importAllEngineEntitiesFromScratch();
    removeDefaultEngineConfiguration();
    addSecondEngineToConfiguration();
    importAllEngineEntitiesFromScratch();

    final List<DefinitionResponseDto> allDefinitions = definitionClient.getAllDefinitions();

    // then the result should not contain the decision/process definitions from the first engine
    assertThat(allDefinitions)
      .extracting(DefinitionResponseDto::getKey)
      .containsExactlyInAnyOrder(PROCESS_KEY_2, DECISION_KEY_2);
  }

  @Test
  public void changeEngineAlias_processReportEvaluation() {
    // givens
    deployAndStartProcessOnDefaultEngine(PROCESS_KEY_1, null);
    deployAndStartProcessOnSecondEngine(PROCESS_KEY_1, null);

    // when
    importAllEngineEntitiesFromScratch();
    removeDefaultEngineConfiguration();
    addSecondEngineToConfiguration();
    importAllEngineEntitiesFromScratch();

    // then the report evaluation should be fine even if it would include a definition from a non existing engine alias
    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = reportClient
      .evaluateRawReport(createRawDataReport(PROCESS_KEY_1)).getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2L);
  }

  @Test
  public void changeEngineAlias_decisionReportEvaluation() {
    // givens
    engineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), null);
    secondaryEngineIntegrationExtension.deployAndStartDecisionDefinition(createSimpleDmnModel(DECISION_KEY_1), null);

    // when
    importAllEngineEntitiesFromScratch();
    removeDefaultEngineConfiguration();
    addSecondEngineToConfiguration();
    importAllEngineEntitiesFromScratch();

    // then the report evaluation should be fine even if it would include a definition from a non existing engine alias
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = reportClient
      .evaluateDecisionRawReport(createRawDecisionDataReport(DECISION_KEY_1)).getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2L);
  }

  @Test
  public void changeEngineAlias_branchAnalysis() {
    // givens
    engineIntegrationExtension.deployAndStartProcessWithVariables(
      BpmnModels.getSimpleGatewayProcess(PROCESS_KEY_1),
      ImmutableMap.of("goToTask1", true)
    );
    secondaryEngineIntegrationExtension.deployAndStartProcessWithVariables(
      BpmnModels.getSimpleGatewayProcess(PROCESS_KEY_1),
      ImmutableMap.of("goToTask1", true)
    );

    // when
    importAllEngineEntitiesFromScratch();
    removeDefaultEngineConfiguration();
    addSecondEngineToConfiguration();
    importAllEngineEntitiesFromScratch();

    BranchAnalysisRequestDto branchAnalysisRequestDto = analysisClient.createAnalysisDto(
      PROCESS_KEY_1,
      Lists.newArrayList(ALL_VERSIONS),
      DEFAULT_TENANT_IDS,
      SPLITTING_GATEWAY_ID,
      END_EVENT
    );

    // then the analysis should be fine even if it would include a definition from a non existing engine alias
    final BranchAnalysisResponseDto result = analysisClient.getProcessDefinitionCorrelation(branchAnalysisRequestDto);
    assertThat(result.getTotal()).isEqualTo(2L);
  }

  @Test
  public void changeEngineAlias_outlierAnalysis() {
    // givens
    engineIntegrationExtension.deployProcessAndGetId(BpmnModels.getTwoServiceTasksProcess(PROCESS_KEY_1));
    final String procDefId =
      secondaryEngineIntegrationExtension.deployProcessAndGetId(BpmnModels.getTwoServiceTasksProcess(PROCESS_KEY_1));
    OutlierDistributionClient outlierDistributionClient =
      new OutlierDistributionClient(secondaryEngineIntegrationExtension);

    // given
    outlierDistributionClient.startPIsDistributedByDuration(
      procDefId, new Gaussian(40 / 2., 12), 40, 0L, SERVICE_TASK_ID_1, SERVICE_TASK_ID_2
    );

    // when
    importAllEngineEntitiesFromScratch();
    removeDefaultEngineConfiguration();
    addSecondEngineToConfiguration();
    importAllEngineEntitiesFromScratch();


    // then the analysis should be fine even if it would include a definition from a non existing engine alias
    HashMap<String, FindingsDto> outlierTest = analysisClient.getFlowNodeOutliers(
      PROCESS_KEY_1,
      Collections.singletonList(ALL_VERSIONS),
      DEFAULT_TENANT_IDS
    );
    assertThat(outlierTest.get(SERVICE_TASK_ID_1).getTotalCount()).isGreaterThan(1L);
  }

  private ProcessReportDataDto createRawDataReport(final String processDefinitionKey) {
    return new ProcessReportDataBuilderHelper()
      .processDefinitionKey(processDefinitionKey)
      .processDefinitionVersion(ALL_VERSIONS)
      .viewProperty(ViewProperty.RAW_DATA)
      .visualization(ProcessVisualization.TABLE)
      .build();
  }

  private DecisionReportDataDto createRawDecisionDataReport(final String decisionDefinitionKey) {
    return new DecisionReportDataBuilder()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }
}
