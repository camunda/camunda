/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.TestIndexedRaftLogEntry;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class AtomixAppendListenerAdapterTest {

  private final LogStorage.AppendListener delegate = mock(LogStorage.AppendListener.class);
  private final AtomixAppendListenerAdapter adapter = new AtomixAppendListenerAdapter(delegate);

  @Test
  void shouldForwardOnWrite() {
    // given
    final var entry = new SerializedApplicationEntry(10, 20, new UnsafeBuffer(new byte[8]));
    final var indexed = new TestIndexedRaftLogEntry(5, 1, entry);

    // when
    adapter.onWrite(indexed);

    // then
    verify(delegate).onWrite(5, 20);
  }

  @Test
  void shouldForwardOnCommit() {
    // when
    adapter.onCommit(5, 20);

    // then
    verify(delegate).onCommit(5, 20);
  }

  @Test
  void shouldForwardOnCommitError() {
    // given
    final var entry = new SerializedApplicationEntry(10, 20, new UnsafeBuffer(new byte[8]));
    final var indexed = new TestIndexedRaftLogEntry(5, 1, entry);
    final var error = new RuntimeException("Leader stepping down");

    // when - onWrite is called first to capture highestPosition
    adapter.onWrite(indexed);
    adapter.onCommitError(5, error);

    // then - the adapter forwards the stored highestPosition from onWrite
    verify(delegate).onCommitError(5, 20, error);
  }

  @Test
  void shouldForwardOnCommitErrorWithDefaultPositionWhenNoWriteOccurred() {
    // given
    final var error = new RuntimeException("Leader stepping down");

    // when - onCommitError called without prior onWrite
    adapter.onCommitError(5, error);

    // then - highestPosition is -1 (default)
    verify(delegate).onCommitError(5, -1, error);
  }
}
