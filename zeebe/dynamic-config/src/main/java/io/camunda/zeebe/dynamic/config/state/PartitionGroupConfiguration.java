/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableSortedMap;
import io.atomix.cluster.MemberId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the configuration of a single named Raft partition group (e.g. one physical tenant).
 *
 * <p>Holds <em>only</em> per-group partition assignment and Raft replica state. Broker lifecycle
 * (JOINING → ACTIVE → LEAVING → LEFT) lives once, cluster-wide, in {@code GlobalConfiguration};
 * this record has no lifecycle {@code State} field. A broker is "active" in this group iff it is
 * present in {@code members} with a non-empty partition map. The per-broker operating mode
 * (PROCESSING/RECOVERING) is tracked on each {@link BrokerPartitionState}, since the mode is scoped
 * to a broker within a single group.
 *
 * <p>{@code version} is incremented only at plan boundaries (see {@link
 * #startConfigurationChange(List)} and {@link #advance()}). Merge uses a two-level scheme: if two
 * copies have different versions, the higher version wins wholesale; if equal, members are merged
 * field-by-field using their per-member versions.
 *
 * <p>This class is immutable; every mutating method returns a new instance.
 *
 * @param version version of this group configuration, bumped at plan boundaries
 * @param incarnationNumber incarnation number of this group, incremented after the data is purged.
 * @param members per-broker partition state within this group
 * @param routingState routing state scoped to this group, if any
 * @param pendingChanges the ongoing change plan for this group, if any
 * @param lastChange the last completed change plan for this group, if any
 * @param availability whether this tenant is currently disabled; carries its own version,
 *     independent of {@code version} — see {@link TenantAvailability}
 */
@NullMarked
public record PartitionGroupConfiguration(
    long version,
    long incarnationNumber,
    SortedMap<MemberId, BrokerPartitionState> members,
    Optional<RoutingState> routingState,
    Optional<ChangePlan> pendingChanges,
    Optional<CompletedChange> lastChange,
    TenantAvailability availability) {

  public static final long INITIAL_VERSION = 1;
  public static final long INITIAL_INCARNATION_NUMBER = 0;

  public PartitionGroupConfiguration {
    Objects.requireNonNull(members, "members must not be null");
    Objects.requireNonNull(routingState, "routingState must not be null");
    Objects.requireNonNull(pendingChanges, "pendingChanges must not be null");
    Objects.requireNonNull(lastChange, "lastChange must not be null");
    Objects.requireNonNull(availability, "availability must not be null");
    if (incarnationNumber < 0) {
      throw new IllegalArgumentException("Incarnation number must be >= 0");
    }
    members = ImmutableSortedMap.copyOf(members);
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public PartitionGroupConfiguration(
      final long version,
      final long incarnationNumber,
      final Map<MemberId, BrokerPartitionState> members,
      final Optional<RoutingState> routingState,
      final Optional<ChangePlan> pendingChanges,
      final Optional<CompletedChange> lastChange) {
    this(
        version,
        incarnationNumber,
        ImmutableSortedMap.copyOf(members),
        routingState,
        pendingChanges,
        lastChange,
        TenantAvailability.enabled());
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public PartitionGroupConfiguration(
      final long version,
      final long incarnationNumber,
      final Map<MemberId, BrokerPartitionState> members,
      final Optional<RoutingState> routingState,
      final Optional<ChangePlan> pendingChanges,
      final Optional<CompletedChange> lastChange,
      final TenantAvailability availability) {
    this(
        version,
        incarnationNumber,
        ImmutableSortedMap.copyOf(members),
        routingState,
        pendingChanges,
        lastChange,
        availability);
  }

  /** Creates an empty group configuration at the given version with no members and no changes. */
  public static PartitionGroupConfiguration empty(final long version) {
    return new PartitionGroupConfiguration(
        version,
        INITIAL_INCARNATION_NUMBER,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Starts a new configuration change for this group by setting {@code pendingChanges} to a new
   * {@link ClusterChangePlan} and bumping the group version.
   *
   * @param operations the operations to execute, must be non-empty
   * @return the updated group configuration
   * @throws IllegalArgumentException if a change is already in progress, or {@code operations} is
   *     empty
   */
  public PartitionGroupConfiguration startConfigurationChange(
      final List<ClusterConfigurationChangeOperation> operations) {
    if (hasPendingChanges()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is a configuration change in progress "
              + pendingChanges);
    }
    if (operations.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is no operation");
    }
    final long newVersion = version + 1;
    return new PartitionGroupConfiguration(
        newVersion,
        incarnationNumber,
        members,
        routingState,
        Optional.of(ClusterChangePlan.init(newVersion, operations)),
        lastChange,
        availability);
  }

  /**
   * Advances the ongoing change plan by removing its first pending operation, following the same
   * semantics as {@code ClusterConfiguration#advance()}.
   *
   * <p>While operations remain, the plan is simply stepped forward and the group version is
   * unchanged. When the last operation is removed, the change is completed: {@code pendingChanges}
   * is cleared, {@code lastChange} is set to the completed change, members whose {@code partitions}
   * map is empty are removed (the structural equivalent of {@code State.LEFT} in the legacy model —
   * a broker no longer replicating any partition of this group is no longer part of it), and the
   * group version is bumped so peers overwrite their local copy on merge.
   *
   * @return the updated group configuration
   * @throws IllegalStateException if there is no pending change to advance
   */
  public PartitionGroupConfiguration advance() {
    if (!hasPendingChanges()) {
      throw new IllegalStateException(
          "Expected to advance the configuration change, but there is no pending change");
    }

    if (!(pendingChanges.orElseThrow() instanceof final ClusterChangePlan queue)) {
      throw new IllegalStateException(
          "advance() steps a queue one operation at a time and is only valid for a "
              + "ClusterChangePlan; a DependencyChangePlan is progressed per operation via "
              + "completeOperation(OperationId, ...)");
    }
    final var result =
        new PartitionGroupConfiguration(
            version,
            incarnationNumber,
            members,
            routingState,
            Optional.of(queue.advance()),
            lastChange,
            availability);

    if (result.hasPendingChanges()) {
      return result;
    }

    // The last operation has been applied. Complete the change: clean up members that no longer
    // replicate any partition of this group and bump the version so other members merge by
    // overwriting their local copy.
    final var remainingMembers =
        result.members().entrySet().stream()
            .filter(entry -> !entry.getValue().partitions().isEmpty())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    final var completedChange = queue.completed();
    return new PartitionGroupConfiguration(
        result.version() + 1,
        incarnationNumber,
        remainingMembers,
        routingState,
        Optional.empty(),
        Optional.of(completedChange),
        availability);
  }

  /**
   * Returns a new {@link PartitionGroupConfiguration} after merging this and {@code other}. Does
   * not mutate either operand.
   *
   * <p>If the versions differ, the higher version wins wholesale. If equal, the result merges:
   * {@code members} field-by-field by per-member version (which also carries each broker's
   * operating mode); {@code pendingChanges} by plan-internal version; {@code routingState} by the
   * higher version; and {@code incarnationNumber} via {@link Math#max}.
   *
   * <p>{@code availability} is always merged by its own version ({@link
   * TenantAvailability#merge(TenantAvailability)}), independently of which branch above is taken —
   * its version is deliberately kept out of {@code version}, so without this it could be silently
   * overwritten by an unrelated top-level version bump in the whole-record-wins branch.
   *
   * @param other the configuration to merge with
   * @return the merged configuration
   */
  public PartitionGroupConfiguration merge(final PartitionGroupConfiguration other) {
    final var mergedAvailability = availability.merge(other.availability);

    if (version > other.version) {
      return availability.equals(mergedAvailability)
          ? this
          : new PartitionGroupConfiguration(
              version,
              incarnationNumber,
              members,
              routingState,
              pendingChanges,
              lastChange,
              mergedAvailability);
    } else if (other.version > version) {
      return other.availability.equals(mergedAvailability)
          ? other
          : new PartitionGroupConfiguration(
              other.version,
              other.incarnationNumber,
              other.members,
              other.routingState,
              other.pendingChanges,
              other.lastChange,
              mergedAvailability);
    }

    final var mergedMembers =
        Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, BrokerPartitionState::merge));

    final Optional<RoutingState> mergedRoutingState =
        Stream.of(routingState, other.routingState)
            .flatMap(Optional::stream)
            .reduce(RoutingState::merge);

    final Optional<ChangePlan> mergedChanges =
        Stream.of(pendingChanges, other.pendingChanges)
            .flatMap(Optional::stream)
            .reduce(PartitionGroupConfiguration::mergePlans);

    return new PartitionGroupConfiguration(
        version,
        Math.max(incarnationNumber, other.incarnationNumber),
        mergedMembers,
        mergedRoutingState,
        mergedChanges,
        mergeLastChange(lastChange, other.lastChange),
        mergedAvailability);
  }

  /**
   * Merges two {@code lastChange} records seen by different brokers, both stamped for the same
   * completed change.
   *
   * <p>Every other equal-version field above is resolved by keeping the receiver's copy, safe
   * because it either only ever has one writer or is itself independently mergeable (members,
   * pendingChanges). {@code lastChange} for a graph change is neither: {@link
   * #completeGraphChangeIfDrained()} runs on every broker with no coordinator gate, and each mints
   * its own {@link CompletedChange} from its own view of when the last operation completed. Two
   * brokers minting a moment apart can disagree on {@code completedAt} for the very same change, at
   * the very same group version, and keeping "the receiver's own" would leave that disagreement
   * standing forever — see {@code DependencyChangePlan#toCompletedChange}'s own javadoc for why.
   *
   * <p>Resolved the same way {@link DependencyChangePlan#merge} already resolves the analogous case
   * for individual operation completions: same change id, earliest {@code completedAt} wins;
   * different change id (a genuinely later, unrelated completion on this group), the higher id
   * wins, since ids are monotonic and a higher one is always the newer change.
   */
  private static Optional<CompletedChange> mergeLastChange(
      final Optional<CompletedChange> mine, final Optional<CompletedChange> theirs) {
    if (mine.isEmpty()) {
      return theirs;
    }
    if (theirs.isEmpty()) {
      return mine;
    }
    final var mineChange = mine.orElseThrow();
    final var theirsChange = theirs.orElseThrow();
    if (mineChange.id() != theirsChange.id()) {
      return mineChange.id() > theirsChange.id() ? mine : theirs;
    }
    return mineChange.completedAt().isBefore(theirsChange.completedAt()) ? mine : theirs;
  }

  /**
   * Adds a new broker to this group. Does not change the group version — the broker carries its own
   * per-member version; the group version only moves at plan boundaries.
   *
   * @throws IllegalStateException if the broker is already part of the group
   */
  public PartitionGroupConfiguration addMember(
      final MemberId memberId, final BrokerPartitionState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }
    final var updatedMembers = new HashMap<>(members);
    updatedMembers.put(memberId, state);
    return withMembers(updatedMembers);
  }

  /**
   * Transforms an existing broker's state via {@code memberStateUpdater} (e.g. {@code bps ->
   * bps.setMode(RECOVERING)}). The updater is responsible only for the transformation; the
   * per-member version is bumped by {@link BrokerPartitionState}'s own update methods. Does not
   * change the group version. Returns {@code this} if the state is unchanged.
   *
   * @throws IllegalStateException if the broker is not part of the group
   */
  public PartitionGroupConfiguration updateMember(
      final MemberId memberId, final UnaryOperator<BrokerPartitionState> memberStateUpdater) {
    final BrokerPartitionState current = members.get(memberId);
    if (current == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to update member %s, but it is not part of the group", memberId.id()));
    }
    final var updated = memberStateUpdater.apply(current);
    if (updated.equals(current)) {
      return this;
    }
    final var updatedMembers = new HashMap<>(members);
    updatedMembers.put(memberId, updated);
    return withMembers(updatedMembers);
  }

  /**
   * Sets the routing state for this group. Does not change the group version — {@link RoutingState}
   * carries its own version.
   */
  public PartitionGroupConfiguration setRoutingState(final RoutingState updatedRoutingState) {
    return new PartitionGroupConfiguration(
        version,
        incarnationNumber,
        members,
        Optional.of(updatedRoutingState),
        pendingChanges,
        lastChange,
        availability);
  }

  public boolean isDisabled() {
    return availability.state() != TenantAvailability.State.ENABLED;
  }

  /**
   * Marks this tenant as disabled, e.g. because it was removed from the local static configuration.
   * Does not change {@code version} or touch {@code members} — the partition assignment is retained
   * so the tenant can resume where it left off if re-enabled. Returns {@code this} if already
   * disabled.
   */
  public PartitionGroupConfiguration disable() {
    return withAvailability(availability.disable());
  }

  /**
   * Marks this tenant as enabled again. Does not change {@code version} or touch {@code members}.
   * Returns {@code this} if already enabled, or if it has been removed — see {@link
   * TenantAvailability#enable()}.
   */
  public PartitionGroupConfiguration enable() {
    return withAvailability(availability.enable());
  }

  /**
   * Whether an operator has explicitly discarded this tenant. A removed tenant is also {@link
   * #isDisabled() disabled}; the difference matters only for leaving the cluster, since a broker
   * holding only removed tenants' partitions may leave it, unlike one holding merely disabled ones.
   */
  public boolean isRemoved() {
    return availability.state() == TenantAvailability.State.REMOVED;
  }

  /**
   * Marks this tenant as explicitly discarded, terminally — see {@link TenantAvailability}. Returns
   * {@code this} if already removed.
   *
   * <p>Clears {@code members} and {@code routingState} rather than retaining them, since a removed
   * tenant is never re-enabled and so has nothing left to resume. This still converges: the group
   * version bumps once the operation's plan completes ({@link #advance()}), so the
   * higher-versioned, cleared copy wins wholesale over a peer that has not seen the removal yet
   * ({@link #merge(PartitionGroupConfiguration)}).
   *
   * <p>{@code incarnationNumber} stays put because removal purges no data — it stays on disk,
   * unreachable through this configuration but not deleted by it. {@code pendingChanges} and {@code
   * version} stay untouched too, so the {@link #advance()} call right after this one still sees,
   * and completes, the operation it belongs to.
   */
  public PartitionGroupConfiguration remove() {
    if (isRemoved()) {
      return this;
    }
    return new PartitionGroupConfiguration(
        version,
        incarnationNumber,
        Map.of(),
        Optional.empty(),
        pendingChanges,
        lastChange,
        availability.remove());
  }

  private PartitionGroupConfiguration withAvailability(
      final TenantAvailability updatedAvailability) {
    if (updatedAvailability.equals(availability)) {
      return this;
    }
    return new PartitionGroupConfiguration(
        version,
        incarnationNumber,
        members,
        routingState,
        pendingChanges,
        lastChange,
        updatedAvailability);
  }

  public boolean hasPendingChanges() {
    return pendingChanges.isPresent() && pendingChanges.orElseThrow().hasPendingChanges();
  }

  /**
   * Returns the next pending operation for the given memberId, or empty if the next pending
   * operation (if any) is not applicable to that member. Mirrors {@code
   * ClusterConfiguration#pendingChangesFor(MemberId)}.
   */
  public Optional<PartitionGroupOperation> pendingChangesFor(final MemberId memberId) {
    if (!(pendingChanges.orElse(null) instanceof final ClusterChangePlan queue)
        || !queue.hasPendingChangesFor(memberId)) {
      return Optional.empty();
    }
    return Optional.of((PartitionGroupOperation) queue.nextPendingOperation());
  }

  public PartitionGroupOperation nextPendingOperation() {
    if (!(pendingChanges.orElse(null) instanceof final ClusterChangePlan queue)
        || !queue.hasPendingChanges()) {
      throw new NoSuchElementException();
    }
    return (PartitionGroupOperation) queue.nextPendingOperation();
  }

  /**
   * When the operation returned by {@link #pendingChangesFor(MemberId)} completes, the result is
   * reflected here by applying {@code configurationUpdater} and then advancing past the completed
   * operation (see {@link #advance()}). Mirrors {@code
   * ClusterConfiguration#advanceConfigurationChange}.
   */
  public PartitionGroupConfiguration advanceConfigurationChange(
      final UnaryOperator<PartitionGroupConfiguration> configurationUpdater) {
    return configurationUpdater.apply(this).advance();
  }

  /**
   * Cancels any pending changes, returning a new configuration with the already-applied changes and
   * no pending changes. This is a dangerous operation that can leave the configuration
   * inconsistent; it should only be used as a last resort when a change is stuck. Mirrors {@code
   * ClusterConfiguration#cancelPendingChanges()}.
   */
  public PartitionGroupConfiguration cancelPendingChanges() {
    if (!hasPendingChanges()) {
      return this;
    }
    final var cancelledChange = pendingChanges.orElseThrow().cancel();
    // Increment version by 2 to avoid conflicts with other members who are applying the change.
    return new PartitionGroupConfiguration(
        version + 2,
        incarnationNumber,
        members,
        routingState,
        Optional.empty(),
        Optional.of(cancelledChange),
        availability);
  }

  /**
   * Merges two plans of the same execution model. The two models never meet in practice — a group
   * runs one change at a time and a change is built by one transformer — so a mismatch means the
   * two copies disagree about which model the change uses, which merging cannot repair. Keeping the
   * receiver is the safe answer; the version-based branch above resolves it once either side moves.
   */
  private static ChangePlan mergePlans(final ChangePlan mine, final ChangePlan theirs) {
    if (mine instanceof final ClusterChangePlan a && theirs instanceof final ClusterChangePlan b) {
      return a.merge(b);
    }
    if (mine instanceof final DependencyChangePlan a
        && theirs instanceof final DependencyChangePlan b) {
      return a.merge(b);
    }
    return mine;
  }

  /** The ongoing change if it uses the dependency-graph model, otherwise empty. */
  public Optional<DependencyChangePlan> pendingGraphChanges() {
    return pendingChanges
        .filter(DependencyChangePlan.class::isInstance)
        .map(DependencyChangePlan.class::cast);
  }

  /**
   * Starts a change whose operations carry their own dependencies, bumping the group version as any
   * plan start does.
   *
   * @throws IllegalArgumentException if a change is already in progress, or the graph is empty
   */
  public PartitionGroupConfiguration startGraphConfigurationChange(final OperationGraph graph) {
    if (hasPendingChanges()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is a configuration change in progress "
              + pendingChanges);
    }
    if (graph.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is no operation");
    }
    final long newVersion = version + 1;
    return new PartitionGroupConfiguration(
        newVersion,
        incarnationNumber,
        members,
        routingState,
        Optional.of(DependencyChangePlan.init(newVersion, graph)),
        lastChange,
        availability);
  }

  /** Everything the given member may start right now. Empty unless a graph change is running. */
  public SortedMap<OperationId, PartitionGroupOperation> runnableFor(final MemberId memberId) {
    final SortedMap<OperationId, PartitionGroupOperation> runnable = new TreeMap<>();
    pendingGraphChanges()
        .ifPresent(
            plan ->
                plan.runnableFor(memberId)
                    .forEach(
                        (operationId, operation) ->
                            runnable.put(operationId, (PartitionGroupOperation) operation)));
    return runnable;
  }

  /**
   * Applies a completed operation's effect and records it against the plan, in one transition.
   *
   * <p>Deliberately does not move the group version. Under a graph change several brokers progress
   * at once, and a version bump here would take the group merge off its structural branch and
   * discard the others' progress. Finishing the change is separate — see {@link
   * #completeGraphChangeIfDrained()}.
   */
  public PartitionGroupConfiguration completeOperation(
      final OperationId operationId, final UnaryOperator<PartitionGroupConfiguration> updater) {
    final var updated = updater.apply(this);
    final var plan =
        updated
            .pendingGraphChanges()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expected to record %s, but no dependency-graph change is in progress"
                            .formatted(operationId)));
    return new PartitionGroupConfiguration(
        updated.version(),
        updated.incarnationNumber(),
        updated.members(),
        updated.routingState(),
        Optional.of(plan.completeOperation(operationId)),
        updated.lastChange(),
        updated.availability());
  }

  /**
   * Finishes a graph change once every operation has completed, doing the same end-of-plan work
   * {@link #advance()} does for a queue. Returns {@code this} unchanged otherwise, so every broker
   * may call it on every merge — which is how the change completes without a coordinator.
   */
  public PartitionGroupConfiguration completeGraphChangeIfDrained() {
    final var plan = pendingGraphChanges().orElse(null);
    if (plan == null || plan.hasPendingChanges()) {
      return this;
    }
    final var remainingMembers =
        members.entrySet().stream()
            .filter(entry -> !entry.getValue().partitions().isEmpty())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    return new PartitionGroupConfiguration(
        version + 1,
        incarnationNumber,
        remainingMembers,
        routingState,
        Optional.empty(),
        Optional.of(plan.toCompletedChange()),
        availability);
  }

  public boolean hasMember(final MemberId memberId) {
    return members.containsKey(memberId);
  }

  public @Nullable BrokerPartitionState getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  /**
   * Returns the <em>desired leader</em> of the given partition within this group (the broker that
   * replicates the partition with the highest {@link PartitionState#priority() priority}).
   *
   * @param partitionId the partition id, unique only within this group
   * @return the desired leader, or empty if no broker in this group replicates the partition
   */
  public Optional<MemberId> getDesiredLeader(final int partitionId) {
    return members.entrySet().stream()
        .map(
            entry -> {
              final PartitionState partition = entry.getValue().getPartition(partitionId);
              return partition == null ? null : Map.entry(entry.getKey(), partition);
            })
        .filter(Objects::nonNull)
        .max(
            Comparator.<Entry<MemberId, PartitionState>>comparingInt(
                    entry -> entry.getValue().priority())
                .thenComparing(Entry::getKey, MemberId.ID_COMPARATOR.reversed()))
        .map(Entry::getKey);
  }

  /**
   * Returns the desired leader of every partition in this group, keyed by partition id. A partition
   * is present iff at least one broker in the group replicates it.
   *
   * @return the desired leaders, keyed by partition id in ascending order
   */
  public SortedMap<Integer, MemberId> desiredLeaders() {
    final SortedMap<Integer, MemberId> leaders = new TreeMap<>();
    members.values().stream()
        .flatMap(broker -> broker.partitions().keySet().stream())
        .distinct()
        .forEach(
            partitionId ->
                getDesiredLeader(partitionId)
                    .ifPresent(leader -> leaders.put(partitionId, leader)));
    return leaders;
  }

  private PartitionGroupConfiguration withMembers(
      final Map<MemberId, BrokerPartitionState> updatedMembers) {
    return new PartitionGroupConfiguration(
        version,
        incarnationNumber,
        updatedMembers,
        routingState,
        pendingChanges,
        lastChange,
        availability);
  }

  public int partitionCount() {
    return members.values().stream()
        .flatMap(broker -> broker.partitions().keySet().stream())
        .distinct()
        .toList()
        .size();
  }

  public Optional<MemberId> getPrimaryForPartition(final int partitionId) {
    return members.entrySet().stream()
        .filter(entry -> entry.getValue().hasPartition(partitionId))
        .max(
            Comparator.comparingInt(
                e -> Objects.requireNonNull(e.getValue().getPartition(partitionId)).priority()))
        .map(Entry::getKey);
  }

  public int minReplicationFactor() {
    // return minimum replication factor. During a configuration change, replication factor might
    // increase temporarily.
    return members.values().stream()
        .flatMap(m -> m.partitions().entrySet().stream())
        .collect(Collectors.groupingBy(Entry::getKey, Collectors.counting()))
        .values()
        .stream()
        .reduce(Math::min)
        .map(Long::intValue)
        .orElse(0);
  }

  public IntStream partitionIds() {
    return members.values().stream()
        .flatMapToInt(m -> m.partitions().keySet().stream().mapToInt(i -> i))
        .sorted()
        .distinct();
  }
}
