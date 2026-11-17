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
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.VoterPolicy;
import io.atomix.raft.cluster.VoterPolicy.ZoneVoters;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.SelfPromotionCheck;
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
        10, 2, 1, raftMembers(members, incarnations), List.of(), false, fenced, policy);
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
      final var selection = SystemPartitionMembership.selectVoters(configuration, Set.of());

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
      final var selection = SystemPartitionMembership.selectVoters(configuration, Set.of());

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
      final var selection = SystemPartitionMembership.selectVoters(configuration, Set.of());

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
      final var selection = SystemPartitionMembership.selectVoters(configuration, Set.of());

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
      final var selection = SystemPartitionMembership.selectVoters(configuration, Set.of(A3));

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
      final var selection = SystemPartitionMembership.selectVoters(configuration, Set.of());

      // then
      assertThat(selection.zone("a").orElseThrow().candidates()).containsExactly(A2);
    }

    @Test
    void shouldKeepMembersOfZoneOutsidePolicyPassive() {
      // given
      final var configuration =
          configuration(members(C1, ACTIVE, B1, ACTIVE, X1, ACTIVE, A1, PASSIVE));

      // when
      final var selection = SystemPartitionMembership.selectVoters(configuration, Set.of());

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
              POLICY);

      // when - then
      assertThatThrownBy(() -> SystemPartitionMembership.selectVoters(joint, Set.of()))
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
      final var priorities = SystemPartitionMembership.priorities(configuration);

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
      final var priorities = SystemPartitionMembership.priorities(configuration);

      // then
      assertThat(priorities.targetPriority()).isEqualTo(5);
      assertThat(priorities.voterPriorities())
          .containsExactly(Map.entry(C1, 5), Map.entry(B1, 4), Map.entry(B2, 3));
    }

    @Test
    void shouldGiveExtraVotersAtLeastPriorityOne() {
      // given - a policy of 1 voter with 3 voters after a forced change
      final var configuration =
          configuration(
              members(A1, ACTIVE, A2, ACTIVE, X1, ACTIVE),
              Map.of(),
              List.of(),
              VoterPolicy.of(new ZoneVoters("a", 1)));

      // when
      final var priorities = SystemPartitionMembership.priorities(configuration);

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
              members(A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE),
              Map.of(),
              List.of(),
              VoterPolicy.of(new ZoneVoters("b", 2), new ZoneVoters("a", 2)));

      // when
      final var priorities = SystemPartitionMembership.priorities(configuration);

      // then
      assertThat(priorities.voterPriorities())
          .containsExactly(Map.entry(B1, 4), Map.entry(B2, 3), Map.entry(A1, 2), Map.entry(A2, 1));
    }
  }

  @Nested
  final class SelfPromotion {

    private final Configuration zoneAEmpty =
        configuration(
            members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE, A1, PASSIVE, A2, PASSIVE),
            Map.of(A1, 4L, A2, 7L),
            List.of(new FenceEntry(A1, 0), new FenceEntry(A2, 0)),
            POLICY);

    private SelfPromotionCheck caughtUp(final MemberId self, final long incarnation) {
      return new SelfPromotionCheck(self, incarnation, true, false, false, 100, 100);
    }

    @Test
    void shouldPromoteIntoEmptySlot() {
      // when
      final var target = SystemPartitionMembership.selfPromotion(zoneAEmpty, caughtUp(A1, 4));

      // then
      assertThat(target)
          .hasValue(
              members(A1, ACTIVE, C1, ACTIVE, B1, ACTIVE, A2, PASSIVE, B2, ACTIVE, B3, PASSIVE));
    }

    @Test
    void shouldNotPromoteIntoFullZone() {
      // when
      final var target = SystemPartitionMembership.selfPromotion(zoneAEmpty, caughtUp(B3, 0));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteBeforeDynamicConfigurationIsInitialized() {
      // when
      final var target =
          SystemPartitionMembership.selfPromotion(
              zoneAEmpty, new SelfPromotionCheck(A1, 4, false, false, false, 100, 100));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteExcludedBroker() {
      // when
      final var target =
          SystemPartitionMembership.selfPromotion(
              zoneAEmpty, new SelfPromotionCheck(A1, 4, true, true, false, 100, 100));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteWithEmptyStartMarker() {
      // when
      final var target =
          SystemPartitionMembership.selfPromotion(
              zoneAEmpty, new SelfPromotionCheck(A1, 4, true, false, true, 100, 100));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteBeforeCaughtUp() {
      // when
      final var target =
          SystemPartitionMembership.selfPromotion(
              zoneAEmpty, new SelfPromotionCheck(A1, 4, true, false, false, 99, 100));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteFencedIncarnation() {
      // given - A1 still runs with its fenced incarnation 0
      final var fenced =
          configuration(
              members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, A1, PASSIVE),
              Map.of(),
              List.of(new FenceEntry(A1, 0)),
              POLICY);

      // when
      final var target = SystemPartitionMembership.selfPromotion(fenced, caughtUp(A1, 0));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteWithIncarnationThatIsNotInConfiguration() {
      // when - the broker joined again, but the configuration has its previous incarnation
      final var target = SystemPartitionMembership.selfPromotion(zoneAEmpty, caughtUp(A1, 5));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteNonMember() {
      // when
      final var target = SystemPartitionMembership.selfPromotion(zoneAEmpty, caughtUp(A3, 0));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldNotPromoteInZoneOutsidePolicy() {
      // given
      final var configuration = configuration(members(C1, ACTIVE, B1, ACTIVE, X1, PASSIVE));

      // when
      final var target = SystemPartitionMembership.selfPromotion(configuration, caughtUp(X1, 0));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldRankCandidatesByNodeIndex() {
      // when - then
      assertThat(SystemPartitionMembership.selfPromotionRank(zoneAEmpty, A1, Set.of())).hasValue(0);
      assertThat(SystemPartitionMembership.selfPromotionRank(zoneAEmpty, A2, Set.of())).hasValue(1);
      assertThat(SystemPartitionMembership.selfPromotionRank(zoneAEmpty, A2, Set.of(A1)))
          .hasValue(0);
      assertThat(SystemPartitionMembership.selfPromotionRank(zoneAEmpty, A1, Set.of(A1))).isEmpty();
    }
  }

  @Nested
  final class Demotion {

    @Test
    void shouldDemoteExtraVoterWithHighestNodeIndex() {
      // given
      final var configuration =
          configuration(members(A1, ACTIVE, A2, ACTIVE, A3, ACTIVE, B1, ACTIVE));

      // when
      final var target = SystemPartitionMembership.demotion(configuration);

      // then
      assertThat(target).hasValue(members(A1, ACTIVE, B1, ACTIVE, A2, ACTIVE, A3, PASSIVE));
    }

    @Test
    void shouldDemoteOneVoterPerChange() {
      // given - policy a:1 with three voters in zone a
      final var configuration =
          configuration(
              members(A1, ACTIVE, A2, ACTIVE, A3, ACTIVE),
              Map.of(),
              List.of(),
              VoterPolicy.of(new ZoneVoters("a", 1)));

      // when
      final var target = SystemPartitionMembership.demotion(configuration);

      // then
      assertThat(target).hasValue(members(A1, ACTIVE, A2, ACTIVE, A3, PASSIVE));
    }

    @Test
    void shouldNotDemoteWithinPolicy() {
      // when
      final var target = SystemPartitionMembership.demotion(configuration(bootstrapped()));

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldKeepOnlyVoterOfZoneOutsidePolicy() {
      // given - a forced change made the only member with the most recent log a voter
      final var configuration = configuration(members(X1, ACTIVE, A1, PASSIVE, B1, PASSIVE));

      // when
      final var target = SystemPartitionMembership.demotion(configuration);

      // then
      assertThat(target).isEmpty();
    }

    @Test
    void shouldDemoteVoterOutsidePolicyOncePolicyZoneHasVoter() {
      // given
      final var configuration = configuration(members(X1, ACTIVE, A1, ACTIVE, B1, PASSIVE));

      // when
      final var target = SystemPartitionMembership.demotion(configuration);

      // then
      assertThat(target).hasValue(members(A1, ACTIVE, B1, PASSIVE, X1, PASSIVE));
    }
  }

  @Nested
  final class MemberLeave {

    @Test
    void shouldReplaceLeavingVoterInOneChange() {
      // given
      final var configuration = configuration(bootstrapped());

      // when
      final var change =
          SystemPartitionMembership.leaveChange(configuration, A1, Set.of(), member -> true)
              .orElseThrow();

      // then
      assertThat(change.target())
          .isEqualTo(
              members(C1, ACTIVE, A2, ACTIVE, A3, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
      assertThat(change.replacement()).isEqualTo(A3);
      assertThat(change.fence()).isEqualTo(new FenceEntry(A1, 0));
    }

    @Test
    void shouldRemoveVoterWithoutReplacementIfNoneIsAvailable() {
      // given
      final var configuration = configuration(bootstrapped());

      // when - A3 has not caught up
      final var change =
          SystemPartitionMembership.leaveChange(configuration, A1, Set.of(), member -> false)
              .orElseThrow();

      // then
      assertThat(change.target())
          .isEqualTo(
              members(C1, ACTIVE, A2, ACTIVE, A3, PASSIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));
      assertThat(change.replacement()).isNull();
    }

    @Test
    void shouldNotReplaceWithExcludedOrOtherZoneMember() {
      // given
      final var configuration = configuration(bootstrapped());

      // when - A3 is the target of a pending removal; B3 is in another zone
      final var change =
          SystemPartitionMembership.leaveChange(configuration, A1, Set.of(A3), member -> true)
              .orElseThrow();

      // then
      assertThat(change.replacement()).isNull();
      assertThat(change.target()).containsEntry(B3, PASSIVE);
    }

    @Test
    void shouldRemovePassiveMember() {
      // given
      final var configuration = configuration(bootstrapped(), Map.of(B3, 9L), List.of(), POLICY);

      // when
      final var change =
          SystemPartitionMembership.leaveChange(configuration, B3, Set.of(), member -> true)
              .orElseThrow();

      // then
      assertThat(change.target()).doesNotContainKey(B3).hasSize(6);
      assertThat(change.fence()).isEqualTo(new FenceEntry(B3, 9));
      assertThat(change.replacement()).isNull();
    }

    @Test
    void shouldCompleteIfBrokerIsNotMember() {
      // given
      final var configuration = configuration(members(C1, ACTIVE, B1, ACTIVE));

      // when
      final var change =
          SystemPartitionMembership.leaveChange(configuration, A1, Set.of(), member -> true);

      // then
      assertThat(change).isEmpty();
    }
  }
}
