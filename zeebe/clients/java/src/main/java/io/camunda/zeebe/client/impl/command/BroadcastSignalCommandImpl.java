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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.zeebe.client.api.command.BroadcastSignalCommandStep1.BroadcastSignalCommandStep2;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.BroadcastSignalResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.BroadcastSignalResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class BroadcastSignalCommandImpl
    extends CommandWithVariables<BroadcastSignalCommandImpl>
    implements BroadcastSignalCommandStep1, BroadcastSignalCommandStep2 {

  private final GatewayStub asyncStub;
  private final Predicate<Throwable> retryPredicate;
  private final BroadcastSignalRequest.Builder builder;
  private Duration requestTimeout;

  public BroadcastSignalCommandImpl(
      final GatewayStub asyncStub,
      final ZeebeClientConfiguration configuration,
      final JsonMapper jsonMapper,
      final Predicate<Throwable> retryPredicate) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    builder = BroadcastSignalRequest.newBuilder();
    requestTimeout = configuration.getDefaultRequestTimeout();
    tenantId(configuration.getDefaultTenantId());
  }

  @Override
  protected BroadcastSignalCommandImpl setVariablesInternal(final String variables) {
    builder.setVariables(variables);
    return this;
  }

  @Override
  public BroadcastSignalCommandStep2 signalName(final String signalName) {
    builder.setSignalName(signalName);
    return this;
  }

  @Override
  public BroadcastSignalCommandStep2 tenantId(final String tenantId) {
    builder.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<BroadcastSignalResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<BroadcastSignalResponse> send() {
    final BroadcastSignalRequest request = builder.build();
    final RetriableClientFutureImpl<
            BroadcastSignalResponse, GatewayOuterClass.BroadcastSignalResponse>
        future =
            new RetriableClientFutureImpl<>(
                BroadcastSignalResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final BroadcastSignalRequest request,
      final StreamObserver<GatewayOuterClass.BroadcastSignalResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .broadcastSignal(request, streamObserver);
  }
}
