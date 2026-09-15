/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferProtocol;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftError;
import io.atomix.raft.RebalanceConfiguration;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class SequentialRebalanceRunnerTest {

  private static final String GROUP = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final MemberId COORDINATOR = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");
  private static final MemberId MEMBER_3 = MemberId.from("3");

  private static final long OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT = 6;

  private static final Duration LEADER_WAIT_TIMEOUT =
      SequentialRebalanceRunner.LEADERSHIP_OBSERVATION_INTERVAL.multipliedBy(
          (int) OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT);

  private static final Duration TEST_HEARTBEAT_INTERVAL = Duration.ofSeconds(1);

  private static final RebalanceConfiguration TEST_CONFIGURATION =
      new RebalanceConfiguration(1024L, Duration.ofSeconds(2), 1);

  private static final Duration TRANSFER_WATCHDOG_TIMEOUT =
      TEST_CONFIGURATION
          .pauseBudget(TEST_HEARTBEAT_INTERVAL)
          .plus(SequentialRebalanceRunner.COORDINATOR_WATCHDOG_SLACK);

  private static final long OBSERVATIONS_UNTIL_WATCHDOG_TIMEOUT =
      TRANSFER_WATCHDOG_TIMEOUT.toMillis()
          / SequentialRebalanceRunner.LEADERSHIP_OBSERVATION_INTERVAL.toMillis();

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final TestConcurrencyControl executor = new TestConcurrencyControl(true);
  private final Map<String, Map<Integer, MemberId>> leaders = new HashMap<>();
  private final Set<String> unavailableGroups = new HashSet<>();
  private final Map<String, Integer> groupLookups = new HashMap<>();
  private final RecordingTransfers transfers = new RecordingTransfers();
  private final PartitionLeaders partitionLeaders =
      physicalTenantId -> {
        groupLookups.merge(physicalTenantId, 1, Integer::sum);
        if (unavailableGroups.contains(physicalTenantId)) {
          throw new IllegalStateException(
              "Topology for physical tenant %s is unavailable".formatted(physicalTenantId));
        }
        final var groupLeaders = leaders.getOrDefault(physicalTenantId, Map.of());
        return partitionId -> Optional.ofNullable(groupLeaders.get(partitionId));
      };

  @Test
  void shouldPlanATransferTowardsTheHighestPriorityReplica() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);

    // when
    final var rebalance = planDryRun(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partitions())
        .containsExactly(PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2));
  }

  @Test
  void shouldPlanNoTransferForAPartitionAlreadyLedByItsDesiredLeader() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_2);

    // when
    final var rebalance = planDryRun(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome())
        .isEqualTo(PartitionRebalanceOutcome.ALREADY_LEADER);
  }

  @Test
  void shouldPlanATransferForAPartitionWithNoLeaderAtAll() {
    // given / when
    final var rebalance = planDryRun(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).currentLeader()).isNull();
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.PENDING);
  }

  @Test
  void shouldPlanAPendingEntryWithNoCurrentLeaderWhenTheKnownTopologyHasNoLeaderForThePartition() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());

    // when
    final var rebalance = planDryRun(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).currentLeader()).isNull();
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.PENDING);
  }

  @Test
  void shouldFailPlanningWhenTheGroupTopologyIsUnavailable() {
    // given
    unavailableGroups.add(GROUP);

    // when / then
    assertThatThrownBy(
            () -> planDryRun(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldObtainTheGroupTopologyOnceForAllOfItsPartitions() {
    // given
    final var configuration =
        configurationOf(
            Map.of(
                GROUP,
                Map.of(
                    MEMBER_1,
                    Map.of(
                        1, active(1),
                        2, active(1),
                        3, active(1)))));

    // when
    planDryRun(configuration);

    // then
    assertThat(groupLookups).containsEntry(GROUP, 1);
  }

  @Test
  void shouldNotPlanThePartitionsOfADisabledPhysicalTenant() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
                Map.of(
                    "tenant-a",
                        Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2))),
                    "tenant-b",
                        Map.of(MEMBER_1, Map.of(1, active(2)), MEMBER_2, Map.of(1, active(1)))))
            .updatePartitionGroupConfig("tenant-b", PartitionGroupConfiguration::disable);

    // when
    final var rebalance = planDryRun(configuration);

    // then
    assertThat(rebalance.partitions())
        .containsExactly(PartitionRebalance.pending("tenant-a", 1, MEMBER_1, MEMBER_2));
  }

  @Test
  void shouldNotLookUpTheTopologyOfADisabledPhysicalTenant() {
    // given
    unavailableGroups.add("tenant-b");
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
                Map.of(
                    "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1))),
                    "tenant-b", Map.of(MEMBER_1, Map.of(1, active(1)))))
            .updatePartitionGroupConfig("tenant-b", PartitionGroupConfiguration::disable);

    // when
    final var rebalance = planDryRun(configuration);

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::physicalTenantId)
        .containsExactly("tenant-a");
  }

  @Test
  void shouldNotPlanThePartitionsOfARecoveringPhysicalTenant() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
                Map.of(
                    "tenant-a",
                        Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2))),
                    "tenant-b",
                        Map.of(MEMBER_1, Map.of(1, active(2)), MEMBER_2, Map.of(1, active(1)))))
            .updatePartitionGroupConfig("tenant-b", SequentialRebalanceRunnerTest::recovering);

    // when
    final var rebalance = planDryRun(configuration);

    // then
    assertThat(rebalance.partitions())
        .containsExactly(PartitionRebalance.pending("tenant-a", 1, MEMBER_1, MEMBER_2));
  }

  @Test
  void shouldNotLookUpTheTopologyOfARecoveringPhysicalTenant() {
    // given
    unavailableGroups.add("tenant-b");
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
                Map.of(
                    "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1))),
                    "tenant-b", Map.of(MEMBER_1, Map.of(1, active(1)))))
            .updatePartitionGroupConfig("tenant-b", SequentialRebalanceRunnerTest::recovering);

    // when
    final var rebalance = planDryRun(configuration);

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::physicalTenantId)
        .containsExactly("tenant-a");
  }

  @Test
  void shouldPlanATransferWithNoOutcomeYet() {
    // given / when
    final var rebalance = planDryRun(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).outcome()).isNull();
  }

  @Test
  void shouldPlanEveryPartitionOfTheConfigurationInOrder() {
    // given
    final var configuration =
        configurationOf(
            Map.of(
                GROUP,
                Map.of(
                    MEMBER_1,
                    Map.of(
                        3, active(1),
                        1, active(1),
                        2, active(1)))));

    // when
    final var rebalance = planDryRun(configuration);

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::partitionId)
        .containsExactly(1, 2, 3);
  }

  @Test
  void shouldPlanEveryPhysicalTenantsPartitionGroupIndependently() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    leaders.computeIfAbsent("tenant-b", ignored -> new HashMap<>()).put(1, MEMBER_2);
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2))),
                "tenant-b",
                    Map.of(MEMBER_1, Map.of(1, active(2)), MEMBER_2, Map.of(1, active(1)))));

    // when
    final var rebalance = planDryRun(configuration);

    // then
    assertThat(rebalance.partitions())
        .containsExactlyInAnyOrder(
            PartitionRebalance.pending("tenant-a", 1, MEMBER_1, MEMBER_2),
            PartitionRebalance.pending("tenant-b", 1, MEMBER_2, MEMBER_1));
  }

  @Test
  void shouldSortThePlanByPhysicalTenantIdThenPartitionId() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    leaders.computeIfAbsent("tenant-b", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-b", Map.of(MEMBER_1, Map.of(2, active(1), 1, active(1))),
                "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1)))));

    // when
    final var rebalance = planDryRun(configuration);

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::physicalTenantId, PartitionRebalance::partitionId)
        .containsExactly(tuple("tenant-a", 1), tuple("tenant-b", 1), tuple("tenant-b", 2));
  }

  @Test
  void shouldNotAskAnyLeaderForADryRun() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);

    // when
    final var rebalance = planDryRun(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(transfers.initiated).isEmpty();
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.PENDING);
  }

  @Test
  void shouldReturnTheCompletePlanForADryRunWithoutTouchingExecutionMetrics() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_2);
    groupLeaders.put(2, MEMBER_1);
    start(twoPartitionsConfiguration());
    final var partitionStateGaugeBefore = partitionStateGauge(1);
    final var partitionDurationSamplesBefore = totalPartitionDurationSamples();

    // when
    final var rebalance = planDryRun(twoPartitionsConfiguration());

    // then
    assertThat(rebalance.partitions()).hasSize(2);
    assertThat(partitionStateGauge(1))
        .as("a dry run must not replace the gauges left behind by a real rebalance")
        .isEqualTo(partitionStateGaugeBefore);
    assertThat(totalPartitionDurationSamples())
        .as("a dry run must not record any partition-duration sample")
        .isEqualTo(partitionDurationSamplesBefore);
  }

  private long totalPartitionDurationSamples() {
    return registry.find("zeebe.cluster.rebalance.partition.duration").timers().stream()
        .mapToLong(Timer::count)
        .sum();
  }

  @Test
  void shouldAskTheCurrentLeaderToTransferToTheDesiredLeader() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);

    // when
    start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

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
  void shouldSendTheGlobalConfigVersion() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        groupConfiguration(GROUP, Map.of(COORDINATOR, 1, MEMBER_1, 2, MEMBER_2, 3));

    // when
    start(configuration);

    // then
    final var leaderCheck = new ClusterConfigurationCoordinatorCheck(() -> configuration);
    assertThat(
            leaderCheck.validate(
                COORDINATOR, transfers.lastInitiated().request().coordinatorConfigVersion()))
        .isEmpty();
  }

  @Test
  void shouldApplyTheRebalancesOverridesToEachTransfer() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var overrides =
        new RebalanceOverrides(4096L, Duration.ofSeconds(30), 5, Duration.ofSeconds(40));
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));

    // when
    start(new RebalanceRun(7, overrides, false, configuration, Instant.EPOCH));

    // then
    final var request = transfers.lastInitiated().request();
    assertThat(request.replicationLagThreshold()).isEqualTo(4096L);
    assertThat(request.replicationTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(request.maxTransferAttempts()).isEqualTo(5);
  }

  @Test
  void shouldStartARebalanceWhenLeaderWaitTimeoutIsShorterThanTheTransferBudget() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);

    // when
    run(rebalance, LEADER_WAIT_TIMEOUT);

    // then
    assertThat(transfers.initiated).hasSize(1);
  }

  @Test
  void shouldUseARequestLevelLeaderWaitTimeoutOverrideOnlyForTheMissingLeaderWait() {
    // given
    final var overrideObservations = OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT - 2;
    final var overrideTimeout =
        SequentialRebalanceRunner.LEADERSHIP_OBSERVATION_INTERVAL.multipliedBy(
            (int) overrideObservations);
    final var overrides = new RebalanceOverrides(null, null, null, overrideTimeout);
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance = new RebalanceRun(7, overrides, false, configuration, Instant.EPOCH);

    // when
    start(rebalance);
    for (long observation = 0; observation < overrideObservations; observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.NO_LEADER);
    assertThat(transfers.initiated).isEmpty();
  }

  @Test
  void shouldNotChangeWhenNoResponseOccursWhenLeaderWaitTimeoutIsOverridden() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var shortLeaderWaitTimeout = SequentialRebalanceRunner.LEADERSHIP_OBSERVATION_INTERVAL;
    final var overrides = new RebalanceOverrides(null, null, null, shortLeaderWaitTimeout);
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance = new RebalanceRun(7, overrides, false, configuration, Instant.EPOCH);
    start(rebalance);
    transfers.accept();

    // when
    for (long observation = 0;
        observation < OBSERVATIONS_UNTIL_WATCHDOG_TIMEOUT - 1;
        observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);

    // when
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.NO_RESPONSE);
  }

  @Test
  void shouldDeriveTheWatchdogFromRequestOverridesOfReplicationTimeoutAndMaxTransferAttempts() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var overrides = new RebalanceOverrides(null, Duration.ofSeconds(10), null, null);
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance = new RebalanceRun(7, overrides, false, configuration, Instant.EPOCH);
    start(rebalance);
    transfers.accept();

    // when
    for (long observation = 0; observation < OBSERVATIONS_UNTIL_WATCHDOG_TIMEOUT; observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
  }

  @Test
  void shouldNotResolveNoResponseBeforeTheLeaderSidePauseWatchdogCouldRecoverThePartition() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));
    transfers.accept();
    final var pauseBudgetObservations =
        TEST_CONFIGURATION.pauseBudget(TEST_HEARTBEAT_INTERVAL).toSeconds();

    // when
    for (long observation = 0; observation < pauseBudgetObservations; observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
  }

  @Test
  void shouldTransferOnlyOnePartitionAtATime() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var rebalance = start(twoPartitionsConfiguration());
    transfers.accept();

    // when
    final var initiatedWhileOneWasInFlight = transfers.initiated.size();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(initiatedWhileOneWasInFlight).isEqualTo(1);
    assertThat(transfers.initiated).hasSize(2);
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
    assertThat(rebalance.partition(1).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
  }

  @Test
  void shouldRecordLeadershipMovingToTheDesiredLeader() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));
    transfers.accept();

    // when
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
    assertThat(rebalance.partition(0).currentLeader()).isEqualTo(MEMBER_2);
  }

  @Test
  void shouldMapEveryDeclinedTransferToTheIdenticallyNamedOutcome() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // when
    transfers.decline(LeadershipTransferResult.LAG_TOO_HIGH);

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.LAG_TOO_HIGH);
  }

  @Test
  void shouldMapEveryReportedResultToTheIdenticallyNamedOutcome() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));
    transfers.accept();

    // when
    transfers.report(LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);

    // then
    assertThat(rebalance.partition(0).outcome())
        .isEqualTo(PartitionRebalanceOutcome.TIMEOUT_NOW_EXHAUSTED);
  }

  @Test
  void shouldNotThrowWhenInitiationRespondsWithErrorAndNoRejectionReason() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // when
    transfers.error(RaftError.Type.ILLEGAL_MEMBER_STATE);
    leaders.get(GROUP).put(1, MEMBER_2);
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
  }

  @Test
  void shouldWaitForATopologyResponseWhenTheInitiateRequestCannotBeAnswered() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // when
    transfers.fail(new RuntimeException("no route to the leader"));

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
  }

  @Test
  void shouldCompleteWithNoLeaderWhenNoneAppearsWithinTheLeaderWaitTimeout() {
    // given
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // when
    for (long observation = 0;
        observation < OBSERVATIONS_UNTIL_LEADER_WAIT_TIMEOUT;
        observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.NO_LEADER);
    assertThat(transfers.initiated).isEmpty();
  }

  @Test
  void shouldInitiateOnceALeaderAppearsWhileWaiting() {
    // given
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // when
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.lastInitiated().leader()).isEqualTo(MEMBER_1);
  }

  @Test
  void shouldNotInitiateATransferWhenCancelledWhileWaitingForALeader() {
    // given
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    final var completion = run(rebalance);

    // when
    rebalance.requestCancel();
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    executor.runAll();

    // then
    assertThat(transfers.initiated).isEmpty();
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.CANCELLED);
    assertThat(completion.isDone()).isTrue();
  }

  @Test
  void shouldStopWaitingForALeaderOnceThePhysicalTenantIsDisabled() {
    // given
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    final var completion = run(rebalance);

    // when
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig(GROUP, PartitionGroupConfiguration::disable));
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome())
        .isEqualTo(PartitionRebalanceOutcome.PHYSICAL_TENANT_DISABLED);
    assertThat(transfers.initiated).isEmpty();
    assertThat(completion.isDone()).isTrue();
  }

  @Test
  void shouldLeaveAlonePartitionsOfAPhysicalTenantDisabledBeforeTheRebalanceReachesThem() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    leaders.computeIfAbsent("tenant-b", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2))),
                "tenant-b",
                    Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2)))));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    run(rebalance);

    // when
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig("tenant-b", PartitionGroupConfiguration::disable));
    transfers.accept();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(rebalance.partition(1).outcome())
        .isEqualTo(PartitionRebalanceOutcome.PHYSICAL_TENANT_DISABLED);
    assertThat(transfers.initiated)
        .map(initiated -> initiated.physicalTenantId())
        .containsExactly("tenant-a");
  }

  @Test
  void shouldStopWatchingATransferOnceThePhysicalTenantIsDisabled() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    final var completion = run(rebalance);
    transfers.accept();

    // when
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig(GROUP, PartitionGroupConfiguration::disable));
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).outcome())
        .isEqualTo(PartitionRebalanceOutcome.PHYSICAL_TENANT_DISABLED);
    assertThat(completion.isDone()).isTrue();
  }

  @Test
  void shouldStopWaitingForALeaderOnceThePhysicalTenantIsRecovering() {
    // given
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    final var completion = run(rebalance);

    // when
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig(GROUP, SequentialRebalanceRunnerTest::recovering));
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome())
        .isEqualTo(PartitionRebalanceOutcome.PHYSICAL_TENANT_RECOVERING);
    assertThat(transfers.initiated).isEmpty();
    assertThat(completion.isDone()).isTrue();
  }

  @Test
  void shouldLeaveAlonePartitionsOfAPhysicalTenantEnteringRecoveryBeforeTheRebalanceReachesThem() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    leaders.computeIfAbsent("tenant-b", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2))),
                "tenant-b",
                    Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2)))));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    run(rebalance);

    // when
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig(
            "tenant-b", SequentialRebalanceRunnerTest::recovering));
    transfers.accept();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(rebalance.partition(1).outcome())
        .isEqualTo(PartitionRebalanceOutcome.PHYSICAL_TENANT_RECOVERING);
    assertThat(transfers.initiated)
        .map(initiated -> initiated.physicalTenantId())
        .containsExactly("tenant-a");
  }

  @Test
  void shouldPreserveAnInFlightTransferWhenThePhysicalTenantEntersRecovery() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    run(rebalance);
    transfers.accept();

    // when
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig(GROUP, SequentialRebalanceRunnerTest::recovering));
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
  }

  @Test
  void shouldRebalanceAPhysicalTenantThatReturnsToProcessingWhileTheRebalanceRuns() {
    // given
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    run(rebalance);
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig(GROUP, SequentialRebalanceRunnerTest::recovering));

    // when
    rebalance.observeConfiguration(configuration);
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.lastInitiated().leader()).isEqualTo(MEMBER_1);
  }

  @Test
  void shouldRebalanceAPhysicalTenantThatIsEnabledAgainWhileTheRebalanceRuns() {
    // given
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    run(rebalance);
    rebalance.observeConfiguration(
        configuration.updatePartitionGroupConfig(GROUP, PartitionGroupConfiguration::disable));

    // when
    rebalance.observeConfiguration(configuration);
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.lastInitiated().leader()).isEqualTo(MEMBER_1);
  }

  @Test
  void shouldResolveImmediatelyWithNoLeaderWhenLeaderWaitTimeoutIsZero() {
    // given
    final var configuration = groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2));
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);

    // when
    run(rebalance, Duration.ZERO);

    // then
    assertThat(transfers.initiated).isEmpty();
    assertThat(executor.scheduledTasks()).isZero();

    // when
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    executor.runAll();

    // then
    assertThat(transfers.initiated).isEmpty();
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.NO_LEADER);
  }

  @Test
  void shouldMoveOnWhenTheTopologyShowsLeadershipReachedTheDesiredLeader() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var rebalance = start(twoPartitionsConfiguration());
    transfers.accept();

    // when
    leaders.get(GROUP).put(1, MEMBER_2);
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
    assertThat(transfers.lastInitiated().partitionId()).isEqualTo(2);
  }

  @Test
  void shouldGiveUpWithNoResponseWhenTheWatchdogExpires() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var rebalance = start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));
    transfers.accept();

    // when
    for (long observation = 0; observation < OBSERVATIONS_UNTIL_WATCHDOG_TIMEOUT; observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.NO_RESPONSE);
  }

  @Test
  void shouldContinueToTheNextPartitionOnlyAfterTheWatchdogExpiresWithNoResponse() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var rebalance = start(twoPartitionsConfiguration());
    transfers.accept();

    // when
    for (long observation = 0;
        observation < OBSERVATIONS_UNTIL_WATCHDOG_TIMEOUT - 1;
        observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.initiated).hasSize(1);

    // when
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.NO_RESPONSE);
    assertThat(rebalance.partition(1).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.initiated).hasSize(2);
  }

  @Test
  void shouldContinueToTheNextPartitionOnlyAfterTheWatchdogExpiresWhenInitiationFailed() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var rebalance = start(twoPartitionsConfiguration());

    // when
    transfers.fail(new RuntimeException("no route to the leader"));

    // then
    assertThat(rebalance.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.initiated).hasSize(1);

    // when
    for (long observation = 0; observation < OBSERVATIONS_UNTIL_WATCHDOG_TIMEOUT; observation++) {
      executor.runAll();
    }

    // then
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.NO_RESPONSE);
    assertThat(rebalance.partition(1).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.initiated).hasSize(2);
  }

  @Test
  void shouldCompleteWithLeaderChangedWhenATopologyShowsAThirdMemberLeading() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var rebalance = start(twoPartitionsConfiguration());
    transfers.accept();

    // when
    groupLeaders.put(1, MEMBER_3);
    executor.runAll();

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome())
        .isEqualTo(PartitionRebalanceOutcome.LEADER_CHANGED);
    assertThat(rebalance.partition(0).currentLeader()).isEqualTo(MEMBER_3);
    assertThat(rebalance.partition(1).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(transfers.lastInitiated().partitionId()).isEqualTo(2);
  }

  @Test
  void shouldContinueToTheNextPartitionAfterAnUnsuccessfulOutcome() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    start(twoPartitionsConfiguration());

    // when
    transfers.decline(LeadershipTransferResult.LAG_TOO_HIGH);

    // then
    assertThat(transfers.initiated).hasSize(2);
    assertThat(transfers.lastInitiated().partitionId()).isEqualTo(2);
  }

  @Test
  void shouldCompleteTheInFlightTransferThenCancelEveryUntouchedPartition() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var rebalance = start(twoPartitionsConfiguration());
    transfers.accept();

    // when
    rebalance.requestCancel();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(transfers.initiated).hasSize(1);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
    assertThat(rebalance.partition(1).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(1).outcome()).isEqualTo(PartitionRebalanceOutcome.CANCELLED);
  }

  @Test
  void shouldTakeOnNoFurtherPartitionOnceTheRebalanceIsAbandoned() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var rebalance = start(twoPartitionsConfiguration());
    transfers.accept();

    // when
    rebalance.abandon();
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(transfers.initiated).hasSize(1);
  }

  @Test
  void shouldAccountForAPartitionByWhatItsLeaderReported() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));
    transfers.accept();

    // when
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(partitionDurationCount(1, LeadershipTransferResult.TRANSFERRED.name())).isEqualTo(1);
  }

  @Test
  void shouldAccountForAPartitionAlreadyBalancedAtPlanTime() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_2);

    // when
    start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(partitionDurationCount(1, PartitionRebalanceOutcome.ALREADY_LEADER.name()))
        .isEqualTo(1);
  }

  @Test
  void shouldReportNoPendingPartitionAsCompletedWhenTheRunFinishes() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_2);

    // when
    start(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(partitionStateGauge(1)).isEqualTo(3);
  }

  @Test
  void shouldRegisterOneResultHandlerForTwoTransfersOfTheSamePartition() {
    // given
    final var runner = newRunner();
    final var partitionId = new PartitionId(GROUP, 1);
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var firstRun =
        new RebalanceRun(
            7,
            RebalanceOverrides.none(),
            false,
            groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)),
            Instant.EPOCH);
    runner.run(firstRun);

    // when
    final var secondRun =
        new RebalanceRun(
            9,
            RebalanceOverrides.none(),
            false,
            groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_3, 2)),
            Instant.EPOCH);
    runner.run(secondRun);

    // then
    assertThat(transfers.initiated).hasSize(2);
    assertThat(transfers.resultHandlerRegistrations(partitionId)).isEqualTo(1);
  }

  @Test
  void shouldRouteAResultToWhicheverTransferIsCurrentlyActiveForThePartition() {
    // given
    final var runner = newRunner();
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var firstRun =
        new RebalanceRun(
            7,
            RebalanceOverrides.none(),
            false,
            groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)),
            Instant.EPOCH);
    runner.run(firstRun);
    final var secondRun =
        new RebalanceRun(
            9,
            RebalanceOverrides.none(),
            false,
            groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_3, 2)),
            Instant.EPOCH);
    runner.run(secondRun);

    // when
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(secondRun.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(secondRun.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
    assertThat(firstRun.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
  }

  @Test
  void shouldNotCompleteTheCurrentTransferWithAStaleCorrelationId() {
    // given
    final var runner = newRunner();
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var firstRun =
        new RebalanceRun(
            7,
            RebalanceOverrides.none(),
            false,
            groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)),
            Instant.EPOCH);
    runner.run(firstRun);
    final var secondRun =
        new RebalanceRun(
            9,
            RebalanceOverrides.none(),
            false,
            groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_3, 2)),
            Instant.EPOCH);
    runner.run(secondRun);

    // when
    transfers.reportWithCorrelationId(firstRun.id(), LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(secondRun.partition(0).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
    assertThat(secondRun.partition(0).outcome()).isNull();
  }

  @Test
  void shouldUseASeparateResultHandlerForEachPartition() {
    // given
    final var groupLeaders = leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());
    groupLeaders.put(1, MEMBER_1);
    groupLeaders.put(2, MEMBER_1);
    final var runner = newRunner();
    final var rebalance =
        new RebalanceRun(
            7, RebalanceOverrides.none(), false, twoPartitionsConfiguration(), Instant.EPOCH);

    // when
    runner.run(rebalance);
    transfers.report(LeadershipTransferResult.TRANSFERRED);

    // then
    assertThat(transfers.resultHandlerRegistrations(new PartitionId(GROUP, 1))).isEqualTo(1);
    assertThat(transfers.resultHandlerRegistrations(new PartitionId(GROUP, 2))).isEqualTo(1);
    assertThat(rebalance.partition(0).outcome()).isEqualTo(PartitionRebalanceOutcome.TRANSFERRED);
    assertThat(rebalance.partition(1).progress())
        .isEqualTo(PartitionRebalanceProgress.TRANSFERRING);
  }

  private SequentialRebalanceRunner newRunner() {
    return new SequentialRebalanceRunner(
        COORDINATOR,
        executor,
        partitionLeaders,
        transfers,
        new ClusterRebalanceMetrics(registry),
        LEADER_WAIT_TIMEOUT,
        TEST_CONFIGURATION,
        TEST_HEARTBEAT_INTERVAL);
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

  private RebalanceRun planDryRun(final CurrentClusterConfiguration configuration) {
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), true, configuration, Instant.EPOCH);
    run(rebalance).join();
    return rebalance;
  }

  private RebalanceRun start(final CurrentClusterConfiguration configuration) {
    return start(
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH));
  }

  private RebalanceRun start(final RebalanceRun rebalance) {
    run(rebalance);
    return rebalance;
  }

  private ActorFuture<Void> run(final RebalanceRun rebalance) {
    return run(rebalance, LEADER_WAIT_TIMEOUT);
  }

  private ActorFuture<Void> run(final RebalanceRun rebalance, final Duration leaderWaitTimeout) {
    return new SequentialRebalanceRunner(
            COORDINATOR,
            executor,
            partitionLeaders,
            transfers,
            new ClusterRebalanceMetrics(registry),
            leaderWaitTimeout,
            TEST_CONFIGURATION,
            TEST_HEARTBEAT_INTERVAL)
        .run(rebalance);
  }

  private PartitionState active(final int priority) {
    return PartitionState.active(priority, partitionConfig);
  }

  private static PartitionGroupConfiguration recovering(final PartitionGroupConfiguration group) {
    var updated = group;
    for (final var memberId : group.members().keySet()) {
      updated = updated.updateMember(memberId, member -> member.setMode(Mode.RECOVERING));
    }
    return updated;
  }

  private CurrentClusterConfiguration groupConfiguration(
      final String group, final Map<MemberId, Integer> prioritiesForPartitionOne) {
    final Map<MemberId, Map<Integer, PartitionState>> partitionsPerMember = new HashMap<>();
    prioritiesForPartitionOne.forEach(
        (memberId, priority) -> partitionsPerMember.put(memberId, Map.of(1, active(priority))));
    return configurationOf(Map.of(group, partitionsPerMember));
  }

  private CurrentClusterConfiguration twoPartitionsConfiguration() {
    return configurationOf(
        Map.of(
            GROUP,
            Map.of(
                MEMBER_1, Map.of(1, active(1), 2, active(1)),
                MEMBER_2, Map.of(1, active(2), 2, active(2)))));
  }

  private CurrentClusterConfiguration configurationOf(
      final Map<String, Map<MemberId, Map<Integer, PartitionState>>> partitionsPerGroup) {
    final Map<MemberId, BrokerState> globalMembers = new HashMap<>();
    final Map<String, PartitionGroupConfiguration> groups = new HashMap<>();
    partitionsPerGroup.forEach(
        (groupId, membersInGroup) -> {
          final Map<MemberId, BrokerPartitionState> groupMembers = new HashMap<>();
          membersInGroup.forEach(
              (memberId, partitions) -> {
                globalMembers.putIfAbsent(memberId, BrokerState.initializeAsActive());
                groupMembers.put(memberId, BrokerPartitionState.initialize(partitions));
              });
          groups.put(
              groupId,
              new PartitionGroupConfiguration(
                  PartitionGroupConfiguration.INITIAL_VERSION,
                  PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
                  groupMembers,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty()));
        });
    final var globalConfiguration =
        new GlobalConfiguration(
            GlobalConfiguration.INITIAL_VERSION,
            Optional.empty(),
            globalMembers,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration,
        groups,
        PhasedChangeState.empty());
  }

  private static final class RecordingTransfers implements LeadershipTransferProtocol {

    private final List<Initiated> initiated = new ArrayList<>();
    private final Map<
            PartitionId,
            Function<
                LeadershipTransferResultRequest,
                CompletableFuture<LeadershipTransferResultResponse>>>
        resultHandlers = new HashMap<>();
    private final Map<PartitionId, Integer> resultHandlerRegistrations = new HashMap<>();

    private CompletableFuture<LeadershipTransferInitiateResponse> pending;

    @Override
    public CompletableFuture<LeadershipTransferInitiateResponse> initiate(
        final MemberId leader,
        final PartitionId partitionId,
        final LeadershipTransferInitiateRequest request) {
      initiated.add(new Initiated(leader, partitionId.group(), partitionId.number(), request));
      pending = new CompletableFuture<>();
      return pending;
    }

    @Override
    public void onResult(
        final PartitionId partitionId,
        final Function<
                LeadershipTransferResultRequest,
                CompletableFuture<LeadershipTransferResultResponse>>
            handler) {
      resultHandlers.put(partitionId, handler);
      resultHandlerRegistrations.merge(partitionId, 1, Integer::sum);
    }

    Initiated lastInitiated() {
      return initiated.get(initiated.size() - 1);
    }

    int resultHandlerRegistrations(final PartitionId partitionId) {
      return resultHandlerRegistrations.getOrDefault(partitionId, 0);
    }

    void accept() {
      pending.complete(LeadershipTransferInitiateResponse.builder().withStatus(Status.OK).build());
    }

    void decline(final LeadershipTransferResult reason) {
      pending.complete(
          LeadershipTransferInitiateResponse.builder()
              .withStatus(Status.OK)
              .withRejectionReason(reason)
              .build());
    }

    void fail(final Throwable error) {
      pending.completeExceptionally(error);
    }

    void error(final RaftError.Type type) {
      pending.complete(
          LeadershipTransferInitiateResponse.builder()
              .withStatus(Status.ERROR)
              .withError(new RaftError(type, null))
              .build());
    }

    void report(final LeadershipTransferResult result) {
      reportWithCorrelationId(lastInitiated().request().correlationId(), result);
    }

    void reportWithCorrelationId(final long correlationId, final LeadershipTransferResult result) {
      final var last = lastInitiated();
      resultHandlers
          .get(new PartitionId(last.physicalTenantId(), last.partitionId()))
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
