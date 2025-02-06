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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.PublishMessageResponseImpl;
import io.camunda.client.protocol.rest.MessagePublicationRequest;
import io.camunda.client.protocol.rest.MessagePublicationResult;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class PublishMessageCommandImpl extends CommandWithVariables<PublishMessageCommandImpl>
    implements PublishMessageCommandStep1, PublishMessageCommandStep2, PublishMessageCommandStep3 {

  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private final PublishMessageRequest.Builder grpcRequestObjectBuilder;
  private Duration requestTimeout;
  private boolean useRest;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final MessagePublicationRequest httpRequestObject = new MessagePublicationRequest();

  public PublishMessageCommandImpl(
      final GatewayStub asyncStub,
      final CamundaClientConfiguration configuration,
      final JsonMapper jsonMapper,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = PublishMessageRequest.newBuilder();
    requestTimeout = configuration.getDefaultRequestTimeout();
    grpcRequestObjectBuilder.setTimeToLive(configuration.getDefaultMessageTimeToLive().toMillis());
    tenantId(configuration.getDefaultTenantId());
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    useRest = preferRestOverGrpc;
  }

  @Override
  protected PublishMessageCommandImpl setVariablesInternal(final String variables) {
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
  public PublishMessageCommandStep3 messageId(final String messageId) {
    grpcRequestObjectBuilder.setMessageId(messageId);
    httpRequestObject.setMessageId(messageId);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 timeToLive(final Duration timeToLive) {
    grpcRequestObjectBuilder.setTimeToLive(timeToLive.toMillis());
    httpRequestObject.setTimeToLive(timeToLive.toMillis());
    return this;
  }

  @Override
  public PublishMessageCommandStep3 correlationKey(final String correlationKey) {
    grpcRequestObjectBuilder.setCorrelationKey(correlationKey);
    httpRequestObject.setCorrelationKey(correlationKey);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 withoutCorrelationKey() {
    return this;
  }

  @Override
  public PublishMessageCommandStep2 messageName(final String messageName) {
    grpcRequestObjectBuilder.setName(messageName);
    httpRequestObject.setName(messageName);
    return this;
  }

  @Override
  public PublishMessageCommandStep3 tenantId(final String tenantId) {
    grpcRequestObjectBuilder.setTenantId(tenantId);
    httpRequestObject.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<PublishMessageResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<PublishMessageResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<PublishMessageResponse> sendRestRequest() {
    final HttpCamundaFuture<PublishMessageResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/messages/publication",
        objectMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        MessagePublicationResult.class,
        PublishMessageResponseImpl::new,
        result);
    return result;
  }

  private CamundaFuture<PublishMessageResponse> sendGrpcRequest() {
    final PublishMessageRequest request = grpcRequestObjectBuilder.build();
    final RetriableClientFutureImpl<
            PublishMessageResponse, GatewayOuterClass.PublishMessageResponse>
        future =
            new RetriableClientFutureImpl<>(
                PublishMessageResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final PublishMessageRequest request,
      final StreamObserver<GatewayOuterClass.PublishMessageResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .publishMessage(request, streamObserver);
  }

  @Override
  public PublishMessageCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public PublishMessageCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
