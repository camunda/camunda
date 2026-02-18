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
package io.camunda.client.spring.actuator;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
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
import io.camunda.client.api.statistics.request.IncidentProcessInstanceStatisticsByDefinitionRequest;
import io.camunda.client.api.statistics.request.IncidentProcessInstanceStatisticsByErrorRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionElementStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionInstanceStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionInstanceVersionStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionMessageSubscriptionStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessInstanceElementStatisticsRequest;
import io.camunda.client.api.statistics.request.UsageMetricsStatisticsRequest;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.client.spring.actuator.CommandCaptor.CompleteJobCommandCaptor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class InvocationCamundaClient implements CamundaClient {
  private final List<CapturedCommand> capturedCommands = new ArrayList<>();
  private final JsonMapper jsonMapper;

  public InvocationCamundaClient(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public List<CapturedCommand> getCapturedCommands() {
    return new ArrayList<>(capturedCommands);
  }

  public void reset() {
    capturedCommands.clear();
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public StatusRequestStep1 newStatusRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CamundaClientConfiguration getConfiguration() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public void close() {}

  @Override
  public DeployProcessCommandStep1 newDeployCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeployResourceCommandStep1 newDeployResourceCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateProcessInstanceCommandStep1 newCreateInstanceCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ModifyProcessInstanceCommandStep1 newModifyProcessInstanceCommand(
      final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MigrateProcessInstanceCommandStep1 newMigrateProcessInstanceCommand(
      final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CancelProcessInstanceCommandStep1 newCancelInstanceCommand(final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteProcessInstanceCommandStep1 newDeleteInstanceCommand(final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public SetVariablesCommandStep1 newSetVariablesCommand(final long elementInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public EvaluateDecisionCommandStep1 newEvaluateDecisionCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public PublishMessageCommandStep1 newPublishMessageCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CorrelateMessageCommandStep1 newCorrelateMessageCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public BroadcastSignalCommandStep1 newBroadcastSignalCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ResolveIncidentCommandStep1 newResolveIncidentCommand(final long incidentKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ResolveProcessInstanceIncidentsCommandStep1 newResolveProcessInstanceIncidentsCommand(
      final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final long jobKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final ActivatedJob job) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(final long jobKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(final ActivatedJob job) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteResourceCommandStep1 newDeleteResourceCommand(final long resourceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CompleteUserTaskCommandStep1 newCompleteUserTaskCommand(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignUserTaskCommandStep1 newAssignUserTaskCommand(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateUserTaskCommandStep1 newUpdateUserTaskCommand(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignUserTaskCommandStep1 newUnassignUserTaskCommand(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateJobCommandStep1 newUpdateJobCommand(final long jobKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateJobCommandStep1 newUpdateJobCommand(final ActivatedJob job) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public PinClockCommandStep1 newPinClockCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ResetClockCommandStep1 newResetClockCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionGetRequest newProcessDefinitionGetRequest(
      final long processDefinitionKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionGetXmlRequest newProcessDefinitionGetXmlRequest(
      final long processDefinitionKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionGetFormRequest newProcessDefinitionGetFormRequest(
      final long processDefinitionKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionSearchRequest newProcessDefinitionSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionElementStatisticsRequest newProcessDefinitionElementStatisticsRequest(
      final long processDefinitionKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessInstanceElementStatisticsRequest newProcessInstanceElementStatisticsRequest(
      final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsRequest
      newProcessDefinitionMessageSubscriptionStatisticsRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UsageMetricsStatisticsRequest newUsageMetricsRequest(
      final OffsetDateTime startTime, final OffsetDateTime endTime) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessInstanceSequenceFlowsRequest newProcessInstanceSequenceFlowsRequest(
      final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessInstanceGetRequest newProcessInstanceGetRequest(final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessInstanceSearchRequest newProcessInstanceSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ElementInstanceSearchRequest newElementInstanceSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ElementInstanceGetRequest newElementInstanceGetRequest(final long elementInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ActivateAdHocSubProcessActivitiesCommandStep1 newActivateAdHocSubProcessActivitiesCommand(
      final String adHocSubProcessInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UserTaskSearchRequest newUserTaskSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionRequirementsSearchRequest newDecisionRequirementsSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionDefinitionSearchRequest newDecisionDefinitionSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionDefinitionGetRequest newDecisionDefinitionGetRequest(
      final long decisionDefinitionKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionDefinitionGetXmlRequest newDecisionDefinitionGetXmlRequest(
      final long decisionDefinitionKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionInstanceSearchRequest newDecisionInstanceSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionInstanceGetRequest newDecisionInstanceGetRequest(final String decisionInstanceId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public IncidentSearchRequest newIncidentSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public IncidentGetRequest newIncidentGetRequest(final long incidentKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateRoleCommandStep1 newCreateRoleCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public RoleGetRequest newRoleGetRequest(final String roleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public RolesSearchRequest newRolesSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateRoleCommandStep1 newUpdateRoleCommand(final String roleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignRoleToMappingRuleCommandStep1 newAssignRoleToMappingRuleCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteRoleCommandStep1 newDeleteRoleCommand(final String roleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignRoleToGroupCommandStep1 newAssignRoleToGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignRoleToClientCommandStep1 newAssignRoleToClientCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ClientsByRoleSearchRequest newClientsByRoleSearchRequest(final String roleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ClientsByGroupSearchRequest newClientsByGroupSearchRequest(final String groupId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ClientsByTenantSearchRequest newClientsByTenantSearchRequest(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignRoleToTenantCommandStep1 newAssignRoleToTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignRoleFromTenantCommandStep1 newUnassignRoleFromTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public RolesByTenantSearchRequest newRolesByTenantSearchRequest(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignRoleFromGroupCommandStep1 newUnassignRoleFromGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignRoleFromMappingRuleCommandStep1 newUnassignRoleFromMappingRuleCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignRoleFromClientCommandStep1 newUnassignRoleFromClientCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignRoleToUserCommandStep1 newAssignRoleToUserCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignRoleFromUserCommandStep1 newUnassignRoleFromUserCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UsersByRoleSearchRequest newUsersByRoleSearchRequest(final String roleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UsersByTenantSearchRequest newUsersByTenantSearchRequest(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UsersSearchRequest newUsersSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateGroupCommandStep1 newCreateGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateGroupCommandStep1 newUpdateGroupCommand(final String groupId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteGroupCommandStep1 newDeleteGroupCommand(final String groupId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteMappingRuleCommandStep1 newDeleteMappingRuleCommand(final String mappingRuleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MappingRuleGetRequest newMappingRuleGetRequest(final String mappingRuleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignUserToGroupCommandStep1 newAssignUserToGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignUserFromGroupCommandStep1 newUnassignUserFromGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateUserCommandStep1 newCreateUserCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteUserCommandStep1 newDeleteUserCommand(final String username) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateUserCommandStep1 newUpdateUserCommand(final String username) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UserGetRequest newUserGetRequest(final String username) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateMappingRuleCommandStep1 newCreateMappingRuleCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateMappingRuleCommandStep1 newUpdateMappingRuleCommand(final String mappingRuleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionRequirementsGetXmlRequest newDecisionRequirementsGetXmlRequest(
      final long decisionRequirementsKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DecisionRequirementsGetRequest newDecisionRequirementsGetRequest(
      final long decisionRequirementsKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UserTaskGetFormRequest newUserTaskGetFormRequest(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UserTaskGetRequest newUserTaskGetRequest(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public VariableSearchRequest newVariableSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public VariableGetRequest newVariableGetRequest(final long variableKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public GloballyScopedClusterVariableCreationCommandStep1
      newGloballyScopedClusterVariableCreateRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TenantScopedClusterVariableCreationCommandStep1
      newTenantScopedClusterVariableCreateRequest(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public GloballyScopedClusterVariableDeletionCommandStep1
      newGloballyScopedClusterVariableDeleteRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TenantScopedClusterVariableDeletionCommandStep1
      newTenantScopedClusterVariableDeleteRequest(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public GloballyScopedClusterVariableGetRequest newGloballyScopedClusterVariableGetRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TenantScopedClusterVariableGetRequest newTenantScopedClusterVariableGetRequest(
      final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ClusterVariableSearchRequest newClusterVariableSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UserTaskVariableSearchRequest newUserTaskVariableSearchRequest(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateDocumentCommandStep1 newCreateDocumentCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateDocumentBatchCommandStep1 newCreateDocumentBatchCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(final String documentId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DocumentContentGetRequest newDocumentContentGetRequest(
      final DocumentReferenceResponse documentReferenceResponse) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(final String documentId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(
      final DocumentReferenceResponse documentReferenceResponse) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(final String documentId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteDocumentCommandStep1 newDeleteDocumentCommand(
      final DocumentReferenceResponse documentReferenceResponse) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateTenantCommandStep1 newCreateTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateTenantCommandStep1 newUpdateTenantCommand(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TenantGetRequest newTenantGetRequest(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public TenantsSearchRequest newTenantsSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteTenantCommandStep1 newDeleteTenantCommand(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignMappingRuleToTenantCommandStep1 newAssignMappingRuleToTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignUserToTenantCommandStep1 newAssignUserToTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignUserFromTenantCommandStep1 newUnassignUserFromTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignGroupToTenantCommandStep1 newAssignGroupToTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignGroupFromTenantCommandStep1 newUnassignGroupFromTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignClientToGroupCommandStep1 newAssignClientToGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignClientFromGroupCommandStep1 newUnassignClientFromGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignClientToTenantCommandStep1 newAssignClientToTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignClientFromTenantCommandStep1 newUnassignClientFromTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignMappingRuleFromTenantCommandStep1 newUnassignMappingRuleFromTenantCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateAuthorizationCommandStep1 newCreateAuthorizationCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AuthorizationGetRequest newAuthorizationGetRequest(final long authorizationKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AuthorizationsSearchRequest newAuthorizationSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public DeleteAuthorizationCommandStep1 newDeleteAuthorizationCommand(
      final long authorizationKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UpdateAuthorizationCommandStep1 newUpdateAuthorizationCommand(
      final long authorizationKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CreateBatchOperationCommandStep1 newCreateBatchOperationCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public BatchOperationGetRequest newBatchOperationGetRequest(final String batchOperationKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public BatchOperationSearchRequest newBatchOperationSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CancelBatchOperationStep1 newCancelBatchOperationCommand(final String batchOperationKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public SuspendBatchOperationStep1 newSuspendBatchOperationCommand(
      final String batchOperationKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ResumeBatchOperationStep1 newResumeBatchOperationCommand(final String batchOperationKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public BatchOperationItemSearchRequest newBatchOperationItemsSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AssignMappingRuleToGroupStep1 newAssignMappingRuleToGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UnassignMappingRuleFromGroupStep1 newUnassignMappingRuleFromGroupCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public GroupGetRequest newGroupGetRequest(final String groupId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public GroupsSearchRequest newGroupsSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UsersByGroupSearchRequest newUsersByGroupSearchRequest(final String groupId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessInstanceGetCallHierarchyRequest newProcessInstanceGetCallHierarchyRequest(
      final Long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MappingRulesByGroupSearchRequest newMappingRulesByGroupSearchRequest(
      final String groupId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MappingRulesByRoleSearchRequest newMappingRulesByRoleSearchRequest(final String roleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MappingRulesByTenantSearchRequest newMappingRulesByTenantSearchRequest(
      final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MappingRulesSearchRequest newMappingRulesSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public RolesByGroupSearchRequest newRolesByGroupSearchRequest(final String groupId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public IncidentsByProcessInstanceSearchRequest newIncidentsByProcessInstanceSearchRequest(
      final long processInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public GroupsByRoleSearchRequest newGroupsByRoleSearchRequest(final String roleId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public GroupsByTenantSearchRequest newGroupsByTenantSearchRequest(final String tenantId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public JobSearchRequest newJobSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public MessageSubscriptionSearchRequest newMessageSubscriptionSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CorrelatedMessageSubscriptionSearchRequest
      newCorrelatedMessageSubscriptionSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public IncidentsByElementInstanceSearchRequest newIncidentsByElementInstanceSearchRequest(
      final long elementInstanceKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AuditLogGetRequest newAuditLogGetRequest(final String auditLogKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public AuditLogSearchRequest newAuditLogSearchRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public UserTaskAuditLogSearchRequest newUserTaskAuditLogSearchRequest(final long userTaskKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionInstanceStatisticsRequest
      newProcessDefinitionInstanceStatisticsRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ProcessDefinitionInstanceVersionStatisticsRequest
      newProcessDefinitionInstanceVersionStatisticsRequest(final String processDefinitionId) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public EvaluateConditionalCommandStep1 newEvaluateConditionalCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public IncidentProcessInstanceStatisticsByErrorRequest
      newIncidentProcessInstanceStatisticsByErrorRequest() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public EvaluateExpressionCommandStep1 newEvaluateExpressionCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public IncidentProcessInstanceStatisticsByDefinitionRequest
      newIncidentProcessInstanceStatisticsByDefinitionRequest(final int errorHashCode) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final long jobKey) {
    return new CompleteJobCommandCaptor(jsonMapper, jobKey, capturedCommands::add);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final ActivatedJob job) {
    return newCompleteCommand(job.getKey());
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final long jobKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final ActivatedJob job) {
    return newFailCommand(job.getKey());
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final long jobKey) {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(final ActivatedJob job) {
    return newThrowErrorCommand(job.getKey());
  }

  @Override
  public ActivateJobsCommandStep1 newActivateJobsCommand() {
    throw new IllegalStateException("Not implemented");
  }

  @Override
  public StreamJobsCommandStep1 newStreamJobsCommand() {
    throw new IllegalStateException("Not implemented");
  }
}
