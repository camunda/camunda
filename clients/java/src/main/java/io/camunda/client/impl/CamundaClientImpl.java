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
package io.camunda.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ActivateAdHocSubProcessActivitiesCommandStep1;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.command.AssignClientToGroupCommandStep1;
import io.camunda.client.api.command.AssignClientToTenantCommandStep1;
import io.camunda.client.api.command.AssignGroupToTenantCommandStep1;
import io.camunda.client.api.command.AssignMappingRuleToGroupStep1;
import io.camunda.client.api.command.AssignMappingRuleToTenantCommandStep1;
import io.camunda.client.api.command.AssignRoleToClientCommandStep1;
import io.camunda.client.api.command.AssignRoleToGroupCommandStep1;
import io.camunda.client.api.command.AssignRoleToMappingRuleCommandStep1;
import io.camunda.client.api.command.AssignRoleToTenantCommandStep1;
import io.camunda.client.api.command.AssignRoleToUserCommandStep1;
import io.camunda.client.api.command.AssignUserTaskCommandStep1;
import io.camunda.client.api.command.AssignUserToGroupCommandStep1;
import io.camunda.client.api.command.AssignUserToTenantCommandStep1;
import io.camunda.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.client.api.command.CancelBatchOperationStep1;
import io.camunda.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.client.api.command.CreateDocumentCommandStep1;
import io.camunda.client.api.command.CreateDocumentLinkCommandStep1;
import io.camunda.client.api.command.CreateGroupCommandStep1;
import io.camunda.client.api.command.CreateMappingRuleCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.CreateRoleCommandStep1;
import io.camunda.client.api.command.CreateTenantCommandStep1;
import io.camunda.client.api.command.CreateUserCommandStep1;
import io.camunda.client.api.command.DeleteAuthorizationCommandStep1;
import io.camunda.client.api.command.DeleteDocumentCommandStep1;
import io.camunda.client.api.command.DeleteGroupCommandStep1;
import io.camunda.client.api.command.DeleteMappingRuleCommandStep1;
import io.camunda.client.api.command.DeleteProcessInstanceCommandStep1;
import io.camunda.client.api.command.DeleteResourceCommandStep1;
import io.camunda.client.api.command.DeleteRoleCommandStep1;
import io.camunda.client.api.command.DeleteTenantCommandStep1;
import io.camunda.client.api.command.DeleteUserCommandStep1;
import io.camunda.client.api.command.DeployProcessCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.EvaluateConditionalCommandStep1;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.GloballyScopedClusterVariableCreationCommandStep1;
import io.camunda.client.api.command.GloballyScopedClusterVariableDeletionCommandStep1;
import io.camunda.client.api.command.GloballyScopedClusterVariableUpdateCommandStep1;
import io.camunda.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.PinClockCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.ResetClockCommandStep1;
import io.camunda.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.client.api.command.ResolveProcessInstanceIncidentsCommandStep1;
import io.camunda.client.api.command.ResumeBatchOperationStep1;
import io.camunda.client.api.command.SetVariablesCommandStep1;
import io.camunda.client.api.command.StatusRequestStep1;
import io.camunda.client.api.command.StreamJobsCommandStep1;
import io.camunda.client.api.command.SuspendBatchOperationStep1;
import io.camunda.client.api.command.TenantScopedClusterVariableCreationCommandStep1;
import io.camunda.client.api.command.TenantScopedClusterVariableDeletionCommandStep1;
import io.camunda.client.api.command.TenantScopedClusterVariableUpdateCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.command.UnassignClientFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignClientFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignGroupFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignMappingRuleFromGroupStep1;
import io.camunda.client.api.command.UnassignMappingRuleFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromClientCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromMappingRuleCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromUserCommandStep1;
import io.camunda.client.api.command.UnassignUserFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignUserFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignUserTaskCommandStep1;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1;
import io.camunda.client.api.command.UpdateGroupCommandStep1;
import io.camunda.client.api.command.UpdateJobCommandStep1;
import io.camunda.client.api.command.UpdateMappingRuleCommandStep1;
import io.camunda.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.client.api.command.UpdateRoleCommandStep1;
import io.camunda.client.api.command.UpdateTenantCommandStep1;
import io.camunda.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.client.api.command.UpdateUserCommandStep1;
import io.camunda.client.api.command.UpdateUserTaskCommandStep1;
import io.camunda.client.api.fetch.AuditLogGetRequest;
import io.camunda.client.api.fetch.AuthorizationGetRequest;
import io.camunda.client.api.fetch.AuthorizationsSearchRequest;
import io.camunda.client.api.fetch.BatchOperationGetRequest;
import io.camunda.client.api.fetch.DecisionDefinitionGetRequest;
import io.camunda.client.api.fetch.DecisionDefinitionGetXmlRequest;
import io.camunda.client.api.fetch.DecisionInstanceGetRequest;
import io.camunda.client.api.fetch.DecisionRequirementsGetRequest;
import io.camunda.client.api.fetch.DecisionRequirementsGetXmlRequest;
import io.camunda.client.api.fetch.DocumentContentGetRequest;
import io.camunda.client.api.fetch.ElementInstanceGetRequest;
import io.camunda.client.api.fetch.GloballyScopedClusterVariableGetRequest;
import io.camunda.client.api.fetch.GroupGetRequest;
import io.camunda.client.api.fetch.IncidentGetRequest;
import io.camunda.client.api.fetch.MappingRuleGetRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetFormRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.client.api.fetch.ProcessInstanceGetCallHierarchyRequest;
import io.camunda.client.api.fetch.ProcessInstanceGetRequest;
import io.camunda.client.api.fetch.RoleGetRequest;
import io.camunda.client.api.fetch.RolesSearchRequest;
import io.camunda.client.api.fetch.TenantGetRequest;
import io.camunda.client.api.fetch.TenantScopedClusterVariableGetRequest;
import io.camunda.client.api.fetch.UserGetRequest;
import io.camunda.client.api.fetch.UserTaskGetFormRequest;
import io.camunda.client.api.fetch.UserTaskGetRequest;
import io.camunda.client.api.fetch.VariableGetRequest;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.search.request.AuditLogSearchRequest;
import io.camunda.client.api.search.request.BatchOperationItemSearchRequest;
import io.camunda.client.api.search.request.BatchOperationSearchRequest;
import io.camunda.client.api.search.request.ClientsByGroupSearchRequest;
import io.camunda.client.api.search.request.ClientsByRoleSearchRequest;
import io.camunda.client.api.search.request.ClientsByTenantSearchRequest;
import io.camunda.client.api.search.request.ClusterVariableSearchRequest;
import io.camunda.client.api.search.request.CorrelatedMessageSubscriptionSearchRequest;
import io.camunda.client.api.search.request.DecisionDefinitionSearchRequest;
import io.camunda.client.api.search.request.DecisionInstanceSearchRequest;
import io.camunda.client.api.search.request.DecisionRequirementsSearchRequest;
import io.camunda.client.api.search.request.ElementInstanceSearchRequest;
import io.camunda.client.api.search.request.GroupsByRoleSearchRequest;
import io.camunda.client.api.search.request.GroupsByTenantSearchRequest;
import io.camunda.client.api.search.request.GroupsSearchRequest;
import io.camunda.client.api.search.request.IncidentSearchRequest;
import io.camunda.client.api.search.request.IncidentsByElementInstanceSearchRequest;
import io.camunda.client.api.search.request.IncidentsByProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.JobSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByGroupSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByRoleSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByTenantSearchRequest;
import io.camunda.client.api.search.request.MappingRulesSearchRequest;
import io.camunda.client.api.search.request.MessageSubscriptionSearchRequest;
import io.camunda.client.api.search.request.ProcessDefinitionSearchRequest;
import io.camunda.client.api.search.request.ProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.ProcessInstanceSequenceFlowsRequest;
import io.camunda.client.api.search.request.RolesByGroupSearchRequest;
import io.camunda.client.api.search.request.RolesByTenantSearchRequest;
import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.client.api.search.request.UserTaskAuditLogSearchRequest;
import io.camunda.client.api.search.request.UserTaskSearchRequest;
import io.camunda.client.api.search.request.UserTaskVariableSearchRequest;
import io.camunda.client.api.search.request.UsersByGroupSearchRequest;
import io.camunda.client.api.search.request.UsersByRoleSearchRequest;
import io.camunda.client.api.search.request.UsersByTenantSearchRequest;
import io.camunda.client.api.search.request.UsersSearchRequest;
import io.camunda.client.api.search.request.VariableSearchRequest;
import io.camunda.client.api.statistics.request.GlobalJobStatisticsRequest;
import io.camunda.client.api.statistics.request.IncidentProcessInstanceStatisticsByDefinitionRequest;
import io.camunda.client.api.statistics.request.IncidentProcessInstanceStatisticsByErrorRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionElementStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionInstanceStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionInstanceVersionStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionMessageSubscriptionStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessInstanceElementStatisticsRequest;
import io.camunda.client.api.statistics.request.UsageMetricsStatisticsRequest;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.client.impl.command.ActivateAdHocSubProcessActivitiesCommandImpl;
import io.camunda.client.impl.command.AssignClientToGroupCommandImpl;
import io.camunda.client.impl.command.AssignClientToTenantCommandImpl;
import io.camunda.client.impl.command.AssignGroupToTenantCommandImpl;
import io.camunda.client.impl.command.AssignMappingRuleToGroupCommandImpl;
import io.camunda.client.impl.command.AssignMappingRuleToTenantCommandImpl;
import io.camunda.client.impl.command.AssignRoleToClientCommandImpl;
import io.camunda.client.impl.command.AssignRoleToGroupCommandImpl;
import io.camunda.client.impl.command.AssignRoleToMappingRuleCommandImpl;
import io.camunda.client.impl.command.AssignRoleToTenantCommandImpl;
import io.camunda.client.impl.command.AssignRoleToUserCommandImpl;
import io.camunda.client.impl.command.AssignUserTaskCommandImpl;
import io.camunda.client.impl.command.AssignUserToGroupCommandImpl;
import io.camunda.client.impl.command.AssignUserToTenantCommandImpl;
import io.camunda.client.impl.command.BroadcastSignalCommandImpl;
import io.camunda.client.impl.command.CancelBatchOperationCommandImpl;
import io.camunda.client.impl.command.CancelProcessInstanceCommandImpl;
import io.camunda.client.impl.command.CompleteUserTaskCommandImpl;
import io.camunda.client.impl.command.CorrelateMessageCommandImpl;
import io.camunda.client.impl.command.CreateAuthorizationCommandImpl;
import io.camunda.client.impl.command.CreateBatchOperationCommandImpl.CreateBatchOperationCommandStep1Impl;
import io.camunda.client.impl.command.CreateDocumentBatchCommandImpl;
import io.camunda.client.impl.command.CreateDocumentCommandImpl;
import io.camunda.client.impl.command.CreateDocumentLinkCommandImpl;
import io.camunda.client.impl.command.CreateGroupCommandImpl;
import io.camunda.client.impl.command.CreateMappingRuleCommandImpl;
import io.camunda.client.impl.command.CreateProcessInstanceCommandImpl;
import io.camunda.client.impl.command.CreateRoleCommandImpl;
import io.camunda.client.impl.command.CreateTenantCommandImpl;
import io.camunda.client.impl.command.CreateUserCommandImpl;
import io.camunda.client.impl.command.DeleteAuthorizationCommandImpl;
import io.camunda.client.impl.command.DeleteDocumentCommandImpl;
import io.camunda.client.impl.command.DeleteGroupCommandImpl;
import io.camunda.client.impl.command.DeleteMappingRuleCommandImpl;
import io.camunda.client.impl.command.DeleteProcessInstanceCommandImpl;
import io.camunda.client.impl.command.DeleteResourceCommandImpl;
import io.camunda.client.impl.command.DeleteRoleCommandImpl;
import io.camunda.client.impl.command.DeleteTenantCommandImpl;
import io.camunda.client.impl.command.DeleteUserCommandImpl;
import io.camunda.client.impl.command.DeployProcessCommandImpl;
import io.camunda.client.impl.command.DeployResourceCommandImpl;
import io.camunda.client.impl.command.EvaluateConditionalCommandImpl;
import io.camunda.client.impl.command.EvaluateDecisionCommandImpl;
import io.camunda.client.impl.command.EvaluateExpressionCommandImpl;
import io.camunda.client.impl.command.GloballyScopedCreateClusterVariableImpl;
import io.camunda.client.impl.command.GloballyScopedDeleteClusterVariableImpl;
import io.camunda.client.impl.command.GloballyScopedUpdateClusterVariableImpl;
import io.camunda.client.impl.command.JobUpdateRetriesCommandImpl;
import io.camunda.client.impl.command.JobUpdateTimeoutCommandImpl;
import io.camunda.client.impl.command.MigrateProcessInstanceCommandImpl;
import io.camunda.client.impl.command.ModifyProcessInstanceCommandImpl;
import io.camunda.client.impl.command.PinClockCommandImpl;
import io.camunda.client.impl.command.PublishMessageCommandImpl;
import io.camunda.client.impl.command.ResetClockCommandImpl;
import io.camunda.client.impl.command.ResolveIncidentCommandImpl;
import io.camunda.client.impl.command.ResolveProcessInstanceIncidentsCommandImpl;
import io.camunda.client.impl.command.ResumeBatchOperationCommandImpl;
import io.camunda.client.impl.command.SetVariablesCommandImpl;
import io.camunda.client.impl.command.StatusRequestImpl;
import io.camunda.client.impl.command.StreamJobsCommandImpl;
import io.camunda.client.impl.command.SuspendBatchOperationCommandImpl;
import io.camunda.client.impl.command.TenantScopedCreateClusterVariableImpl;
import io.camunda.client.impl.command.TenantScopedDeleteClusterVariableImpl;
import io.camunda.client.impl.command.TenantScopedUpdateClusterVariableImpl;
import io.camunda.client.impl.command.TopologyRequestImpl;
import io.camunda.client.impl.command.UnassignClientFromGroupCommandImpl;
import io.camunda.client.impl.command.UnassignClientFromTenantCommandImpl;
import io.camunda.client.impl.command.UnassignGroupFromTenantCommandImpl;
import io.camunda.client.impl.command.UnassignMappingRuleFromGroupCommandImpl;
import io.camunda.client.impl.command.UnassignMappingRuleFromTenantCommandImpl;
import io.camunda.client.impl.command.UnassignRoleFromClientCommandImpl;
import io.camunda.client.impl.command.UnassignRoleFromGroupCommandImpl;
import io.camunda.client.impl.command.UnassignRoleFromMappingRuleCommandImpl;
import io.camunda.client.impl.command.UnassignRoleFromTenantCommandImpl;
import io.camunda.client.impl.command.UnassignRoleFromUserCommandImpl;
import io.camunda.client.impl.command.UnassignUserFromGroupCommandImpl;
import io.camunda.client.impl.command.UnassignUserFromTenantCommandImpl;
import io.camunda.client.impl.command.UnassignUserTaskCommandImpl;
import io.camunda.client.impl.command.UpdateAuthorizationCommandImpl;
import io.camunda.client.impl.command.UpdateGroupCommandImpl;
import io.camunda.client.impl.command.UpdateJobCommandImpl;
import io.camunda.client.impl.command.UpdateMappingRuleCommandImpl;
import io.camunda.client.impl.command.UpdateRoleCommandImpl;
import io.camunda.client.impl.command.UpdateTenantCommandImpl;
import io.camunda.client.impl.command.UpdateUserCommandImpl;
import io.camunda.client.impl.command.UpdateUserTaskCommandImpl;
import io.camunda.client.impl.fetch.AuditLogGetRequestImpl;
import io.camunda.client.impl.fetch.AuthorizationGetRequestImpl;
import io.camunda.client.impl.fetch.BatchOperationGetRequestImpl;
import io.camunda.client.impl.fetch.DecisionDefinitionGetRequestImpl;
import io.camunda.client.impl.fetch.DecisionDefinitionGetXmlRequestImpl;
import io.camunda.client.impl.fetch.DecisionInstanceGetRequestImpl;
import io.camunda.client.impl.fetch.DecisionRequirementsGetRequestImpl;
import io.camunda.client.impl.fetch.DecisionRequirementsGetXmlRequestImpl;
import io.camunda.client.impl.fetch.DocumentContentGetRequestImpl;
import io.camunda.client.impl.fetch.ElementInstanceGetRequestImpl;
import io.camunda.client.impl.fetch.GloballyScopedClusterVariableGetRequestImpl;
import io.camunda.client.impl.fetch.GroupGetRequestImpl;
import io.camunda.client.impl.fetch.IncidentGetRequestImpl;
import io.camunda.client.impl.fetch.MappingRuleGetRequestImpl;
import io.camunda.client.impl.fetch.ProcessDefinitionGetFormRequestImpl;
import io.camunda.client.impl.fetch.ProcessDefinitionGetRequestImpl;
import io.camunda.client.impl.fetch.ProcessDefinitionGetXmlRequestImpl;
import io.camunda.client.impl.fetch.ProcessInstanceGetCallHierarchyRequestImpl;
import io.camunda.client.impl.fetch.ProcessInstanceGetRequestImpl;
import io.camunda.client.impl.fetch.RoleGetRequestImpl;
import io.camunda.client.impl.fetch.TenantGetRequestImpl;
import io.camunda.client.impl.fetch.TenantScopedClusterVariableGetRequestImpl;
import io.camunda.client.impl.fetch.UserGetRequestImpl;
import io.camunda.client.impl.fetch.UserTaskGetFormRequestImpl;
import io.camunda.client.impl.fetch.UserTaskGetRequestImpl;
import io.camunda.client.impl.fetch.VariableGetRequestImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.http.HttpClientFactory;
import io.camunda.client.impl.search.request.AuditLogSearchRequestImpl;
import io.camunda.client.impl.search.request.AuthorizationsSearchRequestImpl;
import io.camunda.client.impl.search.request.BatchOperationItemSearchRequestImpl;
import io.camunda.client.impl.search.request.BatchOperationSearchRequestImpl;
import io.camunda.client.impl.search.request.ClientsByGroupSearchRequestImpl;
import io.camunda.client.impl.search.request.ClientsByRoleSearchRequestImpl;
import io.camunda.client.impl.search.request.ClientsByTenantSearchRequestImpl;
import io.camunda.client.impl.search.request.ClusterVariableSearchRequestImpl;
import io.camunda.client.impl.search.request.CorrelatedMessageSubscriptionSearchRequestImpl;
import io.camunda.client.impl.search.request.DecisionDefinitionSearchRequestImpl;
import io.camunda.client.impl.search.request.DecisionInstanceSearchRequestImpl;
import io.camunda.client.impl.search.request.DecisionRequirementsSearchRequestImpl;
import io.camunda.client.impl.search.request.ElementInstanceSearchRequestImpl;
import io.camunda.client.impl.search.request.GroupSearchRequestImpl;
import io.camunda.client.impl.search.request.GroupsByRoleSearchRequestImpl;
import io.camunda.client.impl.search.request.GroupsByTenantSearchRequestImpl;
import io.camunda.client.impl.search.request.IncidentSearchRequestImpl;
import io.camunda.client.impl.search.request.IncidentsByElementInstanceSearchRequestImpl;
import io.camunda.client.impl.search.request.IncidentsByProcessInstanceSearchRequestImpl;
import io.camunda.client.impl.search.request.JobSearchRequestImpl;
import io.camunda.client.impl.search.request.MappingRulesByGroupSearchRequestImpl;
import io.camunda.client.impl.search.request.MappingRulesByRoleSearchRequestImpl;
import io.camunda.client.impl.search.request.MappingRulesByTenantSearchRequestImpl;
import io.camunda.client.impl.search.request.MappingRulesSearchRequestImpl;
import io.camunda.client.impl.search.request.MessageSubscriptionSearchRequestImpl;
import io.camunda.client.impl.search.request.ProcessDefinitionSearchRequestImpl;
import io.camunda.client.impl.search.request.ProcessInstanceSearchRequestImpl;
import io.camunda.client.impl.search.request.ProcessInstanceSequenceFlowsRequestImpl;
import io.camunda.client.impl.search.request.RolesByGroupSearchRequestImpl;
import io.camunda.client.impl.search.request.RolesByTenantSearchRequestImpl;
import io.camunda.client.impl.search.request.RolesSearchRequestImpl;
import io.camunda.client.impl.search.request.TenantsSearchRequestImpl;
import io.camunda.client.impl.search.request.UserTaskAuditLogSearchRequestImpl;
import io.camunda.client.impl.search.request.UserTaskSearchRequestImpl;
import io.camunda.client.impl.search.request.UserTaskVariableSearchRequestImpl;
import io.camunda.client.impl.search.request.UsersByGroupSearchRequestImpl;
import io.camunda.client.impl.search.request.UsersByRoleSearchRequestImpl;
import io.camunda.client.impl.search.request.UsersByTenantSearchRequestImpl;
import io.camunda.client.impl.search.request.UsersSearchRequestImpl;
import io.camunda.client.impl.search.request.VariableSearchRequestImpl;
import io.camunda.client.impl.statistics.request.GlobalJobStatisticsRequestImpl;
import io.camunda.client.impl.statistics.request.IncidentProcessInstanceStatisticsByDefinitionRequestImpl;
import io.camunda.client.impl.statistics.request.IncidentProcessInstanceStatisticsByErrorRequestImpl;
import io.camunda.client.impl.statistics.request.ProcessDefinitionElementStatisticsRequestImpl;
import io.camunda.client.impl.statistics.request.ProcessDefinitionInstanceStatisticsRequestImpl;
import io.camunda.client.impl.statistics.request.ProcessDefinitionInstanceVersionStatisticsRequestImpl;
import io.camunda.client.impl.statistics.request.ProcessDefinitionMessageSubscriptionStatisticsRequestImpl;
import io.camunda.client.impl.statistics.request.ProcessInstanceElementStatisticsRequestImpl;
import io.camunda.client.impl.statistics.request.UsageMetricsStatisticsRequestImpl;
import io.camunda.client.impl.util.AddressUtil;
import io.camunda.client.impl.util.JobWorkerExecutors;
import io.camunda.client.impl.util.VersionUtil;
import io.camunda.client.impl.worker.JobClientImpl;
import io.camunda.client.impl.worker.JobWorkerBuilderImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CamundaClientImpl implements CamundaClient {
  private final CamundaClientConfiguration config;
  private final JsonMapper jsonMapper;
  private final GatewayStub asyncStub;
  private final ManagedChannel channel;
  private final JobWorkerExecutors executorResource;
  private final List<Closeable> closeables = new CopyOnWriteArrayList<>();
  private final JobClient jobClient;
  private final CredentialsProvider credentialsProvider;
  private final HttpClient httpClient;

  public CamundaClientImpl(final CamundaClientConfiguration configuration) {
    this(configuration, buildChannel(configuration));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration configuration, final ManagedChannel channel) {
    this(configuration, channel, buildGatewayStub(channel, configuration));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration configuration,
      final ManagedChannel channel,
      final HttpClient httpClient) {
    this(
        configuration,
        channel,
        buildGatewayStub(channel, configuration),
        buildExecutorService(configuration),
        httpClient);
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration configuration,
      final ManagedChannel channel,
      final GatewayStub gatewayStub) {
    this(configuration, channel, gatewayStub, buildExecutorService(configuration));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration config,
      final ManagedChannel channel,
      final GatewayStub gatewayStub,
      final JobWorkerExecutors executorResource) {
    this(config, channel, gatewayStub, executorResource, buildHttpClient(config));
  }

  public CamundaClientImpl(
      final CamundaClientConfiguration config,
      final ManagedChannel channel,
      final GatewayStub gatewayStub,
      final JobWorkerExecutors executorResource,
      final HttpClient httpClient) {
    this.config = config;
    jsonMapper = config.getJsonMapper();
    this.channel = channel;
    asyncStub = gatewayStub;
    this.executorResource = executorResource;
    this.httpClient = httpClient;

    if (config.getCredentialsProvider() != null) {
      credentialsProvider = config.getCredentialsProvider();
    } else {
      credentialsProvider = new NoopCredentialsProvider();
    }
    jobClient = newJobClient();
    this.httpClient.start();
  }

  private static HttpClient buildHttpClient(final CamundaClientConfiguration config) {
    return new HttpClientFactory(config).createClient();
  }

  public static ManagedChannel buildChannel(final CamundaClientConfiguration config) {
    final URI address;
    address = config.getGrpcAddress();

    final NettyChannelBuilder channelBuilder =
        NettyChannelBuilder.forAddress(address.getHost(), address.getPort());

    configureConnectionSecurity(config, channelBuilder);
    channelBuilder.keepAliveTime(config.getKeepAlive().toMillis(), TimeUnit.MILLISECONDS);
    channelBuilder.userAgent("camunda-client-java/" + VersionUtil.getVersion());
    channelBuilder.maxInboundMessageSize(config.getMaxMessageSize());
    channelBuilder.maxInboundMetadataSize(config.getMaxMetadataSize());

    if (config.useDefaultRetryPolicy()) {
      final Map<String, Object> serviceConfig = defaultServiceConfig();
      if (!serviceConfig.isEmpty()) {
        channelBuilder.defaultServiceConfig(serviceConfig);
        channelBuilder.enableRetry();
      }
    }

    return channelBuilder.build();
  }

  private static CallCredentials buildCallCredentials(final CamundaClientConfiguration config) {
    final CredentialsProvider customCredentialsProvider = config.getCredentialsProvider();

    if (customCredentialsProvider == null) {
      return null;
    }

    return new CamundaCallCredentials(customCredentialsProvider);
  }

  private static void configureConnectionSecurity(
      final CamundaClientConfiguration config, final NettyChannelBuilder channelBuilder) {
    final URI grpcAddress = config.getGrpcAddress();
    if (!AddressUtil.isPlaintextConnection(grpcAddress)) {
      final String certificatePath = config.getCaCertificatePath();
      SslContext sslContext = null;

      if (certificatePath != null) {
        if (certificatePath.isEmpty()) {
          throw new IllegalArgumentException(
              "Expected valid certificate path but found empty path instead.");
        }

        try (final FileInputStream certInputStream = new FileInputStream(certificatePath)) {
          sslContext = GrpcSslContexts.forClient().trustManager(certInputStream).build();
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }

      channelBuilder.useTransportSecurity().sslContext(sslContext);
      if (config.getOverrideAuthority() != null) {
        channelBuilder.overrideAuthority(config.getOverrideAuthority());
      }
    } else {
      channelBuilder.usePlaintext();
    }
  }

  public static GatewayStub buildGatewayStub(
      final ManagedChannel channel, final CamundaClientConfiguration config) {
    final CallCredentials credentials = buildCallCredentials(config);
    final GatewayStub gatewayStub = GatewayGrpc.newStub(channel).withCallCredentials(credentials);
    if (!config.getInterceptors().isEmpty()) {
      return gatewayStub.withInterceptors(
          config.getInterceptors().toArray(new ClientInterceptor[] {}));
    }
    return gatewayStub;
  }

  private static Map<String, Object> defaultServiceConfig() {
    final ObjectMapper objectMapper = new ObjectMapper();
    final URL defaultServiceConfig =
        ClassLoader.getSystemClassLoader().getResource("gateway-service-config.json");
    if (defaultServiceConfig == null) {
      Loggers.LOGGER.info(
          "No default service config found on classpath; will not configure a default retry policy");
      return new HashMap<>();
    }

    try {
      return objectMapper.readValue(
          defaultServiceConfig, new TypeReference<Map<String, Object>>() {});
    } catch (final IOException e) {
      Loggers.LOGGER.warn(
          "Failed to read default service config from classpath; will not configure a default retry policy",
          e);
      return new HashMap<>();
    }
  }

  private static JobWorkerExecutors buildExecutorService(
      final CamundaClientConfiguration configuration) {

    final ScheduledExecutorService scheduledExecutor;
    final boolean ownsScheduledExecutor;

    if (configuration.jobWorkerSchedulingExecutor() != null) {
      scheduledExecutor = configuration.jobWorkerSchedulingExecutor();
      ownsScheduledExecutor = configuration.ownsJobWorkerSchedulingExecutor();
    } else {
      // default to using a scheduled executor with 0 core pool, which creates threads as needed
      scheduledExecutor = Executors.newScheduledThreadPool(0);
      ownsScheduledExecutor = true;
    }

    final ExecutorService jobHandlingExecutor;
    final boolean ownsJobHandlingExecutor;

    if (configuration.jobHandlingExecutor() != null) {
      jobHandlingExecutor = configuration.jobHandlingExecutor();
      ownsJobHandlingExecutor = configuration.ownsJobHandlingExecutor();
    } else {
      final int threadCount = configuration.getNumJobWorkerExecutionThreads();

      if (threadCount == 0) {
        // fallback to using the scheduled executor for both purposes
        // this ensures backward compatibility with the old behavior when 0 was accepted
        // as a valid value; this reuses the single-threaded scheduled executor for job handling
        jobHandlingExecutor = scheduledExecutor;
        ownsJobHandlingExecutor = false; // since both executors are the same
      } else {
        jobHandlingExecutor = Executors.newFixedThreadPool(threadCount);
        ownsJobHandlingExecutor = true;
      }
    }

    return new JobWorkerExecutors(
        scheduledExecutor, ownsScheduledExecutor, jobHandlingExecutor, ownsJobHandlingExecutor);
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestImpl(
        asyncStub,
        httpClient,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        config.preferRestOverGrpc());
  }

  @Override
  public StatusRequestStep1 newStatusRequest() {
    return new StatusRequestImpl(httpClient, config.getDefaultRequestTimeout());
  }

  @Override
  public CamundaClientConfiguration getConfiguration() {
    return config;
  }

  @Override
  public void close() {
    closeables.forEach(
        c -> {
          try {
            c.close();
          } catch (final IOException e) {
            // ignore
          }
        });

    executorResource.close();
    channel.shutdownNow();

    try {
      if (!channel.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException(
            "Timed out awaiting termination of in-flight request channel after 15 seconds");
      }
    } catch (final InterruptedException e) {
      throw new ClientException(
          "Unexpectedly interrupted awaiting termination of in-flight request channel", e);
    }

    try {
      httpClient.close();
    } catch (final Exception e) {
      throw new ClientException("Failed closing http client.", e);
    }
  }

  @Override
  public DeployProcessCommandStep1 newDeployCommand() {
    return new DeployProcessCommandImpl(
        asyncStub, config.getDefaultRequestTimeout(), credentialsProvider::shouldRetryRequest);
  }

  @Override
  public DeployResourceCommandStep1 newDeployResourceCommand() {
    return new DeployResourceCommandImpl(
        asyncStub,
        config,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc(),
        jsonMapper);
  }

  @Override
  public CreateProcessInstanceCommandStep1 newCreateInstanceCommand() {
    return new CreateProcessInstanceCommandImpl(
        asyncStub,
        jsonMapper,
        config,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public ModifyProcessInstanceCommandStep1 newModifyProcessInstanceCommand(
      final long processInstanceKey) {
    return new ModifyProcessInstanceCommandImpl(
        processInstanceKey,
        jsonMapper,
        asyncStub,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config);
  }

  @Override
  public MigrateProcessInstanceCommandStep1 newMigrateProcessInstanceCommand(
      final long processInstanceKey) {
    return new MigrateProcessInstanceCommandImpl(
        processInstanceKey,
        asyncStub,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config,
        jsonMapper);
  }

  @Override
  public CancelProcessInstanceCommandStep1 newCancelInstanceCommand(final long processInstanceKey) {
    return new CancelProcessInstanceCommandImpl(
        asyncStub,
        processInstanceKey,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config,
        jsonMapper);
  }

  @Override
  public DeleteProcessInstanceCommandStep1 newDeleteInstanceCommand(final long processInstanceKey) {
    return new DeleteProcessInstanceCommandImpl(processInstanceKey, config, httpClient, jsonMapper);
  }

  @Override
  public SetVariablesCommandStep1 newSetVariablesCommand(final long elementInstanceKey) {
    return new SetVariablesCommandImpl(
        asyncStub,
        jsonMapper,
        elementInstanceKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public EvaluateDecisionCommandStep1 newEvaluateDecisionCommand() {
    return new EvaluateDecisionCommandImpl(
        asyncStub, jsonMapper, config, credentialsProvider::shouldRetryRequest, httpClient);
  }

  @Override
  public PublishMessageCommandStep1 newPublishMessageCommand() {
    return new PublishMessageCommandImpl(
        asyncStub,
        config,
        jsonMapper,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc());
  }

  @Override
  public CorrelateMessageCommandStep1 newCorrelateMessageCommand() {
    return new CorrelateMessageCommandImpl(config, httpClient, jsonMapper);
  }

  @Override
  public BroadcastSignalCommandStep1 newBroadcastSignalCommand() {
    return new BroadcastSignalCommandImpl(
        asyncStub, config, jsonMapper, credentialsProvider::shouldRetryRequest, httpClient);
  }

  @Override
  public ResolveIncidentCommandStep1 newResolveIncidentCommand(final long incidentKey) {
    return new ResolveIncidentCommandImpl(
        asyncStub,
        incidentKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc(),
        jsonMapper);
  }

  @Override
  public ResolveProcessInstanceIncidentsCommandStep1 newResolveProcessInstanceIncidentsCommand(
      final long processInstanceKey) {
    return new ResolveProcessInstanceIncidentsCommandImpl(
        processInstanceKey, config.getDefaultRequestTimeout(), httpClient);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final long jobKey) {
    return new JobUpdateRetriesCommandImpl(
        asyncStub,
        jobKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc(),
        jsonMapper);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final ActivatedJob job) {
    return newUpdateRetriesCommand(job.getKey());
  }

  @Override
  public UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(final long jobKey) {
    return new JobUpdateTimeoutCommandImpl(
        asyncStub,
        jobKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config.preferRestOverGrpc(),
        jsonMapper);
  }

  @Override
  public UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(final ActivatedJob job) {
    return newUpdateTimeoutCommand(job.getKey());
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    return new JobWorkerBuilderImpl(
        config,
        jobClient,
        executorResource.scheduledExecutor(),
        executorResource.jobHandlingExecutor(),
        closeables);
  }

  @Override
  public DeleteResourceCommandStep1 newDeleteResourceCommand(final long resourceKey) {
    return new DeleteResourceCommandImpl(
        resourceKey,
        false,
        asyncStub,
        credentialsProvider::shouldRetryRequest,
        httpClient,
        config,
        jsonMapper);
  }

  @Override
  public CompleteUserTaskCommandStep1 newCompleteUserTaskCommand(final long userTaskKey) {
    return new CompleteUserTaskCommandImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public AssignUserTaskCommandStep1 newAssignUserTaskCommand(final long userTaskKey) {
    return new AssignUserTaskCommandImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public UpdateUserTaskCommandStep1 newUpdateUserTaskCommand(final long userTaskKey) {
    return new UpdateUserTaskCommandImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public UnassignUserTaskCommandStep1 newUnassignUserTaskCommand(final long userTaskKey) {
    return new UnassignUserTaskCommandImpl(httpClient, userTaskKey);
  }

  @Override
  public UpdateJobCommandStep1 newUpdateJobCommand(final long jobKey) {
    return new UpdateJobCommandImpl(jobKey, httpClient, jsonMapper);
  }

  @Override
  public UpdateJobCommandStep1 newUpdateJobCommand(final ActivatedJob job) {
    return newUpdateJobCommand(job.getKey());
  }

  @Override
  public PinClockCommandStep1 newPinClockCommand() {
    return new PinClockCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public ResetClockCommandStep1 newResetClockCommand() {
    return new ResetClockCommandImpl(httpClient);
  }

  @Override
  public ProcessDefinitionGetRequest newProcessDefinitionGetRequest(
      final long processDefinitionKey) {
    return new ProcessDefinitionGetRequestImpl(httpClient, processDefinitionKey);
  }

  @Override
  public ProcessDefinitionGetXmlRequest newProcessDefinitionGetXmlRequest(
      final long processDefinitionKey) {
    return new ProcessDefinitionGetXmlRequestImpl(httpClient, processDefinitionKey);
  }

  @Override
  public ProcessDefinitionGetFormRequest newProcessDefinitionGetFormRequest(
      final long processDefinitionKey) {
    return new ProcessDefinitionGetFormRequestImpl(httpClient, processDefinitionKey);
  }

  @Override
  public ProcessDefinitionSearchRequest newProcessDefinitionSearchRequest() {
    return new ProcessDefinitionSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public ProcessDefinitionElementStatisticsRequest newProcessDefinitionElementStatisticsRequest(
      final long processDefinitionKey) {
    return new ProcessDefinitionElementStatisticsRequestImpl(
        httpClient, jsonMapper, processDefinitionKey);
  }

  @Override
  public ProcessInstanceElementStatisticsRequest newProcessInstanceElementStatisticsRequest(
      final long processInstanceKey) {
    return new ProcessInstanceElementStatisticsRequestImpl(httpClient, processInstanceKey);
  }

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsRequest
      newProcessDefinitionMessageSubscriptionStatisticsRequest() {
    return new ProcessDefinitionMessageSubscriptionStatisticsRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public UsageMetricsStatisticsRequest newUsageMetricsRequest(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    return new UsageMetricsStatisticsRequestImpl(httpClient, startTime, endTime);
  }

  @Override
  public GlobalJobStatisticsRequest newGlobalJobStatisticsRequest(
      final OffsetDateTime from, final OffsetDateTime to) {
    return new GlobalJobStatisticsRequestImpl(httpClient, from, to);
  }

  @Override
  public ProcessInstanceSequenceFlowsRequest newProcessInstanceSequenceFlowsRequest(
      final long processInstanceKey) {
    return new ProcessInstanceSequenceFlowsRequestImpl(httpClient, processInstanceKey);
  }

  @Override
  public ProcessInstanceGetRequest newProcessInstanceGetRequest(final long processInstanceKey) {
    return new ProcessInstanceGetRequestImpl(httpClient, processInstanceKey);
  }

  @Override
  public ProcessInstanceSearchRequest newProcessInstanceSearchRequest() {
    return new ProcessInstanceSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public ElementInstanceSearchRequest newElementInstanceSearchRequest() {
    return new ElementInstanceSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public ElementInstanceGetRequest newElementInstanceGetRequest(final long elementInstanceKey) {
    return new ElementInstanceGetRequestImpl(httpClient, elementInstanceKey);
  }

  @Override
  public ActivateAdHocSubProcessActivitiesCommandStep1 newActivateAdHocSubProcessActivitiesCommand(
      final String adHocSubProcessInstanceKey) {
    return new ActivateAdHocSubProcessActivitiesCommandImpl(
        httpClient, jsonMapper, adHocSubProcessInstanceKey);
  }

  @Override
  public UserTaskSearchRequest newUserTaskSearchRequest() {
    return new UserTaskSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionRequirementsSearchRequest newDecisionRequirementsSearchRequest() {
    return new DecisionRequirementsSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionDefinitionSearchRequest newDecisionDefinitionSearchRequest() {
    return new DecisionDefinitionSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionDefinitionGetRequest newDecisionDefinitionGetRequest(
      final long decisionDefinitionKey) {
    return new DecisionDefinitionGetRequestImpl(httpClient, decisionDefinitionKey);
  }

  @Override
  public DecisionDefinitionGetXmlRequest newDecisionDefinitionGetXmlRequest(
      final long decisionKey) {
    return new DecisionDefinitionGetXmlRequestImpl(httpClient, decisionKey);
  }

  @Override
  public DecisionInstanceSearchRequest newDecisionInstanceSearchRequest() {
    return new DecisionInstanceSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public DecisionInstanceGetRequest newDecisionInstanceGetRequest(final String decisionInstanceId) {
    return new DecisionInstanceGetRequestImpl(httpClient, jsonMapper, decisionInstanceId);
  }

  @Override
  public IncidentSearchRequest newIncidentSearchRequest() {
    return new IncidentSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public IncidentGetRequest newIncidentGetRequest(final long incidentKey) {
    return new IncidentGetRequestImpl(httpClient, incidentKey);
  }

  @Override
  public CreateRoleCommandStep1 newCreateRoleCommand() {
    return new CreateRoleCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public RoleGetRequest newRoleGetRequest(final String roleId) {
    return new RoleGetRequestImpl(httpClient, roleId);
  }

  @Override
  public RolesSearchRequest newRolesSearchRequest() {
    return new RolesSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public UpdateRoleCommandStep1 newUpdateRoleCommand(final String roleId) {
    return new UpdateRoleCommandImpl(httpClient, roleId, jsonMapper);
  }

  @Override
  public AssignRoleToMappingRuleCommandStep1 newAssignRoleToMappingRuleCommand() {
    return new AssignRoleToMappingRuleCommandImpl(httpClient);
  }

  @Override
  public DeleteRoleCommandStep1 newDeleteRoleCommand(final String roleId) {
    return new DeleteRoleCommandImpl(httpClient, roleId);
  }

  @Override
  public AssignRoleToGroupCommandStep1 newAssignRoleToGroupCommand() {
    return new AssignRoleToGroupCommandImpl(httpClient);
  }

  @Override
  public AssignRoleToClientCommandStep1 newAssignRoleToClientCommand() {
    return new AssignRoleToClientCommandImpl(httpClient);
  }

  @Override
  public ClientsByRoleSearchRequest newClientsByRoleSearchRequest(final String roleId) {
    return new ClientsByRoleSearchRequestImpl(httpClient, jsonMapper, roleId);
  }

  @Override
  public ClientsByGroupSearchRequest newClientsByGroupSearchRequest(final String groupId) {
    return new ClientsByGroupSearchRequestImpl(httpClient, jsonMapper, groupId);
  }

  @Override
  public ClientsByTenantSearchRequest newClientsByTenantSearchRequest(final String tenantId) {
    return new ClientsByTenantSearchRequestImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public AssignRoleToTenantCommandStep1 newAssignRoleToTenantCommand() {
    return new AssignRoleToTenantCommandImpl(httpClient);
  }

  @Override
  public UnassignRoleFromTenantCommandStep1 newUnassignRoleFromTenantCommand() {
    return new UnassignRoleFromTenantCommandImpl(httpClient);
  }

  @Override
  public RolesByTenantSearchRequest newRolesByTenantSearchRequest(final String tenantId) {
    return new RolesByTenantSearchRequestImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public UnassignRoleFromGroupCommandStep1 newUnassignRoleFromGroupCommand() {
    return new UnassignRoleFromGroupCommandImpl(httpClient);
  }

  @Override
  public UnassignRoleFromMappingRuleCommandStep1 newUnassignRoleFromMappingRuleCommand() {
    return new UnassignRoleFromMappingRuleCommandImpl(httpClient);
  }

  @Override
  public UnassignRoleFromClientCommandStep1 newUnassignRoleFromClientCommand() {
    return new UnassignRoleFromClientCommandImpl(httpClient);
  }

  @Override
  public AssignRoleToUserCommandStep1 newAssignRoleToUserCommand() {
    return new AssignRoleToUserCommandImpl(httpClient);
  }

  @Override
  public UnassignRoleFromUserCommandStep1 newUnassignRoleFromUserCommand() {
    return new UnassignRoleFromUserCommandImpl(httpClient);
  }

  @Override
  public UsersByRoleSearchRequest newUsersByRoleSearchRequest(final String roleId) {
    return new UsersByRoleSearchRequestImpl(httpClient, jsonMapper, roleId);
  }

  @Override
  public UsersByTenantSearchRequest newUsersByTenantSearchRequest(final String tenantId) {
    return new UsersByTenantSearchRequestImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public UsersSearchRequest newUsersSearchRequest() {
    return new UsersSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public CreateGroupCommandStep1 newCreateGroupCommand() {
    return new CreateGroupCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public UpdateGroupCommandStep1 newUpdateGroupCommand(final String groupId) {
    return new UpdateGroupCommandImpl(groupId, httpClient, jsonMapper);
  }

  @Override
  public DeleteGroupCommandStep1 newDeleteGroupCommand(final String groupId) {
    return new DeleteGroupCommandImpl(groupId, httpClient);
  }

  @Override
  public DeleteMappingRuleCommandStep1 newDeleteMappingRuleCommand(final String mappingRuleId) {
    return new DeleteMappingRuleCommandImpl(mappingRuleId, httpClient);
  }

  @Override
  public MappingRuleGetRequest newMappingRuleGetRequest(final String mappingRuleId) {
    return new MappingRuleGetRequestImpl(httpClient, mappingRuleId);
  }

  @Override
  public AssignUserToGroupCommandStep1 newAssignUserToGroupCommand() {
    return new AssignUserToGroupCommandImpl(httpClient);
  }

  @Override
  public UnassignUserFromGroupCommandStep1 newUnassignUserFromGroupCommand() {
    return new UnassignUserFromGroupCommandImpl(httpClient);
  }

  @Override
  public CreateUserCommandStep1 newCreateUserCommand() {
    return new CreateUserCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public DeleteUserCommandStep1 newDeleteUserCommand(final String username) {
    return new DeleteUserCommandImpl(httpClient, username);
  }

  @Override
  public UpdateUserCommandStep1 newUpdateUserCommand(final String username) {
    return new UpdateUserCommandImpl(httpClient, username, jsonMapper);
  }

  @Override
  public UserGetRequest newUserGetRequest(final String username) {
    return new UserGetRequestImpl(httpClient, username);
  }

  @Override
  public CreateMappingRuleCommandStep1 newCreateMappingRuleCommand() {
    return new CreateMappingRuleCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public UpdateMappingRuleCommandStep1 newUpdateMappingRuleCommand(final String mappingRuleId) {
    return new UpdateMappingRuleCommandImpl(httpClient, mappingRuleId, jsonMapper);
  }

  @Override
  public DecisionRequirementsGetXmlRequest newDecisionRequirementsGetXmlRequest(
      final long decisionRequirementsKey) {
    return new DecisionRequirementsGetXmlRequestImpl(httpClient, decisionRequirementsKey);
  }

  @Override
  public DecisionRequirementsGetRequest newDecisionRequirementsGetRequest(
      final long decisionRequirementsKey) {
    return new DecisionRequirementsGetRequestImpl(httpClient, decisionRequirementsKey);
  }

  @Override
  public UserTaskGetFormRequest newUserTaskGetFormRequest(final long userTaskKey) {
    return new UserTaskGetFormRequestImpl(httpClient, userTaskKey);
  }

  @Override
  public UserTaskGetRequest newUserTaskGetRequest(final long userTaskKey) {
    return new UserTaskGetRequestImpl(httpClient, userTaskKey);
  }

  @Override
  public VariableSearchRequest newVariableSearchRequest() {
    return new VariableSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public VariableGetRequest newVariableGetRequest(final long variableKey) {
    return new VariableGetRequestImpl(httpClient, variableKey);
  }

  @Override
  public GloballyScopedClusterVariableCreationCommandStep1
      newGloballyScopedClusterVariableCreateRequest() {
    return new GloballyScopedCreateClusterVariableImpl(httpClient, jsonMapper);
  }

  @Override
  public TenantScopedClusterVariableCreationCommandStep1
      newTenantScopedClusterVariableCreateRequest(final String tenantId) {
    return new TenantScopedCreateClusterVariableImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public GloballyScopedClusterVariableUpdateCommandStep1
      newGloballyScopedClusterVariableUpdateRequest() {
    return new GloballyScopedUpdateClusterVariableImpl(httpClient, jsonMapper);
  }

  @Override
  public TenantScopedClusterVariableUpdateCommandStep1 newTenantScopedClusterVariableUpdateRequest(
      final String tenantId) {
    return new TenantScopedUpdateClusterVariableImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public GloballyScopedClusterVariableDeletionCommandStep1
      newGloballyScopedClusterVariableDeleteRequest() {
    return new GloballyScopedDeleteClusterVariableImpl(httpClient);
  }

  @Override
  public TenantScopedClusterVariableDeletionCommandStep1
      newTenantScopedClusterVariableDeleteRequest(final String tenantId) {
    return new TenantScopedDeleteClusterVariableImpl(httpClient, tenantId);
  }

  @Override
  public GloballyScopedClusterVariableGetRequest newGloballyScopedClusterVariableGetRequest() {
    return new GloballyScopedClusterVariableGetRequestImpl(httpClient);
  }

  @Override
  public TenantScopedClusterVariableGetRequest newTenantScopedClusterVariableGetRequest(
      final String tenantId) {
    return new TenantScopedClusterVariableGetRequestImpl(httpClient, tenantId);
  }

  @Override
  public ClusterVariableSearchRequest newClusterVariableSearchRequest() {
    return new ClusterVariableSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public UserTaskVariableSearchRequest newUserTaskVariableSearchRequest(final long userTaskKey) {
    return new UserTaskVariableSearchRequestImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public CreateDocumentCommandStep1 newCreateDocumentCommand() {
    return new CreateDocumentCommandImpl(jsonMapper, httpClient, config);
  }

  @Override
  public CreateDocumentBatchCommandStep1 newCreateDocumentBatchCommand() {
    return new CreateDocumentBatchCommandImpl(jsonMapper, httpClient, config);
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(final String documentId) {
    return new DocumentContentGetRequestImpl(httpClient, documentId, null, null, config);
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(
      final DocumentReferenceResponse documentReference) {
    return new DocumentContentGetRequestImpl(
        httpClient,
        documentReference.getDocumentId(),
        documentReference.getStoreId(),
        documentReference.getContentHash(),
        config);
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(final String documentId) {
    return new CreateDocumentLinkCommandImpl(
        documentId, null, null, jsonMapper, httpClient, config);
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(
      final DocumentReferenceResponse documentReference) {
    return new CreateDocumentLinkCommandImpl(
        documentReference.getDocumentId(),
        documentReference.getStoreId(),
        documentReference.getContentHash(),
        jsonMapper,
        httpClient,
        config);
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(final String documentId) {
    return new DeleteDocumentCommandImpl(documentId, null, httpClient, config);
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(
      final DocumentReferenceResponse documentReference) {
    return new DeleteDocumentCommandImpl(
        documentReference.getDocumentId(), documentReference.getStoreId(), httpClient, config);
  }

  @Override
  public CreateTenantCommandStep1 newCreateTenantCommand() {
    return new CreateTenantCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public UpdateTenantCommandStep1 newUpdateTenantCommand(final String tenantId) {
    return new UpdateTenantCommandImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public TenantGetRequest newTenantGetRequest(final String tenantId) {
    return new TenantGetRequestImpl(httpClient, tenantId);
  }

  @Override
  public TenantsSearchRequest newTenantsSearchRequest() {
    return new TenantsSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public DeleteTenantCommandStep1 newDeleteTenantCommand(final String tenantId) {
    return new DeleteTenantCommandImpl(httpClient).tenantId(tenantId);
  }

  @Override
  public AssignMappingRuleToTenantCommandStep1 newAssignMappingRuleToTenantCommand() {
    return new AssignMappingRuleToTenantCommandImpl(httpClient);
  }

  @Override
  public AssignUserToTenantCommandStep1 newAssignUserToTenantCommand() {
    return new AssignUserToTenantCommandImpl(httpClient);
  }

  @Override
  public UnassignUserFromTenantCommandStep1 newUnassignUserFromTenantCommand() {
    return new UnassignUserFromTenantCommandImpl(httpClient);
  }

  @Override
  public AssignGroupToTenantCommandStep1 newAssignGroupToTenantCommand() {
    return new AssignGroupToTenantCommandImpl(httpClient);
  }

  @Override
  public UnassignGroupFromTenantCommandStep1 newUnassignGroupFromTenantCommand() {
    return new UnassignGroupFromTenantCommandImpl(httpClient);
  }

  @Override
  public AssignClientToGroupCommandStep1 newAssignClientToGroupCommand() {
    return new AssignClientToGroupCommandImpl(httpClient);
  }

  @Override
  public UnassignClientFromGroupCommandStep1 newUnassignClientFromGroupCommand() {
    return new UnassignClientFromGroupCommandImpl(httpClient);
  }

  @Override
  public AssignClientToTenantCommandStep1 newAssignClientToTenantCommand() {
    return new AssignClientToTenantCommandImpl(httpClient);
  }

  @Override
  public UnassignClientFromTenantCommandStep1 newUnassignClientFromTenantCommand() {
    return new UnassignClientFromTenantCommandImpl(httpClient);
  }

  @Override
  public UnassignMappingRuleFromTenantCommandStep1 newUnassignMappingRuleFromTenantCommand() {
    return new UnassignMappingRuleFromTenantCommandImpl(httpClient);
  }

  @Override
  public CreateAuthorizationCommandStep1 newCreateAuthorizationCommand() {
    return new CreateAuthorizationCommandImpl(httpClient, jsonMapper);
  }

  @Override
  public AuthorizationGetRequest newAuthorizationGetRequest(final long authorizationKey) {
    return new AuthorizationGetRequestImpl(httpClient, authorizationKey);
  }

  @Override
  public AuthorizationsSearchRequest newAuthorizationSearchRequest() {
    return new AuthorizationsSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public DeleteAuthorizationCommandStep1 newDeleteAuthorizationCommand(
      final long authorizationKey) {
    return new DeleteAuthorizationCommandImpl(httpClient, authorizationKey);
  }

  @Override
  public UpdateAuthorizationCommandStep1 newUpdateAuthorizationCommand(
      final long authorizationKey) {
    return new UpdateAuthorizationCommandImpl(httpClient, jsonMapper, authorizationKey);
  }

  @Override
  public CreateBatchOperationCommandStep1 newCreateBatchOperationCommand() {
    return new CreateBatchOperationCommandStep1Impl(httpClient, jsonMapper) {};
  }

  @Override
  public BatchOperationGetRequest newBatchOperationGetRequest(final String batchOperationKey) {
    return new BatchOperationGetRequestImpl(httpClient, batchOperationKey);
  }

  @Override
  public BatchOperationSearchRequest newBatchOperationSearchRequest() {
    return new BatchOperationSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public CancelBatchOperationStep1 newCancelBatchOperationCommand(final String batchOperationKey) {
    return new CancelBatchOperationCommandImpl(httpClient, batchOperationKey);
  }

  @Override
  public SuspendBatchOperationStep1 newSuspendBatchOperationCommand(
      final String batchOperationKey) {
    return new SuspendBatchOperationCommandImpl(httpClient, batchOperationKey);
  }

  @Override
  public ResumeBatchOperationStep1 newResumeBatchOperationCommand(final String batchOperationKey) {
    return new ResumeBatchOperationCommandImpl(httpClient, batchOperationKey);
  }

  @Override
  public BatchOperationItemSearchRequest newBatchOperationItemsSearchRequest() {
    return new BatchOperationItemSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public AssignMappingRuleToGroupStep1 newAssignMappingRuleToGroupCommand() {
    return new AssignMappingRuleToGroupCommandImpl(httpClient);
  }

  @Override
  public UnassignMappingRuleFromGroupStep1 newUnassignMappingRuleFromGroupCommand() {
    return new UnassignMappingRuleFromGroupCommandImpl(httpClient);
  }

  @Override
  public GroupGetRequest newGroupGetRequest(final String groupId) {
    return new GroupGetRequestImpl(httpClient, groupId);
  }

  @Override
  public GroupsSearchRequest newGroupsSearchRequest() {
    return new GroupSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public UsersByGroupSearchRequest newUsersByGroupSearchRequest(final String groupId) {
    return new UsersByGroupSearchRequestImpl(httpClient, jsonMapper, groupId);
  }

  @Override
  public ProcessInstanceGetCallHierarchyRequest newProcessInstanceGetCallHierarchyRequest(
      final Long processInstanceKey) {
    return new ProcessInstanceGetCallHierarchyRequestImpl(httpClient, processInstanceKey);
  }

  @Override
  public MappingRulesByGroupSearchRequest newMappingRulesByGroupSearchRequest(
      final String groupId) {
    return new MappingRulesByGroupSearchRequestImpl(httpClient, jsonMapper, groupId);
  }

  @Override
  public MappingRulesByRoleSearchRequest newMappingRulesByRoleSearchRequest(final String roleId) {
    return new MappingRulesByRoleSearchRequestImpl(httpClient, jsonMapper, roleId);
  }

  @Override
  public MappingRulesByTenantSearchRequest newMappingRulesByTenantSearchRequest(
      final String tenantId) {
    return new MappingRulesByTenantSearchRequestImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public MappingRulesSearchRequest newMappingRulesSearchRequest() {
    return new MappingRulesSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public RolesByGroupSearchRequest newRolesByGroupSearchRequest(final String groupId) {
    return new RolesByGroupSearchRequestImpl(httpClient, jsonMapper, groupId);
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest newIncidentsByProcessInstanceSearchRequest(
      final long processInstanceKey) {
    return new IncidentsByProcessInstanceSearchRequestImpl(
        httpClient, jsonMapper, processInstanceKey);
  }

  @Override
  public GroupsByRoleSearchRequest newGroupsByRoleSearchRequest(final String roleId) {
    return new GroupsByRoleSearchRequestImpl(httpClient, jsonMapper, roleId);
  }

  @Override
  public GroupsByTenantSearchRequest newGroupsByTenantSearchRequest(final String tenantId) {
    return new GroupsByTenantSearchRequestImpl(httpClient, jsonMapper, tenantId);
  }

  @Override
  public JobSearchRequest newJobSearchRequest() {
    return new JobSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public MessageSubscriptionSearchRequest newMessageSubscriptionSearchRequest() {
    return new MessageSubscriptionSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest
      newCorrelatedMessageSubscriptionSearchRequest() {
    return new CorrelatedMessageSubscriptionSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public IncidentsByElementInstanceSearchRequest newIncidentsByElementInstanceSearchRequest(
      final long elementInstanceKey) {
    return new IncidentsByElementInstanceSearchRequestImpl(
        httpClient, jsonMapper, elementInstanceKey);
  }

  @Override
  public AuditLogGetRequest newAuditLogGetRequest(final String auditLogKey) {
    return new AuditLogGetRequestImpl(httpClient, auditLogKey);
  }

  @Override
  public AuditLogSearchRequest newAuditLogSearchRequest() {
    return new AuditLogSearchRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public UserTaskAuditLogSearchRequest newUserTaskAuditLogSearchRequest(final long userTaskKey) {
    return new UserTaskAuditLogSearchRequestImpl(httpClient, jsonMapper, userTaskKey);
  }

  @Override
  public ProcessDefinitionInstanceStatisticsRequest
      newProcessDefinitionInstanceStatisticsRequest() {
    return new ProcessDefinitionInstanceStatisticsRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest
      newProcessDefinitionInstanceVersionStatisticsRequest(final String processDefinitionId) {
    return new ProcessDefinitionInstanceVersionStatisticsRequestImpl(
        httpClient, jsonMapper, processDefinitionId);
  }

  @Override
  public EvaluateConditionalCommandStep1 newEvaluateConditionalCommand() {
    return new EvaluateConditionalCommandImpl(
        asyncStub, config, jsonMapper, credentialsProvider::shouldRetryRequest, httpClient);
  }

  @Override
  public IncidentProcessInstanceStatisticsByErrorRequest
      newIncidentProcessInstanceStatisticsByErrorRequest() {
    return new IncidentProcessInstanceStatisticsByErrorRequestImpl(httpClient, jsonMapper);
  }

  @Override
  public EvaluateExpressionCommandStep1 newEvaluateExpressionCommand() {
    return new EvaluateExpressionCommandImpl(config, httpClient, jsonMapper);
  }

  @Override
  public IncidentProcessInstanceStatisticsByDefinitionRequest
      newIncidentProcessInstanceStatisticsByDefinitionRequest(final int errorHashCode) {
    return new IncidentProcessInstanceStatisticsByDefinitionRequestImpl(
        httpClient, jsonMapper, errorHashCode);
  }

  private JobClient newJobClient() {
    return new JobClientImpl(
        asyncStub, httpClient, config, jsonMapper, credentialsProvider::shouldRetryRequest);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final long jobKey) {
    return jobClient.newCompleteCommand(jobKey);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final ActivatedJob job) {
    return newCompleteCommand(job.getKey());
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final long jobKey) {
    return jobClient.newFailCommand(jobKey);
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final ActivatedJob job) {
    return newFailCommand(job.getKey());
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final long jobKey) {
    return jobClient.newThrowErrorCommand(jobKey);
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final ActivatedJob job) {
    return newThrowErrorCommand(job.getKey());
  }

  @Override
  public ActivateJobsCommandStep1 newActivateJobsCommand() {
    return jobClient.newActivateJobsCommand();
  }

  @Override
  public StreamJobsCommandStep1 newStreamJobsCommand() {
    return new StreamJobsCommandImpl(
        asyncStub, jsonMapper, credentialsProvider::shouldRetryRequest, config);
  }
}
