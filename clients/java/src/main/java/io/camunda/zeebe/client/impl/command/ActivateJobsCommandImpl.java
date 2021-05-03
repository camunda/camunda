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
package io.zeebe.client.impl.command;

import io.grpc.stub.StreamObserver;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.zeebe.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep2;
import io.zeebe.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.client.impl.RetriableStreamingFutureImpl;
import io.zeebe.client.impl.response.ActivateJobsResponseImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest.Builder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ActivateJobsCommandImpl
    implements ActivateJobsCommandStep1, ActivateJobsCommandStep2, ActivateJobsCommandStep3 {

  private static final Duration DEADLINE_OFFSET = Duration.ofSeconds(10);
  private final GatewayStub asyncStub;
  private final JsonMapper jsonMapper;
  private final Predicate<Throwable> retryPredicate;
  private final Builder builder;
  private Duration requestTimeout;

  public ActivateJobsCommandImpl(
      final GatewayStub asyncStub,
      final ZeebeClientConfiguration config,
      final JsonMapper jsonMapper,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.jsonMapper = jsonMapper;
    this.retryPredicate = retryPredicate;
    builder = ActivateJobsRequest.newBuilder();
    requestTimeout(config.getDefaultRequestTimeout());
    timeout(config.getDefaultJobTimeout());
    workerName(config.getDefaultJobWorkerName());
  }

  @Override
  public ActivateJobsCommandStep2 jobType(final String jobType) {
    builder.setType(jobType);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 maxJobsToActivate(final int maxJobsToActivate) {
    builder.setMaxJobsToActivate(maxJobsToActivate);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 timeout(final Duration timeout) {
    builder.setTimeout(timeout.toMillis());
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 workerName(final String workerName) {
    builder.setWorker(workerName);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 fetchVariables(final List<String> fetchVariables) {
    builder.addAllFetchVariable(fetchVariables);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 fetchVariables(final String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public FinalCommandStep<ActivateJobsResponse> requestTimeout(final Duration requestTimeout) {
    builder.setRequestTimeout(requestTimeout.toMillis());
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<ActivateJobsResponse> send() {
    final ActivateJobsRequest request = builder.build();

    final ActivateJobsResponseImpl response = new ActivateJobsResponseImpl(jsonMapper);
    final RetriableStreamingFutureImpl<ActivateJobsResponse, GatewayOuterClass.ActivateJobsResponse>
        future =
            new RetriableStreamingFutureImpl<>(
                response,
                response::addResponse,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final ActivateJobsRequest request,
      final StreamObserver<GatewayOuterClass.ActivateJobsResponse> future) {
    asyncStub
        .withDeadlineAfter(requestTimeout.plus(DEADLINE_OFFSET).toMillis(), TimeUnit.MILLISECONDS)
        .activateJobs(request, future);
  }
}
