/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The shared read-back normalization every serving backend relies on: loose store representations
 * (JSON {@code Integer}s, driver-specific numerics, stringified aggregation bucket keys) coerce to
 * the exact Java type a {@link DimensionKey} carries for the column.
 */
final class DimensionTypeCoercionTest {

  @Test
  void shouldCoerceNumbersToTheDeclaredWidth() {
    // given loose numeric representations (a JSON Integer, a driver Long)
    // when / then they normalize to the declared type
    assertThat(DimensionType.LONG.coerce(3)).isEqualTo(3L);
    assertThat(DimensionType.LONG.coerce(3L)).isEqualTo(3L);
    assertThat(DimensionType.INT.coerce(3L)).isEqualTo(3);
    assertThat(DimensionType.INT.coerce(3)).isEqualTo(3);
  }

  @Test
  void shouldParseStringInputsForNumericTypes() {
    // given a numeric dimension value that arrives as text (e.g. a stringified bucket key)
    // when / then it parses instead of class-cast failing
    assertThat(DimensionType.LONG.coerce("42")).isEqualTo(42L);
    assertThat(DimensionType.INT.coerce("7")).isEqualTo(7);
  }

  @Test
  void shouldParseBooleanAndStringInputs() {
    assertThat(DimensionType.BOOLEAN.coerce(Boolean.TRUE)).isEqualTo(true);
    assertThat(DimensionType.BOOLEAN.coerce("true")).isEqualTo(true);
    assertThat(DimensionType.STRING.coerce(42L)).isEqualTo("42");
    assertThat(DimensionType.TEXT.coerce("payload")).isEqualTo("payload");
  }

  @Test
  void shouldPassNullThrough() {
    for (final DimensionType type : DimensionType.values()) {
      assertThat(type.coerce(null)).isNull();
    }
  }

  @Test
  void shouldRejectUnparseableNumericText() {
    // given text that is not a number for a numeric dimension
    // when / then coercion fails loudly instead of mis-assigning
    assertThatThrownBy(() -> DimensionType.LONG.coerce("not-a-number"))
        .isInstanceOf(NumberFormatException.class);
    assertThatThrownBy(() -> DimensionType.INT.coerce("not-a-number"))
        .isInstanceOf(NumberFormatException.class);
  }
}
