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
package io.zeebe.logstreams.impl.log.index;

import static io.zeebe.logstreams.impl.log.index.LogBlockIndexDescriptor.dataOffset;
import static io.zeebe.logstreams.impl.log.index.LogBlockIndexDescriptor.entryAddressOffset;
import static io.zeebe.logstreams.impl.log.index.LogBlockIndexDescriptor.entryLength;
import static io.zeebe.logstreams.impl.log.index.LogBlockIndexDescriptor.entryLogPositionOffset;
import static io.zeebe.logstreams.impl.log.index.LogBlockIndexDescriptor.entryOffset;
import static io.zeebe.logstreams.impl.log.index.LogBlockIndexDescriptor.indexSizeOffset;

import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.util.StreamUtil;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;
import org.agrona.concurrent.AtomicBuffer;

/**
 * Block index, mapping an event's position to the physical address of the block in which it resides
 * in storage.
 *
 * <p>Each Event has a position inside the stream. This position addresses it uniquely and is
 * assigned when the entry is first published to the stream. The position never changes and is
 * preserved through maintenance operations like compaction.
 *
 * <p>In order to read an event, the position must be translated into the "physical address" of the
 * block in which it resides in storage. Then, the block can be scanned for the event position
 * requested.
 */
public class LogBlockIndex implements SnapshotSupport {
  protected final AtomicBuffer indexBuffer;

  protected final int capacity;

  protected long lastVirtualPosition = -1;

  public LogBlockIndex(int capacity, Function<Integer, AtomicBuffer> bufferAllocator) {
    final int requiredBufferCapacity = dataOffset() + (capacity * entryLength());

    this.indexBuffer = bufferAllocator.apply(requiredBufferCapacity);
    this.capacity = capacity;

    reset();
  }

  /**
   * Returns the physical address of the block in which the log entry identified by the provided
   * position resides.
   *
   * @param position a virtual log position
   * @return the physical address of the block containing the log entry identified by the provided
   *     virtual position
   */
  public long lookupBlockAddress(long position) {
    final int offset = lookupOffset(position);
    return offset >= 0 ? indexBuffer.getLong(entryAddressOffset(offset)) : offset;
  }

  /**
   * Returns the position of the first log entry of the the block in which the log entry identified
   * by the provided position resides.
   *
   * @param position a virtual log position
   * @return the position of the block containing the log entry identified by the provided virtual
   *     position
   */
  public long lookupBlockPosition(long position) {
    final int offset = lookupOffset(position);
    return offset >= 0 ? indexBuffer.getLong(entryLogPositionOffset(offset)) : offset;
  }

  /**
   * Returns the offset of the block in which the log entry identified by the provided position
   * resides.
   *
   * @param position a virtual log position
   * @return the offset of the block containing the log entry identified by the provided virtual
   *     position
   */
  protected int lookupOffset(long position) {
    final int idx = lookupIndex(position);
    return idx >= 0 ? entryOffset(idx) : idx;
  }

  /**
   * Returns the index of the block in which the log entry identified by the provided position
   * resides.
   *
   * @param position a virtual log position
   * @return the index of the block containing the log entry identified by the provided virtual
   *     position
   */
  protected int lookupIndex(long position) {
    final int lastEntryIdx = size() - 1;

    int low = 0;
    int high = lastEntryIdx;

    int idx = -1;

    if (low == high) {
      final int entryOffset = entryOffset(low);
      final long entryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset));

      if (entryValue <= position) {
        idx = low;
      }

      high = -1;
    }

    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final int entryOffset = entryOffset(mid);

      if (mid == lastEntryIdx) {
        idx = mid;
        break;
      } else {
        final long entryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset));
        final long nextEntryValue =
            indexBuffer.getLong(entryLogPositionOffset(entryOffset(mid + 1)));

        if (entryValue <= position && position < nextEntryValue) {
          idx = mid;
          break;
        } else if (entryValue < position) {
          low = mid + 1;
        } else if (entryValue > position) {
          high = mid - 1;
        }
      }
    }

    return idx;
  }

  /**
   * Invoked by the log Appender thread after it has first written one or more entries to a block.
   *
   * @param logPosition the virtual position of the block (equal or smaller to the v position of the
   *     first entry in the block)
   * @param storageAddr the physical address of the block in the underlying storage
   * @return the new size of the index.
   */
  public int addBlock(long logPosition, long storageAddr) {
    final int currentIndexSize =
        indexBuffer.getInt(indexSizeOffset()); // volatile get not necessary
    final int entryOffset = entryOffset(currentIndexSize);
    final int newIndexSize = 1 + currentIndexSize;

    if (newIndexSize > capacity) {
      throw new RuntimeException(
          String.format(
              "LogBlockIndex capacity of %d entries reached. Cannot add new block.", capacity));
    }

    if (lastVirtualPosition >= logPosition) {
      final String errorMessage =
          String.format(
              "Illegal value for position.Value=%d, last value in index=%d. Must provide positions in ascending order.",
              logPosition, lastVirtualPosition);
      throw new IllegalArgumentException(errorMessage);
    }

    lastVirtualPosition = logPosition;

    // write next entry
    indexBuffer.putLong(entryLogPositionOffset(entryOffset), logPosition);
    indexBuffer.putLong(entryAddressOffset(entryOffset), storageAddr);

    // increment size
    indexBuffer.putIntOrdered(indexSizeOffset(), newIndexSize);

    return newIndexSize;
  }

  /** @return the current size of the index */
  public int size() {
    return indexBuffer.getIntVolatile(indexSizeOffset());
  }

  /** @return the capacity of the index */
  public int capacity() {
    return capacity;
  }

  public long getLogPosition(int idx) {
    boundsCheck(idx, size());

    final int entryOffset = entryOffset(idx);

    return indexBuffer.getLong(entryLogPositionOffset(entryOffset));
  }

  public long getAddress(int idx) {
    boundsCheck(idx, size());

    final int entryOffset = entryOffset(idx);

    return indexBuffer.getLong(entryAddressOffset(entryOffset));
  }

  private static void boundsCheck(int idx, int size) {
    if (idx < 0 || idx >= size) {
      throw new IllegalArgumentException(
          String.format("Index out of bounds. index=%d, size=%d.", idx, size));
    }
  }

  @Override
  public long writeSnapshot(OutputStream outputStream) throws Exception {
    StreamUtil.write(indexBuffer, outputStream);
    return indexBuffer.capacity();
  }

  @Override
  public void recoverFromSnapshot(InputStream inputStream) throws Exception {
    final byte[] byteArray = StreamUtil.read(inputStream);

    indexBuffer.putBytes(0, byteArray);
  }

  @Override
  public void reset() {
    // verify alignment to ensure atomicity of updates to the index metadata
    indexBuffer.verifyAlignment();

    // set initial size
    indexBuffer.putIntVolatile(indexSizeOffset(), 0);

    indexBuffer.setMemory(dataOffset(), capacity * entryLength(), (byte) 0);
  }
}
