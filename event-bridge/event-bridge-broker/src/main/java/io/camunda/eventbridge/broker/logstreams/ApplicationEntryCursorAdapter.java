/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.eventbridge.protocol.EventBridgeBatchBlockIterator;
import io.camunda.zeebe.util.JournalIndexCursor;
import org.agrona.DirectBuffer;

public class ApplicationEntryCursorAdapter implements JournalIndexCursor {

  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final EventBridgeBatchBlockIterator iterator = new EventBridgeBatchBlockIterator();

  private boolean isApplicationEntry;
  private int baseOffset;
  private int currentBlockOffset;
  private int nextBlockOffset;

  void reset() {
    isApplicationEntry = false;
    baseOffset = 0;
    currentBlockOffset = 0;
    nextBlockOffset = 0;
  }

  @Override
  public void wrap(final DirectBuffer data, final int offset) {
    reset();
    final var raftEntry = serializer.readRaftLogEntry(data);
    isApplicationEntry = raftEntry.isApplicationEntry();

    if (!isApplicationEntry) {
      return;
    }

    baseOffset = offset + serializer.getApplicationEntrySerializedHeaderLength();
    currentBlockOffset = 0;
    nextBlockOffset = 0;
    final var applicationEntry = (SerializedApplicationEntry) raftEntry.getApplicationEntry();
    iterator.wrap(applicationEntry.data(), 0, applicationEntry.data().capacity());
  }

  @Override
  public boolean hasNext() {
    return isApplicationEntry && iterator.hasNext();
  }

  @Override
  public void next() {
    iterator.next();
    currentBlockOffset = nextBlockOffset;
    nextBlockOffset += currentLength();
  }

  @Override
  public int currentOffset() {
    return baseOffset + currentBlockOffset;
  }

  @Override
  public long currentLowestAsqn() {
    return iterator.currentLowestPosition();
  }

  @Override
  public long currentHighestAsqn() {
    return iterator.currentHighestPosition();
  }

  @Override
  public int currentLength() {
    return iterator.currentLength();
  }
}
