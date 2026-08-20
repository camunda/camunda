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
package io.camunda.client.impl.command;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1;
import io.camunda.client.api.command.EvaluateDecisionCommandStep1.EvaluateDecisionCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.EvaluateDecisionResponseImpl;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.DecisionEvaluationInstruction;
import io.camunda.client.protocol.rest.EvaluateDecisionResult;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

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
      final CamundaClientConfiguration config,
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
  public FinalCommandStep<EvaluateDecisionResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<EvaluateDecisionResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<EvaluateDecisionResponse> sendRestRequest() {
    final HttpCamundaFuture<EvaluateDecisionResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/decision-definitions/evaluation",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        EvaluateDecisionResult.class,
        response -> new EvaluateDecisionResponseImpl(response, jsonMapper),
        result);
    return result;
  }

  private CamundaFuture<EvaluateDecisionResponse> sendGrpcRequest() {
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
