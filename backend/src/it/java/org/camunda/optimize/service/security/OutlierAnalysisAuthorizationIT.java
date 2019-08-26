/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;

@RunWith(Parameterized.class)
public class OutlierAnalysisAuthorizationIT {
  private static final String PROCESS_DEFINITION_KEY = "outlierTest";
  private static final String ENDPOINT_FLOW_NODE_OUTLIERS = "flowNodeOutliers";
  private static final String ENDPOINT_DURATION_CHART = "durationChart";
  private static final String ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS = "significantOutlierVariableTerms";
  private static final String ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS_PROCESS_INSTANCE_IDS_EXPORT =
    "significantOutlierVariableTerms/processInstanceIdsExport";
  private static final String FLOW_NODE_ID_START = "start";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private final AuthorizationClient authorizationClient = new AuthorizationClient(engineRule);
  private final String endpoint;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> endpoints() {
    return Arrays.asList(
      new Object[]{ENDPOINT_FLOW_NODE_OUTLIERS},
      new Object[]{ENDPOINT_DURATION_CHART},
      new Object[]{ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS},
      new Object[]{ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS_PROCESS_INSTANCE_IDS_EXPORT}
    );
  }

  public OutlierAnalysisAuthorizationIT(final String endpoint) {
    this.endpoint = endpoint;
  }

  @Test
  public void outlierEndpoint_unauthenticated() {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final Response response = executeRequest(
      processDefinition, embeddedOptimizeRule.getRequestExecutor().withoutAuthentication(), null
    );

    //then
    Assert.assertThat(response.getStatus(), is(401));
  }

  @Test
  public void outlierEndpoint_authorized() {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final Response response = executeRequest(processDefinition, embeddedOptimizeRule.getRequestExecutor(), null);

    //then
    Assert.assertThat(response.getStatus(), is(200));
  }

  @Test
  public void outlierEndpoint_notAuthorizedToProcessDefinition() {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeRule.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      null
    );

    //then
    Assert.assertThat(response.getStatus(), is(403));
  }

  @Test
  public void outlierEndpoint_noneTenantAuthorized() {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeRule.getRequestExecutor(),
      Collections.singletonList(null)
    );

    //then
    Assert.assertThat(response.getStatus(), is(200));
  }

  @Test
  public void outlierEndpoint_authorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final String activityId = "chartTestActivity";
    engineRule.createTenant(tenantId);
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    startInstanceWithSampleVariables(processDefinition);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeRule.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      Collections.singletonList(tenantId)
    );

    //then
    Assert.assertThat(response.getStatus(), is(200));
  }

  @Test
  public void outlierEndpoint_unauthorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final String activityId = "chartTestActivity";
    engineRule.createTenant(tenantId);
    ProcessDefinitionEngineDto processDefinition =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    startInstanceWithSampleVariables(processDefinition);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeRule.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      Collections.singletonList(tenantId)
    );

    //then
    Assert.assertThat(response.getStatus(), is(403));
  }

  @Test
  public void outlierEndpoint_partiallyUnauthorizedTenants() {
    // given
    final String tenantId1 = "tenantId1";
    engineRule.createTenant(tenantId1);
    final String tenantId2 = "tenantId2";
    engineRule.createTenant(tenantId2);
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition1 =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId1);
    ProcessDefinitionEngineDto processDefinition2 =
      engineRule.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId2);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);

    startInstanceWithSampleVariables(processDefinition1);
    startInstanceWithSampleVariables(processDefinition2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    final Response response = executeRequest(
      processDefinition1,
      embeddedOptimizeRule.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      ImmutableList.of(tenantId1, tenantId2)
    );

    //then
    Assert.assertThat(response.getStatus(), is(403));
  }

  private Response executeRequest(final ProcessDefinitionEngineDto processDefinition,
                                  final OptimizeRequestExecutor optimizeRequestExecutor,
                                  final List<String> tenants) {
    switch (endpoint) {
      case ENDPOINT_FLOW_NODE_OUTLIERS:
        return optimizeRequestExecutor
          .buildFlowNodeOutliersRequest(
            processDefinition.getKey(),
            Collections.singletonList("1"),
            tenants
          )
          .execute();
      case ENDPOINT_DURATION_CHART:
        return optimizeRequestExecutor
          .buildFlowNodeDurationChartRequest(
            processDefinition.getKey(),
            Collections.singletonList("1"),
            tenants,
            FLOW_NODE_ID_START
          )
          .execute();
      case ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS:
        return optimizeRequestExecutor
          .buildSignificantOutlierVariableTermsRequest(
            processDefinition.getKey(),
            Collections.singletonList("1"),
            tenants,
            FLOW_NODE_ID_START,
            null,
            // -1 ensures we get results as
            -1L
          )
          .execute();
      case ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS_PROCESS_INSTANCE_IDS_EXPORT:
        return optimizeRequestExecutor
          .buildSignificantOutlierVariableTermsInstanceIdsRequest(
            processDefinition.getKey(),
            Collections.singletonList("1"),
            tenants,
            FLOW_NODE_ID_START,
            0L,
            100L,
            "fake",
            "fake"
          )
          .execute();
      default:
        throw new OptimizeIntegrationTestException("Unsupported endpoint: " + endpoint);
    }
  }

  private void startInstanceWithSampleVariables(final ProcessDefinitionEngineDto processDefinition) {
    engineRule.startProcessInstance(processDefinition.getId(), ImmutableMap.of("var", "value"));
  }

  private BpmnModelInstance getBpmnModelInstance(String... activityId) {
    StartEventBuilder builder = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .name("aProcessName")
      .startEvent(FLOW_NODE_ID_START);
    for (String activity : activityId) {
      builder.serviceTask(activity)
        .camundaExpression("${true}");
    }
    return builder.endEvent("end").done();
  }

}
