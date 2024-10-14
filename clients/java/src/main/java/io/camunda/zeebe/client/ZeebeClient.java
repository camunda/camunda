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
package io.camunda.zeebe.client;

import io.camunda.zeebe.client.api.ExperimentalApi;
import io.camunda.zeebe.client.api.command.AddPermissionsCommandStep1;
import io.camunda.zeebe.client.api.command.AssignUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.zeebe.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ClockPinCommandStep1;
import io.camunda.zeebe.client.api.command.ClockResetCommandStep1;
import io.camunda.zeebe.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.CreateUserCommandStep1;
import io.camunda.zeebe.client.api.command.DeleteResourceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployProcessCommandStep1;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.client.api.command.UnassignUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateUserTaskCommandStep1;
import io.camunda.zeebe.client.api.fetch.DecisionDefinitionGetRequest;
import io.camunda.zeebe.client.api.fetch.DecisionDefinitionGetXmlRequest;
import io.camunda.zeebe.client.api.fetch.DecisionInstanceGetRequest;
import io.camunda.zeebe.client.api.fetch.DecisionRequirementsGetRequest;
import io.camunda.zeebe.client.api.fetch.DecisionRequirementsGetXmlRequest;
import io.camunda.zeebe.client.api.fetch.ProcessDefinitionGetRequest;
import io.camunda.zeebe.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.zeebe.client.api.fetch.ProcessInstanceGetRequest;
import io.camunda.zeebe.client.api.fetch.UserTaskGetFormRequest;
import io.camunda.zeebe.client.api.fetch.UserTaskGetRequest;
import io.camunda.zeebe.client.api.fetch.VariableGetRequest;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.search.query.DecisionDefinitionQuery;
import io.camunda.zeebe.client.api.search.query.DecisionInstanceQuery;
import io.camunda.zeebe.client.api.search.query.DecisionRequirementsQuery;
import io.camunda.zeebe.client.api.search.query.FlownodeInstanceQuery;
import io.camunda.zeebe.client.api.search.query.IncidentQuery;
import io.camunda.zeebe.client.api.search.query.ProcessDefinitionQuery;
import io.camunda.zeebe.client.api.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.client.api.search.query.UserTaskQuery;
import io.camunda.zeebe.client.api.search.query.VariableQuery;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.client.impl.ZeebeClientCloudBuilderImpl;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;

/** The client to communicate with a Zeebe broker/cluster. */
public interface ZeebeClient extends AutoCloseable, JobClient {

  /**
   * @return a new Zeebe client with default configuration values. In order to customize
   *     configuration, use the methods {@link #newClientBuilder()} or {@link
   *     #newClient(ZeebeClientConfiguration)}. See {@link ZeebeClientBuilder} for the configuration
   *     options and default values.
   */
  static ZeebeClient newClient() {
    return newClientBuilder().build();
  }

  /**
   * @return a new {@link ZeebeClient} using the provided configuration.
   */
  static ZeebeClient newClient(final ZeebeClientConfiguration configuration) {
    return new ZeebeClientImpl(configuration);
  }

  /**
   * @return a builder to configure and create a new {@link ZeebeClient}.
   */
  static ZeebeClientBuilder newClientBuilder() {
    return new ZeebeClientBuilderImpl();
  }

  /**
   * @return a builder with convenient methods to connect to the Camunda Cloud cluster.
   */
  static ZeebeClientCloudBuilderStep1 newCloudClientBuilder() {
    return new ZeebeClientCloudBuilderImpl();
  }

  /**
   * Request the current cluster topology. Can be used to inspect which brokers are available at
   * which endpoint and which broker is the leader of which partition.
   *
   * <pre>
   * List&#60;BrokerInfo&#62; brokers = zeebeClient
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
  ZeebeClientConfiguration getConfiguration();

  @Override
  void close();

  /**
   * Command to deploy new processes.
   *
   * <pre>
   * zeebeClient
   *  .newDeployCommand()
   *  .addResourceFile("~/wf/process1.bpmn")
   *  .addResourceFile("~/wf/process2.bpmn")
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   * @deprecated since 8 for removal with 8.1, replaced by {@link
   *     ZeebeClient#newDeployResourceCommand()}
   */
  DeployProcessCommandStep1 newDeployCommand();

  /**
   * Command to deploy new resources, i.e. BPMN process models and DMN decision models.
   *
   * <pre>
   * zeebeClient
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
   * zeebeClient
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
   *   zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * JobWorker worker = zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
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
   * zeebeClient
   *  .newClockPinCommand()
   *  .time(pinnedTime)
   *  .send();
   *
   * final Instant futureInstant = Instant.now().plus(Duration.ofDays(7));
   * zeebeClient
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
   * zeebeClient
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
   *   zeebeClient
   *   .newProcessDefinitionGetRequest(processDefinitionKey)
   *   .send();
   *   </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param processDefinitionKey the key of the process definition
   * @return a builder for the request to get a process definition
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  ProcessDefinitionGetRequest newProcessDefinitionGetRequest(long processDefinitionKey);

  /*
   * Retrieves the XML representation of a process definition.
   *
   * <pre>
   * long processDefinitionKey = ...;
   *
   * zeebeClient
   *  .newProcessDefinitionGetXmlRequest(processDefinitionKey)
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param decisionDefinitionKey the key of the decision definition
   * @return a builder for the request to get the XML of a decision definition
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  ProcessDefinitionGetXmlRequest newProcessDefinitionGetXmlRequest(long processDefinitionKey);

  /**
   * Executes a search request to query process definitions.
   *
   * <pre>
   * long processDefinitionKey = ...;
   *
   * zeebeClient
   *  .newProcessDefinitionQuery()
   *  .filter((f) -> f.processDefinitionKey(processDefinitionKey))
   *  .sort((s) -> s.name().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the process definition query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  ProcessDefinitionQuery newProcessDefinitionQuery();

  /**
   * Retrieves a process instance by key.
   *
   * <pre>
   * long processInstanceKey = ...;
   *
   * zeebeClient
   *  .newProcessInstanceGetRequest(processInstanceKey)
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the request to get a process instance
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  ProcessInstanceGetRequest newProcessInstanceGetRequest(long processInstanceKey);

  /**
   * Executes a search request to query process instances.
   *
   * <pre>
   * long processInstanceKey = ...;
   *
   * zeebeClient
   *  .newProcessInstanceQuery()
   *  .filter((f) -> f.processInstanceKeys(processInstanceKey))
   *  .sort((s) -> s.startDate().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the process instance query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  ProcessInstanceQuery newProcessInstanceQuery();

  /**
   * Executes a search request to query flow node instances.
   *
   * <pre>
   * long flownodeInstanceKey = ...;
   *
   * zeebeClient
   *  .newFlownodeInstanceQuery()
   *  .filter((f) -> f.processInstanceKeys(processInstanceKey))
   *  .sort((s) -> s.flowNodeName().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the process instance query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  FlownodeInstanceQuery newFlownodeInstanceQuery();

  /**
   * Executes a search request to query user tasks.
   *
   * <pre>
   * zeebeClient
   *  .newUserTaskQuery()
   *  .filter((f) -> f.userTaskKey(userTaskKey))
   *  .sort((s) -> s.creationDate().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the user task query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  UserTaskQuery newUserTaskQuery();

  /**
   * Executes a search request to query Decision Requirements.
   *
   * <pre>
   *   zeebeClient
   *   .newDecisionRequirementsQuery()
   *   .filter((f) -> f.decisionRequirementsKey(decisionRequirementsKey))
   *   .sort((s) -> s.version().asc())
   *   .page((p) -> p.limit(100))
   *   .send();
   *   </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the decision requirements query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionRequirementsQuery newDecisionRequirementsQuery();

  /*
   * Executes a search request to query decision definitions.
   *
   * <pre>
   * long decisionDefinitionKey = ...;
   *
   * zeebeClient
   *  .newDecisionDefinitionQuery()
   *  .filter((f) -> f.decisionKey(decisionDefinitionKey))
   *  .sort((s) -> s.dmnDecisionName().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the decision definition query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionDefinitionQuery newDecisionDefinitionQuery();

  /**
   * Gets a decision definition by key.
   *
   * <pre>
   *   long decisionDefinitionKey = ...;
   *
   *   zeebeClient
   *   .newDecisionDefinitionGetRequest(decisionDefinitionKey)
   *   .send();
   *   </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param decisionDefinitionKey the key of the decision definition
   * @return a builder for the request to get a decision definition
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionDefinitionGetRequest newDecisionDefinitionGetRequest(long decisionDefinitionKey);

  /*
   * Retrieves the XML representation of a decision definition.
   *
   * <pre>
   * long decisionDefinitionKey = ...;
   *
   * zeebeClient
   *  .newDecisionDefinitionGetXmlRequest(decisionDefinitionKey)
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param decisionDefinitionKey the key of the decision definition
   * @return a builder for the request to get the XML of a decision definition
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionDefinitionGetXmlRequest newDecisionDefinitionGetXmlRequest(long decisionDefinitionKey);

  /**
   * Executes a search request to query decision instances.
   *
   * <pre>
   * long decisionInstanceKey = ...;
   *
   * zeebeClient
   *  .newDecisionInstanceQuery()
   *  .filter((f) -> f.decisionInstanceKey(decisionInstanceKey))
   *  .sort((s) -> s.decisionInstanceKey().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the decision instance query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionInstanceQuery newDecisionInstanceQuery();

  /**
   * Retrieves a decision instance by key.
   *
   * <pre>
   * long decisionInstanceKey = ...;
   *
   * zeebeClient
   * .newDecisionInstanceGetQuery(decisionInstanceKey)
   * .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param decisionInstanceKey
   * @return a builder for the request to get a decision instance
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionInstanceGetRequest newDecisionInstanceGetRequest(long decisionInstanceKey);

  /*
   * Executes a search request to query decision definitions.
   *
   * <pre>
   * long decisionDefinitionKey = ...;
   *
   * zeebeClient
   *  .newIncidentQuery()
   *  .filter((f) -> f.processInstanceKey(processInstanceKey))
   *  .sort((s) -> s.processDefinitionKey().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the incident query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  IncidentQuery newIncidentQuery();

  /**
   * Command to create a user.
   *
   * <pre>
   *
   *
   * zeebeClient
   *  .newUserCreateCommand()
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
  CreateUserCommandStep1 newUserCreateCommand();

  /**
   * Command to add permissions to an owner.
   *
   * <pre>
   * zeebeClient
   *  .newAddPermissionsCommand(ownerKey)
   *  .resourceType(resourceType)
   *  .permission(permissionType)
   *  .resourceIds(resourceIds)
   *  .permission(permissionType)
   *  .resourceId(resourceId)
   *  .resourceId(resourceId)
   *  .send();
   * </pre>
   *
   * <p>This command is only sent via REST over HTTP, not via gRPC <br>
   * <br>
   *
   * @param ownerKey the key of the owner
   * @return a builder for the command
   */
  AddPermissionsCommandStep1 newAddPermissionsCommand(long ownerKey);

  /*
   * Retrieves the XML representation of a decision requirements.
   *
   * <pre>
   * long decisionRequirementsKey = ...;
   *
   * zeebeClient
   *  .newDecisionRequirementsGetXmlRequest(decisionRequirementsKey)
   *  .send();
   * </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the request to get the XML of a decision definition
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionRequirementsGetXmlRequest newDecisionRequirementsGetXmlRequest(
      long decisionRequirementsKey);

  /**
   * Gets a decision requirements by key.
   *
   * <pre>
   *   long decisionRequirementsKey = ...;
   *
   *   zeebeClient
   *   .newDecisionRequirementsGetRequest(decisionRequirementsKey)
   *   .send();
   *   </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param decisionRequirementsKey the key of the decision requirements
   * @return a builder for the request to get a decision requirements
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  DecisionRequirementsGetRequest newDecisionRequirementsGetRequest(long decisionRequirementsKey);

  /**
   * Gets a user task form by key.
   *
   * <pre>
   *   long userTaskKey = ...;
   *
   *   zeebeClient
   *   .newUserTaskGetFormRequest(userTaskKey)
   *   .send();
   *   </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the request to get a user task form
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  UserTaskGetFormRequest newUserTaskGetFormRequest(long userTaskKey);

  /**
   * Gets a User Task by key.
   *
   * <pre>
   *   long userTaskKey = ...;
   *
   *   zeebeClient
   *   .newUserTaskGetRequest(userTaskKey)
   *   .send();
   *   </pre>
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @param userTaskKey the key of the user task
   * @return a builder for the request to get a user task
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  UserTaskGetRequest newUserTaskGetRequest(long userTaskKey);

  /**
   * Executes a search request to query variables.
   *
   * <pre>
   * zeebeClient
   *  .newVariableQuery()
   *  .filter((f) -> f.variableKey(variableKey))
   *  .sort((s) -> s.value().asc())
   *  .page((p) -> p.limit(100))
   *  .send();
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   * @return a builder for the variable query
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  VariableQuery newVariableQuery();

  /**
   * Gets a variable by key.
   *
   * <pre>
   *   long variableKey = ...;
   *
   *  zeebeClient
   * .newVariableGetRequest(variableKey)
   * .send();
   *
   * <p><strong>Experimental: This method is under development, and as such using it may have no
   * effect on the client builder when called. The respective API on compatible clusters is not
   * enabled by default. Thus, this method doesn't work out of the box with all clusters. Until this
   * warning is removed, anything described below may not yet have taken effect, and the interface
   * and its description are subject to change.</strong>
   *
   *  @param variableKey the key of the variable
   *  @return a builder for the request to get a variable
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/20596")
  VariableGetRequest newVariableGetRequest(long variableKey);
}
