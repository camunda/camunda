/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DbMigratorImplTest {

  @Test
  void shouldRunMigrationThatNeedsToBeRun() {
    // given
    final var mockZeebeState = mock(MutableZeebeState.class);
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockZeebeState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockZeebeState, () -> Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration).runMigration(mockZeebeState);
  }

  @Test
  void shouldNotRunMigrationThatDoesNotNeedToBeRun() {
    // given
    final var mockZeebeState = mock(MutableZeebeState.class);
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockZeebeState)).thenReturn(false);

    final var sut =
        new DbMigratorImpl(mockZeebeState, () -> Collections.singletonList(mockMigration));

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(mockZeebeState);
  }

  @Test
  void shouldRunMigrationsInOrder() {
    // given
    final var mockZeebeState = mock(MutableZeebeState.class);
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockZeebeState)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockZeebeState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockZeebeState, () -> List.of(mockMigration1, mockMigration2));

    // when
    sut.runMigrations();

    // then
    final var inOrder = Mockito.inOrder(mockMigration1, mockMigration2);

    inOrder.verify(mockMigration1).runMigration(mockZeebeState);
    inOrder.verify(mockMigration2).runMigration(mockZeebeState);
  }

  @Test
  void shouldNotRunAnyMigrationIfAbortSignalWasReceivedInTheVeryBeginning() {
    // given
    final var mockZeebeState = mock(MutableZeebeState.class);
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(mockZeebeState)).thenReturn(false);

    final var sut =
        new DbMigratorImpl(mockZeebeState, () -> Collections.singletonList(mockMigration));

    // when
    sut.abort();
    sut.runMigrations();

    // then
    verifyNoInteractions(mockMigration);
  }

  @Test
  void shouldNotRunSubsequentMigrationsAfterAbortSignalWasReceived() {
    // given
    final var mockZeebeState = mock(MutableZeebeState.class);
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(mockZeebeState)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(mockZeebeState)).thenReturn(true);

    final var sut =
        new DbMigratorImpl(mockZeebeState, () -> List.of(mockMigration1, mockMigration2));

    doAnswer(
            (invocationOnMock) -> {
              // send abort signal during first migration
              sut.abort();
              return null;
            })
        .when(mockMigration1)
        .runMigration(mockZeebeState);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration1).runMigration(mockZeebeState);
    verify(mockMigration2, never()).runMigration(mockZeebeState);
  }
}
