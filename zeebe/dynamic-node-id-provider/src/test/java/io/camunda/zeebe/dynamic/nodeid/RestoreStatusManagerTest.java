/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.nodeid.StoredRestoreStatus.RestoreStatus;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RestoreStatusManagerTest {

  private NodeIdRepository repository;

  private RestoreStatusManager restoreStatusManager;

  @BeforeEach
  void setUp() {
    repository = mock(NodeIdRepository.class);
    restoreStatusManager = new RestoreStatusManager(repository);
  }

  @Test
  void shouldInitializeRestoreWhenNoExistingStatus() throws InterruptedException {
    // given
    when(repository.getRestoreStatus()).thenReturn(null);

    // when
    restoreStatusManager.initializeRestore();

    // then
    verify(repository).updateRestoreStatus(eq(new RestoreStatus(Set.of())), isNull());
  }

  @Test
  void shouldNotInitializeRestoreWhenStatusAlreadyExists() throws InterruptedException {
    // given
    final var existingStatus = new StoredRestoreStatus(new RestoreStatus(Set.of()), "etag123");
    when(repository.getRestoreStatus()).thenReturn(existingStatus);

    // when
    restoreStatusManager.initializeRestore();

    // then
    verify(repository, never()).updateRestoreStatus(any(), any());
  }

  @Test
  void shouldRetryInitializeRestoreOnConcurrentUpdateConflict() throws InterruptedException {
    // given
    final var callCount = new AtomicInteger(0);
    when(repository.getRestoreStatus())
        .thenAnswer(
            inv -> {
              if (callCount.getAndIncrement() == 0) {
                return null;
              }
              // After first attempt, return existing status to stop retry loop
              return new StoredRestoreStatus(new RestoreStatus(Set.of()), "etag");
            });
    doThrow(new RuntimeException("Concurrent update"))
        .when(repository)
        .updateRestoreStatus(any(), isNull());

    // when
    restoreStatusManager.initializeRestore();

    // then
    verify(repository, times(1)).updateRestoreStatus(any(), isNull());
  }

  @Test
  void shouldMarkNodeAsRestored() throws InterruptedException {
    // given
    final var existingStatus = new StoredRestoreStatus(new RestoreStatus(Set.of()), "etag123");
    when(repository.getRestoreStatus()).thenReturn(existingStatus);

    // when
    restoreStatusManager.markNodeRestored(0);

    // then
    verify(repository).updateRestoreStatus(eq(new RestoreStatus(Set.of(0))), eq("etag123"));
  }

  @Test
  void shouldNotMarkNodeAsRestoredWhenAlreadyMarked() throws InterruptedException {
    // given
    final var existingStatus = new StoredRestoreStatus(new RestoreStatus(Set.of(0, 1)), "etag123");
    when(repository.getRestoreStatus()).thenReturn(existingStatus);

    // when
    restoreStatusManager.markNodeRestored(0);

    // then
    verify(repository, never()).updateRestoreStatus(any(), any());
  }

  @Test
  void shouldRetryMarkNodeRestoredOnConcurrentUpdateConflict() throws InterruptedException {
    // given
    final var existingStatus = new StoredRestoreStatus(new RestoreStatus(Set.of()), "etag123");

    // Both calls return status without node 0 marked (simulating that another node updated etag)
    when(repository.getRestoreStatus()).thenReturn(existingStatus);

    // First call throws exception (concurrent update), second call succeeds
    doThrow(new RuntimeException("Concurrent update"))
        .doNothing()
        .when(repository)
        .updateRestoreStatus(any(), any());

    // when
    restoreStatusManager.markNodeRestored(0);

    // then
    verify(repository, times(2)).getRestoreStatus();
    verify(repository, times(2)).updateRestoreStatus(any(), any());
  }

  @Test
  void shouldAddNodeToExistingRestoredNodes() throws InterruptedException {
    // given
    final var existingStatus = new StoredRestoreStatus(new RestoreStatus(Set.of(0, 2)), "etag123");
    when(repository.getRestoreStatus()).thenReturn(existingStatus);

    // when
    restoreStatusManager.markNodeRestored(1);

    // then
    verify(repository).updateRestoreStatus(eq(new RestoreStatus(Set.of(0, 1, 2))), eq("etag123"));
  }

  @Test
  void shouldReturnImmediatelyWhenAllNodesAlreadyRestored() throws InterruptedException {
    // given
    final var completeStatus = new StoredRestoreStatus(new RestoreStatus(Set.of(0, 1, 2)), "etag");
    when(repository.getRestoreStatus()).thenReturn(completeStatus);

    // when
    restoreStatusManager.waitForAllNodesRestored(3, Duration.ofMillis(10));

    // then
    verify(repository, times(1)).getRestoreStatus();
  }

  @Test
  void shouldThrowWhenWaitingAndRestoreStatusNotInitialized() {
    // given
    when(repository.getRestoreStatus()).thenReturn(null);

    // when/then
    assertThatThrownBy(() -> restoreStatusManager.waitForAllNodesRestored(3, Duration.ofMillis(10)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Restore status not initialized");
  }

  @Test
  void shouldWaitForSingleNodeCluster() throws InterruptedException {
    // given
    final var completeStatus = new StoredRestoreStatus(new RestoreStatus(Set.of(0)), "etag");
    when(repository.getRestoreStatus()).thenReturn(completeStatus);

    // when
    restoreStatusManager.waitForAllNodesRestored(1, Duration.ofMillis(10));

    // then
    verify(repository, times(1)).getRestoreStatus();
  }

  @Test
  void shouldContinuePollingUntilAllNodesRestored() throws InterruptedException {
    // given
    final var status1 = new StoredRestoreStatus(new RestoreStatus(Set.of(0)), "etag1");
    final var status2 = new StoredRestoreStatus(new RestoreStatus(Set.of(0, 1)), "etag2");
    final var status3 = new StoredRestoreStatus(new RestoreStatus(Set.of(0, 1, 2)), "etag3");

    when(repository.getRestoreStatus()).thenReturn(status1, status2, status3);

    // when
    restoreStatusManager.waitForAllNodesRestored(3, Duration.ofMillis(10));

    // then
    verify(repository, times(3)).getRestoreStatus();
  }
}
