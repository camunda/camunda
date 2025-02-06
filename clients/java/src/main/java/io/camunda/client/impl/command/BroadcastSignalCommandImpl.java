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
import io.camunda.client.api.command.BroadcastSignalCommandStep1;
import io.camunda.client.api.command.BroadcastSignalCommandStep1.BroadcastSignalCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.BroadcastSignalResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.BroadcastSignalResponseImpl;
import io.camunda.client.protocol.rest.SignalBroadcastRequest;
import io.camunda.client.protocol.rest.SignalBroadcastResult;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class BroadcastSignalCommandImpl
    extends CommandWithVariables<BroadcastSignalCommandImpl>
    implements BroadcastSignalCommandStep1, BroadcastSignalCommandStep2 {

  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private final BroadcastSignalRequest.Builder grpcRequestObjectBuilder;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final SignalBroadcastRequest httpRequestObject;
  private boolean useRest;

  public BroadcastSignalCommandImpl(
      final GatewayStub asyncStub,
      final CamundaClientConfiguration config,
      final JsonMapper jsonMapper,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = BroadcastSignalRequest.newBuilder();
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new SignalBroadcastRequest();
    useRest = config.preferRestOverGrpc();
    tenantId(config.getDefaultTenantId());
    requestTimeout(config.getDefaultRequestTimeout());
  }

  @Override
  protected BroadcastSignalCommandImpl setVariablesInternal(final String variables) {
    grpcRequestObjectBuilder.setVariables(variables);
    // This check is mandatory. Without it, gRPC requests can fail unnecessarily.
    // gRPC and REST handle setting variables differently:
    // - For gRPC commands, we only check if the JSON is valid and forward it to the engine.
    //    The engine checks if the provided String can be transformed into a Map, if not it
    //    throws an error.
    // - For REST commands, users have to provide a valid JSON Object String.
    //    Otherwise, the client throws an exception already.
    if (useRest) {
      httpRequestObject.setVariables(objectMapper.fromJsonAsMap(variables));
    }
    return this;
  }

  @Override
  public BroadcastSignalCommandStep2 signalName(final String signalName) {
    grpcRequestObjectBuilder.setSignalName(signalName);
    httpRequestObject.setSignalName(signalName);
    return this;
  }

  @Override
  public BroadcastSignalCommandStep2 tenantId(final String tenantId) {
    grpcRequestObjectBuilder.setTenantId(tenantId);
    httpRequestObject.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<BroadcastSignalResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<BroadcastSignalResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<BroadcastSignalResponse> sendRestRequest() {
    final HttpCamundaFuture<BroadcastSignalResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/signals/broadcast",
        objectMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        SignalBroadcastResult.class,
        BroadcastSignalResponseImpl::new,
        result);
    return result;
  }

  public CamundaFuture<BroadcastSignalResponse> sendGrpcRequest() {
    final BroadcastSignalRequest request = grpcRequestObjectBuilder.build();
    final RetriableClientFutureImpl<
            BroadcastSignalResponse, GatewayOuterClass.BroadcastSignalResponse>
        future =
            new RetriableClientFutureImpl<>(
                BroadcastSignalResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final BroadcastSignalRequest request,
      final StreamObserver<GatewayOuterClass.BroadcastSignalResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .broadcastSignal(request, streamObserver);
  }

  @Override
  public BroadcastSignalCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public BroadcastSignalCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
