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

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep2;
import io.camunda.zeebe.client.api.response.StreamJobsResponse;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.impl.Loggers;
import io.camunda.zeebe.client.impl.RetriableStreamingFutureImpl;
import io.camunda.zeebe.client.impl.response.ActivatedJobImpl;
import io.camunda.zeebe.client.impl.response.StreamJobsResponseImpl;
import io.camunda.zeebe.client.impl.worker.JobClientImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamJobRequest;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class StreamJobsCommandImpl implements StreamJobsCommandStep1, StreamJobsCommandStep2 {
  private final GatewayStub asyncStub;
  private final JsonMapper jsonMapper;
  private final Predicate<Throwable> retryPredicate;
  private final StreamJobRequest request;
  private final JobClient jobClient;
  private final ExecutorService executorService;

  private JobHandler jobHandler;

  public StreamJobsCommandImpl(
      final GatewayStub asyncStub,
      final ZeebeClientConfiguration config,
      final JsonMapper jsonMapper,
      final ExecutorService executorService,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.jsonMapper = jsonMapper;
    this.executorService = executorService;
    this.retryPredicate = retryPredicate;
    request = StreamJobRequest.newBuilder().build();
    jobClient = new JobClientImpl(asyncStub, config, jsonMapper, retryPredicate);
  }

  @Override
  public StreamJobsCommandStep2 requestTimeout(final Duration requestTimeout) {
    // NOOP
    return this;
  }

  @Override
  public ZeebeFuture<StreamJobsResponse> send() {
    final RetriableStreamingFutureImpl<StreamJobsResponse, GatewayOuterClass.ActivatedJob> future =
        new RetriableStreamingFutureImpl<>(
            new StreamJobsResponseImpl(),
            job -> executorService.execute(() -> forwardJob(job)),
            retryPredicate,
            streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void forwardJob(final ActivatedJob job) {
    try {
      Loggers.JOB_WORKER_LOGGER.error("Received job {}", job.getKey());
      jobHandler.handle(jobClient, new ActivatedJobImpl(jsonMapper, job));
    } catch (final Exception e) {
      jobClient.newFailCommand(job.getKey()).retries(job.getRetries() - 1).send().join();
    }
  }

  @Override
  public StreamJobsCommandStep2 handler(final JobHandler jobHandler) {
    this.jobHandler = jobHandler;
    return this;
  }

  private void send(
      final StreamJobRequest request, final StreamObserver<GatewayOuterClass.ActivatedJob> future) {
    asyncStub.withDeadlineAfter(Long.MAX_VALUE, TimeUnit.MILLISECONDS).streamJobs(request, future);
  }
}
