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

import static io.zeebe.logstreams.impl.LogEntryDescriptor.getFragmentLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.getPosition;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.impl.LogEntryDescriptor;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.LogStorage;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Represents a class which contains some utilities for the log stream. */
public class LogStreamUtil {
  public static final int INVALID_ADDRESS = -1;
  public static final int MAX_READ_EVENT_SIZE = 1024 * 32;

  public static final int HEADER_LENGTH =
      DataFrameDescriptor.HEADER_LENGTH + LogEntryDescriptor.HEADER_BLOCK_LENGTH;

  public static long getAddressForPosition(LogStream stream, long position) {
    final LogEntryAddressSupplier logEntryAddressSupplier = LogEntryAddressSupplier.getInstance();
    logEntryAddressSupplier.wrap(stream);
    return logEntryAddressSupplier.getAddress(position);
  }

  private static final class LogEntryAddressSupplier {

    private static final class InstanceHolder {
      static final LogEntryAddressSupplier INSTANCE = new LogEntryAddressSupplier();
    }

    public static LogEntryAddressSupplier getInstance() {
      return InstanceHolder.INSTANCE;
    }

    protected LogStream logStream;
    protected LogBlockIndex blockIndex;
    protected LogStorage logStorage;

    protected final ByteBuffer ioBuffer = ByteBuffer.allocateDirect(MAX_READ_EVENT_SIZE);
    protected final DirectBuffer buffer = new UnsafeBuffer(0, 0);

    protected long nextReadAddress = INVALID_ADDRESS;
    protected long currentAddress = INVALID_ADDRESS;

    private LogEntryAddressSupplier() {
      buffer.wrap(ioBuffer);
    }

    public void wrap(LogStream logStream) {
      this.logStream = logStream;
      this.blockIndex = logStream.getLogBlockIndex();
      this.logStorage = logStream.getLogStorage();
      clear();
    }

    protected void clear() {
      nextReadAddress = INVALID_ADDRESS;
      currentAddress = INVALID_ADDRESS;
    }

    public long getAddress(final long position) {
      clear();

      if (!findStartAddress(position)) {
        return nextReadAddress;
      }

      return findAddress(position);
    }

    private boolean findStartAddress(long position) {
      final int indexSize = blockIndex.size();
      if (indexSize > 0) {
        nextReadAddress = blockIndex.lookupBlockAddress(position);
      } else {
        // fallback: get first block address
        nextReadAddress = logStorage.getFirstBlockAddress();

        if (nextReadAddress == INVALID_ADDRESS) {
          return false;
        }
      }
      return true;
    }

    private long findAddress(long position) {
      long address = INVALID_ADDRESS;
      boolean hasNext = next();
      while (hasNext) {
        final long currentPosition = getPosition(buffer, 0);
        if (currentPosition < position) {
          hasNext = next();
        } else {
          address = currentAddress;
          hasNext = false;
        }
      }
      return address;
    }

    protected boolean next() {
      currentAddress = nextReadAddress;

      if (!readHeader()) {
        return false;
      }

      final int fragmentLength = getFragmentLength(buffer, 0);
      return readMessage(fragmentLength - HEADER_LENGTH);
    }

    protected boolean readHeader() {
      return readIntoBuffer(ioBuffer, 0, HEADER_LENGTH);
    }

    protected boolean readMessage(final int fragmentLength) {
      final int capacity = buffer.capacity() - HEADER_LENGTH;

      int remainingBytes = fragmentLength;

      while (remainingBytes > 0) {
        final int limit = Math.min(remainingBytes, capacity);
        readIntoBuffer(ioBuffer, HEADER_LENGTH, limit);
        remainingBytes -= limit;
      }
      return true;
    }

    protected boolean readIntoBuffer(final ByteBuffer ioBuffer, final int offset, final int limit) {
      ioBuffer.position(offset);
      ioBuffer.limit(offset + limit);

      final long opResult = logStorage.read(ioBuffer, nextReadAddress);

      if (opResult >= 0) {
        nextReadAddress = opResult;
        return true;
      }

      return false;
    }
  }
}
