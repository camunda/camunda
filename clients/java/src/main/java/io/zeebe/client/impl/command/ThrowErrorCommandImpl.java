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
import io.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.zeebe.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ThrowErrorCommandImpl implements ThrowErrorCommandStep1, ThrowErrorCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;

  public ThrowErrorCommandImpl(
      GatewayStub asyncStub,
      long key,
      Duration requestTimeout,
      Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = ThrowErrorRequest.newBuilder();
    builder.setJobKey(key);
  }

  @Override
  public ThrowErrorCommandStep2 errorCode(String errorCode) {
    builder.setErrorCode(errorCode);
    return this;
  }

  @Override
  public ThrowErrorCommandStep2 errorMessage(String errorMsg) {
    builder.setErrorMessage(errorMsg);
    return this;
  }

  @Override
  public FinalCommandStep<Void> requestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<Void> send() {
    final ThrowErrorRequest request = builder.build();

    final RetriableClientFutureImpl<Void, ThrowErrorResponse> future =
        new RetriableClientFutureImpl<>(
            retryPredicate, streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(ThrowErrorRequest request, StreamObserver<ThrowErrorResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .throwError(request, streamObserver);
  }
}
