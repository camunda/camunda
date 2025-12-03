/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class UnifiedRecordValueTest {

  private static final Set<ValueType> EXPECTED_NULL_VALUE_TYPES =
      Set.of(ValueType.SBE_UNKNOWN, ValueType.NULL_VAL);

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldReturnNonNullRecordValueForValueType(final ValueType valueType) {
    // given
    final var expectedNull = EXPECTED_NULL_VALUE_TYPES.contains(valueType);
    // when
    final UnifiedRecordValue result = UnifiedRecordValue.fromValueType(valueType);

    // then
    if (expectedNull) {
      assertThat(result).as("Expected null for ValueType.%s", valueType.name()).isNull();
    } else {
      assertThat(result)
          .as("Expected non-null record value for ValueType.%s", valueType.name())
          .isNotNull();

      // Enforce the naming convention "${camelCase(valueType)}Record"
      // Checks that every value type is mapped to the right record
      assertThat(result.getClass().getSimpleName())
          .isEqualTo(snakeCaseToCamelCase(valueType.name().toLowerCase()) + "Record");
    }
  }

  /**
   * Converts a SNAKE_CASE string to CamelCase.
   *
   * @param snakeCase the input string in SNAKE_CASE format
   * @return the converted string in CamelCase format
   */
  private static String snakeCaseToCamelCase(final String snakeCase) {
    final StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (final char c : snakeCase.toLowerCase().toCharArray()) {
      if (c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static Stream<Arguments> provideValueTypes() {
    return Arrays.stream(ValueType.values()).map(Arguments::of);
  }
}
