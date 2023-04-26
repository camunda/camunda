/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.AuditRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.DirectBufferOutputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;

public final class AuditLogStreamReader implements LogStreamReader {
  private final LogStreamReader delegate;

  private final LoggedEventImpl currentEvent = new LoggedEventImpl();
  private final DirectBuffer currentEventBuffer = new UnsafeBuffer();

  private final AuditRecord auditReader = new AuditRecord();
  private final RecordMetadata eventMetadata = new RecordMetadata();
  private final LoggedEventImpl nextAuditEvent = new LoggedEventImpl();
  private final DirectBuffer nextAuditEventBuffer = new UnsafeBuffer();

  private LoggedEvent nextEvent;
  private int nextAuditEventOffset;

  public AuditLogStreamReader(final LogStreamReader delegate) {
    this.delegate = delegate;
    reset();
  }

  @Override
  public boolean hasNext() {
    return nextEvent != null || hasBufferedEvents() || readNextEvent();
  }

  @Override
  public LoggedEvent next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    if (!isEventBufferValid(nextAuditEventBuffer)) {
      final var next = nextEvent;
      nextEvent = null;
      return next;
    }

    final var length = nextAuditEvent.getLength();
    currentEventBuffer.wrap(nextAuditEventBuffer, nextAuditEvent.getFragmentOffset(), length);
    nextAuditEventOffset += length;
    nextAuditEvent.wrap(nextAuditEventBuffer, nextAuditEventOffset);

    if (hasBufferedEvents()) {
      nextEvent = nextAuditEvent;
    } else {
      nextEvent = null;
    }

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
    delegate.seek(position);
    reset();
    readNextEvent();

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
  public LoggedEvent peekNext() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    return nextEvent;
  }

  @Override
  public void close() {
    reset();
    delegate.close();
  }

  private long getCurrentPosition() {
    if (isEventBufferValid(currentEventBuffer)) {
      return currentEvent.getPosition();
    }

    return delegate.getPosition();
  }

  private long getNextEventPosition() {
    if (nextEvent != null) {
      return nextEvent.getPosition();
    }

    return -1;
  }

  private void reset() {
    currentEventBuffer.wrap(0, 0);
    currentEvent.wrap(currentEventBuffer, 0);

    nextAuditEventBuffer.wrap(0, 0);
    nextAuditEvent.wrap(nextAuditEventBuffer, 0);
    nextAuditEventOffset = 0;
    nextEvent = null;

    auditReader.reset();
  }

  private boolean hasBufferedEvents() {
    return isEventBufferValid(nextAuditEventBuffer)
        && nextAuditEventOffset < nextAuditEventBuffer.capacity();
  }

  private boolean readNextEvent() {
    if (!delegate.hasNext()) {
      return false;
    }

    final var next = delegate.next();
    next.readMetadata(eventMetadata);
    if (eventMetadata.getRecordType() == RecordType.AUDIT) {
      next.readValue(auditReader);

      final var uncompressedBuffer = new UnsafeBuffer(ByteBuffer.allocate(auditReader.getSize()));
      try (final var input = new DirectBufferInputStream(auditReader.events());
          final var output = new DirectBufferOutputStream(uncompressedBuffer)) {
        final var decompressor = new FramedLZ4CompressorInputStream(input);
        decompressor.transferTo(output);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      auditReader.reset();
      nextAuditEventBuffer.wrap(uncompressedBuffer);
      nextAuditEventOffset = 0;
      nextAuditEvent.wrap(nextAuditEventBuffer, 0);

      nextEvent = nextAuditEvent;
    } else {
      nextAuditEventBuffer.wrap(0, 0);
      nextAuditEventOffset = 0;
      nextEvent = next;
    }

    return true;
  }

  private boolean isEventBufferValid(final DirectBuffer eventBuffer) {
    return eventBuffer.addressOffset() != 0;
  }
}
