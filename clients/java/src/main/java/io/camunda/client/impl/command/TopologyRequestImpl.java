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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.TopologyImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class TopologyRequestImpl implements TopologyRequestStep1 {

  private final GatewayStub asyncStub;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private boolean useRest;

  public TopologyRequestImpl(
      final GatewayStub asyncStub,
      final HttpClient httpClient,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate,
      final boolean useRest) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.useRest = useRest;
  }

  @Override
  public TopologyRequestStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public TopologyRequestStep1 useGrpc() {
    useRest = false;
    return this;
  }

  @Override
  public FinalCommandStep<Topology> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public CamundaFuture<Topology> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private RetriableClientFutureImpl<Topology, TopologyResponse> sendGrpcRequest() {
    final TopologyRequest request = TopologyRequest.getDefaultInstance();

    final RetriableClientFutureImpl<Topology, TopologyResponse> future =
        new RetriableClientFutureImpl<>(
            TopologyImpl::new,
            retryPredicate,
            streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);

    return future;
  }

  private void sendGrpcRequest(
      final TopologyRequest request, final StreamObserver<TopologyResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .topology(request, streamObserver);
  }

  private CamundaFuture<Topology> sendRestRequest() {
    return httpClient.get(
        "/topology",
        httpRequestConfig
            .setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build(),
        io.camunda.client.protocol.rest.TopologyResponse.class,
        TopologyImpl::new);
  }
}
