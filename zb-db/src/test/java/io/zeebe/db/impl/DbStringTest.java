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
package io.zeebe.db.impl;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class DbStringTest {

  private final DbString zbString = new DbString();

  @Test
  public void shouldWrapString() {
    // given
    zbString.wrapString("foo");

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("foo");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("foo");
  }

  @Test
  public void shouldWrapBuffer() {
    // given
    zbString.wrapBuffer(wrapString("foo"));

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("foo");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("foo");
  }

  @Test
  public void shouldWrapBufferView() {
    // given
    final String value = "foobar";
    final DirectBuffer view = new UnsafeBuffer(0, 0);
    view.wrap(value.getBytes(), 0, 3);

    zbString.wrapBuffer(view);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("foo");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("foo");
  }

  @Test
  public void shouldWrap() {
    // given
    final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
    valueBuffer.putInt(0, 3, ZB_DB_BYTE_ORDER);
    valueBuffer.putBytes(Integer.BYTES, "bar".getBytes());
    zbString.wrap(valueBuffer, 0, 3 + Integer.BYTES);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbString.write(buffer, 0);

    // then
    assertThat(zbString.getLength()).isEqualTo(3 + Integer.BYTES);
    assertThat(zbString.toString()).isEqualTo("bar");
    assertThat(buffer.getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(3);
    assertThat(BufferUtil.bufferAsString(buffer, Integer.BYTES, 3)).isEqualTo("bar");
  }
}
