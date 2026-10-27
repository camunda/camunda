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
package io.camunda.client.impl.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.annotation.Nonnull;

/**
 * A custom input stream that stores data in a circular buffer. This implementation offers a
 * non-blocking asynchronous API for writing the data into the buffer.
 *
 * <p>The writing thread learns how much data it may write through a {@link CapacityCallback}: first
 * the space that is free when the callback is set, then the space the reading thread frees up. The
 * buffer grows if the writing thread writes more than that, because the HTTP I/O layer does not
 * always stay within the space it has been given.
 *
 * <p>The buffer space is freed up as the reading thread reads the data from the buffer. The reading
 * thread consumes data using the standard {@link InputStream} API, blocking if no data is
 * available.
 */
public class CircularBufferInputStream extends InputStream {

  private final int capacity;
  private byte[] buffer;
  private int readPos = 0;
  private int writePos = 0;
  private int availableData = 0;
  private int unreportedSpace = 0;
  private boolean endOfStream = false;
  private IOException exception = null;
  private CapacityCallback capacityCallback;

  public CircularBufferInputStream(final int capacity) {
    this.capacity = capacity;
    buffer = new byte[capacity];
  }

  /**
   * Sets the callback and reports the space that is free at this moment to it. From then on, each
   * byte the reading thread frees up is reported exactly once. If the buffer already holds more
   * than its capacity, the excess has to be read before any space is reported.
   */
  public synchronized void setCapacityCallback(final CapacityCallback capacityCallback) {
    this.capacityCallback = capacityCallback;
    reportFreedSpace(capacity - availableData);
  }

  /**
   * Writes the data into the buffer without blocking the calling thread. Data beyond the space
   * reported to the {@link CapacityCallback} grows the buffer instead of being rejected.
   */
  public synchronized void write(final ByteBuffer data) throws IOException {
    if (exception != null) {
      throw exception;
    }
    final int dataSize = data.remaining();
    if (dataSize > buffer.length - availableData) {
      grow(availableData + dataSize);
    }

    final int firstCopyLength = Math.min(dataSize, buffer.length - writePos);
    data.get(buffer, writePos, firstCopyLength);
    writePos = (writePos + firstCopyLength) % buffer.length;

    final int remaining = dataSize - firstCopyLength;
    if (remaining > 0) {
      // Wrap around
      data.get(buffer, writePos, remaining);
      writePos = (writePos + remaining) % buffer.length;
    }
    availableData += dataSize;

    // Notify any waiting readers
    notifyAll();
  }

  public synchronized void endOfStream() {
    endOfStream = true;
    notifyAll();
  }

  /**
   * Fails the stream: a blocked reader is woken up and every read and write throws the error.
   *
   * <p>Only the first error is kept, and errors signalled after {@link #endOfStream()} are ignored:
   * a fully received document must stay readable, and a later, more generic error must not replace
   * the original cause.
   */
  public synchronized void signalError(final IOException e) {
    if (exception != null || endOfStream) {
      return;
    }
    exception = e;
    notifyAll();
  }

  @Override
  public synchronized int read() throws IOException {
    if (canConsumeData()) {
      return -1;
    }

    final int b = buffer[readPos] & 0xFF;
    readPos = (readPos + 1) % buffer.length;
    availableData--;

    // Notify any waiting writers
    notifyAll();

    reportFreedSpace(1);

    return b;
  }

  @Override
  public synchronized int read(@Nonnull final byte[] b, final int off, final int len)
      throws IOException {
    if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException();
    }
    if (len == 0) {
      return 0;
    }

    if (canConsumeData()) {
      return -1;
    }

    int bytesRead = 0;
    final int bytesToRead = Math.min(len, availableData);

    final int firstCopyLength = Math.min(bytesToRead, buffer.length - readPos);
    System.arraycopy(buffer, readPos, b, off, firstCopyLength);
    readPos = (readPos + firstCopyLength) % buffer.length;
    bytesRead += firstCopyLength;
    availableData -= firstCopyLength;

    final int remaining = bytesToRead - firstCopyLength;
    if (remaining > 0) {
      System.arraycopy(buffer, readPos, b, off + firstCopyLength, remaining);
      readPos = (readPos + remaining) % buffer.length;
      bytesRead += remaining;
      availableData -= remaining;
    }

    // Notify any waiting writers
    notifyAll();

    reportFreedSpace(bytesRead);

    return bytesRead;
  }

  private boolean canConsumeData() throws IOException {
    while (availableData == 0 && exception == null && !endOfStream) {
      try {
        wait();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while reading", e);
      }
    }
    if (exception != null) {
      // the error is sticky: callers that read again after a failure must fail again instead of
      // blocking forever on data that will never arrive
      throw exception;
    }
    return availableData == 0;
  }

  /**
   * Holds back freed space while the buffer still holds at least its capacity: the I/O layer reads
   * from the network whenever it is given any space, even if its window stays exhausted, so
   * reporting it then would only push the buffer further over its capacity.
   */
  private void reportFreedSpace(final int freedSpace) {
    if (capacityCallback == null) {
      return;
    }
    unreportedSpace += freedSpace;
    if (availableData < capacity) {
      final int increment = unreportedSpace;
      unreportedSpace = 0;
      capacityCallback.onCapacityAvailable(increment);
    }
  }

  /**
   * Moves the unread data to the start of a larger array. It grows by at least half, as the I/O
   * layer can keep handing over data in many small pieces while the reader is behind.
   */
  private void grow(final int minLength) {
    final byte[] grown = new byte[Math.max(minLength, buffer.length + buffer.length / 2)];
    final int firstCopyLength = Math.min(availableData, buffer.length - readPos);
    System.arraycopy(buffer, readPos, grown, 0, firstCopyLength);
    System.arraycopy(buffer, 0, grown, firstCopyLength, availableData - firstCopyLength);
    buffer = grown;
    readPos = 0;
    writePos = availableData;
  }

  public interface CapacityCallback {
    void onCapacityAvailable(int increment);
  }
}
