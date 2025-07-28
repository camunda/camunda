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
package io.camunda.client;

import io.camunda.client.api.ExperimentalApi;
import io.camunda.client.api.command.ActivateAdHocSubProcessActivitiesCommandStep1;
import io.camunda.client.api.command.AssignClientToGroupCommandStep1;
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
import io.camunda.client.api.command.ClockPinCommandStep1;
import io.camunda.client.api.command.ClockResetCommandStep1;
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
import io.camunda.client.api.command.DeleteResourceCommandStep1;
import io.camunda.client.api.command.DeleteRoleCommandStep1;
import io.camunda.client.api.command.DeleteTenantCommandStep1;
import io.camunda.client.api.command.DeleteUserCommandStep1;
import io.camunda.client.api.command.DeployProcessCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.RemoveUserFromTenantCommandStep1;
import io.camunda.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.client.api.command.ResumeBatchOperationStep1;
import io.camunda.client.api.command.SetVariablesCommandStep1;
import io.camunda.client.api.command.SuspendBatchOperationStep1;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.command.UnassignClientFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignGroupFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignMappingRuleFromGroupStep1;
import io.camunda.client.api.command.UnassignRoleFromClientCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromMappingRuleCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromTenantCommandStep1;
import io.camunda.client.api.command.UnassignRoleFromUserCommandStep1;
import io.camunda.client.api.command.UnassignUserFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignUserTaskCommandStep1;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1;
import io.camunda.client.api.command.UpdateGroupCommandStep1;
import io.camunda.client.api.command.UpdateJobCommandStep1;
import io.camunda.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.client.api.command.UpdateRoleCommandStep1;
import io.camunda.client.api.command.UpdateTenantCommandStep1;
import io.camunda.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.client.api.command.UpdateUserCommandStep1;
import io.camunda.client.api.command.UpdateUserTaskCommandStep1;
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
import io.camunda.client.api.fetch.GroupGetRequest;
import io.camunda.client.api.fetch.IncidentGetRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetFormRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetRequest;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.client.api.fetch.ProcessInstanceGetCallHierarchyRequest;
import io.camunda.client.api.fetch.ProcessInstanceGetRequest;
import io.camunda.client.api.fetch.RoleGetRequest;
import io.camunda.client.api.fetch.RolesSearchRequest;
import io.camunda.client.api.fetch.TenantGetRequest;
import io.camunda.client.api.fetch.UserGetRequest;
import io.camunda.client.api.fetch.UserTaskGetFormRequest;
import io.camunda.client.api.fetch.UserTaskGetRequest;
import io.camunda.client.api.fetch.VariableGetRequest;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.search.request.BatchOperationItemSearchRequest;
import io.camunda.client.api.search.request.BatchOperationSearchRequest;
import io.camunda.client.api.search.request.ClientsByGroupSearchRequest;
import io.camunda.client.api.search.request.ClientsByRoleSearchRequest;
import io.camunda.client.api.search.request.ClientsByTenantSearchRequest;
import io.camunda.client.api.search.request.DecisionDefinitionSearchRequest;
import io.camunda.client.api.search.request.DecisionInstanceSearchRequest;
import io.camunda.client.api.search.request.DecisionRequirementsSearchRequest;
import io.camunda.client.api.search.request.ElementInstanceSearchRequest;
import io.camunda.client.api.search.request.GroupsByRoleSearchRequest;
import io.camunda.client.api.search.request.GroupsByTenantSearchRequest;
import io.camunda.client.api.search.request.GroupsSearchRequest;
import io.camunda.client.api.search.request.IncidentSearchRequest;
import io.camunda.client.api.search.request.IncidentsByProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.JobSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByGroupSearchRequest;
import io.camunda.client.api.search.request.MappingRulesByRoleSearchRequest;
import io.camunda.client.api.search.request.MessageSubscriptionSearchRequest;
import io.camunda.client.api.search.request.ProcessDefinitionSearchRequest;
import io.camunda.client.api.search.request.ProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.ProcessInstanceSequenceFlowsRequest;
import io.camunda.client.api.search.request.RolesByGroupSearchRequest;
import io.camunda.client.api.search.request.RolesByTenantSearchRequest;
import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.client.api.search.request.UserTaskSearchRequest;
import io.camunda.client.api.search.request.UserTaskVariableSearchRequest;
import io.camunda.client.api.search.request.UsersByGroupSearchRequest;
import io.camunda.client.api.search.request.UsersByRoleSearchRequest;
import io.camunda.client.api.search.request.UsersByTenantSearchRequest;
import io.camunda.client.api.search.request.UsersSearchRequest;
import io.camunda.client.api.search.request.VariableSearchRequest;
import io.camunda.client.api.statistics.request.ProcessDefinitionElementStatisticsRequest;
import io.camunda.client.api.statistics.request.ProcessInstanceElementStatisticsRequest;
import io.camunda.client.api.statistics.request.UsageMetricsStatisticsRequest;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientCloudBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import java.time.OffsetDateTime;

/** The client to communicate with a Camunda broker/cluster. */
public interface CamundaClient extends AutoCloseable, JobClient {

  /**
   * @return a new Camunda client with default configuration values. In order to customize
   *     configuration, use the methods {@link #newClientBuilder()} or {@link
   *     #newClient(CamundaClientConfiguration)}. See {@link CamundaClientBuilder} for the
   *     configuration options and default values.
   */
  static CamundaClient newClient() {
    return newClientBuilder().build();
  }

  /**
   * @return a new {@link CamundaClient} using the provided configuration.
   */
  static CamundaClient newClient(final CamundaClientConfiguration configuration) {
    return new CamundaClientImpl(configuration);
  }

  /**
   * @return a builder to configure and create a new {@link CamundaClient}.
   */
  static CamundaClientBuilder newClientBuilder() {
    return new CamundaClientBuilderImpl();
  }

  /**
   * @return a builder with convenient methods to connect to the Camunda Cloud cluster.
   */
  static CamundaClientCloudBuilderStep1 newCloudClientBuilder() {
    return new CamundaClientCloudBuilderImpl();
  }

  /**
   * Request the current cluster topology. Can be used to inspect which brokers are available at
   * which endpoint and which broker is the leader of which partition.
   *
   * <pre>
   * List&#60;BrokerInfo&#62; brokers = camundaClient
   *  .newTopologyRequest()
   *  .send()
   *  .join()
   *  .getBrokers();
   *
   *  InetSocketAddress address = broker.getSocketAddress();
   *
   *  List&#60;PartitionInfo&#62; partitions = broker.getPartitions();
   * </pre>
   *
   * @return the request where you must call {@code send()}
   */
  TopologyRequestStep1 newTopologyRequest();

  /**
   * @return the client's configuration
   */
  CamundaClientConfiguration getConfiguration();

  @Override
  void close();

  /**
   * Command to deploy new processes.
   *
   * <pre>
   * camundaClient
   *  .newDeployCommand()
   *  .addResourceFile("~/wf/process1.bpmn")
   *  .addResourceFile("~/wf/process2.bpmn")
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   * @deprecated since 8 for removal with 8.1, replaced by {@link
   *     CamundaClient#newDeployResourceCommand()}
   */
  DeployProcessCommandStep1 newDeployCommand();

  /**
   * Command to deploy new resources, i.e. BPMN process models and DMN decision models.
   *
   * <pre>
   * camundaClient
   *  .newDeployCommand()
   *  .addResourceFile("~/wf/process1.bpmn")
   *  .addResourceFile("~/wf/process2.bpmn")
   *  .addResourceFile("~/dmn/decision.dmn")
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  DeployResourceCommandStep1 newDeployResourceCommand();

  /**
   * Command to create/start a new instance of a process.
   *
   * <pre>
   * camundaClient
   *  .newCreateInstanceCommand()
   *  .bpmnProcessId("my-process")
   *  .latestVersion()
   *  .variables(json)
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  CreateProcessInstanceCommandStep1 newCreateInstanceCommand();

  /**
   * Command to modify a process instance.
   *
   * <pre>
   *   camundaClient
   *    .newModifyProcessInstanceCommand(processInstanceKey)
   *    .activateElement("element1")
   *    .and()
   *    .activateElement("element2")
   *    .withVariables(globalScopedVariables)
   *    .withVariables(localScopedVariables, "element2")
   *    .and()
   *    .terminateElement("element3")
   *    .send();
   * </pre>
   *
   * @param processInstanceKey the key which identifies the corresponding process instance
   * @return a builder for the command
   */
  ModifyProcessInstanceCommandStep1 newModifyProcessInstanceCommand(long processInstanceKey);

  /**
   * Command to migrate a process instance to a different process definition.
   *
   * <p>The migration command contains a migration plan. Migration plan contains
   * targetProcessDefinitionKey to indicate which process definition to use for the migration.
   * Mapping instructions for the migration describe how to map elements from the source process
   * definition to the target process definition.
   *
   * <p>For example, let's consider we want to migrate process instance with key {@code 1}, target
   * process definition key {@code 2}, a source process definition with a service task with id
   * {@code "task1"} and the target process definition with a service task with id {@code "task2"}.
   * The migration command could be:
   *
   * <pre>{@code
   * {
   *  "processInstanceKey": 1,
   *  "migrationPlan": {
   *   "targetProcessDefinitionKey": 2,
   *   "mappingInstructions": [
   *    {
   *     "sourceElementId": "task1",
   *     "targetElementId": "task2"
   *    }
   *   ]
   *  }
   * }
   * }</pre>
   *
   * <pre>
   *
   * camundaClient
   *  .newMigrateProcessInstanceCommand(1L)
   *  .migrationPlan(2L)
   *  .addMappingInstruction("element1", "element2")
   *  .addMappingInstruction("element3", "element4")
   *  .send();
   * </pre>
   *
   * <pre>
   * final MigrationPlan migrationPlan =
   *         MigrationPlan.newBuilder()
   *             .withTargetProcessDefinitionKey(2L)
   *             .addMappingInstruction("element1", "element2")
   *             .addMappingInstruction("element3", "element4")
   *             .build();
   * camundaClient
   *  .newMigrateProcessInstanceCommand(1L)
   *  .migrationPlan(migrationPlan)
   *  .send();
   * </pre>
   *
   * @param processInstanceKey the key which refers to the process instance to migrate
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/14907")
  MigrateProcessInstanceCommandStep1 newMigrateProcessInstanceCommand(long processInstanceKey);

  /**
   * Command to cancel a process instance.
   *
   * <pre>
   * camundaClient
   *  .newCancelInstanceCommand(processInstanceKey)
   *  .send();
   * </pre>
   *
   * @param processInstanceKey the key which identifies the corresponding process instance
   * @return a builder for the command
   */
  CancelProcessInstanceCommandStep1 newCancelInstanceCommand(long processInstanceKey);

  /**
   * Command to set and/or update the variables of a given flow element (e.g. process instance,
   * task, etc.)
   *
   * <pre>
   * camundaClient
   *  .newSetVariablesCommand(elementInstanceKey)
   *  .variables(json)
   *  .send();
   * </pre>
   *
   * @param elementInstanceKey the key of the element instance to set/update the variables for
   * @return a builder for the command
   */
  SetVariablesCommandStep1 newSetVariablesCommand(long elementInstanceKey);

  /**
   * Command to evaluate a decision.
   *
   * <pre>
   * camundaClient
   *  .newEvaluateDecisionCommand()
   *  .decisionKey("my-decision")
   *  .variables(json)
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  EvaluateDecisionCommandStep1 newEvaluateDecisionCommand();

  /**
   * Command to publish a message which can be correlated to a process instance.
   *
   * <pre>
   * camundaClient
   *  .newPublishMessageCommand()
   *  .messageName("order canceled")
   *  .correlationKey(orderId)
   *  .variables(json)
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  PublishMessageCommandStep1 newPublishMessageCommand();

  /**
   * Command to correlate a message and wait for it to be correlated against a process instance.
   *
   * <pre>
   * camundaClient
   *  .newCorrelateMessageCommand()
   *  .messageName("order canceled")
   *  .correlationKey(orderId)
   *  .variables(json)
   *  .tenantId("tenant")
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  CorrelateMessageCommandStep1 newCorrelateMessageCommand();

  /**
   * Command to broadcast a signal.
   *
   * <pre>
   * camundaClient
   *  .newBroadcastSignalCommand()
   *  .signalName("signal")
   *  .variables(json)
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  BroadcastSignalCommandStep1 newBroadcastSignalCommand();

  /**
   * Command to resolve an existing incident.
   *
   * <pre>
   * camundaClient
   *  .newResolveIncidentCommand(incidentKey)
   *  .send();
   * </pre>
   *
   * @param incidentKey the key of the corresponding incident
   * @return the builder for the command
   */
  ResolveIncidentCommandStep1 newResolveIncidentCommand(long incidentKey);

  /**
   * Command to update the retries of a job.
   *
   * <pre>
   * long jobKey = ..;
   *
   * camundaClient
   *  .newUpdateRetriesCommand(jobKey)
   *  .retries(3)
   *  .send();
   * </pre>
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * worker. This will not close a related incident, which still has to be marked as resolved with
   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
   *
   * @param jobKey the key of the job to update
   * @return a builder for the command
   */
  UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(long jobKey);

  /**
   * Command to update the retries of a job.
   *
   * <pre>
   * ActivatedJob job= ..;
   *
   * camundaClient
   *  .newUpdateRetriesCommand(job)
   *  .retries(3)
   *  .send();
   * </pre>
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * worker. This will not close a related incident, which still has to be marked as resolved with
   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
   *
   * @param job the activated job
   * @return a builder for the command
   */
  UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(ActivatedJob job);

  /**
   * Command to update the timeout of a job.
   *
   * <pre>
   * long jobKey = ..;
   *
   * camundaClient
   *  .newUpdateTimeoutCommand(jobKey)
   *  .timeout(100)
   *  .send();
   * </pre>
   *
   * <p>Timeout value in millis is used to calculate a new job deadline. This will happen when the
   * command to update the timeline is processed. The timeout value will be added to the current
   * time then.
   *
   * @param jobKey the key of the job to update
   * @return a builder for the command
   */
  UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(long jobKey);

  /**
   * Command to update the timeout of a job.
   *
   * <pre>
   * ActivatedJob job= ..;
   *
   * camundaClient
   *  .newUpdateTimeoutCommand(job)
   *  .timeout(100)
   *  .send();
   * </pre>
   *
   * <p>Timeout value in millis is used to calculate a new job deadline. This will happen when the
   * command to update the timeline is processed. The timeout value will be added to the current
   * time then.
   *
   * @param job the activated job
   * @return a builder for the command
   */
  UpdateTimeoutJobCommandStep1 newUpdateTimeoutCommand(ActivatedJob job);

  /**
   * Registers a new job worker for jobs of a given type.
   *
   * <p>After registration, the broker activates available jobs and assigns them to this worker. It
   * then publishes them to the client. The given worker is called for every received job, works on
   * them and eventually completes them.
   *
   * <pre>
   * JobWorker worker = camundaClient
   *  .newWorker()
   *  .jobType("payment")
   *  .handler(paymentHandler)
   *  .open();
   *
   * ...
   * worker.close();
   * </pre>
   *
   * Example JobHandler implementation:
   *
   * <pre>
   * public final class PaymentHandler implements JobHandler
   * {
   *   &#64;Override
   *   public void handle(JobClient client, JobEvent jobEvent)
   *   {
   *     String json = jobEvent.getVariables();
   *     // modify variables
   *
   *     client
   *      .newCompleteCommand()
   *      .event(jobEvent)
   *      .variables(json)
   *      .send();
   *   }
   * };
   * </pre>
   *
   * @return a builder for the worker registration
   */
  JobWorkerBuilderStep1 newWorker();

  /**
   * Command to delete a resource.
   *
   * <pre>
   * camundaClient
   *  .newDeleteResourceCommand(resourceKey)
   *  .send();
   * </pre>
   *
   * @param resourceKey the key of the resource
   * @return the builder for the command
   */
  DeleteResourceCommandStep1 newDeleteResourceCommand(long resourceKey);

  /**
   * Command to complete a user task.
   *
   * <pre>
   * long userTaskKey = ..;
   *
   * camundaClient
   *  .newUserTaskCompleteCommand(userTaskKey)
   *  .variables(map)
   *  .send();
   * </pre>
   *
   * <p>If the user task is linked to a process instance then this command will complete the related
   * activity and continue the flow.
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   * <br>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the command
   */
  CompleteUserTaskCommandStep1 newUserTaskCompleteCommand(long userTaskKey);

  /**
   * Command to assign a user task.
   *
   * <pre>
   * long userTaskKey = ..;
   *
   * camundaClient
   *  .newUserTaskAssignCommand(userTaskKey)
   *  .assignee(newAssignee)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   * <br>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the command
   */
  AssignUserTaskCommandStep1 newUserTaskAssignCommand(long userTaskKey);

  /**
   * Command to update a user task.
   *
   * <pre>
   * long userTaskKey = ..;
   *
   * camundaClient
   *  .newUserTaskUpdateCommand(userTaskKey)
   *  .candidateGroups(newCandidateGroups)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   * <br>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the command
   */
  UpdateUserTaskCommandStep1 newUserTaskUpdateCommand(long userTaskKey);

  /**
   * Command to unassign a user task.
   *
   * <pre>
   * long userTaskKey = ..;
   *
   * camundaClient
   *  .newUserTaskUnassignCommand(userTaskKey)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   * <br>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the command
   */
  UnassignUserTaskCommandStep1 newUserTaskUnassignCommand(long userTaskKey);

  /**
   * Command to update the retries and/or the timeout of a job.
   *
   * <pre>
   * JobChangeset changeset= ..;
   *
   * camundaClient
   *  .newUpdateCommand(jobKey)
   *  .update(changeset)
   *  .send();
   * </pre>
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * worker. This will not close a related incident, which still has to be marked as resolved with
   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
   *
   * <p>Timeout value in millis is used to calculate a new job deadline. This will happen when the
   * command to update the timeline is processed. The timeout value will be added to the current
   * time then.
   *
   * @param jobKey the key of the job to update
   * @return a builder for the command
   */
  UpdateJobCommandStep1 newUpdateJobCommand(long jobKey);

  /**
   * Command to update the retries and/or the timeout of a job.
   *
   * <pre>
   * ActivatedJob job= ..;
   * JobChangeset changeset= ..;
   *
   * camundaClient
   *  .newUpdateCommand(job)
   *  .update(changeset)
   *  .send();
   * </pre>
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * worker. This will not close a related incident, which still has to be marked as resolved with
   * {@link #newResolveIncidentCommand newResolveIncidentCommand(long incidentKey)} .
   *
   * <p>Timeout value in millis is used to calculate a new job deadline. This will happen when the
   * command to update the timeline is processed. The timeout value will be added to the current
   * time then.
   *
   * @param job the activated job
   * @return a builder for the command
   */
  UpdateJobCommandStep1 newUpdateJobCommand(ActivatedJob job);

  /**
   * Command to pin the Zeebe engine's internal clock to a specific time.
   *
   * <p>This method initiates a command to pin the clock to a specified time. You can specify the
   * time using either an epoch timestamp in milliseconds or an {@link java.time.Instant} object.
   *
   * <p>Once pinned, the clock will remain at the specified time and will not advance until another
   * <code>pin</code> or <code>reset</code> command is issued. This is useful for scenarios where
   * you need to simulate process execution at a specific point in time.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * final long pinnedTime = 1742461285000L; // Thu, Mar 20, 2025 09:01:25 GMT+0000
   * camundaClient
   *  .newClockPinCommand()
   *  .time(pinnedTime)
   *  .send();
   *
   * final Instant futureInstant = Instant.now().plus(Duration.ofDays(7));
   * camundaClient
   *  .newClockPinCommand()
   *  .time(futureInstant)
   *  .send();
   * }</pre>
   *
   * <p>The command is marked as <strong>experimental</strong> and may undergo changes or
   * improvements in future releases.
   *
   * @return a builder for the command that allows setting either a timestamp or an instant
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/21647")
  ClockPinCommandStep1 newClockPinCommand();

  /**
   * Command to reset the Zeebe engine's internal clock to the system time.
   *
   * <p>This command allows you to reset the clock to the current system time, effectively undoing
   * any previous <code>pin</code> command that may have set the clock to a specific, static time.
   *
   * <pre>{@code
   * camundaClient
   *  .newClockResetCommand()
   *  .send();
   * }</pre>
   *
   * <p>The command is marked as <strong>experimental</strong> and may undergo changes or
   * improvements in future releases.
   *
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/21647")
  ClockResetCommandStep1 newClockResetCommand();

  /**
   * Gets a process definition by key.
   *
   * <pre>
   *   long processDefinitionKey = ...;
   *
   *   camundaClient
   *   .newProcessDefinitionGetRequest(processDefinitionKey)
   *   .send();
   *   </pre>
   *
   * @param processDefinitionKey the key of the process definition
   * @return a builder for the request to get a process definition
   */
  ProcessDefinitionGetRequest newProcessDefinitionGetRequest(long processDefinitionKey);

  /*
   * Retrieves the XML representation of a process definition.
   *
   * <pre>
   * long processDefinitionKey = ...;
   *
   * camundaClient
   *  .newProcessDefinitionGetXmlRequest(processDefinitionKey)
   *  .send();
   * </pre>
   *
   * @param decisionDefinitionKey the key of the process definition
   * @return a builder for the request to get the XML of a process definition
   */
  ProcessDefinitionGetXmlRequest newProcessDefinitionGetXmlRequest(long processDefinitionKey);

  /*
   * Retrieves the Form of a process definition.
   *
   * <pre>
   * long processDefinitionKey = ...;
   *
   * camundaClient
   *  .newProcessDefinitionGetFormRequest(processDefinitionKey)
   *  .send();
   * </pre>
   *
   * @param processDefinitionKey the key of the process definition
   * @return a builder for the request to get the Form of process definition
   */
  ProcessDefinitionGetFormRequest newProcessDefinitionGetFormRequest(long processDefinitionKey);

  /**
   * Executes a search request to query process definitions.
   *
   * <pre>
   * long processDefinitionKey = ...;
   *
   * camundaClient
   *  .newProcessDefinitionSearchRequest()
   *  .filter((f) -> f.processDefinitionKey(processDefinitionKey))
   *  .sort((s) -> s.name().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the process definition search request
   */
  ProcessDefinitionSearchRequest newProcessDefinitionSearchRequest();

  /**
   * Executes a search request to query process definition element statistics.
   *
   * <pre>
   * long processDefinitionKey = ...;
   *
   * camundaClient
   *  .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
   *  .filter((f) -> f.processInstanceKey(processInstanceKey))
   *  .send();
   * </pre>
   *
   * @return a builder for the process definition statistics request
   */
  ProcessDefinitionElementStatisticsRequest newProcessDefinitionElementStatisticsRequest(
      final long processDefinitionKey);

  /**
   * Executes a search request to query process instance element statistics.
   *
   * <pre>
   * long processInstanceKey = ...;
   *
   * camundaClient
   *  .newProcessInstanceElementStatisticsRequest(processInstanceKey)
   *  .send();
   * </pre>
   *
   * @return a builder for the process instance statistics request
   */
  ProcessInstanceElementStatisticsRequest newProcessInstanceElementStatisticsRequest(
      final long processInstanceKey);

  /**
   * Executes a search request to query usage metrics statistics.
   *
   * <pre>
   * camundaClient
   *  .newUsageMetricsRequest(OffsetDateTime.now().minusYears(1), OffsetDateTime.now())
   *  .withTenants(true)
   *  .send();
   * </pre>
   *
   * @return a builder for the usage metrics statistics request
   */
  UsageMetricsStatisticsRequest newUsageMetricsRequest(
      final OffsetDateTime startTime, final OffsetDateTime endTime);

  /**
   * Executes a search request to query process instance sequence flows.
   *
   * <pre>
   * long processInstanceKey = ...;
   *
   * camundaClient
   *  .newProcessInstanceSequenceFlowsRequest(processInstanceKey)
   *  .send();
   * </pre>
   *
   * @return a builder for the process instance sequence flows request
   */
  ProcessInstanceSequenceFlowsRequest newProcessInstanceSequenceFlowsRequest(
      final long processInstanceKey);

  /**
   * Retrieves a process instance by key.
   *
   * <pre>
   * long processInstanceKey = ...;
   *
   * camundaClient
   *  .newProcessInstanceGetRequest(processInstanceKey)
   *  .send();
   * </pre>
   *
   * @return a builder for the request to get a process instance
   */
  ProcessInstanceGetRequest newProcessInstanceGetRequest(long processInstanceKey);

  /**
   * Executes a search request to query process instances.
   *
   * <pre>
   * long processInstanceKey = ...;
   *
   * camundaClient
   *  .newProcessInstanceSearchRequest()
   *  .filter((f) -> f.processInstanceKeys(processInstanceKey))
   *  .sort((s) -> s.startDate().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the process instance search request
   */
  ProcessInstanceSearchRequest newProcessInstanceSearchRequest();

  /**
   * Executes a search request to query element instances.
   *
   * <pre>
   * long elementInstanceKey = ...;
   *
   * camundaClient
   *  .newElementInstanceSearchRequest()
   *  .filter((f) -> f.processInstanceKeys(processInstanceKey))
   *  .sort((s) -> s.elementName().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the process instance search request
   */
  ElementInstanceSearchRequest newElementInstanceSearchRequest();

  /**
   * Gets a element instance by key.
   *
   * <pre>
   *   long elementInstanceKey = ...;
   *
   *   camundaClient
   *   .newElementInstanceGetRequest(elementInstanceKey)
   *   .send();
   *   </pre>
   *
   * @param elementInstanceKey the key of the element instance
   * @return a builder for the request to get a element instance
   */
  ElementInstanceGetRequest newElementInstanceGetRequest(long elementInstanceKey);

  /**
   * Command to activate activities within an activated ad-hoc sub-process.
   *
   * <pre>
   *   camundaClient
   *    .newActivateAdHocSubProcessActivitiesCommand(adHocSubProcessInstanceKey)
   *    .activateElement("A")
   *    .activateElements("B", "C")
   *    .activateElements(Arrays.asList("D", "E"))
   *    .send();
   * </pre>
   *
   * @param adHocSubProcessInstanceKey the key which identifies the corresponding ad-hoc sub-process
   *     instance
   * @return a builder for the command
   */
  ActivateAdHocSubProcessActivitiesCommandStep1 newActivateAdHocSubProcessActivitiesCommand(
      String adHocSubProcessInstanceKey);

  /**
   * Executes a search request to query user tasks.
   *
   * <pre>
   * camundaClient
   *  .newUserTaskSearchRequest()
   *  .filter((f) -> f.userTaskKey(userTaskKey))
   *  .sort((s) -> s.creationDate().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the user task search request
   */
  UserTaskSearchRequest newUserTaskSearchRequest();

  /**
   * Executes a search request to query Decision Requirements.
   *
   * <pre>
   *   camundaClient
   *   .newDecisionRequirementsSearchRequest()
   *   .filter((f) -> f.decisionRequirementsKey(decisionRequirementsKey))
   *   .sort((s) -> s.version().asc())
   *   .page((p) -> p.limit(100))
   *   .send();
   *   </pre>
   *
   * @return a builder for the decision requirements search request
   */
  DecisionRequirementsSearchRequest newDecisionRequirementsSearchRequest();

  /*
   * Executes a search request to query decision definitions.
   *
   * <pre>
   * long decisionDefinitionKey = ...;
   *
   * camundaClient
   *  .newDecisionDefinitionQuery()
   *  .filter((f) -> f.decisionKey(decisionDefinitionKey))
   *  .sort((s) -> s.dmnDecisionName().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the decision definition search request
   */
  DecisionDefinitionSearchRequest newDecisionDefinitionSearchRequest();

  /**
   * Gets a decision definition by key.
   *
   * <pre>
   *   long decisionDefinitionKey = ...;
   *
   *   camundaClient
   *   .newDecisionDefinitionGetRequest(decisionDefinitionKey)
   *   .send();
   *   </pre>
   *
   * @param decisionDefinitionKey the key of the decision definition
   * @return a builder for the request to get a decision definition
   */
  DecisionDefinitionGetRequest newDecisionDefinitionGetRequest(long decisionDefinitionKey);

  /*
   * Retrieves the XML representation of a decision definition.
   *
   * <pre>
   * long decisionDefinitionKey = ...;
   *
   * camundaClient
   *  .newDecisionDefinitionGetXmlRequest(decisionDefinitionKey)
   *  .send();
   * </pre>
   *
   * @param decisionDefinitionKey the key of the decision definition
   * @return a builder for the request to get the XML of a decision definition
   */
  DecisionDefinitionGetXmlRequest newDecisionDefinitionGetXmlRequest(long decisionDefinitionKey);

  /**
   * Executes a search request to query decision instances.
   *
   * <pre>
   * long decisionInstanceKey = ...;
   *
   * camundaClient
   *  .newDecisionInstanceSearchRequest()
   *  .filter((f) -> f.decisionInstanceKey(decisionInstanceKey))
   *  .sort((s) -> s.decisionInstanceKey().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the decision instance search request
   */
  DecisionInstanceSearchRequest newDecisionInstanceSearchRequest();

  /**
   * Retrieves a decision instance by id.
   *
   * <pre>
   * String decisionInstanceId = ...;
   *
   * camundaClient
   * .newDecisionInstanceGetQuery(decisionInstanceId)
   * .send();
   * </pre>
   *
   * @param decisionInstanceId the id of the decision instance to fetch
   * @return a builder for the request to get a decision instance
   */
  DecisionInstanceGetRequest newDecisionInstanceGetRequest(String decisionInstanceId);

  /*
   * Executes a search request to query decision definitions.
   *
   * <pre>
   * long decisionDefinitionKey = ...;
   *
   * camundaClient
   *  .newIncidentSearchRequest()
   *  .filter((f) -> f.processInstanceKey(processInstanceKey))
   *  .sort((s) -> s.processDefinitionKey().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the incident search request
   */
  IncidentSearchRequest newIncidentSearchRequest();

  /**
   * Gets an incident by key.
   *
   * <pre>
   *   long incidentKey = ...;
   *
   *   camundaClient
   *   .newIncidentGetRequest(incidentKey)
   *   .send();
   *   </pre>
   *
   * @param incidentKey the key of the incident
   * @return a builder for the request to get an incident
   */
  IncidentGetRequest newIncidentGetRequest(long incidentKey);

  /**
   * Command to create a role.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newRoleCreateCommand()
   *  .roleId("roleId")
   *  .name(name)
   *  .description("description")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  CreateRoleCommandStep1 newCreateRoleCommand();

  /**
   * Request to get a role by role ID.
   *
   * <pre>
   *
   * camundaClient
   *  .newRoleGetRequest("roleId")
   *  .send();
   * </pre>
   *
   * @param roleId the ID of the role
   * @return a builder for the request to get a role
   */
  RoleGetRequest newRoleGetRequest(String roleId);

  /**
   * Executes a search request to query roles.
   *
   * <pre>
   *
   * camundaClient
   *  .newRolesSearchRequest()
   *  .filter((f) -> f.name("roleName"))
   *  .sort((s) -> s.name().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the roles search request
   */
  RolesSearchRequest newRolesSearchRequest();

  /**
   * Command to update a role.
   *
   * <pre>
   * camundaClient
   *  .newUpdateRoleCommand("roleId")
   *  .name("name")
   *  .description("description")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  UpdateRoleCommandStep1 newUpdateRoleCommand(String roleId);

  /**
   * Command to assign a role to a mapping rule.
   *
   * <pre>
   *
   * camundaClient
   *  .newAssignRoleToMappingRuleCommand()
   *  .roleId("roleId")
   *  .mappingRuleId("mappingRuleId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder to configure and send the assign role to mapping rule command
   */
  AssignRoleToMappingRuleCommandStep1 newAssignRoleToMappingRuleCommand();

  /**
   * Command to delete a role by role ID.
   *
   * <pre>
   *
   * camundaClient
   *  .newDeleteRoleCommand("roleId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  DeleteRoleCommandStep1 newDeleteRoleCommand(String roleId);

  /**
   * Command to assign a role to a group.
   *
   * <pre>
   *
   * camundaClient
   *  .newAssignRoleToGroupCommand()
   *  .roleId("roleId")
   *  .groupId("groupId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder to configure and send the assign role to group command
   */
  AssignRoleToGroupCommandStep1 newAssignRoleToGroupCommand();

  /**
   * Command to assign a role to a client.
   *
   * <pre>
   *
   * camundaClient
   *  .newAssignRoleToClientCommand()
   *  .roleId("roleId")
   *  .clientId("clientId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder to configure and send the assign role to client command
   */
  AssignRoleToClientCommandStep1 newAssignRoleToClientCommand();

  /**
   * Executes a search request to query clients by role.
   *
   * <pre>
   * camundaClient
   *   .newClientsByRoleSearchRequest("roleId")
   *   .sort((s) -> s.clientId().asc())
   *   .page((p) -> p.limit(100))
   *   .send();
   * </pre>
   *
   * @param roleId the ID of the role
   * @return a builder for the clients by role search request
   */
  ClientsByRoleSearchRequest newClientsByRoleSearchRequest(String roleId);

  /**
   * Executes a search request to query clients by group.
   *
   * <pre>
   * camundaClient
   *   .newClientsByGroupSearchRequest("groupId")
   *   .sort((s) -> s.clientId().asc())
   *   .page((p) -> p.limit(100))
   *   .send();
   * </pre>
   *
   * @param groupId the ID of the group
   * @return a builder for the clients by group search request
   */
  ClientsByGroupSearchRequest newClientsByGroupSearchRequest(String groupId);

  /**
   * Executes a search request to query clients by tenant.
   *
   * <pre>
   * camundaClient
   *   .newClientsByTenantSearchRequest("tenantId")
   *   .sort((s) -> s.clientId().asc())
   *   .page((p) -> p.limit(100))
   *   .send();
   * </pre>
   *
   * @param tenantId the ID of the tenant
   * @return a builder for the clients by tenant search request
   */
  ClientsByTenantSearchRequest newClientsByTenantSearchRequest(String tenantId);

  /**
   * Command to assign a role to a tenant.
   *
   * <pre>
   * camundaClient
   *  .newAssignRoleToTenantCommand()
   *  .roleId("roleId")
   *  .tenantId("tenantId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP.
   *
   * @return a builder for the assign role to tenant command
   */
  AssignRoleToTenantCommandStep1 newAssignRoleToTenantCommand();

  /**
   * Command to unassign a role from a tenant.
   *
   * <pre>
   * camundaClient
   *  .newUnassignRoleFromTenantCommand()
   *  .roleId("roleId")
   *  .tenantId("tenantId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP.
   *
   * @return a builder for the unassign role from tenant command
   */
  UnassignRoleFromTenantCommandStep1 newUnassignRoleFromTenantCommand();

  /**
   * Executes a search request to query roles assigned to a specific tenant.
   *
   * <pre>
   * camundaClient
   *  .newRolesByTenantSearchRequest("tenantId")
   *  .filter((f) -> f.name("admin"))
   *  .sort((s) -> s.name().asc())
   *  .page((p) -> p.limit(50))
   *  .send();
   * </pre>
   *
   * @param tenantId the ID of the tenant
   * @return a builder for the roles by tenant search request
   */
  RolesByTenantSearchRequest newRolesByTenantSearchRequest(String tenantId);

  /**
   * Command to unassign a role from a group.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUnassignRoleFromGroupCommand()
   *   .roleId("roleId")
   *   .groupId("groupId")
   *   .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the unassign role from group command
   */
  UnassignRoleFromGroupCommandStep1 newUnassignRoleFromGroupCommand();

  /**
   * Command to unassign a role from a mapping rule.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUnassignRoleFromMappingRuleCommand()
   *   .roleId("roleId")
   *   .mappingRuleId("mappingRuleId")
   *   .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the unassign role from mapping rule command
   */
  UnassignRoleFromMappingRuleCommandStep1 newUnassignRoleFromMappingRuleCommand();

  /**
   * Command to unassign a role from a client.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUnassignRoleFromClientCommand()
   *   .roleId("roleId")
   *   .clientId("clientId")
   *   .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the unassign role from client command
   */
  UnassignRoleFromClientCommandStep1 newUnassignRoleFromClientCommand();

  /**
   * Command to assign a role to a user.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newAssignRoleToUserCommand()
   *   .roleId("roleId")
   *   .username("username")
   *   .send();
   * </pre>
   *
   * @return a builder for the assign role to user command
   */
  AssignRoleToUserCommandStep1 newAssignRoleToUserCommand();

  /**
   * Command to unassign a role from a user.
   *
   * <pre>
   * camundaClient
   *   .newUnassignRoleFromUserCommand()
   *   .roleId("roleId")
   *   .username("username")
   *   .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC.
   *
   * @return a builder for the unassign role from user command
   */
  UnassignRoleFromUserCommandStep1 newUnassignRoleFromUserCommand();

  /**
   * Executes a search request to query users by role.
   *
   * <pre>
   * camundaClient
   *  .newUsersByRoleSearchRequest("roleId")
   *  .sort((s) -> s.username().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @param roleId the ID of the role
   * @return a builder for the users by role search request
   */
  UsersByRoleSearchRequest newUsersByRoleSearchRequest(String roleId);

  /**
   * Executes a search request to query users assigned to a tenant.
   *
   * <pre>
   * camundaClient
   *  .newUsersByTenantSearchRequest("tenantId")
   *  .sort((s) -> s.username().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @param tenantId the ID of the tenant
   * @return a builder for the users by tenant search request
   */
  UsersByTenantSearchRequest newUsersByTenantSearchRequest(String tenantId);

  /**
   * Executes a search request to query users.
   *
   * <pre>
   *
   * camundaClient
   *  .newUsersSearchRequest()
   *  .filter((f) -> f.username("userName"))
   *  .sort((s) -> s.username().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the users search request
   */
  UsersSearchRequest newUsersSearchRequest();

  /**
   * Command to create a group.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newCreateGroupCommand()
   *  .groupId("groupId")
   *  .name("name")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  CreateGroupCommandStep1 newCreateGroupCommand();

  /**
   * Command to update a group.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newUpdateGroupCommand("groupId")
   *  .name("name")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  UpdateGroupCommandStep1 newUpdateGroupCommand(String groupId);

  /**
   * Command to delete a group.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newDeleteGroupCommand("groupId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  DeleteGroupCommandStep1 newDeleteGroupCommand(String groupId);

  /**
   * Command to assign a user to a group.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newAssignUserToGroupCommand()
   *  .username("username")
   *  .groupId("groupId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  AssignUserToGroupCommandStep1 newAssignUserToGroupCommand();

  /**
   * Command to unassign a user from a group.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newUnassignUserFromGroupCommand()
   *  .username("username")
   *  .groupId("groupId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  UnassignUserFromGroupCommandStep1 newUnassignUserFromGroupCommand();

  /**
   * Command to create a user.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newCreateUserCommand()
   *  .username(username)
   *  .email(email)
   *  .name(name)
   *  .password(password)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  CreateUserCommandStep1 newCreateUserCommand();

  /**
   * Command to delete a user by username
   *
   * <pre>
   *
   * camundaClient
   *  .newDeleteUserCommand("username")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  DeleteUserCommandStep1 newDeleteUserCommand(String username);

  /**
   * Command to update a user.
   *
   * <pre>
   * camundaClient
   *  .newUpdateUserCommand("username")
   *  .name("name")
   *  .email("email@email.com")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  UpdateUserCommandStep1 newUpdateUserCommand(String username);

  /**
   * Request to get a user by username.
   *
   * <pre>
   *
   * camundaClient
   *  .newUserGetRequest("username")
   *  .send();
   * </pre>
   *
   * @param username the username of the user
   * @return a builder for the request to get a user
   */
  UserGetRequest newUserGetRequest(String username);

  /**
   * Command to create a mapping rule.
   *
   * <pre>
   * camundaClient
   *  .newCreateMappingRuleCommand()
   *  .claimName(claimName)
   *  .claimValue(claimValue)
   *  .mappingRuleId(mappingRuleId)
   *  .name(name)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   * <br>
   *
   * @return a builder for the command
   */
  CreateMappingRuleCommandStep1 newCreateMappingRuleCommand();

  /*
   * Retrieves the XML representation of a decision requirements.
   *
   * <pre>
   * long decisionRequirementsKey = ...;
   *
   * camundaClient
   *  .newDecisionRequirementsGetXmlRequest(decisionRequirementsKey)
   *  .send();
   * </pre>
   *
   * @return a builder for the request to get the XML of a decision definition
   */
  DecisionRequirementsGetXmlRequest newDecisionRequirementsGetXmlRequest(
      long decisionRequirementsKey);

  /**
   * Gets a decision requirements by key.
   *
   * <pre>
   *   long decisionRequirementsKey = ...;
   *
   *   camundaClient
   *   .newDecisionRequirementsGetRequest(decisionRequirementsKey)
   *   .send();
   *   </pre>
   *
   * @param decisionRequirementsKey the key of the decision requirements
   * @return a builder for the request to get a decision requirements
   */
  DecisionRequirementsGetRequest newDecisionRequirementsGetRequest(long decisionRequirementsKey);

  /**
   * Gets a user task form by key.
   *
   * <pre>
   *   long userTaskKey = ...;
   *
   *   camundaClient
   *   .newUserTaskGetFormRequest(userTaskKey)
   *   .send();
   *   </pre>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the request to get a user task form
   */
  UserTaskGetFormRequest newUserTaskGetFormRequest(long userTaskKey);

  /**
   * Gets a User Task by key.
   *
   * <pre>
   *   long userTaskKey = ...;
   *
   *   camundaClient
   *   .newUserTaskGetRequest(userTaskKey)
   *   .send();
   *   </pre>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the request to get a user task
   */
  UserTaskGetRequest newUserTaskGetRequest(long userTaskKey);

  /**
   * Executes a search request to query variables.
   *
   * <pre>
   * camundaClient
   *  .newVariableSearchRequest()
   *  .filter((f) -> f.variableKey(variableKey))
   *  .sort((s) -> s.value().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   *
   * @return a builder for the variable search request
   */
  VariableSearchRequest newVariableSearchRequest();

  /**
   * Gets a variable by key.
   *
   * <pre>
   *   long variableKey = ...;
   *
   *  camundaClient
   * .newVariableGetRequest(variableKey)
   * .send();
   *
   *  @param variableKey the key of the variable
   *  @return a builder for the request to get a variable
   */
  VariableGetRequest newVariableGetRequest(long variableKey);

  /**
   * Executes a search request to query variables related to a user task.
   *
   * <pre>
   *   long variableKey = ...;
   *
   *  camundaClient
   *   .newUserTaskVariableSearchRequest(variableKey)
   *   .sort((s) -> s.value().asc())
   *   .page((p) -> p.limit(100))
   *   .send();
   *
   *  @param userTaskKey the key of the user task
   *  @return a builder for the user task variable search request
   */
  UserTaskVariableSearchRequest newUserTaskVariableSearchRequest(long userTaskKey);

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to create a document.
   *
   * <pre>
   *   camundaClient
   *   .newCreateDocumentCommand()
   *   .content(inputStream)
   *   .fileName("file.txt")
   *   .timeToLive(Duration.ofDays(1))
   *   .send();
   *   </pre>
   *
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  CreateDocumentCommandStep1 newCreateDocumentCommand();

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to create a batch of documents. Unlike {@link #newCreateDocumentCommand()}, this
   * command allows you to create multiple documents in a single request. This can be more efficient
   * than creating each document individually, however, there are multiple limitations to consider.
   * <br>
   * <br>
   * <strong>Limitations:</strong>
   *
   * <ul>
   *   <li>The gateway does not guarantee the atomicity of the batch operation. If the gateway
   *       receives the batch but fails to create one or more documents, it will not roll back the
   *       operation. This means that some documents may be created while others are not. The client
   *       should handle this scenario by checking the response for each document.
   *   <li>Each document in the batch must have a unique name.
   *   <li>It is not possible to assign a custom document ID to the documents in the batch. The
   *       document ID will be generated by the broker.
   *   <li>The total size of the batch must not exceed the multipart request size limit of the
   *       gateway.
   *   <li>The documents can only be created in a single store. If you need to create documents in
   *       multiple stores, you must create separate batches for each store.
   * </ul>
   *
   * <br>
   *
   * <pre>
   *   zeebeClient
   *   .newCreateDocumentBatchCommand()
   *   .addDocument()
   *   .content(inputStream1)
   *   .fileName("file1.txt")
   *   .timeToLive(Duration.ofDays(1))
   *   .done()
   *   .addDocument()
   *   .content(inputStream2)
   *   .fileName("file2.txt")
   *   .timeToLive(Duration.ofDays(1))
   *   .done()
   *   </pre>
   *
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  CreateDocumentBatchCommandStep1 newCreateDocumentBatchCommand();

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to get a document.
   *
   * <pre>
   *   camundaClient
   *   .newDocumentContentGetRequest(documentId)
   *   .storeId(storeId)
   *   .send();
   *   </pre>
   *
   * @param documentId the id of the document
   * @return a builder for the request
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  DocumentContentGetRequest newDocumentContentGetRequest(String documentId);

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to get a document.
   *
   * <pre>
   *   camundaClient
   *   .newDocumentContentGetRequest(documentReferenceResponse)
   *   .send();
   *   </pre>
   *
   * @param documentReferenceResponse the reference of the document
   * @return a builder for the request
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  DocumentContentGetRequest newDocumentContentGetRequest(
      DocumentReferenceResponse documentReferenceResponse);

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to update a document.
   *
   * <pre>
   *   camundaClient
   *   .newCreateDocumentLinkCommand(documentId)
   *   .storeId(storeId)
   *   .timeToLive(Duration.ofHours(1))
   *   .send();
   *   </pre>
   *
   * @param documentId the id of the document
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(String documentId);

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to update a document.
   *
   * <pre>
   *   camundaClient
   *   .newCreateDocumentLinkCommand(documentReferenceResponse)
   *   .timeToLive(Duration.ofHours(1))
   *   .send();
   *   </pre>
   *
   * @param documentReferenceResponse the reference of the document
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  CreateDocumentLinkCommandStep1 newCreateDocumentLinkCommand(
      DocumentReferenceResponse documentReferenceResponse);

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to update a document.
   *
   * <pre>
   *   camundaClient
   *   .newDeleteDocumentCommand(documentId)
   *   .storeId(storeId)
   *   .send();
   *   </pre>
   *
   * @param documentId the id of the document
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  DeleteDocumentCommandStep1 newDeleteDocumentCommand(String documentId);

  /**
   * <strong>Experimental: This method is under development. The respective API on compatible
   * clusters cannot be considered production-ready. Thus, this method doesn't work out of the box
   * with all clusters. Until this warning is removed, anything described below may not yet have
   * taken effect, and the interface and its description are subject to change.</strong>
   *
   * <p>Command to update a document.
   *
   * <pre>
   *   camundaClient
   *   .newDeleteDocumentCommand(documentReferenceResponse)
   *   .send();
   *   </pre>
   *
   * @param documentReferenceResponse the reference of the document
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/issues/issues/841")
  DeleteDocumentCommandStep1 newDeleteDocumentCommand(
      DocumentReferenceResponse documentReferenceResponse);

  /**
   * Command to create a tenant.
   *
   * <pre>
   * camundaClient
   *  .newCreateTenantCommand()
   *  .tenantId("tenant-id")
   *  .name("Tenant Name")
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  CreateTenantCommandStep1 newCreateTenantCommand();

  /**
   * Creates a command to update the name of an existing tenant.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUpdateTenantCommand("my-tenant-id") // Specify the tenant id
   *   .name("Updated Tenant Name")   // Set the new tenant name
   *   .send();                       // Send the command to the broker
   * </pre>
   *
   * @param tenantId the unique identifier of the tenant to be updated
   * @return a builder to configure and send the update tenant command
   */
  UpdateTenantCommandStep1 newUpdateTenantCommand(String tenantId);

  /**
   * Request to get a tenant by tenant ID.
   *
   * <pre>
   *
   * camundaClient
   *  .newTenantGetRequest("tenantId")
   *  .send();
   * </pre>
   *
   * @param tenantId the ID of the tenant
   * @return a builder for the request to get a tenant by ID
   */
  TenantGetRequest newTenantGetRequest(String tenantId);

  /**
   * Executes a search request to query tenants.
   *
   * <pre>
   *
   * camundaClient
   *  .newTenantsSearchRequest()
   *  .filter((f) -> f.name("tenantName"))
   *  .sort((s) -> s.name().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the tenants search request
   */
  TenantsSearchRequest newTenantsSearchRequest();

  /**
   * Command to delete a tenant.
   *
   * <pre>
   * camundaClient
   *  .newDeleteTenantCommand(tenantId)
   *  .send();
   * </pre>
   *
   * @param tenantId the id of the tenant to delete
   * @return a builder for the delete tenant command
   */
  DeleteTenantCommandStep1 newDeleteTenantCommand(String tenantId);

  /**
   * Command to assign a mapping rule to a tenant.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newAssignMappingRuleToTenantCommand()
   *   .mappingRuleId("mappingRuleId")
   *   .tenantId("tenantId")
   *   .send();
   * </pre>
   *
   * <p>This command sends an HTTP PUT request to assign the specified mapping rule to the given
   * tenant.
   *
   * @return a builder for the assign mapping rule to tenant command
   */
  AssignMappingRuleToTenantCommandStep1 newAssignMappingRuleToTenantCommand();

  /**
   * Command to assign a user to a tenant.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newAssignUserToTenantCommand()
   *   .username(username)
   *   .tenantId(tenantId)
   *   .send();
   * </pre>
   *
   * <p>This command sends an HTTP PUT request to assign the specified user to the given tenant.
   *
   * @return a builder for the assign user to tenant command
   */
  AssignUserToTenantCommandStep1 newAssignUserToTenantCommand();

  /**
   * Command to remove a user from a tenant.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUnassignUserFromTenantCommand()
   *   .username(username)
   *   .tenantId(tenantId)
   *   .send();
   * </pre>
   *
   * <p>This command sends an HTTP DELETE request to remove the specified user from the given
   * tenant.
   *
   * @return a builder for the remove user from tenant command
   */
  RemoveUserFromTenantCommandStep1 newUnassignUserFromTenantCommand();

  /**
   * Command to assign a group to a tenant.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newAssignGroupToTenantCommand()
   *   .groupId("groupId")
   *   .tenantId("tenantId")
   *   .send();
   * </pre>
   *
   * @return a builder to configure and send the assign group to tenant command
   */
  AssignGroupToTenantCommandStep1 newAssignGroupToTenantCommand();

  /**
   * Command to unassign a group from a tenant.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUnassignGroupFromTenantCommand(tenantId)
   *   .groupId(groupId)
   *   .send();
   * </pre>
   *
   * @param tenantId the unique identifier of the tenant
   * @return a builder to configure and send the unassign group from tenant command
   */
  UnassignGroupFromTenantCommandStep1 newUnassignGroupFromTenantCommand(String tenantId);

  /**
   * Command to assign a client to a group.
   *
   * <pre>
   *
   * camundaClient
   *  .newAssignClientToGroupCommand()
   *  .clientId("clientId")
   *  .groupId("groupId")
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder to configure and send the assign client to group command
   */
  AssignClientToGroupCommandStep1 newAssignClientToGroupCommand();

  /**
   * Command to unassign a client from a group.
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUnassignClientFromGroupCommand()
   *   .clientId("clientId")
   *   .groupId("groupId")
   *   .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the unassign client from group command
   */
  UnassignClientFromGroupCommandStep1 newUnassignClientFromGroupCommand();

  /**
   * Command to create an authorization
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newCreateAuthorizationCommand()
   *   .ownerId(ownerId)
   *   .ownerType(ownerType)
   *   .resourceId(resourceId)
   *   .resourceType(resourceType)
   *   .permission(PermissionType.READ)
   *   .send();
   * </pre>
   *
   * @return a builder to configure and send the create authorization command
   */
  CreateAuthorizationCommandStep1 newCreateAuthorizationCommand();

  /**
   * Request to get an authorization by authorization key.
   *
   * <pre>
   *
   * camundaClient
   *  .newAuthorizationGetRequest(authorizationKey)
   *  .send();
   * </pre>
   *
   * @param authorizationKey the authorizationKey of the authorization
   * @return a builder for the request to get an authorization
   */
  AuthorizationGetRequest newAuthorizationGetRequest(long authorizationKey);

  /**
   * Executes a search request to query authorizations.
   *
   * <pre>
   *
   * camundaClient
   *  .newAuthorizationSearchRequest()
   *  .filter((f) -> f.ownerType("ownerType"))
   *  .sort((s) -> s.ownerId().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the authorizations search request
   */
  AuthorizationsSearchRequest newAuthorizationSearchRequest();

  /**
   * Command to delete an authorization
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newDeleteAuthorizationCommand(authorizationKey)
   *   .send();
   * </pre>
   *
   * @param authorizationKey the key of the authorization to delete
   * @return a builder to configure and send the delete authorization command
   */
  DeleteAuthorizationCommandStep1 newDeleteAuthorizationCommand(long authorizationKey);

  /**
   * Command to update an authorization
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newUpdateAuthorizationCommand(authorizationKey)
   *   .ownerId(ownerId)
   *   .ownerType(ownerType)
   *   .resourceId(resourceId)
   *   .resourceType(resourceType)
   *   .permissionTypes(Set.of(PermissionType.READ))
   *   .send();
   * </pre>
   *
   * @param authorizationKey the key of the authorization to update
   * @return a builder to configure and send the update authorization command
   */
  UpdateAuthorizationCommandStep1 newUpdateAuthorizationCommand(long authorizationKey);

  /**
   * Command to create a batch operation
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newCreateBatchOperationCommand()
   *   .processInstanceCancel()
   *   .filter(filter)
   *   .send();
   * </pre>
   *
   * @return a builder to configure and send the create batch operation command
   */
  CreateBatchOperationCommandStep1 newCreateBatchOperationCommand();

  /**
   * Request to get a single batch operation by batch operation key.
   *
   * <pre>
   * camundaClient
   *  .newBatchOperationGetRequest(batchOperationKey)
   *  .send();
   * </pre>
   *
   * @param batchOperationKey the key which identifies the corresponding batch operation
   * @return a builder for the request
   */
  BatchOperationGetRequest newBatchOperationGetRequest(String batchOperationKey);

  /**
   * Executes a search request to query batch operations.
   *
   * <pre>
   *
   * camundaClient
   *  .newBatchOperationSearchRequest()
   *  .filter((f) -> f.state(state))
   *  .sort((s) -> s.startDate().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the groups search request
   */
  BatchOperationSearchRequest newBatchOperationSearchRequest();

  /**
   * Command to cancel a batch operation
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newCancelBatchOperationCommand("123")
   *   .send();
   * </pre>
   *
   * @return a builder to configure and send the cancel batch operation command
   */
  CancelBatchOperationStep1 newCancelBatchOperationCommand(String batchOperationKey);

  /**
   * Command to suspend a batch operation
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newSuspendBatchOperationCommand("123L")
   *   .send();
   * </pre>
   *
   * @return a builder to configure and send the suspend batch operation command
   */
  SuspendBatchOperationStep1 newSuspendBatchOperationCommand(String batchOperationKey);

  /**
   * Command to resume a batch operation
   *
   * <p>Example usage:
   *
   * <pre>
   * camundaClient
   *   .newResumeBatchOperationCommand("123L")
   *   .send();
   * </pre>
   *
   * @return a builder to configure and send the resume batch operation command
   */
  ResumeBatchOperationStep1 newResumeBatchOperationCommand(String batchOperationKey);

  /**
   * Executes a search request to query batch operation items.
   *
   * <pre>
   *
   * camundaClient
   *  .newBatchOperationItemSearchRequest()
   *  .filter((f) -> f.state(state))
   *  .sort((s) -> s.itemKey().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the groups search request
   */
  BatchOperationItemSearchRequest newBatchOperationItemsSearchRequest();

  /**
   * Command to assign a mapping rule to a group.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newAssignMappingRuleToGroupCommand()
   *  .mappingRuleId(mappingRuleId)
   *  .groupId(groupId)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  AssignMappingRuleToGroupStep1 newAssignMappingRuleToGroupCommand();

  /**
   * Command to unassign a mapping rule from a group.
   *
   * <pre>
   *
   *
   * camundaClient
   *  .newUnassignMappingRuleFromGroupCommand()
   *  .mappingRuleId(mappingRuleId)
   *  .groupId(groupId)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   *
   * @return a builder for the command
   */
  UnassignMappingRuleFromGroupStep1 newUnassignMappingRuleFromGroupCommand();

  /**
   * Request to get a group by group ID.
   *
   * <pre>
   *
   * camundaClient
   *  .newGroupGetRequest(groupId)
   *  .send();
   * </pre>
   *
   * @param groupId the ID of the group
   * @return a builder for the request to get a group
   */
  GroupGetRequest newGroupGetRequest(String groupId);

  /**
   * Executes a search request to query groups.
   *
   * <pre>
   *
   * camundaClient
   *  .newGroupsSearchRequest()
   *  .filter((f) -> f.name(name))
   *  .sort((s) -> s.name().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the groups search request
   */
  GroupsSearchRequest newGroupsSearchRequest();

  /**
   * Executes a search request to query users by group.
   *
   * <pre>
   *
   * camundaClient
   *  .newUsersByGroupSearchRequest(groupId)
   *  .sort((s) -> s.username().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the users by group search request
   */
  UsersByGroupSearchRequest newUsersByGroupSearchRequest(String groupId);

  /**
   * Retrieves the call hierarchy for a given process instance by its key.
   *
   * <pre>
   * camundaClient
   *   .newProcessInstanceGetCallHierarchyRequest(processInstanceKey)
   *   .send();
   * </pre>
   *
   * <p>The returned hierarchical structure represents the relationship of the given process
   * instance to its parent and child process instances, if any.
   *
   * @param processInstanceKey the key of the process instance
   * @return a builder for the request
   */
  ProcessInstanceGetCallHierarchyRequest newProcessInstanceGetCallHierarchyRequest(
      Long processInstanceKey);

  /**
   * Executes a search request to query mapping rules by group.
   *
   * <pre>
   *   camundaClient
   *    .newMappingRulesByGroupSearchRequest(groupId)
   *    .filter((f) -> f.mappingRuleId(mappingRuleId))
   *    .sort((s) -> s.mappingRuleId().asc())
   *    .page((p) -> p.limit(100))
   *    .send();
   * </pre>
   *
   * @param groupId the ID of the group
   * @return a builder for the mapping rules by group search request
   */
  MappingRulesByGroupSearchRequest newMappingRulesByGroupSearchRequest(String groupId);

  /**
   * Executes a search request to query mapping rules by role.
   *
   * <pre>
   * camundaClient
   *  .newMappingRulesByRoleSearchRequest("roleId")
   *  .filter((f) -> f.mappingRuleId("mapping-rule-123"))
   *  .sort((s) -> s.mappingRuleId().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @param roleId the ID of the role
   * @return a builder for the mapping rules by role search request
   */
  MappingRulesByRoleSearchRequest newMappingRulesByRoleSearchRequest(String roleId);

  /**
   * Executes a search request to query roles by group.
   *
   * <pre>
   *   camundaClient
   *    .newRolesByGroupSearchRequest(groupId)
   *    .filter((f) -> f.roleName(roleName))
   *    .sort((s) -> s.roleName().asc())
   *    .page((p) -> p.limit(100))
   *    .send();
   *  </pre>
   *
   * @param groupId the ID of the group
   * @return a builder for the roles by group search request
   */
  RolesByGroupSearchRequest newRolesByGroupSearchRequest(String groupId);

  /**
   * Executes a search request to query incidents by process instance key.
   *
   * <pre>
   *   camundaClient
   *    .newIncidentSearchByProcessInstanceKey(processInstanceKey)
   *    .sort((s) -> s.incidentKey().desc())
   *    .page((p) -> p.limit(100))
   *    .send();
   * </pre>
   */
  IncidentsByProcessInstanceSearchRequest newIncidentsByProcessInstanceSearchRequest(
      long processInstanceKey);

  /**
   * Executes a search request to query groups by role.
   *
   * <pre>
   * camundaClient
   *  .newGroupsByRoleSearchRequest("roleId")
   *  .sort((s) -> s.groupdId().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @param roleId the ID of the role
   * @return a builder for the groups by role search request
   */
  GroupsByRoleSearchRequest newGroupsByRoleSearchRequest(String roleId);

  /**
   * Executes a search request to query groups by tenant.
   *
   * <pre>
   * camundaClient
   *  .newGroupsByTenantSearchRequest("tenantId")
   *  .sort((s) -> s.groupId().desc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @param tenantId the ID of the tenant
   * @return a builder for the groups by tenant search request
   */
  GroupsByTenantSearchRequest newGroupsByTenantSearchRequest(String tenantId);

  /**
   * Executes a search request to query jobs.
   *
   * <pre>
   * camundaClient
   *  .newJobSearchRequest()
   *  .filter((f) -> f.jobKey(userTaskKey))
   *  .sort((s) -> s.endTime.asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the job search request
   */
  JobSearchRequest newJobSearchRequest();

  /**
   * Executes a search request to query message subscriptions.
   *
   * <pre>
   * camundaClient
   *  .newMessageSubscriptionSearchRequest()
   *  .filter((f) -> f.messageSubscriptionKey(messageSubscriptionKey))
   *  .sort((s) -> s.messageName().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * @return a builder for the message subscription search request
   */
  MessageSubscriptionSearchRequest newMessageSubscriptionSearchRequest();
}
