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
package io.zeebe.client;

import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.ActivateJobsCommandStep1;
import io.zeebe.client.api.commands.CancelWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1;
import io.zeebe.client.api.commands.PublishMessageCommandStep1;
import io.zeebe.client.api.commands.ResolveIncidentCommandStep1;
import io.zeebe.client.api.commands.SetVariablesCommandStep1;
import io.zeebe.client.api.commands.TopologyRequestStep1;
import io.zeebe.client.api.commands.UpdateRetriesJobCommandStep1;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;

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

  /** @return a new {@link ZeebeClient} using the provided configuration. */
  static ZeebeClient newClient(final ZeebeClientConfiguration configuration) {
    return new ZeebeClientImpl(configuration);
  }

  /** @return a builder to configure and create a new {@link ZeebeClient}. */
  static ZeebeClientBuilder newClientBuilder() {
    return new ZeebeClientBuilderImpl();
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
   *  SocketAddress address = broker.getSocketAddress();
   *
   *  List&#60;PartitionInfo&#62; partitions = broker.getPartitions();
   * </pre>
   *
   * @return the request where you must call {@code send()}
   */
  TopologyRequestStep1 newTopologyRequest();

  /** @return the client's configuration */
  ZeebeClientConfiguration getConfiguration();

  @Override
  void close();

  /**
   * Command to deploy new workflows.
   *
   * <pre>
   * zeebeClient
   *  .newDeployCommand()
   *  .addResourceFile("~/wf/workflow1.bpmn")
   *  .addResourceFile("~/wf/workflow2.bpmn")
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  DeployWorkflowCommandStep1 newDeployCommand();

  /**
   * Command to create/start a new instance of a workflow.
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
  CreateWorkflowInstanceCommandStep1 newCreateInstanceCommand();

  /**
   * Command to cancel a workflow instance.
   *
   * <pre>
   * zeebeClient
   *  .newCancelInstanceCommand(workflowInstanceKey)
   *  .send();
   * </pre>
   *
   * @param workflowInstanceKey the key which identifies the corresponding workflow instance
   * @return a builder for the command
   */
  CancelWorkflowInstanceCommandStep1 newCancelInstanceCommand(long workflowInstanceKey);

  /**
   * Command to set and/or update the variables of a given flow element (e.g. workflow instance,
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
   * Command to publish a message which can be correlated to a workflow instance.
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
   * public class PaymentHandler implements JobHandler
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
   * Command to activate multiple jobs of a given type.
   *
   * <pre>
   * zeebeClient
   *  .newActivateJobsCommand()
   *  .jobType("payment")
   *  .maxJobsToActivate(10)
   *  .workerName("paymentWorker")
   *  .timeout(Duration.ofMinutes(10))
   *  .send();
   * </pre>
   *
   * <p>The command will try to use {@code maxJobsToActivate} for given {@code jobType}. If less
   * then the requested {@code maxJobsToActivate} jobs of the {@code jobType} are available for
   * activation the returned list will have fewer elements.
   *
   * @return a builder for the command
   */
  ActivateJobsCommandStep1 newActivateJobsCommand();
}
