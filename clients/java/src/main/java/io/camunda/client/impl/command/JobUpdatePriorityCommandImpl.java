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
import io.camunda.client.api.command.UpdateJobPriorityCommandStep1;
import io.camunda.client.api.command.UpdateJobPriorityCommandStep1.UpdateJobPriorityCommandStep2;
import io.camunda.client.api.response.UpdateJobPriorityResponse;
import io.camunda.client.impl.RetriableClientFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UpdateJobPriorityResponseImpl;
import io.camunda.client.protocol.rest.JobChangeset;
import io.camunda.client.protocol.rest.JobUpdateRequest;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobPriorityRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobPriorityRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class JobUpdatePriorityCommandImpl
    implements UpdateJobPriorityCommandStep1, UpdateJobPriorityCommandStep2 {

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

  public JobUpdatePriorityCommandImpl(
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
    grpcRequestObjectBuilder = UpdateJobPriorityRequest.newBuilder();
    grpcRequestObjectBuilder.setJobKey(jobKey);
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new JobUpdateRequest();
    useRest = preferRestOverGrpc;
    this.jobKey = jobKey;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public UpdateJobPriorityCommandStep2 priority(final int priority) {
    grpcRequestObjectBuilder.setPriority(priority);
    getChangesetEnsureInitialized().setPriority(priority);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateJobPriorityResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateJobPriorityResponse> send() {
    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<UpdateJobPriorityResponse> sendRestRequest() {
    final HttpCamundaFuture<UpdateJobPriorityResponse> result = new HttpCamundaFuture<>();
    httpClient.patch(
        "/jobs/" + jobKey,
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        UpdateJobPriorityResponseImpl::new,
        result);
    return result;
  }

  private CamundaFuture<UpdateJobPriorityResponse> sendGrpcRequest() {
    final UpdateJobPriorityRequest request = grpcRequestObjectBuilder.build();

    final RetriableClientFutureImpl<
            UpdateJobPriorityResponse, GatewayOuterClass.UpdateJobPriorityResponse>
        future =
            new RetriableClientFutureImpl<>(
                UpdateJobPriorityResponseImpl::new,
                retryPredicate,
                streamObserver -> sendGrpcRequest(request, streamObserver));

    sendGrpcRequest(request, future);
    return future;
  }

  private void sendGrpcRequest(
      final UpdateJobPriorityRequest request,
      final StreamObserver<GatewayOuterClass.UpdateJobPriorityResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .updateJobPriority(request, streamObserver);
  }

  @Override
  public UpdateJobPriorityCommandStep2 operationReference(final long operationReference) {
    grpcRequestObjectBuilder.setOperationReference(operationReference);
    httpRequestObject.setOperationReference(operationReference);
    return this;
  }

  @Override
  public UpdateJobPriorityCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public UpdateJobPriorityCommandStep1 useGrpc() {
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
