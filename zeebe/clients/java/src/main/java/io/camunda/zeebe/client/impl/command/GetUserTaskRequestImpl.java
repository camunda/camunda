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
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.GetUserTaskRequestStep1;
import io.camunda.zeebe.client.api.response.GetUserTaskResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.GetUserTaskResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.GetEntityRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.GetEntityRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class GetUserTaskRequestImpl implements GetUserTaskRequestStep1 {

  private final GatewayStub asyncStub;
  private final JsonMapper jsonMapper;
  private final Builder builder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;

  public GetUserTaskRequestImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final long key,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate) {
    this.asyncStub = asyncStub;
    this.jsonMapper = jsonMapper;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = GetEntityRequest.newBuilder();
    builder.setKey(key);
  }

  @Override
  public FinalCommandStep<GetUserTaskResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<GetUserTaskResponse> send() {
    final GetEntityRequest request = builder.build();

    final RetriableClientFutureImpl<GetUserTaskResponse, GatewayOuterClass.GetEntityResponse>
        future =
            new RetriableClientFutureImpl<>(
                response -> new GetUserTaskResponseImpl(jsonMapper, response),
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final GetEntityRequest request,
      final StreamObserver<GatewayOuterClass.GetEntityResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .getEntity(request, streamObserver);
  }
}
