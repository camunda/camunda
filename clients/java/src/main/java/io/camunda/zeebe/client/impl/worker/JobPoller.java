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
package io.zeebe.client.impl.worker;

import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.response.ActivatedJobImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import org.slf4j.Logger;

public final class JobPoller implements StreamObserver<ActivateJobsResponse> {

  private static final Logger LOG = Loggers.JOB_POLLER_LOGGER;

  private final GatewayStub gatewayStub;
  private final Builder requestBuilder;
  private final JsonMapper jsonMapper;
  private final long requestTimeout;
  private final Predicate<Throwable> retryPredicate;

  private Consumer<ActivatedJob> jobConsumer;
  private IntConsumer doneCallback;
  private Consumer<Throwable> errorCallback;
  private int activatedJobs;
  private BooleanSupplier openSupplier;

  public JobPoller(
      final GatewayStub gatewayStub,
      final Builder requestBuilder,
      final JsonMapper jsonMapper,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.gatewayStub = gatewayStub;
    this.requestBuilder = requestBuilder;
    this.jsonMapper = jsonMapper;
    this.requestTimeout = requestTimeout.toMillis();
    this.retryPredicate = retryPredicate;
  }

  private void reset() {
    activatedJobs = 0;
  }

  /**
   * Poll for available jobs. Jobs returned by zeebe are activated.
   *
   * @param maxJobsToActivate maximum number of jobs to activate
   * @param jobConsumer consumes each activated job individually
   * @param doneCallback consumes the number of jobs activated
   * @param errorCallback consumes thrown error
   * @param openSupplier supplies whether the consumer is open
   */
  public void poll(
      final int maxJobsToActivate,
      final Consumer<ActivatedJob> jobConsumer,
      final IntConsumer doneCallback,
      final Consumer<Throwable> errorCallback,
      final BooleanSupplier openSupplier) {
    reset();

    requestBuilder.setMaxJobsToActivate(maxJobsToActivate);
    this.jobConsumer = jobConsumer;
    this.doneCallback = doneCallback;
    this.errorCallback = errorCallback;
    this.openSupplier = openSupplier;

    poll();
  }

  private void poll() {
    LOG.trace(
        "Polling at max {} jobs for worker {} and job type {}",
        requestBuilder.getMaxJobsToActivate(),
        requestBuilder.getWorker(),
        requestBuilder.getType());
    gatewayStub
        .withDeadlineAfter(requestTimeout, TimeUnit.MILLISECONDS)
        .activateJobs(requestBuilder.build(), this);
  }

  @Override
  public void onNext(final ActivateJobsResponse activateJobsResponse) {
    activatedJobs += activateJobsResponse.getJobsCount();
    activateJobsResponse.getJobsList().stream()
        .map(job -> new ActivatedJobImpl(jsonMapper, job))
        .forEach(jobConsumer);
  }

  @Override
  public void onError(final Throwable throwable) {
    if (retryPredicate.test(throwable)) {
      poll();
    } else {
      if (openSupplier.getAsBoolean()) {
        try {
          LOG.warn(
              "Failed to activated jobs for worker {} and job type {}",
              requestBuilder.getWorker(),
              requestBuilder.getType(),
              throwable);
        } finally {
          errorCallback.accept(throwable);
        }
      }
    }
  }

  @Override
  public void onCompleted() {
    pollingDone();
  }

  private void pollingDone() {
    if (activatedJobs > 0) {
      LOG.info(
          "Activated {} jobs for worker {} and job type {}",
          activatedJobs,
          requestBuilder.getWorker(),
          requestBuilder.getType());
    } else {
      LOG.trace(
          "No jobs activated for worker {} and job type {}",
          requestBuilder.getWorker(),
          requestBuilder.getType());
    }
    doneCallback.accept(activatedJobs);
  }
}
