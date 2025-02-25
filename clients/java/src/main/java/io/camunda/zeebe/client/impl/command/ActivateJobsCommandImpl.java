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

import io.camunda.client.protocol.rest.JobActivationRequest;
import io.camunda.client.protocol.rest.JobActivationResult;
import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep2;
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.impl.RetriableStreamingFutureImpl;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.impl.response.ActivateJobsResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.config.RequestConfig;

public final class ActivateJobsCommandImpl
    implements ActivateJobsCommandStep1, ActivateJobsCommandStep2, ActivateJobsCommandStep3 {

  private static final Duration DEADLINE_OFFSET = Duration.ofSeconds(10);
  private final GatewayStub asyncStub;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final JsonMapper jsonMapper;
  private final Predicate<StatusCode> retryPredicate;
  private final Builder grpcRequestObjectBuilder;
  private final JobActivationRequest httpRequestObject;
  private Duration requestTimeout;
  private boolean useRest;

  private final Set<String> defaultTenantIds;
  private final Set<String> customTenantIds;

  public ActivateJobsCommandImpl(
      final GatewayStub asyncStub,
      final HttpClient httpClient,
      final ZeebeClientConfiguration config,
      final JsonMapper jsonMapper,
      final Predicate<StatusCode> retryPredicate) {
    this.asyncStub = asyncStub;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.jsonMapper = jsonMapper;
    this.retryPredicate = retryPredicate;
    grpcRequestObjectBuilder = ActivateJobsRequest.newBuilder();
    httpRequestObject = new JobActivationRequest();
    requestTimeout(config.getDefaultRequestTimeout());
    timeout(config.getDefaultJobTimeout());
    workerName(config.getDefaultJobWorkerName());
    useRest = config.preferRestOverGrpc();
    defaultTenantIds = new HashSet<>(config.getDefaultJobWorkerTenantIds());
    customTenantIds = new HashSet<>();
  }

  @Override
  public ActivateJobsCommandStep1 useRest() {
    useRest = true;
    return this;
  }

  @Override
  public ActivateJobsCommandStep1 useGrpc() {
    useRest = false;
    return this;
  }

  @Override
  public ActivateJobsCommandStep2 jobType(final String jobType) {
    grpcRequestObjectBuilder.setType(jobType);
    httpRequestObject.setType(jobType);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 maxJobsToActivate(final int maxJobsToActivate) {
    grpcRequestObjectBuilder.setMaxJobsToActivate(maxJobsToActivate);
    httpRequestObject.setMaxJobsToActivate(maxJobsToActivate);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 timeout(final Duration timeout) {
    grpcRequestObjectBuilder.setTimeout(timeout.toMillis());
    httpRequestObject.setTimeout(timeout.toMillis());
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 workerName(final String workerName) {
    if (workerName != null) {
      grpcRequestObjectBuilder.setWorker(workerName);
      httpRequestObject.setWorker(workerName);
    }
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 fetchVariables(final List<String> fetchVariables) {
    grpcRequestObjectBuilder.addAllFetchVariable(fetchVariables);
    httpRequestObject.fetchVariable(fetchVariables);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 fetchVariables(final String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public FinalCommandStep<ActivateJobsResponse> requestTimeout(final Duration requestTimeout) {
    grpcRequestObjectBuilder.setRequestTimeout(requestTimeout.toMillis());
    httpRequestObject.setRequestTimeout(requestTimeout.toMillis());
    this.requestTimeout = requestTimeout;
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<ActivateJobsResponse> send() {
    grpcRequestObjectBuilder.clearTenantIds();
    httpRequestObject.setTenantIds(new ArrayList<>());
    if (customTenantIds.isEmpty()) {
      grpcRequestObjectBuilder.addAllTenantIds(defaultTenantIds);
      httpRequestObject.setTenantIds(new ArrayList<>(defaultTenantIds));
    } else {
      grpcRequestObjectBuilder.addAllTenantIds(customTenantIds);
      httpRequestObject.setTenantIds(new ArrayList<>(customTenantIds));
    }

    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private ZeebeFuture<ActivateJobsResponse> sendRestRequest() {
    final HttpZeebeFuture<ActivateJobsResponse> result = new HttpZeebeFuture<>();
    final ActivateJobsResponseImpl response = new ActivateJobsResponseImpl(jsonMapper);
    httpClient.post(
        "/jobs/activation",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        JobActivationResult.class,
        response::addResponse,
        result);
    return result;
  }

  private ZeebeFuture<ActivateJobsResponse> sendGrpcRequest() {
    final ActivateJobsRequest request = grpcRequestObjectBuilder.build();

    final ActivateJobsResponseImpl response = new ActivateJobsResponseImpl(jsonMapper);
    final RetriableStreamingFutureImpl<ActivateJobsResponse, GatewayOuterClass.ActivateJobsResponse>
        future =
            new RetriableStreamingFutureImpl<>(
                response,
                response::addResponse,
                retryPredicate,
                streamObserver -> sendGrpc(request, streamObserver));

    sendGrpc(request, future);
    return future;
  }

  private void sendGrpc(
      final ActivateJobsRequest request,
      final StreamObserver<GatewayOuterClass.ActivateJobsResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.plus(DEADLINE_OFFSET).toMillis(), TimeUnit.MILLISECONDS)
        .activateJobs(request, future);
  }

  @Override
  public ActivateJobsCommandStep3 tenantId(final String tenantId) {
    customTenantIds.add(tenantId);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 tenantIds(final List<String> tenantIds) {
    customTenantIds.clear();
    customTenantIds.addAll(tenantIds);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 tenantIds(final String... tenantIds) {
    return tenantIds(Arrays.asList(tenantIds));
  }
}
