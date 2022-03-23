/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class AnalysisAuthorizationIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String START_EVENT_ID = "startEvent";
  private static final String SPLITTING_GATEWAY_ID = "splittingGateway";
  private static final String TASK_ID_1 = "serviceTask1";
  private static final String TASK_ID_2 = "serviceTask2";
  private static final String MERGE_GATEWAY_ID = "mergeExclusiveGateway";
  private static final String END_EVENT_ID = "endEvent";

  @Test
  public void branchAnalysis_authorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeBranchAnalysisAsKermit(processDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void branchAnalysis_noneTenantAuthorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeBranchAnalysisAsKermit(processDefinition, Collections.singletonList(null));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void branchAnalysisWithoutAuthorization() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisRequestDto analysisDto = analysisClient.createAnalysisDto(
      "",
      Lists.newArrayList(""),
      Collections.emptyList(),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );
    Response response = analysisClient.getProcessDefinitionCorrelationRawResponseWithoutAuth(analysisDto);

    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void branchAnalysis_authorizedTenant() {
    // given
    final String tenantId = "tenantId";
    engineIntegrationExtension.createTenant(tenantId);
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    startSimpleGatewayProcessAndTakeTask1(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeBranchAnalysisAsKermit(processDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void branchAnalysis_unauthorizedTenant() {
    // given
    final String tenantId = "tenantId";
    engineIntegrationExtension.createTenant(tenantId);
    final ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeBranchAnalysisAsKermit(processDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void branchAnalysis_partiallyUnauthorizedTenants() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    engineIntegrationExtension.createTenant(tenantId1);
    engineIntegrationExtension.createTenant(tenantId2);
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition(tenantId1);
    final ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition(tenantId2);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    importAllEngineEntitiesFromScratch();

    // when
    Response response = executeBranchAnalysisAsKermit(processDefinition1, Lists.newArrayList(tenantId1, tenantId2));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void branchAnalysis_notAuthorizedToProcessDefinition() {
    // given
    final String tenantId = "tenantId";
    engineIntegrationExtension.createTenant(tenantId);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeBranchAnalysisAsKermit(processDefinition, Lists.newArrayList(tenantId));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private Response executeBranchAnalysisAsKermit(final ProcessDefinitionEngineDto processDefinition) {
    return executeBranchAnalysisAsKermit(
      processDefinition,
      processDefinition.getTenantId().map(Collections::singletonList).orElse(Collections.emptyList())
    );
  }

  private Response executeBranchAnalysisAsKermit(final ProcessDefinitionEngineDto processDefinition,
                                                 List<String> tenantIds) {
    return analysisClient.getProcessDefinitionCorrelationRawResponseAsUser(
      analysisClient.createAnalysisDto(
        processDefinition.getKey(),
        Lists.newArrayList(String.valueOf(processDefinition.getVersion())),
        tenantIds,
        SPLITTING_GATEWAY_ID,
        END_EVENT_ID
      ),
      KERMIT_USER, KERMIT_USER
    );
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
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private void startSimpleGatewayProcessAndTakeTask1(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }
}
