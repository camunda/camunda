/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.configuration.LongPollingCfg;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.gateway.impl.probe.FakeProbePartitions.Activation;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class LongPollingActivateProbesHandlerTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final String AWS = "validate-configuration/io.camunda:aws-credential:1";
  private static final String SLACK = "validate-configuration/io.camunda:slack:1";
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final long MAX_MESSAGE_SIZE = 4 * 1024 * 1024;

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler = new ControlledActorSchedulerExtension();

  private final FakeProbePartitions partitions = new FakeProbePartitions();
  private LongPollingActivateProbesHandler handler;

  @BeforeEach
  void setUp() {
    // unpinned, the clock also follows wall time, and timers fire early by the time tests take
    actorScheduler.getClock().pinCurrentTime();
    handler = startHandler(MAX_MESSAGE_SIZE, longPolling(true));
  }

  @Test
  void shouldCollectWaitingProbesOfEveryProbeIdWithoutHolding() {
    // given
    final long awsProbe = partitions.addProbe(TENANT_A, 1, AWS);
    final long slackProbe = partitions.addProbe(TENANT_A, 3, SLACK);

    // when
    final var worker = poll(TENANT_A, AWS, SLACK);

    // then
    assertThat(worker.probeKeys()).containsExactlyInAnyOrder(awsProbe, slackProbe);
    assertThat(worker.completed).isTrue();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldHoldOnePollUnderEachOfItsProbeIds() {
    // when
    final var worker = poll(TENANT_A, AWS, SLACK);

    // then - one poll tried every partition once and waits under both probe IDs
    assertThat(worker.completed).isFalse();
    assertThat(partitions.activations()).hasSize(FakeProbePartitions.PARTITIONS);
    assertThat(openPolls()).isOne();
    assertThat(registeredPolls(TENANT_A, AWS)).isOne();
    assertThat(registeredPolls(TENANT_A, SLACK)).isOne();
  }

  @Test
  void shouldWakeAHeldPollOnAProbeOfItsFirstProbeId() {
    shouldWakeAHeldPollOnAProbeOf(AWS);
  }

  @Test
  void shouldWakeAHeldPollOnAProbeOfItsSecondProbeId() {
    shouldWakeAHeldPollOnAProbeOf(SLACK);
  }

  private void shouldWakeAHeldPollOnAProbeOf(final String probeId) {
    // given
    final var worker = poll(TENANT_A, AWS, SLACK);

    // when
    final long probe = partitions.addProbe(TENANT_A, 2, probeId);
    partitions.notifyProbesAvailable(TENANT_A, probeId);
    actorScheduler.workUntilDone();

    // then - the poll is answered and no longer registered under any of its probe IDs
    assertThat(worker.probeKeys()).containsExactly(probe);
    assertThat(worker.completed).isTrue();
    assertThat(registeredPolls(TENANT_A, AWS)).isZero();
    assertThat(registeredPolls(TENANT_A, SLACK)).isZero();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldNotTryPartitionsForAPollWokenUnderAnotherProbeId() {
    // given - a held poll woken by a probe of its first probe ID
    poll(TENANT_A, AWS, SLACK);
    partitions.addProbe(TENANT_A, 2, AWS);
    partitions.notifyProbesAvailable(TENANT_A, AWS);
    actorScheduler.workUntilDone();
    final int activations = partitions.activations().size();

    // when - its other probe ID announces a probe
    partitions.addProbe(TENANT_A, 2, SLACK);
    partitions.notifyProbesAvailable(TENANT_A, SLACK);
    actorScheduler.workUntilDone();

    // then
    assertThat(partitions.activations()).hasSize(activations);
  }

  @Test
  void shouldAnswerAHeldPollWithNoProbeAtItsRequestTimeout() {
    // given
    final var worker = poll(TENANT_A, AWS, SLACK);

    // when
    actorScheduler.updateClock(REQUEST_TIMEOUT);
    actorScheduler.workUntilDone();

    // then
    assertThat(worker.batches).isEmpty();
    assertThat(worker.completed).isTrue();
    assertThat(registeredPolls(TENANT_A, AWS)).isZero();
    assertThat(registeredPolls(TENANT_A, SLACK)).isZero();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldHoldAPollWithoutRequestTimeoutForTheLongPollingTimeout() {
    // given
    final var worker = poll(TENANT_A, 0, AWS);

    // when
    actorScheduler.updateClock(DEFAULT_TIMEOUT.minusMillis(1));
    actorScheduler.workUntilDone();
    final boolean completedBefore = worker.completed;
    actorScheduler.updateClock(Duration.ofMillis(1));
    actorScheduler.workUntilDone();

    // then
    assertThat(completedBefore).isFalse();
    assertThat(worker.completed).isTrue();
  }

  @Test
  void shouldNotHoldAPollWithANegativeRequestTimeout() {
    // when
    final var worker = poll(TENANT_A, -1, AWS);

    // then
    assertThat(worker.completed).isTrue();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldNotHoldAPollWhenLongPollingIsDisabled() {
    // given
    handler = startHandler(MAX_MESSAGE_SIZE, longPolling(false));

    // when
    final var worker = poll(TENANT_A, AWS);

    // then
    assertThat(partitions.activations()).hasSize(FakeProbePartitions.PARTITIONS);
    assertThat(worker.completed).isTrue();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldFailAPollRejectedByAPartition() {
    // given
    partitions.rejectActivations();

    // when
    final var worker = poll(TENANT_A, AWS, SLACK);

    // then
    assertThat(worker.error).isInstanceOf(BrokerRejectionException.class);
    assertThat(partitions.activations()).hasSize(1);
    assertThat(registeredPolls(TENANT_A, AWS)).isZero();
    assertThat(registeredPolls(TENANT_A, SLACK)).isZero();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldFailAPollWithoutProbesWhenAPartitionIsExhausted() {
    // given
    partitions.answerWithError(2, ErrorCode.RESOURCE_EXHAUSTED);

    // when
    final var worker = poll(TENANT_A, AWS);

    // then - every partition was tried, then the worker gets backpressure, as for jobs
    assertThat(partitions.activations()).hasSize(FakeProbePartitions.PARTITIONS);
    assertThat(worker.error).isInstanceOf(ResourceExhausted.class);
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldDeliverCollectedProbesAlthoughAPartitionIsExhausted() {
    // given
    final long probe = partitions.addProbe(TENANT_A, 1, AWS);
    partitions.answerWithError(2, ErrorCode.RESOURCE_EXHAUSTED);

    // when
    final var worker = poll(TENANT_A, AWS);

    // then
    assertThat(worker.probeKeys()).containsExactly(probe);
    assertThat(worker.error).isNull();
  }

  @Test
  void shouldTryAnotherPartitionWhenOneFails() {
    // given
    partitions.answerWithError(1, ErrorCode.PARTITION_LEADER_MISMATCH);
    final long probe = partitions.addProbe(TENANT_A, 2, AWS);

    // when
    final var worker = poll(TENANT_A, AWS);

    // then
    assertThat(worker.probeKeys()).containsExactly(probe);
  }

  @Test
  void shouldDropAHeldPollWhenTheWorkerCancelsIt() {
    // given
    final var worker = poll(TENANT_A, AWS, SLACK);

    // when
    worker.cancel();
    actorScheduler.workUntilDone();

    // then - a later probe does not reach it
    assertThat(registeredPolls(TENANT_A, AWS)).isZero();
    assertThat(registeredPolls(TENANT_A, SLACK)).isZero();
    assertThat(openPolls()).isZero();
    final int activations = partitions.activations().size();
    partitions.addProbe(TENANT_A, 1, AWS);
    partitions.notifyProbesAvailable(TENANT_A, AWS);
    actorScheduler.workUntilDone();
    assertThat(partitions.activations()).hasSize(activations);
    assertThat(worker.batches).isEmpty();
  }

  @Test
  void shouldFailHeldPollsWhenTheClusterIncarnationChanges() {
    // given
    final var first = poll(TENANT_A, AWS);
    final var second = poll(TENANT_B, SLACK);

    // when
    handler.onClusterIncarnationChanged();
    actorScheduler.workUntilDone();

    // then
    assertThat(first.error).hasMessageContaining("Cluster was purged");
    assertThat(second.error).hasMessageContaining("Cluster was purged");
    assertThat(registeredPolls(TENANT_A, AWS)).isZero();
    assertThat(registeredPolls(TENANT_B, SLACK)).isZero();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldYieldProbesWhenTheWorkerIsGoneAndLetTheNextPollCollectThem() {
    // given - the worker's connection breaks while its poll is collecting a probe (AT10)
    final long probe = partitions.addProbe(TENANT_A, 1, AWS);
    final var worker = new Worker();
    partitions.onNextActivation(worker::cancel);

    // when
    poll(TENANT_A, REQUEST_TIMEOUT.toMillis(), worker, AWS);

    // then - the probe is yielded to its partition, as the anonymous user
    assertThat(worker.batches).isEmpty();
    assertThat(partitions.yields())
        .singleElement()
        .satisfies(
            yield -> {
              assertThat(yield.getKey()).isEqualTo(probe);
              assertThat(yield.getPartitionGroup()).isEqualTo(TENANT_A);
              assertThat(yield.getAuthorization().isAnonymous()).isTrue();
            });
    assertThat(openPolls()).isZero();

    // and the next poll collects it
    assertThat(poll(TENANT_A, AWS).probeKeys()).containsExactly(probe);
  }

  @Test
  void shouldYieldProbesWhenSendingThemToTheWorkerFails() {
    // given
    final long probe = partitions.addProbe(TENANT_A, 1, AWS);
    final var worker = new Worker();
    worker.failOnNext = true;

    // when
    poll(TENANT_A, REQUEST_TIMEOUT.toMillis(), worker, AWS);

    // then
    assertThat(partitions.yields()).extracting(yield -> yield.getKey()).containsExactly(probe);
    assertThat(worker.error).hasMessageContaining("Failed to send activated probes to client");
  }

  @Test
  void shouldTryEveryPartitionAgainWhenNotifiedWhileTryingThem() {
    // given - the last partition of the round has not answered yet
    partitions.holdNextResponseOf(FakeProbePartitions.PARTITIONS);
    final var worker = poll(TENANT_A, AWS);

    // when - a partition already tried announces a probe meanwhile
    final long probe = partitions.addProbe(TENANT_A, 1, AWS);
    partitions.notifyProbesAvailable(TENANT_A, AWS);
    actorScheduler.workUntilDone();
    partitions.releaseResponse();
    actorScheduler.workUntilDone();

    // then - the poll collects it without waiting for another notification
    assertThat(worker.probeKeys()).containsExactly(probe);
  }

  @Test
  void shouldYieldProbesBeyondTheMaxMessageSizeAndStopCollecting() {
    // given
    handler = startHandler(1024, longPolling(true));
    final long fitting = partitions.addProbe(TENANT_A, 1, AWS, 600);
    final long tooLarge = partitions.addProbe(TENANT_A, 1, AWS, 600);
    partitions.addProbe(TENANT_A, 2, AWS);

    // when
    final var worker = poll(TENANT_A, AWS);

    // then
    assertThat(worker.probeKeys()).containsExactly(fitting);
    assertThat(partitions.yields()).extracting(yield -> yield.getKey()).containsExactly(tooLarge);
    assertThat(partitions.activations()).hasSize(1);
  }

  @Test
  void shouldPollAPartitionAgainWhenItsResponseIsTruncated() {
    // given
    partitions.truncateAfter(1);
    final long first = partitions.addProbe(TENANT_A, 1, AWS);
    final long second = partitions.addProbe(TENANT_A, 1, AWS);

    // when
    final var worker = poll(TENANT_A, -1, AWS);

    // then
    assertThat(worker.probeKeys()).containsExactly(first, second);
    assertThat(partitions.activations())
        .extracting(Activation::partitionId)
        .containsExactly(1, 1, 2, 3);
  }

  @Test
  void shouldMoveToTheNextPartitionWhenATruncatedResponseHasNoProbe() {
    // given - the engine cannot fit the partition's first probe beside the poll
    partitions.truncateAfter(0);
    partitions.addProbe(TENANT_A, 1, AWS);

    // when
    final var worker = poll(TENANT_A, -1, AWS);

    // then
    assertThat(worker.completed).isTrue();
    assertThat(partitions.activations())
        .extracting(Activation::partitionId)
        .containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  void shouldAnswerAPollWithoutProbesWhenItsFirstProbeIsTooLarge() {
    // given
    handler = startHandler(1024, longPolling(true));
    final long tooLarge = partitions.addProbe(TENANT_A, 1, AWS, 2048);

    // when
    final var worker = poll(TENANT_A, AWS);

    // then - the probe is yielded for the next poll, and this one is not held
    assertThat(partitions.yields()).extracting(yield -> yield.getKey()).containsExactly(tooLarge);
    assertThat(worker.batches).isEmpty();
    assertThat(worker.completed).isTrue();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldStartTheTimeoutOfAPollTriedAgainBecauseOfANotification() {
    // given - a notification during the first round, and a slow partition in the second
    partitions.holdNextResponseOf(FakeProbePartitions.PARTITIONS);
    final var worker = poll(TENANT_A, AWS);
    partitions.notifyProbesAvailable(TENANT_A, AWS);
    actorScheduler.workUntilDone();
    partitions.onNextActivation(() -> partitions.holdNextResponseOf(1));
    partitions.releaseResponse();
    actorScheduler.workUntilDone();

    // when
    actorScheduler.updateClock(REQUEST_TIMEOUT);
    actorScheduler.workUntilDone();
    partitions.releaseResponse();
    actorScheduler.workUntilDone();

    // then
    assertThat(worker.completed).isTrue();
    assertThat(openPolls()).isZero();
  }

  @Test
  void shouldAnswerAPollAtItsTimeoutWhileAPartitionHasNotAnswered() {
    // given - a held poll, woken while a partition is slow
    final var worker = poll(TENANT_A, AWS);
    partitions.holdNextResponseOf(2);
    partitions.notifyProbesAvailable(TENANT_A, AWS);
    actorScheduler.workUntilDone();

    // when
    actorScheduler.updateClock(REQUEST_TIMEOUT);
    actorScheduler.workUntilDone();

    // then
    assertThat(worker.completed).isTrue();
    assertThat(openPolls()).isZero();
    assertThat(registeredPolls(TENANT_A, AWS)).isZero();
  }

  @Test
  void shouldAskEachPartitionForTheProbesStillMissing() {
    // given
    partitions.addProbe(TENANT_A, 1, AWS);

    // when
    poll(TENANT_A, -1, AWS);

    // then
    assertThat(partitions.activations())
        .extracting(Activation::maxProbesToActivate)
        .containsExactly(4, 3, 3);
  }

  @Test
  void shouldOnlySendAPollToThePartitionsOfItsPhysicalTenant() {
    // given - a probe of tenant B (AT14)
    final long probe = partitions.addProbe(TENANT_B, 1, AWS);

    // when
    final var workerOfA = poll(TENANT_A, AWS);
    final var workerOfB = poll(TENANT_B, AWS);

    // then
    assertThat(workerOfA.batches).isEmpty();
    assertThat(workerOfB.probeKeys()).containsExactly(probe);
    assertThat(partitions.activations())
        .filteredOn(activation -> activation.physicalTenantId().equals(TENANT_A))
        .hasSize(FakeProbePartitions.PARTITIONS);
  }

  @Test
  void shouldOnlyWakePollsOfThePhysicalTenantThatAnnouncesTheProbe() {
    // given
    final var workerOfA = poll(TENANT_A, AWS);
    final var workerOfB = poll(TENANT_B, AWS);
    final int activationsOfA = activationsOf(TENANT_A);

    // when
    final long probe = partitions.addProbe(TENANT_B, 2, AWS);
    partitions.notifyProbesAvailable(TENANT_B, AWS);
    actorScheduler.workUntilDone();

    // then
    assertThat(workerOfB.probeKeys()).containsExactly(probe);
    assertThat(workerOfA.completed).isFalse();
    assertThat(activationsOf(TENANT_A)).isEqualTo(activationsOfA);
    assertThat(registeredPolls(TENANT_A, AWS)).isOne();
  }

  @Test
  void shouldStartEachRoundOnTheNextPartition() {
    // when
    poll(TENANT_A, -1, AWS);
    poll(TENANT_A, -1, SLACK);

    // then
    final var activations = partitions.activations();
    assertThat(activations.get(FakeProbePartitions.PARTITIONS).partitionId())
        .isNotEqualTo(activations.getFirst().partitionId());
  }

  private Worker poll(final String physicalTenantId, final String... probeIds) {
    return poll(physicalTenantId, REQUEST_TIMEOUT.toMillis(), probeIds);
  }

  private Worker poll(
      final String physicalTenantId, final long requestTimeout, final String... probeIds) {
    return poll(physicalTenantId, requestTimeout, new Worker(), probeIds);
  }

  private Worker poll(
      final String physicalTenantId,
      final long requestTimeout,
      final Worker worker,
      final String... probeIds) {
    final var request =
        new BrokerActivateProbesRequest(List.of(probeIds))
            .setWorker("worker-a")
            .setMaxProbesToActivate(4)
            .setTenantIds(List.of("<default>"))
            .setTenantFilter(TenantFilter.PROVIDED);
    request.setPartitionGroup(physicalTenantId);
    request.setAuthorization(Map.of(Authorization.AUTHORIZED_USERNAME, "worker-a"));
    handler.activateProbes(request, worker, worker.cancelHandler::set, requestTimeout);
    actorScheduler.workUntilDone();
    return worker;
  }

  private int activationsOf(final String physicalTenantId) {
    return (int)
        partitions.activations().stream()
            .filter(activation -> activation.physicalTenantId().equals(physicalTenantId))
            .count();
  }

  private int registeredPolls(final String physicalTenantId, final String probeId) {
    return handler.registeredPolls(physicalTenantId, probeId);
  }

  private int openPolls() {
    return handler.openPolls();
  }

  private LongPollingActivateProbesHandler startHandler(
      final long maxMessageSize, final LongPollingCfg longPolling) {
    final var newHandler =
        new LongPollingActivateProbesHandler(
            partitions, maxMessageSize, longPolling, ResourceExhausted::new, RuntimeException::new);
    final var ready = new CompletableFuture<Void>();
    actorScheduler.submitActor(
        Actor.newActor()
            .name("probe-long-polling")
            .actorStartedHandler(newHandler.andThen(ignored -> ready.complete(null)))
            .build());
    actorScheduler.workUntilDone();
    ready.join();
    return newHandler;
  }

  private static LongPollingCfg longPolling(final boolean enabled) {
    final var cfg = new LongPollingCfg().setEnabled(enabled);
    cfg.setTimeout(DEFAULT_TIMEOUT.toMillis());
    return cfg;
  }

  private static final class ResourceExhausted extends RuntimeException {
    private ResourceExhausted(final String message) {
      super(message);
    }
  }

  /** The worker's side of the poll, as the REST layer's observer of job activation. */
  private final class Worker implements ResponseObserver<ProbeBatchRecord> {

    private final List<ProbeBatchRecord> batches = new ArrayList<>();
    private final AtomicReference<Runnable> cancelHandler = new AtomicReference<>();
    private boolean completed;
    private boolean cancelled;
    private boolean failOnNext;
    private Throwable error;

    @Override
    public void onCompleted() {
      completed = true;
    }

    @Override
    public void onNext(final ProbeBatchRecord element) {
      if (failOnNext) {
        throw new IllegalStateException("connection reset");
      }
      final var copy = new ProbeBatchRecord();
      copy.copyFrom(element);
      batches.add(copy);
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void onError(final Throwable throwable) {
      error = throwable;
    }

    private void cancel() {
      cancelled = true;
      cancelHandler.get().run();
    }

    private List<Long> probeKeys() {
      return batches.stream().flatMap(batch -> batch.getProbeKeys().stream()).toList();
    }
  }
}
