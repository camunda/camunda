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
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.command.PublishMessageCommandStep1;
import io.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class PublishMessageCommandImpl extends CommandWithVariables<PublishMessageCommandImpl>
    implements PublishMessageCommandStep1, PublishMessageCommandStep2, PublishMessageCommandStep3 {

  private final GatewayStub asyncStub;
  private final Predicate<Throwable> retryPredicate;
  private final PublishMessageRequest.Builder builder;
  private Duration requestTimeout;

  public PublishMessageCommandImpl(
      final GatewayStub asyncStub,
      final ZeebeClientConfiguration configuration,
      ZeebeObjectMapper objectMapper,
      Predicate<Throwable> retryPredicate) {
    super(objectMapper);
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    this.builder = PublishMessageRequest.newBuilder();
    this.requestTimeout = configuration.getDefaultRequestTimeout();
    builder.setTimeToLive(configuration.getDefaultMessageTimeToLive().toMillis());
  }

  @Override
  protected PublishMessageCommandImpl setVariablesInternal(String variables) {
    builder.setVariables(variables);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 messageId(final String messageId) {
    builder.setMessageId(messageId);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 timeToLive(final Duration timeToLive) {
    builder.setTimeToLive(timeToLive.toMillis());
    return this;
  }

  @Override
  public PublishMessageCommandStep3 correlationKey(final String correlationKey) {
    builder.setCorrelationKey(correlationKey);
    return this;
  }

  @Override
  public PublishMessageCommandStep2 messageName(final String messageName) {
    builder.setName(messageName);
    return this;
  }

  @Override
  public FinalCommandStep<Void> requestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<Void> send() {
    final PublishMessageRequest request = builder.build();
    final RetriableClientFutureImpl<Void, PublishMessageResponse> future =
        new RetriableClientFutureImpl<>(
            retryPredicate, streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      PublishMessageRequest request, StreamObserver<PublishMessageResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .publishMessage(request, streamObserver);
  }
}
