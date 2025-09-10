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
import io.camunda.zeebe.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.CancelProcessInstanceResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.CancelProcessInstanceResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CancelProcessInstanceCommandImpl implements CancelProcessInstanceCommandStep1 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private boolean useRest;
  private final long processInstanceKey;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;
  private final io.camunda.zeebe.client.protocol.rest.CancelProcessInstanceRequest
      httpRequestObject;
  private final HttpClient httpClient;

  public CancelProcessInstanceCommandImpl(
      final GatewayStub asyncStub,
      final long processInstanceKey,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final ZeebeClientConfiguration config,
      final JsonMapper jsonMapper) {
    this.asyncStub = asyncStub;
    requestTimeout = config.getDefaultRequestTimeout();
    this.retryPredicate = retryPredicate;
    builder = CancelProcessInstanceRequest.newBuilder();
    builder.setProcessInstanceKey(processInstanceKey);
    useRest = config.preferRestOverGrpc();
    this.processInstanceKey = processInstanceKey;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new io.camunda.zeebe.client.protocol.rest.CancelProcessInstanceRequest();
    this.httpClient = httpClient;
    requestTimeout(requestTimeout);
  }

  @Override
  public FinalCommandStep<CancelProcessInstanceResponse> requestTimeout(
      final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<CancelProcessInstanceResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private ZeebeFuture<CancelProcessInstanceResponse> sendRestRequest() {
    final HttpZeebeFuture<CancelProcessInstanceResponse> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/process-instances/" + processInstanceKey + "/cancellation",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        result);
    return result;
  }

  public ZeebeFuture<CancelProcessInstanceResponse> sendGrpcRequest() {
    final CancelProcessInstanceRequest request = builder.build();

    final RetriableClientFutureImpl<
            CancelProcessInstanceResponse, GatewayOuterClass.CancelProcessInstanceResponse>
        future =
            new RetriableClientFutureImpl<>(
                CancelProcessInstanceResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final CancelProcessInstanceRequest request,
      final StreamObserver<GatewayOuterClass.CancelProcessInstanceResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .cancelProcessInstance(request, future);
  }

  @Override
  public CancelProcessInstanceCommandStep1 operationReference(final long operationReference) {
    builder.setOperationReference(operationReference);
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public CancelProcessInstanceCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public CancelProcessInstanceCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
