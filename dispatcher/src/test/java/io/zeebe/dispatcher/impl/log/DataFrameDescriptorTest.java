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
package io.zeebe.dispatcher.impl.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DataFrameDescriptorTest {

  @Test
  public void shouldTestFlags() {
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b0010_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b1111_1111)).isTrue();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b1010_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b0000_0000)).isFalse();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b1000_0000)).isFalse();

    assertThat(DataFrameDescriptor.flagBatchBegin((byte) 0b1000_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchBegin((byte) 0b1111_1111)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchBegin((byte) 0b1010_0000)).isTrue();

    assertThat(DataFrameDescriptor.flagBatchEnd((byte) 0b0100_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchEnd((byte) 0b1111_1111)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchEnd((byte) 0b1100_0000)).isTrue();
  }

  @Test
  public void shouldEnableFlags() {
    assertThat(DataFrameDescriptor.enableFlagFailed((byte) 0b0000_0000))
        .isEqualTo((byte) 0b0010_0000);
    assertThat(DataFrameDescriptor.enableFlagFailed((byte) 0b0010_0000))
        .isEqualTo((byte) 0b0010_0000);
    assertThat(DataFrameDescriptor.enableFlagFailed((byte) 0b1101_1111))
        .isEqualTo((byte) 0b1111_1111);

    assertThat(DataFrameDescriptor.enableFlagBatchBegin((byte) 0b0000_0000))
        .isEqualTo((byte) 0b1000_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchBegin((byte) 0b1000_0000))
        .isEqualTo((byte) 0b1000_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchBegin((byte) 0b0111_1111))
        .isEqualTo((byte) 0b1111_1111);

    assertThat(DataFrameDescriptor.enableFlagBatchEnd((byte) 0b0000_0000))
        .isEqualTo((byte) 0b0100_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchEnd((byte) 0b0100_0000))
        .isEqualTo((byte) 0b0100_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchEnd((byte) 0b1011_1111))
        .isEqualTo((byte) 0b1111_1111);
  }
}
