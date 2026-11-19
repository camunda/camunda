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
package io.camunda.zeebe.protocol.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;

/** The codes are persisted in the log, so they must stay unique and map back to their type. */
final class InternalOperationTypeTest {

  @ParameterizedTest
  @EnumSource(value = InternalOperationType.class, mode = Mode.EXCLUDE, names = "UNKNOWN")
  void shouldMapCodeBackToType(final InternalOperationType type) {
    // when
    final InternalOperationType mapped = InternalOperationType.fromCode(type.code());

    // then
    assertThat(mapped).isEqualTo(type);
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 200, 254, -1})
  void shouldMapUnknownCodeToUnknown(final int code) {
    // when
    final InternalOperationType mapped = InternalOperationType.fromCode(code);

    // then
    assertThat(mapped).isEqualTo(InternalOperationType.UNKNOWN);
  }

  @Test
  void shouldHaveUniqueCodes() {
    // when
    final long distinctCodes =
        Arrays.stream(InternalOperationType.values())
            .mapToInt(InternalOperationType::code)
            .distinct()
            .count();

    // then
    assertThat(distinctCodes).isEqualTo(InternalOperationType.values().length);
  }
}
