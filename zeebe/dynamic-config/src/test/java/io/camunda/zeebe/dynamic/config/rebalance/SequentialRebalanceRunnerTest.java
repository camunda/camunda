/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferProtocol;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftError;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.metrics.ClusterRebalanceMetrics;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class SequentialRebalanceRunnerTest {

  private static final String GROUP = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final MemberId COORDINATOR = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");

  /**
   * Short enough to reach by running the scheduled observation a handful of times. The runner
   * accumulates one observation interval per observation, so the wait is measured in observations.
   */
  private static final long OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT = 3;

  private static final Duration LEADER_WAIT_TIMEOUT =
      SequentialRebalanceRunner.LEADERSHIP_OBSERVATION_INTERVAL.multipliedBy(
          (int) OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  // Scheduled tasks are queued rather than run inline, so a test decides when the runner next looks
  // at the topology.
  private final TestConcurrencyControl executor = new TestConcurrencyControl(true);
  private final Map<Integer, MemberId> leaders = new HashMap<>();
  private final RecordingTransfers transfers = new RecordingTransfers();
  private final PartitionLeaders partitionLeaders =
      (physicalTenantId, partitionId) ->
          GROUP.equals(physicalTenantId)
              ? Optional.ofNullable(leaders.get(partitionId))
              : Optional.empty();

  @Test
  void shouldPlanATransferTowardsTheHighestPriorityReplica() {
    // given
    leaders.put(1, MEMBER_1);

    // when
    final var rebalance = planOnly(configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partitions())
        .containsExactly(
            new PartitionRebalance(GROUP, 1, MEMBER_1, MEMBER_2, PartitionRebalanceState.PENDING));
  }

  @Test
  void shouldPlanNoTransferForAPartitionAlreadyLedByItsDesiredLeader() {
    // given
    leaders.put(1, MEMBER_2);

    // when
    final var rebalance = planOnly(configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.SKIPPED);
  }

  @Test
  void shouldReportWhyAPartitionWasLeftAlone() {
    // given
    leaders.put(1, MEMBER_2);

    // when
    final var rebalance = planOnly(configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).reason()).contains("already with the desired leader");
  }

  @Test
  void shouldSkipAPartitionNoMemberIsEligibleToLead() {
    // given
    final var configuration =
        ClusterConfiguration.init()
            .addMember(MEMBER_1, MemberState.uninitialized().addPartition(1, active(1)));

    // when
    final var rebalance = planOnly(configuration);

    // then
    assertThat(rebalance.partition(0).desiredLeader()).isNull();
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.SKIPPED);
  }

  @Test
  void shouldPlanEveryPartitionOfTheConfigurationInOrder() {
    // when
    final var rebalance = planOnly(configurationWithPartitions(3));

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::partitionId)
        .containsExactly(1, 2, 3);
  }

  @Test
  void shouldNotAskAnyLeaderForADryRun() {
    // when
    final var rebalance = planOnly(configurationWithPartitions(2));

    // then
    assertThat(transfers.initiated).isEmpty();
    assertThat(rebalance.partitions())
        .allMatch(partition -> partition.state() == PartitionRebalanceState.PENDING);
  }

  @Test
  void shouldAskTheCurrentLeaderToTransferToTheDesiredLeader() {
    // when
    start(configurationWithPartitions(1));

    // then
    final var initiated = transfers.lastInitiated();
    assertThat(initiated.leader()).isEqualTo(MEMBER_1);
    assertThat(initiated.physicalTenantId()).isEqualTo(GROUP);
    assertThat(initiated.partitionId()).isEqualTo(1);
    assertThat(initiated.request().desiredLeader()).isEqualTo(MEMBER_2);
    assertThat(initiated.request().coordinator()).isEqualTo(COORDINATOR);
    assertThat(initiated.request().correlationId()).isEqualTo(7);
  }

  @Test
  void shouldTellTheLeaderWhichConfigurationTheRebalanceRunsOn() {
    // given
    final var configuration = configurationWithPartitions(1);

    // when
    start(configuration);

    // then
    assertThat(transfers.lastInitiated().request().coordinatorConfigVersion())
        .isEqualTo(configuration.version());
  }

  @Test
  void shouldApplyTheRebalancesOverridesToEachTransfer() {
    // given
    final var overrides = new RebalanceOverrides(4096L, Duration.ofSeconds(30), 5);

    // when
    start(new RebalanceRun(7, overrides, false, configurationWithPartitions(1)));

    // then
    final var request = transfers.lastInitiated().request();
    assertThat(request.replicationLagThreshold()).isEqualTo(4096L);
    assertThat(request.replicationTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(request.maxTransferAttempts()).isEqualTo(5);
  }

  @Test
  void shouldLeaveEachSettingToTheLeaderWhenTheRebalanceOverridesNothing() {
    // when
    start(configurationWithPartitions(1));

    // then
    final var request = transfers.lastInitiated().request();
    assertThat(request.replicationLagThreshold()).isNull();
    assertThat(request.replicationTimeout()).isNull();
    assertThat(request.maxTransferAttempts()).isNull();
  }

  @Test
  void shouldTransferOnlyOnePartitionAtATime() {
    // given
    final var rebalance = start(configurationWithPartitions(2));
    transfers.accept();

    // when
    final var initiatedWhileOneWasInFlight = transfers.initiated.size();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(initiatedWhileOneWasInFlight).isEqualTo(1);
    assertThat(transfers.initiated).hasSize(2);
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.TRANSFERRED);
    assertThat(rebalance.partition(1).state()).isEqualTo(PartitionRebalanceState.TRANSFERRING);
  }

  @Test
  void shouldRecordLeadershipMovingToTheDesiredLeader() {
    // given
    final var rebalance = start(configurationWithPartitions(1));
    transfers.accept();

    // when
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.TRANSFERRED);
    assertThat(rebalance.partition(0).currentLeader()).isEqualTo(MEMBER_2);
  }

  @Test
  void shouldFailAPartitionWhoseLeaderDeclinesTheTransfer() {
    // given
    final var rebalance = start(configurationWithPartitions(2));

    // when
    transfers.decline(LeadershipTransferResult.LAG_TOO_HIGH);

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.FAILED);
    assertThat(transfers.initiated).hasSize(2);
  }

  @Test
  void shouldReportWhyALeaderDeclinedTheTransfer() {
    // given
    final var rebalance = start(configurationWithPartitions(1));

    // when
    transfers.decline(LeadershipTransferResult.LAG_TOO_HIGH);

    // then
    assertThat(rebalance.partition(0).reason()).contains("LAG_TOO_HIGH");
  }

  @Test
  void shouldFailAPartitionWhoseLeaderTheRequestNoLongerReaches() {
    // given
    final var rebalance = start(configurationWithPartitions(1));

    // when
    transfers.answerWithError(RaftError.Type.ILLEGAL_MEMBER_STATE);

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.FAILED);
  }

  @Test
  void shouldFailAPartitionWhoseTransferDidNotMoveLeadership() {
    // given
    final var rebalance = start(configurationWithPartitions(2));
    transfers.accept();

    // when
    transfers.report(LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.FAILED);
    assertThat(transfers.initiated).hasSize(2);
  }

  @Test
  void shouldFailAPartitionWhoseLeaderCannotBeAsked() {
    // given
    final var rebalance = start(configurationWithPartitions(2));

    // when
    transfers.fail(new RuntimeException("no route to the leader"));

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.FAILED);
    assertThat(transfers.initiated).hasSize(2);
  }

  @Test
  void shouldFailAPartitionWithNoLeaderToAsk() {
    // given
    final var configuration = configurationWithPartitions(2);
    leaders.remove(1);

    // when
    final var rebalance = start(configuration);

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.FAILED);
    assertThat(rebalance.partition(0).reason()).contains("no leader to ask");
    assertThat(transfers.lastInitiated().partitionId()).isEqualTo(2);
  }

  @Test
  void shouldIgnoreAResultFromAnotherRebalance() {
    // given
    final var rebalance = start(configurationWithPartitions(1));
    transfers.accept();

    // when
    transfers.reportWithCorrelationId(8, LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.TRANSFERRING);
  }

  @Test
  void shouldMoveOnWhenTheTopologyShowsLeadershipReachedTheDesiredLeader() {
    // given
    final var rebalance = start(configurationWithPartitions(2));
    transfers.accept();

    // when
    leaders.put(1, MEMBER_2);
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.TRANSFERRED);
    assertThat(transfers.lastInitiated().partitionId()).isEqualTo(2);
  }

  @Test
  void shouldKeepWaitingWhileTheTopologyStillShowsTheOldLeader() {
    // given
    final var rebalance = start(configurationWithPartitions(2));
    transfers.accept();

    // when
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.TRANSFERRING);
    assertThat(transfers.initiated).hasSize(1);
    assertThat(executor.scheduledTasks()).isEqualTo(1);
  }

  @Test
  void shouldGiveUpOnAPartitionWhoseLeaderNeverResolvesTheTransfer() {
    // given
    final var rebalance = start(configurationWithPartitions(2));
    transfers.accept();

    // when
    observeUntilTheLeaderWaitTimeoutElapses();

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.FAILED);
  }

  @Test
  void shouldTakeOnTheNextPartitionAfterGivingUpOnOne() {
    // given
    start(configurationWithPartitions(2));
    transfers.accept();

    // when
    observeUntilTheLeaderWaitTimeoutElapses();

    // then
    assertThat(transfers.lastInitiated().partitionId()).isEqualTo(2);
  }

  @Test
  void shouldKeepWaitingOnTheLeaderUntilTheLeaderWaitTimeoutElapses() {
    // given
    final var rebalance = start(configurationWithPartitions(2));
    transfers.accept();

    // when
    for (int observation = 1; observation < OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT; observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.TRANSFERRING);
    assertThat(executor.scheduledTasks()).isEqualTo(1);
  }

  @Test
  void shouldStopWatchingTheTopologyOnceTheLeaderHasReported() {
    // given
    start(configurationWithPartitions(1));
    transfers.accept();
    transfers.report(LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);

    // when
    leaders.put(1, MEMBER_2);
    executor.runAll();

    // then
    assertThat(executor.scheduledTasks()).isZero();
  }

  @Test
  void shouldStopBetweenPartitionsWhenTheRebalanceIsCancelled() {
    // given
    final var rebalance = start(configurationWithPartitions(2));
    transfers.accept();

    // when
    rebalance.requestCancel();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(transfers.initiated).hasSize(1);
    assertThat(rebalance.partition(1).state()).isEqualTo(PartitionRebalanceState.PENDING);
  }

  @Test
  void shouldFinishOnceEveryPartitionHasBeenReached() {
    // given
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configurationWithPartitions(1));

    // when
    final var completion = run(rebalance);
    transfers.accept();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(completion.isDone()).isTrue();
  }

  @Test
  void shouldFinishWhenThereIsNothingToTransfer() {
    // given
    leaders.put(1, MEMBER_2);
    final var rebalance =
        new RebalanceRun(
            7,
            RebalanceOverrides.none(),
            false,
            configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // when
    final var completion = run(rebalance);

    // then
    assertThat(completion.isDone()).isTrue();
    assertThat(transfers.initiated).isEmpty();
  }

  @Test
  void shouldReportWhichStateEachPartitionIsIn() {
    // when
    start(configurationWithPartitions(2));

    // then
    assertThat(partitionStateGauge(1)).isEqualTo(2);
    assertThat(partitionStateGauge(2)).isEqualTo(1);
  }

  @Test
  void shouldAccountForAPartitionByWhatItsLeaderReported() {
    // given
    start(configurationWithPartitions(1));
    transfers.accept();

    // when
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(partitionDurationCount(1, LeadershipTransferResult.TRANSFERRED.name())).isEqualTo(1);
  }

  @Test
  void shouldAccountForAPartitionWhoseLeaderDeclinedByTheReasonItGave() {
    // given
    start(configurationWithPartitions(1));

    // when
    transfers.decline(LeadershipTransferResult.LAG_TOO_HIGH);

    // then
    assertThat(partitionDurationCount(1, LeadershipTransferResult.LAG_TOO_HIGH.name()))
        .isEqualTo(1);
  }

  @Test
  void shouldAccountForAPartitionTheRebalanceHadNoWorkFor() {
    // given
    final var configuration = configurationWithPartitions(2);
    leaders.put(2, MEMBER_2);

    // when
    start(configuration);

    // then
    assertThat(partitionDurationCount(2, PartitionRebalanceResult.ALREADY_BALANCED.name()))
        .isEqualTo(1);
  }

  @Test
  void shouldAccountForAPartitionWhoseLeaderNeverAnswered() {
    // given
    start(configurationWithPartitions(1));
    transfers.accept();

    // when
    observeUntilTheLeaderWaitTimeoutElapses();

    // then
    assertThat(partitionDurationCount(1, PartitionRebalanceResult.LEADER_SILENT.name()))
        .isEqualTo(1);
  }

  @Test
  void shouldAccountForNoPartitionOfADryRun() {
    // when
    planOnly(configurationWithPartitions(2));

    // then
    assertThat(registry.find("zeebe.cluster.rebalance.partition.duration").timers()).isEmpty();
  }

  private double partitionStateGauge(final int partitionId) {
    return registry
        .get("zeebe.cluster.rebalance.partition.state")
        .tag("partition", String.valueOf(partitionId))
        .tag("physicalTenant", GROUP)
        .gauge()
        .value();
  }

  private long partitionDurationCount(final int partitionId, final String result) {
    return registry
        .get("zeebe.cluster.rebalance.partition.duration")
        .tag("partition", String.valueOf(partitionId))
        .tag("physicalTenant", GROUP)
        .tag("result", result)
        .timer()
        .count();
  }

  private RebalanceRun planOnly(final ClusterConfiguration configuration) {
    final var rebalance = new RebalanceRun(7, RebalanceOverrides.none(), true, configuration);
    run(rebalance).join();
    return rebalance;
  }

  /**
   * The runner accumulates one observation interval per observation of the leader, so the wait is
   * reached by running the scheduled task once per interval the timeout covers.
   */
  private void observeUntilTheLeaderWaitTimeoutElapses() {
    for (long observation = 0;
        observation < OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT;
        observation++) {
      executor.runAll();
    }
  }

  private RebalanceRun start(final ClusterConfiguration configuration) {
    return start(new RebalanceRun(7, RebalanceOverrides.none(), false, configuration));
  }

  private RebalanceRun start(final RebalanceRun rebalance) {
    run(rebalance);
    return rebalance;
  }

  private ActorFuture<Void> run(final RebalanceRun rebalance) {
    return new SequentialRebalanceRunner(
            COORDINATOR,
            executor,
            partitionLeaders,
            transfers,
            new ClusterRebalanceMetrics(registry),
            LEADER_WAIT_TIMEOUT)
        .run(rebalance);
  }

  private PartitionState active(final int priority) {
    return PartitionState.active(priority, partitionConfig);
  }

  /**
   * A configuration of {@code count} partitions replicated by both members, where the desired
   * leader is always the second member and the first member is leading.
   */
  private ClusterConfiguration configurationWithPartitions(final int count) {
    final Map<Integer, PartitionState> lowPriority = new HashMap<>();
    final Map<Integer, PartitionState> highPriority = new HashMap<>();
    for (int partitionId = 1; partitionId <= count; partitionId++) {
      lowPriority.put(partitionId, active(1));
      highPriority.put(partitionId, active(2));
      leaders.putIfAbsent(partitionId, MEMBER_1);
    }
    return ClusterConfiguration.init()
        .addMember(MEMBER_1, MemberState.initializeAsActive(lowPriority))
        .addMember(MEMBER_2, MemberState.initializeAsActive(highPriority));
  }

  private ClusterConfiguration configurationWithPriorities(
      final Map<MemberId, Integer> prioritiesForPartitionOne) {
    var configuration = ClusterConfiguration.init();
    for (final var entry : prioritiesForPartitionOne.entrySet()) {
      configuration =
          configuration.addMember(
              entry.getKey(), MemberState.initializeAsActive(Map.of(1, active(entry.getValue()))));
    }
    return configuration;
  }

  /**
   * Stands in for the partition leaders the coordinator talks to, so that a test can answer for the
   * one transfer in flight and then report what became of it.
   */
  private static final class RecordingTransfers implements LeadershipTransferProtocol {

    private final List<Initiated> initiated = new ArrayList<>();
    private final Map<
            Integer,
            Function<
                LeadershipTransferResultRequest,
                CompletableFuture<LeadershipTransferResultResponse>>>
        resultHandlers = new HashMap<>();

    private CompletableFuture<LeadershipTransferInitiateResponse> pending;

    @Override
    public CompletableFuture<LeadershipTransferInitiateResponse> initiate(
        final MemberId leader,
        final String partitionGroup,
        final int partitionId,
        final LeadershipTransferInitiateRequest request) {
      initiated.add(new Initiated(leader, partitionGroup, partitionId, request));
      pending = new CompletableFuture<>();
      return pending;
    }

    @Override
    public void onResult(
        final String partitionGroup,
        final int partitionId,
        final Function<
                LeadershipTransferResultRequest,
                CompletableFuture<LeadershipTransferResultResponse>>
            handler) {
      resultHandlers.put(partitionId, handler);
    }

    Initiated lastInitiated() {
      return initiated.get(initiated.size() - 1);
    }

    /** Answers the transfer in flight with the leader taking it on. */
    void accept() {
      pending.complete(LeadershipTransferInitiateResponse.builder().withStatus(Status.OK).build());
    }

    /** Answers the transfer in flight with the leader refusing it outright. */
    void decline(final LeadershipTransferResult reason) {
      pending.complete(
          LeadershipTransferInitiateResponse.builder()
              .withStatus(Status.OK)
              .withRejectionReason(reason)
              .build());
    }

    /** Answers as a member that is not the leader the coordinator took it for. */
    void answerWithError(final RaftError.Type type) {
      pending.complete(
          LeadershipTransferInitiateResponse.builder()
              .withStatus(Status.ERROR)
              .withError(new RaftError(type, "not the leader"))
              .build());
    }

    void fail(final Throwable error) {
      pending.completeExceptionally(error);
    }

    void report(final LeadershipTransferResult result) {
      reportWithCorrelationId(lastInitiated().request().correlationId(), result);
    }

    void reportWithCorrelationId(final long correlationId, final LeadershipTransferResult result) {
      final var last = lastInitiated();
      resultHandlers
          .get(last.partitionId())
          .apply(
              LeadershipTransferResultRequest.builder()
                  .withLeader(last.leader())
                  .withDesiredLeader(last.request().desiredLeader())
                  .withResult(result)
                  .withCorrelationId(correlationId)
                  .build());
    }

    private record Initiated(
        MemberId leader,
        String physicalTenantId,
        int partitionId,
        LeadershipTransferInitiateRequest request) {}
  }
}
