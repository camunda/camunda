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

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ThrowErrorCommandImpl implements ThrowErrorCommandStep1, ThrowErrorCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;

  public ThrowErrorCommandImpl(
      final GatewayStub asyncStub,
      final long key,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = ThrowErrorRequest.newBuilder();
    builder.setJobKey(key);
  }

  @Override
  public ThrowErrorCommandStep2 errorCode(final String errorCode) {
    builder.setErrorCode(errorCode);
    return this;
  }

  @Override
  public ThrowErrorCommandStep2 errorMessage(final String errorMsg) {
    builder.setErrorMessage(errorMsg);
    return this;
  }

  @Override
  public ThrowErrorCommandStep2 tenantId(final String tenantId) {
    builder.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<Void> requestTimeout(final Duration requestTimeout) {
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

  private void send(
      final ThrowErrorRequest request, final StreamObserver<ThrowErrorResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .throwError(request, streamObserver);
  }
}
