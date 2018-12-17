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
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.ExpandableArrayBuffer;
import org.junit.Test;

/** */
public class DbLongTest {

  private final DbLong zbLong = new DbLong();

  @Test
  public void shouldWrapLong() {
    // given
    zbLong.wrapLong(234L);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbLong.write(buffer, 0);

    // then
    assertThat(zbLong.getLength()).isEqualTo(Long.BYTES);
    assertThat(zbLong.getValue()).isEqualTo(234L);
    assertThat(buffer.getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(234L);
  }

  @Test
  public void shouldWrap() {
    // given
    final ExpandableArrayBuffer longBuffer = new ExpandableArrayBuffer();
    longBuffer.putLong(0, 234, ZB_DB_BYTE_ORDER);
    zbLong.wrap(longBuffer, 0, Long.BYTES);

    // when
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    zbLong.write(buffer, 0);

    // then
    assertThat(zbLong.getLength()).isEqualTo(Long.BYTES);
    assertThat(zbLong.getValue()).isEqualTo(234L);
    assertThat(buffer.getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(234L);
  }
}
