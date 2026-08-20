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
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.protocol.record.ValueTypeMapping.Mapping;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ValueTypeMappingTest {
  @Test
  void shouldNotAcceptSBESyntheticValues() {
    // given values automatically added by SBE, i.e. "synthetic"
    final EnumSet<ValueType> syntheticValues =
        EnumSet.of(ValueType.NULL_VAL, ValueType.SBE_UNKNOWN);
    final Set<ValueType> nonSyntheticValueTypes = EnumSet.complementOf(syntheticValues);

    // when
    final Set<ValueType> acceptedValueTypes = ValueTypeMapping.getAcceptedValueTypes();

    // then
    assertThat(acceptedValueTypes)
        .doesNotContainAnyElementsOf(syntheticValues)
        .containsExactlyElementsOf(nonSyntheticValueTypes);
  }

  @Test
  void shouldMapAllValueTypes() {
    // given
    final Set<ValueType> acceptedValueTypes = ValueTypeMapping.getAcceptedValueTypes();

    // when
    assertThat(acceptedValueTypes)
        .allSatisfy(
            valueType -> {
              final Mapping<?, ?> typeInfo = ValueTypeMapping.get(valueType);
              assertThat(RecordValue.class)
                  .as(
                      "value type '%s' should have a value class which implements RecordValue",
                      valueType)
                  .isAssignableFrom(typeInfo.getValueClass());
              assertThat(Intent.class)
                  .as(
                      "value type '%s' should have an intent class which implements Intent",
                      valueType)
                  .isAssignableFrom(typeInfo.getIntentClass());
            });
  }

  @Test
  void shouldThrowOnUnmappedValueType() {
    // given
    final ValueType unmappedType = ValueType.NULL_VAL;

    // then
    assertThatCode(() -> ValueTypeMapping.get(unmappedType))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
