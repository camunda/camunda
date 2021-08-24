/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.log.LogStreamBatchReader;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntArrayList;

public class LogStreamBatchReaderImpl implements LogStreamBatchReader {

  private static final Consumer<LoggedEvent> NOOP = event -> {};

  private final LogStreamBatchImpl batch = new LogStreamBatchImpl();

  private final MutableDirectBuffer eventBuffer = new ExpandableArrayBuffer();
  private final IntArrayList bufferOffsets = new IntArrayList();

  private final LogStreamReader logStreamReader;

  public LogStreamBatchReaderImpl(final LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
  }

  @Override
  public boolean seekToNextBatch(final long position) {
    if (position < 0) {
      logStreamReader.seekToFirstEvent();
      return true;

    } else {
      final var found = logStreamReader.seek(position);
      if (found) {
        // seeks to the next batch by reading the current batch
        final var batch = next();
        batch.forEachRemaining(NOOP);
      }
      return found;
    }
  }

  @Override
  public boolean hasNext() {
    return logStreamReader.hasNext();
  }

  @Override
  public Batch next() {
    if (!logStreamReader.hasNext()) {
      throw new NoSuchElementException();
    }

    bufferOffsets.clear();
    int bufferOffset = 0;
    long sourceEventPosition;

    do {
      final LoggedEvent event = logStreamReader.next();
      sourceEventPosition = event.getSourceEventPosition();

      event.write(eventBuffer, bufferOffset);

      bufferOffsets.addInt(bufferOffset);

      bufferOffset += event.getLength();

    } while (logStreamReader.hasNext()
        && sourceEventPosition > 0
        && sourceEventPosition == logStreamReader.peekNext().getSourceEventPosition());

    batch.wrap(eventBuffer, bufferOffsets);
    return batch;
  }

  @Override
  public void close() {
    logStreamReader.close();
    bufferOffsets.clear();
  }

  static class LogStreamBatchImpl implements LogStreamBatchReader.Batch {

    private final LoggedEventImpl event = new LoggedEventImpl();

    private DirectBuffer buffer;
    private IntArrayList offsets;

    private int currentIndex = 0;

    private void wrap(final DirectBuffer buffer, final IntArrayList offsets) {
      this.buffer = buffer;
      this.offsets = offsets;

      head();
    }

    @Override
    public void head() {
      currentIndex = 0;
    }

    @Override
    public boolean hasNext() {
      return currentIndex < offsets.size();
    }

    @Override
    public LoggedEvent next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      final var offset = offsets.get(currentIndex);
      event.wrap(buffer, offset);

      currentIndex += 1;

      return event;
    }
  }
}
