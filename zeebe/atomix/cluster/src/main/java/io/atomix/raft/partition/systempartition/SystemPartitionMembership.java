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

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.MembershipChange;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.ReplicationStatus;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.partition.systempartition.VoterPolicy.ZoneVoters;
import io.atomix.raft.storage.system.Configuration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The rules with which the leader of the system partition keeps its members in line with the
 * cluster members and the voter policy (design {@code
 * docs/system-partition/system-partition-reconfiguration.md}, revision 7, sections 5.2 to 5.6).
 * Pure functions of the committed Raft {@link Configuration}, the leader's view of the cluster
 * members ({@link ClusterView}), the published incarnations, the replication state and the {@link
 * VoterPolicy}. The membership component of the leader sends the result with {@link
 * io.atomix.raft.RaftServer#changeMembers(MembershipChange)}.
 *
 * <p><b>Zones and order.</b> The zone of a member is {@link MemberId#zone()}, or "" for a member
 * without a zone. A zone that is not in the policy has 0 slots. Members of a zone are ordered by
 * {@link MemberId#ID_COMPARATOR}, i.e. by node index.
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
   * A stable hash of the voter policy and the initial configuration, for the probe (5.4.2). Two
   * brokers with different hashes would bootstrap different initial configurations.
   */
  public static String initialConfigurationHash(
      final VoterPolicy policy, final Collection<MemberId> members) {
    final var builder = new StringBuilder();
    policy
        .zones()
        .forEach(z -> builder.append(z.zone()).append('=').append(z.voters()).append(';'));
    builder.append('|');
    initialConfiguration(policy, members)
        .forEach(
            (member, type) -> builder.append(member.id()).append('=').append(type).append(';'));
    return Integer.toHexString(builder.toString().hashCode());
  }

  /**
   * Selects the voters (5.2). Current voters are kept; if a zone has more voters than slots, the
   * voters with the highest node index are extra; empty slots stay empty, the leader fills them
   * later (5.6.4). Voters of a zone without slots are extra too. Liveness is not an input: it never
   * changes the voters.
   *
   * @param policy the voter policy
   * @param configuration a committed configuration that is not in joint consensus
   * @param excluded brokers that are not authorized (5.5): they are never candidates for a slot
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
   * (control zone first), then by node index. The priority of a voter is {@code policyTotal -
   * position}, but at least 1, so that an extra voter (beyond the policy) can still start an
   * election. The target priority is {@code policyTotal}. A passive member has priority 0.
   */
  public static ElectionPriorities priorities(
      final Configuration configuration, final VoterPolicy policy) {
    final var voters =
        configuration.newMembers().stream()
            .filter(member -> member.getType() == Type.ACTIVE)
            .map(RaftMember::memberId)
            .sorted(
                Comparator.comparingInt((MemberId member) -> zoneRank(policy, member))
                    .thenComparing(MemberId.ID_COMPARATOR))
            .toList();
    final int total = policy.zones().stream().mapToInt(ZoneVoters::voters).sum();
    final var priorities = new LinkedHashMap<MemberId, Integer>();
    for (int position = 0; position < voters.size(); position++) {
      priorities.put(voters.get(position), Math.max(1, total - position));
    }
    return new ElectionPriorities(Math.max(1, total), priorities);
  }

  /**
   * Authorization (5.5): a broker is authorized if it is {@code JOINING} or {@code ACTIVE}, or the
   * target of a pending {@code MemberJoin}, and it is not {@code LEAVING} or {@code LEFT} and not
   * the target of a pending {@code MemberLeave} or {@code MemberRemove}. The negative conditions
   * have priority.
   */
  public static boolean isAuthorized(final ClusterView view, final MemberId broker) {
    final var state = view.states().get(broker);
    if (state == BrokerState.LEAVING
        || state == BrokerState.LEFT
        || view.pendingLeaves().contains(broker)
        || view.pendingRemoves().contains(broker)) {
      return false;
    }
    return state == BrokerState.JOINING
        || state == BrokerState.ACTIVE
        || view.pendingJoins().contains(broker);
  }

  /**
   * Rule 1 of 5.5: a member is removed if it is {@code LEAVING} or {@code LEFT}, the target of a
   * pending {@code MemberRemove}, or neither in the dynamic configuration nor the target of a
   * pending {@code MemberJoin}. A pending {@code MemberLeave} alone does not remove it.
   */
  public static boolean mustBeRemoved(final ClusterView view, final MemberId member) {
    final var state = view.states().get(member);
    return state == BrokerState.LEAVING
        || state == BrokerState.LEFT
        || view.pendingRemoves().contains(member)
        || (state == null && !view.pendingJoins().contains(member));
  }

  /**
   * Decides the next change of the members (5.5 rules 1 to 5, 5.6.4). The leader takes the first
   * rule that applies and makes one change at a time.
   *
   * @param input what the leader knows
   * @return the decision; it can carry fenced notices in addition to a change
   */
  public static LeaderDecision decide(final LeaderInput input) {
    final var configuration = input.configuration();
    final var view = input.view();
    final var notices = fencedNotices(input);

    if (!input.committed() || configuration.requiresJointConsensus()) {
      return LeaderDecision.waitFor("the configuration is not committed or a change is in progress")
          .withFencedNotices(notices);
    }
    if (!view.initialized()) {
      return LeaderDecision.waitFor("the dynamic configuration is not initialized")
          .withFencedNotices(notices);
    }
    if (view.version() < configuration.membershipVersion()) {
      return LeaderDecision.waitFor(
              "the view of the cluster (version %d) is older than the membership version %d"
                  .formatted(view.version(), configuration.membershipVersion()))
          .withFencedNotices(notices);
    }

    return ruleRemove(input)
        .or(() -> ruleReplaceIncarnation(input))
        .or(() -> ruleAdd(input))
        .or(() -> ruleDemote(input))
        .or(() -> rulePromote(input))
        .orElse(LeaderDecision.waitFor("the members agree with the cluster and the policy"))
        .withFencedNotices(notices);
  }

  /** Rule 1: remove members that leave or are removed, and replace leaving voters (D9). */
  private static Optional<LeaderDecision> ruleRemove(final LeaderInput input) {
    final var configuration = input.configuration();
    final var removed =
        sorted(configuration.newMembers()).stream()
            .filter(member -> mustBeRemoved(input.view(), member.memberId()))
            .toList();
    if (removed.isEmpty()) {
      return Optional.empty();
    }
    if (removed.stream().anyMatch(member -> member.memberId().equals(input.leader()))) {
      return Optional.of(
          LeaderDecision.transferLeadership(
              "rule 1: the leader %s must be removed, it transfers its leadership first"
                  .formatted(input.leader())));
    }
    final var removedIds = removed.stream().map(RaftMember::memberId).collect(Collectors.toSet());
    final var target = new LinkedHashMap<MemberId, RaftMember>();
    sorted(configuration.newMembers()).stream()
        .filter(member -> !removedIds.contains(member.memberId()))
        .forEach(member -> target.put(member.memberId(), member));
    final var promoted = new ArrayList<MemberId>();
    for (final var member : removed) {
      if (member.getType() == Type.ACTIVE) {
        replacement(input, zoneOf(member.memberId()), target.values(), Set.copyOf(promoted))
            .ifPresent(
                replacement -> {
                  promoted.add(replacement.memberId());
                  target.put(replacement.memberId(), withType(replacement, Type.ACTIVE));
                });
      }
    }
    return Optional.of(
        LeaderDecision.change(
            change(input, target.values()),
            "rule 1: remove %s%s"
                .formatted(
                    removedIds.stream().sorted(MemberId.ID_COMPARATOR).toList(),
                    promoted.isEmpty() ? "" : " and promote " + promoted)));
  }

  /**
   * Rule 2: replace a member whose broker publishes a new incarnation that is not fenced, if the
   * incarnation of the configuration did not answer within the election timeout. The new
   * incarnation is added as passive. A voter is replaced in the same change (D9). The broker must
   * be authorized, else the leader does not add the new incarnation.
   */
  private static Optional<LeaderDecision> ruleReplaceIncarnation(final LeaderInput input) {
    final var configuration = input.configuration();
    for (final var member : sorted(configuration.newMembers())) {
      final var id = member.memberId();
      final var published = input.incarnations().get(id);
      if (id.equals(input.leader())
          || published == null
          || published == member.getIncarnation()
          || configuration.isFenced(id, published)
          || !isAuthorized(input.view(), id)) {
        continue;
      }
      if (answeredRecently(input, member)) {
        // A second process with the same id does not replace a healthy member (5.5).
        continue;
      }
      final var target = new LinkedHashMap<MemberId, RaftMember>();
      sorted(configuration.newMembers()).stream()
          .filter(m -> !m.memberId().equals(id))
          .forEach(m -> target.put(m.memberId(), m));
      Optional<RaftMember> replacement = Optional.empty();
      if (member.getType() == Type.ACTIVE) {
        replacement = replacement(input, zoneOf(id), target.values(), Set.of());
        replacement.ifPresent(r -> target.put(r.memberId(), withType(r, Type.ACTIVE)));
      }
      target.put(id, new DefaultRaftMember(id, Type.PASSIVE, Instant.now(), published));
      return Optional.of(
          LeaderDecision.change(
              change(input, target.values()),
              "rule 2: replace %s#%d by %s#%d%s"
                  .formatted(
                      id,
                      member.getIncarnation(),
                      id,
                      published,
                      replacement.map(r -> " and promote " + r.memberId()).orElse(""))));
    }
    return Optional.empty();
  }

  /** Rule 3: add authorized brokers that publish an incarnation that is not fenced as passive. */
  private static Optional<LeaderDecision> ruleAdd(final LeaderInput input) {
    final var configuration = input.configuration();
    final var added = new ArrayList<RaftMember>();
    for (final var entry : sortedIncarnations(input)) {
      final var id = entry.getKey();
      if (configuration.hasMember(id)
          || configuration.isFenced(id, entry.getValue())
          || !isAuthorized(input.view(), id)) {
        continue;
      }
      added.add(new DefaultRaftMember(id, Type.PASSIVE, Instant.now(), entry.getValue()));
    }
    if (added.isEmpty()) {
      return Optional.empty();
    }
    final var target = new ArrayList<RaftMember>(configuration.newMembers());
    target.addAll(added);
    return Optional.of(
        LeaderDecision.change(
            change(input, target),
            "rule 3: add %s as %s"
                .formatted(added.stream().map(RaftMember::memberId).toList(), Type.PASSIVE)));
  }

  /**
   * Rule 4: demote one extra voter (5.6.4): first the voter with the highest node index of a zone
   * that has more voters than slots; then a voter of a zone without slots, but only once a zone
   * with slots has a voter. If the voter is the leader, it transfers its leadership first.
   */
  private static Optional<LeaderDecision> ruleDemote(final LeaderInput input) {
    final var configuration = input.configuration();
    final var policy = input.policy();
    final var members = sorted(configuration.newMembers());
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
    if (demoted.equals(input.leader())) {
      return Optional.of(
          LeaderDecision.transferLeadership(
              "rule 4: the leader %s is an extra voter, it transfers its leadership first"
                  .formatted(demoted)));
    }
    final var finalDemoted = demoted;
    final var target =
        members.stream()
            .map(m -> m.memberId().equals(finalDemoted) ? withType(m, Type.PASSIVE) : m)
            .toList();
    return Optional.of(LeaderDecision.change(change(input, target), "rule 4: demote " + demoted));
  }

  /** Rule 5: promote one caught-up passive member into an empty slot (5.6.4). */
  private static Optional<LeaderDecision> rulePromote(final LeaderInput input) {
    final var configuration = input.configuration();
    final var members = sorted(configuration.newMembers());
    for (final var zoneVoters : input.policy().zones()) {
      if (voterIds(members, zoneVoters.zone()).size() >= zoneVoters.voters()) {
        continue;
      }
      final var promoted = replacement(input, zoneVoters.zone(), members, Set.of());
      if (promoted.isPresent()) {
        final var id = promoted.get().memberId();
        final var target =
            members.stream()
                .map(m -> m.memberId().equals(id) ? withType(m, Type.ACTIVE) : m)
                .toList();
        return Optional.of(LeaderDecision.change(change(input, target), "rule 5: promote " + id));
      }
    }
    return Optional.empty();
  }

  /**
   * A passive member of the zone that may become a voter (5.6.4): authorized, not fenced, caught up
   * and answered within the election timeout; the lowest node index first.
   */
  private static Optional<RaftMember> replacement(
      final LeaderInput input,
      final String zone,
      final Collection<RaftMember> candidates,
      final Set<MemberId> taken) {
    return candidates.stream()
        .sorted(Comparator.comparing(RaftMember::memberId, MemberId.ID_COMPARATOR))
        .filter(member -> member.getType() == Type.PASSIVE)
        .filter(member -> zoneOf(member.memberId()).equals(zone))
        .filter(member -> !taken.contains(member.memberId()))
        .filter(member -> !input.configuration().isFenced(member))
        .filter(member -> isAuthorized(input.view(), member.memberId()))
        .filter(member -> isCaughtUp(input, member))
        .filter(member -> answeredRecently(input, member))
        .findFirst();
  }

  /**
   * The promotion check of 5.6.4 item 4 (R9): the match index is above 0, and it is not lower than
   * the commit index of the leader, or the replication lag is below the threshold.
   */
  static boolean isCaughtUp(final LeaderInput input, final RaftMember member) {
    return input
        .replication()
        .member(member.memberId())
        .filter(status -> status.incarnation() == member.getIncarnation())
        .map(
            status ->
                status.matchIndex() > 0
                    && (status.matchIndex() >= input.replication().commitIndex()
                        || status.replicationLagBytes() <= input.promotionLagThreshold()))
        .orElse(false);
  }

  /** The member answered with its incarnation of the configuration within the election timeout. */
  static boolean answeredRecently(final LeaderInput input, final RaftMember member) {
    return input
        .replication()
        .member(member.memberId())
        .filter(status -> status.incarnation() == member.getIncarnation())
        .map(status -> input.now() - status.lastResponseTime() <= input.electionTimeoutMillis())
        .orElse(false);
  }

  /**
   * An authorized broker that is not in the configuration with its published incarnation, and that
   * publishes a fenced incarnation, has old data; the leader sends it the fenced notice instead of
   * adding it (5.5, 5.6.1). A removed broker that is not authorized gets no notice: it is never
   * accessible, and gets the notice when the cluster adds it again (5.10).
   */
  private static Set<MemberId> fencedNotices(final LeaderInput input) {
    final var configuration = input.configuration();
    final var notices = new TreeSet<>(MemberId.ID_COMPARATOR);
    if (!input.view().initialized()) {
      return notices;
    }
    input
        .incarnations()
        .forEach(
            (id, incarnation) -> {
              final var configured = configuration.incarnationOf(id);
              final boolean notTheMemberOfTheConfiguration =
                  configured.isEmpty() || configured.getAsLong() != incarnation;
              if (notTheMemberOfTheConfiguration
                  && configuration.isFenced(id, incarnation)
                  && isAuthorized(input.view(), id)) {
                notices.add(id);
              }
            });
    return notices;
  }

  private static MembershipChange change(
      final LeaderInput input, final Collection<RaftMember> target) {
    return new MembershipChange(
        target, List.of(), input.view().version(), input.configuration().index());
  }

  private static List<Map.Entry<MemberId, Long>> sortedIncarnations(final LeaderInput input) {
    return input.incarnations().entrySet().stream()
        .sorted(Map.Entry.comparingByKey(MemberId.ID_COMPARATOR))
        .toList();
  }

  private static RaftMember withType(final RaftMember member, final Type type) {
    return new DefaultRaftMember(member.memberId(), type, Instant.now(), member.getIncarnation());
  }

  static String zoneOf(final MemberId member) {
    return Objects.toString(member.zone(), "");
  }

  static int slotsOf(final VoterPolicy policy, final String zone) {
    return policy.votersOf(zone).orElse(0);
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

  private static int zoneRank(final VoterPolicy policy, final MemberId member) {
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

  /** The state of a broker in the dynamic configuration. */
  public enum BrokerState {
    JOINING,
    ACTIVE,
    LEAVING,
    LEFT
  }

  /**
   * The leader's view of the cluster members (5.5): today its dynamic configuration, later the
   * system partition state.
   *
   * @param initialized false while the dynamic configuration is not initialized
   * @param version the version of the view; it becomes the membership version of a change (R19)
   * @param states the state of each broker of the dynamic configuration
   * @param pendingJoins targets of pending {@code MemberJoin} operations
   * @param pendingLeaves targets of pending {@code MemberLeave} operations
   * @param pendingRemoves targets of pending {@code MemberRemove} operations
   */
  public record ClusterView(
      boolean initialized,
      long version,
      Map<MemberId, BrokerState> states,
      Set<MemberId> pendingJoins,
      Set<MemberId> pendingLeaves,
      Set<MemberId> pendingRemoves) {

    public ClusterView {
      states = Map.copyOf(states);
      pendingJoins = Set.copyOf(pendingJoins);
      pendingLeaves = Set.copyOf(pendingLeaves);
      pendingRemoves = Set.copyOf(pendingRemoves);
    }

    public static ClusterView notInitialized() {
      return new ClusterView(false, 0, Map.of(), Set.of(), Set.of(), Set.of());
    }

    /**
     * @return the brokers that are never selected as new voters (5.2)
     */
    public Set<MemberId> excluded(final Collection<MemberId> members) {
      return members.stream()
          .filter(member -> !isAuthorized(this, member))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
  }

  /**
   * What the leader knows when it decides (5.5, "Input").
   *
   * @param leader the leader itself
   * @param configuration its current configuration
   * @param committed whether the configuration is committed
   * @param view its view of the cluster members
   * @param incarnations the incarnation that each broker publishes (5.4.4); a broker without one is
   *     not in the map
   * @param replication the replication state of the members
   * @param policy the voter policy
   * @param now the current time (epoch millis)
   * @param electionTimeoutMillis the election timeout
   * @param promotionLagThreshold the replication lag in bytes up to which a member is caught up
   */
  public record LeaderInput(
      MemberId leader,
      Configuration configuration,
      boolean committed,
      ClusterView view,
      Map<MemberId, Long> incarnations,
      ReplicationStatus replication,
      VoterPolicy policy,
      long now,
      long electionTimeoutMillis,
      long promotionLagThreshold) {

    public LeaderInput {
      incarnations = Map.copyOf(incarnations);
    }
  }

  /**
   * The decision of the leader.
   *
   * @param change the change to send, or null
   * @param transferLeadership true if the leader must transfer its leadership before the next
   *     change, because the change removes or demotes it
   * @param fencedNotices brokers that get the fenced notice
   * @param reason why
   */
  public record LeaderDecision(
      @Nullable MembershipChange change,
      boolean transferLeadership,
      Set<MemberId> fencedNotices,
      String reason) {

    static LeaderDecision waitFor(final String reason) {
      return new LeaderDecision(null, false, Set.of(), reason);
    }

    static LeaderDecision change(final MembershipChange change, final String reason) {
      return new LeaderDecision(change, false, Set.of(), reason);
    }

    static LeaderDecision transferLeadership(final String reason) {
      return new LeaderDecision(null, true, Set.of(), reason);
    }

    LeaderDecision withFencedNotices(final Set<MemberId> notices) {
      return new LeaderDecision(change, transferLeadership, Set.copyOf(notices), reason);
    }

    public Optional<MembershipChange> optionalChange() {
      return Optional.ofNullable(change);
    }
  }

  /**
   * The result of {@link #selectVoters}.
   *
   * @param zones the zones of the policy, in policy order
   * @param nonPolicyVoters voters of zones that are not in the policy; they are extra
   * @param target the configuration that the policy wants: the kept voters are {@link Type#ACTIVE},
   *     all other members {@link Type#PASSIVE}. The leader changes one member at a time towards it
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
   * @param emptySlots slots without a voter, to be filled by the leader
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
}
