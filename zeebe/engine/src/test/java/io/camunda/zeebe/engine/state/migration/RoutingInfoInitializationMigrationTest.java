/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class RoutingInfoInitializationMigrationTest {
  @SuppressWarnings("unused")
  private MutableProcessingState processingState;

  @Test
  void shouldRunMigration() {
    // given
    final var clusterContext = new ClusterContextImpl(3);

    // when
    final var migration = new RoutingInfoInitializationMigration();
    final var context = new MigrationTaskContextImpl(clusterContext, processingState);
    migration.runMigration(context);

    // then
    final var updatedRoutingState = context.processingState().getRoutingState();
    assertThat(updatedRoutingState.isInitialized()).isTrue();
  }

  @Test
  void shouldNotRunMigrationAgain() {
    // given
    final var clusterContext = new ClusterContextImpl(3);
    final var migration = new RoutingInfoInitializationMigration();
    final var context = new MigrationTaskContextImpl(clusterContext, processingState);

    // when
    migration.runMigration(context);

    // then
    assertThat(migration.needsToRun(context)).isFalse();
  }

  @Test
  void shouldInitializeRoutingInfo() {
    // given
    final var clusterContext = new ClusterContextImpl(3);
    final var migration = new RoutingInfoInitializationMigration();
    final var context = new MigrationTaskContextImpl(clusterContext, processingState);

    // when
    migration.runMigration(context);

    // then
    final var updatedRoutingState = context.processingState().getRoutingState();
    assertThat(updatedRoutingState.isInitialized()).isTrue();
    assertThat(updatedRoutingState.currentPartitions()).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(updatedRoutingState.messageCorrelation())
        .isEqualTo(new MessageCorrelation.HashMod(3));
    assertThat(updatedRoutingState.desiredPartitions()).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  void shouldBeAnInitialization() {
    // given
    final var migration = new RoutingInfoInitializationMigration();

    // when/then
    assertThat(migration.isInitialization()).isTrue();
  }
}
