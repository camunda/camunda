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
package io.zeebe.client.impl.command;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.command.TopologyRequestStep1;
import io.zeebe.client.api.response.Topology;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.response.TopologyImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TopologyRequestImpl implements TopologyRequestStep1 {

  private final GatewayStub asyncStub;
  private Duration requestTimeout;

  public TopologyRequestImpl(final GatewayStub asyncStub, Duration requestTimeout) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
  }

  @Override
  public FinalCommandStep<Topology> requestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<Topology> send() {
    final TopologyRequest request = TopologyRequest.getDefaultInstance();

    final ZeebeClientFutureImpl<Topology, TopologyResponse> future =
        new ZeebeClientFutureImpl<>(TopologyImpl::new);

    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .topology(request, future);

    return future;
  }
}
