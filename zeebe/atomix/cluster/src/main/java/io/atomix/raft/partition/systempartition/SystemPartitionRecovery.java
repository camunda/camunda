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

/**
 * The decisions of the recovery operations of the system partition: phase 0 of a force-remove plan
 * (design 5.6.3) and "force system partition voters" (5.6.5). Pure functions of the reports that
 * the executor collected from the reachable members; the executor applies the decision with {@link
 * io.atomix.raft.RaftServer#reconfigure} or {@link
 * io.atomix.raft.RaftServer#forceConfigure(Configuration)}.
 *
 * <p>A forced configuration gets as voters only the members with the most recent log (highest last
 * term, then highest last index): they have every entry that any reachable member knows as
 * committed. Each zone gets at most its slots, lowest node index first. If none of them is in a
 * zone with slots, the one with the lowest node index becomes the only voter anyway; self-promotion
 * and the leader's demotion restore the policy later.
 */
@NullMarked
public final class SystemPartitionRecovery {

  private static final Comparator<MemberReport> MOST_RECENT_LOG =
      Comparator.comparing(MemberReport::lastLogPosition);

  private SystemPartitionRecovery() {}

  /**
   * Phase 0 of a force-remove plan (5.6.3): removes the given brokers from the system partition.
   * Resends a forced change of an earlier try, else uses a normal change if the voters that are not
   * removed are a quorum, else a forced change. The removed brokers go on the fence list.
   *
   * @param removed the brokers to remove
   * @param reports the reports of the reachable members that are not removed
   */
  public static RecoveryDecision removeMembers(
      final Set<MemberId> removed, final Collection<MemberReport> reports) {
    final var configuration = latestConfiguration(reports);
    final var fencedIds = fencedIds(configuration);
    final var removedMembers =
        configuration.allMembers().stream()
            .filter(member -> removed.contains(member.memberId()))
            .toList();

    if (configuration.force() && fencedIds.containsAll(removed)) {
      return new RecoveryDecision.ResendForced(configuration);
    }
    if (removedMembers.isEmpty()) {
      return new RecoveryDecision.AlreadyDone(configuration);
    }

    final var fence = fenceEntries(removedMembers);
    if (hasQuorumWithout(configuration.newMembers(), removed)
        && (!configuration.requiresJointConsensus()
            || hasQuorumWithout(configuration.oldMembers(), removed))) {
      final var target = typesOf(configuration.newMembers());
      removed.forEach(target::remove);
      return new RecoveryDecision.NormalChange(
          target, fence, configuration.index(), configuration.term());
    }

    return new RecoveryDecision.ForcedChange(
        forcedTarget(configuration, forcedMembers(configuration, reports, removed, true), fence));
  }

  /**
   * "Force system partition voters" (5.6.5): the members with the most recent log that are not down
   * become the only voters; all other members, the down brokers included, stay members as passive
   * members. The down brokers go on the fence list. Resends a forced change of an earlier try.
   *
   * @param down the brokers that the operator verified to be down
   * @param reports the reports of the reachable members that are not down
   */
  public static RecoveryDecision forceVoters(
      final Set<MemberId> down, final Collection<MemberReport> reports) {
    final var configuration = latestConfiguration(reports);
    final var fencedIds = fencedIds(configuration);
    final var downMembers =
        configuration.allMembers().stream()
            .filter(member -> down.contains(member.memberId()))
            .toList();

    if (configuration.force() && fencedIds.containsAll(down)) {
      return new RecoveryDecision.ResendForced(configuration);
    }
    if (downMembers.stream().allMatch(configuration::isFenced)
        && downMembers.stream().noneMatch(member -> member.getType() == Type.ACTIVE)) {
      return new RecoveryDecision.AlreadyDone(configuration);
    }

    return new RecoveryDecision.ForcedChange(
        forcedTarget(
            configuration,
            forcedMembers(configuration, reports, down, false),
            fenceEntries(downMembers)));
  }

  /**
   * The full target of a forced change, built from the chosen (highest) configuration and not from
   * the executor's own one, which can lag: the members keep the incarnations of the chosen
   * configuration, the fence list is the chosen one plus the new entries, and the voter policy is
   * the chosen one.
   */
  private static Configuration forcedTarget(
      final Configuration chosen,
      final Map<MemberId, Type> types,
      final Collection<FenceEntry> newFenceEntries) {
    final var incarnations =
        chosen.allMembers().stream()
            .collect(
                Collectors.toMap(
                    RaftMember::memberId, RaftMember::getIncarnation, (first, second) -> first));
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
        chosen.voterPolicy());
  }

  private static Map<MemberId, Type> forcedMembers(
      final Configuration configuration,
      final Collection<MemberReport> reports,
      final Set<MemberId> failed,
      final boolean removeFailed) {
    final var memberIds =
        configuration.newMembers().stream().map(RaftMember::memberId).collect(Collectors.toSet());
    final var candidates =
        reports.stream()
            .filter(report -> memberIds.contains(report.memberId()))
            .filter(report -> !failed.contains(report.memberId()))
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

    final var policy = configuration.voterPolicy();
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

    final var members = new LinkedHashMap<MemberId, Type>();
    for (final var member : sorted(configuration.newMembers())) {
      if (removeFailed && failed.contains(member.memberId())) {
        continue;
      }
      members.put(
          member.memberId(), voters.contains(member.memberId()) ? Type.ACTIVE : Type.PASSIVE);
    }
    return members;
  }

  private static boolean hasQuorumWithout(
      final Collection<RaftMember> members, final Set<MemberId> removed) {
    final var voters = members.stream().filter(member -> member.getType() == Type.ACTIVE).toList();
    final long remaining =
        voters.stream().filter(member -> !removed.contains(member.memberId())).count();
    return remaining > voters.size() / 2;
  }

  private static List<FenceEntry> fenceEntries(final Collection<RaftMember> members) {
    return members.stream()
        .map(member -> new FenceEntry(member.memberId(), member.getIncarnation()))
        .distinct()
        .sorted(Comparator.comparing(FenceEntry::memberId, MemberId.ID_COMPARATOR))
        .toList();
  }

  private static Set<MemberId> fencedIds(final Configuration configuration) {
    return configuration.fenced().stream().map(FenceEntry::memberId).collect(Collectors.toSet());
  }

  /** Step 1 of 5.6.3: the configuration with the highest index, then term. */
  private static Configuration latestConfiguration(final Collection<MemberReport> reports) {
    return reports.stream()
        .map(MemberReport::configuration)
        .max(Comparator.comparingLong(Configuration::index).thenComparingLong(Configuration::term))
        .orElseThrow(
            () -> new IllegalArgumentException("Expected at least one report, but got none"));
  }

  /**
   * What a reachable member reported in step 1 of 5.6.3.
   *
   * @param memberId the member
   * @param configuration its current configuration, with the term that {@link
   *     io.atomix.raft.RaftServer#reconfigure} expects (see {@link
   *     io.atomix.raft.RaftServer#getConfiguration()})
   * @param lastLogTerm the term of its last log entry
   * @param lastLogIndex the index of its last log entry
   * @param commitIndex its commit index
   */
  public record MemberReport(
      MemberId memberId,
      Configuration configuration,
      long lastLogTerm,
      long lastLogIndex,
      long commitIndex) {

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
     * A normal (joint) change with {@link io.atomix.raft.RaftServer#reconfigure(Map, Collection,
     * io.atomix.raft.cluster.VoterPolicy, long, long)} at the given index and term.
     */
    record NormalChange(Map<MemberId, Type> members, List<FenceEntry> fenced, long index, long term)
        implements RecoveryDecision {}

    /**
     * A forced change with {@link io.atomix.raft.RaftServer#forceConfigure(Configuration)}: the
     * remaining voters are not a quorum.
     *
     * @param target the full target configuration, see {@link #forcedTarget}
     */
    record ForcedChange(Configuration target) implements RecoveryDecision {
      public Map<MemberId, Type> members() {
        return typesOf(target.newMembers());
      }

      public Collection<FenceEntry> fenced() {
        return target.fenced();
      }
    }
  }
}
