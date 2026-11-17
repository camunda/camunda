/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.StateClosedException;
import io.camunda.zeebe.util.health.HealthMonitor;
import org.junit.jupiter.api.Test;

final class MigrationSnapshotDirectorTest {

  private final AsyncSnapshotDirector snapshotDirector = mock(AsyncSnapshotDirector.class);
  private final HealthMonitor healthMonitor = mock(HealthMonitor.class);
  private final Runnable onSnapshotTaken = mock(Runnable.class);

  @Test
  void shouldInvokeCallbackOnceTheFirstSnapshotSucceeds() {
    // given
    final var snapshot = mock(PersistedSnapshot.class);
    when(snapshotDirector.forceSnapshot()).thenReturn(CompletableActorFuture.completed(snapshot));

    // when
    final var sut =
        new MigrationSnapshotDirector(
            snapshotDirector, new TestConcurrencyControl(), healthMonitor, onSnapshotTaken);

    // then
    assertThat(sut.isSnapshotTaken()).isTrue();
    verify(onSnapshotTaken, times(1)).run();
  }

  @Test
  void shouldNotInvokeCallbackBeforeASnapshotSucceeds() {
    // given - the snapshot attempt never completes
    when(snapshotDirector.forceSnapshot()).thenReturn(new CompletableActorFuture<>());

    // when
    final var sut =
        new MigrationSnapshotDirector(
            snapshotDirector, new TestConcurrencyControl(), healthMonitor, onSnapshotTaken);

    // then
    assertThat(sut.isSnapshotTaken()).isFalse();
    verify(onSnapshotTaken, never()).run();
  }

  @Test
  void shouldInvokeCallbackOnlyOnceEvenIfForceSnapshotIsCalledAgain() {
    // given
    final var snapshot = mock(PersistedSnapshot.class);
    when(snapshotDirector.forceSnapshot()).thenReturn(CompletableActorFuture.completed(snapshot));
    final var sut =
        new MigrationSnapshotDirector(
            snapshotDirector, new TestConcurrencyControl(), healthMonitor, onSnapshotTaken);

    // when - a snapshot has already been taken, so this is a no-op
    sut.close();

    // then
    verify(onSnapshotTaken, times(1)).run();
  }

  @Test
  void shouldKeepRetryingAfterANonIoExceptionFailure() {
    // given - the first attempt fails with an error that isn't an IOException (e.g. a transient
    // race during the rapid role-transition churn at broker bootstrap); this must not be treated
    // as a permanent failure, since the periodic snapshot mechanism it wraps never gives up either
    final var snapshot = mock(PersistedSnapshot.class);
    when(snapshotDirector.forceSnapshot())
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new IllegalStateException("boom")))
        .thenReturn(CompletableActorFuture.completed(snapshot));

    // when
    final var sut =
        new MigrationSnapshotDirector(
            snapshotDirector, new TestConcurrencyControl(), healthMonitor, onSnapshotTaken);

    // then
    assertThat(sut.isSnapshotTaken()).isTrue();
    verify(onSnapshotTaken, times(1)).run();
  }

  @Test
  void shouldGiveUpOnAStateClosedExceptionWrappedInsideAnotherException() {
    // given - a StateClosedException nested a level deep, not the outermost exception itself;
    // isRecoverableError() must walk the whole cause chain, not just check the top-level error
    when(snapshotDirector.forceSnapshot())
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new RuntimeException("wrapped", new StateClosedException("expected"))));

    // when
    final var sut =
        new MigrationSnapshotDirector(
            snapshotDirector, new TestConcurrencyControl(), healthMonitor, onSnapshotTaken);

    // then
    assertThat(sut.isSnapshotTaken()).isFalse();
    verify(onSnapshotTaken, never()).run();
  }
}
