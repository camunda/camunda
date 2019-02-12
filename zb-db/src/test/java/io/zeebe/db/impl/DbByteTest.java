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

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.junit.Test;

public class DbByteTest {

  private final DbByte zbByte = new DbByte();

  @Test
  public void shouldWrapByte() {
    // given
    zbByte.wrapByte((byte) 255);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbByte.write(buffer, 0);

    // then
    assertThat(zbByte.getLength()).isEqualTo(Byte.BYTES);
    assertThat(zbByte.getValue()).isEqualTo((byte) 255);
    assertThat(buffer.getByte(0)).isEqualTo((byte) 255);
  }

  @Test
  public void shouldWrap() {
    // given
    final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
    valueBuffer.putByte(0, (byte) 255);
    zbByte.wrap(valueBuffer, 0, 1);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbByte.write(buffer, 0);

    // then
    assertThat(zbByte.getLength()).isEqualTo(Byte.BYTES);
    assertThat(zbByte.getValue()).isEqualTo((byte) 255);
    assertThat(buffer.getByte(0)).isEqualTo((byte) 255);
  }
}
