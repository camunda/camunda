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

package io.camunda.zeebe.client.impl;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.camunda.zeebe.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.zeebe.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeleteResourceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployProcessCommandStep1;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.client.impl.command.BroadcastSignalCommandImpl;
import io.camunda.zeebe.client.impl.command.CancelProcessInstanceCommandImpl;
import io.camunda.zeebe.client.impl.command.CreateProcessInstanceCommandImpl;
import io.camunda.zeebe.client.impl.command.DeleteResourceCommandImpl;
import io.camunda.zeebe.client.impl.command.DeployProcessCommandImpl;
import io.camunda.zeebe.client.impl.command.DeployResourceCommandImpl;
import io.camunda.zeebe.client.impl.command.EvaluateDecisionCommandImpl;
import io.camunda.zeebe.client.impl.command.JobUpdateRetriesCommandImpl;
import io.camunda.zeebe.client.impl.command.ModifyProcessInstanceCommandImpl;
import io.camunda.zeebe.client.impl.command.PublishMessageCommandImpl;
import io.camunda.zeebe.client.impl.command.ResolveIncidentCommandImpl;
import io.camunda.zeebe.client.impl.command.SetVariablesCommandImpl;
import io.camunda.zeebe.client.impl.command.TopologyRequestImpl;
import io.camunda.zeebe.client.impl.util.VersionUtil;
import io.camunda.zeebe.client.impl.worker.JobClientImpl;
import io.camunda.zeebe.client.impl.worker.JobWorkerBuilderImpl;
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
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ZeebeClientImpl implements ZeebeClient {
  private final ZeebeClientConfiguration config;
  private final JsonMapper jsonMapper;
  private final GatewayStub asyncStub;
  private final ManagedChannel channel;
  private final ScheduledExecutorService executorService;
  private final List<Closeable> closeables = new CopyOnWriteArrayList<>();
  private final JobClient jobClient;
  private final CredentialsProvider credentialsProvider;

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration) {
    this(configuration, buildChannel(configuration));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration configuration, final ManagedChannel channel) {
    this(configuration, channel, buildGatewayStub(channel, configuration));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration configuration,
      final ManagedChannel channel,
      final GatewayStub gatewayStub) {
    this(configuration, channel, gatewayStub, buildExecutorService(configuration));
  }

  public ZeebeClientImpl(
      final ZeebeClientConfiguration config,
      final ManagedChannel channel,
      final GatewayStub gatewayStub,
      final ScheduledExecutorService executorService) {
    this.config = config;
    jsonMapper = config.getJsonMapper();
    this.channel = channel;
    asyncStub = gatewayStub;
    this.executorService = executorService;

    if (config.getCredentialsProvider() != null) {
      credentialsProvider = config.getCredentialsProvider();
    } else {
      credentialsProvider = new NoopCredentialsProvider();
    }
    jobClient = newJobClient();
  }

  public static ManagedChannel buildChannel(final ZeebeClientConfiguration config) {
    final URI address;

    try {
      address = new URI("zb://" + config.getGatewayAddress());
    } catch (final URISyntaxException e) {
      throw new RuntimeException("Failed to parse broker contact point", e);
    }

    final NettyChannelBuilder channelBuilder =
        NettyChannelBuilder.forAddress(address.getHost(), address.getPort());

    configureConnectionSecurity(config, channelBuilder);
    channelBuilder.keepAliveTime(config.getKeepAlive().toMillis(), TimeUnit.MILLISECONDS);
    channelBuilder.userAgent("zeebe-client-java/" + VersionUtil.getVersion());
    channelBuilder.maxInboundMessageSize(config.getMaxMessageSize());
    return channelBuilder.build();
  }

  private static CallCredentials buildCallCredentials(final ZeebeClientConfiguration config) {
    final CredentialsProvider customCredentialsProvider = config.getCredentialsProvider();

    if (customCredentialsProvider == null) {
      return null;
    }

    return new ZeebeCallCredentials(customCredentialsProvider);
  }

  private static void configureConnectionSecurity(
      final ZeebeClientConfiguration config, final NettyChannelBuilder channelBuilder) {
    if (!config.isPlaintextConnectionEnabled()) {
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
      final ManagedChannel channel, final ZeebeClientConfiguration config) {
    final CallCredentials credentials = buildCallCredentials(config);
    final GatewayStub gatewayStub = GatewayGrpc.newStub(channel).withCallCredentials(credentials);
    if (!config.getInterceptors().isEmpty()) {
      return gatewayStub.withInterceptors(
          config.getInterceptors().toArray(new ClientInterceptor[] {}));
    }
    return gatewayStub;
  }

  private static ScheduledExecutorService buildExecutorService(
      final ZeebeClientConfiguration configuration) {
    final int threadCount = configuration.getNumJobWorkerExecutionThreads();
    return Executors.newScheduledThreadPool(threadCount);
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestImpl(
        asyncStub, config.getDefaultRequestTimeout(), credentialsProvider::shouldRetryRequest);
  }

  @Override
  public ZeebeClientConfiguration getConfiguration() {
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

    executorService.shutdownNow();

    try {
      if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException(
            "Timed out awaiting termination of job worker executor after 15 seconds");
      }
    } catch (final InterruptedException e) {
      throw new ClientException(
          "Unexpected interrupted awaiting termination of job worker executor", e);
    }

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
  }

  @Override
  public DeployProcessCommandStep1 newDeployCommand() {
    return new DeployProcessCommandImpl(
        asyncStub, config.getDefaultRequestTimeout(), credentialsProvider::shouldRetryRequest);
  }

  @Override
  public DeployResourceCommandStep1 newDeployResourceCommand() {
    return new DeployResourceCommandImpl(
        asyncStub, config.getDefaultRequestTimeout(), credentialsProvider::shouldRetryRequest);
  }

  @Override
  public CreateProcessInstanceCommandStep1 newCreateInstanceCommand() {
    return new CreateProcessInstanceCommandImpl(
        asyncStub,
        jsonMapper,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest);
  }

  @Override
  public ModifyProcessInstanceCommandStep1 newModifyProcessInstanceCommand(
      final long processInstanceKey) {
    return new ModifyProcessInstanceCommandImpl(
        processInstanceKey,
        jsonMapper,
        asyncStub,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest);
  }

  @Override
  public CancelProcessInstanceCommandStep1 newCancelInstanceCommand(final long processInstanceKey) {
    return new CancelProcessInstanceCommandImpl(
        asyncStub,
        processInstanceKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest);
  }

  @Override
  public SetVariablesCommandStep1 newSetVariablesCommand(final long elementInstanceKey) {
    return new SetVariablesCommandImpl(
        asyncStub,
        jsonMapper,
        elementInstanceKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest);
  }

  @Override
  public EvaluateDecisionCommandStep1 newEvaluateDecisionCommand() {
    return new EvaluateDecisionCommandImpl(
        asyncStub,
        jsonMapper,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest);
  }

  @Override
  public PublishMessageCommandStep1 newPublishMessageCommand() {
    return new PublishMessageCommandImpl(
        asyncStub, config, jsonMapper, credentialsProvider::shouldRetryRequest);
  }

  @Override
  public BroadcastSignalCommandStep1 newBroadcastSignalCommand() {
    return new BroadcastSignalCommandImpl(
        asyncStub, config, jsonMapper, credentialsProvider::shouldRetryRequest);
  }

  @Override
  public ResolveIncidentCommandStep1 newResolveIncidentCommand(final long incidentKey) {
    return new ResolveIncidentCommandImpl(
        asyncStub,
        incidentKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final long jobKey) {
    return new JobUpdateRetriesCommandImpl(
        asyncStub,
        jobKey,
        config.getDefaultRequestTimeout(),
        credentialsProvider::shouldRetryRequest);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(final ActivatedJob job) {
    return newUpdateRetriesCommand(job.getKey());
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    return new JobWorkerBuilderImpl(config, jobClient, executorService, closeables);
  }

  @Override
  public ActivateJobsCommandStep1 newActivateJobsCommand() {
    return jobClient.newActivateJobsCommand();
  }

  @Override
  public DeleteResourceCommandStep1 newDeleteResourceCommand(final long resourceKey) {
    return new DeleteResourceCommandImpl(
        resourceKey,
        asyncStub,
        credentialsProvider::shouldRetryRequest,
        config.getDefaultRequestTimeout());
  }

  private JobClient newJobClient() {
    return new JobClientImpl(
        asyncStub, config, jsonMapper, credentialsProvider::shouldRetryRequest);
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
}
