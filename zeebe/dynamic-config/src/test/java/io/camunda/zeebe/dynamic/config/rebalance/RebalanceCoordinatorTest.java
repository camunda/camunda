/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.metrics.ClusterRebalanceMetrics;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceRequestFailedException.NotCoordinator;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceRequestFailedException.RebalanceInProgress;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class RebalanceCoordinatorTest {

  private static final MemberId LOWEST_ID_MEMBER = MemberId.from("1");
  private static final MemberId OTHER_MEMBER = MemberId.from("2");
  private static final PartitionId PARTITION = new PartitionId("default", 1);
  private static final PartitionRebalance PLAN =
      new PartitionRebalance(
          "default", 1, LOWEST_ID_MEMBER, OTHER_MEMBER, PartitionRebalanceState.PENDING);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ClusterRebalanceMetrics metrics = new ClusterRebalanceMetrics(registry);
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final AtomicLong nextRebalanceId = new AtomicLong(100);

  @Test
  void shouldRefuseRequestsBeforeAnyConfigurationArrives() {
    // given
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, RebalanceRunner.none());

    // when
    final var status = coordinator.getRebalanceStatus();

    // then
    assertThatThrownBy(status::join).hasCauseInstanceOf(NotCoordinator.class);
  }

  @Test
  void shouldRefuseRequestsOnAMemberThatIsNotTheLowestId() {
    // given
    final var coordinator = startCoordinator(OTHER_MEMBER, RebalanceRunner.none());
    configurationWithBothMembers(coordinator);

    // when
    final var triggered =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThatThrownBy(triggered::join).hasCauseInstanceOf(NotCoordinator.class);
  }

  @Test
  void shouldReportTheRebalanceItStarted() {
    // given
    final var coordinator = coordinatingWith(new BlockedRunner());
    final var overrides = new RebalanceOverrides(4096L, Duration.ofSeconds(30), 5, null);

    // when
    final var triggered =
        coordinator.triggerRebalance(new TriggerRebalanceRequest(overrides, false));

    // then
    final var running = triggered.join().running();
    assertThat(running).isNotNull();
    assertThat(running.rebalanceId()).isEqualTo(100);
    assertThat(running.overrides()).isEqualTo(overrides);
    assertThat(running.dryRun()).isFalse();
    assertThat(running.cancelRequested()).isFalse();
    assertThat(triggered.join().lastCompleted()).isNull();
  }

  @Test
  void shouldRefuseASecondRebalanceWhileOneIsRunning() {
    // given
    final var coordinator = coordinatingWith(new BlockedRunner());
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    final var second =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThatThrownBy(second::join)
        .hasCauseInstanceOf(RebalanceInProgress.class)
        .hasMessageContaining("Rebalance 100 is already running");
  }

  @Test
  void shouldAcceptANewRebalanceOnceTheRunningOneFinished() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    runner.finish();
    final var second =
        coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    final var status = second.join();
    assertThat(status.running()).isNotNull();
    assertThat(status.running().rebalanceId()).isEqualTo(101);
    assertThat(status.lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(100, RebalanceOutcome.COMPLETED, false, List.of(PLAN)));
  }

  @Test
  void shouldReportWhereTheRebalanceHasGotToWithEachPartition() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    runner.rebalance.updatePartition(0, partition -> partition.transferred());

    // then
    assertThat(coordinator.getRebalanceStatus().join().running().partitions())
        .containsExactly(PLAN.transferred());
  }

  @Test
  void shouldAnswerADryRunWithThePlanItWouldHaveCarriedOut() {
    // given
    final var coordinator = coordinatingWith(new PlanningRunner());

    // when
    final var triggered =
        coordinator.triggerRebalance(new TriggerRebalanceRequest(RebalanceOverrides.none(), true));

    // then
    final var status = triggered.join();
    assertThat(status.running()).isNull();
    assertThat(status.lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(100, RebalanceOutcome.COMPLETED, true, List.of(PLAN)));
  }

  @Test
  void shouldRunARebalanceAgainstTheConfigurationItWasAdmittedUnder() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);

    // when
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // then
    assertThat(runner.rebalance.configuration().members().keySet())
        .containsExactlyInAnyOrder(LOWEST_ID_MEMBER, OTHER_MEMBER);
  }

  @Test
  void shouldReportARebalanceThatFailedAsFailed() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    runner.fail(new RuntimeException("no leader answered"));
    final var status = coordinator.getRebalanceStatus();

    // then
    assertThat(status.join().running()).isNull();
    assertThat(status.join().lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(100, RebalanceOutcome.FAILED, false, List.of(PLAN)));
  }

  @Test
  void shouldReportHowLongARebalanceTookAndHowItEnded() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    runner.finish();

    // then
    assertThat(elapsed(RebalanceOutcome.COMPLETED).count()).isEqualTo(1);
  }

  @Test
  void shouldKeepReportingWhatBecameOfEachPartitionOnceTheRebalanceHasFinished() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    metrics.setPartitionStates(Map.of(PARTITION, PartitionRebalanceState.TRANSFERRED));

    // when
    runner.finish();

    // then
    assertThat(partitionState()).isEqualTo(3);
  }

  @Test
  void shouldStopReportingWhatARebalanceIsDoingWhenItIsNoLongerTheCoordinator() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    metrics.setPartitionStates(Map.of(PARTITION, PartitionRebalanceState.TRANSFERRED));

    // when
    coordinator.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
            .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of())));

    // then
    assertThat(registry.find("zeebe.cluster.rebalance.partition.state").gauge()).isNull();
  }

  @Test
  void shouldKeepAccountingForRebalancesItRanAfterTheCoordinatorMoves() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    runner.finish();

    // when
    coordinator.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
            .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of())));

    // then
    assertThat(elapsed(RebalanceOutcome.COMPLETED).count()).isEqualTo(1);
  }

  @Test
  void shouldPublishRebalanceCountsBeforeThereIsAnythingToCount() {
    // given
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, RebalanceRunner.none());

    // when
    configurationWithBothMembers(coordinator);

    // then
    assertThat(RebalanceOutcome.values())
        .allSatisfy(outcome -> assertThat(elapsed(outcome).count()).isZero());
  }

  @Test
  void shouldNotPublishRebalanceCountsOnAMemberThatDoesNotCoordinate() {
    // given
    final var coordinator = startCoordinator(OTHER_MEMBER, RebalanceRunner.none());

    // when
    configurationWithBothMembers(coordinator);

    // then
    assertThat(registry.find("zeebe.cluster.rebalance.elapsed").timers()).isEmpty();
  }

  private double partitionState() {
    return registry.get("zeebe.cluster.rebalance.partition.state").gauge().value();
  }

  private Timer elapsed(final RebalanceOutcome outcome) {
    return registry.get("zeebe.cluster.rebalance.elapsed").tag("result", outcome.name()).timer();
  }

  @Test
  void shouldStopTheRunningRebalanceOnCancellation() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    final var cancelled = coordinator.cancelRebalance();

    // then
    assertThat(cancelled.join().wasRunning()).isTrue();
    assertThat(runner.rebalance.isCancelRequested()).isTrue();
  }

  @Test
  void shouldReportACancelledRebalanceAsCancelled() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    coordinator.cancelRebalance();

    // when
    runner.finish();
    final var status = coordinator.getRebalanceStatus();

    // then
    assertThat(status.join().lastCompleted())
        .isEqualTo(
            new RebalanceStatus.Completed(100, RebalanceOutcome.CANCELLED, false, List.of(PLAN)));
  }

  @Test
  void shouldReportNothingWasRunningWhenCancellingWhileIdle() {
    // given
    final var coordinator = coordinatingWith(RebalanceRunner.none());

    // when
    final var cancelled = coordinator.cancelRebalance();

    // then
    assertThat(cancelled.join().wasRunning()).isFalse();
  }

  @Test
  void shouldDiscardItsStateWhenItIsNoLongerTheCoordinator() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    coordinator.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
            .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of())));

    // then
    final var status = coordinator.getRebalanceStatus();
    assertThatThrownBy(status::join).hasCauseInstanceOf(NotCoordinator.class);
  }

  @Test
  void shouldAbandonTheRebalanceInFlightWhenItIsNoLongerTheCoordinator() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());

    // when
    coordinator.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
            .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of())));

    // then
    assertThat(runner.rebalance.isAbandoned()).isTrue();
  }

  @Test
  void shouldNotReportTheAbandonedRebalanceWhenItBecomesTheCoordinatorAgain() {
    // given
    final var runner = new BlockedRunner();
    final var coordinator = coordinatingWith(runner);
    coordinator.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings());
    coordinator.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
            .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of())));

    // when
    configurationWithBothMembers(coordinator);
    runner.finish();

    // then
    final var status = coordinator.getRebalanceStatus();
    assertThat(status.join()).isEqualTo(RebalanceStatus.idle());
  }

  private RebalanceCoordinator coordinatingWith(final RebalanceRunner runner) {
    final var coordinator = startCoordinator(LOWEST_ID_MEMBER, runner);
    configurationWithBothMembers(coordinator);
    return coordinator;
  }

  private RebalanceCoordinator startCoordinator(
      final MemberId localMemberId, final RebalanceRunner runner) {
    return new RebalanceCoordinator(
        localMemberId, executor, runner, nextRebalanceId::getAndIncrement, metrics);
  }

  private void configurationWithBothMembers(final RebalanceCoordinator coordinator) {
    coordinator.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(LOWEST_ID_MEMBER, MemberState.initializeAsActive(Map.of()))
            .addMember(OTHER_MEMBER, MemberState.initializeAsActive(Map.of())));
  }

  /**
   * A rebalance that runs until the test says it is done, so a test can act while one is running.
   */
  private static final class BlockedRunner implements RebalanceRunner {

    private final CompletableActorFuture<Void> completion = new CompletableActorFuture<>();
    private RebalanceRun rebalance;

    @Override
    public ActorFuture<Void> run(final RebalanceRun rebalance) {
      this.rebalance = rebalance;
      rebalance.plan(List.of(PLAN));
      return completion;
    }

    void finish() {
      completion.complete(null);
    }

    void fail(final Throwable error) {
      completion.completeExceptionally(error);
    }
  }

  /** A rebalance that plans its partitions and is over by the time it returns, as a dry run is. */
  private static final class PlanningRunner implements RebalanceRunner {

    @Override
    public ActorFuture<Void> run(final RebalanceRun rebalance) {
      rebalance.plan(List.of(PLAN));
      return CompletableActorFuture.completed();
    }
  }
}
