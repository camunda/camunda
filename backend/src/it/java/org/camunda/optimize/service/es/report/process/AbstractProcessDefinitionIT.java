/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AbstractProcessDefinitionIT {

  protected static final String TEST_ACTIVITY = "testActivity";
  protected static final String BUSINESS_KEY = "aBusinessKey";
  protected static final String END_EVENT = "endEvent";
  protected static final String START_EVENT = "startEvent";
  protected static final String USER_TASK = "userTask";
  protected static final String DEFAULT_VARIABLE_NAME = "foo";
  protected static final String DEFAULT_VARIABLE_VALUE = "bar";
  protected static final VariableType DEFAULT_VARIABLE_TYPE = VariableType.STRING;

  protected EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  protected ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  protected EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  protected EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(engineIntegrationExtensionRule.getEngineName());

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchIntegrationTestExtensionRule)
    .around(engineIntegrationExtensionRule)
    .around(embeddedOptimizeExtensionRule)
    .around(engineDatabaseExtensionRule);

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcess(null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess(String tenantId) {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>(), tenantId);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    return deployAndStartSimpleProcessWithVariables(variables, null);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables,
                                                                            String tenantId) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(processModel, variables, BUSINESS_KEY, tenantId);
  }


  protected ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployAndStartProcess(processModel);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition() {
    return deploySimpleOneUserTasksDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    return deployAndStartSimpleServiceTaskProcess("aProcess", activityId, null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String key,
                                                                            String activityId,
                                                                            String tenantId) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(key)
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(activityId)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(
      processModel, ImmutableMap.of(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE), tenantId
    );
  }

  protected ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask("task1")
          .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
        .endEvent("endEvent")
      .moveToNode("splittingGateway")
        .condition("no", "${!goToTask1}")
        .serviceTask("task2")
          .camundaExpression("${true}")
        .connectTo("mergeGateway")
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  protected String deployAndStartMultiTenantSimpleServiceTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtensionRule.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> deployAndStartSimpleServiceTaskProcess(processKey, TEST_ACTIVITY, tenant));

    return processKey;
  }

  protected AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluateMapReportById(String id) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluateNumberReportById(String id) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<NumberResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateRawReportById(final String reportId) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluateMapReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluateHyperMapReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluateNumberReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<NumberResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateRawReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  protected Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  protected String createNewReport(ProcessReportDataDto processReportDataDto) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(processReportDataDto);
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    singleProcessReportDefinitionDto.setCreated(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setLastModified(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setOwner("something");
    return createNewReport(singleProcessReportDefinitionDto);
  }

  protected String createNewReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

}
