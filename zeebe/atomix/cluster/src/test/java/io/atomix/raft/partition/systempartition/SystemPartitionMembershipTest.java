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
package io.atomix.raft.partition.systempartition;

import static io.atomix.raft.cluster.RaftMember.Type.ACTIVE;
import static io.atomix.raft.cluster.RaftMember.Type.PASSIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.MembershipChange;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.ReplicationStatus;
import io.atomix.raft.cluster.ReplicationStatus.MemberStatus;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.BrokerState;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.ClusterView;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.LeaderDecision;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.LeaderInput;
import io.atomix.raft.partition.systempartition.VoterPolicy.ZoneVoters;
import io.atomix.raft.storage.system.Configuration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class SystemPartitionMembershipTest {

  static final MemberId A1 = MemberId.from("a", 1);
  static final MemberId A2 = MemberId.from("a", 2);
  static final MemberId A3 = MemberId.from("a", 3);
  static final MemberId B1 = MemberId.from("b", 1);
  static final MemberId B2 = MemberId.from("b", 2);
  static final MemberId B3 = MemberId.from("b", 3);
  static final MemberId C1 = MemberId.from("c", 1);
  static final MemberId X1 = MemberId.from("x", 1);

  /** 2.5 regions: control zone c first, then a and b. */
  static final VoterPolicy POLICY =
      SystemPartitionMembership.policy(
          new ZoneVoters("c", 1), List.of(new ZoneVoters("a", 2), new ZoneVoters("b", 2)));

  static Configuration configuration(final Map<MemberId, Type> members) {
    return configuration(members, Map.of(), List.of(), POLICY);
  }

  static Configuration configuration(
      final Map<MemberId, Type> members,
      final Map<MemberId, Long> incarnations,
      final Collection<FenceEntry> fenced,
      final VoterPolicy policy) {
    return new Configuration(
        10, 2, 1, raftMembers(members, incarnations), List.of(), false, fenced, 0);
  }

  static List<RaftMember> raftMembers(
      final Map<MemberId, Type> members, final Map<MemberId, Long> incarnations) {
    return members.entrySet().stream()
        .map(
            entry ->
                (RaftMember)
                    new DefaultRaftMember(
                        entry.getKey(),
                        entry.getValue(),
                        Instant.ofEpochMilli(1),
                        incarnations.getOrDefault(entry.getKey(), 0L)))
        .toList();
  }

  /** Builds an ordered map from alternating member ids and types. */
  static Map<MemberId, Type> members(final Object... idsAndTypes) {
    final var members = new LinkedHashMap<MemberId, Type>();
    for (int i = 0; i < idsAndTypes.length; i += 2) {
      members.put((MemberId) idsAndTypes[i], (Type) idsAndTypes[i + 1]);
    }
    return members;
  }

  static Map<MemberId, Type> bootstrapped() {
    return members(
        C1, ACTIVE, A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE, A3, PASSIVE, B3, PASSIVE);
  }

  @Nested
  final class Policy {

    @Test
    void shouldPutControlZoneFirst() {
      // when
      final var policy =
          SystemPartitionMembership.policy(
              new ZoneVoters("c", 1), List.of(new ZoneVoters("a", 2), new ZoneVoters("b", 2)));

      // then
      assertThat(policy.zones()).extracting(ZoneVoters::zone).containsExactly("c", "a", "b");
    }

    @Test
    void shouldCreatePolicyWithoutControlZone() {
      // when
      final var policy =
          SystemPartitionMembership.policy(
              null, List.of(new ZoneVoters("a", 2), new ZoneVoters("b", 2)));

      // then
      assertThat(policy.zones()).extracting(ZoneVoters::zone).containsExactly("a", "b");
    }

    @Test
    void shouldFillSlotsWithLowestNodeIndexesInInitialConfiguration() {
      // when
      final var initial =
          SystemPartitionMembership.initialConfiguration(
              POLICY, List.of(B3, A3, B2, C1, A2, B1, A1));

      // then
      assertThat(initial).isEqualTo(bootstrapped());
    }

    @Test
    void shouldMakeMembersOfZoneOutsidePolicyPassiveInInitialConfiguration() {
      // when
      final var initial =
          SystemPartitionMembership.initialConfiguration(POLICY, List.of(A1, X1, C1));

      // then
      assertThat(initial).isEqualTo(members(A1, ACTIVE, C1, ACTIVE, X1, PASSIVE));
    }
  }

  @Nested
  final class SelectVoters {

    @Test
    void shouldKeepVotersOfFullZones() {
      // given
      final var configuration = configuration(bootstrapped());

      // when
      final var selection = SystemPartitionMembership.selectVoters(POLICY, configuration, Set.of());

      // then
      assertThat(selection.target()).isEqualTo(bootstrapped());
      assertThat(selection.zones())
          .allSatisfy(
              zone -> {
                assertThat(zone.extraVoters()).isEmpty();
                assertThat(zone.emptySlots()).isZero();
              });
    }

    @Test
    void shouldLeaveSlotsOfEmptyZoneEmpty() {
      // given - zone a was removed
      final var configuration =
          configuration(members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));

      // when
      final var selection = SystemPartitionMembership.selectVoters(POLICY, configuration, Set.of());

      // then - no vote moves to zone b
      assertThat(selection.target())
          .isEqualTo(members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
      final var zoneA = selection.zone("a").orElseThrow();
      assertThat(zoneA.emptySlots()).isEqualTo(2);
      assertThat(zoneA.candidates()).isEmpty();
      assertThat(selection.zone("b").orElseThrow().candidates()).containsExactly(B3);
    }

    @Test
    void shouldOfferPassiveMembersOfZoneWithEmptySlot() {
      // given - A1 left, zone a has one empty slot
      final var configuration =
          configuration(
              members(C1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE, A3, PASSIVE, B3, PASSIVE));

      // when
      final var selection = SystemPartitionMembership.selectVoters(POLICY, configuration, Set.of());

      // then
      final var zoneA = selection.zone("a").orElseThrow();
      assertThat(zoneA.voters()).containsExactly(A2);
      assertThat(zoneA.emptySlots()).isOne();
      assertThat(zoneA.candidates()).containsExactly(A3);
      assertThat(selection.target()).containsEntry(A3, PASSIVE);
    }

    @Test
    void shouldMarkVotersWithHighestNodeIndexAsExtra() {
      // given - a forced change left three voters in zone a
      final var configuration =
          configuration(members(A1, ACTIVE, A2, ACTIVE, A3, ACTIVE, B1, PASSIVE));

      // when
      final var selection = SystemPartitionMembership.selectVoters(POLICY, configuration, Set.of());

      // then
      final var zoneA = selection.zone("a").orElseThrow();
      assertThat(zoneA.voters()).containsExactly(A1, A2);
      assertThat(zoneA.extraVoters()).containsExactly(A3);
      assertThat(selection.target()).containsEntry(A3, PASSIVE);
    }

    @Test
    void shouldNotOfferExcludedBrokers() {
      // given - A3 is LEAVING or the target of a pending removal
      final var configuration =
          configuration(members(C1, ACTIVE, A2, ACTIVE, A3, PASSIVE, B1, ACTIVE, B2, ACTIVE));

      // when
      final var selection =
          SystemPartitionMembership.selectVoters(POLICY, configuration, Set.of(A3));

      // then
      assertThat(selection.zone("a").orElseThrow().candidates()).isEmpty();
    }

    @Test
    void shouldNotOfferFencedMembers() {
      // given - A3 with incarnation 0 is fenced, A2 joined again with a new incarnation
      final var configuration =
          configuration(
              members(C1, ACTIVE, A2, PASSIVE, A3, PASSIVE, B1, ACTIVE, B2, ACTIVE),
              Map.of(A2, 7L),
              List.of(new FenceEntry(A2, 0), new FenceEntry(A3, 0)),
              POLICY);

      // when
      final var selection = SystemPartitionMembership.selectVoters(POLICY, configuration, Set.of());

      // then
      assertThat(selection.zone("a").orElseThrow().candidates()).containsExactly(A2);
    }

    @Test
    void shouldKeepMembersOfZoneOutsidePolicyPassive() {
      // given
      final var configuration =
          configuration(members(C1, ACTIVE, B1, ACTIVE, X1, ACTIVE, A1, PASSIVE));

      // when
      final var selection = SystemPartitionMembership.selectVoters(POLICY, configuration, Set.of());

      // then
      assertThat(selection.nonPolicyVoters()).containsExactly(X1);
      assertThat(selection.target()).containsEntry(X1, PASSIVE);
      assertThat(selection.zones()).extracting(zone -> zone.zone()).doesNotContain("x");
    }

    @Test
    void shouldRejectJointConfiguration() {
      // given
      final var joint =
          new Configuration(
              10,
              2,
              1,
              raftMembers(members(A1, ACTIVE), Map.of()),
              raftMembers(members(A1, ACTIVE, A2, ACTIVE), Map.of()),
              false,
              List.of(),
              0);

      // when - then
      assertThatThrownBy(() -> SystemPartitionMembership.selectVoters(POLICY, joint, Set.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  final class Priorities {

    @Test
    void shouldGiveControlZoneVoterTheTargetPriority() {
      // given
      final var configuration = configuration(bootstrapped());

      // when
      final var priorities = SystemPartitionMembership.priorities(configuration, POLICY);

      // then
      assertThat(priorities.targetPriority()).isEqualTo(5);
      assertThat(priorities.voterPriorities())
          .containsExactly(
              Map.entry(C1, 5),
              Map.entry(A1, 4),
              Map.entry(A2, 3),
              Map.entry(B1, 2),
              Map.entry(B2, 1));
      assertThat(priorities.priorityOf(A3)).isZero();
      assertThat(priorities.priorityOf(B3)).isZero();
    }

    @Test
    void shouldKeepTargetFromPolicyWhenZoneIsEmpty() {
      // given - zone a was removed
      final var configuration =
          configuration(members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));

      // when
      final var priorities = SystemPartitionMembership.priorities(configuration, POLICY);

      // then
      assertThat(priorities.targetPriority()).isEqualTo(5);
      assertThat(priorities.voterPriorities())
          .containsExactly(Map.entry(C1, 5), Map.entry(B1, 4), Map.entry(B2, 3));
    }

    @Test
    void shouldGiveExtraVotersAtLeastPriorityOne() {
      // given - a policy of 1 voter with 3 voters after a forced change
      final var configuration =
          configuration(members(A1, ACTIVE, A2, ACTIVE, X1, ACTIVE), Map.of(), List.of(), POLICY);

      // when
      final var priorities =
          SystemPartitionMembership.priorities(
              configuration, VoterPolicy.of(new ZoneVoters("a", 1)));

      // then - the zone outside the policy comes last
      assertThat(priorities.targetPriority()).isOne();
      assertThat(priorities.voterPriorities())
          .containsExactly(Map.entry(A1, 1), Map.entry(A2, 1), Map.entry(X1, 1));
    }

    @Test
    void shouldUseZoneOrderOfPolicy() {
      // given - zone b has a higher priority than zone a
      final var configuration =
          configuration(
              members(A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE), Map.of(), List.of(), POLICY);

      // when
      final var priorities =
          SystemPartitionMembership.priorities(
              configuration, VoterPolicy.of(new ZoneVoters("b", 2), new ZoneVoters("a", 2)));

      // then
      assertThat(priorities.voterPriorities())
          .containsExactly(Map.entry(B1, 4), Map.entry(B2, 3), Map.entry(A1, 2), Map.entry(A2, 1));
    }
  }

  @Nested
  final class LeaderRules {

    private static final long NOW = 1_000_000;
    private static final long ELECTION_TIMEOUT = 2_500;
    private static final MemberId LEADER = C1;

    private static Map<MemberId, BrokerState> active(final Collection<MemberId> brokers) {
      final var states = new LinkedHashMap<MemberId, BrokerState>();
      brokers.forEach(broker -> states.put(broker, BrokerState.ACTIVE));
      return states;
    }

    private static ClusterView view(final Map<MemberId, BrokerState> states) {
      return new ClusterView(true, 5, states, Set.of(), Set.of(), Set.of());
    }

    private static ClusterView view(
        final Map<MemberId, BrokerState> states,
        final Set<MemberId> pendingJoins,
        final Set<MemberId> pendingLeaves,
        final Set<MemberId> pendingRemoves) {
      return new ClusterView(true, 5, states, pendingJoins, pendingLeaves, pendingRemoves);
    }

    /** Each member of the configuration has caught up and answered just now. */
    private static ReplicationStatus caughtUp(final Configuration configuration) {
      return new ReplicationStatus(
          3,
          100,
          configuration.newMembers().stream()
              .filter(member -> !member.memberId().equals(LEADER))
              .map(
                  member ->
                      new MemberStatus(
                          member.memberId(),
                          member.getIncarnation(),
                          member.getType(),
                          100,
                          NOW,
                          0))
              .toList());
    }

    private static ReplicationStatus withStatus(
        final ReplicationStatus replication, final MemberStatus status) {
      final var members =
          replication.members().stream()
              .filter(member -> !member.memberId().equals(status.memberId()))
              .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
      members.add(status);
      return new ReplicationStatus(replication.term(), replication.commitIndex(), members);
    }

    /** Each member publishes the incarnation that it has in the configuration. */
    private static Map<MemberId, Long> published(final Configuration configuration) {
      final var incarnations = new LinkedHashMap<MemberId, Long>();
      configuration
          .newMembers()
          .forEach(member -> incarnations.put(member.memberId(), member.getIncarnation()));
      return incarnations;
    }

    private static LeaderInput input(
        final Configuration configuration,
        final ClusterView view,
        final Map<MemberId, Long> incarnations,
        final ReplicationStatus replication) {
      return new LeaderInput(
          LEADER,
          configuration,
          true,
          view,
          incarnations,
          replication,
          POLICY,
          NOW,
          ELECTION_TIMEOUT,
          1024);
    }

    private static LeaderInput input(final Configuration configuration, final ClusterView view) {
      return input(configuration, view, published(configuration), caughtUp(configuration));
    }

    private static Map<MemberId, Type> targetTypes(final LeaderDecision decision) {
      assertThat(decision.change()).describedAs(decision.reason()).isNotNull();
      final var types = new LinkedHashMap<MemberId, Type>();
      decision.change().members().forEach(member -> types.put(member.memberId(), member.getType()));
      return types;
    }

    private static Map<MemberId, Long> targetIncarnations(final LeaderDecision decision) {
      final var incarnations = new LinkedHashMap<MemberId, Long>();
      decision
          .change()
          .members()
          .forEach(member -> incarnations.put(member.memberId(), member.getIncarnation()));
      return incarnations;
    }

    @Test
    void shouldDoNothingWhenMembersAgreeWithClusterAndPolicy() {
      // given
      final var configuration = configuration(bootstrapped());

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(configuration, view(active(bootstrapped().keySet()))));

      // then
      assertThat(decision.change()).isNull();
      assertThat(decision.transferLeadership()).isFalse();
      assertThat(decision.fencedNotices()).isEmpty();
    }

    @Test
    void shouldAddJoiningBrokerAsPassive() {
      // given
      final var configuration = configuration(bootstrapped());
      final var b4 = MemberId.from("b", 4);
      final var states = active(bootstrapped().keySet());
      states.put(b4, BrokerState.JOINING);
      final var incarnations = published(configuration);
      incarnations.put(b4, 44L);

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(configuration, view(states), incarnations, caughtUp(configuration)));

      // then - rule 3
      assertThat(targetTypes(decision)).containsEntry(b4, PASSIVE).hasSize(8);
      assertThat(targetIncarnations(decision)).containsEntry(b4, 44L);
      assertThat(decision.change().membershipVersion()).isEqualTo(5);
      assertThat(decision.change().expectedIndex()).isEqualTo(configuration.index());
    }

    @Test
    void shouldAddTargetOfPendingMemberJoin() {
      // given - the broker is not in the dynamic configuration yet (D21)
      final var configuration = configuration(bootstrapped());
      final var b4 = MemberId.from("b", 4);
      final var incarnations = published(configuration);
      incarnations.put(b4, 44L);

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(active(bootstrapped().keySet()), Set.of(b4), Set.of(), Set.of()),
                  incarnations,
                  caughtUp(configuration)));

      // then
      assertThat(targetTypes(decision)).containsEntry(b4, PASSIVE);
    }

    @Test
    void shouldRemoveTargetOfCancelledMemberJoin() {
      // given - b4 was added for a pending MemberJoin, then the plan was cancelled
      final var withB4 = new LinkedHashMap<>(bootstrapped());
      final var b4 = MemberId.from("b", 4);
      withB4.put(b4, PASSIVE);
      final var configuration = configuration(withB4, Map.of(b4, 44L), List.of(), POLICY);

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(configuration, view(active(bootstrapped().keySet()))));

      // then - rule 1
      assertThat(targetTypes(decision)).doesNotContainKey(b4).hasSize(7);
    }

    @Test
    void shouldNotAddBrokerWithoutIncarnation() {
      // given - a broker of an older version publishes no incarnation (Q12)
      final var configuration = configuration(bootstrapped());
      final var b4 = MemberId.from("b", 4);
      final var states = active(bootstrapped().keySet());
      states.put(b4, BrokerState.JOINING);

      // when
      final var decision = SystemPartitionMembership.decide(input(configuration, view(states)));

      // then
      assertThat(decision.change()).isNull();
    }

    @Test
    void shouldSendFencedNoticeToBrokerWithFencedIncarnationInsteadOfAddingIt() {
      // given - b4 was removed and comes back with its old data
      final var b4 = MemberId.from("b", 4);
      final var configuration =
          configuration(bootstrapped(), Map.of(), List.of(new FenceEntry(b4, 0)), POLICY);
      final var states = active(bootstrapped().keySet());
      states.put(b4, BrokerState.JOINING);
      final var incarnations = published(configuration);
      incarnations.put(b4, 0L);

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(configuration, view(states), incarnations, caughtUp(configuration)));

      // then
      assertThat(decision.change()).isNull();
      assertThat(decision.fencedNotices()).containsExactly(b4);
    }

    @Test
    void shouldRemoveLeavingVoterAndPromoteReplacementInOneChange() {
      // given
      final var configuration = configuration(bootstrapped());
      final var states = active(bootstrapped().keySet());
      states.put(B1, BrokerState.LEAVING);

      // when
      final var decision = SystemPartitionMembership.decide(input(configuration, view(states)));

      // then - rule 1 with a replacement of the same zone (D9)
      assertThat(targetTypes(decision))
          .doesNotContainKey(B1)
          .containsEntry(B2, ACTIVE)
          .containsEntry(B3, ACTIVE)
          .hasSize(6);
    }

    @Test
    void shouldRemoveLeavingVoterWithoutReplacementIfNoneHasCaughtUp() {
      // given
      final var configuration = configuration(bootstrapped());
      final var states = active(bootstrapped().keySet());
      states.put(B1, BrokerState.LEAVING);
      final var replication =
          withStatus(caughtUp(configuration), new MemberStatus(B3, 0, PASSIVE, 0, NOW, 1 << 30));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(configuration, view(states), published(configuration), replication));

      // then
      assertThat(targetTypes(decision)).doesNotContainKey(B1).containsEntry(B3, PASSIVE);
    }

    @Test
    void shouldRemoveLeftBrokersAndTargetsOfPendingRemoveInOneChange() {
      // given - zone a is force-removed: A1 and A2 are targets of MemberRemove, A3 is LEFT
      final var configuration = configuration(bootstrapped());
      final var states = active(bootstrapped().keySet());
      states.put(A3, BrokerState.LEFT);

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(states, Set.of(), Set.of(), Set.of(A1, A2)),
                  published(configuration),
                  caughtUp(configuration)));

      // then - nobody can replace them in zone a; their slots stay empty
      assertThat(targetTypes(decision))
          .containsExactlyInAnyOrderEntriesOf(
              members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
    }

    @Test
    void shouldNotRemoveOrPromoteTargetOfPendingMemberLeave() {
      // given - B1 is the target of a pending MemberLeave but not LEAVING yet; B3 too
      final var configuration =
          configuration(
              members(C1, ACTIVE, A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, A3, PASSIVE, B3, PASSIVE));
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(
                      active(
                          configuration.newMembers().stream().map(RaftMember::memberId).toList()),
                      Set.of(),
                      Set.of(B1, B3),
                      Set.of()),
                  published(configuration),
                  caughtUp(configuration)));

      // then - B1 keeps its vote, and B3 is not promoted into the empty slot of zone b
      assertThat(decision.change()).isNull();
    }

    @Test
    void shouldTransferLeadershipBeforeRemovingTheLeader() {
      // given
      final var configuration = configuration(bootstrapped());
      final var states = active(bootstrapped().keySet());
      states.put(LEADER, BrokerState.LEAVING);

      // when
      final var decision = SystemPartitionMembership.decide(input(configuration, view(states)));

      // then
      assertThat(decision.change()).isNull();
      assertThat(decision.transferLeadership()).isTrue();
    }

    @Test
    void shouldReplaceSilentVoterThatPublishesNewIncarnation() {
      // given - A1 lost its disk and runs with incarnation 11; its old incarnation is silent
      final var configuration = configuration(bootstrapped());
      final var incarnations = published(configuration);
      incarnations.put(A1, 11L);
      final var replication =
          withStatus(
              caughtUp(configuration),
              new MemberStatus(A1, 0, ACTIVE, 100, NOW - ELECTION_TIMEOUT - 1, 0));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration, view(active(bootstrapped().keySet())), incarnations, replication));

      // then - rule 2: A1#11 is added as passive and A3 is promoted in the same change
      assertThat(targetTypes(decision)).containsEntry(A1, PASSIVE).containsEntry(A3, ACTIVE);
      assertThat(targetIncarnations(decision)).containsEntry(A1, 11L);
    }

    @Test
    void shouldReplacePassiveMemberThatPublishesNewIncarnation() {
      // given
      final var configuration = configuration(bootstrapped());
      final var incarnations = published(configuration);
      incarnations.put(B3, 33L);
      final var replication =
          withStatus(
              caughtUp(configuration),
              new MemberStatus(B3, 0, PASSIVE, 100, NOW - ELECTION_TIMEOUT - 1, 0));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration, view(active(bootstrapped().keySet())), incarnations, replication));

      // then
      assertThat(targetTypes(decision)).containsExactlyInAnyOrderEntriesOf(bootstrapped());
      assertThat(targetIncarnations(decision)).containsEntry(B3, 33L);
    }

    @Test
    void shouldNotReplaceIncarnationThatAnsweredWithinElectionTimeout() {
      // given - a second process publishes another incarnation, the member still answers
      final var configuration = configuration(bootstrapped());
      final var incarnations = published(configuration);
      incarnations.put(A1, 11L);

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(active(bootstrapped().keySet())),
                  incarnations,
                  caughtUp(configuration)));

      // then
      assertThat(decision.change()).isNull();
    }

    @Test
    void shouldNotReplaceWithFencedIncarnation() {
      // given
      final var configuration =
          configuration(bootstrapped(), Map.of(), List.of(new FenceEntry(A1, 11)), POLICY);
      final var incarnations = published(configuration);
      incarnations.put(A1, 11L);
      final var replication =
          withStatus(
              caughtUp(configuration),
              new MemberStatus(A1, 0, ACTIVE, 100, NOW - ELECTION_TIMEOUT - 1, 0));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration, view(active(bootstrapped().keySet())), incarnations, replication));

      // then
      assertThat(decision.change()).isNull();
    }

    @Test
    void shouldPromoteCaughtUpPassiveMemberIntoEmptySlot() {
      // given - zone a has one voter
      final var configuration =
          configuration(
              members(C1, ACTIVE, A1, ACTIVE, B1, ACTIVE, B2, ACTIVE, A2, PASSIVE, A3, PASSIVE));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(
                      active(
                          configuration.newMembers().stream()
                              .map(RaftMember::memberId)
                              .toList()))));

      // then - rule 5, lowest node index first
      assertThat(targetTypes(decision)).containsEntry(A2, ACTIVE).containsEntry(A3, PASSIVE);
    }

    @Test
    void shouldNotPromoteMemberThatDidNotAnswerRecentlyOrLags() {
      // given
      final var configuration =
          configuration(
              members(C1, ACTIVE, A1, ACTIVE, B1, ACTIVE, B2, ACTIVE, A2, PASSIVE, A3, PASSIVE));
      var replication =
          withStatus(
              caughtUp(configuration),
              new MemberStatus(A2, 0, PASSIVE, 100, NOW - ELECTION_TIMEOUT - 1, 0));
      replication = withStatus(replication, new MemberStatus(A3, 0, PASSIVE, 50, NOW, 1 << 30));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(
                      active(
                          configuration.newMembers().stream().map(RaftMember::memberId).toList())),
                  published(configuration),
                  replication));

      // then
      assertThat(decision.change()).isNull();
    }

    @Test
    void shouldPromoteMemberThatLagsBelowThreshold() {
      // given - under load a passive member lags a little
      final var configuration =
          configuration(members(C1, ACTIVE, A1, ACTIVE, B1, ACTIVE, B2, ACTIVE, A2, PASSIVE));
      final var replication =
          withStatus(caughtUp(configuration), new MemberStatus(A2, 0, PASSIVE, 98, NOW, 512));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(
                      active(
                          configuration.newMembers().stream().map(RaftMember::memberId).toList())),
                  published(configuration),
                  replication));

      // then
      assertThat(targetTypes(decision)).containsEntry(A2, ACTIVE);
    }

    @Test
    void shouldDemoteExtraVoterWithHighestNodeIndex() {
      // given - zone a has three voters
      final var configuration =
          configuration(members(C1, ACTIVE, A1, ACTIVE, A2, ACTIVE, A3, ACTIVE, B1, ACTIVE));

      // when
      final var decision =
          SystemPartitionMembership.decide(
              input(
                  configuration,
                  view(
                      active(
                          configuration.newMembers().stream()
                              .map(RaftMember::memberId)
                              .toList()))));

      // then - rule 4 before rule 5
      assertThat(targetTypes(decision)).containsEntry(A3, PASSIVE).containsEntry(A2, ACTIVE);
    }

    @Test
    void shouldDemoteVoterOfZoneOutsidePolicyOnlyOnceAPolicyZoneHasAVoter() {
      // given - only X1, outside the policy, is a voter, e.g. after a forced change
      final var onlyOutside = configuration(members(X1, ACTIVE, A1, PASSIVE));
      final var outsideAndPolicy = configuration(members(X1, ACTIVE, A1, ACTIVE));
      final var states = active(List.of(X1, A1, LEADER));

      // when
      final var keep =
          SystemPartitionMembership.decide(
              new LeaderInput(
                  X1,
                  onlyOutside,
                  true,
                  view(states),
                  published(onlyOutside),
                  new ReplicationStatus(
                      3, 100, List.of(new MemberStatus(A1, 0, PASSIVE, 0, 0, 1 << 30))),
                  POLICY,
                  NOW,
                  ELECTION_TIMEOUT,
                  1024));
      final var demote =
          SystemPartitionMembership.decide(
              new LeaderInput(
                  A1,
                  outsideAndPolicy,
                  true,
                  view(states),
                  published(outsideAndPolicy),
                  caughtUp(outsideAndPolicy),
                  POLICY,
                  NOW,
                  ELECTION_TIMEOUT,
                  1024));

      // then
      assertThat(keep.change()).isNull();
      assertThat(targetTypes(demote)).containsEntry(X1, PASSIVE).containsEntry(A1, ACTIVE);
    }

    @Test
    void shouldWaitWhileDynamicConfigurationIsNotInitialized() {
      // given
      final var configuration = configuration(bootstrapped());

      // when
      final var decision =
          SystemPartitionMembership.decide(input(configuration, ClusterView.notInitialized()));

      // then - not even rule 1 removes the members that it does not know yet
      assertThat(decision.change()).isNull();
      assertThat(decision.reason()).contains("not initialized");
    }

    @Test
    void shouldWaitWhileViewIsOlderThanMembershipVersion() {
      // given - an earlier leader changed the members with a newer view (R19)
      final var configuration =
          new Configuration(
              10, 2, 1, raftMembers(bootstrapped(), Map.of()), List.of(), false, List.of(), 9);
      final var states = active(bootstrapped().keySet());
      states.put(B1, BrokerState.LEAVING);

      // when
      final var decision = SystemPartitionMembership.decide(input(configuration, view(states)));

      // then
      assertThat(decision.change()).isNull();
      assertThat(decision.reason()).contains("older than the membership version");
    }

    @Test
    void shouldWaitWhileJointChangeIsInProgress() {
      // given
      final var joint =
          new Configuration(
              10,
              2,
              1,
              raftMembers(bootstrapped(), Map.of()),
              raftMembers(bootstrapped(), Map.of()),
              false,
              List.of(),
              0);
      final var states = active(bootstrapped().keySet());
      states.put(B1, BrokerState.LEAVING);

      // when
      final var decision = SystemPartitionMembership.decide(input(joint, view(states)));

      // then
      assertThat(decision.change()).isNull();
    }

    @Test
    void shouldAuthorizeOnlyJoiningOrActiveBrokersAndPendingJoinTargets() {
      // given
      final var b4 = MemberId.from("b", 4);
      final var b5 = MemberId.from("b", 5);
      final var states = new LinkedHashMap<MemberId, BrokerState>();
      states.put(A1, BrokerState.ACTIVE);
      states.put(A2, BrokerState.JOINING);
      states.put(A3, BrokerState.LEAVING);
      states.put(B1, BrokerState.LEFT);
      states.put(B2, BrokerState.ACTIVE);
      final var clusterView = view(states, Set.of(b4, B1), Set.of(B2), Set.of(b5));

      // then - LEFT and pending removals have priority over a pending MemberJoin
      assertThat(SystemPartitionMembership.isAuthorized(clusterView, A1)).isTrue();
      assertThat(SystemPartitionMembership.isAuthorized(clusterView, A2)).isTrue();
      assertThat(SystemPartitionMembership.isAuthorized(clusterView, b4)).isTrue();
      assertThat(SystemPartitionMembership.isAuthorized(clusterView, A3)).isFalse();
      assertThat(SystemPartitionMembership.isAuthorized(clusterView, B1)).isFalse();
      assertThat(SystemPartitionMembership.isAuthorized(clusterView, B2)).isFalse();
      assertThat(SystemPartitionMembership.isAuthorized(clusterView, b5)).isFalse();
      assertThat(SystemPartitionMembership.mustBeRemoved(clusterView, B2)).isFalse();
      assertThat(SystemPartitionMembership.mustBeRemoved(clusterView, A3)).isTrue();
    }

    @Test
    void shouldProduceChangeThatCarriesViewVersionAndExpectedIndex() {
      // given
      final var configuration = configuration(bootstrapped());
      final var states = active(bootstrapped().keySet());
      states.put(B3, BrokerState.LEAVING);

      // when
      final MembershipChange change =
          SystemPartitionMembership.decide(input(configuration, view(states))).change();

      // then
      assertThat(change).isNotNull();
      assertThat(change.membershipVersion()).isEqualTo(5);
      assertThat(change.expectedIndex()).isEqualTo(10);
    }
  }
}
