/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.dynamic.config.api.TestChangePlan.plannedOperations;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InternalError;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidState;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class RestoreRequestTransformerTest {

  private static final MemberId MEMBER = MemberId.from("0");

  private static RestoreRequest restoreRequest() {
    return new RestoreRequest(
        DEFAULT_PHYSICAL_TENANT_ID,
        new TenantRestoreArguments(
            new RestoreParameters(List.of(1L), null, null), "elasticsearch", false),
        false);
  }

  private static CurrentClusterConfiguration recoveringTopology() {
    return singleTenantCluster(
        Map.of(
            MEMBER,
            BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))
                .setMode(Mode.RECOVERING)));
  }

  /** The given members as a cluster with a single partition group on the default tenant. */
  private static CurrentClusterConfiguration singleTenantCluster(
      final Map<MemberId, BrokerPartitionState> members) {
    var configuration = CurrentClusterConfiguration.init();
    for (final var member : members.keySet()) {
      configuration =
          configuration.updateGlobalConfiguration(
              globalConfiguration ->
                  globalConfiguration.addMember(member, BrokerState.initializeAsActive()));
    }
    configuration = configuration.initPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
    for (final var member : members.entrySet()) {
      configuration =
          configuration.updatePartitionGroupConfig(
              DEFAULT_PHYSICAL_TENANT_ID,
              group -> group.addMember(member.getKey(), member.getValue()));
    }
    return configuration;
  }

  private static RestoreResolvedRequest resolvedRequest() {
    return new RestoreResolvedRequest(Map.of(1, new long[] {1L}), false);
  }

  private static ClusterConfigurationRequestValidator<RestoreRequest, RestoreResolvedRequest>
      validatorReturning(final Either<Exception, RestoreResolvedRequest> result) {
    return new ClusterConfigurationRequestValidator<>() {
      @Override
      public Class<RestoreRequest> requestType() {
        return RestoreRequest.class;
      }

      @Override
      public Either<Exception, RestoreResolvedRequest> validate(final RestoreRequest request) {
        return result;
      }
    };
  }

  private static RequestValidatorRegistry registryWithValidator(
      final ClusterConfigurationRequestValidator<RestoreRequest, RestoreResolvedRequest>
          validator) {
    final var registry = new RequestValidatorRegistry();
    registry.registerValidator(null, validator);
    return registry;
  }

  @Test
  void shouldRejectWhenClusterIsNotRecovering() {
    // given a cluster that is not in recovery, even though a validator would accept the request
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result =
        plannedOperations(
            transformer,
            CurrentClusterConfiguration.init().initPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID));

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldRejectWhenABrokerHoldingAPartitionToRestoreIsStillActive() {
    // given - both brokers hold the partition to restore, but only one of them is recovering
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var partitionState = Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()));
    final var topology =
        singleTenantCluster(
            Map.of(
                memberOne,
                BrokerPartitionState.initialize(partitionState),
                memberTwo,
                BrokerPartitionState.initialize(partitionState).setMode(Mode.RECOVERING)));
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result = plannedOperations(transformer, topology);

    // then - restoring a partition still being processed elsewhere is refused
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldRejectWhenNoValidatorIsRegistered() {
    // given
    final var transformer =
        new RestoreRequestTransformer(restoreRequest(), new RequestValidatorRegistry());

    // when
    final var result = plannedOperations(transformer, recoveringTopology());

    // then
    EitherAssert.assertThat(result).isLeft().left().isInstanceOf(InternalError.class);
  }

  @Test
  void shouldPropagateValidatorRejection() {
    // given
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.left(
                        new InvalidRequest("backupId and time range are mutually exclusive")))));

    // when
    final var result = plannedOperations(transformer, recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidRequest.class)
        .extracting(Exception::getMessage)
        .isEqualTo("backupId and time range are mutually exclusive");
  }

  @Test
  void shouldMapIllegalArgumentExceptionToInvalidRequest() {
    // given - the registered validator reports malformed requests as plain exceptions
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.left(new IllegalArgumentException("bad request parameters")))));

    // when
    final var result = plannedOperations(transformer, recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidRequest.class)
        .extracting(Exception::getMessage)
        .isEqualTo("bad request parameters");
  }

  @Test
  void shouldMapIllegalStateExceptionToInvalidState() {
    // given
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.left(new IllegalStateException("no common checkpoint")))));

    // when
    final var result = plannedOperations(transformer, recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidState.class)
        .extracting(Exception::getMessage)
        .isEqualTo("no common checkpoint");
  }

  @Test
  void shouldMapNoSuchElementExceptionToNotFound() {
    // given
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(Either.left(new NoSuchElementException("backup not found")))));

    // when
    final var result = plannedOperations(transformer, recoveringTopology());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(NotFound.class)
        .extracting(Exception::getMessage)
        .isEqualTo("backup not found");
  }

  @Test
  void shouldMapUnrecognizedExceptionToInternalError() {
    // given - a failure mode the validator was not expected to produce
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(Either.left(new RuntimeException("unexpected")))));

    // when
    final var result = plannedOperations(transformer, recoveringTopology());

    // then
    EitherAssert.assertThat(result).isLeft().left().isInstanceOf(InternalError.class);
  }

  @Test
  void shouldGeneratePhaseMajorRestorePlanForRecoveringMembersWithLocalPartitions() {
    // given - two recovering members, each replicating partition 1
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var partitionState = Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()));
    final var topology =
        singleTenantCluster(
            Map.of(
                memberOne,
                BrokerPartitionState.initialize(partitionState).setMode(Mode.RECOVERING),
                memberTwo,
                BrokerPartitionState.initialize(partitionState).setMode(Mode.RECOVERING)));
    final var resolved = new RestoreResolvedRequest(Map.of(1, new long[] {1L, 2L}), false);
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(), registryWithValidator(validatorReturning(Either.right(resolved))));

    // when
    final var result = plannedOperations(transformer, topology);

    // then - phase-major: all PreRestore, then all Restore, then exit recovery, then incarnation
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new PartitionPreRestoreOperation(memberOne, 1),
            new PartitionPreRestoreOperation(memberTwo, 1),
            new PartitionRestoreOperation(memberOne, 1, new TreeSet<>(List.of(1L, 2L))),
            new PartitionRestoreOperation(memberTwo, 1, new TreeSet<>(List.of(1L, 2L))),
            new ModeChangeOperation(memberOne, Mode.PROCESSING),
            new ModeChangeOperation(memberTwo, Mode.PROCESSING),
            new AwaitModeChangeOperation(memberOne, Mode.PROCESSING),
            new AwaitModeChangeOperation(memberTwo, Mode.PROCESSING),
            new UpdateIncarnationNumberOperation(memberOne));
  }

  @Test
  void shouldRestoreThePhysicalTenantsPartitionGroup() {
    // given - both brokers of the tenant's partition group replicate partition 1 and are recovering
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var resolved = new RestoreResolvedRequest(Map.of(1, new long[] {1L, 2L}), false);
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(), registryWithValidator(validatorReturning(Either.right(resolved))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, recovering(1),
                    memberTwo, recovering(1))));

    // then - one phase scoped to the tenant's own group, phase-major within it
    EitherAssert.assertThat(result).isRight();
    assertThat(groupOperationsOf(result.get()))
        .isEqualTo(
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                List.of(
                    new PartitionPreRestoreOperation(memberOne, 1),
                    new PartitionPreRestoreOperation(memberTwo, 1),
                    new PartitionRestoreOperation(memberOne, 1, new TreeSet<>(List.of(1L, 2L))),
                    new PartitionRestoreOperation(memberTwo, 1, new TreeSet<>(List.of(1L, 2L))),
                    new ModeChangeOperation(memberOne, Mode.PROCESSING),
                    new ModeChangeOperation(memberTwo, Mode.PROCESSING),
                    new AwaitModeChangeOperation(memberOne, Mode.PROCESSING),
                    new AwaitModeChangeOperation(memberTwo, Mode.PROCESSING),
                    new UpdateIncarnationNumberOperation(memberOne))));
  }

  @Test
  void shouldRestoreWhenABrokerHoldingNoPartitionOfTheTenantIsStillProcessing() {
    // given - memberTwo holds the partition to restore and is recovering, while memberOne holds no
    // partition of this tenant: the shape of a physical tenant hosted on a subset of the cluster's
    // brokers, where the partition-less broker is never transitioned into the tenant's recovery
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, BrokerPartitionState.initialize(Map.of()),
                    memberTwo, recovering(1))));

    // then - the restore is planned for the recovering broker alone
    EitherAssert.assertThat(result).isRight();
    assertThat(groupOperationsOf(result.get()))
        .isEqualTo(
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                List.of(
                    new PartitionPreRestoreOperation(memberTwo, 1),
                    new PartitionRestoreOperation(memberTwo, 1, new TreeSet<>(List.of(1L))),
                    new ModeChangeOperation(memberTwo, Mode.PROCESSING),
                    new AwaitModeChangeOperation(memberTwo, Mode.PROCESSING),
                    new UpdateIncarnationNumberOperation(memberTwo))));
  }

  @Test
  void shouldRejectWhenThePhysicalTenantIsNotRecovering() {
    // given - the tenant's group still processes partition 1, even though a validator would accept
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    MEMBER,
                    BrokerPartitionState.initialize(
                        Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))));

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldRejectWhenThePhysicalTenantIsUnknown() {
    // given - the cluster has no partition group for the tenant the request names
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result = transformer.phases(clusterWithoutPartitionGroups());

    // then - the caller gets 404 rather than a plan that can never apply
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(NotFound.class)
        .hasMessageContaining(DEFAULT_PHYSICAL_TENANT_ID);
  }

  private static BrokerPartitionState recovering(final int... partitionIds) {
    return BrokerPartitionState.initialize(
            Arrays.stream(partitionIds)
                .boxed()
                .collect(
                    Collectors.toMap(
                        Function.identity(),
                        ignored -> PartitionState.active(1, DynamicPartitionConfig.init()))))
        .setMode(Mode.RECOVERING);
  }

  private static CurrentClusterConfiguration clusterWithDefaultGroup(
      final Map<MemberId, BrokerPartitionState> members) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration(members.keySet()),
        Map.of(
            DEFAULT_PHYSICAL_TENANT_ID,
            new PartitionGroupConfiguration(
                1, 0, members, Optional.empty(), Optional.empty(), Optional.empty())),
        PhasedChangeState.empty());
  }

  private static CurrentClusterConfiguration clusterWithoutPartitionGroups() {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration(Set.of(MEMBER)),
        Map.of(),
        PhasedChangeState.empty());
  }

  private static GlobalConfiguration globalConfiguration(final Set<MemberId> members) {
    return new GlobalConfiguration(
        1,
        Optional.empty(),
        members.stream()
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    member -> new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE))),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * The point of the dependency-graph model is which operations may run at the same time. The
   * assertions above pin <em>which</em> operations a restore produces; these two pin the
   * concurrency, which is the part that changed.
   */
  @Test
  void shouldOfferEveryBrokerAndPartitionAtOnce() {
    // given - two brokers, each recovering the same two partitions
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.right(
                        new RestoreResolvedRequest(
                            Map.of(1, new long[] {1L}, 2, new long[] {2L}), false)))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, recovering(1, 2),
                    memberTwo, recovering(1, 2))));

    // then - a plan started from this graph offers all four pre-restores at once: both brokers,
    // both partitions. Under the queue those were four serialised round trips.
    EitherAssert.assertThat(result).isRight();
    final var plan = DependencyChangePlan.init(1L, graphOf(result.get()));
    assertThat(plan.runnableFor(memberOne)).hasSize(2);
    assertThat(plan.runnableFor(memberTwo)).hasSize(2);
    assertThat(plan.runnableFor(memberOne).values())
        .allSatisfy(
            operation -> assertThat(operation).isInstanceOf(PartitionPreRestoreOperation.class));
  }

  @Test
  void shouldWaitOnlyForItsOwnBrokersPreRestoreOfThatPartition() {
    // given - two brokers replicating partition 1. Wiping and reloading a broker's own copy is
    // local to that broker, so a restore is ordered behind exactly one pre-restore: its own.
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, recovering(1),
                    memberTwo, recovering(1))));

    // then - each restore depends on the single pre-restore of the same broker and partition, so
    // neither broker is held up by the other's wipe
    EitherAssert.assertThat(result).isRight();
    final var graph = graphOf(result.get());
    assertThat(idsOf(graph, PartitionRestoreOperation.class))
        .hasSize(2)
        .allSatisfy(
            restore -> {
              final var dependsOn = graph.operations().get(restore).dependsOn();
              assertThat(dependsOn).hasSize(1);
              final var preRestore = dependsOn.first();
              assertThat(graph.operations().get(preRestore).operation())
                  .isInstanceOf(PartitionPreRestoreOperation.class);
              assertThat(memberOf(graph, preRestore)).isEqualTo(memberOf(graph, restore));
              assertThat(partitionOf(graph, preRestore)).isEqualTo(partitionOf(graph, restore));
            });
  }

  @Test
  void shouldNotOrderOnePartitionBehindAnother() {
    // given - two partitions on two brokers. Partition 2 shares nothing with partition 1, so
    // holding its restore behind partition 1's wipe would serialise work that cannot interfere.
    // This is the difference from a plan-wide barrier, and where the restore's I/O actually is.
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.right(
                        new RestoreResolvedRequest(
                            Map.of(1, new long[] {1L}, 2, new long[] {2L}), false)))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, recovering(1, 2),
                    memberTwo, recovering(1, 2))));

    // then - completing only partition 1's two pre-restores releases partition 1's restores, while
    // partition 2 is still waiting on its own
    EitherAssert.assertThat(result).isRight();
    final var graph = graphOf(result.get());
    var group =
        new PartitionGroupConfiguration(
                1, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty())
            .startGraphConfigurationChange(graph);
    for (final var preRestore : idsOf(graph, PartitionPreRestoreOperation.class)) {
      if (partitionOf(graph, preRestore) == 1 && memberOf(graph, preRestore).equals(memberOne)) {
        group = group.completeOperation(preRestore, UnaryOperator.identity());
      }
    }

    final var released =
        group.pendingChanges().orElseThrow().runnableFor(memberOne).values().stream()
            .filter(PartitionRestoreOperation.class::isInstance)
            .map(PartitionRestoreOperation.class::cast)
            .toList();
    assertThat(released).singleElement().returns(1, PartitionRestoreOperation::partitionId);
  }

  @Test
  void shouldOrderAModeChangeBehindTheRestoresOfOnlyItsOwnPartitions() {
    // given - an asymmetric group: member 0 holds partition 1 alone, member 1 holds both. A broker
    // leaves recovery as soon as the partitions it holds are back, so partition 2 must not block
    // member 0, which does not host it. This edge is what keeps the mode changes broker-scoped
    // instead of turning them into a second cluster-wide barrier - the whole point of the graph.
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.right(
                        new RestoreResolvedRequest(
                            Map.of(1, new long[] {1L}, 2, new long[] {2L}), false)))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, recovering(1),
                    memberTwo, recovering(1, 2))));

    // then - each mode change waits for the restores of its own partitions on every broker, and for
    // nothing else. Partition 1 is replicated by both brokers, partition 2 only by member 1.
    EitherAssert.assertThat(result).isRight();
    final var graph = graphOf(result.get());
    final var restoresOfPartitionOne = restoresOfPartition(graph, 1);
    final var restoresOfPartitionTwo = restoresOfPartition(graph, 2);
    assertThat(restoresOfPartitionOne).hasSize(2);
    assertThat(restoresOfPartitionTwo).hasSize(1);

    final var modeChangeOf = modeChangesByMember(graph);
    assertThat(graph.operations().get(modeChangeOf.get(memberOne)).dependsOn())
        .describedAs("dependencies of the mode change on the broker holding partition 1 only")
        .containsExactlyInAnyOrderElementsOf(restoresOfPartitionOne);
    assertThat(graph.operations().get(modeChangeOf.get(memberTwo)).dependsOn())
        .describedAs("dependencies of the mode change on the broker holding both partitions")
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(restoresOfPartitionOne.stream(), restoresOfPartitionTwo.stream())
                .toList());
  }

  @Test
  void shouldAwaitTheModeChangeOnlyAfterEveryRestoreEverywhere() {
    // given - two brokers, two partitions. Restores are narrowed per partition, but awaiting the
    // transition observes the group as a whole, so this stage is a cluster-wide barrier: no broker
    // may start awaiting while any partition anywhere is still reloading.
    //
    // The barrier also covers every mode change, including the awaiting broker's own. An earlier
    // version of the edge rules derived an await's dependencies from the brokers it shares a
    // partition with, leaving a broker that shares none unordered against its own mode change. Both
    // write that broker's member entry, so the sub-configuration merge would keep one by version
    // and
    // silently drop the other. Nothing rejects that shape any more, so it is pinned here.
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.right(
                        new RestoreResolvedRequest(
                            Map.of(1, new long[] {1L}, 2, new long[] {2L}), false)))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, recovering(1, 2),
                    memberTwo, recovering(1, 2))));

    // then - every await waits for all four restores and for both mode changes, and for nothing
    // else: the pre-restores are already covered transitively by the restores
    EitherAssert.assertThat(result).isRight();
    final var graph = graphOf(result.get());
    final var restores = idsOf(graph, PartitionRestoreOperation.class);
    final var modeChanges = idsOf(graph, ModeChangeOperation.class);
    assertThat(restores).hasSize(4);
    assertThat(modeChanges).hasSize(2);
    assertThat(idsOf(graph, AwaitModeChangeOperation.class))
        .hasSize(2)
        .allSatisfy(
            await ->
                assertThat(graph.operations().get(await).dependsOn())
                    .describedAs("dependencies of the await on %s", memberOf(graph, await))
                    .containsExactlyInAnyOrderElementsOf(
                        Stream.concat(restores.stream(), modeChanges.stream()).toList()));
  }

  @Test
  void shouldOrderTheIncarnationBumpAfterEveryAwait() {
    // given - two brokers, two partitions. The incarnation number is the group's own state, written
    // once the restore is done. Bumping it while any broker is still observing its transition would
    // advertise a recovery that has not finished, so it is ordered behind every await - not just
    // behind one of them, and not behind the restores alone.
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(
                validatorReturning(
                    Either.right(
                        new RestoreResolvedRequest(
                            Map.of(1, new long[] {1L}, 2, new long[] {2L}), false)))));

    // when
    final var result =
        transformer.phases(
            clusterWithDefaultGroup(
                Map.of(
                    memberOne, recovering(1, 2),
                    memberTwo, recovering(1, 2))));

    // then - one bump, waiting for every await and for nothing else
    EitherAssert.assertThat(result).isRight();
    final var graph = graphOf(result.get());
    final var awaits = idsOf(graph, AwaitModeChangeOperation.class);
    assertThat(awaits).hasSize(2);
    assertThat(idsOf(graph, UpdateIncarnationNumberOperation.class))
        .singleElement()
        .satisfies(
            bump ->
                assertThat(graph.operations().get(bump).dependsOn())
                    .describedAs("dependencies of the incarnation bump")
                    .containsExactlyInAnyOrderElementsOf(awaits));
  }

  private static MemberId memberOf(final OperationGraph graph, final OperationId operationId) {
    return graph.operations().get(operationId).operation().memberId();
  }

  private static int partitionOf(final OperationGraph graph, final OperationId operationId) {
    return ((PartitionChangeOperation) graph.operations().get(operationId).operation())
        .partitionId();
  }

  /** The restores of the given partition, one per broker replicating it. */
  private static List<OperationId> restoresOfPartition(
      final OperationGraph graph, final int partitionId) {
    return idsOf(graph, PartitionRestoreOperation.class).stream()
        .filter(restore -> partitionOf(graph, restore) == partitionId)
        .toList();
  }

  private static Map<MemberId, OperationId> modeChangesByMember(final OperationGraph graph) {
    final var modeChangeOf = new HashMap<MemberId, OperationId>();
    idsOf(graph, ModeChangeOperation.class)
        .forEach(id -> modeChangeOf.put(memberOf(graph, id), id));
    return modeChangeOf;
  }

  private static OperationGraph graphOf(final List<Phase> phases) {
    assertThat(phases).singleElement().isInstanceOf(PartitionGroupPhase.class);
    return ((PartitionGroupPhase) phases.getFirst()).groupGraphs().get(DEFAULT_PHYSICAL_TENANT_ID);
  }

  private static List<OperationId> idsOf(
      final OperationGraph graph, final Class<? extends ClusterConfigurationChangeOperation> type) {
    return graph.operations().entrySet().stream()
        .filter(entry -> type.isInstance(entry.getValue().operation()))
        .map(Entry::getKey)
        .toList();
  }

  /**
   * The operations each group's graph holds, in plan order. The graph's dependency structure is
   * covered separately; these assertions are about which operations a request produces.
   */
  private static Map<String, List<PartitionGroupOperation>> groupOperationsOf(
      final List<Phase> phases) {
    assertThat(phases).singleElement().isInstanceOf(PartitionGroupPhase.class);
    return ((PartitionGroupPhase) phases.getFirst()).groupOperations();
  }
}
