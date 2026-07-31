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

import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import org.junit.jupiter.api.Test;

final class AtomixLogStorageTest {

  @Test
  void shouldNotifyCommitListenersWithPosition() {
    // given
    final var logStorage =
        new AtomixLogStorage(mock(AtomixReaderFactory.class), mock(ZeebeLogAppender.class));
    final var listener = mock(CommitListener.class);
    logStorage.addCommitListener(listener);

    // when
    logStorage.onCommit(42);

    // then
    verify(listener).onCommit(42);
  }
}
