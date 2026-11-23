/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
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
public record CurrentClusterConfiguration(
    long version,
    GlobalConfiguration globalConfiguration,
    Map<String, PartitionGroupConfiguration> partitionGroups,
    PhasedChangeState phasedChangeState) {

  public static final long INITIAL_VERSION = 0;
  public static final String DEFAULT_GROUP = "default";

  public CurrentClusterConfiguration {
    requireNonNull(globalConfiguration, "globalConfiguration must not be null");
    requireNonNull(partitionGroups, "partitionGroups must not be null");
    requireNonNull(phasedChangeState, "phasedChangeState must not be null");
    partitionGroups = Map.copyOf(partitionGroups);
  }

  public static CurrentClusterConfiguration uninitialized() {
    return new CurrentClusterConfiguration(
        INITIAL_VERSION, GlobalConfiguration.uninitialized(), Map.of(), PhasedChangeState.empty());
  }

  public boolean isUninitialized() {
    return globalConfiguration.isUninitialized();
  }

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
   * <p>Read as the minimum rather than a configured value: during a configuration change a
   * partition can temporarily hold more replicas than the cluster is configured for.
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
   * Activates the current pending plan's phase (see {@link #applyPhase}) if one is pending,
   * otherwise returns this configuration unchanged. Mirrors what {@link #initPlan(List)} does for a
   * freshly-started plan; intended for call sites, such as a restore, that need to take over
   * driving an already-pending plan rather than starting a new one.
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

  public CurrentClusterConfiguration initPartitionGroup(final String groupId) {
    if (hasPartitionGroup(groupId)) {
      throw new IllegalStateException("Partition group %s already exists".formatted(groupId));
    }
    final var updatedPartitionGroups = new HashMap<>(partitionGroups);
    updatedPartitionGroups.put(groupId, PartitionGroupConfiguration.empty(0));
    return new CurrentClusterConfiguration(
        version, globalConfiguration, updatedPartitionGroups, phasedChangeState);
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
    final var plan = requireNonNull(newState.pending().get(newId));
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
   * Returns true if this configuration was produced by a restore: the pending plan's id is {@link
   * PhasedChangePlan#RESTORED_PLAN_ID} (see {@link PhasedChangePlan#hasRestorePlanId()}) and it
   * contains exactly one {@link PartitionGroupPhase} whose every named group runs exactly one
   * operation, an {@link UpdateRoutingState}.
   *
   * <p>The phase may name any number of groups, one per restored physical tenant: {@code
   * RestoreManager} writes one {@link UpdateRoutingState} per group into a single phase, and {@link
   * #applyPhase} activates every one of them. Constraining this to a single group would silently
   * report {@code false} for a multi-tenant restore, switching off the post-restore handling in
   * {@code PartitionGroupExporterStateInitializer} exactly when it is needed most — with no error
   * and no log.
   */
  public boolean isAfterRestore() {
    return phasedChangeState.pending().values().stream().anyMatch(this::isRestorePlan);
  }

  private boolean isRestorePlan(final PhasedChangePlan plan) {
    return plan.hasRestorePlanId()
        && plan.phases().size() == 1
        && plan.phases().get(0) instanceof final PartitionGroupPhase groupPhase
        && !groupPhase.groupGraphs().isEmpty()
        && groupPhase.groupOperations().values().stream()
            .allMatch(
                operations ->
                    operations.size() == 1 && operations.get(0) instanceof UpdateRoutingState);
  }

  public @Nullable PartitionGroupConfiguration partitionGroup(final String groupId) {
    return partitionGroups.get(groupId);
  }

  /**
   * The version of the configuration as seen from one partition group: the higher of the global
   * configuration's version and the group's, since a change to either changes what that group runs.
   * A group that does not exist counts as the global configuration alone.
   */
  public long versionOf(final String groupId) {
    final var group = partitionGroups.get(groupId);
    return group == null
        ? globalConfiguration.version()
        : Math.max(globalConfiguration.version(), group.version());
  }

  /**
   * The change plan running on the global configuration if there is one, otherwise the one running
   * on the given partition group.
   */
  public Optional<DependencyChangePlan> pendingChangesOf(final String groupId) {
    final var group = partitionGroups.get(groupId);
    return globalConfiguration
        .pendingChanges()
        .filter(DependencyChangePlan::hasPendingChanges)
        .or(
            () ->
                group == null
                    ? Optional.empty()
                    : group.pendingChanges().filter(DependencyChangePlan::hasPendingChanges));
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
   * and {@link #phasedChangeState()} unchanged. Intended for {@code
   * ConfigurationChangeCoordinator.ConfigurationChangeRequest#phases} implementations that
   * enumerate all physical tenants and must not target a disabled one.
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

  public int getPartitionCount(final String partitionGroup) {
    final var group = partitionGroups.get(partitionGroup);
    if (group == null) {
      return 0;
    } else {
      return group.partitionCount();
    }
  }
}
