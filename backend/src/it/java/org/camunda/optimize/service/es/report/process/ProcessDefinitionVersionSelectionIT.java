/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.EvaluationResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ProcessDefinitionVersionSelectionIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String VARIABLE_NAME = "StringVar";
  private static final String VARIABLE_VALUE = "StringVal";
  private static final String DEFINITION_KEY = "aProcess";

  private EngineIntegrationRule engineRule = new EngineIntegrationRule();
  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  public void processReportAcrossAllVersions() {
    // given
    ProcessDefinitionEngineDto definition1 = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(definition1.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    engineRule.startProcessInstance(definition1.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    ProcessDefinitionEngineDto definition2 = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(definition2.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<ProcessReportDataDto> allPossibleReports = createAllPossibleProcessReports(
      definition1.getKey(),
      ImmutableList.of(ALL_VERSIONS)
    );
    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      EvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getProcessInstanceCount(), is(3L));
    }
  }

  @Test
  public void processReportAcrossMultipleVersions() {
    // given
    ProcessDefinitionEngineDto definition1 = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(definition1.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    engineRule.startProcessInstance(definition1.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    ProcessDefinitionEngineDto definition2 = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(definition2.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    ProcessDefinitionEngineDto definition3 = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(definition3.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    engineRule.startProcessInstance(definition3.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    engineRule.startProcessInstance(definition3.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<ProcessReportDataDto> allPossibleReports = createAllPossibleProcessReports(
      definition1.getKey(),
      ImmutableList.of(definition1.getVersionAsString(), definition3.getVersionAsString())
    );
    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      EvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getProcessInstanceCount(), is(5L));
    }
  }

  @Test
  public void missingDefinitionVersionResultsIn500() {
    // given
    ProcessDefinitionEngineDto definition1 = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(definition1.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    engineRule.startProcessInstance(definition1.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));
    ProcessDefinitionEngineDto definition2 = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(definition2.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<ProcessReportDataDto> allPossibleReports = createAllPossibleProcessReports(
      definition1.getKey(),
      ImmutableList.of()
    );
    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      Response response = evaluateReportWithResponse(report);

      // then
      assertThat(response.getStatus(), is(500));
    }
  }

  private List<ProcessReportDataDto> createAllPossibleProcessReports(String definitionKey,
                                                                            List<String> definitionVersions) {
    List<ProcessReportDataDto> reports = new ArrayList<>();
    for (ProcessReportDataType reportDataType : ProcessReportDataType.values()) {
      ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
        .setReportDataType(reportDataType)
        .setProcessDefinitionKey(definitionKey)
        .setProcessDefinitionVersions(definitionVersions)
        .setVariableName(VARIABLE_NAME)
        .setVariableType(VariableType.STRING)
        .setDateInterval(GroupByDateUnit.DAY)
        .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
        .setStartFlowNodeId(START_EVENT)
        .setEndFlowNodeId(END_EVENT)
        .build();
      reports.add(reportData);
    }
    return reports;
  }

  private EvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute(new TypeReference<EvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto>>() {
      });
  }

  private Response evaluateReportWithResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(DEFINITION_KEY)
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }
}
