/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.log;

import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.storage.LogStorageReader;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A {@link LogStreamReader} implementation which can read single events from the blocks given by
 * the {@link LogStorageReader}.
 *
 * <p>This implementation assumes that blocks have no padding - they contain a contiguous series of
 * {@link LoggedEvent} which fits exactly within the block.
 */
public final class LogStreamReaderImpl implements LogStreamReader {
  private final LogStorageReader reader;

  private final LoggedEventImpl currentEvent;
  private final DirectBuffer currentEventBuffer;

  private final LoggedEventImpl nextEvent;
  private final DirectBuffer nextEventBuffer;

  private int nextEventOffset;

  public LogStreamReaderImpl(final LogStorageReader reader) {
    this.reader = reader;

    currentEvent = new LoggedEventImpl();
    currentEventBuffer = new UnsafeBuffer();

    nextEvent = new LoggedEventImpl();
    nextEventBuffer = new UnsafeBuffer();

    reset();
    seekToFirstEvent();
  }

  @Override
  public boolean hasNext() {
    return hasBufferedEvents() || readNextBlock();
  }

  @Override
  public LoggedEvent next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    currentEventBuffer.wrap(
        nextEventBuffer, nextEvent.getFragmentOffset(), nextEvent.getFragmentLength());

    nextEventOffset += nextEvent.getLength();
    nextEvent.wrap(nextEventBuffer, nextEventOffset);

    return currentEvent;
  }

  /**
   * Seeks to the event after the given position.
   *
   * <p>On negative position it seeks to the first event.
   *
   * @param position the position which should be used
   * @return <code>true</code>, if the position is negative or exists
   */
  @Override
  public boolean seekToNextEvent(final long position) {
    if (position < 0) {
      seekToFirstEvent();
      return true;
    }

    // seek(position + 1) may return false, as there may not be an event with position + 1 yet, but
    // we still seeked to the right position
    final long nextPosition = position + 1;
    seek(nextPosition);

    return getNextEventPosition() == nextPosition || getCurrentPosition() == position;
  }

  /**
   * Seek to the given log position if exists, or the lowest position that is greater than the given
   * one.
   *
   * @param position the position in the log to seek to
   * @return <code>true</code>, if the given position exists.
   */
  @Override
  public boolean seek(final long position) {
    reader.seek(position);
    reset();
    readNextBlock();

    while (hasNext() && getNextEventPosition() < position) {
      next();
    }

    return getNextEventPosition() == position;
  }

  /** Seek to the log position of the first event. */
  @Override
  public void seekToFirstEvent() {
    seek(Long.MIN_VALUE);
  }

  /**
   * Seek to the end of the log, which means after the last event.
   *
   * @return the position of the last entry
   */
  @Override
  public long seekToEnd() {
    seek(Long.MAX_VALUE);

    // has to iterate for now as there's no way to know what's the last entry's offset
    while (hasNext()) {
      next();
    }

    return getPosition();
  }

  /**
   * Returns the current log position of the reader.
   *
   * @return the current log position, or negative value if the log is empty or not initialized
   */
  @Override
  public long getPosition() {
    final long currentPosition = getCurrentPosition();
    if (currentPosition > -1) {
      return currentPosition;
    }

    return getNextEventPosition();
  }

  @Override
  public void close() {
    reset();
    reader.close();
  }

  private long getCurrentPosition() {
    if (isEventBufferValid(currentEventBuffer)) {
      return currentEvent.getPosition();
    }

    return -1;
  }

  private long getNextEventPosition() {
    if (hasBufferedEvents()) {
      return nextEvent.getPosition();
    }

    return -1;
  }

  private void reset() {
    currentEventBuffer.wrap(0, 0);
    currentEvent.wrap(currentEventBuffer, 0);

    nextEventBuffer.wrap(0, 0);
    nextEvent.wrap(nextEventBuffer, 0);
    nextEventOffset = 0;
  }

  private boolean hasBufferedEvents() {
    return isEventBufferValid(nextEventBuffer) && nextEventOffset < nextEventBuffer.capacity();
  }

  private boolean readNextBlock() {
    if (!reader.hasNext()) {
      return false;
    }

    final DirectBuffer nextBlock = reader.next();
    nextEventBuffer.wrap(nextBlock);
    nextEventOffset = 0;
    nextEvent.wrap(nextEventBuffer, nextEventOffset);

    return true;
  }

  private boolean isEventBufferValid(final DirectBuffer eventBuffer) {
    return eventBuffer.addressOffset() != 0;
  }
}
