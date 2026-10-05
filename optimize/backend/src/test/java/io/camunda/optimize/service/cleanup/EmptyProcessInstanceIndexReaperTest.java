/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmptyProcessInstanceIndexReaperTest {

  private static final String KEY = "some-process";
  private static final String ALIAS = "optimize-process-instance-some-process";
  private static final String INDEX = "optimize-process-instance-some-process_v8";

  private final DatabaseClient databaseClient = mock(DatabaseClient.class);
  private final EmptyProcessInstanceIndexReaper reaper =
      new EmptyProcessInstanceIndexReaper(databaseClient);

  @BeforeEach
  void setUp() throws IOException {
    when(databaseClient.convertToPrefixedAliasName("process-instance-some-process"))
        .thenReturn(ALIAS);
    when(databaseClient.getAllIndicesForAlias(ALIAS)).thenReturn(Set.of(INDEX));
  }

  @Test
  void shouldDeleteAnEmptyIndexWhileWritesAreBlocked() throws IOException {
    // given
    when(databaseClient.countWithoutPrefix(INDEX)).thenReturn(0L, 0L);

    // when
    reaper.deleteIfEmpty(KEY);

    // then - the index is only recounted and deleted once no write can land in it anymore
    final var order = inOrder(databaseClient);
    order.verify(databaseClient).refresh("process-instance-some-process");
    order.verify(databaseClient).countWithoutPrefix(INDEX);
    order.verify(databaseClient).addWriteBlock(INDEX);
    order.verify(databaseClient).refresh("process-instance-some-process");
    order.verify(databaseClient).countWithoutPrefix(INDEX);
    order.verify(databaseClient).deleteIndexByRawIndexNames(INDEX);
    verify(databaseClient, never()).removeWriteBlock(any());
  }

  @Test
  void shouldKeepAndUnblockAnIndexThatReceivedAnInstanceBeforeTheBlock() throws IOException {
    // given - empty on the first count, but an instance was imported before the block took effect
    when(databaseClient.countWithoutPrefix(INDEX)).thenReturn(0L, 1L);

    // when
    reaper.deleteIfEmpty(KEY);

    // then
    verify(databaseClient, never()).deleteIndexByRawIndexNames(any());
    verify(databaseClient).removeWriteBlock(INDEX);
  }

  @Test
  void shouldNotBlockAnIndexThatHasInstances() throws IOException {
    // given
    when(databaseClient.countWithoutPrefix(INDEX)).thenReturn(3L);

    // when
    reaper.deleteIfEmpty(KEY);

    // then
    verify(databaseClient, never()).addWriteBlock(any());
    verify(databaseClient, never()).deleteIndexByRawIndexNames(any());
  }

  @Test
  void shouldDoNothingWhenTheDefinitionHasNoIndex() throws IOException {
    // given
    when(databaseClient.getAllIndicesForAlias(ALIAS)).thenReturn(Set.of());

    // when
    reaper.deleteIfEmpty(KEY);

    // then
    verify(databaseClient, never()).countWithoutPrefix(any());
    verify(databaseClient, never()).addWriteBlock(any());
  }

  @Test
  void shouldUnblockTheIndexAndNotFailTheCleanupWhenTheDeleteFails() throws IOException {
    // given
    when(databaseClient.countWithoutPrefix(INDEX)).thenReturn(0L, 0L);
    doThrow(new OptimizeRuntimeException("delete failed"))
        .when(databaseClient)
        .deleteIndexByRawIndexNames(INDEX);

    // when - then the remaining definitions of the cleanup run are still processed
    assertThatNoException().isThrownBy(() -> reaper.deleteIfEmpty(KEY));

    // then - a failed attempt must not leave the index rejecting imports
    verify(databaseClient).removeWriteBlock(INDEX);
  }

  @Test
  void shouldUnblockTheIndexWhenAddingTheBlockFails() throws IOException {
    // given - e.g. a timeout after the block was already applied
    when(databaseClient.countWithoutPrefix(INDEX)).thenReturn(0L);
    doThrow(new OptimizeRuntimeException("timed out")).when(databaseClient).addWriteBlock(INDEX);

    // when
    assertThatNoException().isThrownBy(() -> reaper.deleteIfEmpty(KEY));

    // then
    verify(databaseClient, never()).deleteIndexByRawIndexNames(any());
    verify(databaseClient).removeWriteBlock(INDEX);
  }

  @Test
  void shouldUnblockTheIndexWhenTheRecountFails() throws IOException {
    // given
    when(databaseClient.countWithoutPrefix(INDEX))
        .thenReturn(0L)
        .thenThrow(new IOException("connection reset"));

    // when
    assertThatNoException().isThrownBy(() -> reaper.deleteIfEmpty(KEY));

    // then
    verify(databaseClient, never()).deleteIndexByRawIndexNames(any());
    verify(databaseClient).removeWriteBlock(INDEX);
  }
}
