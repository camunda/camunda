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
import io.camunda.client.api.command.StreamJobsCommandStep1;
import io.camunda.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep2;
import io.camunda.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep3;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.StreamJobsResponse;
import io.camunda.client.impl.RetriableStreamingFutureImpl;
import io.camunda.client.impl.response.ActivatedJobImpl;
import io.camunda.client.impl.response.StreamJobsResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class StreamJobsCommandImpl
    implements StreamJobsCommandStep1, StreamJobsCommandStep2, StreamJobsCommandStep3 {

  private final GatewayStub asyncStub;
  private final JsonMapper jsonMapper;
  private final Predicate<StatusCode> retryPredicate;
  private final Builder builder;

  private Consumer<ActivatedJob> consumer;
  private Duration requestTimeout;

  private final Set<String> defaultTenantIds;
  private final Set<String> customTenantIds;

  public StreamJobsCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final Predicate<StatusCode> retryPredicate,
      final CamundaClientConfiguration config) {
    this.asyncStub = asyncStub;
    this.jsonMapper = jsonMapper;
    this.retryPredicate = retryPredicate;
    builder = StreamActivatedJobsRequest.newBuilder();

    timeout(config.getDefaultJobTimeout());
    workerName(config.getDefaultJobWorkerName());

    defaultTenantIds = new HashSet<>(config.getDefaultJobWorkerTenantIds());
    customTenantIds = new HashSet<>();
  }

  @Override
  public FinalCommandStep<StreamJobsResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public CamundaFuture<StreamJobsResponse> send() {
    builder.clearTenantIds();
    if (customTenantIds.isEmpty()) {
      builder.addAllTenantIds(defaultTenantIds);
    } else {
      builder.addAllTenantIds(customTenantIds);
    }

    final StreamActivatedJobsRequest request = builder.build();
    final RetriableStreamingFutureImpl<StreamJobsResponse, GatewayOuterClass.ActivatedJob> result =
        new RetriableStreamingFutureImpl<>(
            new StreamJobsResponseImpl(),
            this::consumeJob,
            retryPredicate,
            streamObserver -> send(request, streamObserver));

    send(request, result);
    return result;
  }

  private void send(
      final StreamActivatedJobsRequest request,
      final StreamObserver<GatewayOuterClass.ActivatedJob> observer) {
    GatewayStub stub = asyncStub;
    if (requestTimeout != null) {
      stub = stub.withDeadlineAfter(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    stub.streamActivatedJobs(request, observer);
  }

  @Override
  public StreamJobsCommandStep2 jobType(final String jobType) {
    builder.setType(Objects.requireNonNull(jobType, "must specify a job type"));
    return this;
  }

  @Override
  public StreamJobsCommandStep3 consumer(final Consumer<ActivatedJob> consumer) {
    this.consumer = Objects.requireNonNull(consumer, "must specify a job consumer");
    return this;
  }

  @Override
  public StreamJobsCommandStep3 timeout(final Duration timeout) {
    Objects.requireNonNull(timeout, "must specify a job timeout");
    builder.setTimeout(timeout.toMillis());
    return this;
  }

  @Override
  public StreamJobsCommandStep3 workerName(final String workerName) {
    builder.setWorker(workerName);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 fetchVariables(final List<String> fetchVariables) {
    builder.addAllFetchVariable(fetchVariables);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 fetchVariables(final String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public StreamJobsCommandStep3 tenantId(final String tenantId) {
    customTenantIds.add(tenantId);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 tenantIds(final List<String> tenantIds) {
    customTenantIds.clear();
    customTenantIds.addAll(tenantIds);
    return this;
  }

  @Override
  public StreamJobsCommandStep3 tenantIds(final String... tenantIds) {
    return tenantIds(Arrays.asList(tenantIds));
  }

  @Override
  public StreamJobsCommandStep3 tenantFilter(final TenantFilter tenantFilter) {
    // TODO: https://github.com/camunda/camunda/issues/45356
    return this;
  }

  private void consumeJob(final GatewayOuterClass.ActivatedJob job) {
    final ActivatedJobImpl mappedJob = new ActivatedJobImpl(jsonMapper, job);
    consumer.accept(mappedJob);
  }
}
