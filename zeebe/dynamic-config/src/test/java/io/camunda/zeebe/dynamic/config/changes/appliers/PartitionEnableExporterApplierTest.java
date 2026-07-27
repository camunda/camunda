/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionEnableExporterApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId memberId = MemberId.from("1");

  private final GlobalConfiguration globalConfigurationWithMember =
      globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));

  private static GlobalConfiguration globalConfigurationWith(
      final Map<MemberId, BrokerState> members) {
    return new GlobalConfiguration(
        GlobalConfiguration.INITIAL_VERSION,
        Optional.empty(),
        members,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static PartitionGroupConfiguration groupWithMembers(
      final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        1, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static BrokerPartitionState brokerWith(final Map<Integer, PartitionState> partitions) {
    return new BrokerPartitionState(1, Instant.EPOCH, partitions, Mode.PROCESSING);
  }

  @Test
  void shouldRejectIfMemberDoesNotExist() {
    // given
    final var group = groupWithMembers(Map.of());

    // when
    final var result =
        new PartitionEnableExporterApplier(
                memberId, 1, "exporterA", Optional.empty(), partitionChangeExecutor)
            .init(globalConfigurationWith(Map.of()), group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not exist in the cluster");
  }

  @Test
  void shouldRejectIfMemberDoesNotHavePartition() {
    // given
    final var group = groupWithMembers(Map.of(memberId, brokerWith(Map.of())));

    // when
    final var result =
        new PartitionEnableExporterApplier(
                memberId, 1, "exporterA", Optional.empty(), partitionChangeExecutor)
            .init(globalConfigurationWithMember, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the partition");
  }

  @Test
  void shouldRejectIfExporterAlreadyEnabled() {
    // given
    final var config =
        DynamicPartitionConfig.init().updateExporting(c -> c.addExporters(Set.of("exporterA")));
    final var group =
        groupWithMembers(Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, config)))));

    // when
    final var result =
        new PartitionEnableExporterApplier(
                memberId, 1, "exporterA", Optional.empty(), partitionChangeExecutor)
            .init(globalConfigurationWithMember, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already enabled");
  }

  @Test
  void shouldRejectIfInitializeFromExporterIsDisabled() {
    // given
    final var config =
        DynamicPartitionConfig.init()
            .updateExporting(c -> c.addExporters(Set.of("exporterA")))
            .updateExporting(c -> c.disableExporter("exporterA"));
    final var group =
        groupWithMembers(Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, config)))));

    // when
    final var result =
        new PartitionEnableExporterApplier(
                memberId, 1, "exporterB", Optional.of("exporterA"), partitionChangeExecutor)
            .init(globalConfigurationWithMember, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is disabled");
  }

  @Test
  void shouldExecuteEnableExporterCallbackForNewExporter() {
    // given
    final var config = DynamicPartitionConfig.init();
    final var group =
        groupWithMembers(Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, config)))));
    final var applier =
        new PartitionEnableExporterApplier(
            memberId, 1, "exporterA", Optional.empty(), partitionChangeExecutor);
    when(partitionChangeExecutor.enableExporter(anyInt(), any(), anyLong(), any()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfigurationWithMember, group);
    assertThat(initResult).isRight();
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(partitionChangeExecutor, times(1)).enableExporter(1, "exporterA", 1, null);
    final var exporters =
        resultingGroup.getMember(memberId).getPartition(1).config().exporting().exporters();
    Assertions.assertThat(exporters.get("exporterA").metadataVersion()).isEqualTo(1);
  }
}
