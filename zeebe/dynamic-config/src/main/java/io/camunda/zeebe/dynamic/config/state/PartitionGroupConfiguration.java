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
import java.util.Map;
import java.util.Map.Entry;
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
 * #startGraphConfigurationChange(OperationGraph)} and {@link #completeGraphChangeIfDrained()}).
 * Merge uses a two-level scheme: if two copies have different versions, the higher version wins
 * wholesale; if equal, members are merged field-by-field using their per-member versions.
 *
 * <p>This class is immutable; every mutating method returns a new instance.
 *
 * @param version version of this group configuration, bumped at plan boundaries
 * @param incarnationNumber incarnation number of this group, incremented after the data is purged.
 * @param members per-broker partition state within this group
 * @param routingState routing state scoped to this group, if any
 * @param pendingChanges the ongoing change plan for this group, if any. Always a {@link
 *     DependencyChangePlan}, as every sub-configuration's is: the one-at-a-time queue ({@link
 *     ClusterChangePlan}) no longer executes anything, and survives only as the shape the legacy
 *     single-group configuration is encoded as on the wire.
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
    Optional<DependencyChangePlan> pendingChanges,
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
      final Optional<DependencyChangePlan> pendingChanges,
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
      final Optional<DependencyChangePlan> pendingChanges,
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
   * Returns a new {@link PartitionGroupConfiguration} after merging this and {@code other}. Does
   * not mutate either operand.
   *
   * <p>If the versions differ, the higher version wins wholesale. If equal, the result merges:
   * {@code members} field-by-field by per-member version (which also carries each broker's
   * operating mode); {@code pendingChanges} by unioning the two copies' completed operations;
   * {@code routingState} by the higher version; and {@code incarnationNumber} via {@link Math#max}.
   *
   * <p>{@code lastChange} is merged by {@link CompletedChange#merge}: every other equal-version
   * field above is resolved by keeping the receiver's copy, safe because it either only ever has
   * one writer or is itself independently mergeable (members, pendingChanges), and {@code
   * lastChange} for a graph change is neither.
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

    final Optional<DependencyChangePlan> mergedChanges =
        Stream.of(pendingChanges, other.pendingChanges)
            .flatMap(Optional::stream)
            .reduce(DependencyChangePlan::merge);

    return new PartitionGroupConfiguration(
        version,
        Math.max(incarnationNumber, other.incarnationNumber),
        mergedMembers,
        mergedRoutingState,
        mergedChanges,
        CompletedChange.merge(lastChange, other.lastChange),
        mergedAvailability);
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
   * Starts a change whose operations carry their own dependencies, bumping the group version as any
   * plan start does.
   *
   * <p>Rejects on {@code pendingChanges.isPresent()} rather than on {@link #hasPendingChanges()}: a
   * graph plan whose last operation has completed but which has not yet been cleared by {@link
   * #completeGraphChangeIfDrained()} still has content this call would destroy — its {@code
   * lastChange} would never be recorded and the members it emptied never pruned — while {@link
   * #hasPendingChanges()}, which reads the plan's content, already reports {@code false} for it.
   *
   * @throws IllegalArgumentException if the group still carries a change plan, drained or not, or
   *     the graph is empty
   */
  public PartitionGroupConfiguration startGraphConfigurationChange(final OperationGraph graph) {
    if (pendingChanges.isPresent()) {
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

  /** Everything the given member may start right now. Empty unless a change is running. */
  public SortedMap<OperationId, PartitionGroupOperation> runnableFor(final MemberId memberId) {
    final SortedMap<OperationId, PartitionGroupOperation> runnable = new TreeMap<>();
    pendingChanges.ifPresent(
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
            .pendingChanges()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expected to record %s, but no change is in progress"
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
   * Finishes the change once every operation has completed: records {@code lastChange}, prunes
   * members left replicating no partition of this group, and bumps the group version so peers
   * overwrite their local copy on merge. Returns {@code this} unchanged otherwise, so every broker
   * may call it on every merge — which is how the change completes without a coordinator.
   */
  public PartitionGroupConfiguration completeGraphChangeIfDrained() {
    final var plan = pendingChanges.orElse(null);
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
   * Returns the <em>desired leader</em> of the given partition within this group (the highest-
   * priority broker that durably participates in its Raft quorum right now - see {@link
   * PartitionState.State#isActiveReplica()}). A learner catching up, or a member on its way out,
   * cannot become leader and must not be reported as the desired one.
   *
   * @param partitionId the partition id, unique only within this group
   * @return the desired leader, or empty if no eligible broker in this group replicates the
   *     partition
   */
  public Optional<MemberId> getDesiredLeader(final int partitionId) {
    return members.entrySet().stream()
        .map(
            entry -> {
              final PartitionState partition = entry.getValue().getPartition(partitionId);
              return partition == null ? null : Map.entry(entry.getKey(), partition);
            })
        .filter(entry -> entry != null && entry.getValue().state().isActiveReplica())
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

  /**
   * Returns the highest-priority member currently eligible to lead this partition: one that durably
   * participates in its Raft quorum (see {@link PartitionState.State#isActiveReplica()}). A learner
   * catching up, or a member on its way out, cannot become leader and must not be reported as the
   * primary.
   */
  public Optional<MemberId> getPrimaryForPartition(final int partitionId) {
    return members.entrySet().stream()
        .filter(entry -> entry.getValue().hasPartition(partitionId))
        .filter(
            entry ->
                Objects.requireNonNull(entry.getValue().getPartition(partitionId))
                    .state()
                    .isActiveReplica())
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
