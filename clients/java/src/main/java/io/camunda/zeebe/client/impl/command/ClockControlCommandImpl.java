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

import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ClockControlCommandStep1;
import io.camunda.zeebe.client.api.command.ClockControlCommandStep1.ClockControlCommandStep2;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ClockControlResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.ClockControlResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ClockControlRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ClockControlRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ClockControlCommandImpl
    implements ClockControlCommandStep1, ClockControlCommandStep2 {

  private static final Duration DEADLINE_OFFSET = Duration.ofSeconds(10);
  private final GatewayStub asyncStub;

  private final Predicate<StatusCode> retryPredicate;
  private final Builder grpcRequestObjectBuilder;
  private Duration requestTimeout;

  private final ClockControlRequest.Builder builder;

  public ClockControlCommandImpl(
      final GatewayStub asyncStub,
      final ZeebeClientConfiguration config,
      final Predicate<StatusCode> retryPredicate) {
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = ClockControlRequest.newBuilder();
    requestTimeout(config.getDefaultRequestTimeout());
    builder = ClockControlRequest.newBuilder();
  }

  @Override
  public ClockControlCommandStep1 time(final long time) {
    builder.setTime(time);
    return this;
  }

  @Override
  public ClockControlCommandStep2 tenantId(final String tenantId) {
    builder.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<ClockControlResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<ClockControlResponse> send() {
    /** TODO: evaluate opportunity to expose rest endpoint */
    return sendGrpcRequest();
  }

  private ZeebeFuture<ClockControlResponse> sendGrpcRequest() {
    final ClockControlRequest request = grpcRequestObjectBuilder.build();
    return new RetriableClientFutureImpl<>(
        ClockControlResponseImpl::new,
        retryPredicate,
        streamObserver -> sendGrpc(request, streamObserver));
  }

  private void sendGrpc(
      final ClockControlRequest request,
      final StreamObserver<GatewayOuterClass.ClockControlResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.plus(DEADLINE_OFFSET).toMillis(), TimeUnit.MILLISECONDS)
        .clockControl(request, future);
  }
}
