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
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    final var result = plannedOperations(transformer, ClusterConfiguration.init());

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
        ClusterConfiguration.init()
            .addMember(memberOne, MemberState.initializeAsActive(partitionState).toRecovering())
            .addMember(memberTwo, MemberState.initializeAsActive(partitionState).toRecovering());
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
    assertThat(result.get())
        .containsExactly(
            new PartitionGroupParallelPhase(
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
                        new UpdateIncarnationNumberOperation(memberOne)))));
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
    assertThat(result.get())
        .containsExactly(
            new PartitionGroupParallelPhase(
                Map.of(
                    DEFAULT_PHYSICAL_TENANT_ID,
                    List.of(
                        new PartitionPreRestoreOperation(memberTwo, 1),
                        new PartitionRestoreOperation(memberTwo, 1, new TreeSet<>(List.of(1L))),
                        new ModeChangeOperation(memberTwo, Mode.PROCESSING),
                        new AwaitModeChangeOperation(memberTwo, Mode.PROCESSING),
                        new UpdateIncarnationNumberOperation(memberTwo)))));
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

  private static BrokerPartitionState recovering(final int partitionId) {
    return BrokerPartitionState.initialize(
            Map.of(partitionId, PartitionState.active(1, DynamicPartitionConfig.init())))
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
}
