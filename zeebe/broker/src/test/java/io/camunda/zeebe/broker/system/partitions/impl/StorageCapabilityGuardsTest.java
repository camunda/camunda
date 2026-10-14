/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotApiHandlerTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotDirectorPartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ZeebeDbPartitionTransitionStep;
import io.camunda.zeebe.db.StorageCapabilities;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The broker assumes local, directory-snapshottable storage. These tests pin that every place that
 * makes the assumption refuses a backend that does not declare it, rather than failing later.
 */
final class StorageCapabilityGuardsTest {

  private static final StorageCapabilities NONE = new StorageCapabilities(false, false, false);

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void shouldRejectStateControllerWithoutFileSnapshots() {
    // given
    final var factory = mock(ZeebeDbFactory.class);
    when(factory.capabilities()).thenReturn(NONE);

    // when - then
    assertThatThrownBy(
            () ->
                new StateControllerImpl(
                    factory,
                    mock(ConstructableSnapshotStore.class),
                    Path.of("runtime"),
                    index -> java.util.Optional.empty(),
                    db -> null,
                    mock(ConcurrencyControl.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("StateControllerImpl")
        .hasMessageContaining("fileSnapshots");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void shouldFailClearlyWhenFactoryDeclaresNoCapabilities() {
    // given a backend that forgot to implement capabilities()
    final var factory = mock(ZeebeDbFactory.class);

    // when - then
    assertThatThrownBy(
            () ->
                new StateControllerImpl(
                    factory,
                    mock(ConstructableSnapshotStore.class),
                    Path.of("runtime"),
                    index -> java.util.Optional.empty(),
                    db -> null,
                    mock(ConcurrencyControl.class)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("must declare its StorageCapabilities");
  }

  @Test
  void shouldRejectZeebeDbStepWithoutFileSnapshots() {
    // when - then
    assertThatThrownBy(() -> new ZeebeDbPartitionTransitionStep(NONE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fileSnapshots");
  }

  @Test
  void shouldRejectZeebeDbStepWithoutEphemeralRuntime() {
    // given
    final var capabilities = new StorageCapabilities(true, false, true);

    // when - then
    assertThatThrownBy(() -> new ZeebeDbPartitionTransitionStep(capabilities))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ephemeralRuntime");
  }

  @Test
  void shouldRejectSnapshotDirectorStepWithoutFileSnapshots() {
    // when - then
    assertThatThrownBy(() -> new SnapshotDirectorPartitionTransitionStep(NONE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fileSnapshots");
  }

  @Test
  void shouldRejectSnapshotApiHandlerStepWithoutScopedCopy() {
    // given
    final var capabilities = new StorageCapabilities(true, true, false);

    // when - then
    assertThatThrownBy(() -> new SnapshotApiHandlerTransitionStep(capabilities))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("scopedCopy");
  }
}
