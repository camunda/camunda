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
package io.zeebe.util.buffer;

import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class BufferUtilTest {
  protected static final byte[] BYTES1 = getBytes("foo");
  protected static final byte[] BYTES2 = getBytes("bar");
  protected static final byte[] BYTES3 = new byte[BYTES1.length + BYTES2.length];

  static {
    System.arraycopy(BYTES1, 0, BYTES3, 0, BYTES1.length);
    System.arraycopy(BYTES2, 0, BYTES3, BYTES1.length, BYTES2.length);
  }

  @Test
  public void testEquals() {
    assertThat(BufferUtil.contentsEqual(asBuffer(BYTES1), asBuffer(BYTES1))).isTrue();
    assertThat(BufferUtil.contentsEqual(asBuffer(BYTES1), asBuffer(BYTES2))).isFalse();
    assertThat(BufferUtil.contentsEqual(asBuffer(BYTES1), asBuffer(BYTES3))).isFalse();
    assertThat(BufferUtil.contentsEqual(asBuffer(BYTES3), asBuffer(BYTES1))).isFalse();
  }

  @Test
  public void testCloneUnsafeBuffer() {
    // given
    final DirectBuffer src = new UnsafeBuffer(BYTES1);

    // when
    final DirectBuffer dst = cloneBuffer(src);

    // then
    assertThat(dst).isNotSameAs(src).isEqualTo(src).hasSameClassAs(src);
  }

  @Test
  public void testCloneExpandableArrayBuffer() {
    // given
    final MutableDirectBuffer src = new ExpandableArrayBuffer(BYTES1.length);
    src.putBytes(0, BYTES1);

    // when
    final DirectBuffer dst = cloneBuffer(src);

    // then
    assertThat(dst).isNotSameAs(src).isEqualTo(src).hasSameClassAs(src);
  }

  @Test
  public void shouldReturnCopyOfByteArrayWhenWrappingPartialArray() {
    // given
    final byte[] expected = new byte[BYTES1.length - 1];
    final DirectBuffer buffer = new UnsafeBuffer();
    buffer.wrap(BYTES1, 1, BYTES1.length - 1);

    // when
    final byte[] bytes = BufferUtil.bufferAsArray(buffer);
    System.arraycopy(BYTES1, 1, expected, 0, expected.length);

    // then
    assertThat(buffer.byteArray()).isEqualTo(BYTES1);
    assertThat(bytes).isEqualTo(expected);
    assertThat(bytes).isNotEqualTo(BYTES1);
  }

  @Test
  public void shouldReturnNewByteArrayWhenNotWrappingByteArray() {
    // given
    final MutableDirectBuffer buffer = new ExpandableDirectByteBuffer();
    buffer.putBytes(0, BYTES1);

    // when
    final byte[] bytes = BufferUtil.bufferAsArray(buffer);

    // then
    assertThat(buffer.byteArray()).isNull();
    for (int i = 0; i < BYTES1.length; i++) {
      assertThat(bytes[i]).isEqualTo(buffer.getByte(i));
    }
  }

  /**
   * Returning the wrapped array is an inconsistency in the API, because we cannot safely modify the
   * returned array.
   */
  @Test
  public void shouldNotReturnWrappedArrayIfBufferOffsetIsZero() {
    // given
    final DirectBuffer buffer = new UnsafeBuffer(BYTES1);

    // when
    final byte[] bytes = BufferUtil.bufferAsArray(buffer);

    // then
    assertThat(buffer.byteArray()).isEqualTo(BYTES1);
    assertThat(bytes).isNotSameAs(BYTES1);
  }

  public DirectBuffer asBuffer(byte[] bytes) {
    return new UnsafeBuffer(bytes);
  }
}
