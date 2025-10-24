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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.client.api.command.UpdateTimeoutJobCommandStep1.UpdateTimeoutJobCommandStep2;
import io.camunda.client.api.response.UpdateTimeoutJobResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateTimeoutJobResponseImpl;
import io.camunda.client.protocol.rest.JobChangeset;
import io.camunda.client.protocol.rest.JobUpdateRequest;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public class JobUpdateTimeoutCommandImpl
    implements UpdateTimeoutJobCommandStep1, UpdateTimeoutJobCommandStep2 {

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

  public JobUpdateTimeoutCommandImpl(
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
    grpcRequestObjectBuilder = UpdateJobTimeoutRequest.newBuilder();
    grpcRequestObjectBuilder.setJobKey(jobKey);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new JobUpdateRequest();
    useRest = preferRestOverGrpc;
    this.jobKey = jobKey;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public UpdateTimeoutJobCommandStep2 timeout(final long timeout) {
    grpcRequestObjectBuilder.setTimeout(timeout);
    getChangesetEnsureInitialized().setTimeout(timeout);
    return this;
  }

  @Override
  public UpdateTimeoutJobCommandStep2 timeout(final Duration timeout) {
    return timeout(timeout.toMillis());
  }

  @Override
  public FinalCommandStep<UpdateTimeoutJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateTimeoutJobResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<UpdateTimeoutJobResponse> sendRestRequest() {
    final HttpCamundaFuture<UpdateTimeoutJobResponse> result = new HttpCamundaFuture<>();
    httpClient.patch(
        "/jobs/" + jobKey,
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        UpdateTimeoutJobResponseImpl::new,
        result);
    return result;
  }

  private CamundaFuture<UpdateTimeoutJobResponse> sendGrpcRequest() {
    final UpdateJobTimeoutRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<UpdateTimeoutJobResponse, UpdateJobTimeoutResponse> future =
        new RetriableClientFutureImpl<>(
            UpdateTimeoutJobResponseImpl::new,
            retryPredicate,
            streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final UpdateJobTimeoutRequest request,
      final StreamObserver<UpdateJobTimeoutResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .updateJobTimeout(request, streamObserver);
  }

  @Override
  public UpdateTimeoutJobCommandStep2 operationReference(final long operationReference) {
    grpcRequestObjectBuilder.setOperationReference(operationReference);
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public UpdateTimeoutJobCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public UpdateTimeoutJobCommandStep1 useGrpc() {
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
