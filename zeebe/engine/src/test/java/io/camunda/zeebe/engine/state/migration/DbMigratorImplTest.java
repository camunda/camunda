/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DbMigratorImplTest {

  @Test
  void shouldRunMigrationThatNeedsToBeRun() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration = mock(MigrationTask.class);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    when(mockMigration.needsToRun(context)).thenReturn(true);

    final var sut = new DbMigratorImpl(context, Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration).runMigration(context);
  }

  @Test
  void shouldNotRunMigrationThatDoesNotNeedToBeRun() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration = mock(MigrationTask.class);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    when(mockMigration.needsToRun(context)).thenReturn(false);

    final var sut = new DbMigratorImpl(context, Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(context);
  }

  @Test
  void shouldRunMigrationsInOrder() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration1 = mock(MigrationTask.class);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    when(mockMigration1.needsToRun(context)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    final var sut = new DbMigratorImpl(context, List.of(mockMigration1, mockMigration2));

    // when
    sut.runMigrations();

    // then
    final var inOrder = Mockito.inOrder(mockMigration1, mockMigration2);

    inOrder.verify(mockMigration1).runMigration(context);
    inOrder.verify(mockMigration2).runMigration(context);
  }

  @Test
  void shouldNotSetVersionIfFirstMigrationFails() {
    // given -- two migrations that both need to be run
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);

    final var mockMigration1 = mock(MigrationTask.class);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    when(mockMigration1.needsToRun(context)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    final var sut = new DbMigratorImpl(context, List.of(mockMigration1, mockMigration2));

    // when -- first migration fails
    doThrow(RuntimeException.class).when(mockMigration1).runMigration(context);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);
    // then -- second migration is not run
    verify(mockMigration2, never()).runMigration(any());
    // then -- version is not set
    verify(mockMigrationState, never()).setMigratedByVersion(any());
  }

  @Test
  void shouldNotSetVersionIfSecondMigrationFails() {
    // given -- two migrations that both need to be run
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);

    final var mockMigration1 = mock(MigrationTask.class);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    when(mockMigration1.needsToRun(context)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    final var sut = new DbMigratorImpl(context, List.of(mockMigration1, mockMigration2));

    // when -- second migration fails
    doThrow(RuntimeException.class).when(mockMigration2).runMigration(context);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);

    // then -- the version is not set
    verify(mockMigrationState, never()).setMigratedByVersion(any());
  }

  @Test
  void shouldSetVersionAfterRunningMigrations() {
    // given -- two migrations that both need to be run
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);

    final var mockMigration1 = mock(MigrationTask.class);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    when(mockMigration1.needsToRun(context)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    final var sut = new DbMigratorImpl(context, List.of(mockMigration1, mockMigration2));

    // when -- running migrations
    sut.runMigrations();

    // then -- the version is set
    verify(mockMigrationState).setMigratedByVersion(VersionUtil.getVersion());
  }

  @Test
  void shouldThrowOnInvalidUpgrade() {
    // given -- state that was migrated by version 1.0.0
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    when(mockMigrationState.getMigratedByVersion()).thenReturn("1.0.0");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(any())).thenReturn(true);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    final var sut = new DbMigratorImpl(context, Collections.singletonList(mockMigration));

    // when -- upgrading to a version that is not compatible
    try (final var util = Mockito.mockStatic(VersionUtil.class)) {
      util.when(VersionUtil::getVersion).thenReturn("2.0.0");
      // then -- running migrations throws
      assertThatThrownBy(sut::runMigrations)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Snapshot is not compatible with current version");
    }

    // when - upgrading to a pre-release version
    try (final var util = Mockito.mockStatic(VersionUtil.class)) {
      util.when(VersionUtil::getVersion).thenReturn("2.0.0-alpha1");

      // then -- running migrations throws
      assertThatThrownBy(sut::runMigrations)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot upgrade to or from a pre-release version");
    }

    // then -- migration is not run
    verify(mockMigration, never())
        .runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));
  }

  @Test
  void shouldNotThrowOnInvalidUpgrade() {
    // given -- state that was migrated by version 1.0.0
    final boolean versionCheckEnabled = false; // disable version check

    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    when(mockMigrationState.getMigratedByVersion()).thenReturn("1.0.0");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(any())).thenReturn(true);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    final var sut =
        new DbMigratorImpl(versionCheckEnabled, context, Collections.singletonList(mockMigration));

    // when -- upgrading to a version that is not compatible
    try (final var util = Mockito.mockStatic(VersionUtil.class)) {
      util.when(VersionUtil::getVersion).thenReturn("2.0.0");
      // then -- running migrations throws
      assertThatNoException().isThrownBy(sut::runMigrations);
    }

    // when - upgrading to a pre-release version
    try (final var util = Mockito.mockStatic(VersionUtil.class)) {
      util.when(VersionUtil::getVersion).thenReturn("2.0.0-alpha1");

      // then -- running migrations throws
      assertThatNoException().isThrownBy(sut::runMigrations);
    }

    // then -- migration is run
    verify(mockMigration, times(2))
        .runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));
  }

  @Test
  void shouldNotRunMigrationsIfTheSameVersion() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    // the version is the same as the migrated version
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final String currentVersion = VersionUtil.getVersion();
    when(mockMigrationState.getMigratedByVersion()).thenReturn(currentVersion);

    final var mockMigration = mock(MigrationTask.class);

    final var sut =
        new DbMigratorImpl(
            new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState),
            Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(any());
  }
}
