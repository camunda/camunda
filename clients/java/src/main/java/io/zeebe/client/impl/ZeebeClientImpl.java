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
import io.zeebe.client.api.commands.PartitionsRequestStep1;
import io.zeebe.client.api.commands.TopologyRequestStep1;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import java.net.URI;
import java.net.URISyntaxException;

public class ZeebeClientImpl implements ZeebeClient {

  private final ZeebeClientConfiguration config;
  private final GatewayStub asyncStub;

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration) {
    this.config = configuration;
    final URI address;

    try {
      address = new URI("zb://" + configuration.getBrokerContactPoint());
    } catch (final URISyntaxException e) {
      throw new RuntimeException("failed to parse broker contact point", e);
    }

    // TODO: Issue #1134 - https://github.com/zeebe-io/zeebe/issues/1134
    final ManagedChannel channel =
        ManagedChannelBuilder.forAddress(address.getHost(), address.getPort())
            .usePlaintext()
            .build();

    asyncStub = GatewayGrpc.newStub(channel);
  }

  @Override
  public ZeebeObjectMapper objectMapper() {
    return null;
  }

  @Override
  public WorkflowClient workflowClient() {
    return new WorkflowsClientImpl(asyncStub);
  }

  @Override
  public JobClient jobClient() {
    return null;
  }

  @Override
  public TopicSubscriptionBuilderStep1 newSubscription() {
    return null;
  }

  @Override
  public PartitionsRequestStep1 newPartitionsRequest() {
    return null;
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
  public void close() {}
}
