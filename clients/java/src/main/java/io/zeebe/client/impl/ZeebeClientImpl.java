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

package io.zeebe.client.impl;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.ActivateJobsCommandStep1;
import io.zeebe.client.api.commands.CancelWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.CompleteJobCommandStep1;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1;
import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.api.commands.PublishMessageCommandStep1;
import io.zeebe.client.api.commands.ResolveIncidentCommandStep1;
import io.zeebe.client.api.commands.SetVariablesCommandStep1;
import io.zeebe.client.api.commands.TopologyRequestStep1;
import io.zeebe.client.api.commands.UpdateRetriesJobCommandStep1;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.job.ActivateJobsCommandImpl;
import io.zeebe.client.impl.job.JobUpdateRetriesCommandImpl;
import io.zeebe.client.impl.subscription.JobWorkerBuilderImpl;
import io.zeebe.client.impl.workflow.CancelWorkflowInstanceCommandImpl;
import io.zeebe.client.impl.workflow.CreateWorkflowInstanceCommandImpl;
import io.zeebe.client.impl.workflow.DeployWorkflowCommandImpl;
import io.zeebe.client.impl.workflow.PublishMessageCommandImpl;
import io.zeebe.client.impl.workflow.ResolveIncidentCommandImpl;
import io.zeebe.client.impl.workflow.SetVariablesCommandImpl;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.util.CloseableSilently;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ZeebeClientImpl implements ZeebeClient {
  private final ZeebeClientConfiguration config;
  private final ZeebeObjectMapper objectMapper;
  private final GatewayStub asyncStub;
  private final ManagedChannel channel;
  private final ScheduledExecutorService executorService;
  private final List<CloseableSilently> closeables = new CopyOnWriteArrayList<>();
  private final JobClient jobClient;

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration) {
    this(configuration, buildChannel(configuration));
  }

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration, ManagedChannel channel) {
    this(configuration, channel, buildExecutorService(configuration));
  }

  public ZeebeClientImpl(
      ZeebeClientConfiguration config,
      ManagedChannel channel,
      ScheduledExecutorService executorService) {
    this.config = config;
    this.objectMapper = new ZeebeObjectMapper();
    this.channel = channel;
    this.asyncStub = GatewayGrpc.newStub(channel);
    this.executorService = executorService;
    this.jobClient = newJobClient();
  }

  public static ManagedChannel buildChannel(ZeebeClientConfiguration config) {
    final URI address;

    try {
      address = new URI("zb://" + config.getBrokerContactPoint());
    } catch (final URISyntaxException e) {
      throw new RuntimeException("Failed to parse broker contact point", e);
    }

    // TODO: Issue #1134 - https://github.com/zeebe-io/zeebe/issues/1134
    return ManagedChannelBuilder.forAddress(address.getHost(), address.getPort())
        .usePlaintext()
        .build();
  }

  private static ScheduledExecutorService buildExecutorService(
      ZeebeClientConfiguration configuration) {
    final int threadCount = configuration.getNumJobWorkerExecutionThreads();
    return Executors.newScheduledThreadPool(threadCount);
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestImpl(asyncStub);
  }

  @Override
  public ZeebeClientConfiguration getConfiguration() {
    return this.config;
  }

  @Override
  public void close() {
    closeables.forEach(CloseableSilently::close);

    executorService.shutdown();

    try {
      if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException(
            "Timed out awaiting termination of job worker executor after 15 seconds");
      }
    } catch (InterruptedException e) {
      throw new ClientException(
          "Unexpected interrupted awaiting termination of job worker executor", e);
    }

    channel.shutdown();

    try {
      if (!channel.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException(
            "Timed out awaiting termination of in-flight request channel after 15 seconds");
      }
    } catch (InterruptedException e) {
      throw new ClientException(
          "Unexpectedly interrupted awaiting termination of in-flight request channel", e);
    }
  }

  @Override
  public DeployWorkflowCommandStep1 newDeployCommand() {
    return new DeployWorkflowCommandImpl(asyncStub);
  }

  @Override
  public CreateWorkflowInstanceCommandStep1 newCreateInstanceCommand() {
    return new CreateWorkflowInstanceCommandImpl(asyncStub, objectMapper);
  }

  @Override
  public CancelWorkflowInstanceCommandStep1 newCancelInstanceCommand(
      final long workflowInstanceKey) {
    return new CancelWorkflowInstanceCommandImpl(asyncStub, workflowInstanceKey);
  }

  @Override
  public SetVariablesCommandStep1 newSetVariablesCommand(final long elementInstanceKey) {
    return new SetVariablesCommandImpl(asyncStub, objectMapper, elementInstanceKey);
  }

  @Override
  public PublishMessageCommandStep1 newPublishMessageCommand() {
    return new PublishMessageCommandImpl(asyncStub, config, objectMapper);
  }

  @Override
  public ResolveIncidentCommandStep1 newResolveIncidentCommand(long incidentKey) {
    return new ResolveIncidentCommandImpl(asyncStub, incidentKey);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(long jobKey) {
    return new JobUpdateRetriesCommandImpl(asyncStub, jobKey);
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    return new JobWorkerBuilderImpl(
        config, asyncStub, jobClient, objectMapper, executorService, closeables);
  }

  private JobClient newJobClient() {
    return new JobClientImpl(asyncStub, config, objectMapper, executorService, closeables);
  }

  @Override
  public ActivateJobsCommandStep1 newActivateJobsCommand() {
    return new ActivateJobsCommandImpl(asyncStub, config, objectMapper);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(long jobKey) {
    return jobClient.newCompleteCommand(jobKey);
  }

  @Override
  public FailJobCommandStep1 newFailCommand(long jobKey) {
    return jobClient.newFailCommand(jobKey);
  }
}
