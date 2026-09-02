/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.InitializableClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.OperationGraph.PlannedOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Top-level wrapper for the multi-partition-group cluster configuration model. Holds all cluster
 * state: the cluster-wide {@link GlobalConfiguration} (broker lifecycle and cluster-level config),
 * the per-group {@link PartitionGroupConfiguration}s keyed by group id, and the {@link
 * PhasedChangeState} for operations that span both.
 *
 * <p>{@code version} is always {@link #INITIAL_VERSION} and is reserved for a potential future
 * root-level merge fast path — it is <em>not</em> used in merge decisions today. Merge is always a
 * structural merge that delegates to the sub-configurations (which carry their own versions).
 *
 * <p>This class is immutable; every mutating method returns a new instance.
 *
 * @param version reserved, always {@link #INITIAL_VERSION}
 * @param globalConfiguration cluster-wide broker lifecycle and configuration
 * @param partitionGroups per-group partition configuration, keyed by group id
 * @param phasedChangeState the lifecycle state of the cluster-spanning change plan
 */
@NullMarked
public record CurrentClusterConfiguration(
    long version,
    GlobalConfiguration globalConfiguration,
    Map<String, PartitionGroupConfiguration> partitionGroups,
    PhasedChangeState phasedChangeState)
    implements InitializableClusterConfiguration {

  public static final long INITIAL_VERSION = 0;
  public static final String DEFAULT_GROUP = "default";

  public CurrentClusterConfiguration {
    Objects.requireNonNull(globalConfiguration, "globalConfiguration must not be null");
    Objects.requireNonNull(partitionGroups, "partitionGroups must not be null");
    Objects.requireNonNull(phasedChangeState, "phasedChangeState must not be null");
    partitionGroups = Map.copyOf(partitionGroups);
  }

  public static CurrentClusterConfiguration uninitialized() {
    return new CurrentClusterConfiguration(
        INITIAL_VERSION, GlobalConfiguration.uninitialized(), Map.of(), PhasedChangeState.empty());
  }

  @Override
  public boolean isUninitialized() {
    return globalConfiguration.isUninitialized();
  }

  @Override
  public Set<MemberId> getMembers() {
    return globalConfiguration.members().keySet();
  }

  public int getClusterSize() {
    return liveMembers().size();
  }

  /**
   * The members eligible to host partitions: every broker known to the cluster except those in
   * {@link BrokerState.State#LEFT} or {@link BrokerState.State#UNINITIALIZED}. Using the raw {@link
   * #getMembers()} instead would let a decommissioned or not-yet-joined broker be picked as the
   * target of a partition bootstrap or join, stalling the change once that operation can never
   * complete.
   */
  public Set<MemberId> liveMembers() {
    return globalConfiguration.members().entrySet().stream()
        .filter(
            entry ->
                entry.getValue().state() != BrokerState.State.LEFT
                    && entry.getValue().state() != BrokerState.State.UNINITIALIZED)
        .map(Entry::getKey)
        .collect(Collectors.toSet());
  }

  public Optional<String> clusterId() {
    return globalConfiguration.clusterId();
  }

  /**
   * Whether the cluster does not use zone awareness at all: no broker is assigned to a zone and the
   * partition distributor is not zone-aware. A cluster where only some of the two holds is
   * mid-migration between the two modes rather than unzoned.
   *
   * <p>Both are global state, so this reads the same fields {@link
   * ClusterConfiguration#isUnzoned()} does — the projection through {@link #toLegacy(String)} that
   * method needs is not.
   */
  public boolean isUnzoned() {
    return globalConfiguration.members().keySet().stream().allMatch(member -> member.zone() == null)
        && globalConfiguration
            .partitionDistributorConfig()
            .filter(ZoneAwareConfig.class::isInstance)
            .isEmpty();
  }

  /**
   * Whether the cluster uses zone awareness throughout: every broker is assigned to a zone and the
   * partition distributor is zone-aware. A cluster where only one of the two holds is mid-migration
   * between the two modes rather than fully zone-aware.
   *
   * <p>Both are global state, so this reads the same fields {@link
   * ClusterConfiguration#isFullyZoneAware()} does — the projection through {@link
   * #toLegacy(String)})} that method needs is not.
   */
  public boolean isFullyZoneAware() {
    return globalConfiguration.members().keySet().stream().allMatch(member -> member.zone() != null)
        && globalConfiguration
            .partitionDistributorConfig()
            .filter(ZoneAwareConfig.class::isInstance)
            .isPresent();
  }

  /**
   * Whether the cluster is between the two modes: some but not all brokers are assigned to a zone,
   * or every broker is but the partition distributor is not zone-aware yet.
   *
   * <p>Both are global state, so this reads the same fields {@link
   * ClusterConfiguration#isPartiallyZoneAware()} does — the projection through {@link
   * #toLegacy(String)})} that method needs is not.
   */
  public boolean isPartiallyZoneAware() {
    final var members = globalConfiguration.members().keySet();
    if (members.isEmpty()) {
      return false;
    }
    final var zonedCount = members.stream().filter(member -> member.zone() != null).count();
    final var distributorIsNotZoneAware =
        globalConfiguration
            .partitionDistributorConfig()
            .filter(ZoneAwareConfig.class::isInstance)
            .isEmpty();
    return (zonedCount > 0 && zonedCount < members.size())
        || (zonedCount == members.size() && distributorIsNotZoneAware);
  }

  /**
   * The lowest number of replicas any partition of any physical tenant currently has. Taken across
   * every group because the replication factor is a cluster-wide setting: a request that has to
   * match what the cluster runs today has to match it for every tenant, not only the default one.
   *
   * <p>Read as the minimum rather than a configured value, mirroring {@link
   * ClusterConfiguration#minReplicationFactor()}: during a configuration change a partition can
   * temporarily hold more replicas than the cluster is configured for.
   *
   * <p>Answers 0 for a cluster that runs no partitions at all, which is what makes an equality
   * check against it fail rather than pass. {@code PartitionGroupScalingPhases} asks a different
   * question — the replication factor to plan with when the request names none — and defaults to 1
   * there for the same reason.
   */
  public int minReplicationFactor() {
    final var countingBrokers = liveMembers();
    return partitionGroups.values().stream()
        .flatMap(
            group ->
                group.members().entrySet().stream()
                    .filter(member -> countingBrokers.contains(member.getKey()))
                    .flatMap(member -> member.getValue().partitions().keySet().stream())
                    .collect(
                        Collectors.groupingBy(partitionId -> partitionId, Collectors.counting()))
                    .values()
                    .stream())
        .mapToInt(Long::intValue)
        .min()
        .orElse(0);
  }

  /**
   * Creates an empty configuration with an initial global configuration and no partition groups.
   */
  public static CurrentClusterConfiguration init() {
    return new CurrentClusterConfiguration(
        INITIAL_VERSION, GlobalConfiguration.init(), Map.of(), PhasedChangeState.empty());
  }

  /**
   * Migration factory: converts a legacy {@link ClusterConfiguration} (single partition group) into
   * the new model. Broker lifecycle state is extracted into {@link GlobalConfiguration}; partition
   * assignment is extracted into the {@link #DEFAULT_GROUP} partition group.
   *
   * <p>Field placement:
   *
   * <ul>
   *   <li>{@code clusterId} and {@code partitionDistributorConfig} → {@link GlobalConfiguration}.
   *   <li>{@code routingState} and {@code incarnationNumber} → the default {@link
   *       PartitionGroupConfiguration}.
   *   <li>{@code pendingChanges} → the {@link PhasedChangeState} pending plan. The legacy plan
   *       mixes {@link GlobalChangeOperation}s and {@link PartitionGroupOperation}s in one flat
   *       list; those cannot all live on the default group. They are re-expressed as phases,
   *       preserving order: each maximal run of consecutive operations of the same kind becomes one
   *       phase (see {@link #toPhases(List)}).
   *   <li>{@code lastChange} → the {@link PhasedChangeState} last completed change. Its status must
   *       be terminal (COMPLETED / FAILED / CANCELLED); an {@code IN_PROGRESS} legacy last change
   *       is rejected.
   *   <li>every member appears in {@link GlobalConfiguration}; a member appears in the default
   *       group only if it currently replicates at least one partition.
   *   <li>a legacy {@code RECOVERING} member maps to a lifecycle {@link BrokerState.State#ACTIVE}
   *       broker whose default-group {@link BrokerPartitionState} is in {@link Mode#RECOVERING},
   *       since recovery is now per-group.
   * </ul>
   *
   * <p>This is a pure, side-effect-free conversion: the returned pending plan (if any) is built at
   * phase 0 but is <em>not</em> activated into the default group — the default group's own {@code
   * pendingChanges} stays empty. Activating phase 0 (via {@link #applyPhase}) is deliberately left
   * to the caller, because this factory is invoked both for genuine one-time migrations (e.g.
   * {@code PersistedCurrentClusterConfiguration} upgrading an on-disk v1 file, exactly once per
   * broker) and for repeated, read-only re-derivations of a legacy view (e.g. {@code
   * BrokerTopologyManagerImpl#onClusterConfigurationUpdated(ClusterConfiguration)}, invoked on
   * every gossip update). Auto-activating here would re-run {@code startConfigurationChange} on a
   * freshly-built (and therefore never-"pending") default group on every such repeated call,
   * endlessly restarting an already in-progress plan from scratch. Callers that are performing a
   * genuine one-time migration should call {@link #activatePendingPhase()} explicitly afterwards.
   *
   * @throws IllegalStateException if the legacy {@code lastChange} has {@code IN_PROGRESS} status
   */
  public static CurrentClusterConfiguration fromLegacy(final ClusterConfiguration legacy) {
    final long version = legacy.version();

    final Map<MemberId, BrokerState> brokerStates =
        legacy.members().entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> toBrokerState(e.getValue())));

    final var globalConfiguration =
        new GlobalConfiguration(
            version,
            legacy.clusterId(),
            brokerStates,
            legacy.partitionDistributorConfig(),
            Optional.empty(),
            Optional.empty());

    final Map<MemberId, BrokerPartitionState> partitionStates =
        legacy.members().entrySet().stream()
            .filter(e -> !e.getValue().partitions().isEmpty())
            .collect(Collectors.toMap(Entry::getKey, e -> toBrokerPartitionState(e.getValue())));

    final var defaultGroup =
        new PartitionGroupConfiguration(
            version,
            legacy.incarnationNumber(),
            partitionStates,
            legacy.routingState(),
            Optional.empty(),
            Optional.empty());

    final PhasedChangeState phasedChangeState = toPhasedChangeState(legacy);
    return new CurrentClusterConfiguration(
        INITIAL_VERSION,
        globalConfiguration,
        Map.of(DEFAULT_GROUP, defaultGroup),
        phasedChangeState);
  }

  /**
   * Activates the current pending plan's phase (see {@link #applyPhase}) if one is pending,
   * otherwise returns this configuration unchanged. Mirrors what {@link #initPlan(List)} does for a
   * freshly-started plan; intended for one-time migration call sites (see {@link
   * #fromLegacy(ClusterConfiguration)}) that need to take over driving an already-pending plan
   * rather than starting a new one.
   *
   * @throws IllegalStateException if the pending phase targets a sub-configuration that already has
   *     a change in progress
   */
  public CurrentClusterConfiguration activatePendingPhase() {
    var result = this;
    for (final var plan : phasedChangeState.pending().values()) {
      result = result.applyPhase(plan);
    }
    return result;
  }

  /**
   * Projects this multi-group configuration back to a legacy single-group {@link
   * ClusterConfiguration} representing the named partition group. This is the inverse of {@link
   * #fromLegacy(ClusterConfiguration)}.
   *
   * <p>Each member combines its cluster-wide lifecycle state (from {@link #globalConfiguration})
   * with its partition assignment in {@code groupId} (from {@code partitionGroups[groupId]}); a
   * member whose operating mode in that group is {@link Mode#RECOVERING} is projected to the legacy
   * {@link MemberState.State#RECOVERING} state. {@code routingState} and {@code incarnationNumber}
   * come from that group; {@code clusterId} and {@code partitionDistributorConfig} from the global
   * configuration.
   *
   * <p>The pending/last change is a best-effort projection: {@code pendingChanges} reflects
   * whichever sub-config currently has an active plan (global first, then the named group), and
   * {@code lastChange} is derived from the {@link PhasedChangeState} — which is cluster-wide, not
   * per-group, so the projected {@code lastChange} is the same regardless of which group is
   * requested. This is intended: the phased change plan spans the whole cluster (see {@link
   * PhasedChangeState}), so there is no group-scoped completed-change history to derive it from
   * instead. Cross-sub-config change details cannot be represented losslessly in the flat legacy
   * plan, so this projection is intended for display and equivalence checks, not for driving legacy
   * change execution.
   *
   * <p>Uninitialized is projected explicitly: {@link #isUninitialized()} is driven by {@code
   * globalConfiguration}'s own uninitialized sentinel version, which is {@code 0} — distinct from
   * the legacy {@link ClusterConfiguration}'s sentinel version of {@code -1}. Deriving the legacy
   * version as {@code Math.max(globalConfiguration.version(), group.version())} would therefore
   * never equal the legacy sentinel, so callers of {@code ClusterConfiguration#isUninitialized()}
   * on the projection could never observe {@code true}.
   *
   * @param groupId the partition group to project; looked up with {@link
   *     PartitionGroupConfiguration#empty(long)} as the fallback when absent
   */
  public ClusterConfiguration toLegacy(final String groupId) {
    if (isUninitialized()) {
      return ClusterConfiguration.uninitialized();
    }

    final PartitionGroupConfiguration group =
        partitionGroups.getOrDefault(groupId, PartitionGroupConfiguration.empty(version));

    final Map<MemberId, MemberState> members = new HashMap<>();
    globalConfiguration
        .members()
        .forEach(
            (memberId, brokerState) ->
                members.put(memberId, toLegacyMemberState(brokerState, group.getMember(memberId))));

    // The projection carries whichever model the sub-configuration it comes from is running, so a
    // reader in this process sees the real change — including that several of its operations may be
    // running at once. It is flattened to a queue only where the wire demands one, when the legacy
    // ClusterTopology message is encoded (see ClusterChangePlan#flatten).
    final Optional<ChangePlan> pendingChanges =
        globalConfiguration
            .pendingChanges()
            .<ChangePlan>map(plan -> plan)
            .filter(ChangePlan::hasPendingChanges)
            .or(
                () ->
                    group
                        .pendingChanges()
                        .<ChangePlan>map(plan -> plan)
                        .filter(ChangePlan::hasPendingChanges));

    final Optional<CompletedChange> lastChange =
        phasedChangeState.lastChange().map(CurrentClusterConfiguration::toLegacyCompletedChange);

    return ClusterConfiguration.builder()
        .version(Math.max(globalConfiguration.version(), group.version()))
        .members(members)
        .lastChange(lastChange)
        .pendingChanges(pendingChanges)
        .routingState(group.routingState())
        .clusterId(globalConfiguration.clusterId())
        .incarnationNumber(group.incarnationNumber())
        .partitionDistributorConfig(globalConfiguration.partitionDistributorConfig())
        .build();
  }

  /**
   * Delegates to {@link #toLegacy(String)} for {@link #DEFAULT_GROUP}. Kept as the stable entry
   * point for the many call sites that only ever cared about the single default group, from before
   * this class supported more than one partition group.
   */
  public ClusterConfiguration toLegacyDefault() {
    return toLegacy(DEFAULT_GROUP);
  }

  private static MemberState toLegacyMemberState(
      final BrokerState brokerState, final @Nullable BrokerPartitionState partitionState) {
    final Map<Integer, PartitionState> partitions =
        partitionState != null ? partitionState.partitions() : Map.of();
    final MemberState.State state =
        partitionState != null && partitionState.mode() == Mode.RECOVERING
            ? MemberState.State.RECOVERING
            : toLegacyLifecycleState(brokerState.state());
    final long version =
        partitionState != null
            ? Math.max(brokerState.version(), partitionState.version())
            : brokerState.version();
    final Instant lastUpdated =
        partitionState != null && partitionState.lastUpdated().isAfter(brokerState.lastUpdated())
            ? partitionState.lastUpdated()
            : brokerState.lastUpdated();
    return new MemberState(version, lastUpdated, state, partitions);
  }

  private static MemberState.State toLegacyLifecycleState(final BrokerState.State state) {
    return switch (state) {
      case UNINITIALIZED -> MemberState.State.UNINITIALIZED;
      case JOINING -> MemberState.State.JOINING;
      case ACTIVE -> MemberState.State.ACTIVE;
      case LEAVING -> MemberState.State.LEAVING;
      case LEFT -> MemberState.State.LEFT;
    };
  }

  private static CompletedChange toLegacyCompletedChange(final CompletedPhasedChange change) {
    final Status status =
        switch (change.status()) {
          case COMPLETED -> Status.COMPLETED;
          case FAILED -> Status.FAILED;
          case CANCELLED -> Status.CANCELLED;
        };
    return new CompletedChange(change.id(), status, change.startedAt(), change.completedAt());
  }

  /**
   * Returns a new configuration after merging this and {@code other}. This is always a structural
   * merge — the top-level {@code version} is ignored. {@code globalConfiguration} and each
   * partition group are merged by delegating to their own {@code merge}; partition-group keys use
   * union semantics (a group present on only one side is adopted directly); the {@code
   * phasedChangeState} is merged by {@link PhasedChangeState#merge(PhasedChangeState)}.
   */
  public CurrentClusterConfiguration merge(final CurrentClusterConfiguration other) {
    if (other.version() != version) {
      throw new IllegalStateException(
          String.format(
              "Cannot merge cluster configurations with different versions: this=%d, other=%d",
              version, other.version()));
    }

    final var mergedGlobal = globalConfiguration.merge(other.globalConfiguration);

    final Map<String, PartitionGroupConfiguration> mergedGroups = new HashMap<>(partitionGroups);
    other.partitionGroups.forEach(
        (groupId, group) -> mergedGroups.merge(groupId, group, PartitionGroupConfiguration::merge));

    final var mergedPhasedChangeState = phasedChangeState.merge(other.phasedChangeState);

    return new CurrentClusterConfiguration(
        version, mergedGlobal, mergedGroups, mergedPhasedChangeState);
  }

  /**
   * Applies {@code updater} to the global configuration. Returns {@code this} if the global
   * configuration is unchanged.
   */
  public CurrentClusterConfiguration updateGlobalConfiguration(
      final UnaryOperator<GlobalConfiguration> updater) {
    final var updated = updater.apply(globalConfiguration);
    if (updated.equals(globalConfiguration)) {
      return this;
    }
    return new CurrentClusterConfiguration(version, updated, partitionGroups, phasedChangeState);
  }

  /**
   * Applies {@code updater} to the named partition group. Returns {@code this} if the group is
   * unchanged.
   *
   * @throws IllegalStateException if the group does not exist
   */
  public CurrentClusterConfiguration updatePartitionGroupConfig(
      final String groupId, final UnaryOperator<PartitionGroupConfiguration> updater) {
    final PartitionGroupConfiguration current = partitionGroups.get(groupId);
    if (current == null) {
      throw new IllegalStateException(
          String.format("Expected to update partition group %s, but it does not exist", groupId));
    }
    final var updated = updater.apply(current);
    if (updated.equals(current)) {
      return this;
    }
    final var updatedGroups = new HashMap<>(partitionGroups);
    updatedGroups.put(groupId, updated);
    return new CurrentClusterConfiguration(
        version, globalConfiguration, updatedGroups, phasedChangeState);
  }

  /**
   * Initializes a new phased change plan from {@code phases} and activates its first phase (phase
   * 0) into the sub-configurations. The plan id is derived inside {@link PhasedChangeState} from
   * the last completed change, so callers never supply an id (see Amendment 1 of the solution
   * spec).
   *
   * <p>Consecutive phases must not target the same sub-configuration: activating a phase starts a
   * configuration change on the affected sub-config, and a later phase targeting the same
   * sub-config can only be activated once that change has been fully advanced (drained) — otherwise
   * {@link #activateNextPhase()} throws because a change is still in progress.
   *
   * @throws IllegalArgumentException if {@code phases} is empty
   * @throws IllegalStateException if a plan is already pending
   */
  public CurrentClusterConfiguration initPlan(final List<Phase> phases) {
    if (phases.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to init a plan with at least one phase, but the phase list is empty");
    }

    final long newId = phasedChangeState.nextId();
    final var newState = phasedChangeState.initPlan(phases);
    final var plan = Objects.requireNonNull(newState.pending().get(newId));
    return withPhasedChangeState(newState).applyPhase(plan);
  }

  /**
   * Advances the pending plan {@code planId} to the next phase and activates that phase into the
   * sub-configurations.
   *
   * <p>The next phase must not target a sub-configuration that still has the previous phase's
   * change in progress (see {@link #initPlan(List)}); if it does, this throws because a
   * configuration change is already in progress on that sub-config.
   *
   * @throws IllegalStateException if no plan {@code planId} is pending, or it is already on its
   *     last phase
   * @apiNote Unconditional and unguarded — it throws if the precondition doesn't hold, rather than
   *     re-validating it, so it is only safe against a config snapshot no concurrent trigger can
   *     act on (e.g. a single-threaded dry-run simulation). A caller driving a live plan against a
   *     config that another trigger could concurrently advance must use {@link
   *     #tryActivateNextPhase(long, int)} instead.
   */
  public CurrentClusterConfiguration activateNextPhase(final long planId) {
    final var plan = phasedChangeState.pending().get(planId);
    if (plan == null) {
      throw new IllegalStateException(
          "Cannot activate the next phase: no plan '%d' is pending".formatted(planId));
    }
    if (!plan.hasNextPhase()) {
      throw new IllegalStateException(
          "Cannot activate the next phase: the plan is already on its last phase");
    }
    final var advanced = plan.withNextPhase();
    final var newState = phasedChangeState.withAdvancedPlan(advanced);
    return withPhasedChangeState(newState).applyPhase(advanced);
  }

  /**
   * Like {@link #activateNextPhase(long)}, but re-validates {@code expectedPhaseIndex} against the
   * plan's actual current phase index first, no-op'ing (returning {@code this}) instead of mutating
   * if it no longer matches — i.e. the plan is no longer pending, or some other trigger already
   * advanced it past the phase this call was decided against. Intended for callers that computed
   * "phase complete, advance it" from a config snapshot that may since have gone stale (e.g. two
   * independent triggers both observing the same completed phase): re-validating at execution time,
   * rather than throwing, turns a stale/duplicate advance into a harmless no-op instead of an
   * exception that would otherwise be retried forever against a precondition that can never become
   * true again. The {@code try} prefix marks it as the safe-by-default choice for any caller
   * driving a live plan, as opposed to {@link #activateNextPhase(long)}.
   */
  public CurrentClusterConfiguration tryActivateNextPhase(
      final long planId, final int expectedPhaseIndex) {
    final var plan = phasedChangeState.pending().get(planId);
    if (plan == null || plan.currentPhaseIndex() != expectedPhaseIndex) {
      return this;
    }
    return activateNextPhase(planId);
  }

  /**
   * Completes the pending plan {@code planId} with the given terminal status, moving it into {@code
   * history}. Sub-configuration changes activated by the plan are left untouched.
   *
   * @throws IllegalStateException if plan {@code planId} is not pending
   * @apiNote Unconditional and unguarded — it throws if the plan isn't pending, rather than
   *     re-validating, so it is only safe against a config snapshot no concurrent trigger can act
   *     on (e.g. a single-threaded dry-run simulation, or a caller that already confirmed the plan
   *     is pending under the same single-writer authority that owns it). A caller driving a live
   *     plan against a config that another trigger could concurrently complete must use {@link
   *     #tryCompletePlan(long, int, PhasedChangePlanStatus, int)} instead.
   */
  public CurrentClusterConfiguration completePlan(
      final long planId, final PhasedChangePlanStatus status) {
    return completePlan(planId, status, PhasedChangeState.DEFAULT_HISTORY_LIMIT);
  }

  /** Same as {@link #completePlan(long, PhasedChangePlanStatus)}, with an explicit history cap. */
  public CurrentClusterConfiguration completePlan(
      final long planId, final PhasedChangePlanStatus status, final int historyLimit) {
    return withPhasedChangeState(phasedChangeState.completePlan(planId, status, historyLimit));
  }

  /**
   * Like {@link #completePlan(long, PhasedChangePlanStatus, int)}, but re-validates {@code
   * expectedPhaseIndex} first, no-op'ing (returning {@code this}) instead of throwing if the plan
   * is no longer pending or has moved past that phase. See {@link #tryActivateNextPhase(long, int)}
   * for why re-validation, not an exception, is the correct response to a stale/duplicate
   * completion trigger. The {@code try} prefix marks it as the safe-by-default choice for any
   * caller driving a live plan, as opposed to the unguarded {@link #completePlan(long,
   * PhasedChangePlanStatus, int)}.
   */
  public CurrentClusterConfiguration tryCompletePlan(
      final long planId,
      final int expectedPhaseIndex,
      final PhasedChangePlanStatus status,
      final int historyLimit) {
    final var plan = phasedChangeState.pending().get(planId);
    if (plan == null || plan.currentPhaseIndex() != expectedPhaseIndex) {
      return this;
    }
    return completePlan(planId, status, historyLimit);
  }

  /**
   * Returns {@code true} if plan {@code planId}'s current phase has fully drained: every
   * sub-configuration it targets (the global configuration for a {@link GlobalPhase}, or each named
   * partition group for a {@link PartitionGroupPhase}) has no pending changes left.
   *
   * <p>"No pending changes left" means the sub-configuration's {@code pendingChanges} is gone
   * entirely, not merely that every operation in it has completed. Those two are different moments:
   * {@code hasPendingChanges()} reads the plan's own content and goes {@code false} the instant the
   * last operation completes, while clearing the plan itself is a separate, later step ({@code
   * completeGraphChangeIfDrained()}), run by whichever broker's reconcile or gossip round gets
   * there first. Treating "drained" as "complete" here would let this plan be archived into history
   * while the sub-configuration still carries a fully drained but not yet cleared plan — which is
   * exactly the state a decoder without this phase to consult can no longer tell apart from an
   * actually-still-running one.
   *
   * @throws IllegalStateException if no plan {@code planId} is pending
   */
  public boolean isCurrentPhaseComplete(final long planId) {
    final var plan = phasedChangeState.pending().get(planId);
    if (plan == null) {
      throw new IllegalStateException(
          "Cannot check phase completion: no plan '%d' is pending".formatted(planId));
    }
    return switch (plan.currentPhase()) {
      case final GlobalPhase ignored -> globalConfiguration.pendingChanges().isEmpty();
      case final PartitionGroupPhase groupPhase ->
          groupPhase.groupGraphs().keySet().stream()
              .map(this::partitionGroup)
              .allMatch(group -> group != null && group.pendingChanges().isEmpty());
    };
  }

  /**
   * Cancels the pending plan {@code planId}: clears the pending change on the sub-configuration(s)
   * targeted by its <em>current</em> phase (the only ones that can have a change in progress
   * belonging to this plan — earlier phases have already drained), and moves the plan into {@code
   * history} with {@link PhasedChangePlanStatus#CANCELLED}. This is an unsafe operation and should
   * be used only as a last resort when a plan is stuck — already applied operations are not
   * reverted, so sub-configurations affected by earlier phases may be left in an intermediate
   * state.
   *
   * <p>Only the named plan's own sub-configurations are touched; other concurrently pending plans
   * (which, by admission, target disjoint sub-configurations) are unaffected.
   *
   * @return {@code this} if plan {@code planId} is not pending
   */
  public CurrentClusterConfiguration cancelPendingChanges(final long planId) {
    return cancelPendingChanges(planId, PhasedChangeState.DEFAULT_HISTORY_LIMIT);
  }

  /** Same as {@link #cancelPendingChanges(long)}, with an explicit history cap. */
  public CurrentClusterConfiguration cancelPendingChanges(
      final long planId, final int historyLimit) {
    final var plan = phasedChangeState.pending().get(planId);
    if (plan == null) {
      return this;
    }
    final var result =
        switch (plan.currentPhase()) {
          case final GlobalPhase ignored ->
              updateGlobalConfiguration(GlobalConfiguration::cancelPendingChanges);
          case final PartitionGroupPhase groupPhase -> {
            var r = this;
            for (final var groupId : groupPhase.groupGraphs().keySet()) {
              r =
                  r.updatePartitionGroupConfig(
                      groupId, PartitionGroupConfiguration::cancelPendingChanges);
            }
            yield r;
          }
        };
    return result.completePlan(planId, PhasedChangePlanStatus.CANCELLED, historyLimit);
  }

  /**
   * Returns the number of members in the cluster that are not {@link BrokerState.State#LEFT} or
   * {@link BrokerState.State#UNINITIALIZED}.
   */
  public int clusterSize() {
    return (int)
        globalConfiguration.members().entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().state() != BrokerState.State.LEFT
                        && entry.getValue().state() != BrokerState.State.UNINITIALIZED)
            .count();
  }

  /**
   * Activates the plan's current phase by copying its operations into the affected sub-config(s): a
   * {@link GlobalPhase} starts a configuration change on {@link GlobalConfiguration} only; a {@link
   * PartitionGroupPhase} starts one on each named partition group only.
   */
  private CurrentClusterConfiguration applyPhase(final PhasedChangePlan plan) {
    return switch (plan.currentPhase()) {
      case final GlobalPhase globalPhase ->
          updateGlobalConfiguration(
              global -> {
                if (global.pendingChanges().isPresent()) {
                  throw new IllegalStateException(
                      "Cannot activate global phase: global configuration already has pending changes");
                }
                return global.startConfigurationChange(
                    List.<ClusterConfigurationChangeOperation>copyOf(globalPhase.operations()));
              });
      case final PartitionGroupPhase groupPhase -> {
        var result = this;
        for (final var entry : groupPhase.groupGraphs().entrySet()) {
          final var groupId = entry.getKey();
          final var graph = entry.getValue();
          result =
              result.updatePartitionGroupConfig(
                  groupId,
                  group -> {
                    // isPresent(), not hasPendingChanges(): matches what
                    // startGraphConfigurationChange rejects on, so a drained-but-uncleared plan
                    // fails here with this message rather than one frame down with a vaguer one.
                    if (group.pendingChanges().isPresent()) {
                      throw new IllegalStateException(
                          "Cannot activate partition-group phase for %s: group already has pending changes"
                              .formatted(groupId));
                    }
                    return group.startGraphConfigurationChange(graph);
                  });
        }
        yield result;
      }
    };
  }

  private CurrentClusterConfiguration withPhasedChangeState(final PhasedChangeState newState) {
    return new CurrentClusterConfiguration(version, globalConfiguration, partitionGroups, newState);
  }

  public boolean hasPartitionGroup(final String groupId) {
    return partitionGroups.containsKey(groupId);
  }

  /**
   * Returns true if this configuration was produced by migrating a legacy {@link
   * ClusterConfiguration} that was itself {@link ClusterConfiguration#isAfterRestore()}: the
   * pending plan's id is {@link PhasedChangePlan#RESTORED_PLAN_ID} (see {@link
   * PhasedChangePlan#hasRestorePlanId()}) and it contains exactly one phase with exactly one
   * operation, an {@link UpdateRoutingState}. Mirrors {@link
   * ClusterConfiguration#isAfterRestore()}.
   */
  public boolean isAfterRestore() {
    return phasedChangeState.pending().values().stream().anyMatch(this::isRestorePlan);
  }

  private boolean isRestorePlan(final PhasedChangePlan plan) {
    return plan.hasRestorePlanId()
        && plan.phases().size() == 1
        && plan.phases().get(0) instanceof final PartitionGroupPhase groupPhase
        && groupPhase.groupGraphs().size() == 1
        && groupPhase.groupOperations().values().stream()
            .allMatch(
                operations ->
                    operations.size() == 1 && operations.get(0) instanceof UpdateRoutingState);
  }

  public @Nullable PartitionGroupConfiguration partitionGroup(final String groupId) {
    return partitionGroups.get(groupId);
  }

  /**
   * The subset of {@link #partitionGroups()} that are not disabled, keyed by physical tenant id. A
   * disabled group (removed from local static configuration after being provisioned, see {@code
   * PhysicalTenantAvailabilityInitializer}) is retained in {@link #partitionGroups()} — its data
   * and partition assignment are not deleted — but excluded here, since it is not running on any
   * broker: it must not be counted as load when placing a new tenant's partitions, nor shown in
   * gateway routing topology, nor targeted by cluster-wide operations.
   */
  public Map<String, PartitionGroupConfiguration> activePartitionGroups() {
    return Collections.unmodifiableMap(
        partitionGroups.entrySet().stream()
            .filter(entry -> !entry.getValue().isDisabled())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
  }

  /**
   * A view of this configuration with every disabled partition group (see {@link
   * #activePartitionGroups()}) removed, leaving {@link #version()}, {@link #globalConfiguration()}
   * and {@link #phasedChangeState()} unchanged. Intended for {@link
   * io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest#phases}
   * implementations that enumerate all physical tenants and must not target a disabled one.
   */
  public CurrentClusterConfiguration withoutDisabledPartitionGroups() {
    return new CurrentClusterConfiguration(
        version, globalConfiguration, activePartitionGroups(), phasedChangeState);
  }

  /**
   * Returns the desired leader of every partition in the cluster, grouped by partition group id.
   * Partition ids are unique only within a group, so the outer group-id key is required to
   * disambiguate them. See {@link PartitionGroupConfiguration#desiredLeaders()} for how the
   * per-group leaders are derived.
   *
   * @return the desired leaders per group, keyed by group id then by partition id
   */
  public Map<String, SortedMap<Integer, MemberId>> desiredLeaders() {
    return partitionGroups.entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().desiredLeaders()));
  }

  private static PhasedChangeState toPhasedChangeState(final ClusterConfiguration legacy) {
    final Optional<CompletedPhasedChange> lastChange =
        legacy.lastChange().map(CurrentClusterConfiguration::toCompletedPhasedChange);
    final long lastChangeId = lastChange.map(CompletedPhasedChange::id).orElse(0L);
    final Optional<PhasedChangePlan> pending =
        legacy.pendingChanges().flatMap(plan -> toPhasedChangePlan(plan, lastChangeId));
    final List<CompletedPhasedChange> history = lastChange.map(List::of).orElse(List.of());
    final long nextId =
        pending
            .map(plan -> plan.id() + 1)
            .orElse(Math.max(lastChangeId + 1, PhasedChangePlan.INITIAL_PLAN_ID));
    final Map<Long, PhasedChangePlan> pendingMap =
        pending.map(plan -> Map.of(plan.id(), plan)).orElse(Map.of());
    return new PhasedChangeState(nextId, pendingMap, history);
  }

  /**
   * Builds the pending {@link PhasedChangePlan}, normalizing the plan id. Legacy restore plans use
   * a negative sentinel id ({@code ClusterChangePlan.RESTORE_CHANGE_ID = -2}) that cannot be
   * preserved as-is ({@link PhasedChangePlan} requires a non-negative id); they are instead
   * assigned {@link PhasedChangePlan#RESTORED_PLAN_ID}, the new model's own sentinel (see {@link
   * PhasedChangePlan#hasRestorePlanId()} and {@link #isAfterRestore()}). Any other legacy id that
   * is not positive or not greater than {@code lastChangeId} is replaced with {@code lastChangeId +
   * 1}, keeping ids positive and monotonic after migration.
   *
   * <p>A restore is only ever produced by {@code RestoreManager#restoreTopologyFile}, which always
   * regenerates a completely fresh legacy configuration (via {@code StaticConfigurationGenerator})
   * and attaches the restore plan to it — so a legacy restore plan is expected to never carry a
   * prior completed change ({@code lastChangeId == 0}). This is asserted below rather than assumed
   * silently: violating it would mean two restore plans could both be assigned {@link
   * PhasedChangePlan#RESTORED_PLAN_ID}, and the second migration would only fail downstream in
   * {@link PhasedChangeState}'s id-monotonicity check with no restore-specific context.
   *
   * <p>Only what is left to run is migrated, in both models: a phase is a template with no progress
   * of its own, so an operation that has already completed has nowhere to be recorded and would
   * come back as pending if it were carried over. A queue therefore migrates its pending
   * operations, and a graph the subgraph of its incomplete ones (see {@link
   * #remainingGraph(DependencyChangePlan)}).
   *
   * @throws IllegalStateException if a legacy restore plan is migrated alongside a prior completed
   *     change, which would violate the assumption above
   */
  private static Optional<PhasedChangePlan> toPhasedChangePlan(
      final ChangePlan plan, final long lastChangeId) {
    final var phases =
        switch (plan) {
          case final ClusterChangePlan queue -> toPhases(queue.pendingOperations());
          case final DependencyChangePlan graph ->
              remainingGraph(graph).map(CurrentClusterConfiguration::toPhase).stream().toList();
        };
    if (phases.isEmpty()) {
      return Optional.empty();
    }

    if (plan instanceof final ClusterChangePlan queue && queue.isRestore()) {
      if (lastChangeId != 0) {
        throw new IllegalStateException(
            "Cannot migrate a legacy restore plan: expected no prior completed change "
                + "(lastChangeId=0) since a restore always regenerates a fresh configuration, but "
                + "found lastChangeId="
                + lastChangeId);
      }
      return Optional.of(PhasedChangePlan.initForRestore(phases, plan.startedAt()));
    }
    final long id = plan.id() > 0 && plan.id() > lastChangeId ? plan.id() : lastChangeId + 1;
    return Optional.of(new PhasedChangePlan(id, 0, phases, plan.startedAt()));
  }

  /**
   * The phase a graph change belongs in, taken from the operations it holds: cluster-wide ones make
   * a {@link GlobalPhase}, a partition group's a {@link PartitionGroupPhase} targeting the default
   * group — which is the only group a single-group projection can be describing.
   *
   * <p>A group's graph keeps its edges, so the concurrency it was planned with survives the round
   * trip. A cluster-wide one cannot: {@link GlobalPhase} carries a flat list. That loses nothing
   * today, because {@link GlobalConfiguration#startConfigurationChange} only ever builds a
   * sequential graph and {@link OperationGraph#inOrder()} reproduces exactly that order — but a
   * cluster-wide change that one day declares two independent operations would need {@code
   * GlobalPhase} to carry a graph before this could migrate it faithfully.
   *
   * <p>A graph mixing both kinds has no phase to go in, and no sub-configuration can produce one:
   * each holds operations of a single kind. It is rejected rather than split across two phases,
   * which would invent an ordering between them that the graph never declared.
   */
  private static Phase toPhase(final OperationGraph graph) {
    final var operations = graph.inOrder();
    if (operations.stream().allMatch(GlobalChangeOperation.class::isInstance)) {
      return new GlobalPhase(operations.stream().map(GlobalChangeOperation.class::cast).toList());
    }
    if (operations.stream().allMatch(PartitionGroupOperation.class::isInstance)) {
      return new PartitionGroupPhase(Map.of(DEFAULT_GROUP, graph));
    }
    throw new IllegalStateException(
        "Cannot migrate a change whose operations are partly cluster-wide and partly a partition"
            + " group's: "
            + operations);
  }

  /**
   * The part of a graph change that has not run yet: its incomplete operations, keeping every edge
   * between two of them and dropping the ones pointing at an operation that has already completed —
   * a dependency that is already satisfied constrains nothing, and leaving it in would name an
   * operation the graph no longer contains.
   *
   * <p>Empty when every operation has completed, which is a change with nothing left to migrate
   * rather than a graph with no operations — {@link OperationGraph} rejects the latter.
   */
  private static Optional<OperationGraph> remainingGraph(final DependencyChangePlan plan) {
    final SortedMap<OperationId, PlannedOperation> remaining = new TreeMap<>();
    plan.operations()
        .forEach(
            (operationId, planned) -> {
              if (plan.isComplete(operationId)) {
                return;
              }
              final SortedSet<OperationId> outstanding = new TreeSet<>(planned.dependsOn());
              outstanding.removeIf(plan::isComplete);
              remaining.put(
                  operationId,
                  new PlannedOperation(planned.operation(), outstanding, planned.groupId()));
            });
    return remaining.isEmpty() ? Optional.empty() : Optional.of(new OperationGraph(remaining));
  }

  /**
   * Splits a flat legacy operation list into phases, preserving order: each maximal run of
   * consecutive operations of the same kind becomes one phase — a run of {@link
   * GlobalChangeOperation} becomes a {@link GlobalPhase}, a run of {@link PartitionGroupOperation}
   * becomes a sequential {@link PartitionGroupPhase} targeting the default group. For example
   * {@code [MemberJoin, PartitionJoin, PartitionLeave, MemberLeave]} yields three phases: a global
   * phase, a default-group phase with the two partition operations, and another global phase.
   *
   * <p>Also used by the coordinator to turn a freshly generated flat operation list (from the
   * unchanged request transformers) into a {@link PhasedChangePlan} for the default group.
   */
  public static List<Phase> toPhases(final List<ClusterConfigurationChangeOperation> operations) {
    final List<Phase> phases = new ArrayList<>();
    final List<GlobalChangeOperation> globalRun = new ArrayList<>();
    final List<PartitionGroupOperation> partitionRun = new ArrayList<>();
    for (final ClusterConfigurationChangeOperation operation : operations) {
      switch (operation) {
        case final GlobalChangeOperation global -> {
          flushPartitionRun(phases, partitionRun);
          globalRun.add(global);
        }
        case final PartitionGroupOperation partition -> {
          flushGlobalRun(phases, globalRun);
          partitionRun.add(partition);
        }
      }
    }
    flushGlobalRun(phases, globalRun);
    flushPartitionRun(phases, partitionRun);
    return phases;
  }

  private static void flushGlobalRun(
      final List<Phase> phases, final List<GlobalChangeOperation> run) {
    if (!run.isEmpty()) {
      phases.add(new GlobalPhase(List.copyOf(run)));
      run.clear();
    }
  }

  private static void flushPartitionRun(
      final List<Phase> phases, final List<PartitionGroupOperation> run) {
    if (!run.isEmpty()) {
      phases.add(PartitionGroupPhase.sequential(DEFAULT_GROUP, List.copyOf(run)));
      run.clear();
    }
  }

  private static CompletedPhasedChange toCompletedPhasedChange(final CompletedChange change) {
    final var status =
        switch (change.status()) {
          case COMPLETED -> PhasedChangePlanStatus.COMPLETED;
          case FAILED -> PhasedChangePlanStatus.FAILED;
          case CANCELLED -> PhasedChangePlanStatus.CANCELLED;
          case IN_PROGRESS ->
              throw new IllegalStateException(
                  "Cannot migrate a legacy last change with IN_PROGRESS status: " + change);
        };
    // A completed legacy restore keeps the negative sentinel id
    // (ClusterChangePlan.RESTORE_CHANGE_ID
    // = -2). Clamp non-positive ids to 0 so the next derived plan id (lastChange.id() + 1) stays
    // positive and PhasedChangePlan's id-must-be-positive invariant is preserved.
    final long id = Math.max(change.id(), 0);
    return new CompletedPhasedChange(id, status, change.startedAt(), change.completedAt());
  }

  private static BrokerState toBrokerState(final MemberState memberState) {
    return new BrokerState(
        memberState.version(), memberState.lastUpdated(), toLifecycleState(memberState.state()));
  }

  private static BrokerPartitionState toBrokerPartitionState(final MemberState memberState) {
    final var mode =
        memberState.state() == MemberState.State.RECOVERING ? Mode.RECOVERING : Mode.PROCESSING;
    return new BrokerPartitionState(
        memberState.version(), memberState.lastUpdated(), memberState.partitions(), mode);
  }

  private static BrokerState.State toLifecycleState(final MemberState.State state) {
    return switch (state) {
      case UNINITIALIZED -> BrokerState.State.UNINITIALIZED;
      case JOINING -> BrokerState.State.JOINING;
      // A recovering broker is lifecycle-active; recovery is tracked per group as a Mode.
      case ACTIVE, RECOVERING -> BrokerState.State.ACTIVE;
      case LEAVING -> BrokerState.State.LEAVING;
      case LEFT -> BrokerState.State.LEFT;
    };
  }

  public int getPartitionCount(final String partitionGroup) {
    final var group = partitionGroups.get(partitionGroup);
    if (group == null) {
      return 0;
    } else {
      return group.partitionCount();
    }
  }
}
