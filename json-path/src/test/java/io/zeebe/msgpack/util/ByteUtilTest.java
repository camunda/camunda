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
package io.zeebe.msgpack.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class ByteUtilTest {

  @Test
  public void testIsNumeric() {
    // given
    final DirectBuffer buffer =
        new UnsafeBuffer("foo0123456789bar".getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(ByteUtil.isNumeric(buffer, 0, buffer.capacity())).isFalse();
    assertThat(ByteUtil.isNumeric(buffer, 3, 10)).isTrue();
    assertThat(ByteUtil.isNumeric(buffer, 2, 10)).isFalse();
    assertThat(ByteUtil.isNumeric(buffer, 3, 11)).isFalse();
  }

  @Test
  public void testParseInteger() {
    // given
    final DirectBuffer buffer = new UnsafeBuffer("foo56781bar".getBytes(StandardCharsets.UTF_8));

    // then
    assertThat(ByteUtil.parseInteger(buffer, 3, 5)).isEqualTo(56781);
  }

  @Test
  public void testBytesToBinary() {
    // given
    final byte[] bytes = new byte[] {-0x01, 0x1a};

    // when
    final String binary = ByteUtil.bytesToBinary(bytes);

    // then
    assertThat(binary).isEqualTo("11111111, 00011010, ");
  }
}
