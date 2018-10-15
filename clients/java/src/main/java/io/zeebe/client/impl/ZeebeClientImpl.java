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
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.commands.TopologyRequestStep1;
import io.zeebe.client.cmd.ClientException;
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
  }

  public static ManagedChannel buildChannel(ZeebeClientConfiguration config) {
    final URI address;

    try {
      address = new URI("zb://" + config.getBrokerContactPoint());
    } catch (final URISyntaxException e) {
      throw new RuntimeException("failed to parse broker contact point", e);
    }

    // TODO: Issue #1134 - https://github.com/zeebe-io/zeebe/issues/1134
    return ManagedChannelBuilder.forAddress(address.getHost(), address.getPort())
        .usePlaintext()
        .build();
  }

  private static ScheduledExecutorService buildExecutorService(
      ZeebeClientConfiguration configuration) {
    final int threadCount = configuration.getNumSubscriptionExecutionThreads();
    return Executors.newScheduledThreadPool(threadCount);
  }

  @Override
  public WorkflowClient workflowClient() {
    return new WorkflowsClientImpl(asyncStub, config, objectMapper);
  }

  @Override
  public JobClient jobClient() {
    return new JobClientImpl(asyncStub, config, objectMapper, executorService, closeables);
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
        throw new ClientException("Failed to await termination of job worker executor");
      }
    } catch (InterruptedException e) {
      throw new ClientException("Failed to await termination of job worker exectuor", e);
    }

    channel.shutdown();

    try {
      if (!channel.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new ClientException("Failed to await termination of in-flight requests");
      }
    } catch (InterruptedException e) {
      throw new ClientException("Failed to await termination of in-flight requests", e);
    }
  }
}
