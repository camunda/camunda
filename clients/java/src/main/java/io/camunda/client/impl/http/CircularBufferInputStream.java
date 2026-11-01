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
import java.util.function.IntFunction;
import javax.annotation.Nonnull;
import org.apache.hc.core5.http.HttpStreamResetException;

/**
 * A custom input stream that stores data in a circular buffer. This implementation offers a
 * non-blocking asynchronous API for writing the data into the buffer.
 *
 * <p>The writing thread learns how much data it may write through a {@link CapacityCallback}: the
 * space that is free whenever it calls {@link #reportAvailableSpace}, then the space the reading
 * thread frees up after that. The buffer grows if the writing thread writes more than that, because
 * the HTTP I/O layer does not always stay within the space it has been given. If there is not
 * enough memory to grow it, the stream fails with an {@link IOException}.
 *
 * <p>The buffer space is freed up as the reading thread reads the data from the buffer. The reading
 * thread consumes data using the standard {@link InputStream} API, blocking if no data is
 * available.
 *
 * <p>Closing the stream before the end of the data makes every later write fail, which makes the
 * I/O layer abort the download.
 */
public class CircularBufferInputStream extends InputStream {

  /** The largest array the JVM can reliably allocate; {@code ArrayList} uses the same limit. */
  private static final int MAX_BUFFER_LENGTH = Integer.MAX_VALUE - 8;

  private static final byte[] NO_BUFFER = new byte[0];

  /** About two pieces of the data the I/O layer hands over at once. */
  private static final int MIN_GROWTH = 64 * 1024;

  private final int capacity;
  private final IntFunction<byte[]> arrayAllocator;
  private byte[] buffer;
  private int readPos = 0;
  private int writePos = 0;
  private int availableData = 0;
  private long unreportedSpace = 0;
  private boolean endOfStream = false;
  private boolean closed = false;
  private IOException exception = null;
  private CapacityCallback capacityCallback;

  public CircularBufferInputStream(final int capacity) {
    this(capacity, byte[]::new);
  }

  CircularBufferInputStream(final int capacity, final IntFunction<byte[]> arrayAllocator) {
    this.capacity = capacity;
    this.arrayAllocator = arrayAllocator;
    buffer = arrayAllocator.apply(capacity);
  }

  /**
   * Reports the space that is free at this moment to the callback, which then also receives the
   * space the reading thread frees up from now on. Space freed before is part of the free space, so
   * it is not reported a second time. If the buffer holds more than its capacity, the excess has to
   * be read before any space is reported.
   */
  public synchronized void reportAvailableSpace(final CapacityCallback capacityCallback) {
    this.capacityCallback = capacityCallback;
    unreportedSpace = 0;
    reportFreedSpace(capacity - availableData);
  }

  /**
   * Writes the data into the buffer without blocking the calling thread. Data beyond the space
   * reported to the {@link CapacityCallback} grows the buffer instead of being rejected.
   *
   * @throws HttpStreamResetException if the stream was closed, so the I/O layer aborts the download
   */
  public synchronized void write(final ByteBuffer data) throws IOException {
    if (closed) {
      // On HTTP/2, httpcore5 cancels only this stream for this exception. Any other exception ends
      // the whole connection, with every request on it.
      throw new HttpStreamResetException(
          "Document stream was closed before the response was fully received");
    }
    if (exception != null) {
      throw exception;
    }
    final int dataSize = data.remaining();
    if (dataSize > buffer.length - availableData) {
      try {
        grow((long) availableData + dataSize);
      } catch (final IOException e) {
        // The I/O layer fails the exchange with this exception but doesn't hand it to this stream,
        // which only learns of the generic abort from DocumentDataConsumer#releaseResources.
        // Signalled first, it is the error the reader gets.
        signalError(e);
        throw e;
      }
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

  /**
   * Drops the buffered data and wakes up a blocked reader; every later read fails.
   *
   * <p>If the data has not been fully received yet, the next write fails, which aborts the
   * download. The dropped data is reported as free space, so the I/O layer reads again even if the
   * buffer was full, and that write happens.
   */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    final int droppedData = availableData;
    buffer = NO_BUFFER;
    readPos = 0;
    writePos = 0;
    availableData = 0;
    notifyAll();

    if (!endOfStream && exception == null) {
      reportFreedSpace(droppedData);
    }
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
    while (availableData == 0 && exception == null && !endOfStream && !closed) {
      try {
        wait();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while reading", e);
      }
    }
    if (closed) {
      throw new IOException("Stream closed");
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
      final int increment = (int) Math.min(unreportedSpace, Integer.MAX_VALUE);
      unreportedSpace -= increment;
      capacityCallback.onCapacityAvailable(increment);
    }
  }

  /** Moves the unread data to the start of a larger array. */
  private void grow(final long minLength) throws IOException {
    final int length = grownLength(capacity, buffer.length, minLength);
    final byte[] grown = allocate(length, (int) minLength);
    final int firstCopyLength = Math.min(availableData, buffer.length - readPos);
    System.arraycopy(buffer, readPos, grown, 0, firstCopyLength);
    System.arraycopy(buffer, 0, grown, firstCopyLength, availableData - firstCopyLength);
    buffer = grown;
    readPos = 0;
    writePos = availableData;
  }

  /**
   * Allocates the array, falling back to the required length if there is not enough memory for the
   * preferred one: the extra room only saves later copies.
   *
   * <p>This runs on the I/O thread. Thrown from there, an {@link OutOfMemoryError} would stop the
   * thread and close the connections of every other request it serves, so only this download fails
   * instead.
   */
  private byte[] allocate(final int preferredLength, final int requiredLength) throws IOException {
    try {
      return arrayAllocator.apply(preferredLength);
    } catch (final OutOfMemoryError e) {
      if (preferredLength == requiredLength) {
        throw notEnoughMemory(requiredLength, e);
      }
    }
    try {
      return arrayAllocator.apply(requiredLength);
    } catch (final OutOfMemoryError e) {
      throw notEnoughMemory(requiredLength, e);
    }
  }

  private static IOException notEnoughMemory(final int length, final OutOfMemoryError cause) {
    return new IOException(
        String.format(
            "Not enough memory to buffer %d bytes of the document that have not been read yet",
            length),
        cause);
  }

  /**
   * Doubles the part of the buffer beyond its capacity, by at least {@link #MIN_GROWTH}. An overrun
   * by a read or two costs little memory, and when the I/O layer keeps handing over data while the
   * reader is behind, the unread data is copied only a few times. The length stays within the
   * largest array the JVM can allocate.
   */
  static int grownLength(final int capacity, final int currentLength, final long minLength)
      throws IOException {
    if (minLength > MAX_BUFFER_LENGTH) {
      throw new IOException(
          String.format(
              "Cannot buffer %d bytes of the document that have not been read yet, the limit is %d",
              minLength, MAX_BUFFER_LENGTH));
    }
    final long growth = Math.max((long) currentLength - capacity, MIN_GROWTH);
    return (int) Math.min(Math.max(minLength, currentLength + growth), MAX_BUFFER_LENGTH);
  }

  public interface CapacityCallback {
    void onCapacityAvailable(int increment);
  }
}
