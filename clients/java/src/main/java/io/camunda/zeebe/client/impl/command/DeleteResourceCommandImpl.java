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
import io.camunda.zeebe.client.api.command.DeleteResourceCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.DeleteResourceResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.DeleteResourceResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class DeleteResourceCommandImpl implements DeleteResourceCommandStep1 {

  private final DeleteResourceRequest.Builder requestBuilder = DeleteResourceRequest.newBuilder();
  private final GatewayStub asyncStub;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;

  public DeleteResourceCommandImpl(
      final long resourceKey,
      final GatewayStub asyncStub,
      final Predicate<Throwable> retryPredicate,
      final Duration requestTimeout) {
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    requestBuilder.setResourceKey(resourceKey);
    this.requestTimeout = requestTimeout;
  }

  @Override
  public FinalCommandStep<DeleteResourceResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<DeleteResourceResponse> send() {
    final DeleteResourceRequest request = requestBuilder.build();

    final RetriableClientFutureImpl<
            DeleteResourceResponse, GatewayOuterClass.DeleteResourceResponse>
        future =
            new RetriableClientFutureImpl<>(
                DeleteResourceResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final DeleteResourceRequest request,
      final StreamObserver<GatewayOuterClass.DeleteResourceResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .deleteResource(request, streamObserver);
  }
}
