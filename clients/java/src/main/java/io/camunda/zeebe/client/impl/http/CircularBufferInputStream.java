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
package io.camunda.zeebe.client.impl.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.annotation.Nonnull;

/**
 * A custom input stream that stores data in a circular buffer with a fixed capacity. This
 * implementation offers a non-blocking asynchronous API for writing the data into the buffer.
 *
 * <p>The writing thread can check the available space in the buffer by calling {@link
 * #getAvailableSpace()}. If the buffer is full, the writing thread must avoid writing more data. A
 * callback can be set by the writing thread to be notified when space is available in the buffer.
 * See {@link CapacityCallback}.
 *
 * <p>The buffer space is freed up as the reading thread reads the data from the buffer. The reading
 * thread consumes data using the standard {@link InputStream} API, blocking if no data is
 * available.
 */
public class CircularBufferInputStream extends InputStream {

  private final byte[] buffer;
  private final int capacity;
  private int readPos = 0;
  private int writePos = 0;
  private int availableData = 0;
  private boolean endOfStream = false;
  private IOException exception = null;
  private CapacityCallback capacityCallback;

  public CircularBufferInputStream(final int capacity) {
    this.capacity = capacity;
    buffer = new byte[capacity];
  }

  public synchronized void setCapacityCallback(final CapacityCallback capacityCallback) {
    this.capacityCallback = capacityCallback;
  }

  /**
   * Writes the data into the buffer. This method does not block the calling thread if the buffer is
   * full, instead it throws an {@link IOException}. This means that the writing thread must handle
   * capacity issues by proactively checking the available space in the buffer before writing data.
   *
   * @throws IOException if the buffer is full
   */
  public synchronized void write(final ByteBuffer data) throws IOException {
    if (exception != null) {
      throw exception;
    }
    final int dataSize = data.remaining();
    final int availableSpace = getAvailableSpace();
    if (dataSize > availableSpace) {
      throw new IOException("Buffer is full");
    }

    final int firstCopyLength = Math.min(dataSize, capacity - writePos);
    data.get(buffer, writePos, firstCopyLength);
    writePos = (writePos + firstCopyLength) % capacity;

    final int remaining = dataSize - firstCopyLength;
    if (remaining > 0) {
      // Wrap around
      data.get(buffer, writePos, remaining);
      writePos = (writePos + remaining) % capacity;
    }
    availableData += dataSize;

    // Notify any waiting readers
    notifyAll();
  }

  public synchronized void endOfStream() {
    endOfStream = true;
    notifyAll();
  }

  public synchronized void signalError(final IOException e) {
    exception = e;
    notifyAll();
  }

  @Override
  public synchronized int read() throws IOException {
    if (canConsumeData()) {
      return -1;
    }

    final int b = buffer[readPos] & 0xFF;
    readPos = (readPos + 1) % capacity;
    availableData--;

    // Notify any waiting writers
    notifyAll();

    // Inform capacity callback
    if (capacityCallback != null) {
      capacityCallback.onCapacityAvailable(1);
    }

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

    final int firstCopyLength = Math.min(bytesToRead, capacity - readPos);
    System.arraycopy(buffer, readPos, b, off, firstCopyLength);
    readPos = (readPos + firstCopyLength) % capacity;
    bytesRead += firstCopyLength;
    availableData -= firstCopyLength;

    final int remaining = bytesToRead - firstCopyLength;
    if (remaining > 0) {
      System.arraycopy(buffer, readPos, b, off + firstCopyLength, remaining);
      readPos = (readPos + remaining) % capacity;
      bytesRead += remaining;
      availableData -= remaining;
    }

    // Notify any waiting writers
    notifyAll();

    // Inform capacity callback
    if (capacityCallback != null) {
      capacityCallback.onCapacityAvailable(bytesRead);
    }

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
      final IOException ex = exception;
      exception = null;
      throw ex;
    }
    return availableData == 0;
  }

  public synchronized int getAvailableSpace() {
    return capacity - availableData;
  }

  public interface CapacityCallback {
    void onCapacityAvailable(int increment);
  }
}
