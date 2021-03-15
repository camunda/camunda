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
import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceWithResultCommandStep1;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.response.ProcessInstanceResult;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.client.impl.response.CreateProcessInstanceWithResultResponseImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest.Builder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class CreateProcessInstanceWithResultCommandImpl
    implements CreateProcessInstanceWithResultCommandStep1 {

  private static final Duration DEADLINE_OFFSET = Duration.ofSeconds(10);
  private final JsonMapper jsonMapper;
  private final GatewayStub asyncStub;
  private final CreateProcessInstanceRequest.Builder createProcessInstanceRequestBuilder;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;

  public CreateProcessInstanceWithResultCommandImpl(
      final JsonMapper jsonMapper,
      final GatewayStub asyncStub,
      final CreateProcessInstanceRequest.Builder builder,
      final Predicate<Throwable> retryPredicate,
      final Duration requestTimeout) {
    this.jsonMapper = jsonMapper;
    this.asyncStub = asyncStub;
    createProcessInstanceRequestBuilder = builder;
    this.retryPredicate = retryPredicate;
    this.requestTimeout = requestTimeout;
    this.builder = CreateProcessInstanceWithResultRequest.newBuilder();
  }

  @Override
  public FinalCommandStep<ProcessInstanceResult> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    builder.setRequestTimeout(requestTimeout.toMillis());
    return this;
  }

  @Override
  public ZeebeFuture<ProcessInstanceResult> send() {
    final CreateProcessInstanceWithResultRequest request =
        builder
            .setRequest(createProcessInstanceRequestBuilder)
            .setRequestTimeout(requestTimeout.toMillis())
            .build();

    final RetriableClientFutureImpl<
            ProcessInstanceResult, GatewayOuterClass.CreateProcessInstanceWithResultResponse>
        future =
            new RetriableClientFutureImpl<>(
                response -> new CreateProcessInstanceWithResultResponseImpl(jsonMapper, response),
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final CreateProcessInstanceWithResultRequest request,
      final StreamObserver<GatewayOuterClass.CreateProcessInstanceWithResultResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.plus(DEADLINE_OFFSET).toMillis(), TimeUnit.MILLISECONDS)
        .createProcessInstanceWithResult(request, future);
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 fetchVariables(
      final List<String> fetchVariables) {
    builder.addAllFetchVariables(fetchVariables);
    return this;
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 fetchVariables(
      final String... fetchVariables) {
    builder.addAllFetchVariables(Arrays.asList(fetchVariables));
    return this;
  }
}
