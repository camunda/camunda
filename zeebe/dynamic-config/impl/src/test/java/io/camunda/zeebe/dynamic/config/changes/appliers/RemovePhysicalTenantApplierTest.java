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
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class RemovePhysicalTenantApplierTest {

  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();
  private final RemovePhysicalTenantApplier applier = new RemovePhysicalTenantApplier();

  private static PartitionGroupConfiguration group() {
    return new PartitionGroupConfiguration(
        1, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static PartitionGroupConfiguration groupWithAMember() {
    return new PartitionGroupConfiguration(
        1,
        0,
        Map.of(
            MemberId.from("1"),
            new BrokerPartitionState(
                1,
                Instant.EPOCH,
                Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                Mode.PROCESSING)),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  @Test
  void shouldRecordTheRemovalOfADisabledTenantAndClearItsAssignment() {
    // given — a disabled tenant whose partition is still assigned to a member
    final var group = groupWithAMember().disable();

    // when
    assertThat(applier.init(globalConfiguration, group)).isRight();
    final var updated = applier.apply().join().apply(group);

    // then
    Assertions.assertThat(updated.isRemoved()).isTrue();
    Assertions.assertThat(updated.isDisabled())
        .describedAs("a removed tenant stays disabled, so every existing reader still excludes it")
        .isTrue();
    Assertions.assertThat(updated.members())
        .describedAs("removal clears the old assignment rather than retaining it")
        .isEmpty();
  }

  /**
   * A running tenant is still in the coordinator's static configuration, so {@code
   * PhysicalTenantProvisioningInitializer} would provision it again on the next configuration
   * initialization — as a fresh, empty group, while the data of the one discarded here stayed
   * orphaned on disk.
   */
  @Test
  void shouldRejectRemovingAnEnabledTenant() {
    // given — the tenant is still running
    final var group = group();

    // when / then
    assertThat(applier.init(globalConfiguration, group))
        .isLeft()
        .left()
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * A restarted change may re-apply an operation it already completed, so a second removal is a
   * no-op rather than a failure — the tombstone is permanent, so it cannot mean anything new.
   */
  @Test
  void shouldTolerateRemovingAnAlreadyRemovedTenant() {
    // given — the tenant has been discarded once already
    final var group = group().disable().remove();

    // when / then
    assertThat(applier.init(globalConfiguration, group)).isRight();
  }
}
