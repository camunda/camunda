/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.util.ValueTypeMapping.Mapping;
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
              final Mapping<?, ?> typeInfo = ValueTypeMapping.getTypeInfoOrNull(valueType);
              assertThat(typeInfo)
                  .as("value type '%s' should have a type info mapping", valueType)
                  .isNotNull();
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
    assertThatCode(() -> ValueTypeMapping.getTypeInfo(unmappedType))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
