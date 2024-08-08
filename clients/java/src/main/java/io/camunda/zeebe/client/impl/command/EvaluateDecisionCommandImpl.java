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
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CommandWithTenantStep;
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
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class EvaluateDecisionCommandImpl extends CommandWithVariables<EvaluateDecisionCommandImpl>
    implements EvaluateDecisionCommandStep1, EvaluateDecisionCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<StatusCode> retryPredicate;
  private final JsonMapper jsonMapper;
  private Duration requestTimeout;

  public EvaluateDecisionCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final ZeebeClientConfiguration config,
      final Predicate<StatusCode> retryPredicate) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    requestTimeout = config.getDefaultRequestTimeout();
    this.retryPredicate = retryPredicate;
    this.jsonMapper = jsonMapper;
    builder = EvaluateDecisionRequest.newBuilder();
    tenantId(config.getDefaultTenantId());
  }

  /**
   * A constructor that provides an instance with the <code><default></code> tenantId set.
   *
   * <p>From version 8.3.0, the java client supports multi-tenancy for this command, which requires
   * the <code>tenantId</code> property to be defined. This constructor is only intended for
   * backwards compatibility in tests.
   *
   * @deprecated since 8.3.0, use {@link
   *     EvaluateDecisionCommandImpl#EvaluateDecisionCommandImpl(GatewayStub asyncStub, JsonMapper
   *     jsonMapper, ZeebeClientConfiguration config, Predicate retryPredicate)}
   */
  public EvaluateDecisionCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    this.jsonMapper = jsonMapper;
    this.requestTimeout = requestTimeout;
    builder = EvaluateDecisionRequest.newBuilder();
    tenantId(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  @Override
  protected EvaluateDecisionCommandImpl setVariablesInternal(final String variables) {
    builder.setVariables(variables);
    return this;
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

  @Override
  public EvaluateDecisionCommandStep2 tenantId(final String tenantId) {
    builder.setTenantId(tenantId);
    return this;
  }

  private void send(
      final EvaluateDecisionRequest request,
      final StreamObserver<GatewayOuterClass.EvaluateDecisionResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .evaluateDecision(request, streamObserver);
  }
}
