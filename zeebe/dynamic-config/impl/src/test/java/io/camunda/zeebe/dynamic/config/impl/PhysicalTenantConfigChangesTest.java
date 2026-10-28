/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PhysicalTenantConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.OperationNotAllowed;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ApplyPhysicalTenantConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.StopPhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PhysicalTenantConfigChangesTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");
  private static final byte[] CONFIGURATION = "a=1".getBytes(StandardCharsets.UTF_8);
  private static final String SHA = PhysicalTenantConfigStore.sha256(CONFIGURATION);

  private final PhysicalTenantConfigChanges changes =
      new PhysicalTenantConfigChanges(MEMBER_0, () -> "incarnation");

  @Test
  void shouldCreateATenantRunningItOnEveryMemberBeforeBootstrappingItsPartitions() {
    // when
    final var result =
        changes
            .apply(cluster(), create("tenant", Owner.API, 2), SHA)
            .get()
            .partitionGroup("tenant");

    // then
    assertThat(result.tenantConfig())
        .contains(PhysicalTenantConfigReference.initial(SHA, Owner.API, "incarnation"));
    final var operations = result.pendingChanges().orElseThrow().pendingOperations();
    assertThat(operations.subList(0, 3))
        .containsExactly(
            new ApplyPhysicalTenantConfigOperation(MEMBER_0, 1),
            new ApplyPhysicalTenantConfigOperation(MEMBER_1, 1),
            new ApplyPhysicalTenantConfigOperation(MEMBER_2, 1));
    assertThat(operations.get(3)).isInstanceOf(PartitionBootstrapOperation.class);
    assertThat(operations.subList(3, operations.size()))
        .noneMatch(ApplyPhysicalTenantConfigOperation.class::isInstance);
  }

  @Test
  void shouldRegisterATenantProvisionedFromTheFileWithoutAChange() {
    // when
    final var result =
        changes
            .apply(cluster(), create("default", Owner.FILE, 0), SHA)
            .get()
            .partitionGroup("default");

    // then
    assertThat(result.tenantConfig())
        .map(PhysicalTenantConfigReference::owner)
        .contains(Owner.FILE);
    assertThat(result.pendingChanges()).isEmpty();
  }

  @Test
  void shouldRejectCreatingATenantThatExists() {
    // given
    final var created = changes.apply(cluster(), create("tenant", Owner.API, 1), SHA).get();

    // when
    final var result =
        changes.apply(completed(created, "tenant"), create("tenant", Owner.API, 1), SHA);

    // then
    assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void shouldRejectCreatingATenantWhoseIdWasRemoved() {
    // given
    final var removed =
        cluster().updatePartitionGroupConfig("default", group -> group.disable().remove());

    // when
    final var result = changes.apply(removed, create("default", Owner.FILE, 1), SHA);

    // then
    assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("stays reserved");
  }

  @Test
  void shouldUpdateToTheNextVersionAndApplyItOnEveryMember() {
    // given
    final var created = completed(created("tenant", Owner.API), "tenant");

    // when
    final var result =
        changes
            .apply(
                created,
                PhysicalTenantConfigRequest.update("tenant", Owner.API, bytes("a=2"), 1),
                sha("a=2"))
            .get()
            .partitionGroup("tenant");

    // then
    assertThat(result.tenantConfig().orElseThrow().version()).isEqualTo(2);
    assertThat(result.pendingChanges().orElseThrow().pendingOperations())
        .containsExactly(
            new ApplyPhysicalTenantConfigOperation(MEMBER_0, 2),
            new ApplyPhysicalTenantConfigOperation(MEMBER_1, 2),
            new ApplyPhysicalTenantConfigOperation(MEMBER_2, 2));
  }

  @Test
  void shouldNotChangeAnythingForAnUpdateWithTheSameConfiguration() {
    // given
    final var created = completed(created("tenant", Owner.API), "tenant");

    // when
    final var result =
        changes.apply(
            created,
            PhysicalTenantConfigRequest.update("tenant", Owner.API, CONFIGURATION, 0),
            SHA);

    // then
    assertThat(result.get()).isEqualTo(created);
  }

  @Test
  void shouldRejectAnUpdateValidatedAgainstAnOlderVersion() {
    // given
    final var created = completed(created("tenant", Owner.API), "tenant");

    // when
    final var result =
        changes.apply(
            created,
            PhysicalTenantConfigRequest.update("tenant", Owner.API, bytes("a=2"), 7),
            sha("a=2"));

    // then
    assertThat(result.getLeft()).isInstanceOf(ConcurrentModificationException.class);
  }

  @Test
  void shouldRejectAChangeThroughAnInputThatDoesNotOwnTheTenant() {
    // given
    final var created = created("tenant", Owner.FILE);

    // when
    final var update =
        changes.apply(
            created,
            PhysicalTenantConfigRequest.update("tenant", Owner.API, bytes("a=2"), 0),
            sha("a=2"));
    final var delete =
        changes.apply(created, PhysicalTenantConfigRequest.delete("tenant", Owner.API), "");

    // then
    assertThat(update.getLeft())
        .isInstanceOf(OperationNotAllowed.class)
        .hasMessageContaining("owned by FILE");
    assertThat(delete.getLeft()).isInstanceOf(OperationNotAllowed.class);
  }

  @Test
  void shouldRejectAChangeOfAnUnknownTenant() {
    // when
    final var result =
        changes.apply(
            cluster(),
            PhysicalTenantConfigRequest.update("unknown", Owner.API, CONFIGURATION, 0),
            SHA);

    // then
    assertThat(result.getLeft()).isInstanceOf(NotFound.class);
  }

  @Test
  void shouldRejectAChangeWhileAnotherTenantChanges() {
    // given
    final var creating = created("tenant", Owner.API);

    // when
    final var result = changes.apply(creating, create("other", Owner.API, 1), SHA);

    // then
    assertThat(result.getLeft())
        .isInstanceOf(ConcurrentModificationException.class)
        .hasMessageContaining("one at a time");
  }

  @Test
  void shouldDisableAndStopADeletedTenantOnEveryMemberBeforeRemovingIt() {
    // given
    final var created = completed(created("tenant", Owner.API), "tenant");

    // when
    final var result =
        changes
            .apply(created, PhysicalTenantConfigRequest.delete("tenant", Owner.API), "")
            .get()
            .partitionGroup("tenant");

    // then
    assertThat(result.isDisabled()).isTrue();
    assertThat(result.pendingChanges().orElseThrow().pendingOperations())
        .containsExactly(
            new StopPhysicalTenantOperation(MEMBER_0),
            new StopPhysicalTenantOperation(MEMBER_1),
            new StopPhysicalTenantOperation(MEMBER_2),
            new RemovePhysicalTenantOperation(MEMBER_0));
  }

  private CurrentClusterConfiguration created(final String tenantId, final Owner owner) {
    final var registered = owner == Owner.FILE ? cluster().initPartitionGroup(tenantId) : cluster();
    return changes.apply(registered, create(tenantId, owner, 1), SHA).get();
  }

  private static CurrentClusterConfiguration completed(
      final CurrentClusterConfiguration configuration, final String tenantId) {
    return configuration.updatePartitionGroupConfig(
        tenantId,
        group ->
            new PartitionGroupConfiguration(
                group.version() + 1,
                group.incarnationNumber(),
                group.members(),
                group.routingState(),
                Optional.empty(),
                group.lastChange(),
                group.availability(),
                group.tenantConfig()));
  }

  private static PhysicalTenantConfigRequest create(
      final String tenantId, final Owner owner, final int partitionCount) {
    return PhysicalTenantConfigRequest.create(
        tenantId, owner, CONFIGURATION, partitionCount, Set.of());
  }

  private static CurrentClusterConfiguration cluster() {
    final var members = Set.of(MEMBER_0, MEMBER_1, MEMBER_2);
    final var partition =
        new PartitionMetadata(
            new PartitionId("default", 1),
            members,
            Map.of(MEMBER_0, 3, MEMBER_1, 2, MEMBER_2, 1),
            3,
            MEMBER_0);
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        members,
        Set.of(partition),
        Map.of(
            "default",
            new DynamicPartitionConfig(new ExportingConfig(ExportingState.EXPORTING, Map.of()))),
        "cluster");
  }

  private static byte[] bytes(final String configuration) {
    return configuration.getBytes(StandardCharsets.UTF_8);
  }

  private static String sha(final String configuration) {
    return PhysicalTenantConfigStore.sha256(bytes(configuration));
  }
}
