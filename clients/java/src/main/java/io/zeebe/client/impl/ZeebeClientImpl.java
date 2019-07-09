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
import io.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.zeebe.client.api.command.CancelWorkflowInstanceCommandStep1;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.command.CompleteJobCommandStep1;
import io.zeebe.client.api.command.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.command.DeployWorkflowCommandStep1;
import io.zeebe.client.api.command.FailJobCommandStep1;
import io.zeebe.client.api.command.PublishMessageCommandStep1;
import io.zeebe.client.api.command.ResolveIncidentCommandStep1;
import io.zeebe.client.api.command.SetVariablesCommandStep1;
import io.zeebe.client.api.command.TopologyRequestStep1;
import io.zeebe.client.api.command.UpdateRetriesJobCommandStep1;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.zeebe.client.impl.command.ActivateJobsCommandImpl;
import io.zeebe.client.impl.command.CancelWorkflowInstanceCommandImpl;
import io.zeebe.client.impl.command.CreateWorkflowInstanceCommandImpl;
import io.zeebe.client.impl.command.DeployWorkflowCommandImpl;
import io.zeebe.client.impl.command.JobUpdateRetriesCommandImpl;
import io.zeebe.client.impl.command.PublishMessageCommandImpl;
import io.zeebe.client.impl.command.ResolveIncidentCommandImpl;
import io.zeebe.client.impl.command.SetVariablesCommandImpl;
import io.zeebe.client.impl.command.TopologyRequestImpl;
import io.zeebe.client.impl.worker.JobClientImpl;
import io.zeebe.client.impl.worker.JobWorkerBuilderImpl;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import java.io.Closeable;
import java.io.IOException;
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
  private final List<Closeable> closeables = new CopyOnWriteArrayList<>();
  private final JobClient jobClient;

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration) {
    this(configuration, buildChannel(configuration));
  }

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration, ManagedChannel channel) {
    this(configuration, channel, buildGatewayStub(channel));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration configuration,
      ManagedChannel channel,
      GatewayStub gatewayStub) {
    this(configuration, channel, gatewayStub, buildExecutorService(configuration));
  }

  public ZeebeClientImpl(
      ZeebeClientConfiguration config,
      ManagedChannel channel,
      GatewayStub gatewayStub,
      ScheduledExecutorService executorService) {
    this.config = config;
    this.objectMapper = new ZeebeObjectMapper();
    this.channel = channel;
    this.asyncStub = gatewayStub;
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

  public static GatewayStub buildGatewayStub(ManagedChannel channel) {
    return GatewayGrpc.newStub(channel);
  }

  private static ScheduledExecutorService buildExecutorService(
      ZeebeClientConfiguration configuration) {
    final int threadCount = configuration.getNumJobWorkerExecutionThreads();
    return Executors.newScheduledThreadPool(threadCount);
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestImpl(asyncStub, config.getDefaultRequestTimeout());
  }

  @Override
  public ZeebeClientConfiguration getConfiguration() {
    return this.config;
  }

  @Override
  public void close() {
    closeables.forEach(
        c -> {
          try {
            c.close();
          } catch (IOException e) {
            // ignore
          }
        });

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
    return new DeployWorkflowCommandImpl(asyncStub, config.getDefaultRequestTimeout());
  }

  @Override
  public CreateWorkflowInstanceCommandStep1 newCreateInstanceCommand() {
    return new CreateWorkflowInstanceCommandImpl(
        asyncStub, objectMapper, config.getDefaultRequestTimeout());
  }

  @Override
  public CancelWorkflowInstanceCommandStep1 newCancelInstanceCommand(
      final long workflowInstanceKey) {
    return new CancelWorkflowInstanceCommandImpl(
        asyncStub, workflowInstanceKey, config.getDefaultRequestTimeout());
  }

  @Override
  public SetVariablesCommandStep1 newSetVariablesCommand(final long elementInstanceKey) {
    return new SetVariablesCommandImpl(
        asyncStub, objectMapper, elementInstanceKey, config.getDefaultRequestTimeout());
  }

  @Override
  public PublishMessageCommandStep1 newPublishMessageCommand() {
    return new PublishMessageCommandImpl(asyncStub, config, objectMapper);
  }

  @Override
  public ResolveIncidentCommandStep1 newResolveIncidentCommand(long incidentKey) {
    return new ResolveIncidentCommandImpl(
        asyncStub, incidentKey, config.getDefaultRequestTimeout());
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(long jobKey) {
    return new JobUpdateRetriesCommandImpl(asyncStub, jobKey, config.getDefaultRequestTimeout());
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    return new JobWorkerBuilderImpl(
        config, asyncStub, jobClient, objectMapper, executorService, closeables);
  }

  private JobClient newJobClient() {
    return new JobClientImpl(asyncStub, config, objectMapper);
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
