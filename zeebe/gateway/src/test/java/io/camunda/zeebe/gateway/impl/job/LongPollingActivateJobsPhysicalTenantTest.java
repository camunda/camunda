/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsFactory;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;

/** Covers physical-tenant scoping of job-available notifications (issue #56224). */
final class LongPollingActivateJobsPhysicalTenantTest {

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
                        return response.brokerResponse().getJobKeys().size();
                      }

                      @Override
                      public List<JobActivationResult.ActivatedJob> getJobs() {
                        return Collections.emptyList();
                      }

                      @Override
                      public Object getActivateJobsResponse() {
                        return response;
                      }

                      @Override
                      public List<JobActivationResult.ActivatedJob> getJobsToDefer() {
                        return Collections.emptyList();
                      }
                    })
            .setResourceExhaustedExceptionProvider(RuntimeException::new)
            .setRequestCanceledExceptionProvider(RuntimeException::new)
            .setMetricsFactory(LongPollingMetricsFactory.noop())
            .build();
    submitHandlerActor(handler);

    activateJobsStub = new ActivateJobsStub();
    activateJobsStub.registerWith(brokerClient);
    activateJobsStub.addAvailableJobs(TYPE, 0);
  }

  @Test
  void shouldNotWakeRequestOfAnotherPhysicalTenant() {
    // given — one pending request per tenant, for the same job type
    final var tenantACompletions = new AtomicInteger();
    final var tenantBCompletions = new AtomicInteger();
    submitPendingRequest("tenanta", tenantACompletions);
    submitPendingRequest("tenantb", tenantBCompletions);

    // when — a job becomes available and only tenanta's topic is notified
    activateJobsStub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable("tenanta-jobsAvailable", TYPE);
    actorScheduler.workUntilDone();

    // then — only the matching tenant's request is unblocked
    assertThat(tenantACompletions.get()).isEqualTo(1);
    assertThat(tenantBCompletions.get()).isEqualTo(0);
  }

  @Test
  void shouldWakeDefaultTenantRequestViaLegacyTopic() {
    // given — default tenant listens on both the scoped and the legacy prefix-less topic
    final var completions = new AtomicInteger();
    submitPendingRequest(DEFAULT_PHYSICAL_TENANT_ID, completions);

    // when
    activateJobsStub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable(TYPE);
    actorScheduler.workUntilDone();

    // then
    assertThat(completions.get()).isEqualTo(1);
  }

  @Test
  void shouldWakeDefaultTenantRequestViaScopedTopic() {
    // given — default tenant also listens on its own new, prefixed topic, not only the legacy one
    final var completions = new AtomicInteger();
    submitPendingRequest(DEFAULT_PHYSICAL_TENANT_ID, completions);

    // when
    activateJobsStub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable("default-jobsAvailable", TYPE);
    actorScheduler.workUntilDone();

    // then
    assertThat(completions.get()).isEqualTo(1);
  }

  // -- helpers --

  private void submitPendingRequest(
      final String physicalTenantId, final AtomicInteger completions) {
    final var brokerRequest =
        new BrokerActivateJobsRequest(TYPE)
            .setMaxJobsToActivate(MAX_JOBS_TO_ACTIVATE)
            .setTimeout(LONG_POLLING_TIMEOUT)
            .setTenantIds(Collections.emptyList())
            .setVariables(Collections.emptyList())
            .setWorker("test-worker");
    brokerRequest.setPartitionGroup(physicalTenantId);

    final ResponseObserver<Object> observer =
        new ResponseObserver<>() {
          @Override
          public void onCompleted() {
            completions.incrementAndGet();
          }

          @Override
          public void onNext(final Object element) {}

          @Override
          public boolean isCancelled() {
            return false;
          }

          @Override
          public void onError(final Throwable throwable) {}
        };

    handler.activateJobs(brokerRequest, observer, cancelHandler -> {}, LONG_POLLING_TIMEOUT);
    actorScheduler.workUntilDone();
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
}
