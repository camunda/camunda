/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import org.junit.jupiter.api.Test;

final class ObjectValueTest {
  @Test
  void shouldMaskSanitizedValuesInToString() {
    // given
    final TestObjectValue objectValue =
        new TestObjectValue("non-sanitized-value", "sanitized-value");

    // when
    final String result = objectValue.toString();

    // then
    assertThat(result)
        .isEqualTo(
            "{\"non-sanitized-property\":\"non-sanitized-value\",\"sanitized-property\":\"***\"}");
  }

  @Test
  void shouldNotLeaveASeparatorForAPropertyWithoutAValue() {
    // given a property without a default is printed only once it has a value
    final var objectValue = new TestObjectValueWithUnsetProperties(true);

    // when
    final String result = objectValue.toString();

    // then the properties without a value leave no separator behind
    assertThat(result).isEqualTo("{\"set-property\":1}");
  }

  @Test
  void shouldPrintNoSeparatorWhenNoPropertyHasAValue() {
    // given
    final var objectValue = new TestObjectValueWithUnsetProperties(false);

    // when
    final String result = objectValue.toString();

    // then
    assertThat(result).isEqualTo("{}");
  }

  private static final class TestObjectValueWithUnsetProperties extends ObjectValue {
    private final LongProperty unsetFirstProperty = new LongProperty("unset-first-property");
    private final LongProperty setProperty = new LongProperty("set-property");
    private final LongProperty unsetLastProperty = new LongProperty("unset-last-property");

    private TestObjectValueWithUnsetProperties(final boolean set) {
      super(3);
      declareProperty(unsetFirstProperty)
          .declareProperty(setProperty)
          .declareProperty(unsetLastProperty);
      if (set) {
        setProperty.setValue(1L);
      }
    }
  }

  private static final class TestObjectValue extends ObjectValue {
    private final StringProperty nonSanitizedProperty =
        new StringProperty("non-sanitized-property");
    private final StringProperty sanitizedProperty =
        new StringProperty("sanitized-property").sanitized();

    private TestObjectValue(final String nonSanitizedProperty, final String sanitizedProperty) {
      super(2);
      declareProperty(this.nonSanitizedProperty).declareProperty(this.sanitizedProperty);
      this.nonSanitizedProperty.setValue(nonSanitizedProperty);
      this.sanitizedProperty.setValue(sanitizedProperty);
    }
  }
}
