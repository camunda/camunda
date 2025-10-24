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
import io.camunda.zeebe.client.api.command.DeleteResourceCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.DeleteResourceResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.DeleteResourceResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.impl.command.DeleteResourceCommandImpl}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class DeleteResourceCommandImpl implements DeleteResourceCommandStep1 {

  private final DeleteResourceRequest.Builder requestBuilder = DeleteResourceRequest.newBuilder();
  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final io.camunda.zeebe.client.protocol.rest.DeleteResourceRequest httpRequestObject;
  private boolean useRest;
  private final long resourceKey;
  private final JsonMapper jsonMapper;

  public DeleteResourceCommandImpl(
      final long resourceKey,
      final GatewayStub asyncStub,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final ZeebeClientConfiguration config,
      final JsonMapper jsonMapper) {
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    requestBuilder.setResourceKey(resourceKey);
    requestTimeout = config.getDefaultRequestTimeout();
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new io.camunda.zeebe.client.protocol.rest.DeleteResourceRequest();
    useRest = config.preferRestOverGrpc();
    this.resourceKey = resourceKey;
    this.jsonMapper = jsonMapper;
    requestTimeout(requestTimeout);
  }

  @Override
  public FinalCommandStep<DeleteResourceResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<DeleteResourceResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private ZeebeFuture<DeleteResourceResponse> sendRestRequest() {
    final HttpZeebeFuture<DeleteResourceResponse> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/resources/" + resourceKey + "/deletion",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        result);
    return result;
  }

  private ZeebeFuture<DeleteResourceResponse> sendGrpcRequest() {
    final DeleteResourceRequest request = requestBuilder.build();

    final RetriableClientFutureImpl<
            DeleteResourceResponse, GatewayOuterClass.DeleteResourceResponse>
        future =
            new RetriableClientFutureImpl<>(
                DeleteResourceResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final DeleteResourceRequest request,
      final StreamObserver<GatewayOuterClass.DeleteResourceResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .deleteResource(request, streamObserver);
  }

  @Override
  public DeleteResourceCommandStep1 operationReference(final long operationReference) {
    requestBuilder.setOperationReference(operationReference);
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public DeleteResourceCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public DeleteResourceCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
