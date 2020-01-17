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

import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.GatewayVersionRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.GatewayVersionResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class GatewayVersionCommandImpl implements FinalCommandStep<String> {

  private final GatewayStub asyncStub;
  private final Predicate<Throwable> retryPredicate;
  private Duration timeout;
  private GatewayVersionRequest.Builder builder;

  public GatewayVersionCommandImpl(
      GatewayStub asyncStub, Duration defaultTimeout, Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.timeout = defaultTimeout;
    this.retryPredicate = retryPredicate;
    this.builder = GatewayVersionRequest.newBuilder();
  }

  @Override
  public FinalCommandStep<String> requestTimeout(Duration requestTimeout) {
    timeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<String> send() {
    final GatewayVersionRequest request = builder.build();

    final RetriableClientFutureImpl<String, GatewayVersionResponse> future =
        new RetriableClientFutureImpl<>(
            GatewayVersionResponse::getVersion,
            retryPredicate,
            streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final GatewayVersionRequest request, final StreamObserver<GatewayVersionResponse> future) {
    asyncStub
        .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
        .gatewayVersion(request, future);
  }
}
