/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocator;
import io.zeebe.util.allocation.DirectBufferAllocator;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.function.LongUnaryOperator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class BufferedLogStreamReader implements LogStreamReader {
  static final int DEFAULT_INITIAL_BUFFER_CAPACITY = 32 * 1024;
  static final int MAX_BUFFER_CAPACITY = 128 * 1024 * 1024; // 128MB
  static final long FIRST_POSITION = Long.MIN_VALUE;
  private static final int UNINITIALIZED = -1;
  // configuration
  private final CompleteEventsInBlockProcessor completeEventsInBlockProcessor =
      new CompleteEventsInBlockProcessor();
  private final LoggedEventImpl nextEvent = new LoggedEventImpl();
  // event returned to caller (important: has to be preserved even after compact/buffer resize)
  private final LoggedEventImpl returnedEvent = new LoggedEventImpl();
  // buffer
  private final BufferAllocator bufferAllocator = new DirectBufferAllocator();
  private final DirectBuffer directBuffer = new UnsafeBuffer(0, 0);
  // stateful storage reader
  private LogStorageReader storageReader;
  // state
  private IteratorState state;
  private long nextLogStorageReadAddress;
  private long lastReadAddress;
  private AllocatedBuffer allocatedBuffer;
  private ByteBuffer byteBuffer;
  private int bufferOffset;

  BufferedLogStreamReader(final LogStorage logStorage) {
    state = IteratorState.WRAP_NOT_CALLED;
    this.storageReader = logStorage.newReader();

    if (isClosed()) {
      allocateBuffer(DEFAULT_INITIAL_BUFFER_CAPACITY);
    }

    seek(FIRST_POSITION);
  }

  @Override
  public boolean seekToNextEvent(final long position) {

    if (position <= -1) {
      seekToFirstEvent();
      return true;
    }

    final boolean found = seek(position);
    if (found && hasNext()) {
      next();
      return true;
    }

    return false;
  }

  @Override
  public boolean seek(final long position) {
    if (state == IteratorState.WRAP_NOT_CALLED) {
      throw new IllegalStateException("Iterator not initialized");
    }

    final long seekAddress = storageReader.lookUpApproximateAddress(position);

    invalidateBufferAndOffsets();
    return seekFrom(seekAddress, position);
  }

  @Override
  public void seekToFirstEvent() {
    seek(FIRST_POSITION);
  }

  @Override
  public long seekToEnd() {
    // invalidate events first as the buffer content may change
    invalidateBufferAndOffsets();

    final long blockAddress = storageReader.getFirstBlockAddress();
    if (blockAddress < 0) {
      // no block found => empty log
      state = IteratorState.EMPTY_LOG_STREAM;
      return -1;
    } else {
      readLastBlockIntoBuffer();
      return completeEventsInBlockProcessor.getLastReadEventPosition();
    }
  }

  @Override
  public long getPosition() {
    // if an event was already returned use it's position otherwise use position of next event if
    // available, kind of strange but seemed to be the old API
    if (isReturnedEventInitialized()) {
      return returnedEvent.getPosition();
    }

    switch (state) {
      case EVENT_AVAILABLE:
        return nextEvent.getPosition();
      default:
        return UNINITIALIZED;
    }
  }

  @Override
  public long lastReadAddress() {
    return lastReadAddress;
  }

  @Override
  public boolean isClosed() {
    return allocatedBuffer == null;
  }

  private boolean seekFrom(final long blockAddress, final long position) {
    if (blockAddress < 0) {
      // no block found => empty log
      state = IteratorState.EMPTY_LOG_STREAM;
      return false;
    } else {
      readBlockIntoBuffer(blockAddress);
      readNextEvent();
      return searchPositionInBuffer(position);
    }
  }

  @Override
  public void close() {
    if (allocatedBuffer != null) {
      allocatedBuffer.close();
      allocatedBuffer = null;
      byteBuffer = null;
      directBuffer.wrap(0, 0);
      bufferOffset = 0;

      storageReader = null;

      state = IteratorState.WRAP_NOT_CALLED;
    }
  }

  @Override
  public boolean hasNext() {
    switch (state) {
      case EVENT_AVAILABLE:
        return true;
      case EMPTY_LOG_STREAM:
        seekToFirstEvent();
        break;
      case NOT_ENOUGH_DATA:
        readNextAddress();
        break;
      case EVENT_NOT_COMMITTED:
        checkIfNextEventIsCommitted();
        break;
      case WRAP_NOT_CALLED:
        throw new IllegalStateException("Iterator not initialized");
      default:
        throw new IllegalStateException("Unknown reader state " + state.name());
    }

    return state == IteratorState.EVENT_AVAILABLE;
  }

  @Override
  public LoggedEvent next() {
    switch (state) {
      case EVENT_AVAILABLE:
        // wrap event for returning
        wrapReturnedEvent(nextEvent.getFragmentOffset());
        // find next event in log
        readNextEvent();
        return returnedEvent;
      case WRAP_NOT_CALLED:
        throw new IllegalStateException("Iterator not initialized");
      default:
        throw new NoSuchElementException(
            "Api protocol violation: No next log entry available; You need to probe with hasNext() first.");
    }
  }

  private void allocateBuffer(final int capacity) {
    if (!isClosed()
        && (allocatedBuffer != null && allocatedBuffer.capacity() == MAX_BUFFER_CAPACITY)
        && capacity >= MAX_BUFFER_CAPACITY) {
      throw new RuntimeException(
          "Next fragment requires more space then the maximal buffer capacity of "
              + BufferedLogStreamReader.MAX_BUFFER_CAPACITY);
    }

    final AllocatedBuffer newAllocatedBuffer = bufferAllocator.allocate(capacity);
    final ByteBuffer newByteBuffer = newAllocatedBuffer.getRawBuffer();

    if (!isClosed()) {
      // copy remaining data to new buffer
      // set position to minimal offset to preserve
      // set limit to bufferOffset to remove everything afterwards as it will be read again next
      // time
      final int offsetToCopy = minimalOffsetToPreserve();
      byteBuffer.position(offsetToCopy);
      byteBuffer.limit(bufferOffset);

      newByteBuffer.put(byteBuffer);

      // update buffer and event offsets
      bufferOffset -= offsetToCopy;
      wrapReturnedEvent(returnedEvent.getFragmentOffset() - offsetToCopy);
      wrapNextEvent(nextEvent.getFragmentOffset() - offsetToCopy);
    } else {
      // update buffer offset and invalidate events
      invalidateBufferAndOffsets();
    }

    // replace old buffers by new ones
    byteBuffer = newByteBuffer;
    directBuffer.wrap(byteBuffer);

    if (allocatedBuffer != null) {
      allocatedBuffer.close();
    }
    allocatedBuffer = newAllocatedBuffer;
  }

  private void compactBuffer() {
    // check if an event is wrapped and preserve it
    if (isReturnedEventInitialized() || isNextEventInitialized()) {
      final int offsetToCopy = minimalOffsetToPreserve();

      // set position to last returned offset
      byteBuffer.position(offsetToCopy);

      // compact buffer to move old events to front
      byteBuffer.compact();

      // update buffer offset
      bufferOffset -= offsetToCopy;

      // update event offsets
      if (isNextEventInitialized()) {
        wrapNextEvent(nextEvent.getFragmentOffset() - offsetToCopy);
      }

      if (isReturnedEventInitialized()) {
        wrapReturnedEvent(returnedEvent.getFragmentOffset() - offsetToCopy);
      }
    } else {
      // otherwise just clear the buffer
      invalidateBufferAndOffsets();
      byteBuffer.clear();
    }
  }

  private void readLastBlockIntoBuffer() {
    executeReadMethod(
        Long.MAX_VALUE,
        readAddress -> storageReader.readLastBlock(byteBuffer, completeEventsInBlockProcessor));
  }

  private boolean readBlockIntoBuffer(final long blockAddress) {
    return executeReadMethod(
        blockAddress,
        readAddress -> storageReader.read(byteBuffer, readAddress, completeEventsInBlockProcessor));
  }

  private boolean executeReadMethod(final long blockAddress, final LongUnaryOperator readMethod) {
    if (byteBuffer.remaining() < LogEntryDescriptor.HEADER_BLOCK_LENGTH) {
      compactBuffer();
    }

    final long result = readMethod.applyAsLong(blockAddress);

    if (result == LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY) {
      // it was not possible to read the block in the existing buffer => expand buffer
      long nextCapacity = 2L * (long) byteBuffer.capacity();
      nextCapacity = Math.min(nextCapacity, MAX_BUFFER_CAPACITY);
      allocateBuffer((int) nextCapacity);

      // retry to read the next block
      return executeReadMethod(blockAddress, readMethod);
    } else if (result == LogStorage.OP_RESULT_INVALID_ADDR) {
      throw new IllegalStateException("Invalid address to read from " + blockAddress);
    } else if (result == LogStorage.OP_RESULT_NO_DATA) {
      state = IteratorState.NOT_ENOUGH_DATA;
      return false;
    } else {
      this.lastReadAddress = blockAddress;
      this.nextLogStorageReadAddress = result;
      return true;
    }
  }

  private boolean searchPositionInBuffer(final long position) {
    while (isNextUncommittedEventAvailable() && nextEvent.getPosition() < position) {
      readNextEvent();
    }

    if (nextEvent.getPosition() < position) {
      // not in buffered block, read next block and continue the search
      return readNextAddress() && searchPositionInBuffer(position);
    }

    return nextEvent.getPosition() == position;
  }

  private boolean isNextUncommittedEventAvailable() {
    return state == IteratorState.EVENT_AVAILABLE || state == IteratorState.EVENT_NOT_COMMITTED;
  }

  private boolean readNextAddress() {
    final boolean blockFound = readBlockIntoBuffer(nextLogStorageReadAddress);

    if (blockFound) {
      readNextEvent();
    }

    return blockFound;
  }

  private void readNextEvent() {
    // initially we assume there is not enough data
    state = IteratorState.NOT_ENOUGH_DATA;

    final int remaining = byteBuffer.position() - bufferOffset;
    if (remaining > 0) {
      wrapNextEvent(bufferOffset);
      bufferOffset += nextEvent.getFragmentLength();
      checkIfNextEventIsCommitted();
    } else {
      readNextAddress();
    }
  }

  private boolean isReturnedEventInitialized() {
    return returnedEvent.getFragmentOffset() >= 0;
  }

  private boolean isNextEventInitialized() {
    return nextEvent.getFragmentOffset() >= 0;
  }

  private int minimalOffsetToPreserve() {
    if (isReturnedEventInitialized()) {
      return returnedEvent.getFragmentOffset();
    } else if (isNextEventInitialized()) {
      return nextEvent.getFragmentOffset();
    } else {
      return bufferOffset;
    }
  }

  private void invalidateBufferAndOffsets() {
    state = IteratorState.NOT_ENOUGH_DATA;

    wrapNextEvent(UNINITIALIZED);
    wrapReturnedEvent(UNINITIALIZED);

    bufferOffset = 0;
    if (!isClosed()) {
      byteBuffer.clear();
    }
  }

  private void wrapNextEvent(final int offset) {
    nextEvent.wrap(directBuffer, offset);
  }

  private void wrapReturnedEvent(final int offset) {
    returnedEvent.wrap(directBuffer, offset);
  }

  private void checkIfNextEventIsCommitted() {
    // Next Event is always committed.
    state = IteratorState.EVENT_AVAILABLE;
  }

  enum IteratorState {
    WRAP_NOT_CALLED,
    EMPTY_LOG_STREAM,
    EVENT_AVAILABLE,
    NOT_ENOUGH_DATA,
    EVENT_NOT_COMMITTED,
  }
}
