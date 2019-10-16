/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AnalysisAuthorizationIT {
  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String START_EVENT_ID = "startEvent";
  private static final String SPLITTING_GATEWAY_ID = "splittingGateway";
  private static final String TASK_ID_1 = "serviceTask1";
  private static final String TASK_ID_2 = "serviceTask2";
  private static final String MERGE_GATEWAY_ID = "mergeExclusiveGateway";
  private static final String END_EVENT_ID = "endEvent";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtensionRule);

  @Test
  public void branchAnalysis_authorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //when
    Response response = executeBranchAnalysisAsKermit(processDefinition);

    //then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void branchAnalysis_noneTenantAuthorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //when
    Response response = executeBranchAnalysisAsKermit(processDefinition, Collections.singletonList(null));

    //then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void branchAnalysisWithoutAuthorization() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildProcessDefinitionCorrelation(
        createAnalysisDto("", "", Collections.emptyList())
      )
      .execute();

    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void branchAnalysis_authorizedTenant() {
    // given
    final String tenantId = "tenantId";
    engineIntegrationExtensionRule.createTenant(tenantId);
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    startSimpleGatewayProcessAndTakeTask1(processDefinition);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //when
    Response response = executeBranchAnalysisAsKermit(processDefinition);

    //then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void branchAnalysis_unauthorizedTenant() {
    // given
    final String tenantId = "tenantId";
    engineIntegrationExtensionRule.createTenant(tenantId);
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //when
    Response response = executeBranchAnalysisAsKermit(processDefinition);

    //then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void branchAnalysis_partiallyUnauthorizedTenants() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    engineIntegrationExtensionRule.createTenant(tenantId1);
    engineIntegrationExtensionRule.createTenant(tenantId2);
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition(tenantId1);
    final ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition(tenantId2);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //when
    Response response = executeBranchAnalysisAsKermit(processDefinition1, ImmutableList.of(tenantId1, tenantId2));

    //then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  public void branchAnalysis_notAuthorizedToProcessDefinition() {
    // given
    final String tenantId = "tenantId";
    engineIntegrationExtensionRule.createTenant(tenantId);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final Response response = executeBranchAnalysisAsKermit(processDefinition, ImmutableList.of(tenantId));

    // then
    assertThat(response.getStatus(), is(403));
  }

  private Response executeBranchAnalysisAsKermit(final ProcessDefinitionEngineDto processDefinition) {
    return executeBranchAnalysisAsKermit(
      processDefinition,
      processDefinition.getTenantId().map(Collections::singletonList).orElse(Collections.emptyList())
    );
  }

  private Response executeBranchAnalysisAsKermit(final ProcessDefinitionEngineDto processDefinition,
                                                 List<String> tenantIds) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildProcessDefinitionCorrelation(createAnalysisDto(
        processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), tenantIds
      ))
      .execute();
  }

  private BranchAnalysisQueryDto createAnalysisDto(final String processDefinitionKey,
                                                   final String processDefinitionVersion,
                                                   final List<String> tenantIds) {
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersion(processDefinitionVersion);
    dto.setTenantIds(tenantIds);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    return dto;
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    return deploySimpleGatewayProcessDefinition(null);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition(final String tenantId) {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask(TASK_ID_1)
        .camundaExpression("${true}")
      .exclusiveGateway(MERGE_GATEWAY_ID)
        .endEvent(END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
        .condition("no", "${!goToTask1}")
        .serviceTask(TASK_ID_2)
        .camundaExpression("${true}")
        .connectTo(MERGE_GATEWAY_ID)
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private void startSimpleGatewayProcessAndTakeTask1(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
  }
}
