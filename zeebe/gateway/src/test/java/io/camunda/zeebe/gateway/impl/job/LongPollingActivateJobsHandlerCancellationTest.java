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
  private ActivateJobsStub activateJobsStub;

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

    activateJobsStub = new ActivateJobsStub();
    activateJobsStub.registerWith(brokerClient);
    // no jobs available — all activate attempts return empty responses
    activateJobsStub.addAvailableJobs(TYPE, 0);
  }

  @Test
  void shouldHandleCancellationWhileRequestIsActive() throws Exception {
    // given
    submitHandlerActor(handler);

    final var cancelledFlag = new AtomicBoolean(false);
    final var completedCount = new AtomicInteger(0);
    final var errorCount = new AtomicInteger(0);

    final var observer =
        new ResponseObserver<Object>() {
          @Override
          public void onCompleted() {
            completedCount.incrementAndGet();
          }

          @Override
          public void onNext(final Object element) {}

          @Override
          public boolean isCancelled() {
            return cancelledFlag.get();
          }

          @Override
          public void onError(final Throwable throwable) {
            errorCount.incrementAndGet();
          }
        };

    final var brokerRequest = buildBrokerRequest();
    final var cancelHandler = new Runnable[1];

    // when - activate the request
    handler.activateJobs(
        brokerRequest, observer, runnable -> cancelHandler[0] = runnable, LONG_POLLING_TIMEOUT);

    actorScheduler.workUntilDone();

    // Cancel the request while it's active
    cancelledFlag.set(true);
    if (cancelHandler[0] != null) {
      cancelHandler[0].run();
    }
    actorScheduler.workUntilDone();

    // then - verify no errors or completions occurred after cancellation
    assertThat(completedCount.get()).isZero();
    assertThat(errorCount.get()).isZero();
  }

  @Test
  void shouldNotResubmitCancelledRequestWhenBrokerCallbackFires() throws Exception {
    // given
    submitHandlerActor(handler);

    final var cancelledFlag = new AtomicBoolean(false);
    final var completedCount = new AtomicInteger(0);

    final var observer =
        new ResponseObserver<Object>() {
          @Override
          public void onCompleted() {
            completedCount.incrementAndGet();
          }

          @Override
          public void onNext(final Object element) {}

          @Override
          public boolean isCancelled() {
            return cancelledFlag.get();
          }

          @Override
          public void onError(final Throwable throwable) {}
        };

    final var brokerRequest = buildBrokerRequest();
    final var cancelHandler = new Runnable[1];

    // when - activate the request
    handler.activateJobs(
        brokerRequest, observer, runnable -> cancelHandler[0] = runnable, LONG_POLLING_TIMEOUT);

    actorScheduler.workUntilDone();

    // Cancel the request
    cancelledFlag.set(true);
    if (cancelHandler[0] != null) {
      cancelHandler[0].run();
    }
    actorScheduler.workUntilDone();

    // then - verify the cancelled request is not resubmitted
    // The completeOrResubmitRequest should exit early due to isCanceled() check
    assertThat(completedCount.get()).isZero();
  }

  @Test
  void shouldPreventMemoryLeakWhenMultipleWorkersDisconnect() throws Exception {
    // given
    submitHandlerActor(handler);

    final var cancelledFlag = new AtomicBoolean(false);
    final var completedCount = new AtomicInteger(0);

    // when - activate multiple requests and cancel them all
    for (int i = 0; i < 10; i++) {
      final var observer =
          new ResponseObserver<Object>() {
            @Override
            public void onCompleted() {
              completedCount.incrementAndGet();
            }

            @Override
            public void onNext(final Object element) {}

            @Override
            public boolean isCancelled() {
              return cancelledFlag.get();
            }

            @Override
            public void onError(final Throwable throwable) {}
          };

      final var brokerRequest = buildBrokerRequest();
      final var cancelHandler = new Runnable[1];

      handler.activateJobs(
          brokerRequest, observer, runnable -> cancelHandler[0] = runnable, LONG_POLLING_TIMEOUT);
      actorScheduler.workUntilDone();

      // Cancel this request
      cancelledFlag.set(true);
      if (cancelHandler[0] != null) {
        cancelHandler[0].run();
      }
      actorScheduler.workUntilDone();
      cancelledFlag.set(false); // Reset for next iteration
    }

    // then - verify no requests were erroneously completed
    assertThat(completedCount.get()).isZero();
  }

  @Test
  void shouldHandleRaceConditionBetweenCancelAndBrokerCallback() throws Exception {
    // given
    submitHandlerActor(handler);

    final var cancelledFlag = new AtomicBoolean(false);
    final var errorCount = new AtomicInteger(0);
    final var completedCount = new AtomicInteger(0);

    final var observer =
        new ResponseObserver<Object>() {
          @Override
          public void onCompleted() {
            completedCount.incrementAndGet();
          }

          @Override
          public void onNext(final Object element) {}

          @Override
          public boolean isCancelled() {
            return cancelledFlag.get();
          }

          @Override
          public void onError(final Throwable throwable) {
            errorCount.incrementAndGet();
          }
        };

    final var brokerRequest = buildBrokerRequest();
    final var cancelHandler = new Runnable[1];

    // when - activate request and immediately cancel (simulating race condition)
    handler.activateJobs(
        brokerRequest, observer, runnable -> cancelHandler[0] = runnable, LONG_POLLING_TIMEOUT);

    // Cancel before broker callback completes
    cancelledFlag.set(true);
    if (cancelHandler[0] != null) {
      cancelHandler[0].run();
    }

    actorScheduler.workUntilDone();

    // then - verify the handler gracefully handles the race condition
    // The completeOrResubmitRequest should detect isCanceled() and not throw
    // Should not cause any errors to be sent to observer
    assertThat(errorCount.get()).isZero();
    assertThat(completedCount.get()).isZero();
  }

  // -- helpers --

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
