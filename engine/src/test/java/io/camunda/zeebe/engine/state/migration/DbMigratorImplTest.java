/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
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
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockProcessingState, Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotRunMigrationThatDoesNotNeedToBeRun() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(false);

    final var sut =
        new DbMigratorImpl(mockProcessingState, Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(mockProcessingState);
  }

  @Test
  void shouldRunMigrationsInOrder() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockProcessingState, List.of(mockMigration1, mockMigration2));

    // when
    sut.runMigrations();

    // then
    final var inOrder = Mockito.inOrder(mockMigration1, mockMigration2);

    inOrder.verify(mockMigration1).runMigration(mockProcessingState);
    inOrder.verify(mockMigration2).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotSetVersionIfMigrationsFailed() {
    // given -- two migrations that both need to be run
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);

    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockProcessingState, List.of(mockMigration1, mockMigration2));

    // when -- first migration fails
    doThrow(RuntimeException.class).when(mockMigration1).runMigration(mockProcessingState);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);
    // then -- second migration is not run
    verify(mockMigration2, never()).runMigration(any());
    // then -- version is not set
    verify(mockMigrationState, never()).setMigratedByVersion(any());
  }

  @Test
  void shouldSetVersionAfterRunningMigrations() {
    // given -- two migrations that both need to be run
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);

    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockProcessingState, List.of(mockMigration1, mockMigration2));

    // when -- running migrations
    sut.runMigrations();

    // then -- the version is set
    verify(mockMigrationState).setMigratedByVersion(VersionUtil.getVersion());
  }

  @Test
  void shouldNotSetVersionIfMigrationsFail() {
    // given -- two migrations that both need to be run
    final var mockProcessingState = mock(MutableProcessingState.class);
    final var mockMigrationState = mock(MutableMigrationState.class);
    when(mockProcessingState.getMigrationState()).thenReturn(mockMigrationState);

    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockProcessingState, List.of(mockMigration1, mockMigration2));

    // when -- second migration fails
    doThrow(RuntimeException.class).when(mockMigration2).runMigration(mockProcessingState);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);

    // then -- the version is not set
    verify(mockMigrationState, never()).setMigratedByVersion(any());
  }
}
