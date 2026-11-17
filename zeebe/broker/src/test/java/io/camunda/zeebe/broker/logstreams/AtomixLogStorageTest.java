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
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommittedPositionListener;
import org.junit.jupiter.api.Test;

final class AtomixLogStorageTest {

  private final AtomixLogStorage logStorage =
      new AtomixLogStorage(
          mock(AtomixReaderFactory.class),
          mock(AtomixReaderFactory.class),
          mock(ZeebeLogAppender.class));

  @Test
  void shouldNotifyCommitListenersOnCommittedIndex() {
    // given
    final var listener = mock(CommitListener.class);
    logStorage.addCommitListener(listener);

    // when -- raft notifies a new commit index, which is all a follower ever observes
    logStorage.onCommit(7);

    // then
    verify(listener).onCommit();
  }

  @Test
  void shouldNotifyCommittedPositionListenersWithPosition() {
    // given
    final var listener = mock(CommittedPositionListener.class);
    logStorage.addCommittedPositionListener(listener);

    // when
    logStorage.committedPositionNotifier().onCommit(42);

    // then
    verify(listener).onCommittedPosition(42);
  }

  @Test
  void shouldNotNotifyCommittedPositionListenersOnCommittedIndex() {
    // given -- a committed index carries no application record position
    final var listener = mock(CommittedPositionListener.class);
    logStorage.addCommittedPositionListener(listener);

    // when
    logStorage.onCommit(7);

    // then
    verifyNoInteractions(listener);
  }
}
