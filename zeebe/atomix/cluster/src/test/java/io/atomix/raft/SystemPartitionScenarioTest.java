/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

import static io.atomix.raft.cluster.RaftMember.Type.ACTIVE;
import static io.atomix.raft.cluster.RaftMember.Type.PASSIVE;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.SystemPartitionCluster.MemberState;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.VoterPolicy;
import io.atomix.raft.cluster.VoterPolicy.ZoneVoters;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.ForcedChange;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.NormalChange;
import io.atomix.raft.storage.system.Configuration;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The deployment examples of the system partition design (5.10), end to end on in-process Raft
 * servers: the Raft configuration (members, types, incarnations, fence list, policy) and the quorum
 * at each step, and that committed entries stay readable on the survivors.
 */
final class SystemPartitionScenarioTest {

  private static final MemberId A1 = MemberId.from("a", 1);
  private static final MemberId A2 = MemberId.from("a", 2);
  private static final MemberId A3 = MemberId.from("a", 3);
  private static final MemberId A4 = MemberId.from("a", 4);
  private static final MemberId B1 = MemberId.from("b", 1);
  private static final MemberId B2 = MemberId.from("b", 2);
  private static final MemberId B3 = MemberId.from("b", 3);
  private static final MemberId B4 = MemberId.from("b", 4);
  private static final MemberId C1 = MemberId.from("c", 1);
  private static final Duration NO_QUORUM_PERIOD = Duration.ofSeconds(3);

  @TempDir private Path tmp;
  private SystemPartitionCluster cluster;

  @AfterEach
  void tearDown() {
    if (cluster != null) {
      cluster.close();
    }
  }

  private static Map<MemberId, Type> members(final Object... idsAndTypes) {
    final var members = new LinkedHashMap<MemberId, Type>();
    for (int i = 0; i < idsAndTypes.length; i += 2) {
      members.put((MemberId) idsAndTypes[i], (Type) idsAndTypes[i + 1]);
    }
    return members;
  }

  private static Set<FenceEntry> fenced(final MemberId... members) {
    return Arrays.stream(members)
        .map(member -> new FenceEntry(member, 0))
        .collect(Collectors.toSet());
  }

  private void assertConfiguration(
      final Configuration configuration,
      final Map<MemberId, Type> types,
      final Set<FenceEntry> fence,
      final VoterPolicy policy) {
    assertThat(configuration.requiresJointConsensus()).isFalse();
    assertThat(SystemPartitionCluster.typesOf(configuration)).isEqualTo(types);
    assertThat(Set.copyOf(configuration.fenced())).isEqualTo(fence);
    assertThat(configuration.voterPolicy()).isEqualTo(policy);
  }

  /** Waits until all given brokers have the leader's configuration, and checks it. */
  private Configuration awaitConfiguration(
      final List<MemberId> brokers,
      final Map<MemberId, Type> types,
      final Set<FenceEntry> fence,
      final VoterPolicy policy) {
    Awaitility.await("brokers %s have the configuration %s".formatted(brokers, types))
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              for (final var broker : brokers) {
                assertConfiguration(cluster.configuration(broker), types, fence, policy);
              }
            });
    return cluster.configuration(brokers.getFirst());
  }

  private long incarnationInConfiguration(final MemberId member) {
    return cluster.awaitLeaderView().newMembers().stream()
        .filter(m -> m.memberId().equals(member))
        .mapToLong(RaftMember::getIncarnation)
        .findAny()
        .orElseThrow();
  }

  /** 5.10.2: zones a and b with 3 brokers each, control zone c with 1 broker. */
  @Nested
  final class TwoAndAHalfRegions {

    private final VoterPolicy policy =
        SystemPartitionMembership.policy(
            new ZoneVoters("c", 1), List.of(new ZoneVoters("a", 2), new ZoneVoters("b", 2)));
    private final List<MemberId> all = List.of(A1, A2, A3, B1, B2, B3, C1);
    private final Map<MemberId, Type> bootstrapped =
        members(
            C1, ACTIVE, A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE, A3, PASSIVE, B3, PASSIVE);

    private void bootstrap() {
      cluster = new SystemPartitionCluster(tmp, policy);
      cluster.bootstrap(all);
      awaitConfiguration(all, bootstrapped, Set.of(), policy);
    }

    private void awaitControlLeader() {
      Awaitility.await("the control broker leads")
          .atMost(Duration.ofSeconds(30))
          .until(() -> C1.equals(cluster.awaitLeader()));
    }

    @Test
    void shouldBootstrapWithControlLeaderAndKeepConfigurationOverRestart() {
      // when
      bootstrap();

      // then - the first leader appended the initial configuration as an entry
      awaitControlLeader();
      final var first = cluster.awaitLeaderView();
      assertThat(first.index()).isPositive();
      assertThat(first.newMembers()).allMatch(member -> member.getIncarnation() == 0);
      assertThat(all).extracting(cluster::nodePriority).containsExactly(4, 3, 0, 2, 1, 0, 5);
      assertThat(all).extracting(cluster::targetPriority).containsOnly(5);
      cluster.appendEntry();
      cluster.appendEntry();

      // when - all brokers restart at the same time
      all.forEach(cluster::stop);
      cluster.restartAll(all);

      // then - the stored configuration and the priorities come back without a leader
      assertThat(all).extracting(cluster::nodePriority).containsExactly(4, 3, 0, 2, 1, 0, 5);
      awaitControlLeader();
      final var restarted = awaitConfiguration(all, bootstrapped, Set.of(), policy);
      assertThat(restarted.index()).isGreaterThanOrEqualTo(first.index());
      assertThat(cluster.server(A3).getRole()).isEqualTo(Role.PASSIVE);
      assertThat(cluster.server(B3).getRole()).isEqualTo(Role.PASSIVE);
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(all);
    }

    @Test
    void shouldAddBrokerAsPassiveAndSwapLeavingVoterInOneJointChange() {
      // given
      bootstrap();
      cluster.awaitLeader();
      cluster.appendEntry();
      final var b4 = MemberId.from("b", 4);

      // when - scale-up of zone b
      cluster.memberJoin(b4);
      cluster.joined(b4).orTimeout(30, TimeUnit.SECONDS).join();
      cluster.dynamic.setState(b4, MemberState.ACTIVE);

      // then - zone b is full, thus b_4 stays passive
      final var withB4 = new LinkedHashMap<>(bootstrapped);
      withB4.put(b4, PASSIVE);
      awaitConfiguration(List.of(C1, b4), withB4, Set.of(), policy);
      assertThat(incarnationInConfiguration(b4)).isEqualTo(cluster.incarnation(b4));
      assertThat(cluster.reconcileOnce()).isFalse();

      // when - the voter B1 leaves
      final var recorded = cluster.recordConfigurations(C1);
      final var leave = cluster.memberLeave(B1);

      // then - one joint change removes B1 and promotes B3, the lowest passive member of zone b
      assertThat(leave.replacement()).isEqualTo(B3);
      awaitConfiguration(
          List.of(C1, A1, B3, b4),
          members(
              C1, ACTIVE, A1, ACTIVE, A2, ACTIVE, B2, ACTIVE, B3, ACTIVE, A3, PASSIVE, b4, PASSIVE),
          fenced(B1),
          policy);
      assertThat(recorded)
          .as("zone b never has more than 2 voters in a new configuration")
          .allSatisfy(
              configuration ->
                  assertThat(
                          configuration.newMembers().stream()
                              .filter(m -> "b".equals(m.memberId().zone()))
                              .filter(m -> m.getType() == ACTIVE))
                      .hasSizeLessThanOrEqualTo(2))
          .anySatisfy(
              joint -> {
                assertThat(joint.requiresJointConsensus()).isTrue();
                assertThat(joint.oldMembers())
                    .anyMatch(m -> m.memberId().equals(B1) && m.getType() == ACTIVE);
                assertThat(joint.newMembers())
                    .anyMatch(m -> m.memberId().equals(B3) && m.getType() == ACTIVE);
              });
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(C1, A1, A2, A3, B2, B3, b4));
    }

    @Test
    void shouldFailOverZoneWithNormalChangeAndFailBackBySelfPromotion() {
      // given
      bootstrap();
      cluster.awaitLeader();
      cluster.appendEntry();

      // when - zone a fails; 3 of 5 voters are alive
      List.of(A1, A2, A3).forEach(cluster::stop);
      cluster.awaitLeader();
      cluster.appendEntry();
      final var decision = cluster.forceRemove(Set.of(A1, A2, A3));

      // then - phase 0 removes zone a with a normal change, the slots of zone a stay empty
      assertThat(decision).isInstanceOf(NormalChange.class);
      final var failedOver = members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE);
      awaitConfiguration(List.of(C1, B1, B2, B3), failedOver, fenced(A1, A2, A3), policy);
      assertThat(cluster.reconcileOnce()).isFalse();
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(C1, B1, B2, B3));

      // and the system partition still tolerates one failure
      cluster.stop(B1);
      cluster.awaitLeader();
      cluster.appendEntry();
      cluster.restart(B1);

      // when - zone a comes back with its old data, its brokers still think they are members
      final var leader = cluster.awaitLeader();
      final long term = cluster.server(leader).getTerm();
      List.of(A1, A2, A3).forEach(member -> cluster.dynamic.setStaleView(member, true));
      cluster.restartAll(List.of(A1, A2, A3));

      // then - the old voters are fenced, and their joins are refused, because they are LEFT
      Awaitility.await("the old voters of zone a are fenced and refused")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                assertThat(cluster.fencedNotices(A1)).contains(0L);
                assertThat(cluster.fencedNotices(A2)).contains(0L);
                assertThat(cluster.dynamic.refusals(A1)).isPositive();
                assertThat(cluster.dynamic.refusals(A2)).isPositive();
              });
      assertThat(cluster.awaitLeader()).isEqualTo(leader);
      assertThat(cluster.server(leader).getTerm()).isEqualTo(term);
      // the old passive member sends no requests, nobody tells it anything
      assertThat(cluster.fencedNotices(A3)).isEmpty();
      assertThat(SystemPartitionCluster.typesOf(cluster.configuration(A3))).isEqualTo(bootstrapped);
      awaitConfiguration(List.of(C1), failedOver, fenced(A1, A2, A3), policy);

      // when - failback adds zone a again
      List.of(A1, A2, A3).forEach(member -> cluster.dynamic.setStaleView(member, false));
      List.of(A1, A2, A3).forEach(cluster::memberJoin);
      List.of(A1, A2, A3)
          .forEach(member -> cluster.joined(member).orTimeout(60, TimeUnit.SECONDS).join());
      List.of(A1, A2, A3).forEach(member -> cluster.dynamic.setState(member, MemberState.ACTIVE));

      // then - self-promotion fills the slots of zone a with the lowest node indexes
      final var failedBack =
          members(
              C1, ACTIVE, A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE, A3, PASSIVE, B3, PASSIVE);
      cluster.awaitReconciled(failedBack);
      final var configuration =
          awaitConfiguration(List.of(C1, A1, A2, A3), failedBack, fenced(A1, A2, A3), policy);
      assertThat(configuration.newMembers())
          .filteredOn(member -> "a".equals(member.memberId().zone()))
          .allSatisfy(
              member ->
                  assertThat(member.getIncarnation())
                      .isEqualTo(cluster.incarnation(member.memberId()))
                      .isNotZero());
      Awaitility.await("zone a voters get their priorities back")
          .untilAsserted(
              () ->
                  assertThat(List.of(A1, A2, A3))
                      .extracting(cluster::nodePriority)
                      .containsExactly(4, 3, 0));
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(all);
    }

    @Test
    void shouldNotChangeConfigurationWhenControlZoneFails() {
      // given
      bootstrap();
      awaitControlLeader();
      cluster.appendEntry();
      final var before = cluster.awaitLeaderView();

      // when - 4 of 5 voters are alive
      cluster.stop(C1);

      // then - the voter with the next priority leads, the configuration does not change
      Awaitility.await("A1 leads")
          .atMost(Duration.ofSeconds(30))
          .until(() -> A1.equals(cluster.awaitLeader()));
      cluster.appendEntry();
      assertThat(cluster.reconcileOnce()).isFalse();
      final var during = cluster.awaitLeaderView();
      assertThat(during.index()).isEqualTo(before.index());
      assertConfiguration(during, bootstrapped, Set.of(), policy);

      // when - C1 comes back with its disk, and the leader steps down for leader balancing
      cluster.restart(C1);
      cluster.assertCommittedEntriesReadable(List.of(C1));
      cluster.server(A1).stepDown().join();

      // then - C1 has the highest priority and leads again
      awaitControlLeader();
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(all);
    }

    @Test
    void shouldForceVotersWhenControlZoneAndZoneFail() {
      // given
      bootstrap();
      cluster.awaitLeader();
      cluster.appendEntry();

      // when - the control zone and zone a fail; 2 of 5 voters are alive
      List.of(C1, A1, A2, A3).forEach(cluster::stop);

      // then - no quorum
      cluster.awaitNoLeaderFor(NO_QUORUM_PERIOD);

      // when - the operator forces the system partition voters
      final var forced = cluster.forceVoters(Set.of(A1, A2, A3, C1));

      // then - B1 and B2 are the voters, all others stay members as passive members
      assertThat(forced).isInstanceOf(ForcedChange.class);
      final var fence = fenced(A1, A2, A3, C1);
      awaitConfiguration(
          List.of(B1, B2, B3),
          members(
              B1, ACTIVE, B2, ACTIVE, A1, PASSIVE, A2, PASSIVE, A3, PASSIVE, B3, PASSIVE, C1,
              PASSIVE),
          fence,
          policy);
      cluster.awaitLeader();
      cluster.appendEntry();

      // when - the automatic failover removes zone a with a normal change
      final var removal = cluster.forceRemove(Set.of(A1, A2, A3));

      // then
      assertThat(removal).isInstanceOf(NormalChange.class);
      final var failedOver = members(B1, ACTIVE, B2, ACTIVE, B3, PASSIVE, C1, PASSIVE);
      awaitConfiguration(List.of(B1, B2, B3), failedOver, fence, policy);
      cluster.appendEntry();

      // when - all survivors restart
      List.of(B1, B2, B3).forEach(cluster::stop);
      cluster.restartAll(List.of(B1, B2, B3));

      // then
      cluster.awaitLeader();
      awaitConfiguration(List.of(B1, B2, B3), failedOver, fence, policy);
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(B1, B2, B3));

      // when - C1 comes back with its old data
      cluster.restart(C1);

      // then - it is fenced, deletes its data, joins with a new incarnation and promotes itself
      Awaitility.await("C1 is fenced")
          .atMost(Duration.ofSeconds(30))
          .until(() -> cluster.fencedNotices(C1).contains(0L));
      cluster.joined(C1).orTimeout(60, TimeUnit.SECONDS).join();
      final var withControl = members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE);
      cluster.awaitReconciled(withControl);
      final var configuration = awaitConfiguration(List.of(B1, C1), withControl, fence, policy);
      assertThat(cluster.incarnation(C1)).isNotZero();
      assertThat(incarnationInConfiguration(C1)).isEqualTo(cluster.incarnation(C1));
      assertThat(configuration.fenced()).contains(new FenceEntry(C1, 0));
      Awaitility.await("C1 gets the highest priority again")
          .untilAsserted(() -> assertThat(cluster.nodePriority(C1)).isEqualTo(5));
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(B1, B2, B3, C1));
    }

    @Test
    void shouldKeepVoterWithLostDiskFromVotingUntilCaughtUp() {
      // given - zone a's A1 is down
      bootstrap();
      awaitControlLeader();
      cluster.appendEntry();
      cluster.stop(A1);

      // when - B1 loses its disk and joins again, while a voter reported a higher term
      cluster.loseDisk(B1);
      cluster.joinWithoutData(B1, 1);
      cluster.joined(B1).orTimeout(60, TimeUnit.SECONDS).join();

      // then - B1 is still a voter, with its new incarnation, but guarded
      final var configuration = cluster.awaitLeaderView();
      assertThat(SystemPartitionCluster.typesOf(configuration)).containsEntry(B1, ACTIVE);
      assertThat(incarnationInConfiguration(B1)).isEqualTo(cluster.incarnation(B1));
      assertThat(cluster.server(B1).isEmptyStartGuardActive().join()).isTrue();

      // when - the leader fails; A2, B2 and the guarded B1 are 3 of 5 voters
      cluster.stop(C1);

      // then - no leader can be elected without the vote of B1
      cluster.awaitNoLeaderFor(NO_QUORUM_PERIOD);
      assertThat(cluster.server(B1).isEmptyStartGuardActive().join()).isTrue();

      // when - C1 comes back
      cluster.restart(C1);

      // then - a new leader with a higher term releases the guard once B1 has caught up
      cluster.awaitLeader();
      Awaitility.await("the guard of B1 is released")
          .atMost(Duration.ofSeconds(30))
          .until(() -> !cluster.server(B1).isEmptyStartGuardActive().join());
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(C1, A2, B1, B2));
    }

    @Test
    void shouldKeepPassiveMemberWithLostDiskPassive() {
      // given
      bootstrap();
      cluster.awaitLeader();
      cluster.appendEntry();

      // when
      cluster.loseDisk(A3);
      cluster.joinWithoutData(A3);
      cluster.joined(A3).orTimeout(60, TimeUnit.SECONDS).join();

      // then
      awaitConfiguration(List.of(C1, A3), bootstrapped, Set.of(), policy);
      assertThat(incarnationInConfiguration(A3)).isEqualTo(cluster.incarnation(A3));
      assertThat(cluster.reconcileOnce()).isFalse();
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(A3));
    }
  }

  /** 5.10.1: zones a and b with 4 brokers each, no control zone. */
  @Nested
  final class DualRegion {

    private final VoterPolicy policy =
        SystemPartitionMembership.policy(
            null, List.of(new ZoneVoters("a", 2), new ZoneVoters("b", 2)));
    private final List<MemberId> all = List.of(A1, A2, A3, A4, B1, B2, B3, B4);
    private final Map<MemberId, Type> bootstrapped =
        members(
            A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE, A3, PASSIVE, A4, PASSIVE, B3, PASSIVE,
            B4, PASSIVE);
    private final List<MemberId> zoneB = List.of(B1, B2, B3, B4);

    private void bootstrap() {
      cluster = new SystemPartitionCluster(tmp, policy);
      cluster.bootstrap(all);
      awaitConfiguration(all, bootstrapped, Set.of(), policy);
    }

    @Test
    void shouldForceZoneAWhenZoneBFailsAndFailBack() {
      // given
      bootstrap();
      cluster.awaitLeader();
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(all);

      // when - zone b fails: 2 of 4 voters
      zoneB.forEach(cluster::stop);

      // then
      cluster.awaitNoLeaderFor(NO_QUORUM_PERIOD);

      // when - remove zone b with force
      final var decision = cluster.forceRemove(Set.copyOf(zoneB));

      // then - a forced change to the voters with the most recent log
      assertThat(decision).isInstanceOf(ForcedChange.class);
      final var forced = members(A1, ACTIVE, A2, ACTIVE, A3, PASSIVE, A4, PASSIVE);
      final var fence = fenced(B1, B2, B3, B4);
      awaitConfiguration(List.of(A1, A2, A3, A4), forced, fence, policy);
      cluster.awaitLeader();
      cluster.appendEntry();

      // when - restart of the cluster after the forced change
      List.of(A1, A2, A3, A4).forEach(cluster::stop);
      cluster.restartAll(List.of(A1, A2, A3, A4));
      zoneB.forEach(cluster::restart);

      // then - A1 and A2 elect a leader; the old voters of zone b are fenced
      cluster.awaitLeader();
      awaitConfiguration(List.of(A1, A2, A3, A4), forced, fence, policy);
      Awaitility.await("the old voters of zone b are fenced")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                assertThat(cluster.fencedNotices(B1)).contains(0L);
                assertThat(cluster.fencedNotices(B2)).contains(0L);
              });
      assertThat(cluster.fencedNotices(B3)).isEmpty();
      assertThat(cluster.fencedNotices(B4)).isEmpty();
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(A1, A2, A3, A4));

      // when - failback
      zoneB.forEach(cluster::memberJoin);
      zoneB.forEach(member -> cluster.joined(member).orTimeout(60, TimeUnit.SECONDS).join());
      zoneB.forEach(member -> cluster.dynamic.setState(member, MemberState.ACTIVE));

      // then - self-promotion fills the slots of zone b
      cluster.awaitReconciled(bootstrapped);
      final var configuration = awaitConfiguration(all, bootstrapped, fence, policy);
      assertThat(configuration.newMembers())
          .filteredOn(member -> "b".equals(member.memberId().zone()))
          .allSatisfy(member -> assertThat(member.getIncarnation()).isNotZero());
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(all);
    }

    @Test
    void shouldForceOnlyMemberWithMostRecentLogAndRestorePolicyBySelfPromotion() {
      // given - A1 does not lead, A1 and A4 are cut off while entries commit on A2, B1 and B2
      bootstrap();
      Awaitility.await("A1 does not lead")
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                final var leader = cluster.awaitLeader();
                if (leader.equals(A1)) {
                  // A1 has the highest priority, thus it would win again after a step-down
                  cluster.server(B1).anoint().join();
                  return false;
                }
                return true;
              });
      cluster.partition(A1);
      cluster.partition(A4);
      cluster.appendEntry();
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(A2, A3));

      // when - A2 and zone b fail, then A1 and A4 can be reached again
      List.of(A2, B1, B2, B3, B4).forEach(cluster::stop);
      cluster.heal(A1);
      cluster.heal(A4);
      final var decision = cluster.forceRemove(Set.copyOf(zoneB));

      // then - only A3 has the most recent log, thus it is the only voter
      assertThat(decision).isInstanceOf(ForcedChange.class);
      final var forced = members(A3, ACTIVE, A1, PASSIVE, A2, PASSIVE, A4, PASSIVE);
      final var fence = fenced(B1, B2, B3, B4);
      awaitConfiguration(List.of(A1, A3, A4), forced, fence, policy);
      assertThat(cluster.awaitLeader()).isEqualTo(A3);
      cluster.appendEntry();

      // when - self-promotion
      final var restored = members(A3, ACTIVE, A1, ACTIVE, A2, PASSIVE, A4, PASSIVE);
      cluster.awaitReconciled(restored);

      // then - zone a has 2 voters again; the down voter A2 was not promoted
      awaitConfiguration(List.of(A1, A3, A4), restored, fence, policy);
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(A1, A3, A4));

      // when - A2 comes back with its old configuration, in which it is a voter
      cluster.restart(A2);

      // then - it learns that it is passive now and catches up
      awaitConfiguration(List.of(A2), restored, fence, policy);
      Awaitility.await("A2 is passive")
          .atMost(Duration.ofSeconds(30))
          .until(() -> cluster.server(A2).getRole() == Role.PASSIVE);
      assertThat(cluster.awaitLeader()).isIn(A1, A3);
      cluster.appendEntry();
      cluster.assertCommittedEntriesReadable(List.of(A1, A2, A3, A4));
    }
  }
}
