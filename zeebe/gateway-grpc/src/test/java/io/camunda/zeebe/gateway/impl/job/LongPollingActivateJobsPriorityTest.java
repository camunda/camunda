/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;

final class LongPollingActivateJobsPriorityTest {

  private static final String TYPE = "test";
  private static final long LONG_POLLING_TIMEOUT = 5000;
  private static final int MAX_JOBS_TO_ACTIVATE = 1;
  private static final long MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4).toBytes();

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  private LongPollingActivateJobsHandler<ActivateJobsResponse> handler;
  private ActivateJobsStub activateJobsStub;

  @BeforeEach
  void setUp() {
    handler =
        LongPollingActivateJobsHandler.<ActivateJobsResponse>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(LONG_POLLING_TIMEOUT)
            .setProbeTimeoutMillis(20000)
            .setMinEmptyResponses(3)
            .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
            .setResourceExhaustedExceptionProvider(Gateway.RESOURCE_EXHAUSTED_EXCEPTION_PROVIDER)
            .setRequestCanceledExceptionProvider(Gateway.REQUEST_CANCELED_EXCEPTION_PROVIDER)
            .setMetrics(LongPollingMetrics.noop())
            .build();
    submitHandlerActor(handler);

    activateJobsStub = new ActivateJobsStub();
    activateJobsStub.registerWith(brokerClient);
    activateJobsStub.addAvailableJobs(TYPE, 0);
  }

  @Test
  void shouldIncludePriorityWhenJobImmediatelyActivated() {
    // given
    activateJobsStub.addAvailableJobs(TYPE, 1);
    final var responseRef = new AtomicReference<ActivateJobsResponse>();

    // when
    handler.activateJobs(
        buildBrokerRequest(MAX_JOBS_TO_ACTIVATE),
        buildObserver(responseRef),
        ignored -> {},
        LONG_POLLING_TIMEOUT);
    actorScheduler.workUntilDone();

    // then
    assertThat(responseRef.get()).as("expected a response to be received").isNotNull();
    assertThat(responseRef.get().getJobsList())
        .singleElement()
        .satisfies(
            job ->
                assertThat(job.getPriority())
                    .as("activated job priority should match stub value")
                    .isEqualTo(ActivateJobsStub.PRIORITY));
  }

  @Test
  void shouldIncludePriorityInAllJobsWhenBatchIsActivated() {
    // given
    final int batchSize = 3;
    final var responseRef = new AtomicReference<ActivateJobsResponse>();
    final var request =
        new InflightActivateJobsRequest<>(
            1L, buildBrokerRequest(batchSize), buildObserver(responseRef), LONG_POLLING_TIMEOUT);

    // park the request
    handler.internalActivateJobsRetry(request);
    actorScheduler.workUntilDone();

    // when
    activateJobsStub.addAvailableJobs(TYPE, batchSize);
    brokerClient.notifyJobsAvailable(TYPE);
    actorScheduler.workUntilDone();

    // then
    assertThat(responseRef.get()).as("expected a response to be received").isNotNull();
    assertThat(responseRef.get().getJobsList())
        .hasSize(batchSize)
        .allSatisfy(
            job ->
                assertThat(job.getPriority())
                    .as("activated job priority should match stub value")
                    .isEqualTo(ActivateJobsStub.PRIORITY));
  }

  private BrokerActivateJobsRequest buildBrokerRequest(final int maxJobs) {
    final var grpcRequest =
        ActivateJobsRequest.newBuilder()
            .setType(TYPE)
            .setMaxJobsToActivate(maxJobs)
            .setRequestTimeout(LONG_POLLING_TIMEOUT)
            .build();
    return RequestMapper.toActivateJobsRequest(grpcRequest);
  }

  private ResponseObserver<ActivateJobsResponse> buildObserver(
      final AtomicReference<ActivateJobsResponse> responseRef) {
    return new ResponseObserver<>() {
      @Override
      public void onCompleted() {}

      @Override
      public void onNext(final ActivateJobsResponse response) {
        responseRef.set(response);
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public void onError(final Throwable t) {
        throw new AssertionError("Unexpected error in activate jobs observer", t);
      }
    };
  }

  private void submitHandlerActor(final LongPollingActivateJobsHandler<ActivateJobsResponse> h) {
    final var ready = new CompletableFuture<Void>();
    final var actor =
        Actor.newActor()
            .name("TestHandler")
            .actorStartedHandler(h.andThen(ignored -> ready.complete(null)))
            .build();
    actorScheduler.submitActor(actor);
    actorScheduler.workUntilDone();
    ready.join();
  }
}
