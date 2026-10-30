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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CircularBufferInputStreamTest {

  private static final int MAX_BUFFER_LENGTH = Integer.MAX_VALUE - 8;
  private static final int KIB = 1024;
  private static final int MIB = 1024 * KIB;

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldFailEveryReadAfterAnErrorWasSignalled() {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    final IOException error = new IOException("connection closed");
    stream.signalError(error);

    // when - then, a second read must throw again instead of blocking for data that never arrives
    assertThatThrownBy(stream::read).isSameAs(error);
    assertThatThrownBy(stream::read).isSameAs(error);
    assertThatThrownBy(() -> stream.read(new byte[4], 0, 4)).isSameAs(error);
  }

  @Test
  void shouldRejectWritesAfterAnErrorWasSignalled() {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    final IOException error = new IOException("connection closed");
    stream.signalError(error);

    // when - then
    assertThatThrownBy(() -> stream.write(ByteBuffer.wrap(new byte[] {1}))).isSameAs(error);
  }

  @Test
  void shouldFailEveryReadAfterClose() throws IOException {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    stream.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));

    // when
    stream.close();

    // then, the buffered data is dropped
    assertThatThrownBy(stream::read).isInstanceOf(IOException.class).hasMessage("Stream closed");
    assertThatThrownBy(() -> stream.read(new byte[4], 0, 4))
        .isInstanceOf(IOException.class)
        .hasMessage("Stream closed");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldWakeBlockedReaderWhenClosed() {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    final CompletableFuture<Throwable> readFailure = new CompletableFuture<>();
    final Thread reader =
        new Thread(
            () -> {
              try {
                readFailure.completeExceptionally(
                    new AssertionError("read returned " + stream.read()));
              } catch (final IOException e) {
                readFailure.complete(e);
              }
            },
            "blocked-reader");
    reader.setDaemon(true);
    reader.start();
    try {
      await().until(() -> reader.getState() == Thread.State.WAITING);

      // when
      stream.close();

      // then
      assertThat(readFailure).succeedsWithin(Duration.ofSeconds(5)).isInstanceOf(IOException.class);
    } finally {
      reader.interrupt();
    }
  }

  @Test
  void shouldRejectWritesAfterClose() {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    stream.close();

    // when - then, so that httpcore5 cancels the download instead of filling a closed stream
    assertThatThrownBy(() -> stream.write(ByteBuffer.wrap(new byte[] {1})))
        .isInstanceOf(HttpStreamResetException.class);
  }

  @Test
  void shouldReportDroppedDataAsFreeSpaceWhenClosed() throws IOException {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(4);
    final AtomicInteger reportedSpace = new AtomicInteger();
    stream.reportAvailableSpace(reportedSpace::addAndGet);
    stream.write(ByteBuffer.wrap(new byte[6]));
    stream.read();
    // the buffer still holds more than its capacity, so the read byte is held back
    assertThat(reportedSpace.get()).isEqualTo(4);

    // when
    stream.close();

    // then, the I/O layer is given space again, so it reads and hits the failing write
    assertThat(reportedSpace.get()).isEqualTo(10);
  }

  @Test
  void shouldNotReportFreeSpaceWhenClosedAfterEndOfStream() throws IOException {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(4);
    final AtomicInteger reportedSpace = new AtomicInteger();
    stream.reportAvailableSpace(reportedSpace::addAndGet);
    stream.write(ByteBuffer.wrap(new byte[4]));
    stream.endOfStream();

    // when
    stream.close();

    // then, the download is already over
    assertThat(reportedSpace.get()).isEqualTo(4);
  }

  @Test
  void shouldReadBufferedDataAndEndOfStream() throws IOException {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    stream.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
    stream.endOfStream();

    // when - then
    final byte[] data = new byte[3];
    assertThat(stream.read(data, 0, 3)).isEqualTo(3);
    assertThat(data).containsExactly(1, 2, 3);
    assertThat(stream.read()).isEqualTo(-1);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldKeepTheFirstErrorWhenSeveralErrorsAreSignalled() {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    final IOException firstError = new IOException("connection reset");
    stream.signalError(firstError);

    // when
    stream.signalError(new IOException("released before the response was fully received"));

    // then
    assertThatThrownBy(stream::read).isSameAs(firstError);
    assertThatThrownBy(() -> stream.write(ByteBuffer.wrap(new byte[] {1}))).isSameAs(firstError);
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldIgnoreErrorsSignalledAfterEndOfStream() throws IOException {
    // given
    final CircularBufferInputStream stream = new CircularBufferInputStream(16);
    stream.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
    stream.endOfStream();

    // when
    stream.signalError(new IOException("released after the response was fully received"));

    // then, the fully received data is still readable and the stream ends normally
    final byte[] data = new byte[3];
    assertThat(stream.read(data, 0, 3)).isEqualTo(3);
    assertThat(data).containsExactly(1, 2, 3);
    assertThat(stream.read()).isEqualTo(-1);
    assertThat(stream.read()).isEqualTo(-1);
  }

  @Test
  @Timeout(10)
  void shouldFailReaderWhenThereIsNotEnoughMemoryToGrowBuffer() {
    // given
    final CircularBufferInputStream stream =
        new CircularBufferInputStream(
            4,
            length -> {
              if (length > 4) {
                throw new OutOfMemoryError("Java heap space");
              }
              return new byte[length];
            });

    // when
    final Throwable writeFailure = catchThrowable(() -> stream.write(ByteBuffer.wrap(new byte[8])));

    // then
    assertThat(writeFailure)
        .isInstanceOf(IOException.class)
        .hasCauseInstanceOf(OutOfMemoryError.class);
    // the reader gets the failure instead of waiting for data that will never come
    assertThatThrownBy(stream::read).isSameAs(writeFailure);
  }

  @Test
  @Timeout(10)
  void shouldGrowBufferToRequiredLengthWhenLargerBufferCannotBeAllocated() throws IOException {
    // given
    final CircularBufferInputStream stream =
        new CircularBufferInputStream(
            4,
            length -> {
              if (length > 8) {
                throw new OutOfMemoryError("Java heap space");
              }
              return new byte[length];
            });

    // when
    stream.write(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
    stream.endOfStream();

    // then
    final byte[] data = new byte[8];
    assertThat(stream.read(data, 0, 8)).isEqualTo(8);
    assertThat(data).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
  }

  @Test
  void shouldGrowSlightlyPastCapacityWhenFirstOverrun() throws IOException {
    // an overrun by one read of the I/O layer
    assertThat(CircularBufferInputStream.grownLength(5 * MIB, 5 * MIB, 5 * MIB + 32 * KIB))
        .isEqualTo(5 * MIB + 64 * KIB);
  }

  @Test
  void shouldDoublePartBeyondCapacityWhenGrowingAgain() throws IOException {
    assertThat(
            CircularBufferInputStream.grownLength(5 * MIB, 5 * MIB + 64 * KIB, 5 * MIB + 65 * KIB))
        .isEqualTo(5 * MIB + 128 * KIB);
  }

  @Test
  void shouldGrowAtLeastToRequiredLength() throws IOException {
    assertThat(CircularBufferInputStream.grownLength(8, 8, 1_000_000)).isEqualTo(1_000_000);
  }

  @Test
  void shouldNotGrowBufferPastLargestArray() throws IOException {
    // doubling the part beyond the capacity is more than an int can hold
    assertThat(CircularBufferInputStream.grownLength(8, 1_500_000_000, 1_500_000_001L))
        .isEqualTo(MAX_BUFFER_LENGTH);
  }

  @Test
  void shouldFailWhenUnreadDataDoesNotFitIntoLargestArray() {
    assertThatThrownBy(
            () ->
                CircularBufferInputStream.grownLength(8, MAX_BUFFER_LENGTH, MAX_BUFFER_LENGTH + 1L))
        .isInstanceOf(IOException.class);
  }
}
