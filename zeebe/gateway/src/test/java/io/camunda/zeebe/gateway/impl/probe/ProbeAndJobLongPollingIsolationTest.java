/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probe;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.configuration.LongPollingCfg;
import io.camunda.zeebe.gateway.impl.job.JobActivationResponse;
import io.camunda.zeebe.gateway.impl.job.JobActivationResult;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsFactory;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A job worker and a probe worker in the same gateway (AT15): a probe never wakes a job poll, and a
 * job never wakes a probe poll, even when the job type and the probe ID are the same string.
 */
final class ProbeAndJobLongPollingIsolationTest {

  private static final String NAME = "validate-configuration/io.camunda:aws-credential:1";
  private static final long REQUEST_TIMEOUT = Duration.ofSeconds(10).toMillis();

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final FakeProbePartitions partitions = new FakeProbePartitions();
  private LongPollingActivateProbesHandler probeHandler;
  private LongPollingActivateJobsHandler<Object> jobHandler;

  @BeforeEach
  void setUp() {
    probeHandler =
        new LongPollingActivateProbesHandler(
            partitions,
            4 * 1024 * 1024,
            new LongPollingCfg(),
            RuntimeException::new,
            RuntimeException::new);
    jobHandler =
        LongPollingActivateJobsHandler.<Object>newBuilder()
            .setBrokerClient(partitions)
            .setMaxMessageSize(4 * 1024 * 1024)
            .setLongPollingTimeout(REQUEST_TIMEOUT)
            .setNotificationBatchWindow(Duration.ZERO)
            .setActivationResultMapper(ProbeAndJobLongPollingIsolationTest::noJobs)
            .setResourceExhaustedExceptionProvider(RuntimeException::new)
            .setRequestCanceledExceptionProvider(RuntimeException::new)
            .setMetricsFactory(LongPollingMetricsFactory.noop())
            .build();
    start(probeHandler);
    start(jobHandler);

    // a held job poll and a held probe poll, for the same name
    pollJobs();
    pollProbes();
  }

  @Test
  void shouldNotWakeAJobPollOnAProbeNotification() {
    // given
    final int jobActivations = partitions.jobActivations();
    final int probeActivations = partitions.activations().size();

    // when
    partitions.notifyProbesAvailable(DEFAULT_PHYSICAL_TENANT_ID, NAME);
    actorScheduler.workUntilDone();

    // then
    assertThat(partitions.jobActivations()).isEqualTo(jobActivations);
    assertThat(partitions.activations()).hasSizeGreaterThan(probeActivations);
  }

  @Test
  void shouldNotWakeAProbePollOnAJobNotification() {
    // given
    final int jobActivations = partitions.jobActivations();
    final int probeActivations = partitions.activations().size();

    // when - on the scoped and on the legacy job topic
    partitions.notify(DEFAULT_PHYSICAL_TENANT_ID + "-jobsAvailable", NAME);
    partitions.notify("jobsAvailable", NAME);
    actorScheduler.workUntilDone();

    // then
    assertThat(partitions.activations()).hasSize(probeActivations);
    assertThat(partitions.jobActivations()).isGreaterThan(jobActivations);
  }

  private void pollJobs() {
    final var request =
        new BrokerActivateJobsRequest(NAME)
            .setMaxJobsToActivate(1)
            .setTimeout(REQUEST_TIMEOUT)
            .setTenantIds(List.of())
            .setVariables(List.of())
            .setWorker("worker");
    request.setPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
    jobHandler.activateJobs(request, new OpenObserver<>(), ignored -> {}, REQUEST_TIMEOUT);
    actorScheduler.workUntilDone();
  }

  private void pollProbes() {
    final var request =
        new BrokerActivateProbesRequest(List.of(NAME))
            .setWorker("worker")
            .setMaxProbesToActivate(1)
            .setTenantFilter(TenantFilter.PROVIDED);
    request.setPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
    probeHandler.activateProbes(request, new OpenObserver<>(), ignored -> {}, REQUEST_TIMEOUT);
    actorScheduler.workUntilDone();
  }

  private void start(final Consumer<ActorControl> handler) {
    final var ready = new CompletableFuture<Void>();
    actorScheduler.submitActor(
        Actor.newActor()
            .name("handler-" + handler.getClass().getSimpleName())
            .actorStartedHandler(handler.andThen(ignored -> ready.complete(null)))
            .build());
    actorScheduler.workUntilDone();
    ready.join();
  }

  private static JobActivationResult<Object> noJobs(final JobActivationResponse response) {
    return new JobActivationResult<>() {
      @Override
      public int getJobsCount() {
        return 0;
      }

      @Override
      public List<ActivatedJob> getJobs() {
        return List.of();
      }

      @Override
      public Object getActivateJobsResponse() {
        return response;
      }

      @Override
      public List<ActivatedJob> getJobsToDefer() {
        return List.of();
      }
    };
  }

  /** A worker that stays connected and ignores what it gets. */
  private static final class OpenObserver<T> implements ResponseObserver<T> {

    @Override
    public void onCompleted() {}

    @Override
    public void onNext(final T element) {}

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void onError(final Throwable throwable) {}
  }
}
