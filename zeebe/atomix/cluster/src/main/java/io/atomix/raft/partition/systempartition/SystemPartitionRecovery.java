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

import static io.atomix.raft.partition.systempartition.SystemPartitionMembership.slotsOf;
import static io.atomix.raft.partition.systempartition.SystemPartitionMembership.sorted;
import static io.atomix.raft.partition.systempartition.SystemPartitionMembership.typesOf;
import static io.atomix.raft.partition.systempartition.SystemPartitionMembership.zoneOf;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.system.Configuration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The decisions of the recovery operations of the system partition: phase 0 of a force-remove plan
 * (design 5.6.3) and "force system partition voters" (5.6.5). Pure functions of the reports that
 * the executor collected from the reachable members (step 1); the executor waits for the leader
 * (normal change) or applies the decision with {@link
 * io.atomix.raft.RaftServer#forceConfigure(Configuration)}.
 *
 * <p><b>Step 1.</b> {@code K} is the configuration with the highest index, then term. {@code S(K)}
 * are its voter sets: one, or the old and the new set if {@code K} is joint. {@code R} are the
 * members that answered with the incarnation that {@code K} has for them in a set; a member that
 * answers with another incarnation lost its log.
 *
 * <p><b>Completeness (D20).</b> A forced change keeps each committed entry if {@code R} is a
 * blocking set of each set of {@code S(K)}: it has at least {@code n - q + 1} voters of the set.
 * Else the operator must accept a possible loss.
 *
 * <p>A forced configuration gets as voters only the members with the most recent log (highest last
 * term, then highest last index). Each zone gets at most its slots, lowest node index first. If
 * none of them is in a zone with slots, the one with the lowest node index becomes the only voter
 * anyway; the leader restores the policy later.
 */
@NullMarked
public final class SystemPartitionRecovery {

  private static final Comparator<MemberReport> MOST_RECENT_LOG =
      Comparator.comparing(MemberReport::lastLogPosition);

  private SystemPartitionRecovery() {}

  /**
   * Phase 0 of a force-remove plan (5.6.3).
   *
   * @param removed the brokers to remove
   * @param reports the reports of the reachable members that are not removed
   * @param staticPolicy the policy to use if no member reports one
   * @param acceptPossibleLoss whether the operator accepts that a forced change loses entries
   */
  public static RecoveryDecision removeMembers(
      final Set<MemberId> removed,
      final Collection<MemberReport> reports,
      final VoterPolicy staticPolicy,
      final boolean acceptPossibleLoss) {
    final var configuration = latestConfiguration(reports);
    final var removedMembers = membersOfAnySet(configuration, removed);

    // Step 2: a forced configuration that already removed and fenced the brokers is an earlier try
    // of this operation. One that still has them, e.g. of "force system partition voters", is not.
    if (configuration.force()
        && removedMembers.isEmpty()
        && removed.stream()
            .allMatch(member -> isFencedAsReported(member, configuration, reports))) {
      return new RecoveryDecision.ResendForced(configuration);
    }
    if (removedMembers.isEmpty()) {
      return new RecoveryDecision.AlreadyDone(configuration);
    }

    // Step 3: a normal change if the voters that are not removed are a quorum of each set.
    final var voterSets = voterSets(configuration);
    if (voterSets.stream().allMatch(set -> hasQuorumWithout(set, removed))) {
      return new RecoveryDecision.NormalChange(configuration, Set.copyOf(removed));
    }

    // Item 0: completeness check.
    final var completeness = completeness(configuration, reports);
    if (!completeness.complete() && !acceptPossibleLoss) {
      return new RecoveryDecision.Rejected(
          RejectionReason.METADATA_COMPLETENESS_UNKNOWN,
          completeness,
          "The members that answered %s are not a blocking set of each voter set %s; the voters %s did not answer. A forced change can lose committed entries; bring back a voter with its disk or accept the loss."
              .formatted(
                  completeness.answered(), completeness.voterSets(), completeness.missing()));
    }

    // Items 1 to 4: the voters with the most recent log, all others passive; the removed brokers
    // leave.
    final var policy = latestPolicy(reports, staticPolicy);
    final var voters = forcedVoters(configuration, reports, removed, policy);
    final var members = new LinkedHashMap<MemberId, Type>();
    for (final var member : sorted(configuration.newMembers())) {
      if (!removed.contains(member.memberId())) {
        members.put(
            member.memberId(), voters.contains(member.memberId()) ? Type.ACTIVE : Type.PASSIVE);
      }
    }

    // Items 5 and 6: fence the removed brokers, and each voter of each set that is not removed
    // but is not a voter of the forced configuration.
    final var fence = new LinkedHashSet<FenceEntry>(fenceEntries(removedMembers));
    for (final var set : voterSets) {
      for (final var voter : set) {
        if (!removed.contains(voter.memberId()) && !voters.contains(voter.memberId())) {
          fence.add(new FenceEntry(voter.memberId(), voter.getIncarnation()));
        }
      }
    }
    return new RecoveryDecision.ForcedChange(
        forcedTarget(configuration, members, fence), completeness);
  }

  /**
   * "Force system partition voters" (5.6.5): the members with the most recent log that are not down
   * become the only voters; all other members, the down brokers included, stay members as passive
   * members. The down brokers go on the fence list.
   *
   * @param down the brokers that the operator verified to be down
   * @param reports the reports of the reachable members that are not down
   * @param staticPolicy the policy to use if no member reports one
   * @param acceptPossibleLoss whether the operator accepts that the change loses entries
   */
  public static RecoveryDecision forceVoters(
      final Set<MemberId> down,
      final Collection<MemberReport> reports,
      final VoterPolicy staticPolicy,
      final boolean acceptPossibleLoss) {
    final var configuration = latestConfiguration(reports);
    final var downMembers = membersOfAnySet(configuration, down);

    if (!downMembers.isEmpty()
        && downMembers.stream().allMatch(configuration::isFenced)
        && downMembers.stream().noneMatch(member -> member.getType() == Type.ACTIVE)) {
      // A forced configuration that already made the change is an earlier try; it is sent again
      // until a leader ends it.
      return configuration.force()
          ? new RecoveryDecision.ResendForced(configuration)
          : new RecoveryDecision.AlreadyDone(configuration);
    }

    final var completeness = completeness(configuration, reports);
    final var notAnswered =
        completeness.missing().stream().filter(member -> !down.contains(member)).toList();
    if (!notAnswered.isEmpty()) {
      return new RecoveryDecision.Rejected(
          RejectionReason.VOTER_DID_NOT_ANSWER,
          completeness,
          "The voters %s did not answer and are not in the down brokers %s"
              .formatted(notAnswered, down));
    }
    if (!completeness.complete() && !acceptPossibleLoss) {
      return new RecoveryDecision.Rejected(
          RejectionReason.METADATA_COMPLETENESS_UNKNOWN,
          completeness,
          "The members that answered %s are not a blocking set of each voter set %s; the voters %s did not answer."
              .formatted(
                  completeness.answered(), completeness.voterSets(), completeness.missing()));
    }

    final var policy = latestPolicy(reports, staticPolicy);
    final var voters = forcedVoters(configuration, reports, down, policy);
    final var members = new LinkedHashMap<MemberId, Type>();
    for (final var member : sorted(configuration.newMembers())) {
      members.put(
          member.memberId(), voters.contains(member.memberId()) ? Type.ACTIVE : Type.PASSIVE);
    }
    return new RecoveryDecision.ForcedChange(
        forcedTarget(configuration, members, fenceEntries(downMembers)), completeness);
  }

  /**
   * The completeness check of 5.6.3, item 0 (D20): {@code R} must be a blocking set of each voter
   * set of {@code K}.
   */
  public static Completeness completeness(
      final Configuration configuration, final Collection<MemberReport> reports) {
    final var sets = voterSets(configuration);
    final var answered = new LinkedHashSet<MemberId>();
    final var missing = new LinkedHashSet<MemberId>();
    boolean complete = true;
    for (final var set : sets) {
      int inR = 0;
      for (final var voter : set) {
        if (answeredWithIncarnation(reports, voter)) {
          inR++;
          answered.add(voter.memberId());
        } else {
          missing.add(voter.memberId());
        }
      }
      final int n = set.size();
      final int quorum = n / 2 + 1;
      if (inR < n - quorum + 1) {
        complete = false;
      }
    }
    return new Completeness(
        complete,
        sets.stream()
            .map(
                set ->
                    set.stream().map(RaftMember::memberId).sorted(MemberId.ID_COMPARATOR).toList())
            .toList(),
        List.copyOf(answered),
        List.copyOf(missing));
  }

  /**
   * The full target of a forced change, built from {@code K} and not from the executor's own
   * configuration, which can lag: the members keep the incarnations of {@code K}, the fence list is
   * the one of {@code K} plus the new entries, and the membership version is the one of {@code K}.
   */
  private static Configuration forcedTarget(
      final Configuration chosen,
      final Map<MemberId, Type> types,
      final Collection<FenceEntry> newFenceEntries) {
    final var incarnations = new HashMap<MemberId, Long>();
    chosen.oldMembers().forEach(m -> incarnations.put(m.memberId(), m.getIncarnation()));
    chosen.newMembers().forEach(m -> incarnations.put(m.memberId(), m.getIncarnation()));
    final var now = Instant.now();
    final List<RaftMember> members =
        types.entrySet().stream()
            .map(
                entry ->
                    (RaftMember)
                        new DefaultRaftMember(
                            entry.getKey(),
                            entry.getValue(),
                            now,
                            incarnations.getOrDefault(entry.getKey(), 0L)))
            .toList();
    final var fenced = new LinkedHashSet<>(chosen.fenced());
    fenced.addAll(newFenceEntries);
    return new Configuration(
        chosen.index(),
        chosen.term(),
        chosen.time(),
        members,
        List.of(),
        true,
        List.copyOf(fenced),
        chosen.membershipVersion());
  }

  /** Items 1 to 3 of 5.6.3: the voters of a forced configuration (D11). */
  private static Set<MemberId> forcedVoters(
      final Configuration configuration,
      final Collection<MemberReport> reports,
      final Set<MemberId> failed,
      final VoterPolicy policy) {
    final var candidates =
        reports.stream()
            .filter(report -> !failed.contains(report.memberId()))
            .filter(
                report ->
                    configuration.newMembers().stream()
                        .anyMatch(
                            member ->
                                member.memberId().equals(report.memberId())
                                    && member.getIncarnation() == report.incarnation()))
            .toList();
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected a report of at least one member of %s that is not one of %s, but got %s"
              .formatted(configuration.newMembers(), failed, reports));
    }
    final var mostRecent =
        candidates.stream().max(MOST_RECENT_LOG).map(MemberReport::lastLogPosition).orElseThrow();
    final var withMostRecentLog =
        candidates.stream()
            .filter(report -> report.lastLogPosition().equals(mostRecent))
            .map(MemberReport::memberId)
            .sorted(MemberId.ID_COMPARATOR)
            .toList();

    final var votersPerZone = new HashMap<String, Integer>();
    final var voters = new HashSet<MemberId>();
    for (final var member : withMostRecentLog) {
      final var zone = zoneOf(member);
      final int zoneVoters = votersPerZone.getOrDefault(zone, 0);
      if (zoneVoters < slotsOf(policy, zone)) {
        votersPerZone.put(zone, zoneVoters + 1);
        voters.add(member);
      }
    }
    if (voters.isEmpty()) {
      voters.add(withMostRecentLog.getFirst());
    }
    return voters;
  }

  static List<List<RaftMember>> voterSets(final Configuration configuration) {
    final var sets = new ArrayList<List<RaftMember>>();
    sets.add(voters(configuration.newMembers()));
    if (configuration.requiresJointConsensus()) {
      sets.add(voters(configuration.oldMembers()));
    }
    return sets;
  }

  private static List<RaftMember> voters(final Collection<RaftMember> members) {
    return sorted(members).stream().filter(member -> member.getType() == Type.ACTIVE).toList();
  }

  private static boolean answeredWithIncarnation(
      final Collection<MemberReport> reports, final RaftMember voter) {
    return reports.stream()
        .anyMatch(
            report ->
                report.memberId().equals(voter.memberId())
                    && report.incarnation() == voter.getIncarnation());
  }

  private static boolean hasQuorumWithout(
      final List<RaftMember> voters, final Set<MemberId> removed) {
    final long remaining =
        voters.stream().filter(member -> !removed.contains(member.memberId())).count();
    return remaining > voters.size() / 2;
  }

  private static List<RaftMember> membersOfAnySet(
      final Configuration configuration, final Set<MemberId> ids) {
    final var members = new ArrayList<RaftMember>();
    configuration.newMembers().stream()
        .filter(member -> ids.contains(member.memberId()))
        .forEach(members::add);
    configuration.oldMembers().stream()
        .filter(member -> ids.contains(member.memberId()))
        .filter(
            member ->
                members.stream()
                    .noneMatch(
                        m ->
                            m.memberId().equals(member.memberId())
                                && m.getIncarnation() == member.getIncarnation()))
        .forEach(members::add);
    return members;
  }

  private static List<FenceEntry> fenceEntries(final Collection<RaftMember> members) {
    return members.stream()
        .map(member -> new FenceEntry(member.memberId(), member.getIncarnation()))
        .distinct()
        .sorted(Comparator.comparing(FenceEntry::memberId, MemberId.ID_COMPARATOR))
        .toList();
  }

  /**
   * @return true if the configuration fences the member with each incarnation that a report knows
   *     for it, or with any incarnation if no report knows the member
   */
  private static boolean isFencedAsReported(
      final MemberId member,
      final Configuration configuration,
      final Collection<MemberReport> reports) {
    final var knownIncarnations =
        reports.stream()
            .map(MemberReport::configuration)
            .filter(java.util.Objects::nonNull)
            .flatMap(
                c ->
                    java.util.stream.Stream.concat(
                        c.newMembers().stream(), c.oldMembers().stream()))
            .filter(m -> m.memberId().equals(member))
            .map(RaftMember::getIncarnation)
            .collect(Collectors.toSet());
    if (knownIncarnations.isEmpty()) {
      return configuration.fenced().stream().anyMatch(f -> f.memberId().equals(member));
    }
    return knownIncarnations.stream()
        .allMatch(incarnation -> configuration.isFenced(member, incarnation));
  }

  /** Step 1 of 5.6.3: the configuration with the highest index, then term. */
  private static Configuration latestConfiguration(final Collection<MemberReport> reports) {
    return reports.stream()
        .map(MemberReport::configuration)
        .filter(java.util.Objects::nonNull)
        .max(Comparator.comparingLong(Configuration::index).thenComparingLong(Configuration::term))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected at least one report with a configuration, but got " + reports));
  }

  /** Step 1 of 5.6.3: the policy with the highest version, else the static policy. */
  private static VoterPolicy latestPolicy(
      final Collection<MemberReport> reports, final VoterPolicy staticPolicy) {
    return reports.stream()
        .filter(report -> report.policy() != null)
        .max(Comparator.comparingLong(MemberReport::policyVersion))
        .map(MemberReport::policy)
        .orElse(staticPolicy);
  }

  /**
   * What a reachable member reported in step 1 of 5.6.3.
   *
   * @param memberId the member
   * @param configuration its current configuration with the configuration term, or null if it has
   *     none (it started without data and was not added yet)
   * @param incarnation its current incarnation
   * @param lastLogTerm the term of its last log entry
   * @param lastLogIndex the index of its last log entry
   * @param commitIndex its commit index
   * @param policy the voter policy that it applied (or its cache), or null if it has none
   * @param policyVersion the version of the policy
   */
  public record MemberReport(
      MemberId memberId,
      @Nullable Configuration configuration,
      long incarnation,
      long lastLogTerm,
      long lastLogIndex,
      long commitIndex,
      @Nullable VoterPolicy policy,
      long policyVersion) {

    LogPosition lastLogPosition() {
      return new LogPosition(lastLogTerm, lastLogIndex);
    }
  }

  record LogPosition(long term, long index) implements Comparable<LogPosition> {
    @Override
    public int compareTo(final LogPosition other) {
      return Comparator.comparingLong(LogPosition::term)
          .thenComparingLong(LogPosition::index)
          .compare(this, other);
    }
  }

  /**
   * The result of the completeness check.
   *
   * @param complete true if the members that answered are a blocking set of each voter set
   * @param voterSets the voter sets of {@code K}
   * @param answered the voters that answered with their incarnation of {@code K}
   * @param missing the voters that did not
   */
  public record Completeness(
      boolean complete,
      List<List<MemberId>> voterSets,
      List<MemberId> answered,
      List<MemberId> missing) {}

  public enum RejectionReason {
    /** The check of 5.6.3, item 0 failed and the operator did not accept a possible loss. */
    METADATA_COMPLETENESS_UNKNOWN,
    /** "Force system partition voters": a voter did not answer and is not a down broker. */
    VOTER_DID_NOT_ANSWER
  }

  /** The decision of a recovery operation. */
  public sealed interface RecoveryDecision {

    /** Nothing to do: the brokers are already removed, or already fenced and passive. */
    record AlreadyDone(Configuration configuration) implements RecoveryDecision {}

    /**
     * An earlier try already made a forced change; send it again, unchanged, with {@link
     * io.atomix.raft.RaftServer#forceConfigure(Configuration)}.
     *
     * @param target the forced configuration of the earlier try
     */
    record ResendForced(Configuration target) implements RecoveryDecision {
      public Map<MemberId, Type> members() {
        return typesOf(target.newMembers());
      }
    }

    /**
     * The remaining voters are a quorum of each set: the leader removes the brokers, because they
     * are the targets of pending {@code MemberRemove} operations (5.5, rule 1). The executor waits
     * until they are not in the leader's committed, non-joint configuration.
     */
    record NormalChange(Configuration configuration, Set<MemberId> removed)
        implements RecoveryDecision {}

    /**
     * A forced change with {@link io.atomix.raft.RaftServer#forceConfigure(Configuration)}.
     *
     * @param target the full target configuration, see {@link #forcedTarget}
     * @param completeness the result of the completeness check, for the recovery record
     */
    record ForcedChange(Configuration target, Completeness completeness)
        implements RecoveryDecision {
      public Map<MemberId, Type> members() {
        return typesOf(target.newMembers());
      }

      public Collection<FenceEntry> fenced() {
        return target.fenced();
      }
    }

    /** The operation must fail; the operator must act. */
    record Rejected(RejectionReason reason, Completeness completeness, String message)
        implements RecoveryDecision {}
  }
}
