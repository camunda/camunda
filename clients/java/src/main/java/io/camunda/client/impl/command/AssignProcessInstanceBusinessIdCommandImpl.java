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
import io.camunda.client.api.command.AssignProcessInstanceBusinessIdCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignProcessInstanceBusinessIdResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.AssignProcessInstanceBusinessIdResponseImpl;
import io.camunda.client.protocol.rest.ProcessInstanceBusinessIdAssignmentInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.AssignProcessInstanceBusinessIdRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AssignProcessInstanceBusinessIdCommandImpl
    implements AssignProcessInstanceBusinessIdCommandStep1,
        AssignProcessInstanceBusinessIdCommandStep1.AssignProcessInstanceBusinessIdCommandStep2 {

  private final AssignProcessInstanceBusinessIdRequest.Builder requestBuilder =
      AssignProcessInstanceBusinessIdRequest.newBuilder();
  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final ProcessInstanceBusinessIdAssignmentInstruction httpRequestObject;
  private boolean useRest;
  private final long processInstanceKey;
  private final JsonMapper jsonMapper;

  public AssignProcessInstanceBusinessIdCommandImpl(
      final long processInstanceKey,
      final GatewayStub asyncStub,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final CamundaClientConfiguration config,
      final JsonMapper jsonMapper) {
    requestBuilder.setProcessInstanceKey(processInstanceKey);
    this.asyncStub = asyncStub;
    requestTimeout = config.getDefaultRequestTimeout();
    this.retryPredicate = retryPredicate;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new ProcessInstanceBusinessIdAssignmentInstruction();
    useRest = config.preferRestOverGrpc();
    this.processInstanceKey = processInstanceKey;
    this.jsonMapper = jsonMapper;
    requestTimeout(requestTimeout);
  }

  @Override
  public AssignProcessInstanceBusinessIdCommandStep2 businessId(final String businessId) {
    requestBuilder.setBusinessId(businessId);
    httpRequestObject.setBusinessId(businessId);
    return this;
  }

  @Override
  public FinalCommandStep<AssignProcessInstanceBusinessIdResponse> requestTimeout(
      final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignProcessInstanceBusinessIdResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<AssignProcessInstanceBusinessIdResponse> sendRestRequest() {
    final HttpCamundaFuture<AssignProcessInstanceBusinessIdResponse> result =
        new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances/" + processInstanceKey + "/business-id-assignment",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        AssignProcessInstanceBusinessIdResponseImpl::new,
        result);
    return result;
  }

  private CamundaFuture<AssignProcessInstanceBusinessIdResponse> sendGrpcRequest() {
    final AssignProcessInstanceBusinessIdRequest request = requestBuilder.build();

    final RetriableClientFutureImpl<
            AssignProcessInstanceBusinessIdResponse,
            GatewayOuterClass.AssignProcessInstanceBusinessIdResponse>
        future =
            new RetriableClientFutureImpl<>(
                AssignProcessInstanceBusinessIdResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);

    return future;
  }

  private void sendGrpcRequest(
      final AssignProcessInstanceBusinessIdRequest request,
      final StreamObserver<GatewayOuterClass.AssignProcessInstanceBusinessIdResponse>
          streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .assignProcessInstanceBusinessId(request, streamObserver);
  }

  @Override
  public AssignProcessInstanceBusinessIdCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public AssignProcessInstanceBusinessIdCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
