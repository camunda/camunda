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
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep2;
import io.camunda.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.impl.RetriableStreamingFutureImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.ActivateJobsResponseImpl;
import io.camunda.client.protocol.rest.JobActivationRequest;
import io.camunda.client.protocol.rest.JobActivationResult;
import io.camunda.client.protocol.rest.TenantFilterEnum;
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
  private TenantFilter tenantFilter;
  private final CamundaClientConfiguration config;

  public ActivateJobsCommandImpl(
      final GatewayStub asyncStub,
      final HttpClient httpClient,
      final CamundaClientConfiguration config,
      final JsonMapper jsonMapper,
      final Predicate<StatusCode> retryPredicate) {
    this.config = config;
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
    tenantFilter = config.getDefaultJobWorkerTenantFilter();
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
  public ActivateJobsCommandStep3 requestTimeout(final Duration requestTimeout) {
    grpcRequestObjectBuilder.setRequestTimeout(requestTimeout.toMillis());
    httpRequestObject.setRequestTimeout(requestTimeout.toMillis());
    this.requestTimeout = requestTimeout;
    // increment response timeout so client doesn't time out before the server
    final long offsetMillis = config.getDefaultRequestTimeoutOffset().toMillis();
    httpRequestConfig.setResponseTimeout(
        requestTimeout.toMillis() + offsetMillis, TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ActivateJobsResponse> send() {
    grpcRequestObjectBuilder.setTenantFilter(
        GatewayOuterClass.TenantFilter.valueOf(tenantFilter.name()));
    httpRequestObject.setTenantFilter(TenantFilterEnum.valueOf(tenantFilter.name()));

    grpcRequestObjectBuilder.clearTenantIds();
    httpRequestObject.setTenantIds(new ArrayList<>());
    if (tenantFilter == TenantFilter.PROVIDED) {
      if (customTenantIds.isEmpty()) {
        grpcRequestObjectBuilder.addAllTenantIds(defaultTenantIds);
        httpRequestObject.setTenantIds(new ArrayList<>(defaultTenantIds));
      } else {
        grpcRequestObjectBuilder.addAllTenantIds(customTenantIds);
        httpRequestObject.setTenantIds(new ArrayList<>(customTenantIds));
      }
    }

    if (useRest) {
      return sendRestRequest();
    } else {
      return sendGrpcRequest();
    }
  }

  private CamundaFuture<ActivateJobsResponse> sendRestRequest() {
    final HttpCamundaFuture<ActivateJobsResponse> result = new HttpCamundaFuture<>();
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

  private CamundaFuture<ActivateJobsResponse> sendGrpcRequest() {
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

  @Override
  public ActivateJobsCommandStep3 tenantFilter(final TenantFilter tenantFilter) {
    this.tenantFilter = tenantFilter;
    return this;
  }
}
