/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ClusterPurgeRequestTransformerTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldPurgePhysicalTenant() {
    // given
    final var transformer = new PurgeRequestTransformer();
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A,
                group()
                    .addMember(id0, member(Map.of(0, active(2), 1, active(1))))
                    .addMember(id1, member(Map.of(0, active(1), 1, active(2))))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — every leave except a partition's last is preceded by a demotion; the last replica
    // must keep its one-shot leave since demoting it would leave the group without a voting member
    assertThat(operationsOf(result, TENANT_A))
        .containsExactly(
            new PartitionDemoteOperation(id0, 0),
            new PartitionLeaveOperation(id0, 0, 0),
            new PartitionDemoteOperation(id0, 1),
            new PartitionLeaveOperation(id0, 1, 0),
            new PartitionLeaveOperation(id1, 0, 0),
            new PartitionLeaveOperation(id1, 1, 0),
            new DeleteHistoryOperation(id0),
            new UpdateIncarnationNumberOperation(id0),
            new PartitionBootstrapOperation(id0, 0, 2, Optional.of(partitionConfig), false),
            new PartitionBootstrapOperation(id1, 1, 2, Optional.of(partitionConfig), false),
            new PartitionJoinOperation(id1, 0, 1),
            new PartitionPromoteOperation(id1, 0),
            new PartitionJoinOperation(id0, 1, 1),
            new PartitionPromoteOperation(id0, 1));
  }

  @Test
  void purgeShouldBootstrapPartitionsInOrder() {
    // given
    final var transformer = new PurgeRequestTransformer();
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A,
                group()
                    .addMember(id0, member(Map.of(0, active(2), 1, active(1), 2, active(2))))
                    .addMember(id1, member(Map.of(0, active(1), 1, active(2), 2, active(1))))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    assertThat(operationsOf(result, TENANT_A))
        .containsExactly(
            new PartitionDemoteOperation(id0, 0),
            new PartitionLeaveOperation(id0, 0, 0),
            new PartitionDemoteOperation(id0, 1),
            new PartitionLeaveOperation(id0, 1, 0),
            new PartitionDemoteOperation(id0, 2),
            new PartitionLeaveOperation(id0, 2, 0),
            new PartitionLeaveOperation(id1, 0, 0),
            new PartitionLeaveOperation(id1, 1, 0),
            new PartitionLeaveOperation(id1, 2, 0),
            new DeleteHistoryOperation(id0),
            new UpdateIncarnationNumberOperation(id0),
            new PartitionBootstrapOperation(id0, 0, 2, Optional.of(partitionConfig), false),
            new PartitionBootstrapOperation(id1, 1, 2, Optional.of(partitionConfig), false),
            new PartitionBootstrapOperation(id0, 2, 2, Optional.of(partitionConfig), false),
            new PartitionJoinOperation(id1, 0, 1),
            new PartitionPromoteOperation(id1, 0),
            new PartitionJoinOperation(id0, 1, 1),
            new PartitionPromoteOperation(id0, 1),
            new PartitionJoinOperation(id1, 2, 1),
            new PartitionPromoteOperation(id1, 2));
  }

  @Test
  void shouldPurgeEveryPhysicalTenantWhenNoneIsGiven() {
    // given
    final var transformer = new PurgeRequestTransformer();
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, group().addMember(id0, member(Map.of(0, active(1)))),
                TENANT_B, group().addMember(id1, member(Map.of(0, active(1))))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — both tenants are purged, each within its own group
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations()).containsOnlyKeys(TENANT_A, TENANT_B);
    assertThat(phase.groupOperations().get(TENANT_A))
        .containsExactly(
            new PartitionLeaveOperation(id0, 0, 0),
            new DeleteHistoryOperation(id0),
            new UpdateIncarnationNumberOperation(id0),
            new PartitionBootstrapOperation(id0, 0, 1, Optional.of(partitionConfig), false));
    assertThat(phase.groupOperations().get(TENANT_B))
        .containsExactly(
            new PartitionLeaveOperation(id1, 0, 0),
            new DeleteHistoryOperation(id1),
            new UpdateIncarnationNumberOperation(id1),
            new PartitionBootstrapOperation(id1, 0, 1, Optional.of(partitionConfig), false));
  }

  @Test
  void shouldPurgeOnlyTheGivenPhysicalTenant() {
    // given
    final var transformer = new PurgeRequestTransformer(Optional.of(TENANT_A));
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, group().addMember(id0, member(Map.of(0, active(1)))),
                TENANT_B, group().addMember(id1, member(Map.of(0, active(1))))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — tenant B keeps its partitions and its history
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations()).containsOnlyKeys(TENANT_A);
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenantId() {
    // given
    final var transformer = new PurgeRequestTransformer(Optional.of("unknowntenant"));
    final var clusterConfiguration =
        withPartitionGroups(Map.of(TENANT_A, group().addMember(id0, member(Map.of(0, active(1))))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(NotFound.class).hasMessageContaining("unknowntenant");
  }

  @Test
  void shouldNotPlanAnythingWhenThePhysicalTenantHasNoMembers() {
    // given
    final var transformer = new PurgeRequestTransformer(Optional.of(TENANT_A));
    final var clusterConfiguration = withPartitionGroups(Map.of(TENANT_A, group()));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }

  @Test
  void shouldDeleteHistoryOnAMemberThatReplicatesTheTenant() {
    // given — the lowest-id member is present in the group but replicates nothing, which happens
    // transiently while it is being removed from the group. It is not active in the group, so it
    // holds none of the tenant's exporter state.
    final var transformer = new PurgeRequestTransformer(Optional.of(TENANT_A));
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A,
                group()
                    .addMember(id0, member(Map.of()))
                    .addMember(id1, member(Map.of(1, active(1))))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — the member that actually replicates partition 1 deletes the history
    assertThat(operationsOf(result, TENANT_A))
        .containsExactly(
            new PartitionLeaveOperation(id1, 1, 0),
            new DeleteHistoryOperation(id1),
            new UpdateIncarnationNumberOperation(id1),
            new PartitionBootstrapOperation(id1, 1, 1, Optional.of(partitionConfig), false));
  }

  @Test
  void shouldNotPlanAnythingWhenNoMemberReplicatesThePhysicalTenant() {
    // given — a member is listed for the tenant but holds no partition of it: there is nothing to
    // tear down or re-bootstrap, and no member is responsible for the tenant's storage
    final var transformer = new PurgeRequestTransformer(Optional.of(TENANT_A));
    final var clusterConfiguration =
        withPartitionGroups(Map.of(TENANT_A, group().addMember(id0, member(Map.of()))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }

  @Test
  void shouldBootstrapPartitionsWithTheirExporterConfig() {
    // given — the two partitions of the tenant have a different exporter configuration
    final var transformer = new PurgeRequestTransformer();
    final var enabledExporter =
        configWithExporter(new ExporterState(1, State.ENABLED, Optional.of("config")));
    final var disabledExporter =
        configWithExporter(new ExporterState(1, State.DISABLED, Optional.of("config")));
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A,
                group()
                    .addMember(id0, member(Map.of(0, PartitionState.active(2, enabledExporter))))
                    .addMember(
                        id1, member(Map.of(1, PartitionState.active(2, disabledExporter))))));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — each partition is recreated with the configuration it had
    assertThat(operationsOf(result, TENANT_A))
        .contains(
            new PartitionBootstrapOperation(id0, 0, 2, Optional.of(enabledExporter), false),
            new PartitionBootstrapOperation(id1, 1, 2, Optional.of(disabledExporter), false));
  }

  private List<PartitionGroupOperation> operationsOf(
      final Either<Exception, List<Phase>> result, final String groupId) {
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    return phase.groupOperations().get(groupId);
  }

  private DynamicPartitionConfig configWithExporter(final ExporterState exporterState) {
    return DynamicPartitionConfig.init()
        .updateExporting(
            new ExportingConfig(ExportingState.EXPORTING, Map.of("exporter", exporterState)));
  }

  private PartitionState active(final int priority) {
    return PartitionState.active(priority, partitionConfig);
  }

  private BrokerPartitionState member(final Map<Integer, PartitionState> partitions) {
    return BrokerPartitionState.initialize(partitions);
  }

  private PartitionGroupConfiguration group() {
    return PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION);
  }

  private CurrentClusterConfiguration withPartitionGroups(
      final Map<String, PartitionGroupConfiguration> partitionGroups) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.init(),
        partitionGroups,
        PhasedChangeState.empty());
  }
}
