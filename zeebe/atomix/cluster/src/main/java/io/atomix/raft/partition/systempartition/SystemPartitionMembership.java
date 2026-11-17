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

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.VoterPolicy;
import io.atomix.raft.cluster.VoterPolicy.ZoneVoters;
import io.atomix.raft.storage.system.Configuration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The rules that keep the members of the system partition in line with its voter policy (design:
 * {@code docs/system-partition/system-partition-reconfiguration.md}, sections 5.2 to 5.6). Pure
 * functions of a Raft {@link Configuration} and of what the caller knows about the cluster; the
 * caller sends the result with {@link io.atomix.raft.RaftServer#reconfigure} at the index and term
 * of the configuration that it used.
 *
 * <p><b>Control zone.</b> The control zone is the <em>first zone of the voter policy</em>. The
 * policy is stored in the Raft configuration, thus all brokers agree on it without reading the
 * dynamic configuration, and its zone order is the election priority order. Whoever creates a
 * policy (the bootstrap from the static configuration, or "update system partition voter policy")
 * must put the zone with the control role first; {@link #policy(ZoneVoters, List)} does that. A
 * policy without a control zone (dual-region) just starts with the data zone of the highest
 * priority. Thus "control-zone voters first, then the zone order of the policy" (5.3) is the zone
 * order of the policy, and the voter policy format needs no extra field.
 *
 * <p><b>Zones and order.</b> The zone of a member is {@link MemberId#zone()}, or "" for a member
 * without zone, as in the leader's policy check. Members of a zone are ordered by {@link
 * MemberId#ID_COMPARATOR}, i.e. by node index.
 */
@NullMarked
public final class SystemPartitionMembership {

  private SystemPartitionMembership() {}

  /**
   * Creates a voter policy with the control zone first, followed by the data zones in the given
   * order (highest priority first).
   *
   * @param controlZone the control zone, or null if the cluster has none
   * @param dataZones the data zones, highest priority first
   */
  public static VoterPolicy policy(
      final @Nullable ZoneVoters controlZone, final List<ZoneVoters> dataZones) {
    final var zones = new ArrayList<ZoneVoters>();
    if (controlZone != null) {
      zones.add(controlZone);
    }
    zones.addAll(dataZones);
    return new VoterPolicy(zones);
  }

  /**
   * The initial configuration (5.4.1): in each zone, the members with the lowest node indexes fill
   * the voter slots; all other members are passive.
   */
  public static Map<MemberId, Type> initialConfiguration(
      final VoterPolicy policy, final Collection<MemberId> members) {
    final var result = new LinkedHashMap<MemberId, Type>();
    final var votersPerZone = new HashMap<String, Integer>();
    members.stream()
        .sorted(MemberId.ID_COMPARATOR)
        .forEach(
            member -> {
              final var zone = zoneOf(member);
              final int voters = votersPerZone.getOrDefault(zone, 0);
              if (voters < slotsOf(policy, zone)) {
                votersPerZone.put(zone, voters + 1);
                result.put(member, Type.ACTIVE);
              } else {
                result.put(member, Type.PASSIVE);
              }
            });
    return result;
  }

  /**
   * {@link #selectVoters(VoterPolicy, Configuration, Set)} with the policy of the configuration.
   */
  public static VoterSelection selectVoters(
      final Configuration configuration, final Set<MemberId> excluded) {
    return selectVoters(
        requireNonNull(configuration.voterPolicy(), "the configuration has no voter policy"),
        configuration,
        excluded);
  }

  /**
   * Selects the voters (5.2). Current voters are kept; if a zone has more voters than slots, the
   * voters with the highest node index are extra; empty slots stay empty, self-promotion fills them
   * later. Voters of a zone without slots are extra too. Liveness is not an input: it never changes
   * the voters.
   *
   * @param policy the voter policy
   * @param configuration a committed configuration that is not in joint consensus
   * @param excluded brokers that are not {@code JOINING} or {@code ACTIVE} in the dynamic
   *     configuration, and targets of pending removals: they are never candidates for a slot
   */
  public static VoterSelection selectVoters(
      final VoterPolicy policy, final Configuration configuration, final Set<MemberId> excluded) {
    requireNotJoint(configuration);
    final var members = sorted(configuration.newMembers());
    final var zones = new ArrayList<ZoneSelection>();
    final var target = new LinkedHashMap<MemberId, Type>();
    members.forEach(member -> target.put(member.memberId(), Type.PASSIVE));

    for (final var zoneVoters : policy.zones()) {
      final var zone = zoneVoters.zone();
      final var voters = voterIds(members, zone);
      final var kept = voters.subList(0, Math.min(voters.size(), zoneVoters.voters()));
      final var extra = voters.subList(kept.size(), voters.size());
      final var candidates =
          members.stream()
              .filter(member -> zoneOf(member.memberId()).equals(zone))
              .filter(member -> member.getType() == Type.PASSIVE)
              .filter(member -> !excluded.contains(member.memberId()))
              .filter(member -> !configuration.isFenced(member))
              .map(RaftMember::memberId)
              .toList();
      kept.forEach(voter -> target.put(voter, Type.ACTIVE));
      zones.add(
          new ZoneSelection(
              zone,
              zoneVoters.voters(),
              List.copyOf(kept),
              List.copyOf(extra),
              zoneVoters.voters() - kept.size(),
              candidates));
    }

    final var nonPolicyVoters =
        members.stream()
            .filter(member -> member.getType() == Type.ACTIVE)
            .map(RaftMember::memberId)
            .filter(member -> policy.votersOf(zoneOf(member)).isEmpty())
            .toList();
    return new VoterSelection(zones, nonPolicyVoters, target);
  }

  /**
   * Calculates the election priorities (5.3): voters are ordered by the zone order of the policy
   * (control zone first, see the class documentation), then by node index. The priority of a voter
   * is {@code policyTotal - position}, but at least 1, so that an extra voter (beyond the policy)
   * can still start an election. The target priority is {@code policyTotal}. A passive member has
   * priority 0. Without a policy, the total is the number of voters.
   */
  public static ElectionPriorities priorities(final Configuration configuration) {
    final var policy = configuration.voterPolicy();
    final var voters =
        configuration.newMembers().stream()
            .filter(member -> member.getType() == Type.ACTIVE)
            .map(RaftMember::memberId)
            .sorted(
                Comparator.comparingInt((MemberId member) -> zoneRank(policy, member))
                    .thenComparing(MemberId.ID_COMPARATOR))
            .toList();
    final int total =
        policy == null ? voters.size() : policy.zones().stream().mapToInt(ZoneVoters::voters).sum();
    final var priorities = new LinkedHashMap<MemberId, Integer>();
    for (int position = 0; position < voters.size(); position++) {
      priorities.put(voters.get(position), Math.max(1, total - position));
    }
    return new ElectionPriorities(Math.max(1, total), priorities);
  }

  /**
   * Self-promotion (5.6.4): a passive broker promotes itself if its zone has an empty slot in the
   * committed configuration, it is not excluded and not fenced, it has no empty-start marker, and
   * it has caught up with the leader.
   *
   * @param committed the committed configuration of the broker
   * @param check what the broker knows about itself
   * @return the target configuration that promotes the broker, or empty if it must not promote
   *     itself
   */
  public static Optional<Map<MemberId, Type>> selfPromotion(
      final Configuration committed, final SelfPromotionCheck check) {
    final var policy = committed.voterPolicy();
    if (policy == null
        || committed.requiresJointConsensus()
        || !check.dynamicConfigurationInitialized()
        || check.excluded()
        || check.emptyStartMarker()
        || check.localCommitIndex() < check.leaderCommitIndex()
        || committed.isFenced(check.self(), check.incarnation())) {
      return Optional.empty();
    }
    final var self =
        committed.newMembers().stream()
            .filter(member -> member.memberId().equals(check.self()))
            .findAny();
    if (self.isEmpty()
        || self.get().getType() != Type.PASSIVE
        || self.get().getIncarnation() != check.incarnation()) {
      return Optional.empty();
    }
    final var zone = zoneOf(check.self());
    if (voterIds(sorted(committed.newMembers()), zone).size() >= slotsOf(policy, zone)) {
      return Optional.empty();
    }
    final var target = typesOf(committed.newMembers());
    target.put(check.self(), Type.ACTIVE);
    return Optional.of(target);
  }

  /**
   * The position of a broker among the brokers of its zone that may fill an empty slot (passive,
   * not excluded, not fenced), lowest node index first. A broker waits {@code rank * delay} before
   * it sends its self-promotion, so that the broker with the lowest node index usually wins; the
   * exact index check makes sure that only one of them fills a slot.
   *
   * @return the rank, or empty if the broker is not a candidate
   */
  public static Optional<Integer> selfPromotionRank(
      final Configuration committed, final MemberId self, final Set<MemberId> excluded) {
    final var policy = committed.voterPolicy();
    if (policy == null) {
      return Optional.empty();
    }
    final var candidates =
        selectVoters(policy, committed, excluded).zones().stream()
            .filter(zone -> zone.zone().equals(zoneOf(self)))
            .flatMap(zone -> zone.candidates().stream())
            .toList();
    final int rank = candidates.indexOf(self);
    return rank < 0 ? Optional.empty() : Optional.of(rank);
  }

  /**
   * The leader's demotion of extra voters (5.6.4): one voter in each change. First the voter with
   * the highest node index of a zone that has more voters than slots. Then a voter of a zone
   * without slots, but only once a zone with slots has a voter, so that the configuration keeps a
   * voter that the policy wants.
   *
   * @param committed the committed configuration of the leader
   * @return the target configuration that demotes one extra voter, or empty if there is none
   */
  public static Optional<Map<MemberId, Type>> demotion(final Configuration committed) {
    final var policy = committed.voterPolicy();
    if (policy == null || committed.requiresJointConsensus()) {
      return Optional.empty();
    }
    final var members = sorted(committed.newMembers());
    MemberId demoted = null;
    int policyVoters = 0;
    for (final var zoneVoters : policy.zones()) {
      final var voters = voterIds(members, zoneVoters.zone());
      if (zoneVoters.voters() > 0) {
        policyVoters += Math.min(voters.size(), zoneVoters.voters());
        if (demoted == null && voters.size() > zoneVoters.voters()) {
          demoted = voters.getLast();
        }
      }
    }
    if (demoted == null && policyVoters > 0) {
      demoted =
          members.stream()
              .filter(member -> member.getType() == Type.ACTIVE)
              .map(RaftMember::memberId)
              .filter(member -> slotsOf(policy, zoneOf(member)) == 0)
              .reduce((first, second) -> second)
              .orElse(null);
    }
    if (demoted == null) {
      return Optional.empty();
    }
    final var target = typesOf(committed.newMembers());
    target.put(demoted, Type.PASSIVE);
    return Optional.of(target);
  }

  /**
   * The change of {@code MemberLeave} (5.6.2): a passive member is removed; a voter is removed and
   * replaced in the same joint change by a passive member of its zone, if one is available. The
   * leaving broker is fenced with its incarnation.
   *
   * @param current the current configuration (from the leader)
   * @param leaving the leaving broker
   * @param excluded brokers that must not become voters (5.2)
   * @param replacementAvailable whether a passive member has caught up and answered recently, as
   *     far as the caller knows; the leader checks it again
   * @return the change, or empty if the broker is not a member any more
   */
  public static Optional<LeaveChange> leaveChange(
      final Configuration current,
      final MemberId leaving,
      final Set<MemberId> excluded,
      final Predicate<MemberId> replacementAvailable) {
    final var member =
        current.newMembers().stream().filter(m -> m.memberId().equals(leaving)).findAny();
    if (member.isEmpty()) {
      return Optional.empty();
    }
    final var target = typesOf(current.newMembers());
    target.remove(leaving);
    final var fence = new FenceEntry(leaving, member.get().getIncarnation());
    if (member.get().getType() != Type.ACTIVE) {
      return Optional.of(new LeaveChange(target, fence, null));
    }
    final var replacement =
        sorted(current.newMembers()).stream()
            .filter(m -> m.getType() == Type.PASSIVE)
            .filter(m -> zoneOf(m.memberId()).equals(zoneOf(leaving)))
            .filter(m -> !excluded.contains(m.memberId()))
            .filter(m -> !current.isFenced(m))
            .map(RaftMember::memberId)
            .filter(replacementAvailable)
            .findFirst()
            .orElse(null);
    if (replacement != null) {
      target.put(replacement, Type.ACTIVE);
    }
    return Optional.of(new LeaveChange(target, fence, replacement));
  }

  static String zoneOf(final MemberId member) {
    return Objects.toString(member.zone(), "");
  }

  static int slotsOf(final @Nullable VoterPolicy policy, final String zone) {
    return policy == null ? Integer.MAX_VALUE : policy.votersOf(zone).orElse(0);
  }

  static Map<MemberId, Type> typesOf(final Collection<RaftMember> members) {
    final var types = new LinkedHashMap<MemberId, Type>();
    sorted(members).forEach(member -> types.put(member.memberId(), member.getType()));
    return types;
  }

  static List<RaftMember> sorted(final Collection<RaftMember> members) {
    return members.stream()
        .sorted(Comparator.comparing(RaftMember::memberId, MemberId.ID_COMPARATOR))
        .toList();
  }

  private static List<MemberId> voterIds(final List<RaftMember> sortedMembers, final String zone) {
    return sortedMembers.stream()
        .filter(member -> member.getType() == Type.ACTIVE)
        .map(RaftMember::memberId)
        .filter(member -> zoneOf(member).equals(zone))
        .toList();
  }

  private static int zoneRank(final @Nullable VoterPolicy policy, final MemberId member) {
    if (policy == null) {
      return 0;
    }
    final var zone = zoneOf(member);
    for (int rank = 0; rank < policy.zones().size(); rank++) {
      if (policy.zones().get(rank).zone().equals(zone)) {
        return rank;
      }
    }
    return policy.zones().size();
  }

  private static void requireNotJoint(final Configuration configuration) {
    if (configuration.requiresJointConsensus()) {
      throw new IllegalArgumentException(
          "Expected a configuration without a joint change in progress, but got " + configuration);
    }
  }

  /**
   * The result of {@link #selectVoters}.
   *
   * @param zones the zones of the policy, in policy order
   * @param nonPolicyVoters voters of zones that are not in the policy; they are extra
   * @param target the configuration that the policy wants: the kept voters are {@link Type#ACTIVE},
   *     all other members {@link Type#PASSIVE}. Callers change one member at a time towards it
   *     (5.6.4).
   */
  public record VoterSelection(
      List<ZoneSelection> zones, List<MemberId> nonPolicyVoters, Map<MemberId, Type> target) {

    public Optional<ZoneSelection> zone(final String zone) {
      return zones.stream().filter(selection -> selection.zone().equals(zone)).findAny();
    }
  }

  /**
   * @param zone the zone
   * @param slots the voter slots of the zone in the policy
   * @param voters the voters that are kept, lowest node index first
   * @param extraVoters voters beyond the slots, to be demoted by the leader
   * @param emptySlots slots without a voter, to be filled by self-promotion
   * @param candidates passive members of the zone that may fill an empty slot, lowest node index
   *     first
   */
  public record ZoneSelection(
      String zone,
      int slots,
      List<MemberId> voters,
      List<MemberId> extraVoters,
      int emptySlots,
      List<MemberId> candidates) {}

  /**
   * @param targetPriority the target priority of the priority election
   * @param voterPriorities the priority of each voter; passive members have priority 0
   */
  public record ElectionPriorities(int targetPriority, Map<MemberId, Integer> voterPriorities) {
    public int priorityOf(final MemberId member) {
      return voterPriorities.getOrDefault(member, 0);
    }
  }

  /**
   * What a broker knows about itself when it checks self-promotion.
   *
   * @param self the broker
   * @param incarnation its current incarnation
   * @param dynamicConfigurationInitialized whether its local dynamic configuration is initialized
   * @param excluded whether it is excluded (5.2) in its local dynamic configuration
   * @param emptyStartMarker whether its empty-start guard is still active
   * @param localCommitIndex its commit index
   * @param leaderCommitIndex the commit index of the leader, as far as it knows
   */
  public record SelfPromotionCheck(
      MemberId self,
      long incarnation,
      boolean dynamicConfigurationInitialized,
      boolean excluded,
      boolean emptyStartMarker,
      long localCommitIndex,
      long leaderCommitIndex) {}

  /**
   * @param target the target configuration
   * @param fence the fence entry of the leaving broker
   * @param replacement the passive member that the change promotes, or null
   */
  public record LeaveChange(
      Map<MemberId, Type> target, FenceEntry fence, @Nullable MemberId replacement) {}
}
