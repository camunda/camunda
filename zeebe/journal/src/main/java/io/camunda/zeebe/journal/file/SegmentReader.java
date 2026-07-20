/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import com.google.common.base.Preconditions;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.JournalRecordReaderUtil;
import io.camunda.zeebe.journal.record.SBESerializer;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Log segment reader. */
final class SegmentReader implements Iterator<JournalRecord> {

  private final ByteBuffer buffer;
  private final JournalIndex index;
  private final Segment segment;
  private long currentIndex;
  private final JournalRecordReaderUtil recordReader;
  private final int descriptorLength;

  SegmentReader(final ByteBuffer buffer, final Segment segment, final JournalIndex index) {
    this.index = index;
    this.segment = segment;
    descriptorLength = segment.descriptor().length();
    recordReader = new JournalRecordReaderUtil(new SBESerializer());
    this.buffer = buffer;
    reset();
  }

  @Override
  public boolean hasNext() {
    if (!segment.isOpen()) {
      // When the segment has been deleted concurrently, we do not want to allow the readers to
      // continue reading from this segment.
      return false;
    }

    // if the next entry exists the version would be non-zero
    return FrameUtil.hasValidVersion(buffer);
  }

  @Override
  public JournalRecord next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    // Read version so that buffer's position is advanced.
    FrameUtil.readVersion(buffer);

    final var currentEntry = recordReader.read(buffer, getNextIndex());
    // currentEntry should not be null as hasNext returns true
    currentIndex = currentEntry.index();
    return currentEntry;
  }

  void reset() {
    buffer.position(descriptorLength);
    currentIndex = segment.index() - 1;
  }

  void seek(final long index) {
    checkSegmentOpen();
    final long firstIndex = segment.index();
    final long lastIndex = segment.lastIndex();

    reset();

    final var position = this.index.lookup(index - 1);
    if (position != null && position.index() >= firstIndex && position.index() <= lastIndex) {
      buffer.position(position.position());
      currentIndex = position.index() - 1;
    }

    // If the returned index is far away from the seekIndex, it is likely that this segment was not
    // indexed before. So we try to index them during the seek.
    final boolean shouldIndex = !this.index.hasIndexed(index);

    while (getNextIndex() < index && hasNext()) {
      final var nextPosition = buffer.position();
      final var nextEntry = next();
      if (shouldIndex) {
        this.index.index(nextEntry, nextPosition);
      }
    }
  }

  void close() {
    segment.onReaderClosed(this);
  }

  long getNextIndex() {
    return currentIndex + 1;
  }

  private void checkSegmentOpen() {
    Preconditions.checkState(
        segment.isOpen(), "Segment is already closed. Reader must reset to a valid index.");
  }
}
