/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
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
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupGraphPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  private static ClusterConfiguration recoveringTopology() {
    return ClusterConfiguration.init()
        .addMember(
            MEMBER,
            MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))
                .toRecovering());
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
    final var result = transformer.operations(ClusterConfiguration.init());

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldRejectWhenABrokerWithoutPartitionsIsStillActive() {
    // given - memberOne holds no partition and is still active, while memberTwo holds the partition
    // to restore and is recovering. On the legacy model recovery is a broker-wide state that every
    // active broker enters, so memberOne having stayed behind means the cluster is not recovering
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var partitionState = Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()));
    final var topology =
        ClusterConfiguration.init()
            .addMember(memberOne, MemberState.initializeAsActive(Map.of()))
            .addMember(memberTwo, MemberState.initializeAsActive(partitionState).toRecovering());
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result = transformer.operations(topology);

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
        ClusterConfiguration.init()
            .addMember(memberOne, MemberState.initializeAsActive(partitionState))
            .addMember(memberTwo, MemberState.initializeAsActive(partitionState).toRecovering());
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(),
            registryWithValidator(validatorReturning(Either.right(resolvedRequest()))));

    // when
    final var result = transformer.operations(topology);

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
    final var result = transformer.operations(recoveringTopology());

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
    final var result = transformer.operations(recoveringTopology());

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
    final var result = transformer.operations(recoveringTopology());

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
    final var result = transformer.operations(recoveringTopology());

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
    final var result = transformer.operations(recoveringTopology());

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
    final var result = transformer.operations(recoveringTopology());

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
        ClusterConfiguration.init()
            .addMember(memberOne, MemberState.initializeAsActive(partitionState).toRecovering())
            .addMember(memberTwo, MemberState.initializeAsActive(partitionState).toRecovering());
    final var resolved = new RestoreResolvedRequest(Map.of(1, new long[] {1L, 2L}), false);
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(), registryWithValidator(validatorReturning(Either.right(resolved))));

    // when
    final var result = transformer.operations(topology);

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
  void shouldSkipPartitionOperationsForRecoveringMemberWithNoLocalPartitions() {
    // given - memberOne (sorted first) has no local partitions, memberTwo has partition 1
    final var memberOne = MemberId.from("0");
    final var memberTwo = MemberId.from("1");
    final var partitionState = Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()));
    final var topology =
        ClusterConfiguration.init()
            .addMember(memberOne, MemberState.initializeAsActive(Map.of()).toRecovering())
            .addMember(memberTwo, MemberState.initializeAsActive(partitionState).toRecovering());
    final var resolved = new RestoreResolvedRequest(Map.of(1, new long[] {1L, 2L}), false);
    final var transformer =
        new RestoreRequestTransformer(
            restoreRequest(), registryWithValidator(validatorReturning(Either.right(resolved))));

    // when
    final var result = transformer.operations(topology);

    // then - memberOne contributes no Pre/Restore operations but still gets the tail operations
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new PartitionPreRestoreOperation(memberTwo, 1),
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
  void shouldHoldTheStageBoundaryAcrossBrokers() {
    // given - the conservative stage edges: no restore may start until every pre-restore has
    // finished, on any broker. Partitions are replicated, and nothing in the current code
    // establishes that one broker may restore while a peer is still wiping the same partition.
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

    // then - each restore waits for both pre-restores, not only for its own broker's
    EitherAssert.assertThat(result).isRight();
    final var graph = graphOf(result.get());
    final var preRestores = idsOf(graph, PartitionPreRestoreOperation.class);
    assertThat(preRestores).hasSize(2);
    assertThat(idsOf(graph, PartitionRestoreOperation.class))
        .hasSize(2)
        .allSatisfy(
            restore ->
                assertThat(graph.operations().get(restore).dependsOn())
                    .containsExactlyElementsOf(preRestores));
  }

  private static OperationGraph graphOf(final List<Phase> phases) {
    assertThat(phases).singleElement().isInstanceOf(PartitionGroupGraphPhase.class);
    return ((PartitionGroupGraphPhase) phases.getFirst())
        .groupGraphs()
        .get(DEFAULT_PHYSICAL_TENANT_ID);
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
    assertThat(phases).singleElement().isInstanceOf(PartitionGroupGraphPhase.class);
    return ((PartitionGroupGraphPhase) phases.getFirst()).groupOperations();
  }
}
