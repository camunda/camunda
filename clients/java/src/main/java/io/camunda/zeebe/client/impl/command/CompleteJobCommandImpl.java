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

import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.CompleteJobResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class CompleteJobCommandImpl extends CommandWithVariables<CompleteJobCommandStep1>
    implements CompleteJobCommandStep1 {

  private final GatewayStub asyncStub;
  private final Builder grpcRequestObjectBuilder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final JobCompletionRequest httpRequestObject;
  private boolean useRest;
  private final long jobKey;
  private final JsonMapper jsonMapper;

  public CompleteJobCommandImpl(
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
    grpcRequestObjectBuilder = CompleteJobRequest.newBuilder();
    grpcRequestObjectBuilder.setJobKey(key);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new JobCompletionRequest();
    useRest = preferRestOverGrpc;
    jobKey = key;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public FinalCommandStep<CompleteJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<CompleteJobResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  @Override
  public CompleteJobCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public CompleteJobCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }

  private ZeebeFuture<CompleteJobResponse> sendRestRequest() {
    final HttpZeebeFuture<CompleteJobResponse> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/jobs/" + jobKey + "/completion",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        result);
    return result;
  }

  private ZeebeFuture<CompleteJobResponse> sendGrpcRequest() {
    final CompleteJobRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<CompleteJobResponse, GatewayOuterClass.CompleteJobResponse>
        future =
            new RetriableClientFutureImpl<>(
                CompleteJobResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final CompleteJobRequest request,
      final StreamObserver<GatewayOuterClass.CompleteJobResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .completeJob(request, streamObserver);
  }

  @Override
  protected CompleteJobCommandStep1 setVariablesInternal(final String variables) {
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
}
