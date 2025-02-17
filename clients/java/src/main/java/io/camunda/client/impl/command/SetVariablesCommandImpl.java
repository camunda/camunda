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

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.SetVariablesCommandStep1;
import io.camunda.client.api.command.SetVariablesCommandStep1.SetVariablesCommandStep2;
import io.camunda.client.api.response.SetVariablesResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.SetVariablesResponseImpl;
import io.camunda.client.protocol.rest.SetVariableRequest;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class SetVariablesCommandImpl
    implements SetVariablesCommandStep1, SetVariablesCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder grpcRequestObjectBuilder;
  private final JsonMapper jsonMapper;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private boolean useRest;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final SetVariableRequest httpRequestObject = new SetVariableRequest();
  private final long elementInstanceKey;

  public SetVariablesCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final long elementInstanceKey,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc) {
    this.asyncStub = asyncStub;
    this.jsonMapper = jsonMapper;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = SetVariablesRequest.newBuilder();
    grpcRequestObjectBuilder.setElementInstanceKey(elementInstanceKey);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    useRest = preferRestOverGrpc;
    this.elementInstanceKey = elementInstanceKey;
    requestTimeout(requestTimeout);
  }

  @Override
  public FinalCommandStep<SetVariablesResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SetVariablesResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<SetVariablesResponse> sendRestRequest() {
    final HttpCamundaFuture<SetVariablesResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/element-instances/" + elementInstanceKey + "/variables",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        result);
    return result;
  }

  private CamundaFuture<SetVariablesResponse> sendGrpcRequest() {
    final SetVariablesRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<SetVariablesResponse, GatewayOuterClass.SetVariablesResponse>
        future =
            new RetriableClientFutureImpl<>(
                SetVariablesResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final SetVariablesRequest request,
      final StreamObserver<GatewayOuterClass.SetVariablesResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .setVariables(request, streamObserver);
  }

  @Override
  public SetVariablesCommandStep2 local(final boolean local) {
    grpcRequestObjectBuilder.setLocal(local);
    httpRequestObject.setLocal(local);
    return this;
  }

  @Override
  public SetVariablesCommandStep2 variables(final InputStream variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.validateJson("variables", variables));
  }

  @Override
  public SetVariablesCommandStep2 variables(final String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.validateJson("variables", variables));
  }

  @Override
  public SetVariablesCommandStep2 variables(final Map<String, Object> variables) {
    return variables((Object) variables);
  }

  @Override
  public SetVariablesCommandStep2 variables(final Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.toJson(variables));
  }

  private SetVariablesCommandStep2 setVariables(final String jsonDocument) {
    grpcRequestObjectBuilder.setVariables(jsonDocument);
    // This check is mandatory. Without it, gRPC requests can fail unnecessarily.
    // gRPC and REST handle setting variables differently:
    // - For gRPC commands, we only check if the JSON is valid and forward it to the engine.
    //    The engine checks if the provided String can be transformed into a Map, if not it
    //    throws an error.
    // - For REST commands, users have to provide a valid JSON Object String.
    //    Otherwise, the client throws an exception already.
    if (useRest) {
      httpRequestObject.setVariables(jsonMapper.fromJsonAsMap(jsonDocument));
    }
    return this;
  }

  @Override
  public SetVariablesCommandStep2 operationReference(final long operationReference) {
    grpcRequestObjectBuilder.setOperationReference(operationReference);
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public SetVariablesCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public SetVariablesCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
