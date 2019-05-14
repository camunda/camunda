/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.log;

import io.zeebe.logstreams.impl.CompleteEventsInBlockProcessor;
import io.zeebe.logstreams.impl.LogEntryDescriptor;
import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.impl.log.index.LogBlockIndexContext;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.ReadResultProcessor;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocator;
import io.zeebe.util.allocation.DirectBufferAllocator;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class BufferedLogStreamReader implements LogStreamReader {
  public static final int DEFAULT_INITIAL_BUFFER_CAPACITY = 32 * 1024;
  public static final int MAX_BUFFER_CAPACITY = 128 * 1024 * 1024; // 128MB

  private static final int UNINITIALIZED = -1;
  private static final long FIRST_POSITION = Long.MIN_VALUE;
  private static final long LAST_POSITION = Long.MAX_VALUE;

  // configuration
  private final ReadResultProcessor completeEventsInBlockProcessor =
      new CompleteEventsInBlockProcessor();

  // wrapped logstream
  private LogStorage logStorage;
  private LogBlockIndex logBlockIndex;
  private LogBlockIndexContext indexContext;

  // state
  private IteratorState state;
  private long nextLogStorageReadAddress;
  private final LoggedEventImpl nextEvent = new LoggedEventImpl();
  // event returned to caller (important: has to be preserved even after compact/buffer resize)
  private final LoggedEventImpl returnedEvent = new LoggedEventImpl();

  // buffer
  private final BufferAllocator bufferAllocator = new DirectBufferAllocator();
  private AllocatedBuffer allocatedBuffer;
  private ByteBuffer byteBuffer;
  private int bufferOffset;
  private final DirectBuffer directBuffer = new UnsafeBuffer(0, 0);

  public BufferedLogStreamReader(final LogStream logStream) {
    this();
    wrap(logStream);
  }

  public BufferedLogStreamReader() {
    state = IteratorState.WRAP_NOT_CALLED;
  }

  @Override
  public void wrap(final LogStream log) {
    wrap(log, FIRST_POSITION);
  }

  @Override
  public void wrap(final LogStream log, final long position) {
    wrap(log.getLogStorage(), log.getLogBlockIndex(), position);
  }

  public void wrap(final LogStorage logStorage, final LogBlockIndex logBlockIndex) {
    wrap(logStorage, logBlockIndex, FIRST_POSITION);
  }

  public void wrap(
      final LogStorage logStorage, final LogBlockIndex logBlockIndex, final long position) {
    this.logStorage = logStorage;
    this.logBlockIndex = logBlockIndex;
    this.indexContext = logBlockIndex.createLogBlockIndexContext();

    if (isClosed()) {
      allocateBuffer(DEFAULT_INITIAL_BUFFER_CAPACITY);
    }

    seek(position);
  }

  @Override
  public boolean seekToNextEvent(long position) {

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

    // invalidate events first as the buffer content may change
    invalidateBufferAndOffsets();

    final long blockAddress = lookUpBlockAddressForPosition(position);

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
  public void seekToFirstEvent() {
    seek(FIRST_POSITION);
  }

  @Override
  public void seekToLastEvent() {
    seek(getLastPosition());

    if (isNextEventInitialized()) {
      checkIfNextEventIsCommitted();
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
  public boolean isClosed() {
    return allocatedBuffer == null;
  }

  @Override
  public void close() {
    if (allocatedBuffer != null) {
      allocatedBuffer.close();
      allocatedBuffer = null;
      byteBuffer = null;
      directBuffer.wrap(0, 0);
      bufferOffset = 0;

      logStorage = null;
      logBlockIndex = null;

      state = IteratorState.WRAP_NOT_CALLED;
    }
  }

  @Override
  public boolean hasNext() {
    switch (state) {
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

  private boolean readBlockIntoBuffer(final long blockAddress) {
    if (byteBuffer.remaining() < LogEntryDescriptor.HEADER_BLOCK_LENGTH) {
      compactBuffer();
    }

    final long result = logStorage.read(byteBuffer, blockAddress, completeEventsInBlockProcessor);

    if (result == LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY) {
      // it was not possible to read the block in the existing buffer => expand buffer
      long nextCapacity = 2L * (long) byteBuffer.capacity();
      nextCapacity = Math.min(nextCapacity, MAX_BUFFER_CAPACITY);
      allocateBuffer((int) nextCapacity);

      // retry to read the next block
      return readBlockIntoBuffer(blockAddress);
    } else if (result == LogStorage.OP_RESULT_INVALID_ADDR) {
      throw new IllegalStateException("Invalid address to read from " + blockAddress);
    } else if (result == LogStorage.OP_RESULT_NO_DATA) {
      state = IteratorState.NOT_ENOUGH_DATA;
      return false;
    } else {
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

  private long lookUpBlockAddressForPosition(final long position) {
    long address = logBlockIndex.lookupBlockAddress(indexContext, position);
    if (address < 0) {
      // position not found in index fallback to first block
      address = logStorage.getFirstBlockAddress();
    }

    return address;
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

  private long getLastPosition() {
    return LAST_POSITION;
  }

  enum IteratorState {
    WRAP_NOT_CALLED,
    EMPTY_LOG_STREAM,
    EVENT_AVAILABLE,
    NOT_ENOUGH_DATA,
    EVENT_NOT_COMMITTED,
  }
}
