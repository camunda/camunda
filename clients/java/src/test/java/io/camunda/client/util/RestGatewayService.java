/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.util;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.protocol.rest.AuditLogResult;
import io.camunda.client.protocol.rest.AuditLogSearchQueryResult;
import io.camunda.client.protocol.rest.AuthorizationCreateResult;
import io.camunda.client.protocol.rest.AuthorizationResult;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.BatchOperationResponse;
import io.camunda.client.protocol.rest.BatchOperationSearchQueryResult;
import io.camunda.client.protocol.rest.ClusterVariableResult;
import io.camunda.client.protocol.rest.DecisionDefinitionResult;
import io.camunda.client.protocol.rest.DecisionInstanceResult;
import io.camunda.client.protocol.rest.DecisionRequirementsResult;
import io.camunda.client.protocol.rest.DeleteResourceResponse;
import io.camunda.client.protocol.rest.DeploymentResult;
import io.camunda.client.protocol.rest.ElementInstanceResult;
import io.camunda.client.protocol.rest.EvaluateConditionalResult;
import io.camunda.client.protocol.rest.EvaluateDecisionResult;
import io.camunda.client.protocol.rest.ExpressionEvaluationResult;
import io.camunda.client.protocol.rest.FormResult;
import io.camunda.client.protocol.rest.GroupCreateResult;
import io.camunda.client.protocol.rest.GroupResult;
import io.camunda.client.protocol.rest.GroupUpdateResult;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.client.protocol.rest.IncidentResult;
import io.camunda.client.protocol.rest.JobActivationResult;
import io.camunda.client.protocol.rest.MappingRuleCreateResult;
import io.camunda.client.protocol.rest.MappingRuleResult;
import io.camunda.client.protocol.rest.MappingRuleUpdateResult;
import io.camunda.client.protocol.rest.MessageCorrelationResult;
import io.camunda.client.protocol.rest.MessagePublicationResult;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import io.camunda.client.protocol.rest.ProcessDefinitionResult;
import io.camunda.client.protocol.rest.ProcessInstanceResult;
import io.camunda.client.protocol.rest.ProcessInstanceSequenceFlowsQueryResult;
import io.camunda.client.protocol.rest.RoleCreateResult;
import io.camunda.client.protocol.rest.RoleResult;
import io.camunda.client.protocol.rest.RoleUpdateResult;
import io.camunda.client.protocol.rest.SearchQueryResponse;
import io.camunda.client.protocol.rest.SignalBroadcastResult;
import io.camunda.client.protocol.rest.TenantCreateResult;
import io.camunda.client.protocol.rest.TenantResult;
import io.camunda.client.protocol.rest.TenantUpdateResult;
import io.camunda.client.protocol.rest.TopologyResponse;
import io.camunda.client.protocol.rest.UsageMetricsResponse;
import io.camunda.client.protocol.rest.UserCreateResult;
import io.camunda.client.protocol.rest.UserResult;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.protocol.rest.UserUpdateResult;
import io.camunda.client.protocol.rest.VariableResult;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.instancio.Instancio;

public class RestGatewayService {

  private static final CamundaObjectMapper JSON_MAPPER = new CamundaObjectMapper();
  private static final SearchQueryResponse DUMMY_SEARCH_RESULT =
      Instancio.create(SearchQueryResponse.class);

  private final WireMockRuntimeInfo mockInfo;

  protected RestGatewayService(final WireMockRuntimeInfo mockInfo) {
    this.mockInfo = mockInfo;
    /*
     * Register a default response to support. Tests that don't need a specific response
     * registration can simply invoke commands and send requests.
     * Otherwise, Wiremock fails if no stubs are registered but a request is sent.
     */
    this.mockInfo.getWireMock().register(WireMock.any(WireMock.anyUrl()).willReturn(WireMock.ok()));
    // Register a default search response
    register(WireMock.post(WireMock.urlPathMatching(".*/search")), DUMMY_SEARCH_RESULT);
    // Register an empty default statistics response
    register(
        WireMock.any(WireMock.urlPathMatching(".*/statistics/.*")),
        Collections.singletonMap("items", Collections.emptyList()));
  }

  /**
   * Register the given response for job activation requests.
   *
   * @param response the response to provide upon a job activation request
   */
  public void onActivateJobsRequest(final JobActivationResult response) {
    registerPost(RestGatewayPaths.getJobActivationUrl(), response);
  }

  /**
   * Register the given response for topology requests.
   *
   * @param response the response to provide upon a topology request
   */
  public void onTopologyRequest(final TopologyResponse response) {
    registerGet(RestGatewayPaths.getTopologyUrl(), response);
  }

  public void onEvaluateDecisionRequest(final EvaluateDecisionResult response) {
    registerPost(RestGatewayPaths.getEvaluateDecisionUrl(), response);
  }

  public void onExpressionEvaluationRequest(final ExpressionEvaluationResult response) {
    registerPost(RestGatewayPaths.getExpressionEvaluationUrl(), response);
  }

  public void onDeploymentsRequest(final DeploymentResult response) {
    registerPost(RestGatewayPaths.getDeploymentsUrl(), response);
  }

  public void onDeleteResourceRequest(
      final long resourceKey, final DeleteResourceResponse response) {
    registerPost(RestGatewayPaths.getResourceDeletionUrl(resourceKey), response);
  }

  public void onUsageMetricsRequest(final UsageMetricsResponse response) {
    register(
        WireMock.get(WireMock.urlMatching(RestGatewayPaths.getUsageMetricsUrl() + ".*")), response);
  }

  public void onDecisionDefinitionRequest(
      final long decisionDefinitionKey, final DecisionDefinitionResult response) {
    registerGet(RestGatewayPaths.getDecisionDefinitionUrl(decisionDefinitionKey), response);
  }

  /**
   * Fetch the last request that was served and convert it to the request target type.
   *
   * @param requestType the Java type to convert the request to
   * @return the last request
   * @param <T> the request type
   */
  public <T> T getLastRequest(final Class<T> requestType) {
    return JSON_MAPPER.fromJson(getLastRequest().getBodyAsString(), requestType);
  }

  /**
   * Fetch the last request that was served. This is a generic {@link LoggedRequest}, provided by
   * the test framework.
   *
   * @return the last logged request
   */
  public static LoggedRequest getLastRequest() {
    final List<ServeEvent> serveEvents = WireMock.getAllServeEvents();
    if (serveEvents.isEmpty()) {
      Assertions.fail("No request was found");
    }
    return serveEvents.get(serveEvents.size() - 1).getRequest();
  }

  /**
   * Register the given error response for a URL. The client will receive a response with the status
   * provided by the given problem detail upon a request to the URL with any HTTP method. If the
   * problem detail does not contain a status, BAD_REQUEST (HTTP status 400) is used.
   *
   * @param url the URL to register the error response for
   * @param problemDetailSupplier the supplier for the error details the client will receive upon a
   *     request
   */
  public void errorOnRequest(
      final String url, final Supplier<ProblemDetail> problemDetailSupplier) {
    final ProblemDetail problemDetail = problemDetailSupplier.get();
    mockInfo
        .getWireMock()
        .register(
            WireMock.any(WireMock.urlEqualTo(url))
                .willReturn(
                    WireMock.jsonResponse(
                            JSON_MAPPER.toJson(problemDetail),
                            problemDetail.getStatus() == null ? 400 : problemDetail.getStatus())
                        .withHeader("Content-Type", "application/problem+json")));
  }

  private void registerPost(final String url, final Object response) {
    register(WireMock.post(url), response);
  }

  private void registerGet(final String url, final Object response) {
    register(WireMock.get(url), response);
  }

  private void registerPut(final String url, final Object response) {
    register(WireMock.put(url), response);
  }

  private void register(final MappingBuilder builder, final Object response) {
    mockInfo
        .getWireMock()
        .register(builder.willReturn(WireMock.okJson(JSON_MAPPER.toJson(response))));
  }

  public void onCreateTenantRequest(final TenantCreateResult response) {
    registerPost(RestGatewayPaths.getCreateTenantUrl(), response);
  }

  public void onCreateUserRequest(final UserCreateResult response) {
    registerPost(RestGatewayPaths.getCreateUserUrl(), response);
  }

  public void onCorrelateMessageRequest(final MessageCorrelationResult response) {
    registerPost(RestGatewayPaths.getMessageCorrelationUrl(), response);
  }

  public void onProcessInstanceCallHierarchyRequest(
      final long processInstanceKey, final Object[] response) {
    registerGet(RestGatewayPaths.getProcessInstanceCallHierarchyUrl(processInstanceKey), response);
  }

  public void onProcessInstanceRequest(
      final long processInstanceKey, final ProcessInstanceResult response) {
    registerGet(RestGatewayPaths.getProcessInstancesUrl(processInstanceKey), response);
  }

  public void onUpdateGroupRequest(final String groupId, final GroupUpdateResult response) {
    registerPut(RestGatewayPaths.getGroupUrl(groupId), response);
  }

  public void onUpdateUserRequest(final String username, final UserUpdateResult response) {
    registerPut(RestGatewayPaths.getUserUrl(username), response);
  }

  public void onProcessDefinitionFormRequest(
      final long processDefinitionKey, final FormResult response) {
    registerGet(RestGatewayPaths.getProcessDefinitionFormUrl(processDefinitionKey), response);
  }

  public void onProcessDefinitionRequest(
      final long processDefinitionKey, final ProcessDefinitionResult response) {
    registerGet(RestGatewayPaths.getProcessDefinitionUrl(processDefinitionKey), response);
  }

  public void onUserRequest(final String username, final UserResult response) {
    registerGet(RestGatewayPaths.getUserUrl(username), response);
  }

  public void onCreateMappingRuleRequest(final MappingRuleCreateResult response) {
    registerPost(RestGatewayPaths.getMappingRulesUrl(), response);
  }

  public void onMappingRuleRequest(final String mappingRuleId, final MappingRuleResult response) {
    registerGet(RestGatewayPaths.getMappingRuleUrl(mappingRuleId), response);
  }

  public void onCreateGroupRequest(final GroupCreateResult response) {
    registerPost(RestGatewayPaths.getGroupsUrl(), response);
  }

  public void onCancelProcessInstancesRequest(final BatchOperationCreatedResult response) {
    registerPost(RestGatewayPaths.getProcessInstancesCancelUrl(), response);
  }

  public void onDeleteProcessInstanceRequest(
      final long processInstanceKey, final BatchOperationCreatedResult response) {
    registerPost(RestGatewayPaths.getDeleteProcessInstanceUrl(processInstanceKey), response);
  }

  public void onDeleteProcessInstancesRequest(final BatchOperationCreatedResult response) {
    registerPost(RestGatewayPaths.getProcessInstancesDeletionUrl(), response);
  }

  public void onResolveIncidentsRequest(final BatchOperationCreatedResult response) {
    registerPost(RestGatewayPaths.getProcessInstancesIncidentResolutionUrl(), response);
  }

  public void onMigrateProcessInstancesRequest(final BatchOperationCreatedResult response) {
    registerPost(RestGatewayPaths.getProcessInstancesMigrateUrl(), response);
  }

  public void onModifyProcessInstances(final BatchOperationCreatedResult response) {
    registerPost(RestGatewayPaths.getProcessInstancesModifyUrl(), response);
  }

  public void onCreateAuthorizationRequest(final AuthorizationCreateResult response) {
    registerPost(RestGatewayPaths.getAuthorizationsUrl(), response);
  }

  public void onTenantRequest(final String tenantId, final TenantResult response) {
    registerGet(RestGatewayPaths.getTenantUrl(tenantId), response);
  }

  public void onUpdateRoleRequest(final String roleId, final RoleUpdateResult response) {
    registerPut(RestGatewayPaths.getRoleUrl(roleId), response);
  }

  public void onUpdateMappingRuleRequest(
      final String mappingRuleId, final MappingRuleUpdateResult response) {
    registerPut(RestGatewayPaths.getMappingRuleUrl(mappingRuleId), response);
  }

  public void onDecisionRequirementsRequest(
      final long decisionRequirementsKey, final DecisionRequirementsResult response) {
    registerGet(RestGatewayPaths.getDecisionRequirementsUrl(decisionRequirementsKey), response);
  }

  public void onCreateProcessInstanceRequest(final ProcessInstanceResult response) {
    registerPost(RestGatewayPaths.getProcessInstancesUrl(), response);
  }

  public void onIncidentRequest(final long incidentKey, final IncidentResult response) {
    registerGet(RestGatewayPaths.getIncidentUrl(incidentKey), response);
  }

  public void onUpdateTenantRequest(final String tenantId, final TenantUpdateResult response) {
    registerPut(RestGatewayPaths.getTenantUrl(tenantId), response);
  }

  public void onDecisionInstanceRequest(
      final String decisionInstanceId, final DecisionInstanceResult response) {
    registerGet(RestGatewayPaths.getDecisionInstanceUrl(decisionInstanceId), response);
  }

  public void onPublishMessageRequest(final MessagePublicationResult response) {
    registerPost(RestGatewayPaths.getMessagePublicationUrl(), response);
  }

  public void onGroupRequest(final String groupId, final GroupResult response) {
    registerGet(RestGatewayPaths.getGroupUrl(groupId), response);
  }

  public void onUserTaskFormRequest(final long userTaskKey, final FormResult response) {
    registerGet(RestGatewayPaths.getUserTaskFormUrl(userTaskKey), response);
  }

  public void onProcessInstanceSequenceFlowsRequest(
      final long processInstanceKey, final ProcessInstanceSequenceFlowsQueryResult response) {
    registerGet(RestGatewayPaths.getProcessInstanceSequenceFlowsUrl(processInstanceKey), response);
  }

  public void onBatchOperationRequest(
      final String batchOperationKey, final BatchOperationResponse response) {
    registerGet(RestGatewayPaths.getBatchOperationUrl(batchOperationKey), response);
  }

  public void onSearchBatchOperationsRequest(final BatchOperationSearchQueryResult response) {
    registerPost(RestGatewayPaths.getBatchOperationsSearchUrl(), response);
  }

  public void onVariableRequest(final long variableKey, final VariableResult response) {
    registerGet(RestGatewayPaths.getVariableUrl(variableKey), response);
  }

  public void onCreateRoleRequest(final RoleCreateResult response) {
    registerPost(RestGatewayPaths.getRolesUrl(), response);
  }

  public void onAuthorizationRequest(
      final long authorizationKey, final AuthorizationResult response) {
    registerGet(RestGatewayPaths.getAuthorizationUrl(authorizationKey), response);
  }

  public void onUserTaskRequest(final long userTaskKey, final UserTaskResult response) {
    registerGet(RestGatewayPaths.getUserTaskUrl(userTaskKey), response);
  }

  public void onElementInstanceRequest(
      final long elementInstanceKey, final ElementInstanceResult response) {
    registerGet(RestGatewayPaths.getElementInstanceUrl(elementInstanceKey), response);
  }

  public void onCreateClusterVariableRequest(final ClusterVariableResult response) {
    registerPost(RestGatewayPaths.getClusterVariablesUrl(), response);
  }

  public void onCreateGlobalClusterVariableRequest(final ClusterVariableResult response) {
    registerPost(RestGatewayPaths.getClusterVariablesCreateGlobalUrl(), response);
  }

  public void onCreateTenantClusterVariableRequest(
      final String tenantId, final ClusterVariableResult response) {
    registerPost(RestGatewayPaths.getClusterVariablesCreateTenantUrl(tenantId), response);
  }

  public void onGetClusterVariableRequest(
      final ClusterVariableResult response, final String variableName) {
    registerGet(RestGatewayPaths.getClusterVariablesGetGlobalUrl(variableName), response);
  }

  public void onGetClusterVariableRequest(
      final ClusterVariableResult response, final String variableName, final String tenantId) {
    registerGet(RestGatewayPaths.getClusterVariablesGetTenantUrl(tenantId, variableName), response);
  }

  public void onGetGlobalClusterVariableRequest(
      final String variableName, final ClusterVariableResult response) {
    registerGet(RestGatewayPaths.getClusterVariablesGetGlobalUrl(variableName), response);
  }

  public void onGetTenantClusterVariableRequest(
      final String tenantId, final String variableName, final ClusterVariableResult response) {
    registerGet(RestGatewayPaths.getClusterVariablesGetTenantUrl(tenantId, variableName), response);
  }

  public void onUpdateGlobalClusterVariableRequest(
      final String variableName, final ClusterVariableResult response) {
    registerPut(RestGatewayPaths.getClusterVariablesUpdateGlobalUrl(variableName), response);
  }

  public void onUpdateTenantClusterVariableRequest(
      final String tenantId, final String variableName, final ClusterVariableResult response) {
    registerPut(
        RestGatewayPaths.getClusterVariablesUpdateTenantUrl(tenantId, variableName), response);
  }

  public void onSearchClusterVariableRequest(final SearchQueryResponse response) {
    registerPost(RestGatewayPaths.getClusterVariablesSearchUrl(), response);
  }

  public void onBroadcastSignalRequest(final SignalBroadcastResult response) {
    registerPost(RestGatewayPaths.getBroadcastSignalUrl(), response);
  }

  public void onEvaluateConditionalRequest(final EvaluateConditionalResult response) {
    registerPost(RestGatewayPaths.getEvaluateConditionalUrl(), response);
  }

  public void onRoleRequest(final String roleId, final RoleResult response) {
    registerGet(RestGatewayPaths.getRoleUrl(roleId), response);
  }

  public void onGetAuditLogRequest(final String auditLogKey, final AuditLogResult response) {
    registerGet(RestGatewayPaths.getAuditLogGetUrl(auditLogKey), response);
  }

  public void onSearchAuditLogRequest(final AuditLogSearchQueryResult response) {
    registerPost(RestGatewayPaths.getAuditLogSearchUrl(), response);
  }

  public void onSearchUserTaskAuditLogRequest(
      final long userTaskKey, final AuditLogSearchQueryResult response) {
    registerPost(RestGatewayPaths.getUserTaskAuditLogSearchUrl(userTaskKey), response);
  }

  public void onProcessDefinitionInstanceStatisticsRequest(
      final ProcessDefinitionInstanceStatisticsQueryResult response) {
    registerPost(RestGatewayPaths.getProcessDefinitionInstanceStatisticsUrl(), response);
  }

  public void onProcessDefinitionInstanceVersionStatisticsRequest(
      final ProcessDefinitionInstanceVersionStatisticsQueryResult response) {
    registerPost(RestGatewayPaths.getProcessDefinitionInstanceVersionStatisticsUrl(), response);
  }

  public void onIncidentProcessInstanceStatisticsByErrorRequest(
      final IncidentProcessInstanceStatisticsByErrorQueryResult response) {
    registerPost(RestGatewayPaths.getIncidentProcessInstanceStatisticsByErrorUrl(), response);
  }

  public void onIncidentProcessInstanceStatisticsByDefinitionRequest(
      final IncidentProcessInstanceStatisticsByDefinitionQueryResult response) {
    registerPost(RestGatewayPaths.getIncidentProcessInstanceStatisticsByDefinitionUrl(), response);
  }

  public void onGlobalJobStatisticsRequest(
      final io.camunda.client.protocol.rest.GlobalJobStatisticsQueryResult response) {
    register(
        WireMock.get(WireMock.urlPathEqualTo(RestGatewayPaths.getGlobalJobStatisticsUrl())),
        response);
  }

  public void onStatusRequestHealthy() {
    onStatusRequest(204);
  }

  public void onStatusRequestUnhealthy() {
    onStatusRequest(503);
  }

  /**
   * Register a status response with a custom HTTP status code.
   *
   * @param statusCode the HTTP status code to return for status requests
   */
  public void onStatusRequest(final int statusCode) {
    mockInfo
        .getWireMock()
        .register(
            WireMock.get(RestGatewayPaths.getStatusUrl())
                .willReturn(WireMock.aResponse().withStatus(statusCode)));
  }
}
