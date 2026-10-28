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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CircularBufferInputStreamTest {

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
}
