/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.eventbridge.protocol.EventBridgeBatchBlockIterator;
import io.camunda.zeebe.util.JournalIndexCursor;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Interprets a journal record's data as a raft entry and iterates the event-bridge batches of its
 * application data.
 *
 * <p>Allocation-free: runs per journal record on self-warming fetch scans and per appended block on
 * both leader and follower, so it peeks at the raft entry's headers via the serializer instead of
 * materializing the full entry. All wire-format knowledge stays inside {@link
 * RaftEntrySBESerializer}.
 */
public class ApplicationEntryCursorAdapter implements JournalIndexCursor {

  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final EventBridgeBatchBlockIterator iterator = new EventBridgeBatchBlockIterator();
  // Reusable zero-based view of the application data, mirroring the slice a full
  // deserialization would expose as the application entry's data buffer.
  private final UnsafeBuffer applicationData = new UnsafeBuffer(0, 0);

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
    isApplicationEntry = serializer.isApplicationEntry(data);

    if (!isApplicationEntry) {
      return;
    }

    final int dataOffset = serializer.applicationDataOffset();
    final int dataLength = serializer.applicationDataLength();
    baseOffset = offset + dataOffset;
    applicationData.wrap(data, dataOffset, dataLength);
    iterator.wrap(applicationData, 0, dataLength);
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
