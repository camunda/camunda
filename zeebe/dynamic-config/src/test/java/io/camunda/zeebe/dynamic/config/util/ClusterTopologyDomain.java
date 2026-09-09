/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static dev.hegel.Generators.composite;
import static dev.hegel.Generators.fromRegex;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.oneOf;
import static dev.hegel.Generators.optional;
import static dev.hegel.Generators.records;
import static dev.hegel.Generators.sampledFrom;
import static dev.hegel.Generators.sets;
import static dev.hegel.Generators.text;

import dev.hegel.Generator;
import dev.hegel.TestCase;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.dynamic.config.state.TenantAvailability;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Contains all generators needed to generate a {@link ClusterConfiguration}. The topology is not
 * semantically correct (e.g. contains operations for members that don't exist) but all fields
 * should have valid values.
 */
public final class ClusterTopologyDomain {

  private static final DerivedGenerators PLAIN = new DerivedGenerators(Map.of());

  /**
   * Derivation for types whose components include the model's value types. Deriving those from the
   * type alone would either fail (they are not records) or produce values their constructors
   * reject.
   */
  private static final DerivedGenerators DERIVED =
      new DerivedGenerators(
          Map.of(
              MemberId.class, memberIds(),
              Instant.class, nanoPrecisionInstants(),
              PartitionDistributorConfig.class, partitionDistributorConfig(),
              DynamicPartitionConfig.class, dynamicPartitionConfigs(),
              RoutingState.class, routingStates()));

  private ClusterTopologyDomain() {}

  public static Generator<ClusterConfiguration> clusterTopologies() {
    // Combine generators (instead of just deriving from `ClusterConfiguration.class`) so that we
    // have control over the version. Version must be greater than 0 for
    // `ClusterTopology#isUninitialized` to return false.
    final var members = maps(memberIds(), DERIVED.forType(MemberState.class)).maxSize(10);
    final var completedChange = optional(DERIVED.forType(CompletedChange.class));
    final var changePlan =
        optional(DERIVED.forType(ClusterChangePlan.class).<ChangePlan>map(plan -> plan));
    final var routingState = optional(routingStates());
    final var clusterId = optional(text().minSize(1).maxSize(50));
    return composite(
        tc ->
            new ClusterConfiguration(
                tc.draw(integers().min(0)),
                tc.draw(members),
                tc.draw(completedChange),
                tc.draw(changePlan),
                tc.draw(routingState),
                tc.draw(clusterId),
                tc.draw(longs().min(0)),
                tc.draw(partitionDistributorConfigs())));
  }

  public static Generator<PartitionDistributorConfig> partitionDistributorConfig() {
    return sampledFrom(
        new PartitionDistributorConfig.RoundRobinConfig(),
        new PartitionDistributorConfig.FixedConfig(),
        new PartitionDistributorConfig.ZoneAwareConfig(
            List.of(new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000))),
        new PartitionDistributorConfig.ZoneAwareConfig(
            List.of(
                new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000),
                new PartitionDistributorConfig.ZoneSpec("zone-b", 1, 500))),
        new PartitionDistributorConfig.ZoneAwareConfig(
            List.of(
                new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000),
                new PartitionDistributorConfig.ZoneSpec("zone-b", 2, 500),
                new PartitionDistributorConfig.ZoneSpec("zone-c", 1, 200))),
        new PartitionDistributorConfig.ZoneAwareConfig(
            List.of(new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000))));
  }

  public static Generator<Optional<PartitionDistributorConfig>> partitionDistributorConfigs() {
    return sampledFrom(
        Optional.empty(),
        Optional.of(new PartitionDistributorConfig.RoundRobinConfig()),
        Optional.of(new PartitionDistributorConfig.FixedConfig()),
        Optional.of(
            new PartitionDistributorConfig.ZoneAwareConfig(
                List.of(
                    new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000),
                    new PartitionDistributorConfig.ZoneSpec("zone-b", 1, 500)))));
  }

  public static Generator<RoutingState> routingStates() {
    final var requestHandling = requestHandling();
    final var messageCorrelation = messageCorrelation();
    return composite(
        tc ->
            new RoutingState(
                tc.draw(longs().min(0)), tc.draw(requestHandling), tc.draw(messageCorrelation)));
  }

  public static Generator<RequestHandling> requestHandling() {
    return oneOf(allPartitions(), activePartitions());
  }

  public static Generator<AllPartitions> allPartitions() {
    return integers().min(1).max(5).map(AllPartitions::new);
  }

  public static Generator<ActivePartitions> activePartitions() {
    final var activePartitions = lists(integers().min(4).max(8));
    final var inactivePartitions = lists(integers().min(9).max(12));
    return composite(
        tc ->
            new ActivePartitions(
                tc.draw(integers().min(1).max(3)),
                new TreeSet<>(tc.draw(activePartitions)),
                new TreeSet<>(tc.draw(inactivePartitions))));
  }

  public static Generator<MessageCorrelation> messageCorrelation() {
    final var hashMods = records(HashMod.class).with("partitionCount", integers().min(1));
    return new DerivedGenerators(Map.of(HashMod.class, hashMods)).forType(MessageCorrelation.class);
  }

  public static Generator<ClusterConfigurationChangeOperation> topologyChangeOperations() {
    return DERIVED.forType(ClusterConfigurationChangeOperation.class);
  }

  public static Generator<MemberId> memberIds() {
    return integers().min(0).map(id -> MemberId.from(id.toString()));
  }

  public static Generator<DynamicPartitionConfig> dynamicPartitionConfigs() {
    return PLAIN.forType(ExportingConfig.class).map(DynamicPartitionConfig::new);
  }

  // ---- New multi-partition-group model (8.10) ----

  public static Generator<CurrentClusterConfiguration> currentClusterConfigurations() {
    final var global = globalConfigurations();
    final var partitionGroups =
        maps(partitionGroupIds(), partitionGroupConfigurations()).maxSize(4);
    final var phasedChangeState = phasedChangeStates();
    return composite(
        tc ->
            // version is always INITIAL_VERSION and reserved; it is not used in merge.
            new CurrentClusterConfiguration(
                CurrentClusterConfiguration.INITIAL_VERSION,
                tc.draw(global),
                tc.draw(partitionGroups),
                tc.draw(phasedChangeState)));
  }

  public static Generator<GlobalConfiguration> globalConfigurations() {
    // clusterId must be non-empty: the serializer treats the empty string as "absent".
    final var clusterId = optional(text().minSize(1).maxSize(50));
    final var members = maps(memberIds(), brokerStates()).maxSize(6);
    // Cluster-wide changes run as graphs too, so this is the only plan shape generated here.
    final var pendingChanges = optional(globalDependencyChangePlans());
    final var lastChange = optional(DERIVED.forType(CompletedChange.class));
    return composite(
        tc ->
            new GlobalConfiguration(
                tc.draw(longs().min(0)),
                tc.draw(clusterId),
                tc.draw(members),
                tc.draw(partitionDistributorConfigs()),
                tc.draw(pendingChanges),
                tc.draw(lastChange)));
  }

  public static Generator<PartitionGroupConfiguration> partitionGroupConfigurations() {
    final var members = maps(memberIds(), brokerPartitionStates()).maxSize(6);
    final var routingState = optional(routingStates());
    // A group's change is always a dependency graph, so this is the only plan shape generated here.
    final var pendingChanges = optional(dependencyChangePlans());
    final var lastChange = optional(DERIVED.forType(CompletedChange.class));
    final var availability = tenantAvailabilities();
    return composite(
        tc ->
            new PartitionGroupConfiguration(
                tc.draw(longs().min(0)),
                tc.draw(longs().min(0)),
                tc.draw(members),
                tc.draw(routingState),
                tc.draw(pendingChanges),
                tc.draw(lastChange),
                tc.draw(availability)));
  }

  public static Generator<TenantAvailability> tenantAvailabilities() {
    final var state = sampledFrom(TenantAvailability.State.values());
    return composite(tc -> new TenantAvailability(tc.draw(longs().min(0)), tc.draw(state)));
  }

  public static Generator<BrokerState> brokerStates() {
    final var state = sampledFrom(BrokerState.State.values());
    return composite(
        tc ->
            new BrokerState(
                tc.draw(longs().min(0)), tc.draw(nanoPrecisionInstants()), tc.draw(state)));
  }

  public static Generator<BrokerPartitionState> brokerPartitionStates() {
    final var partitions =
        maps(integers().min(1).max(20), DERIVED.forType(PartitionState.class)).maxSize(4);
    final var mode = sampledFrom(Mode.values());
    return composite(
        tc ->
            new BrokerPartitionState(
                tc.draw(longs().min(0)),
                tc.draw(nanoPrecisionInstants()),
                tc.draw(partitions),
                tc.draw(mode)));
  }

  public static Generator<PhasedChangeState> phasedChangeStates() {
    // History ids are reassigned sequentially from 0, and an optional pending plan (if any) gets
    // the next id after that — this keeps every id below nextId, satisfying PhasedChangeState's
    // invariant, while still exercising arbitrary statuses/timestamps/phases via the existing
    // completedPhasedChanges()/phases() generators.
    final var rawHistory = lists(completedPhasedChanges()).maxSize(3);
    final var maybePhaseList = optional(lists(phases()).minSize(1).maxSize(4));
    return composite(
        tc -> {
          final List<CompletedPhasedChange> history = new ArrayList<>();
          final var raw = tc.draw(rawHistory);
          for (int i = 0; i < raw.size(); i++) {
            final var c = raw.get(i);
            history.add(new CompletedPhasedChange(i, c.status(), c.startedAt(), c.completedAt()));
          }
          final long pendingId = Math.max(history.size(), PhasedChangePlan.INITIAL_PLAN_ID);
          final var maybePhases = tc.draw(maybePhaseList);
          if (maybePhases.isEmpty()) {
            return new PhasedChangeState(pendingId, Map.of(), history);
          }
          final var phaseList = maybePhases.get();
          final var index = tc.draw(integers().min(0).max(phaseList.size() - 1));
          final var plan =
              new PhasedChangePlan(pendingId, index, phaseList, tc.draw(nanoPrecisionInstants()));
          return new PhasedChangeState(pendingId + 1, Map.of(pendingId, plan), history);
        });
  }

  public static Generator<Phase> phases() {
    final Generator<Phase> globalPhases =
        lists(globalChangeOperations()).maxSize(3).<Phase>map(GlobalPhase::new);
    // Each group's own operation list must be non-empty: PartitionGroupPhase.sequential builds an
    // OperationGraph per group, and OperationGraph.of rejects an empty one -- the same invariant
    // operationGraphs() below already respects.
    final Generator<Phase> sequentialGroupPhases =
        maps(partitionGroupIds(), lists(partitionGroupChangeOperations()).minSize(1).maxSize(3))
            .maxSize(3)
            .<Phase>map(PartitionGroupPhase::sequential);
    final Generator<Phase> graphGroupPhases =
        maps(partitionGroupIds(), operationGraphs())
            .maxSize(3)
            .<Phase>map(PartitionGroupPhase::new);
    return oneOf(globalPhases, sequentialGroupPhases, graphGroupPhases);
  }

  /**
   * A graph over 1–4 operations, each depending on an arbitrary subset of the operations before it
   * in generation order. Ids are assigned by that same order ({@link OperationId#of(int)} matching
   * list index), so a dependency can only ever point to an earlier id — the result is acyclic by
   * construction, with no rejection sampling needed to keep {@link OperationGraph#of} from
   * throwing.
   */
  public static Generator<OperationGraph> operationGraphs() {
    final var operations = lists(partitionGroupChangeOperations()).minSize(1).maxSize(4);
    return composite(tc -> operationGraphOf(tc, tc.draw(operations)));
  }

  /**
   * Graphs of cluster-wide operations, which is what {@link GlobalConfiguration} runs. Generated
   * separately from {@link #operationGraphs()} because the two operation kinds are encoded through
   * different arms of {@code PlannedOperation}'s oneof, and a round-trip property fed only
   * partition-group operations would never exercise the other one.
   */
  public static Generator<OperationGraph> globalOperationGraphs() {
    final var operations = lists(globalChangeOperations()).minSize(1).maxSize(4);
    return composite(tc -> operationGraphOf(tc, tc.draw(operations)));
  }

  private static OperationGraph operationGraphOf(
      final TestCase tc, final List<? extends ClusterConfigurationChangeOperation> operations) {
    final SortedMap<OperationId, OperationGraph.PlannedOperation> planned = new TreeMap<>();
    for (int i = 0; i < operations.size(); i++) {
      final Set<Integer> dependsOnIndexes =
          i == 0 ? Set.of() : tc.draw(sets(integers().min(0).max(i - 1)).maxSize(i));
      final SortedSet<OperationId> dependsOn = new TreeSet<>();
      for (final var index : dependsOnIndexes) {
        dependsOn.add(OperationId.of(index));
      }
      planned.put(
          OperationId.of(i), new OperationGraph.PlannedOperation(operations.get(i), dependsOn));
    }
    return OperationGraph.of(planned);
  }

  /**
   * A {@link DependencyChangePlan} over an arbitrary {@link #operationGraphs()} graph, with an
   * arbitrary subset of that graph's own operation ids marked completed — never an id outside the
   * graph, which is the shape every real plan has and the one round-trip/merge/decode tests need to
   * see exercised.
   */
  public static Generator<DependencyChangePlan> dependencyChangePlans() {
    return dependencyChangePlansOver(operationGraphs());
  }

  public static Generator<DependencyChangePlan> globalDependencyChangePlans() {
    return dependencyChangePlansOver(globalOperationGraphs());
  }

  private static Generator<DependencyChangePlan> dependencyChangePlansOver(
      final Generator<OperationGraph> graphs) {
    final var status = sampledFrom(ClusterChangePlan.Status.values());
    return composite(
        tc -> {
          final long id = tc.draw(longs().min(0).max(500));
          final var planStatus = tc.draw(status);
          final var startedAt = tc.draw(nanoPrecisionInstants());
          final var graph = tc.draw(graphs);
          final var ids = new ArrayList<>(graph.operations().keySet());
          final var pickedIds = tc.draw(sets(sampledFrom(ids)).maxSize(ids.size()));
          // An operation counts as complete only once everything it depends on is: no
          // execution can produce any other combination, and DependencyChangePlan rejects
          // one outright. Ids ascend with dependency order (see operationGraphs()), so a
          // single ascending pass suffices -- a picked operation whose dependencies were not
          // themselves picked is simply not reached yet, and is dropped.
          //
          // Each completion gets its own instant, derived from the operation id so it stays
          // reproducible. Reusing startedAt for all of them would make every generated plan
          // share the shape an encoder bug produces -- writing startedAt in place of each
          // operation's real completion instant -- and the round-trip property could then
          // never tell the bug from the fixture.
          final SortedMap<OperationId, Instant> completed = new TreeMap<>();
          for (final var operationId : ids) {
            final var plannedOperation = graph.operations().get(operationId);
            if (pickedIds.contains(operationId)
                && completed.keySet().containsAll(plannedOperation.dependsOn())) {
              completed.put(operationId, startedAt.plusMillis(1L + operationId.value()));
            }
          }
          return new DependencyChangePlan(id, planStatus, startedAt, graph, completed);
        });
  }

  public static Generator<CompletedPhasedChange> completedPhasedChanges() {
    final var status = sampledFrom(PhasedChangePlanStatus.values());
    return composite(
        tc ->
            new CompletedPhasedChange(
                tc.draw(longs().min(0).max(500)),
                tc.draw(status),
                tc.draw(nanoPrecisionInstants()),
                tc.draw(nanoPrecisionInstants())));
  }

  public static Generator<GlobalChangeOperation> globalChangeOperations() {
    return DERIVED.forType(GlobalChangeOperation.class);
  }

  public static Generator<PartitionGroupOperation> partitionGroupChangeOperations() {
    return DERIVED.forType(PartitionGroupOperation.class);
  }

  public static Generator<String> partitionGroupIds() {
    return fromRegex("[a-zA-Z]{1,10}");
  }

  /** Nanosecond-precision instants. */
  public static Generator<Instant> nanoPrecisionInstants() {
    return composite(
        tc ->
            Instant.ofEpochSecond(
                tc.draw(longs().min(0).max(4_000_000_000L)),
                tc.draw(integers().min(0).max(999_999_999))));
  }
}
