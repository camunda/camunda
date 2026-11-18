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
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.MemberReport;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.AlreadyDone;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.ForcedChange;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.NormalChange;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.Rejected;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision.ResendForced;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RejectionReason;
import io.atomix.raft.partition.systempartition.VoterPolicy.ZoneVoters;
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

  private static Configuration configuration(final long index, final Map<MemberId, Type> members) {
    return new Configuration(
        index, 3, 1, raftMembers(members, Map.of()), List.of(), false, List.of(), 4);
  }

  private static Configuration joint(
      final long index,
      final Map<MemberId, Type> oldMembers,
      final Map<MemberId, Type> newMembers) {
    return new Configuration(
        index,
        3,
        1,
        raftMembers(newMembers, Map.of()),
        raftMembers(oldMembers, Map.of()),
        false,
        List.of(),
        4);
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
    return report(member, configuration, 3, lastLogIndex, 0);
  }

  private static MemberReport report(
      final MemberId member,
      final Configuration configuration,
      final long lastLogTerm,
      final long lastLogIndex,
      final long incarnation) {
    return new MemberReport(
        member, configuration, incarnation, lastLogTerm, lastLogIndex, lastLogIndex, null, 0);
  }

  private static List<FenceEntry> fenced(final MemberId... members) {
    return Arrays.stream(members).map(member -> new FenceEntry(member, 0)).toList();
  }

  private static Map<MemberId, Type> types(final Configuration configuration) {
    final var types = new LinkedHashMap<MemberId, Type>();
    configuration.newMembers().forEach(member -> types.put(member.memberId(), member.getType()));
    return types;
  }

  @Nested
  final class RemoveMembers {

    @Test
    void shouldUseNormalChangeIfRemainingVotersAreQuorum() {
      // given - 2.5 regions, zone a fails
      final var configuration = configuration(7, twoAndAHalfRegions());
      final var reports =
          List.of(
              report(C1, configuration, 20), report(B1, configuration, 20),
              report(B2, configuration, 20), report(B3, configuration, 18));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(Set.of(A1, A2, A3), reports, POLICY, false);

      // then - the leader removes them; the executor waits for it
      assertThat(decision).isEqualTo(new NormalChange(configuration, Set.of(A1, A2, A3)));
    }

    @Test
    void shouldUseHighestConfiguration() {
      // given - B3 missed the last change, which added B4
      final var old = configuration(5, twoAndAHalfRegions());
      final var withB4 = new LinkedHashMap<>(twoAndAHalfRegions());
      withB4.put(B4, PASSIVE);
      final var current = configuration(7, withB4);

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(A1, A2, A3),
              List.of(report(B3, old, 10), report(C1, current, 20), report(B1, current, 20)),
              POLICY,
              false);

      // then
      assertThat(decision).isInstanceOf(NormalChange.class);
      assertThat(((NormalChange) decision).configuration().index()).isEqualTo(7);
    }

    @Test
    void shouldForceSurvivingZoneWithoutLossWhenSurvivorsAreBlockingSet() {
      // given - dual region, zone b fails; A1 and A2 are 2 of 4 voters, quorum 3 (5.10.1)
      final var configuration = configuration(7, dualRegion());
      final var reports =
          List.of(
              report(A1, configuration, 20),
              report(A2, configuration, 20),
              report(A3, configuration, 20),
              report(A4, configuration, 19));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4), reports, DUAL_REGION, false);

      // then - the policy limits the voters of zone a to A1 and A2
      assertThat(decision).isInstanceOf(ForcedChange.class);
      final var forced = (ForcedChange) decision;
      assertThat(forced.completeness().complete()).isTrue();
      assertThat(types(forced.target()))
          .containsExactlyInAnyOrderEntriesOf(
              members(A1, ACTIVE, A2, ACTIVE, A3, PASSIVE, A4, PASSIVE));
      assertThat(forced.fenced()).containsExactlyInAnyOrderElementsOf(fenced(B1, B2, B3, B4));
      assertThat(forced.target().force()).isTrue();
      assertThat(forced.target().membershipVersion()).isEqualTo(4);
    }

    @Test
    void shouldRejectForcedChangeIfSurvivorsAreNotBlockingSet() {
      // given - only A1 of the voters answers, and only A3 has the most recent log (5.10.1)
      final var configuration = configuration(7, dualRegion());
      final var reports =
          List.of(
              report(A1, configuration, 18),
              report(A3, configuration, 20),
              report(A4, configuration, 19));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4), reports, DUAL_REGION, false);

      // then
      assertThat(decision).isInstanceOf(Rejected.class);
      final var rejected = (Rejected) decision;
      assertThat(rejected.reason()).isEqualTo(RejectionReason.METADATA_COMPLETENESS_UNKNOWN);
      assertThat(rejected.completeness().answered()).containsExactly(A1);
      assertThat(rejected.completeness().missing()).containsExactlyInAnyOrder(A2, B1, B2);
    }

    @Test
    void shouldForceMemberWithMostRecentLogAndFenceDemotedVotersWhenLossIsAccepted() {
      // given - as before, with acceptPossibleLoss
      final var configuration = configuration(7, dualRegion());
      final var reports =
          List.of(
              report(A1, configuration, 18),
              report(A3, configuration, 20),
              report(A4, configuration, 19));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(Set.of(B1, B2, B3, B4), reports, DUAL_REGION, true);

      // then - A1 and A2 were voters and are not voters any more: they are fenced too (item 6)
      assertThat(decision).isInstanceOf(ForcedChange.class);
      final var forced = (ForcedChange) decision;
      assertThat(forced.completeness().complete()).isFalse();
      assertThat(types(forced.target()))
          .containsExactlyInAnyOrderEntriesOf(
              members(A3, ACTIVE, A1, PASSIVE, A2, PASSIVE, A4, PASSIVE));
      assertThat(forced.fenced())
          .containsExactlyInAnyOrderElementsOf(fenced(B1, B2, B3, B4, A1, A2));
    }

    @Test
    void shouldNotCountMemberThatAnswersWithAnotherIncarnation() {
      // given - A2 lost its disk and answers with incarnation 22
      final var configuration = configuration(7, dualRegion());
      final var reports = List.of(report(A1, configuration, 20), report(A2, null, 0, 0, 22));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4), reports, DUAL_REGION, false);

      // then - R has only A1, which is not a blocking set of 4 voters
      assertThat(decision).isInstanceOf(Rejected.class);
      assertThat(((Rejected) decision).completeness().missing()).contains(A2);
    }

    @Test
    void shouldNeedQuorumInBothSetsOfJointConfiguration() {
      // given - a joint change replaces B2 by B3; B1 and B2 fail
      final var oldMembers = dualRegion();
      final var newMembers = new LinkedHashMap<>(dualRegion());
      newMembers.put(B2, PASSIVE);
      newMembers.put(B3, ACTIVE);
      final var configuration = joint(7, oldMembers, newMembers);
      final var reports =
          List.of(
              report(A1, configuration, 20),
              report(A2, configuration, 20),
              report(B3, configuration, 20));

      // when - the new set {A1, A2, B1, B3} keeps 3 of 4, the old set {A1, A2, B1, B2} only 2
      final var decision =
          SystemPartitionRecovery.removeMembers(Set.of(B1, B2), reports, DUAL_REGION, false);

      // then - a forced change; R = {A1, A2, B3} is a blocking set of both sets
      assertThat(decision).isInstanceOf(ForcedChange.class);
      final var forced = (ForcedChange) decision;
      assertThat(forced.completeness().complete()).isTrue();
      assertThat(forced.completeness().voterSets()).hasSize(2);
      assertThat(types(forced.target()))
          .containsEntry(A1, ACTIVE)
          .containsEntry(A2, ACTIVE)
          .containsEntry(B3, ACTIVE)
          .doesNotContainKeys(B1, B2);
      assertThat(forced.fenced()).containsExactlyInAnyOrderElementsOf(fenced(B1, B2));
    }

    @Test
    void shouldFenceVotersOfBothSetsThatAreNotVotersOfForcedConfiguration() {
      // given - a joint change replaces B2 by B3; zone b fails except B3, which lags
      final var oldMembers = dualRegion();
      final var newMembers = new LinkedHashMap<>(dualRegion());
      newMembers.put(B2, PASSIVE);
      newMembers.put(B3, ACTIVE);
      final var configuration = joint(7, oldMembers, newMembers);
      final var reports =
          List.of(
              report(A1, configuration, 20),
              report(A2, configuration, 20),
              report(B3, configuration, 15));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(Set.of(B1, B2), reports, DUAL_REGION, false);

      // then - B3 is a voter of the new set without the most recent log: it is fenced
      final var forced = (ForcedChange) decision;
      assertThat(types(forced.target())).containsEntry(B3, PASSIVE);
      assertThat(forced.fenced()).containsExactlyInAnyOrderElementsOf(fenced(B1, B2, B3));
    }

    @Test
    void shouldCompareLastTermBeforeLastIndex() {
      // given - A2 has fewer entries, but of a higher term
      final var configuration = configuration(7, dualRegion());
      final var reports =
          List.of(report(A1, configuration, 3, 30, 0), report(A2, configuration, 4, 20, 0));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4), reports, DUAL_REGION, false);

      // then
      final var forced = (ForcedChange) decision;
      assertThat(types(forced.target())).containsEntry(A2, ACTIVE).containsEntry(A1, PASSIVE);
    }

    @Test
    void shouldForceMemberOutsidePolicyIfOnlyItHasMostRecentLog() {
      // given - X1 is in a zone without slots
      final var membersWithX1 = new LinkedHashMap<>(dualRegion());
      membersWithX1.put(X1, PASSIVE);
      final var configuration = configuration(7, membersWithX1);
      final var reports =
          List.of(
              report(A1, configuration, 18),
              report(A2, configuration, 18),
              report(X1, configuration, 20));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4), reports, DUAL_REGION, false);

      // then
      final var forced = (ForcedChange) decision;
      assertThat(types(forced.target()))
          .containsEntry(X1, ACTIVE)
          .containsEntry(A1, PASSIVE)
          .containsEntry(A2, PASSIVE);
    }

    @Test
    void shouldUsePolicyWithHighestVersionFromReports() {
      // given - A3 applied a newer policy that gives zone a three slots
      final var configuration = configuration(7, dualRegion());
      final var newer = VoterPolicy.of(new ZoneVoters("a", 3), new ZoneVoters("b", 2));
      final var reports =
          List.of(
              report(A1, configuration, 20),
              report(A2, configuration, 20),
              new MemberReport(A3, configuration, 0, 3, 20, 20, newer, 12),
              new MemberReport(A4, configuration, 0, 3, 20, 20, DUAL_REGION, 2));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4), reports, DUAL_REGION, false);

      // then
      final var forced = (ForcedChange) decision;
      assertThat(types(forced.target()))
          .containsEntry(A1, ACTIVE)
          .containsEntry(A2, ACTIVE)
          .containsEntry(A3, ACTIVE)
          .containsEntry(A4, PASSIVE);
    }

    @Test
    void shouldResendForcedChangeOfEarlierTry() {
      // given - an earlier try forced the configuration and fenced zone b
      final var forced =
          new Configuration(
              7,
              3,
              1,
              raftMembers(members(A1, ACTIVE, A2, ACTIVE, A3, PASSIVE, A4, PASSIVE), Map.of()),
              List.of(),
              true,
              fenced(B1, B2, B3, B4),
              4);
      final var old = configuration(6, dualRegion());

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4),
              List.of(report(A1, forced, 20), report(A3, old, 20)),
              DUAL_REGION,
              false);

      // then
      assertThat(decision).isEqualTo(new ResendForced(forced));
    }

    @Test
    void shouldRemoveBrokersThatForceVotersKeptAsFencedPassiveMembers() {
      // given - "force system partition voters" kept zone a and C1 as fenced passive members
      final var forced =
          new Configuration(
              9,
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
              4);

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(A1, A2, A3),
              List.of(report(B1, forced, 20), report(B2, forced, 20)),
              POLICY,
              false);

      // then - a normal change, not a resend: B1 and B2 are a quorum of {B1, B2}
      assertThat(decision).isEqualTo(new NormalChange(forced, Set.of(A1, A2, A3)));
    }

    @Test
    void shouldBeDoneIfBrokersAreNotMembers() {
      // given
      final var configuration =
          configuration(9, members(C1, ACTIVE, B1, ACTIVE, B2, ACTIVE, B3, PASSIVE));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(A1, A2, A3), List.of(report(B1, configuration, 20)), POLICY, false);

      // then
      assertThat(decision).isEqualTo(new AlreadyDone(configuration));
    }

    @Test
    void shouldFenceRemovedMembersWithTheirIncarnation() {
      // given - B1 runs with incarnation 5
      final var configuration =
          new Configuration(
              7, 3, 1, raftMembers(dualRegion(), Map.of(B1, 5L)), List.of(), false, List.of(), 4);
      final var reports = List.of(report(A1, configuration, 20), report(A2, configuration, 20));

      // when
      final var decision =
          SystemPartitionRecovery.removeMembers(
              Set.of(B1, B2, B3, B4), reports, DUAL_REGION, false);

      // then
      assertThat(((ForcedChange) decision).fenced())
          .contains(new FenceEntry(B1, 5))
          .doesNotContain(new FenceEntry(B1, 0));
    }
  }

  @Nested
  final class ForceVoters {

    @Test
    void shouldForceSurvivingVotersAndKeepDownBrokersAsPassiveWhenLossIsAccepted() {
      // given - 2.5 regions, zone a and the control zone are down (5.10.2)
      final var configuration = configuration(7, twoAndAHalfRegions());
      final var reports =
          List.of(
              report(B1, configuration, 20),
              report(B2, configuration, 20),
              report(B3, configuration, 20));

      // when
      final var decision =
          SystemPartitionRecovery.forceVoters(Set.of(A1, A2, A3, C1), reports, POLICY, true);

      // then - the policy limits the voters of zone b to B1 and B2
      final var forced = (ForcedChange) decision;
      assertThat(forced.completeness().complete()).isFalse();
      assertThat(types(forced.target()))
          .containsExactlyInAnyOrderEntriesOf(
              members(
                  B1, ACTIVE, B2, ACTIVE, A1, PASSIVE, A2, PASSIVE, A3, PASSIVE, B3, PASSIVE, C1,
                  PASSIVE));
      assertThat(forced.fenced()).containsExactlyInAnyOrderElementsOf(fenced(A1, A2, A3, C1));
    }

    @Test
    void shouldRejectWithoutAcceptPossibleLoss() {
      // given
      final var configuration = configuration(7, twoAndAHalfRegions());
      final var reports = List.of(report(B1, configuration, 20), report(B2, configuration, 20));

      // when
      final var decision =
          SystemPartitionRecovery.forceVoters(Set.of(A1, A2, A3, C1), reports, POLICY, false);

      // then - B1 and B2 are not a blocking set of 5 voters (3 necessary)
      assertThat(decision).isInstanceOf(Rejected.class);
      assertThat(((Rejected) decision).reason())
          .isEqualTo(RejectionReason.METADATA_COMPLETENESS_UNKNOWN);
    }

    @Test
    void shouldRejectIfVoterDidNotAnswerAndIsNotDown() {
      // given - B2 did not answer, but the operator did not name it
      final var configuration = configuration(7, twoAndAHalfRegions());
      final var reports = List.of(report(B1, configuration, 20));

      // when
      final var decision =
          SystemPartitionRecovery.forceVoters(Set.of(A1, A2, A3, C1), reports, POLICY, true);

      // then
      assertThat(decision).isInstanceOf(Rejected.class);
      assertThat(((Rejected) decision).reason()).isEqualTo(RejectionReason.VOTER_DID_NOT_ANSWER);
      assertThat(((Rejected) decision).message()).contains(B2.id());
    }

    @Test
    void shouldResendForcedChangeOfEarlierTry() {
      // given
      final var forced =
          new Configuration(
              7,
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
              4);

      // when
      final var decision =
          SystemPartitionRecovery.forceVoters(
              Set.of(A1, A2, A3, C1),
              List.of(report(B1, forced, 20), report(B2, forced, 20)),
              POLICY,
              true);

      // then
      assertThat(decision).isEqualTo(new ResendForced(forced));
    }

    @Test
    void shouldBeDoneOnceDownBrokersAreFencedAndPassive() {
      // given - a leader ended the forced configuration
      final var current =
          new Configuration(
              8,
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
              4);

      // when
      final var decision =
          SystemPartitionRecovery.forceVoters(
              Set.of(A1, A2, A3, C1), List.of(report(B1, current, 20)), POLICY, true);

      // then
      assertThat(decision).isEqualTo(new AlreadyDone(current));
    }
  }

  @Nested
  final class Completeness {

    @Test
    void shouldNeedOnlyBlockingSetForCompleteness() {
      // given - 5 voters, quorum 3: any 3 voters are a blocking set
      final var configuration = configuration(7, twoAndAHalfRegions());

      // when
      final var three =
          SystemPartitionRecovery.completeness(
              configuration,
              List.of(
                  report(C1, configuration, 1),
                  report(B1, configuration, 1),
                  report(B2, configuration, 1)));
      final var two =
          SystemPartitionRecovery.completeness(
              configuration, List.of(report(B1, configuration, 1), report(B2, configuration, 1)));

      // then
      assertThat(three.complete()).isTrue();
      assertThat(two.complete()).isFalse();
      assertThat(two.missing()).extracting(MemberId::id).hasSize(3);
      assertThat(three.voterSets().getFirst()).hasSize(5);
    }
  }
}
