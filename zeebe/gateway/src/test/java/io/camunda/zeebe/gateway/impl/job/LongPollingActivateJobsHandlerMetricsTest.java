/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.GatewayProtocol;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsFactory;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;

/**
 * Covers the full wiring from {@link LongPollingActivateJobsHandler} through {@link
 * io.camunda.zeebe.gateway.metrics.LongPollingMetricsFactory} down to the actual metrics registry —
 * not just the individual metrics classes in isolation.
 */
final class LongPollingActivateJobsHandlerMetricsTest {

  private static final String TYPE = "testJob";
  private static final long LONG_POLLING_TIMEOUT = 5000;
  private static final long PROBE_TIMEOUT = 20000;
  private static final int FAILED_RESPONSE_THRESHOLD = 3;
  private static final int MAX_JOBS_TO_ACTIVATE = 2;
  private static final long MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4).toBytes();
  private static final String GAUGE_NAME = "zeebe.long.polling.queued.current";

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final StubbedBrokerClient brokerClient = new StubbedBrokerClient();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private LongPollingActivateJobsHandler<Object> handler;
  private ActivateJobsStub activateJobsStub;

  @BeforeEach
  void setUp() {
    handler =
        LongPollingActivateJobsHandler.newBuilder()
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
            .setMetricsFactory(new LongPollingMetricsFactory(meterRegistry, GatewayProtocol.GRPC))
            .build();
    submitHandlerActor(handler);

    activateJobsStub = new ActivateJobsStub();
    activateJobsStub.registerWith(brokerClient);
    activateJobsStub.addAvailableJobs(TYPE, 0);
  }

  @Test
  void shouldTagBlockedRequestsGaugePerPhysicalTenantIndependently() {
    // given — a different number of pending requests per tenant, for the same job type
    submitPendingRequest("tenanta");
    submitPendingRequest("tenanta");
    submitPendingRequest("tenantb");

    // then — each tenant has its own gauge, correctly tagged, with its own count.
    // Different counts prove the tenants aren't sharing a counter under the hood.
    assertThat(blockedRequestsGauge("tenanta")).isEqualTo(2);
    assertThat(blockedRequestsGauge("tenantb")).isEqualTo(1);
  }

  @Test
  void shouldDropBlockedRequestsGaugeBackToZeroOnceUnblocked() {
    // given
    submitPendingRequest("tenanta");
    assertThat(blockedRequestsGauge("tenanta")).isEqualTo(1);

    // when — a job becomes available and the pending request is unblocked
    activateJobsStub.addAvailableJobs(TYPE, 1);
    brokerClient.notifyJobsAvailable("tenanta-jobsAvailable", TYPE);
    actorScheduler.workUntilDone();

    // then
    assertThat(blockedRequestsGauge("tenanta")).isEqualTo(0);
  }

  // -- helpers --

  private Double blockedRequestsGauge(final String physicalTenantId) {
    final var gauge =
        meterRegistry
            .find(GAUGE_NAME)
            .tag("physicalTenantId", physicalTenantId)
            .tag("type", TYPE)
            .gauge();
    assertThat(gauge).describedAs("gauge for tenant %s", physicalTenantId).isNotNull();
    return gauge.value();
  }

  private void submitPendingRequest(final String physicalTenantId) {
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
          public void onCompleted() {}

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
