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
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.EvaluateDecisionResponseImpl;
import io.camunda.zeebe.client.impl.util.ParseUtil;
import io.camunda.zeebe.client.protocol.rest.DecisionEvaluationInstruction;
import io.camunda.zeebe.client.protocol.rest.EvaluateDecisionResult;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.impl.command.EvaluateDecisionCommandImpl}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class EvaluateDecisionCommandImpl extends CommandWithVariables<EvaluateDecisionCommandImpl>
    implements EvaluateDecisionCommandStep1, EvaluateDecisionCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder grpcRequestObjectBuilder;
  private final Predicate<StatusCode> retryPredicate;
  private final JsonMapper jsonMapper;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final DecisionEvaluationInstruction httpRequestObject;
  private boolean useRest;

  public EvaluateDecisionCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final ZeebeClientConfiguration config,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    requestTimeout = config.getDefaultRequestTimeout();
    this.retryPredicate = retryPredicate;
    this.jsonMapper = jsonMapper;
    grpcRequestObjectBuilder = EvaluateDecisionRequest.newBuilder();
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new DecisionEvaluationInstruction();
    useRest = config.preferRestOverGrpc();
    tenantId(config.getDefaultTenantId());
    requestTimeout(requestTimeout);
  }

  /**
   * A constructor that provides an instance with the <code><default></code> tenantId set.
   *
   * <p>From version 8.3.0, the java client supports multi-tenancy for this command, which requires
   * the <code>tenantId</code> property to be defined. This constructor is only intended for
   * backwards compatibility in tests.
   *
   * @deprecated since 8.3.0, use {@link
   *     EvaluateDecisionCommandImpl#EvaluateDecisionCommandImpl(GatewayStub, JsonMapper ,
   *     ZeebeClientConfiguration, Predicate, HttpClient)}
   */
  @Deprecated
  public EvaluateDecisionCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    this.jsonMapper = jsonMapper;
    this.requestTimeout = requestTimeout;
    grpcRequestObjectBuilder = EvaluateDecisionRequest.newBuilder();
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new DecisionEvaluationInstruction();
    tenantId(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
    requestTimeout(requestTimeout);
  }

  @Override
  protected EvaluateDecisionCommandImpl setVariablesInternal(final String variables) {
    grpcRequestObjectBuilder.setVariables(variables);
    // This check is mandatory. Without it, gRPC requests can fail unnecessarily.
    // gRPC and REST handle setting variables differently:
    // - For gRPC commands, we only check if the JSON is valid and forward it to the engine.
    //    The engine checks if the provided String can be transformed into a Map, if not it
    //    throws an error.
    // - For REST commands, users have to provide a valid JSON Object String.
    //    Otherwise, the client throws an exception already.
    if (useRest) {
      httpRequestObject.setVariables(jsonMapper.fromJsonAsMap(variables));
    }
    return this;
  }

  @Override
  public EvaluateDecisionCommandStep2 decisionId(final String decisionId) {
    grpcRequestObjectBuilder.setDecisionId(decisionId);
    httpRequestObject.setDecisionDefinitionId(decisionId);
    return this;
  }

  @Override
  public EvaluateDecisionCommandStep2 decisionKey(final long decisionKey) {
    grpcRequestObjectBuilder.setDecisionKey(decisionKey);
    httpRequestObject.setDecisionDefinitionKey(ParseUtil.keyToString(decisionKey));
    return this;
  }

  @Override
  public EvaluateDecisionCommandStep2 version(final int version) {
    grpcRequestObjectBuilder.setDecisionVersion(version);
    httpRequestObject.setDecisionDefinitionVersion(version);
    return this;
  }

  @Override
  public EvaluateDecisionCommandStep2 latestVersion() {
    return version(-1);
  }

  @Override
  public FinalCommandStep<EvaluateDecisionResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<EvaluateDecisionResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private ZeebeFuture<EvaluateDecisionResponse> sendRestRequest() {
    final HttpZeebeFuture<EvaluateDecisionResponse> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/decision-definitions/evaluation",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        EvaluateDecisionResult.class,
        response -> new EvaluateDecisionResponseImpl(response, jsonMapper),
        result);
    return result;
  }

  private ZeebeFuture<EvaluateDecisionResponse> sendGrpcRequest() {
    final EvaluateDecisionRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<
            EvaluateDecisionResponse, GatewayOuterClass.EvaluateDecisionResponse>
        future =
            new RetriableClientFutureImpl<>(
                gatewayResponse -> new EvaluateDecisionResponseImpl(jsonMapper, gatewayResponse),
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);

    return future;
  }

  @Override
  public EvaluateDecisionCommandStep2 tenantId(final String tenantId) {
    grpcRequestObjectBuilder.setTenantId(tenantId);
    httpRequestObject.setTenantId(tenantId);
    return this;
  }

  private void sendGrpcRequest(
      final EvaluateDecisionRequest request,
      final StreamObserver<GatewayOuterClass.EvaluateDecisionResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .evaluateDecision(request, streamObserver);
  }

  @Override
  public EvaluateDecisionCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public EvaluateDecisionCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
