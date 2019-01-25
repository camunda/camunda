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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Test;

public class DbBufferViewTest {

  private final DbBufferView zbBufferView = new DbBufferView();

  @Test
  public void shouldWrapBuffer() {
    // given
    final DirectBuffer value = wrapString("a");
    zbBufferView.wrapBuffer(value);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbBufferView.write(buffer, 0);

    // then
    assertThat(zbBufferView.getLength()).isEqualTo(value.capacity());
    assertThat(zbBufferView.getValue()).isEqualTo(value);
    assertThat(buffer.getByte(0)).isEqualTo(value.getByte(0));
  }

  @Test
  public void shouldWrap() {
    // given
    final DirectBuffer value = wrapString("a");
    zbBufferView.wrap(value, 0, value.capacity());

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbBufferView.write(buffer, 0);

    // then
    assertThat(zbBufferView.getLength()).isEqualTo(value.capacity());
    assertThat(zbBufferView.getValue()).isEqualTo(value);
    assertThat(buffer.getByte(0)).isEqualTo(value.getByte(0));
  }

  @Test
  public void shouldNotCopyOnWrap() {
    final DirectBuffer value1 = wrapString("a");
    final DirectBuffer value2 = wrapString("b");

    // given
    final MutableDirectBuffer readBuffer = new ExpandableArrayBuffer();
    readBuffer.putBytes(0, value1, 0, value1.capacity());
    zbBufferView.wrap(readBuffer, 0, value1.capacity());

    final ExpandableArrayBuffer writeBuffer = new ExpandableArrayBuffer();
    zbBufferView.write(writeBuffer, 0);

    // when
    readBuffer.putBytes(0, value2, 0, value2.capacity());

    // then
    assertThat(zbBufferView.getValue()).isEqualTo(value2);
    assertThat(zbBufferView.getLength()).isEqualTo(value2.capacity());
    assertThat(writeBuffer.getByte(0)).isEqualTo(value1.getByte(0));
  }
}
