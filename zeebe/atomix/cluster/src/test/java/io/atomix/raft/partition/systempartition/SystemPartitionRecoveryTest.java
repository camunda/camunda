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
import static io.atomix.raft.partition.systempartition.SystemPartitionMembershipTest.POLICY;
import static io.atomix.raft.partition.systempartition.SystemPartitionMembershipTest.members;
import static io.atomix.raft.partition.systempartition.SystemPartitionMembershipTest.raftMembers;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.VoterPolicy;
import io.atomix.raft.cluster.VoterPolicy.ZoneVoters;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.MemberReport;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.AlreadyDone;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.ForcedChange;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.NormalChange;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.ResendForced;
import io.atomix.raft.storage.system.Configuration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class SystemPartitionRecoveryTest {

  private static final MemberId A1 = MemberId.from("a", 1);
  private static final MemberId A2 = MemberId.from("a", 2);
  private static final MemberId A3 = MemberId.from("a", 3);
  private static final MemberId A4 = MemberId.from("a", 4);
  private static final MemberId B1 = MemberId.from("b", 1);
  private static final MemberId B2 = MemberId.from("b", 2);
  private static final MemberId B3 = MemberId.from("b", 3);
  private static final MemberId B4 = MemberId.from("b", 4);
  private static final MemberId C1 = MemberId.from("c", 1);
  private static final MemberId X1 = MemberId.from("x", 1);

  private static final VoterPolicy DUAL_REGION =
      VoterPolicy.of(new ZoneVoters("a", 2), new ZoneVoters("b", 2));

  private static Configuration configuration(
      final long index, final Map<MemberId, Type> members, final VoterPolicy policy) {
    return new Configuration(
        index, 3, 1, raftMembers(members, Map.of()), List.of(), false, List.of(), policy);
  }

  private static Map<MemberId, Type> dualRegion() {
    return members(
        A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE, A3, PASSIVE, A4, PASSIVE, B3, PASSIVE, B4,
        PASSIVE);
  }

  private static Map<MemberId, Type> twoAndAHalfRegions() {
    return members(
        C1, ACTIVE, A1, ACTIVE, A2, ACTIVE, B1, ACTIVE, B2, ACTIVE, A3, PASSIVE, B3, PASSIVE);
  }

  private static MemberReport report(
      final MemberId member, final Configuration configuration, final long lastLogIndex) {
    return new MemberReport(member, configuration, 3, lastLogIndex, lastLogIndex);
  }

  private static List<FenceEntry> fenced(final MemberId... members) {
    return Arrays.stream(members).map(member -> new FenceEntry(member, 0)).toList();
  }

  @Nested
  final class RemoveMembers {

    @Test
    void shouldUseNormalChangeIfRemainingVotersAreQuorum() {
      // given - 2.5 regions, zone a fails
      final var configuration = configuration(7, twoAndAHalfRegions(), POLICY);
      final var reports =
          List.of(
              report(C1, configuration, 20), report(B1, configuration, 20),
              report(B2, configuration, 20), report(B3, configuration, 18));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(A1, A2, A3), reports);

      // then
      assertThat(decision)
          .isEqualTo(
              new NormalChange(
                  members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE),
                  fenced(A1, A2, A3),
                  7,
                  3));
    }

    @Test
    void shouldUseHighestConfiguration() {
      // given - B3 missed the last change, which added B4
      final var old = configuration(5, twoAndAHalfRegions(), POLICY);
      final var withB4 = new LinkedHashMap<>(twoAndAHalfRegions());
      withB4.put(B4, PASSIVE);
      final var current = configuration(7, withB4, POLICY);

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(A1, A2, A3),
              List.of(report(B3, old, 10), report(C1, current, 20), report(B1, current, 20)));

      // then
      assertThat(decision).isInstanceOf(NormalChange.class);
      final var normal = (NormalChange) decision;
      assertThat(normal.index()).isEqualTo(7);
      assertThat(normal.members()).containsEntry(B4, PASSIVE);
    }

    @Test
    void shouldForceMembersWithMostRecentLogIfRemainingVotersAreNoQuorum() {
      // given - dual region, zone b fails
      final var configuration = configuration(4, dualRegion(), DUAL_REGION);
      final var reports =
          List.of(
              report(A1, configuration, 30), report(A2, configuration, 30),
              report(A3, configuration, 29), report(A4, configuration, 28));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports);

      // then
      assertThat(decision).isInstanceOf(ForcedChange.class);
      assertThat(((ForcedChange) decision).members())
          .isEqualTo(members(A1, ACTIVE, A2, ACTIVE, A3, PASSIVE, A4, PASSIVE));
      assertThat(((ForcedChange) decision).fenced())
          .containsExactlyInAnyOrderElementsOf(fenced(B1, B2, B3, B4));
    }

    @Test
    void shouldBuildForcedTargetFromHighestReport() {
      // given - A4 lags: it missed the rejoin of A3 with incarnation 7 and the fence of X1
      final var old = configuration(3, dualRegion(), DUAL_REGION);
      final var current =
          new Configuration(
              4,
              3,
              1,
              raftMembers(dualRegion(), Map.of(A3, 7L)),
              List.of(),
              false,
              List.of(new FenceEntry(X1, 2)),
              DUAL_REGION);
      final var reports =
          List.of(
              report(A1, current, 30),
              report(A2, current, 30),
              report(A3, current, 30),
              report(A4, old, 28));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports);

      // then - the target has the incarnations, fence list and policy of the highest report
      assertThat(decision).isInstanceOf(ForcedChange.class);
      final var target = ((ForcedChange) decision).target();
      assertThat(target.force()).isTrue();
      assertThat(target.voterPolicy()).isEqualTo(DUAL_REGION);
      assertThat(target.newMembers())
          .filteredOn(member -> member.memberId().equals(A3))
          .extracting(RaftMember::getIncarnation)
          .containsExactly(7L);
      assertThat(target.fenced())
          .containsExactlyInAnyOrder(
              new FenceEntry(X1, 2),
              new FenceEntry(B1, 0),
              new FenceEntry(B2, 0),
              new FenceEntry(B3, 0),
              new FenceEntry(B4, 0));
    }

    @Test
    void shouldForceOnlyPassiveMemberWithMostRecentLog() {
      // given - only A3 has the most recent log
      final var configuration = configuration(4, dualRegion(), DUAL_REGION);
      final var reports =
          List.of(
              report(A1, configuration, 29), report(A2, configuration, 29),
              report(A3, configuration, 30), report(A4, configuration, 28));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports);

      // then
      assertThat(((ForcedChange) decision).members())
          .isEqualTo(members(A3, ACTIVE, A1, PASSIVE, A2, PASSIVE, A4, PASSIVE));
    }

    @Test
    void shouldCompareLastTermBeforeLastIndex() {
      // given - A2 has a shorter log with a higher last term
      final var configuration = configuration(4, dualRegion(), DUAL_REGION);
      final var reports =
          List.of(
              new MemberReport(A1, configuration, 3, 40, 30),
              new MemberReport(A2, configuration, 4, 35, 30));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports);

      // then
      assertThat(((ForcedChange) decision).members())
          .containsEntry(A2, ACTIVE)
          .containsEntry(A1, PASSIVE);
    }

    @Test
    void shouldLimitForcedVotersToSlotsOfZone() {
      // given - all of zone a has the most recent log
      final var configuration = configuration(4, dualRegion(), DUAL_REGION);
      final var reports =
          List.of(
              report(A4, configuration, 30), report(A3, configuration, 30),
              report(A2, configuration, 30), report(A1, configuration, 30));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports);

      // then
      assertThat(((ForcedChange) decision).members())
          .isEqualTo(members(A1, ACTIVE, A2, ACTIVE, A3, PASSIVE, A4, PASSIVE));
    }

    @Test
    void shouldForceMemberOutsidePolicyIfOnlyItHasMostRecentLog() {
      // given
      final var members = new LinkedHashMap<>(dualRegion());
      members.put(X1, PASSIVE);
      final var configuration = configuration(4, members, DUAL_REGION);
      final var reports =
          List.of(
              report(A1, configuration, 29),
              report(A2, configuration, 29),
              report(X1, configuration, 30));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports);

      // then
      assertThat(((ForcedChange) decision).members())
          .containsEntry(X1, ACTIVE)
          .containsEntry(A1, PASSIVE)
          .containsEntry(A2, PASSIVE);
    }

    @Test
    void shouldResendForcedChangeOfEarlierTry() {
      // given - an earlier try forced the configuration
      final var forced =
          new Configuration(
              5,
              3,
              1,
              raftMembers(members(A1, ACTIVE, A2, ACTIVE, A3, PASSIVE, A4, PASSIVE), Map.of()),
              List.of(),
              true,
              fenced(B1, B2, B3, B4),
              DUAL_REGION);
      // A3 now has a more recent log than the forced voters; the decision must not change
      final var reports = List.of(report(A1, forced, 30), report(A3, forced, 31));

      // when
      final var decision = SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports);

      // then
      assertThat(decision).isEqualTo(new ResendForced(forced));
    }

    @Test
    void shouldBeDoneIfBrokersAreNotMembers() {
      // given - the normal change of an earlier try committed
      final var configuration =
          configuration(9, members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE), POLICY);

      // when
      final RecoveryDecision decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(A1, A2, A3), List.of(report(C1, configuration, 20)));

      // then
      assertThat(decision).isInstanceOf(AlreadyDone.class);
    }

    @Test
    void shouldFenceRemovedMembersWithTheirIncarnation() {
      // given - A1 joined again with incarnation 4 and is removed again
      final var configuration =
          new Configuration(
              7,
              3,
              1,
              raftMembers(twoAndAHalfRegions(), Map.of(A1, 4L)),
              List.of(),
              false,
              fenced(A1),
              POLICY);

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(Set.of(A1), List.of(report(C1, configuration, 20)));

      // then
      assertThat(((NormalChange) decision).fenced()).containsExactly(new FenceEntry(A1, 4));
    }
  }

  @Nested
  final class ForceVoters {

    @Test
    void shouldForceSurvivingVotersAndKeepDownBrokersAsPassive() {
      // given - control zone and zone a are down
      final var configuration = configuration(7, twoAndAHalfRegions(), POLICY);
      final var reports =
          List.of(
              report(B1, configuration, 20),
              report(B2, configuration, 20),
              report(B3, configuration, 20));

      // when
      final var decision = SystemPartitionRecovery.forceVoters(Set.of(A1, A2, A3, C1), reports);

      // then - the policy b:2 limits the voters to B1 and B2
      assertThat(decision).isInstanceOf(ForcedChange.class);
      assertThat(((ForcedChange) decision).members())
          .isEqualTo(
              members(
                  B1, ACTIVE, B2, ACTIVE, A1, PASSIVE, A2, PASSIVE, A3, PASSIVE, B3, PASSIVE, C1,
                  PASSIVE));
      assertThat(((ForcedChange) decision).fenced())
          .containsExactlyInAnyOrderElementsOf(fenced(A1, C1, A2, A3));
    }

    @Test
    void shouldResendForcedChangeOfEarlierTry() {
      // given
      final var forced =
          new Configuration(
              8,
              3,
              1,
              raftMembers(
                  members(
                      B1, ACTIVE, B2, ACTIVE, A1, PASSIVE, A2, PASSIVE, A3, PASSIVE, B3, PASSIVE,
                      C1, PASSIVE),
                  Map.of()),
              List.of(),
              true,
              fenced(A1, A2, A3, C1),
              POLICY);

      // when
      final var decision =
          SystemPartitionRecovery.forceVoters(
              Set.of(A1, A2, A3, C1), List.of(report(B1, forced, 20)));

      // then
      assertThat(decision).isInstanceOf(ResendForced.class);
      assertThat(((ResendForced) decision).members()).containsEntry(B1, ACTIVE);
    }

    @Test
    void shouldBeDoneOnceDownBrokersAreFencedAndPassive() {
      // given - the next leader appended a normal configuration after the forced change
      final var configuration =
          new Configuration(
              9,
              4,
              1,
              raftMembers(
                  members(
                      B1, ACTIVE, B2, ACTIVE, A1, PASSIVE, A2, PASSIVE, A3, PASSIVE, B3, PASSIVE,
                      C1, PASSIVE),
                  Map.of()),
              List.of(),
              false,
              fenced(A1, A2, A3, C1),
              POLICY);

      // when
      final var decision =
          SystemPartitionRecovery.forceVoters(
              Set.of(A1, A2, A3, C1), List.of(report(B1, configuration, 20)));

      // then
      assertThat(decision).isInstanceOf(AlreadyDone.class);
    }
  }
}
