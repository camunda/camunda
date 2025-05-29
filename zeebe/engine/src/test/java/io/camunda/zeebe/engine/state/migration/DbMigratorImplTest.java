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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

public class DbMigratorImplTest {

  private static final String CURRENT_VERSION = "8.8.0";
  MutableProcessingState mockProcessingState;
  ArrayList<MigrationTask> migrations = new ArrayList<>();
  DbMigratorImpl sut;

  @BeforeEach
  public void setup() {
    mockProcessingState = mock(MutableProcessingState.class, Answers.RETURNS_DEEP_STUBS);
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("8.7.0");
    migrations.clear();
    sut = new DbMigratorImpl(mockProcessingState, migrations, CURRENT_VERSION);
  }

  @Test
  void shouldRunMigrationThatNeedsToBeRun() {
    // given
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(true);

    migrations.add(mockMigration);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotRunMigrationThatDoesNotNeedToBeRun() {
    // given
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(false);
    migrations.add(mockMigration);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(mockProcessingState);
  }

  @Test
  void shouldRunMigrationsInOrder() {
    // given
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when
    sut.runMigrations();

    // then
    final var inOrder = Mockito.inOrder(mockMigration1, mockMigration2);

    inOrder.verify(mockMigration1).runMigration(mockProcessingState);
    inOrder.verify(mockMigration2).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotSetVersionIfFirstMigrationFails() {
    // given -- two migrations that both need to be run
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when -- first migration fails
    doThrow(RuntimeException.class).when(mockMigration1).runMigration(mockProcessingState);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);
    // then -- second migration is not run
    verify(mockMigration2, never()).runMigration(any());
    // then -- version is not set
    verify(mockProcessingState.getMigrationState(), never()).setMigratedByVersion(any());
  }

  @Test
  void shouldNotSetVersionIfSecondMigrationFails() {
    // given -- two migrations that both need to be run
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when -- second migration fails
    doThrow(RuntimeException.class).when(mockMigration2).runMigration(mockProcessingState);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);

    // then -- the version is not set
    verify(mockProcessingState.getMigrationState(), never()).setMigratedByVersion(any());
  }

  @Test
  void shouldSetVersionAfterRunningMigrations() {
    // given -- two migrations that both need to be run
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockProcessingState)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockProcessingState)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when -- running migrations
    sut.runMigrations();

    // then -- the version is set
    verify(mockProcessingState.getMigrationState()).setMigratedByVersion(CURRENT_VERSION);
  }

  @Test
  void shouldThrowOnInvalidUpgradeFromMinor() {
    // given -- state that was migrated by version 1.0.0
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("1.0.0");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockProcessingState)).thenReturn(true);
    migrations.add(mockMigration);

    // when -- upgrading to a version that is not compatible
    // then -- running migrations throws
    assertThatThrownBy(sut::runMigrations)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Snapshot is not compatible with current version");
  }

  @Test
  void shouldThrowOnInvalidUpgradeFromAlpha() {
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("2.0.0-alpha1");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(any())).thenReturn(true);
    migrations.add(mockMigration);

    // then -- running migrations throws
    assertThatThrownBy(sut::runMigrations)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot upgrade to or from a pre-release version");

    // then -- migration is not run
    verify(mockMigration, never()).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotRunMigrationsIfTheSameVersion() {
    // given
    // the version is the same as the migrated version
    when(mockProcessingState.getMigrationState().getMigratedByVersion())
        .thenReturn(CURRENT_VERSION);

    final var mockMigration = mock(MigrationTask.class);

    migrations.add(mockMigration);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(mockProcessingState);
  }

  @Test
  void shouldNotRunMigrationsWhenNoVersionIsSavedInTheState() {
    // given
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn(null);

    final var migration = mock(MigrationTask.class);
    when(migration.needsToRun(any())).thenReturn(true);
    migrations.add(migration);

    // when
    sut.runMigrations();

    // then
    verify(migration).isInitialization();
    verify(migration, never()).runMigration(any());
    verify(mockProcessingState.getMigrationState()).setMigratedByVersion(eq(CURRENT_VERSION));
  }

  @Test
  void shouldOnlyRunInitializationMigrationsWhenDbIsEmpty() {
    // given
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn(null);

    final var migration = mock(MigrationTask.class);
    when(migration.needsToRun(any())).thenReturn(true);
    when(migration.isInitialization()).thenReturn(true);
    migrations.add(migration);

    // when
    sut.runMigrations();

    // then
    verify(migration).isInitialization();
    verify(migration).runMigration(any());
    verify(mockProcessingState.getMigrationState()).setMigratedByVersion(eq(CURRENT_VERSION));
  }
}
