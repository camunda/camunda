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
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.EmptyApiResponse;
import io.camunda.client.impl.response.FailJobResponseImpl;
import io.camunda.client.protocol.rest.JobFailRequest;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class FailJobCommandImpl extends CommandWithVariables<FailJobCommandStep2>
    implements FailJobCommandStep1, FailJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder grpcRequestObjectBuilder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private boolean useRest;
  private final JobFailRequest httpRequestObject;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long jobKey;

  public FailJobCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final long key,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc) {
    super(jsonMapper);
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = FailJobRequest.newBuilder();
    grpcRequestObjectBuilder.setJobKey(key);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new JobFailRequest();
    useRest = preferRestOverGrpc;
    jobKey = key;
  }

  @Override
  public FailJobCommandStep2 retries(final int retries) {
    grpcRequestObjectBuilder.setRetries(retries);
    httpRequestObject.setRetries(retries);
    return this;
  }

  @Override
  public FailJobCommandStep2 retryBackoff(final Duration backoffTimeout) {
    grpcRequestObjectBuilder.setRetryBackOff(backoffTimeout.toMillis());
    httpRequestObject.setRetryBackOff(backoffTimeout.toMillis());
    return this;
  }

  @Override
  public FailJobCommandStep2 errorMessage(final String errorMsg) {
    grpcRequestObjectBuilder.setErrorMessage(errorMsg);
    httpRequestObject.setErrorMessage(errorMsg);
    return this;
  }

  @Override
  public FailJobCommandStep2 setVariablesInternal(final String variables) {
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
  public FinalCommandStep<FailJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<FailJobResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<FailJobResponse> sendRestRequest() {
    final HttpCamundaFuture<FailJobResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/jobs/" + jobKey + "/failure",
        objectMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        r -> new EmptyApiResponse(),
        result);
    return result;
  }

  private CamundaFuture<FailJobResponse> sendGrpcRequest() {
    final FailJobRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<FailJobResponse, GatewayOuterClass.FailJobResponse> future =
        new RetriableClientFutureImpl<>(
            FailJobResponseImpl::new,
            retryPredicate,
            streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final FailJobRequest request,
      final StreamObserver<GatewayOuterClass.FailJobResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .failJob(request, streamObserver);
  }

  @Override
  public FailJobCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public FailJobCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }
}
