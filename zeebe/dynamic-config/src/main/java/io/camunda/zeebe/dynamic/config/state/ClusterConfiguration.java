/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.InitializableClusterConfiguration;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Represents the cluster configuration which describes the current active, joining or leaving
 * brokers and the partitions that each broker replicates.
 *
 * @param version - represents the current version of the configuration. It is incremented only by
 *     the coordinator when a new configuration change is triggered.
 * @param members - represents the state of each member
 * @param pendingChanges - keeps track of the ongoing configuration changes. A projection of a live
 *     sub-configuration carries the {@link DependencyChangePlan} that sub-configuration is running,
 *     so a consumer reading it in-process sees the real change — including that several of its
 *     operations may be running at once. It is flattened to a {@link ClusterChangePlan} only where
 *     the wire demands one, when {@code ProtoBufSerializer} encodes the legacy {@code
 *     ClusterTopology} message a broker without the graph model reads (see {@link
 *     ClusterChangePlan#flatten(ChangePlan)}), which is therefore also the only shape a
 *     configuration decoded from that message can hold.
 * @param incarnationNumber - represents the incarnation number of the cluster configuration
 *     <p>This class is immutable. Each mutable methods returns a new instance with the updated
 *     state.
 */
public record ClusterConfiguration(
    long version,
    SortedMap<MemberId, MemberState> members,
    Optional<CompletedChange> lastChange,
    Optional<ChangePlan> pendingChanges,
    Optional<RoutingState> routingState,
    Optional<String> clusterId,
    long incarnationNumber,
    Optional<PartitionDistributorConfig> partitionDistributorConfig)
    implements InitializableClusterConfiguration {

  public static final int INITIAL_VERSION = 1;
  public static final long INITIAL_INCARNATION_NUMBER = 0;
  private static final int UNINITIALIZED_VERSION = -1;

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public ClusterConfiguration(
      final long version,
      final Map<MemberId, MemberState> members,
      final Optional<CompletedChange> lastChange,
      final Optional<ChangePlan> pendingChanges,
      final Optional<RoutingState> routingState,
      final Optional<String> clusterId,
      final long incarnationNumber,
      final Optional<PartitionDistributorConfig> partitionDistributorConfig) {
    this(
        version,
        ImmutableSortedMap.copyOf(members),
        lastChange,
        pendingChanges,
        routingState,
        clusterId,
        incarnationNumber,
        partitionDistributorConfig);
  }

  public ClusterConfiguration {
    if (version < UNINITIALIZED_VERSION) {
      throw new IllegalArgumentException(
          String.format("Version must be >= %d", UNINITIALIZED_VERSION));
    }

    Objects.requireNonNull(members);
    Objects.requireNonNull(lastChange);
    Objects.requireNonNull(pendingChanges);
    Objects.requireNonNull(routingState);
    Objects.requireNonNull(clusterId);
    Objects.requireNonNull(partitionDistributorConfig);
    if (incarnationNumber < 0) {
      throw new IllegalArgumentException("Incarnation number must be >= 0");
    }
  }

  public static ClusterConfiguration uninitialized() {
    return new ClusterConfiguration(
        UNINITIALIZED_VERSION,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        INITIAL_INCARNATION_NUMBER,
        Optional.empty());
  }

  @Override
  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }

  @Override
  public Set<MemberId> getMembers() {
    return members.keySet();
  }

  public static ClusterConfiguration init() {
    return new ClusterConfiguration(
        INITIAL_VERSION,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        INITIAL_INCARNATION_NUMBER,
        Optional.empty());
  }

  public ClusterConfiguration addMember(final MemberId memberId, final MemberState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }

    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder().putAll(members).put(memberId, state).build();
    return new ClusterConfiguration(
        version,
        newMembers,
        lastChange,
        pendingChanges,
        routingState,
        clusterId,
        incarnationNumber,
        partitionDistributorConfig);
  }

  public ClusterConfiguration setRoutingState(final RoutingState updatedRoutingState) {
    return new ClusterConfiguration(
        version,
        members,
        lastChange,
        pendingChanges,
        Optional.of(updatedRoutingState),
        clusterId,
        incarnationNumber,
        partitionDistributorConfig);
  }

  /**
   * Change the partition distribution configuration. This must be done exclusivelyu from the
   * coordinator node.
   */
  public ClusterConfiguration setPartitionDistributorConfig(
      final PartitionDistributorConfig config) {
    if (partitionDistributorConfig.map(cfg -> cfg.equals(config)).orElse(false)) {
      return this;
    }
    return new ClusterConfiguration(
        version + 1,
        members,
        lastChange,
        pendingChanges,
        routingState,
        clusterId,
        incarnationNumber,
        Optional.of(config));
  }

  /**
   * Adds or updates a member in the configuration.
   *
   * <p>memberStateUpdater is invoked with the current state of the member. If the member does not
   * exist, and memberStateUpdater returns a non-null value, then the member is added to the
   * configuration. If the member exists, and the memberStateUpdater returns a null value, then the
   * member is removed.
   *
   * @param memberId id of the member to be updated
   * @param memberStateUpdater transforms the current state of the member to the new state
   * @return the updated ClusterConfiguration
   */
  public ClusterConfiguration updateMember(
      final MemberId memberId, final UnaryOperator<MemberState> memberStateUpdater) {
    final MemberState currentState = members.get(memberId);
    final var updateMemberState = memberStateUpdater.apply(currentState);

    if (Objects.equals(currentState, updateMemberState)) {
      return this;
    }

    final var mapBuilder = ImmutableMap.<MemberId, MemberState>builder();

    if (updateMemberState != null) {
      // Add/Update the member
      mapBuilder.putAll(members).put(memberId, updateMemberState);
    } else {
      // remove memberId from the map
      mapBuilder.putAll(
          members.entrySet().stream()
              .filter(entry -> !entry.getKey().equals(memberId))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    final var newMembers = mapBuilder.buildKeepingLast();
    return new ClusterConfiguration(
        version,
        newMembers,
        lastChange,
        pendingChanges,
        routingState,
        clusterId,
        incarnationNumber,
        partitionDistributorConfig);
  }

  /**
   * Returns a new ClusterConfiguration after merging this and other. This doesn't overwrite this or
   * other. If this.version == other.version then the new ClusterConfiguration contains merged
   * members and changes. Otherwise, it returns the one with the highest version.
   *
   * @param other ClusterConfiguration to merge
   * @return merged ClusterConfiguration
   * @throws IllegalStateException when the two configurations cannot be merged
   */
  public ClusterConfiguration merge(final ClusterConfiguration other) {
    if (version > other.version) {
      return this;
    } else if (other.version > version) {
      return other;
    } else {
      final var mergedMembers =
          Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue, MemberState::merge));

      final Optional<ChangePlan> mergedChanges =
          Stream.of(pendingChanges, other.pendingChanges)
              .flatMap(Optional::stream)
              .reduce(ClusterConfiguration::mergePlans);

      final var mergedRoutingState =
          Stream.of(routingState, other.routingState)
              .flatMap(Optional::stream)
              .reduce(RoutingState::merge);

      final var mergedDistributorConfig =
          Stream.of(partitionDistributorConfig, other.partitionDistributorConfig)
              .flatMap(Optional::stream)
              .reduce(PartitionDistributorConfig::merge);

      return new ClusterConfiguration(
          version,
          ImmutableMap.copyOf(mergedMembers),
          lastChange,
          mergedChanges,
          mergedRoutingState,
          clusterId,
          Math.max(incarnationNumber, other.incarnationNumber()),
          mergedDistributorConfig);
    }
  }

  /**
   * Merges two copies of the same change, each in its own model: two queues by version, two graphs
   * by unioning their completions.
   *
   * <p>The two models never meet here. Every side of this merge is either a configuration decoded
   * from the legacy {@code ClusterTopology} message — whose plan is a queue by construction, since
   * that is the only shape the message can carry — or a projection of one sub-configuration, and a
   * projection is never merged with anything: {@code CurrentClusterConfiguration} merges the
   * sub-configurations themselves and projects afterwards. A mixed pair would therefore mean two
   * unrelated changes were being merged as one, which is rejected rather than resolved by taking a
   * side.
   */
  private static ChangePlan mergePlans(final ChangePlan plan, final ChangePlan other) {
    if (plan instanceof final ClusterChangePlan queue
        && other instanceof final ClusterChangePlan otherQueue) {
      return queue.merge(otherQueue);
    }
    if (plan instanceof final DependencyChangePlan graph
        && other instanceof final DependencyChangePlan otherGraph) {
      return graph.merge(otherGraph);
    }
    throw new IllegalStateException(
        "Cannot merge a queue change with a dependency-graph change: %s vs %s"
            .formatted(plan, other));
  }

  public boolean hasPendingChanges() {
    return pendingChanges.isPresent() && pendingChanges.orElseThrow().hasPendingChanges();
  }

  /**
   * Returns true if this configuration was produced by a restore: {@code pendingChanges} is
   * present, marked as a restore plan, and contains exactly one pending operation which is an
   * {@link UpdateRoutingState}.
   *
   * <p>Only a queue can answer this. The restore sentinel id lives on {@link ClusterChangePlan},
   * which is the shape {@code RestoreManager} writes into the configuration file it leaves for the
   * restored broker. A partition group running a restore as a graph gives its plan an ordinary
   * version-derived id and marks the restore on the enclosing {@link PhasedChangePlan} instead,
   * which a single-group projection cannot see — ask {@link
   * CurrentClusterConfiguration#isAfterRestore()} on that path.
   */
  public boolean isAfterRestore() {
    return pendingChanges
        .filter(ClusterChangePlan.class::isInstance)
        .map(ClusterChangePlan.class::cast)
        .filter(ClusterChangePlan::isRestore)
        .map(ClusterChangePlan::pendingOperations)
        .filter(ops -> ops.size() == 1 && ops.get(0) instanceof UpdateRoutingState)
        .isPresent();
  }

  /**
   * @return true if All brokers are not zoned and the partition distribution config is not
   *     ZoneAware, false otherwise.
   */
  public boolean isUnzoned() {
    return members().keySet().stream().allMatch(m -> m.zone() == null)
        && !partitionDistributorConfig.map(ZoneAwareConfig.class::isInstance).orElse(false);
  }

  /**
   * @return true if at least one broker is zoned, indicating that the cluster is transitioning from
   *     an "unzoned" cluster to a zoned one.
   */
  public boolean isPartiallyZoneAware() {
    final var membersCount = members.size();
    if (membersCount == 0) {
      return false;
    }
    final var zonedCount = members().keySet().stream().filter(m -> m.zone() != null).count();
    final var distributionIsNotZoned =
        partitionDistributorConfig.filter(ZoneAwareConfig.class::isInstance).isEmpty();
    return (zonedCount > 0 && zonedCount < membersCount) // not all brokers are zoned
        || (zonedCount == membersCount && distributionIsNotZoned); // are all zoned, config isn't
  }

  /**
   * @return true if all brokers are zone aware and the partition distribution config is ZoneAware
   */
  public boolean isFullyZoneAware() {
    return members().keySet().stream().allMatch(m -> m.zone() != null)
        && partitionDistributorConfig.map(ZoneAwareConfig.class::isInstance).orElse(false);
  }

  public boolean hasMember(final MemberId memberId) {
    return members().containsKey(memberId);
  }

  public MemberState getMember(final MemberId memberId) {
    return members().get(memberId);
  }

  public int clusterSize() {
    return (int)
        members.entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().state() != State.LEFT
                        && entry.getValue().state() != State.UNINITIALIZED)
            .count();
  }

  public boolean hasPartition(final int partitionId) {
    return members.values().stream().anyMatch(member -> member.hasPartition(partitionId));
  }

  public PartitionDistributor partitionDistributor() {
    return partitionDistributorConfig()
        .map(PartitionDistributorConfig::toDistributor)
        .orElseGet(RoundRobinPartitionDistributor::new);
  }

  public int partitionCount() {
    return (int)
        members.values().stream().flatMap(m -> m.partitions().keySet().stream()).distinct().count();
  }

  public IntStream partitionIds() {
    return members.values().stream()
        .flatMapToInt(m -> m.partitions().keySet().stream().mapToInt(i -> i))
        .sorted()
        .distinct();
  }

  public Integer minReplicationFactor() {
    // return minimum replication factor. During a configuration change, replication factor might
    // increase temporarily.
    return members.values().stream()
        .filter(entry -> entry.state() != State.LEFT && entry.state() != State.UNINITIALIZED)
        .flatMap(m -> m.partitions().entrySet().stream())
        .collect(Collectors.groupingBy(Entry::getKey, Collectors.counting()))
        .values()
        .stream()
        .reduce(Math::min)
        .map(Long::intValue)
        .orElse(0);
  }

  /**
   * Returns the highest-priority member currently eligible to lead a given partition: one whose
   * member lifecycle is neither {@link State#LEFT} nor {@link State#UNINITIALIZED}, and whose
   * partition state durably participates in the Raft quorum right now (see {@link
   * PartitionState.State#isActiveReplica()}) - a learner catching up, or a member on its way out,
   * cannot become leader and must not be returned as the primary.
   *
   * @param partitionId the partition ID
   * @return Optional containing the MemberId of the member with highest priority, or empty if no
   *     eligible member replicates the partition
   */
  public Optional<MemberId> getPrimaryMemberForPartition(final int partitionId) {
    return members.entrySet().stream()
        .filter(entry -> entry.getValue().hasPartition(partitionId))
        .filter(entry -> entry.getValue().state() != State.LEFT)
        .filter(entry -> entry.getValue().state() != State.UNINITIALIZED)
        .filter(entry -> entry.getValue().getPartition(partitionId) != null)
        .filter(entry -> entry.getValue().getPartition(partitionId).state().isActiveReplica())
        .max(
            (e1, e2) ->
                Integer.compare(
                    e1.getValue().getPartition(partitionId).priority(),
                    e2.getValue().getPartition(partitionId).priority()))
        .map(Entry::getKey);
  }

  /**
   * Cancel any pending changes and return a new configuration with the already applied changes.
   *
   * @note This is a dangerous operation that can lead to an inconsistent cluster configuration.
   *     This should be only called as a last resort when the configuration change is stuck and not
   *     able to make progress on its own.
   * @return a new configuration with the already applied changes and no pending changes.
   */
  public ClusterConfiguration cancelPendingChanges() {
    if (hasPendingChanges()) {
      final var cancelledChange = pendingChanges.orElseThrow().cancel();
      // Increment version by 2 to avoid conflicts with other members who are applying the change.
      // A conflict would not happen if the cancel is only called when the operation is truly stuck.
      final var newVersion = version + 2;
      return new ClusterConfiguration(
          newVersion,
          members,
          Optional.of(cancelledChange),
          Optional.empty(),
          routingState,
          clusterId,
          incarnationNumber,
          partitionDistributorConfig);
    } else {
      return this;
    }
  }

  /** Returns a new builder for creating ClusterConfiguration instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for creating ClusterConfiguration instances. */
  public static class Builder {
    private long version = INITIAL_VERSION;
    private Map<MemberId, MemberState> members = Map.of();
    private Optional<CompletedChange> lastChange = Optional.empty();
    private Optional<ChangePlan> pendingChanges = Optional.empty();
    private Optional<RoutingState> routingState = Optional.empty();
    private Optional<String> clusterId = Optional.empty();
    private long incarnationNumber = INITIAL_INCARNATION_NUMBER;
    private Optional<PartitionDistributorConfig> partitionDistributorConfig = Optional.empty();

    /**
     * Copies all properties from the given ClusterConfiguration.
     *
     * @param config the ClusterConfiguration to copy from
     * @return this builder
     */
    public Builder from(final ClusterConfiguration config) {
      version = config.version;
      members = config.members;
      lastChange = config.lastChange;
      pendingChanges = config.pendingChanges;
      routingState = config.routingState;
      clusterId = config.clusterId;
      incarnationNumber = config.incarnationNumber;
      partitionDistributorConfig = config.partitionDistributorConfig;
      return this;
    }

    /**
     * Sets the version.
     *
     * @param version the version
     * @return this builder
     */
    public Builder version(final long version) {
      this.version = version;
      return this;
    }

    /**
     * Sets the members.
     *
     * @param members the members map
     * @return this builder
     */
    public Builder members(final Map<MemberId, MemberState> members) {
      this.members = members;
      return this;
    }

    /**
     * Sets the last change.
     *
     * @param lastChange the last completed change
     * @return this builder
     */
    public Builder lastChange(final Optional<CompletedChange> lastChange) {
      this.lastChange = lastChange;
      return this;
    }

    /**
     * Sets the pending changes.
     *
     * @param pendingChanges the pending changes
     * @return this builder
     */
    public Builder pendingChanges(final Optional<ChangePlan> pendingChanges) {
      this.pendingChanges = pendingChanges;
      return this;
    }

    /**
     * Sets the routing state.
     *
     * @param routingState the routing state
     * @return this builder
     */
    public Builder routingState(final Optional<RoutingState> routingState) {
      this.routingState = routingState;
      return this;
    }

    /**
     * Sets the cluster ID.
     *
     * @param clusterId the cluster ID
     * @return this builder
     */
    public Builder clusterId(final Optional<String> clusterId) {
      this.clusterId = clusterId;
      return this;
    }

    /**
     * Sets the incarnation number.
     *
     * @param incarnationNumber the incarnation number
     * @return this builder
     */
    public Builder incarnationNumber(final long incarnationNumber) {
      this.incarnationNumber = incarnationNumber;
      return this;
    }

    /**
     * Sets the partition distributor config.
     *
     * @param partitionDistributorConfig the partition distributor config
     * @return this builder
     */
    public Builder partitionDistributorConfig(
        final Optional<PartitionDistributorConfig> partitionDistributorConfig) {
      this.partitionDistributorConfig = partitionDistributorConfig;
      return this;
    }

    /**
     * Builds and returns a new ClusterConfiguration instance.
     *
     * @return the new ClusterConfiguration
     */
    public ClusterConfiguration build() {
      return new ClusterConfiguration(
          version,
          members,
          lastChange,
          pendingChanges,
          routingState,
          clusterId,
          incarnationNumber,
          partitionDistributorConfig);
    }
  }
}
