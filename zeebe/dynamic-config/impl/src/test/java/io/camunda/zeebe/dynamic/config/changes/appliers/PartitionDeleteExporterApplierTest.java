/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class PartitionDeleteExporterApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      Mockito.mock(PartitionChangeExecutor.class);
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
        new PartitionDeleteExporterApplier(memberId, 1, "exporterA", partitionChangeExecutor)
            .init(globalConfigurationWith(Map.of()), group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not exist in the cluster");
  }

  @Test
  void shouldRejectIfPartitionDoesNotExist() {
    // given — member exists in the group, but does not replicate the requested partition
    final var group = groupWithMembers(Map.of(memberId, brokerWith(Map.of())));

    // when
    final var result =
        new PartitionDeleteExporterApplier(memberId, 1, "exporterA", partitionChangeExecutor)
            .init(globalConfigurationWithMember, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the partition");
  }

  @Test
  void shouldRejectIfPartitionDoesNotHaveExporter() {
    // given
    final var config = DynamicPartitionConfig.init();
    final var group =
        groupWithMembers(Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, config)))));

    // when
    final var result =
        new PartitionDeleteExporterApplier(memberId, 1, "exporterA", partitionChangeExecutor)
            .init(globalConfigurationWithMember, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the exporter");
  }

  @Test
  void shouldRejectIfExporterIsNotInConfigNotFoundState() {
    // given — exporter is still ENABLED, not CONFIG_NOT_FOUND
    final var config =
        DynamicPartitionConfig.init().updateExporting(c -> c.addExporters(Set.of("exporterA")));
    final var group =
        groupWithMembers(Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, config)))));

    // when
    final var result =
        new PartitionDeleteExporterApplier(memberId, 1, "exporterA", partitionChangeExecutor)
            .init(globalConfigurationWithMember, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("instead of");
  }

  @Test
  void shouldNotChangeGroupConfigurationInInit() {
    // given
    final var config =
        DynamicPartitionConfig.init()
            .updateExporting(c -> c.addExporters(Set.of("exporterA")))
            .updateExporting(c -> c.withConfigNotFoundFor(Set.of("exporterA")));
    final var group =
        groupWithMembers(Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, config)))));

    // when
    final var result =
        new PartitionDeleteExporterApplier(memberId, 1, "exporterA", partitionChangeExecutor)
            .init(globalConfigurationWithMember, group);

    // then
    assertThat(result).isRight();
    Assertions.assertThat(result.get().apply(group)).isEqualTo(group);
  }

  @Test
  void shouldFailApplyFutureIfDeleteExporterFails() {
    // given
    Mockito.when(partitionChangeExecutor.deleteExporter(1, "exporterA"))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("force fail")));
    final var applier =
        new PartitionDeleteExporterApplier(memberId, 1, "exporterA", partitionChangeExecutor);

    // when
    final var result = applier.apply();

    // then
    Assertions.assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("force fail");
  }

  @Test
  void shouldExecuteDeleteExporterCallback() {
    // given
    final var config =
        DynamicPartitionConfig.init()
            .updateExporting(c -> c.addExporters(Set.of("exporterA")))
            .updateExporting(c -> c.withConfigNotFoundFor(Set.of("exporterA")));
    final var group =
        groupWithMembers(Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, config)))));
    final var applier =
        new PartitionDeleteExporterApplier(memberId, 1, "exporterA", partitionChangeExecutor);
    Mockito.when(partitionChangeExecutor.deleteExporter(1, "exporterA"))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfigurationWithMember, group);
    assertThat(initResult).isRight();
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    Mockito.verify(partitionChangeExecutor, Mockito.times(1)).deleteExporter(1, "exporterA");
    Assertions.assertThat(
            resultingGroup
                .getMember(memberId)
                .getPartition(1)
                .config()
                .exporting()
                .exporters()
                .containsKey("exporterA"))
        .isFalse();
  }
}
