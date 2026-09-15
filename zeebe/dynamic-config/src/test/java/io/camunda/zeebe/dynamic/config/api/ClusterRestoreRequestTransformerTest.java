/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration.DEFAULT_GROUP;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class ClusterRestoreRequestTransformerTest {

  private static final MemberId MEMBER = MemberId.from("0");
  private static final String TENANT_B = "tenant-b";
  private static final String TENANT_C = "tenant-c";

  @Test
  void shouldRestoreOnlyTheNamedPhysicalTenantOnTheMultiGroupModel() {
    // given — a broker that replicates a partition of all three physical tenants, all in recovery,
    // but the request only names tenant-b
    final var request = clusterRestoreRequest(Map.of(TENANT_B, args(55L)));
    final var transformer = new ClusterRestoreRequestTransformer(request, registryOf(1, 2, 3));

    // when
    final var result = transformer.phases(threeTenantCluster());

    // then — one phase, scoped to tenant-b's own partition; the other two tenants are untouched
    EitherAssert.assertThat(result).isRight();
    assertThat(groupOperationsOf(result.get()))
        .isEqualTo(Map.of(TENANT_B, tenantBRestoreOperations(List.of(55L))));
  }

  @Test
  void shouldRestoreEveryNamedPhysicalTenantInOnePhase() {
    // given — a cluster-wide request expanded to every known physical tenant, as
    // ClusterRecoveryServices builds it, each with its own backup selection
    final var request =
        clusterRestoreRequest(
            Map.of(DEFAULT_GROUP, args(100L), TENANT_B, args(55L), TENANT_C, args(77L)));
    final var transformer = new ClusterRestoreRequestTransformer(request, registryOf(1, 2, 3));

    // when
    final var result = transformer.phases(threeTenantCluster());

    // then — all three physical tenants restore in the same phase, each from its own backup
    EitherAssert.assertThat(result).isRight();
    assertThat(groupOperationsOf(result.get()))
        .isEqualTo(
            Map.of(
                DEFAULT_GROUP, defaultRestoreOperations(List.of(100L)),
                TENANT_B, tenantBRestoreOperations(List.of(55L)),
                TENANT_C, tenantCRestoreOperations(List.of(77L))));
  }

  @Test
  void shouldRestoreATenantHostedOnASubsetOfTheClusterBrokers() {
    // given — tenant-b lives on one broker only and is in recovery, while the other broker keeps
    // processing the default tenant. The projection of tenant-b's group still carries that other
    // broker on its cluster-wide ACTIVE state, since a broker holding no partition of the group
    // never transitions into recovery
    final var request = clusterRestoreRequest(Map.of(TENANT_B, args(55L)));
    final var transformer = new ClusterRestoreRequestTransformer(request, registryOf(2));

    // when
    final var result = transformer.phases(tenantOnSubsetOfBrokersCluster());

    // then — the restore is planned rather than refused as a cluster that is not recovering
    EitherAssert.assertThat(result).isRight();
    assertThat(groupOperationsOf(result.get()))
        .isEqualTo(Map.of(TENANT_B, tenantBRestoreOperations(List.of(55L))));
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenantOnTheMultiGroupModel() {
    // given — the request names a physical tenant the cluster does not have
    final var request = clusterRestoreRequest(Map.of("unknown-tenant", args(55L)));
    final var transformer = new ClusterRestoreRequestTransformer(request, registryOf(1, 2, 3));

    // when
    final var result = transformer.phases(threeTenantCluster());

    // then — the caller gets 404 rather than a plan that can never apply
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(NotFound.class)
        .hasMessageContaining("unknown-tenant");
  }

  private static ClusterRestoreRequest clusterRestoreRequest(
      final Map<String, TenantRestoreArguments> tenantArguments) {
    return new ClusterRestoreRequest(tenantArguments, false);
  }

  private static TenantRestoreArguments args(final long backupId) {
    return new TenantRestoreArguments(
        new RestoreParameters(List.of(backupId), null, null), "elasticsearch", false);
  }

  private static List<PartitionGroupOperation> defaultRestoreOperations(
      final List<Long> backupIds) {
    return List.of(
        new PartitionPreRestoreOperation(MEMBER, 1),
        new PartitionRestoreOperation(MEMBER, 1, new TreeSet<>(backupIds)),
        new ModeChangeOperation(MEMBER, Mode.PROCESSING),
        new AwaitModeChangeOperation(MEMBER, Mode.PROCESSING),
        new UpdateIncarnationNumberOperation(MEMBER));
  }

  private static List<PartitionGroupOperation> tenantBRestoreOperations(
      final List<Long> backupIds) {
    return List.of(
        new PartitionPreRestoreOperation(MEMBER, 2),
        new PartitionRestoreOperation(MEMBER, 2, new TreeSet<>(backupIds)),
        new ModeChangeOperation(MEMBER, Mode.PROCESSING),
        new AwaitModeChangeOperation(MEMBER, Mode.PROCESSING),
        new UpdateIncarnationNumberOperation(MEMBER));
  }

  private static List<PartitionGroupOperation> tenantCRestoreOperations(
      final List<Long> backupIds) {
    return List.of(
        new PartitionPreRestoreOperation(MEMBER, 3),
        new PartitionRestoreOperation(MEMBER, 3, new TreeSet<>(backupIds)),
        new ModeChangeOperation(MEMBER, Mode.PROCESSING),
        new AwaitModeChangeOperation(MEMBER, Mode.PROCESSING),
        new UpdateIncarnationNumberOperation(MEMBER));
  }

  /**
   * The same broker replicates partition 1 for the default physical tenant, partition 2 for
   * tenant-b and partition 3 for tenant-c, all in recovery — modeling one broker set serving more
   * than two physical tenants.
   */
  private static CurrentClusterConfiguration threeTenantCluster() {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(MEMBER, new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()),
        Map.of(
            DEFAULT_GROUP, group(1, Mode.RECOVERING),
            TENANT_B, group(2, Mode.RECOVERING),
            TENANT_C, group(3, Mode.RECOVERING)),
        PhasedChangeState.empty());
  }

  /**
   * A cluster of two brokers where only {@link #MEMBER} hosts tenant-b — the shape of a physical
   * tenant placed on a subset of the cluster's brokers. Tenant-b is in recovery; the default
   * tenant, hosted on the other broker alone, keeps processing.
   */
  private static CurrentClusterConfiguration tenantOnSubsetOfBrokersCluster() {
    final var otherMember = MemberId.from("1");
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                MEMBER,
                new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE),
                otherMember,
                new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()),
        Map.of(
            DEFAULT_GROUP, group(otherMember, 1, Mode.PROCESSING),
            TENANT_B, group(MEMBER, 2, Mode.RECOVERING)),
        PhasedChangeState.empty());
  }

  private static PartitionGroupConfiguration group(final int partitionId, final Mode mode) {
    return group(MEMBER, partitionId, mode);
  }

  private static PartitionGroupConfiguration group(
      final MemberId member, final int partitionId, final Mode mode) {
    final var partitions =
        Map.of(partitionId, PartitionState.active(1, DynamicPartitionConfig.init()));
    return new PartitionGroupConfiguration(
        1,
        0,
        Map.of(member, BrokerPartitionState.initialize(partitions).setMode(mode)),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /** Resolves every request to the backups it asked for, on the single partition of the member. */
  private static RequestValidatorRegistry registry() {
    return registryOf(1);
  }

  /**
   * Resolves every request to the backups it asked for, on every partition id given — a real
   * validator only ever resolves the partitions of the physical tenant it validates for, but the
   * fan-out test drives more than one, so this fake simply answers for all of them.
   */
  private static RequestValidatorRegistry registryOf(final int... partitionIds) {
    final var registry = new RequestValidatorRegistry();
    registry.registerValidator(
        null,
        new ClusterConfigurationRequestValidator<RestoreRequest, RestoreResolvedRequest>() {
          @Override
          public Class<RestoreRequest> requestType() {
            return RestoreRequest.class;
          }

          @Override
          public Either<Exception, RestoreResolvedRequest> validate(final RestoreRequest request) {
            final var backupIds =
                request.arguments().parameters().backupIds().stream()
                    .mapToLong(Long::longValue)
                    .toArray();
            final var backups = new HashMap<Integer, long[]>();
            for (final int partitionId : partitionIds) {
              backups.put(partitionId, backupIds);
            }
            return Either.right(new RestoreResolvedRequest(backups, false));
          }
        });
    return registry;
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
