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
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateRetriesJobCommandStep1.UpdateRetriesJobCommandStep2;
import io.camunda.zeebe.client.api.response.UpdateRetriesJobResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.UpdateRetriesJobResponseImpl;
import io.camunda.zeebe.client.protocol.rest.JobChangeset;
import io.camunda.zeebe.client.protocol.rest.JobUpdateRequest;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class JobUpdateRetriesCommandImpl
    implements UpdateRetriesJobCommandStep1, UpdateRetriesJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder grpcRequestObjectBuilder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;
  private boolean useRest;
  private final JobUpdateRequest httpRequestObject;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long jobKey;
  private final JsonMapper jsonMapper;

  public JobUpdateRetriesCommandImpl(
      final GatewayStub asyncStub,
      final long jobKey,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate,
      final HttpClient httpClient,
      final boolean preferRestOverGrpc,
      final JsonMapper jsonMapper) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = UpdateJobRetriesRequest.newBuilder();
    grpcRequestObjectBuilder.setJobKey(jobKey);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new JobUpdateRequest();
    useRest = preferRestOverGrpc;
    this.jobKey = jobKey;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public UpdateRetriesJobCommandStep2 retries(final int retries) {
    grpcRequestObjectBuilder.setRetries(retries);
    getChangesetEnsureInitialized().setRetries(retries);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateRetriesJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<UpdateRetriesJobResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private ZeebeFuture<UpdateRetriesJobResponse> sendRestRequest() {
    final HttpZeebeFuture<UpdateRetriesJobResponse> result = new HttpZeebeFuture<>();
    httpClient.patch(
        "/jobs/" + jobKey, jsonMapper.toJson(httpRequestObject), httpRequestConfig.build(), result);
    return result;
  }

  private ZeebeFuture<UpdateRetriesJobResponse> sendGrpcRequest() {
    final UpdateJobRetriesRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<UpdateRetriesJobResponse, UpdateJobRetriesResponse> future =
        new RetriableClientFutureImpl<>(
            UpdateRetriesJobResponseImpl::new,
            retryPredicate,
            streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final UpdateJobRetriesRequest request,
      final StreamObserver<GatewayOuterClass.UpdateJobRetriesResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .updateJobRetries(request, streamObserver);
  }

  @Override
  public UpdateRetriesJobCommandStep2 operationReference(final long operationReference) {
    grpcRequestObjectBuilder.setOperationReference(operationReference);
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public UpdateRetriesJobCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public UpdateRetriesJobCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }

  private JobChangeset getChangesetEnsureInitialized() {
    JobChangeset changeset = httpRequestObject.getChangeset();
    if (changeset == null) {
      changeset = new JobChangeset();
      httpRequestObject.setChangeset(changeset);
    }
    return changeset;
  }
}
