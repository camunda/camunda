/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

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
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.dynamic.config.state.TenantAvailability;
import io.camunda.zeebe.util.ReflectUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Provide;
import net.jqwik.api.domains.DomainContextBase;
import net.jqwik.api.providers.ArbitraryProvider;
import net.jqwik.api.providers.TypeUsage;
import net.jqwik.api.support.CollectorsSupport;
import net.jqwik.time.api.DateTimes;

/**
 * Contains all arbitraries needed to generate a {@link ClusterConfiguration}. The topology is not
 * semantically correct (e.g. contains operations for members that don't exist) but all fields
 * should have valid values.
 */
public final class ClusterTopologyDomain extends DomainContextBase {

  @Provide
  Arbitrary<ClusterConfiguration> clusterTopologies() {
    // Combine arbitraries (instead of just using `Arbitraries.forType(ClusterTopology.class)`
    // here so that we have control over the version. Version must be greater than 0 for
    // `ClusterTopology#isUninitialized` to return false.
    final var arbitraryVersion = Arbitraries.integers().greaterOrEqual(0);
    final var arbitraryMembers =
        Arbitraries.maps(memberIds(), Arbitraries.forType(MemberState.class).enableRecursion())
            .ofMaxSize(10);
    final var arbitraryCompletedChange =
        Arbitraries.forType(CompletedChange.class).enableRecursion().optional();
    final var arbitraryChangePlan =
        Arbitraries.forType(ClusterChangePlan.class)
            .enableRecursion()
            .<ChangePlan>map(plan -> plan)
            .optional();
    final var arbitraryRoutingState = routingStates().optional();
    final var arbitraryClusterId = Arbitraries.strings().ofMinLength(1).ofMaxLength(50).optional();
    final var arbitraryIncarnationNumber = Arbitraries.longs().greaterOrEqual(0);
    return Combinators.combine(
            arbitraryVersion,
            arbitraryMembers,
            arbitraryCompletedChange,
            arbitraryChangePlan,
            arbitraryRoutingState,
            arbitraryClusterId,
            arbitraryIncarnationNumber)
        .flatAs(
            (version,
                members,
                lastChange,
                pendingChanges,
                routingState,
                clusterId,
                incarnationNumber) ->
                partitionDistributorConfigs()
                    .map(
                        distributorConfig ->
                            new ClusterConfiguration(
                                version,
                                members,
                                lastChange,
                                pendingChanges,
                                routingState,
                                clusterId,
                                incarnationNumber,
                                distributorConfig)));
  }

  @Provide
  Arbitrary<PartitionDistributorConfig> partitionDistributorConfig() {
    return Arbitraries.of(
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

  @Provide
  Arbitrary<Optional<PartitionDistributorConfig>> partitionDistributorConfigs() {
    return Arbitraries.of(
        Optional.empty(),
        Optional.of(new PartitionDistributorConfig.RoundRobinConfig()),
        Optional.of(new PartitionDistributorConfig.FixedConfig()),
        Optional.of(
            new PartitionDistributorConfig.ZoneAwareConfig(
                List.of(
                    new PartitionDistributorConfig.ZoneSpec("zone-a", 2, 1000),
                    new PartitionDistributorConfig.ZoneSpec("zone-b", 1, 500)))));
  }

  @Provide
  Arbitrary<RoutingState> routingStates() {
    final var version = Arbitraries.longs().greaterOrEqual(0);
    return Combinators.combine(version, requestHandling(), messageCorrelation())
        .as(RoutingState::new);
  }

  @Provide
  Arbitrary<RequestHandling> requestHandling() {
    return Arbitraries.oneOf(
        allPartitions().map(RequestHandling.class::cast),
        activePartitions().map(RequestHandling.class::cast));
  }

  @Provide
  Arbitrary<AllPartitions> allPartitions() {
    return Arbitraries.integers().between(1, 5).map(AllPartitions::new);
  }

  @Provide
  Arbitrary<ActivePartitions> activePartitions() {
    final var basePartitionCount = Arbitraries.integers().between(1, 3);
    final var activePartitions = Arbitraries.integers().between(4, 8).list().map(TreeSet::new);
    final var inactivePartitions = Arbitraries.integers().between(9, 12).list().map(TreeSet::new);

    return Combinators.combine(basePartitionCount, activePartitions, inactivePartitions)
        .as(ActivePartitions::new);
  }

  @Provide
  Arbitrary<MessageCorrelation> messageCorrelation() {
    return Arbitraries.of(
            ReflectUtil.implementationsOfSealedInterface(MessageCorrelation.class).toList())
        .flatMap(Arbitraries::forType);
  }

  @Provide
  Arbitrary<ClusterConfigurationChangeOperation> topologyChangeOperations() {
    // jqwik does not support sealed classes yet, so we have to use reflection to get all possible
    // types. See https://github.com/jqwik-team/jqwik/issues/523
    return Arbitraries.of(
            ReflectUtil.implementationsOfSealedInterface(ClusterConfigurationChangeOperation.class)
                .toList())
        .flatMap(Arbitraries::forType);
  }

  @Provide
  Arbitrary<MemberId> memberIds() {
    return Arbitraries.integers().greaterOrEqual(0).map(id -> MemberId.from(id.toString()));
  }

  @Provide
  Arbitrary<DynamicPartitionConfig> dynamicPartitionConfigs() {
    return Arbitraries.forType(ExportingConfig.class)
        .enableRecursion()
        .map(DynamicPartitionConfig::new)
        .filter(DynamicPartitionConfig::isInitialized);
  }

  // ---- New multi-partition-group model (8.10) ----

  @Provide
  Arbitrary<CurrentClusterConfiguration> currentClusterConfigurations() {
    final var partitionGroups =
        Arbitraries.maps(partitionGroupIds(), partitionGroupConfigurations()).ofMaxSize(4);
    return Combinators.combine(globalConfigurations(), partitionGroups, phasedChangeStates())
        .as(
            (global, groups, phasedChangeState) ->
                // version is always INITIAL_VERSION and reserved; it is not used in merge.
                new CurrentClusterConfiguration(
                    CurrentClusterConfiguration.INITIAL_VERSION,
                    global,
                    groups,
                    phasedChangeState));
  }

  @Provide
  Arbitrary<GlobalConfiguration> globalConfigurations() {
    final var version = Arbitraries.longs().greaterOrEqual(0);
    // clusterId must be non-empty: the serializer treats the empty string as "absent".
    final var clusterId = Arbitraries.strings().ofMinLength(1).ofMaxLength(50).optional();
    final var members = Arbitraries.maps(memberIds(), brokerStates()).ofMaxSize(6);
    // Cluster-wide changes run as graphs too, so this is the only plan shape generated here.
    final Arbitrary<Optional<DependencyChangePlan>> pendingChanges =
        globalDependencyChangePlans().optional();
    final var lastChange = Arbitraries.forType(CompletedChange.class).enableRecursion().optional();
    return Combinators.combine(
            version, clusterId, members, partitionDistributorConfigs(), pendingChanges, lastChange)
        .as(
            (v, cid, m, distributor, pending, last) ->
                new GlobalConfiguration(v, cid, m, distributor, pending, last));
  }

  @Provide
  Arbitrary<PartitionGroupConfiguration> partitionGroupConfigurations() {
    final var version = Arbitraries.longs().greaterOrEqual(0);
    final var incarnationNumber = Arbitraries.longs().greaterOrEqual(0);
    final var members = Arbitraries.maps(memberIds(), brokerPartitionStates()).ofMaxSize(6);
    final var routingState = routingStates().optional();
    // A group's change is always a dependency graph, so this is the only plan shape generated here.
    final Arbitrary<Optional<DependencyChangePlan>> pendingChanges =
        dependencyChangePlans().optional();
    final var lastChange = Arbitraries.forType(CompletedChange.class).enableRecursion().optional();
    final var availability = tenantAvailabilities();
    return Combinators.combine(
            version,
            incarnationNumber,
            members,
            routingState,
            pendingChanges,
            lastChange,
            availability)
        .as(
            (v, inc, m, routing, pending, last, tenantAvailability) ->
                new PartitionGroupConfiguration(
                    v, inc, m, routing, pending, last, tenantAvailability));
  }

  @Provide
  Arbitrary<TenantAvailability> tenantAvailabilities() {
    final var version = Arbitraries.longs().greaterOrEqual(0);
    final var state = Arbitraries.of(TenantAvailability.State.values());
    return Combinators.combine(version, state).as(TenantAvailability::new);
  }

  @Provide
  Arbitrary<BrokerState> brokerStates() {
    final var version = Arbitraries.longs().greaterOrEqual(0);
    final var state = Arbitraries.of(BrokerState.State.values());
    return Combinators.combine(version, nanoPrecisionInstants(), state)
        .as((v, lastUpdated, s) -> new BrokerState(v, lastUpdated, s));
  }

  @Provide
  Arbitrary<BrokerPartitionState> brokerPartitionStates() {
    final var version = Arbitraries.longs().greaterOrEqual(0);
    final var partitions =
        Arbitraries.maps(
                Arbitraries.integers().between(1, 20),
                Arbitraries.forType(PartitionState.class).enableRecursion())
            .ofMaxSize(4);
    final var mode = Arbitraries.of(Mode.values());
    return Combinators.combine(version, nanoPrecisionInstants(), partitions, mode)
        .as((v, lastUpdated, p, m) -> new BrokerPartitionState(v, lastUpdated, p, m));
  }

  @Provide
  Arbitrary<PhasedChangeState> phasedChangeStates() {
    // History ids are reassigned sequentially from 0, and an optional pending plan (if any) gets
    // the next id after that — this keeps every id below nextId, satisfying PhasedChangeState's
    // invariant, while still exercising arbitrary statuses/timestamps/phases via the existing
    // completedPhasedChanges()/phases() generators.
    final var rawHistory = completedPhasedChanges().list().ofMaxSize(3);
    final var maybePhaseList = phases().list().ofMinSize(1).ofMaxSize(4).optional();
    return Combinators.combine(rawHistory, maybePhaseList, nanoPrecisionInstants())
        .flatAs(
            (raw, maybePhases, pendingStartedAt) -> {
              final List<CompletedPhasedChange> history = new ArrayList<>();
              for (int i = 0; i < raw.size(); i++) {
                final var c = raw.get(i);
                history.add(
                    new CompletedPhasedChange(i, c.status(), c.startedAt(), c.completedAt()));
              }
              final long pendingId = Math.max(history.size(), PhasedChangePlan.INITIAL_PLAN_ID);
              if (maybePhases.isEmpty()) {
                return Arbitraries.just(new PhasedChangeState(pendingId, Map.of(), history));
              }
              final var phaseList = maybePhases.get();
              return Arbitraries.integers()
                  .between(0, phaseList.size() - 1)
                  .map(
                      index -> {
                        final var plan =
                            new PhasedChangePlan(pendingId, index, phaseList, pendingStartedAt);
                        return new PhasedChangeState(
                            pendingId + 1, Map.of(pendingId, plan), history);
                      });
            });
  }

  @Provide
  Arbitrary<Phase> phases() {
    final Arbitrary<Phase> globalPhases =
        globalChangeOperations().list().ofMaxSize(3).<Phase>map(GlobalPhase::new);
    // Each group's own operation list must be non-empty: PartitionGroupPhase.sequential builds an
    // OperationGraph per group, and OperationGraph.of rejects an empty one -- the same invariant
    // operationGraphs() below already respects.
    final Arbitrary<Phase> sequentialGroupPhases =
        Arbitraries.maps(
                partitionGroupIds(),
                partitionGroupChangeOperations().list().ofMinSize(1).ofMaxSize(3))
            .ofMaxSize(3)
            .<Phase>map(PartitionGroupPhase::sequential);
    final Arbitrary<Phase> graphGroupPhases =
        Arbitraries.maps(partitionGroupIds(), operationGraphs())
            .ofMaxSize(3)
            .<Phase>map(PartitionGroupPhase::new);
    return Arbitraries.oneOf(globalPhases, sequentialGroupPhases, graphGroupPhases);
  }

  /**
   * A graph over 1–4 operations, each depending on an arbitrary subset of the operations before it
   * in generation order. Ids are assigned by that same order ({@link OperationId#of(int)} matching
   * list index), so a dependency can only ever point to an earlier id — the result is acyclic by
   * construction, with no rejection sampling needed to keep {@link OperationGraph#of} from
   * throwing.
   */
  @Provide
  Arbitrary<OperationGraph> operationGraphs() {
    return partitionGroupChangeOperations()
        .list()
        .ofMinSize(1)
        .ofMaxSize(4)
        .flatMap(ClusterTopologyDomain::operationGraphOf);
  }

  /**
   * Graphs of cluster-wide operations, which is what {@link GlobalConfiguration} runs. Generated
   * separately from {@link #operationGraphs()} because the two operation kinds are encoded through
   * different arms of {@code PlannedOperation}'s oneof, and a round-trip property fed only
   * partition-group operations would never exercise the other one.
   */
  @Provide
  Arbitrary<OperationGraph> globalOperationGraphs() {
    return globalChangeOperations()
        .list()
        .ofMinSize(1)
        .ofMaxSize(4)
        .flatMap(ClusterTopologyDomain::operationGraphOf);
  }

  private static Arbitrary<OperationGraph> operationGraphOf(
      final List<? extends ClusterConfigurationChangeOperation> operations) {
    final List<Arbitrary<Set<Integer>>> dependsOnPerIndex = new ArrayList<>();
    for (int i = 0; i < operations.size(); i++) {
      dependsOnPerIndex.add(
          i == 0
              ? Arbitraries.just(Set.of())
              : Arbitraries.integers().between(0, i - 1).set().ofMaxSize(i));
    }
    return Combinators.combine(dependsOnPerIndex)
        .as(
            dependsOnByIndex -> {
              final SortedMap<OperationId, OperationGraph.PlannedOperation> planned =
                  new TreeMap<>();
              for (int i = 0; i < operations.size(); i++) {
                final SortedSet<OperationId> dependsOn = new TreeSet<>();
                for (final var index : dependsOnByIndex.get(i)) {
                  dependsOn.add(OperationId.of(index));
                }
                planned.put(
                    OperationId.of(i),
                    new OperationGraph.PlannedOperation(operations.get(i), dependsOn));
              }
              return OperationGraph.of(planned);
            });
  }

  /**
   * A {@link DependencyChangePlan} over an arbitrary {@link #operationGraphs()} graph, with an
   * arbitrary subset of that graph's own operation ids marked completed — never an id outside the
   * graph, which is the shape every real plan has and the one round-trip/merge/decode tests need to
   * see exercised.
   */
  @Provide
  Arbitrary<DependencyChangePlan> dependencyChangePlans() {
    return dependencyChangePlansOver(operationGraphs());
  }

  @Provide
  Arbitrary<DependencyChangePlan> globalDependencyChangePlans() {
    return dependencyChangePlansOver(globalOperationGraphs());
  }

  private Arbitrary<DependencyChangePlan> dependencyChangePlansOver(
      final Arbitrary<OperationGraph> graphs) {
    final var id = Arbitraries.longs().between(0, 500);
    final var status = Arbitraries.of(ClusterChangePlan.Status.values());
    return Combinators.combine(id, status, nanoPrecisionInstants(), graphs)
        .as(PartialDependencyChangePlan::new)
        .flatMap(ClusterTopologyDomain::withCompletedOperations);
  }

  private static Arbitrary<DependencyChangePlan> withCompletedOperations(
      final PartialDependencyChangePlan partial) {
    final var ids = new ArrayList<>(partial.graph().operations().keySet());
    return Arbitraries.of(ids)
        .set()
        .ofMaxSize(ids.size())
        .map(
            pickedIds -> {
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
                final var planned = partial.graph().operations().get(operationId);
                if (pickedIds.contains(operationId)
                    && completed.keySet().containsAll(planned.dependsOn())) {
                  completed.put(
                      operationId, partial.startedAt().plusMillis(1L + operationId.value()));
                }
              }
              return new DependencyChangePlan(
                  partial.id(), partial.status(), partial.startedAt(), partial.graph(), completed);
            });
  }

  @Provide
  Arbitrary<CompletedPhasedChange> completedPhasedChanges() {
    final var id = Arbitraries.longs().between(0, 500);
    final var status = Arbitraries.of(PhasedChangePlanStatus.values());
    return Combinators.combine(id, status, nanoPrecisionInstants(), nanoPrecisionInstants())
        .as(
            (planId, s, startedAt, completedAt) ->
                new CompletedPhasedChange(planId, s, startedAt, completedAt));
  }

  @Provide
  Arbitrary<GlobalChangeOperation> globalChangeOperations() {
    return Arbitraries.of(
            ReflectUtil.implementationsOfSealedInterface(GlobalChangeOperation.class).toList())
        .flatMap(Arbitraries::forType);
  }

  @Provide
  Arbitrary<PartitionGroupOperation> partitionGroupChangeOperations() {
    return Arbitraries.of(
            ReflectUtil.implementationsOfSealedInterface(PartitionGroupOperation.class).toList())
        .flatMap(Arbitraries::forType);
  }

  @Provide
  Arbitrary<String> partitionGroupIds() {
    return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
  }

  /** Nanosecond-precision instants. The default precision in jqwik is seconds. */
  @Provide
  Arbitrary<Instant> nanoPrecisionInstants() {
    return DateTimes.instants()
        .between(Instant.ofEpochSecond(0), Instant.ofEpochSecond(4_000_000_000L))
        .ofPrecision(ChronoUnit.NANOS);
  }

  @SuppressWarnings("unused")
  static class SortedMapArbitraryProvider implements ArbitraryProvider {
    @Override
    public boolean canProvideFor(final TypeUsage targetType) {
      return targetType.isAssignableFrom(SortedMap.class);
    }

    @Override
    public Set<Arbitrary<?>> provideFor(
        final TypeUsage targetType, final SubtypeProvider subtypeProvider) {
      final TypeUsage keyType = targetType.getTypeArgument(0);
      final TypeUsage valueType = targetType.getTypeArgument(1);

      return subtypeProvider
          .resolveAndCombine(keyType, valueType)
          .map(
              arbitraries -> {
                final Arbitrary<?> keyArbitrary = arbitraries.get(0);
                final Arbitrary<?> valueArbitrary = arbitraries.get(1);
                return Arbitraries.maps(keyArbitrary, valueArbitrary).map(TreeMap::new);
              })
          .collect(CollectorsSupport.toLinkedHashSet());
    }
  }

  @SuppressWarnings("unused")
  static class SortedSetArbitraryProvider implements ArbitraryProvider {
    @Override
    public boolean canProvideFor(final TypeUsage targetType) {
      return targetType.isAssignableFrom(SortedSet.class);
    }

    @Override
    public Set<Arbitrary<?>> provideFor(
        final TypeUsage targetType, final SubtypeProvider subtypeProvider) {
      final TypeUsage elementType = targetType.getTypeArgument(0);
      final Set<Arbitrary<?>> elementArbitraries = subtypeProvider.apply(elementType);
      return elementArbitraries.stream()
          .map(arbitrary -> arbitrary.set().map(TreeSet::new))
          .collect(CollectorsSupport.toLinkedHashSet());
    }
  }

  private record PartialDependencyChangePlan(
      long id, ClusterChangePlan.Status status, Instant startedAt, OperationGraph graph) {}
}
