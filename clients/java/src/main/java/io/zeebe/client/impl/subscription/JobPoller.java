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
package io.zeebe.client.impl.subscription;

import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.client.impl.response.ActivatedJobImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class JobPoller implements StreamObserver<ActivateJobsResponse> {

  private static final Logger LOG = Loggers.JOB_POLLER_LOGGER;

  private final GatewayStub gatewayStub;
  private final Builder requestBuilder;
  private final ZeebeObjectMapper objectMapper;

  private Consumer<ActivatedJob> jobConsumer;
  private Consumer<Integer> doneCallback;
  private int activatedJobs;

  public JobPoller(
      GatewayStub gatewayStub, Builder requestBuilder, ZeebeObjectMapper objectMapper) {
    this.gatewayStub = gatewayStub;
    this.requestBuilder = requestBuilder;
    this.objectMapper = objectMapper;
  }

  private void reset() {
    activatedJobs = 0;
  }

  void poll(
      int maxJobsToActivate, Consumer<ActivatedJob> jobConsumer, Consumer<Integer> doneCallback) {
    reset();

    requestBuilder.setMaxJobsToActivate(maxJobsToActivate);
    this.jobConsumer = jobConsumer;
    this.doneCallback = doneCallback;

    poll();
  }

  private void poll() {
    LOG.trace(
        "Polling at max {} jobs for worker {} and job type {}",
        requestBuilder.getMaxJobsToActivate(),
        requestBuilder.getWorker(),
        requestBuilder.getType());
    gatewayStub.activateJobs(requestBuilder.build(), this);
  }

  @Override
  public void onNext(ActivateJobsResponse activateJobsResponse) {
    activatedJobs += activateJobsResponse.getJobsCount();
    activateJobsResponse.getJobsList().stream()
        .map(job -> new ActivatedJobImpl(objectMapper, job))
        .forEach(jobConsumer);
  }

  @Override
  public void onError(Throwable throwable) {
    LOG.warn(
        "Failed to activated jobs for worker {} and job type {}",
        requestBuilder.getWorker(),
        requestBuilder.getType(),
        throwable);
    pollingDone();
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
