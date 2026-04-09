/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;

final class LongPollingActivateJobsHandlerPurgeTest {

  private static final String TYPE = "testJob";
  private static final long LONG_POLLING_TIMEOUT = 5000;
  private static final long PROBE_TIMEOUT = 20000;
  // threshold of 3: after 3 failed attempts the next request goes pending immediately
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
  void shouldBeNoOpWhenActorNotStarted() {
    // handler.actor is null because we never submitted it to the scheduler
    assertThatCode(handler::onClusterIncarnationChanged).doesNotThrowAnyException();
  }

  @Test
  void shouldFailPendingRequestsOnPurge() throws Exception {
    // Start the handler actor
    submitHandlerActor(handler);

    // Build a request and put it in pending state.
    // We exhaust the failure threshold first so the next internalActivateJobsRetry
    // enqueues the request as pending rather than trying the broker again.
    final var errorRef = new AtomicReference<Throwable>();
    final InflightActivateJobsRequest<Object> pendingRequest = buildRequest(errorRef);

    // Exhaust the threshold via internalActivateJobsRetry calls
    // (each call triggers tryToActivateJobsOnAllPartitions which returns 0 jobs and
    //  increments the failed attempt counter)
    for (int i = 0; i < FAILED_RESPONSE_THRESHOLD; i++) {
      handler.internalActivateJobsRetry(pendingRequest);
      actorScheduler.workUntilDone();
    }

    // After threshold is reached the next retry marks the request as pending
    handler.internalActivateJobsRetry(pendingRequest);
    actorScheduler.workUntilDone();

    // Trigger purge
    handler.onClusterIncarnationChanged();
    actorScheduler.workUntilDone();

    // The response observer should have received an error mentioning "purge"
    assertThat(errorRef.get()).isNotNull();
    assertThat(errorRef.get().getMessage()).containsIgnoringCase("purge");
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

  private InflightActivateJobsRequest<Object> buildRequest(
      final AtomicReference<Throwable> errorRef) {
    final var brokerRequest = mock(BrokerActivateJobsRequest.class);
    final var requestWriter = new JobBatchRecord();
    requestWriter.setType(TYPE);
    requestWriter.setWorker("test-worker");
    requestWriter.setMaxJobsToActivate(MAX_JOBS_TO_ACTIVATE);
    when(brokerRequest.getRequestWriter()).thenReturn(requestWriter);
    when(brokerRequest.getPartitionId()).thenReturn(1);

    final ResponseObserver<Object> observer =
        new ResponseObserver<>() {
          @Override
          public void onCompleted() {}

          @Override
          public void onNext(final Object element) {}

          @Override
          public boolean isCancelled() {
            return false;
          }

          @Override
          public void onError(final Throwable throwable) {
            errorRef.set(throwable);
          }
        };

    return new InflightActivateJobsRequest<>(1L, brokerRequest, observer, LONG_POLLING_TIMEOUT);
  }
}
