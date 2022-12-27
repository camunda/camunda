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

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.zeebe.client.api.command.EvaluateDecisionCommandStep1.EvaluateDecisionCommandStep2;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.EvaluateDecisionResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.EvaluateDecisionResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class EvaluateDecisionCommandImpl
    implements EvaluateDecisionCommandStep1, EvaluateDecisionCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private final JsonMapper jsonMapper;
  private Duration requestTimeout;

  public EvaluateDecisionCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    this.jsonMapper = jsonMapper;
    this.requestTimeout = requestTimeout;
    builder = EvaluateDecisionRequest.newBuilder();
  }

  @Override
  public EvaluateDecisionCommandStep2 decisionId(final String decisionId) {
    builder.setDecisionId(decisionId);
    return this;
  }

  @Override
  public EvaluateDecisionCommandStep2 decisionKey(final long decisionKey) {
    builder.setDecisionKey(decisionKey);
    return this;
  }

  @Override
  public EvaluateDecisionCommandStep2 variables(final InputStream variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.validateJson("variables", variables));
  }

  @Override
  public EvaluateDecisionCommandStep2 variables(final String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(variables);
  }

  @Override
  public EvaluateDecisionCommandStep2 variables(final Map<String, Object> variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.toJson(variables));
  }

  @Override
  public EvaluateDecisionCommandStep2 variables(final Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.toJson(variables));
  }

  @Override
  public FinalCommandStep<EvaluateDecisionResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<EvaluateDecisionResponse> send() {
    final EvaluateDecisionRequest request = builder.build();

    final RetriableClientFutureImpl<
            EvaluateDecisionResponse, GatewayOuterClass.EvaluateDecisionResponse>
        future =
            new RetriableClientFutureImpl<>(
                gatewayResponse -> new EvaluateDecisionResponseImpl(jsonMapper, gatewayResponse),
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);

    return future;
  }

  private void send(
      final EvaluateDecisionRequest request,
      final StreamObserver<GatewayOuterClass.EvaluateDecisionResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .evaluateDecision(request, streamObserver);
  }

  private EvaluateDecisionCommandStep2 setVariables(final String jsonDocument) {
    builder.setVariables(jsonDocument);
    return this;
  }
}
