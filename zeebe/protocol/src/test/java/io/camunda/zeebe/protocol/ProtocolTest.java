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
package io.camunda.zeebe.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

public final class ProtocolTest {

  @Test
  public void testEndiannessConstant() {
    assertThat(Protocol.ENDIANNESS).isEqualTo(ByteOrder.LITTLE_ENDIAN);
  }

  @Test
  public void shouldNotEncodeNegativeKeys() {
    // given
    final long value = -1029819L;

    // when/then
    assertThatThrownBy(() -> Protocol.encodePartitionId(128, value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid key provided: got -1029819, expected a positive value");
  }

  @Test
  public void shouldNotEncodeKeyAlreadyEncoded() {
    // given
    final long value = 1029819L;
    final long encoded = Protocol.encodePartitionId(128, value);

    // when/then
    assertThatThrownBy(() -> Protocol.encodePartitionId(128, encoded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Invalid key provided: got 288230376152741563, but it has the partitionId encoded already (partitionId=128)");
  }
}
