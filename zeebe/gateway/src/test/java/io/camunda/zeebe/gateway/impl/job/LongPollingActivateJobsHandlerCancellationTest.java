/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;

final class LongPollingActivateJobsHandlerCancellationTest {

  private static final String TYPE = "testJob";
  private static final long LONG_POLLING_TIMEOUT = 5000;
  private static final long PROBE_TIMEOUT = 20000;
  private static final int FAILED_RESPONSE_THRESHOLD = 3;
  private static final int MAX_JOBS_TO_ACTIVATE = 2;
  private static final long MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4).toBytes();

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  private LongPollingActivateJobsHandler<Object> handler;

  @BeforeEach
  void setUp() {
    handler =
        LongPollingActivateJobsHandler.<Object>newBuilder()
            .setBrokerClient(brokerClient)
            .setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setLongPollingTimeout(LONG_POLLING_TIMEOUT)
            .setProbeTimeoutMillis(PROBE_TIMEOUT)
            .setMinEmptyResponses(FAILED_RESPONSE_THRESHOLD)
            .setActivationResultMapper(
                response ->
                    new JobActivationResult<>() {
                      @Override
                      public int getJobsCount() {
                        return 0;
                      }

                      @Override
                      public List<ActivatedJob> getJobs() {
                        return Collections.emptyList();
                      }

                      @Override
                      public Object getActivateJobsResponse() {
                        return response;
                      }

                      @Override
                      public List<ActivatedJob> getJobsToDefer() {
                        return Collections.emptyList();
                      }
                    })
            .setResourceExhaustedExceptionProvider(RuntimeException::new)
            .setRequestCanceledExceptionProvider(RuntimeException::new)
            .setMetrics(LongPollingMetrics.noop())
            .build();

    final var stub = new ActivateJobsStub();
    stub.registerWith(brokerClient);
    // no jobs available — all activate attempts return empty responses
    stub.addAvailableJobs(TYPE, 0);

    submitHandlerActor(handler);
  }

  /**
   * Verifies that cancelled requests are fully removed from all tracking collections after the
   * broker responds. Runs multiple iterations to surface any accumulation across restarts — the
   * regression in c51abb99733 caused requests to remain in {@code activeRequests} indefinitely.
   */
  @Test
  void shouldCleanUpCancelledRequestsAfterBrokerResponds() {
    final var totalCompletions = new AtomicInteger(0);

    for (int i = 0; i < 10; i++) {
      // given
      final var cancelled = new AtomicBoolean(false);
      final var cancelHandler = new Runnable[1];
      final var observer = newTrackingObserver(cancelled, totalCompletions);

      // when — activate, let broker respond (no jobs), then cancel
      handler.activateJobs(
          buildBrokerRequest(),
          observer,
          runnable -> cancelHandler[0] = runnable,
          LONG_POLLING_TIMEOUT);
      actorScheduler.workUntilDone();

      cancelled.set(true);
      if (cancelHandler[0] != null) {
        cancelHandler[0].run();
      }
      actorScheduler.workUntilDone();
    }

    // then
    assertThat(totalCompletions.get())
        .as("no cancelled request should have been completed or resubmitted")
        .isZero();
    assertThat(handler.hasInflightRequestsForJobType(TYPE))
        .as("handler should have no tracked state after all cancellations")
        .isFalse();
  }

  /**
   * Verifies that when the long-polling timer expires the request is completed to the observer and
   * removed from {@code pendingRequests}. Active broker requests are cleaned up by their own error
   * or success callbacks (which always fire within the configured broker request timeout), so the
   * timer is only responsible for removing from the pending queue.
   */
  @Test
  void shouldCleanUpRequestWhenLongPollingTimerExpires() {
    // given - activate, let broker respond with no jobs; request parks in pendingRequests
    final var completions = new AtomicInteger(0);
    handler.activateJobs(
        buildBrokerRequest(),
        newTrackingObserver(new AtomicBoolean(false), completions),
        r -> {},
        LONG_POLLING_TIMEOUT);
    actorScheduler.workUntilDone();

    // when - advance the actor clock past the long-polling timeout to fire the timer
    actorScheduler.getClock().addTime(Duration.ofMillis(LONG_POLLING_TIMEOUT + 1));
    actorScheduler.workUntilDone();

    // then - timer fires request.timeout() which calls onCompleted(), then cleans up tracking state
    assertThat(completions.get())
        .as("timer should have sent onCompleted() to the observer")
        .isEqualTo(1);
    assertThat(handler.hasInflightRequestsForJobType(TYPE))
        .as("timed-out request should be removed from all tracking state")
        .isFalse();
  }

  /**
   * Verifies that the handler is safe when the cancel handler fires before the actor has processed
   * the initial activation (i.e. cancel and broker callback are interleaved on the actor thread).
   */
  @Test
  void shouldHandleRaceConditionBetweenCancelAndBrokerCallback() {
    // given
    final var cancelled = new AtomicBoolean(false);
    final var totalCompletions = new AtomicInteger(0);
    final var cancelHandler = new Runnable[1];
    final var observer = newTrackingObserver(cancelled, totalCompletions);

    // when — cancel immediately, before workUntilDone processes either the activation or
    // the broker callback, so both tasks are queued and run interleaved
    handler.activateJobs(
        buildBrokerRequest(),
        observer,
        runnable -> cancelHandler[0] = runnable,
        LONG_POLLING_TIMEOUT);

    cancelled.set(true);
    if (cancelHandler[0] != null) {
      cancelHandler[0].run();
    }

    actorScheduler.workUntilDone();

    // then
    assertThat(totalCompletions.get())
        .as("no cancelled request should have been completed or resubmitted")
        .isZero();
    assertThat(handler.hasInflightRequestsForJobType(TYPE))
        .as("handler should have no tracked state after race-condition cancellation")
        .isFalse();
  }

  // -- helpers --

  private ResponseObserver<Object> newTrackingObserver(
      final AtomicBoolean cancelled, final AtomicInteger completions) {
    return new ResponseObserver<>() {
      @Override
      public void onCompleted() {
        completions.incrementAndGet();
      }

      @Override
      public void onNext(final Object element) {}

      @Override
      public boolean isCancelled() {
        return cancelled.get();
      }

      @Override
      public void onError(final Throwable throwable) {}
    };
  }

  private void submitHandlerActor(final LongPollingActivateJobsHandler<Object> handlerToSubmit) {
    final var ready = new CompletableFuture<Void>();
    final var actor =
        Actor.newActor()
            .name("TestHandler")
            .actorStartedHandler(handlerToSubmit.andThen(ignored -> ready.complete(null)))
            .build();
    actorScheduler.submitActor(actor);
    actorScheduler.workUntilDone();
    ready.join();
  }

  private BrokerActivateJobsRequest buildBrokerRequest() {
    final var brokerRequest = mock(BrokerActivateJobsRequest.class);
    final var requestWriter = new JobBatchRecord();
    requestWriter.setType(TYPE);
    requestWriter.setWorker("test-worker");
    requestWriter.setMaxJobsToActivate(MAX_JOBS_TO_ACTIVATE);
    when(brokerRequest.getRequestWriter()).thenReturn(requestWriter);
    when(brokerRequest.getPartitionId()).thenReturn(1);
    return brokerRequest;
  }
}
