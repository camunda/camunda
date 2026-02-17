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
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceWithResultCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateProcessInstanceWithResultResponseImpl;
import io.camunda.client.protocol.rest.CreateProcessInstanceResult;
import io.camunda.client.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CreateProcessInstanceWithResultCommandImpl
    implements CreateProcessInstanceWithResultCommandStep1 {

  private static final Duration DEADLINE_OFFSET = Duration.ofSeconds(10);
  private final JsonMapper jsonMapper;
  private final GatewayStub asyncStub;
  private final CreateProcessInstanceRequest.Builder createProcessInstanceRequestBuilder;
  private final Builder grpcRequestObject;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private final boolean useRest;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final ProcessInstanceCreationInstruction httpRequestObject;

  public CreateProcessInstanceWithResultCommandImpl(
      final JsonMapper jsonMapper,
      final GatewayStub asyncStub,
      final CreateProcessInstanceRequest.Builder grpcRequestObject,
      final Predicate<StatusCode> retryPredicate,
      final Duration requestTimeout,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc,
      final ProcessInstanceCreationInstruction httpRequestObject) {
    this.jsonMapper = jsonMapper;
    this.asyncStub = asyncStub;
    createProcessInstanceRequestBuilder = grpcRequestObject;
    this.retryPredicate = retryPredicate;
    this.requestTimeout = requestTimeout;
    this.grpcRequestObject = CreateProcessInstanceWithResultRequest.newBuilder();
    this.httpRequestObject = httpRequestObject.awaitCompletion(true);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    useRest = preferRestOverGrpc;
    requestTimeout(requestTimeout);
  }

  @Override
  public FinalCommandStep<ProcessInstanceResult> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    grpcRequestObject.setRequestTimeout(requestTimeout.toMillis());
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    httpRequestObject.setRequestTimeout(requestTimeout.toMillis());
    return this;
  }

  @Override
  public CamundaFuture<ProcessInstanceResult> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<ProcessInstanceResult> sendRestRequest() {
    final HttpCamundaFuture<ProcessInstanceResult> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        CreateProcessInstanceResult.class,
        response -> new CreateProcessInstanceWithResultResponseImpl(jsonMapper, response),
        result);
    return result;
  }

  private CamundaFuture<ProcessInstanceResult> sendGrpcRequest() {
    final CreateProcessInstanceWithResultRequest request =
        grpcRequestObject
            .setRequest(createProcessInstanceRequestBuilder)
            .setRequestTimeout(requestTimeout.toMillis())
            .build();

    final RetriableClientFutureImpl<ProcessInstanceResult, CreateProcessInstanceWithResultResponse>
        future =
            new RetriableClientFutureImpl<>(
                response -> new CreateProcessInstanceWithResultResponseImpl(jsonMapper, response),
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final CreateProcessInstanceWithResultRequest request,
      final StreamObserver<GatewayOuterClass.CreateProcessInstanceWithResultResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.plus(DEADLINE_OFFSET).toMillis(), TimeUnit.MILLISECONDS)
        .createProcessInstanceWithResult(request, future);
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 fetchVariables(
      final List<String> fetchVariables) {
    grpcRequestObject.addAllFetchVariables(fetchVariables);
    httpRequestObject.setFetchVariables(fetchVariables);
    return this;
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 fetchVariables(
      final String... fetchVariables) {
    grpcRequestObject.addAllFetchVariables(Arrays.asList(fetchVariables));
    httpRequestObject.setFetchVariables(Arrays.asList(fetchVariables));
    return this;
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 tenantId(final String tenantId) {
    createProcessInstanceRequestBuilder.setTenantId(tenantId);
    httpRequestObject.setTenantId(tenantId);
    return this;
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 useRest() {
    return null;
  }

  @Override
  public CreateProcessInstanceWithResultCommandStep1 useGrpc() {
    return null;
  }
}
