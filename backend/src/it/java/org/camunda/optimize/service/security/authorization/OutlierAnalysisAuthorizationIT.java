/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class OutlierAnalysisAuthorizationIT extends AbstractIT {
  private static final String PROCESS_DEFINITION_KEY = "outlierTest";
  private static final String ENDPOINT_FLOW_NODE_OUTLIERS = "flowNodeOutliers";
  private static final String ENDPOINT_DURATION_CHART = "durationChart";
  private static final String ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS = "significantOutlierVariableTerms";
  private static final String ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS_PROCESS_INSTANCE_IDS_EXPORT =
    "significantOutlierVariableTerms/processInstanceIdsExport";
  private static final String FLOW_NODE_ID_START = "start";

  private final AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  private static Stream<String> endpoints() {
    return Stream.of(
      ENDPOINT_FLOW_NODE_OUTLIERS,
      ENDPOINT_DURATION_CHART,
      ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS,
      ENDPOINT_SIGNIFICANT_OUTLIER_VARIABLE_TERMS_PROCESS_INSTANCE_IDS_EXPORT
    );
  }

  @ParameterizedTest
  @MethodSource("endpoints")
  public void outlierEndpoint_unauthenticated(String endpoint) {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeExtension.getRequestExecutor().withoutAuthentication(),
      null,
      endpoint
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("endpoints")
  public void outlierEndpoint_authorized(String endpoint) {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeExtension.getRequestExecutor(),
      null,
      endpoint
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("endpoints")
  public void outlierEndpoint_notAuthorizedToProcessDefinition(String endpoint) {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeExtension.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      null,
      endpoint
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("endpoints")
  public void outlierEndpoint_noneTenantAuthorized(String endpoint) {
    // given
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId));

    startInstanceWithSampleVariables(processDefinition);
    startInstanceWithSampleVariables(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeExtension.getRequestExecutor(),
      Collections.singletonList(null),
      endpoint
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("endpoints")
  public void outlierEndpoint_authorizedTenant(String endpoint) {
    // given
    final String tenantId = "tenantId";
    final String activityId = "chartTestActivity";
    engineIntegrationExtension.createTenant(tenantId);
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);

    startInstanceWithSampleVariables(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeExtension.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      Collections.singletonList(tenantId),
      endpoint
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("endpoints")
  public void outlierEndpoint_unauthorizedTenant(String endpoint) {
    // given
    final String tenantId = "tenantId";
    final String activityId = "chartTestActivity";
    engineIntegrationExtension.createTenant(tenantId);
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    startInstanceWithSampleVariables(processDefinition);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeRequest(
      processDefinition,
      embeddedOptimizeExtension.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      Collections.singletonList(tenantId),
      endpoint
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("endpoints")
  public void outlierEndpoint_partiallyUnauthorizedTenants(String endpoint) {
    // given
    final String tenantId1 = "tenantId1";
    engineIntegrationExtension.createTenant(tenantId1);
    final String tenantId2 = "tenantId2";
    engineIntegrationExtension.createTenant(tenantId2);
    final String activityId = "chartTestActivity";
    ProcessDefinitionEngineDto processDefinition1 =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId1);
    ProcessDefinitionEngineDto processDefinition2 =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getBpmnModelInstance(activityId), tenantId2);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);

    startInstanceWithSampleVariables(processDefinition1);
    startInstanceWithSampleVariables(processDefinition2);

    importAllEngineEntitiesFromScratch();

    // when
    final Response response = executeRequest(
      processDefinition1,
      embeddedOptimizeExtension.getRequestExecutor().withUserAuthentication(KERMIT_USER, KERMIT_USER),
      ImmutableList.of(tenantId1, tenantId2),
      endpoint
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private Response executeRequest(final ProcessDefinitionEngineDto processDefinition,
                                  final OptimizeRequestExecutor optimizeRequestExecutor,
                                  final List<String> tenants,
                                  final String endpoint) {
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
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), ImmutableMap.of("var", "value"));
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
