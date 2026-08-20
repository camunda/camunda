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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class ZbColumnFamiliesTest {

  @Test
  void shouldNotReuseEnumValues() {
    assertThat(Arrays.stream(ZbColumnFamilies.values()).map(ZbColumnFamilies::getValue))
        .doesNotHaveDuplicates();
  }

  /** If this test case fails, you can update ZbColumnFamilies to include all known enum values. */
  @Test
  void shouldNotSkipEnumValues() {
    assertThat(
            Arrays.stream(ZbColumnFamilies.values()).mapToInt(ZbColumnFamilies::getValue).toArray())
        .describedAs("The enum values must be sequential")
        .isEqualTo(IntStream.range(0, ZbColumnFamilies.values().length).toArray());
  }

  public static Stream<Arguments> values() {
    return Arrays.stream(ZbColumnFamilies.values()).map(Arguments::of);
  }
}
