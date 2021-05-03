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
import io.zeebe.client.api.command.FailJobCommandStep1;
import io.zeebe.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.response.FailJobResponse;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest.Builder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class FailJobCommandImpl implements FailJobCommandStep1, FailJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;

  public FailJobCommandImpl(
      final GatewayStub asyncStub,
      final long key,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = FailJobRequest.newBuilder();
    builder.setJobKey(key);
  }

  @Override
  public FailJobCommandStep2 retries(final int retries) {
    builder.setRetries(retries);
    return this;
  }

  @Override
  public FailJobCommandStep2 errorMessage(final String errorMsg) {
    builder.setErrorMessage(errorMsg);
    return this;
  }

  @Override
  public FinalCommandStep<FailJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<FailJobResponse> send() {
    final FailJobRequest request = builder.build();

    final RetriableClientFutureImpl<FailJobResponse, GatewayOuterClass.FailJobResponse> future =
        new RetriableClientFutureImpl<>(
            retryPredicate, streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final FailJobRequest request,
      final StreamObserver<GatewayOuterClass.FailJobResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .failJob(request, streamObserver);
  }
}
