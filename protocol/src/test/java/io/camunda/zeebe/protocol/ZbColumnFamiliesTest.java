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

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ZbColumnFamiliesTest {

  /**
   * This is just a temporary test to ensure that the enum values are not reordered, when we
   * re-implment the getValue() method.
   */
  @ParameterizedTest
  @MethodSource("values")
  void shouldReturnOrdinalForEnumValue(final ZbColumnFamilies columnFamily) {
    assertThat(columnFamily.getValue()).isEqualTo(columnFamily.ordinal());
  }

  @Test
  void shouldNotReuseEnumValues() {
    assertThat(Arrays.stream(ZbColumnFamilies.values()).map(ZbColumnFamilies::getValue))
        .doesNotHaveDuplicates();
  }

  public static Stream<Arguments> values() {
    return Arrays.stream(ZbColumnFamilies.values()).map(Arguments::of);
  }
}
