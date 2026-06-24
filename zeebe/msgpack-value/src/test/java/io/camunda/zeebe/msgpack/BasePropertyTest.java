/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.property.BaseProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class BasePropertyTest {
  @Test
  void shouldMaskSanitizedValuesInToString() {
    // given
    final TestProperty property = new TestProperty("non-sanitized-value").sanitized();

    // when
    final String result = property.toString();

    // then
    assertThat(property.isSanitized()).isTrue();
    assertThat(result).isEqualTo("test-key => ***");
  }

  @Test
  void shouldNotMaskNonSanitizedValuesInToString() {
    // given
    final TestProperty property = new TestProperty("non-sanitized-value");

    // when
    final String result = property.toString();

    // then
    assertThat(property.isSanitized()).isFalse();
    assertThat(result).isEqualTo("test-key => non-sanitized-value");
  }

  @Test
  void shouldMaskSanitizedPropertiesWhenWritingJson() {
    // given
    final var sb = new StringBuilder();
    final TestProperty property = new TestProperty("non-sanitized-value").sanitized();

    // when
    property.writeJSON(sb, true);

    // then
    assertThat(property.isSanitized()).isTrue();
    assertThat(sb).hasToString("\"test-key\":\"***\"");
  }

  @Test
  void shouldNotMaskSanitizedPropertiesWhenWritingJson() {
    // given
    final var sb = new StringBuilder();
    final TestProperty property = new TestProperty("non-sanitized-value").sanitized();

    // when
    property.writeJSON(sb, false);

    // then
    assertThat(property.isSanitized()).isTrue();
    assertThat(sb).hasToString("\"test-key\":\"non-sanitized-value\"");
  }

  private static final class TestProperty extends BaseProperty<StringValue> {

    private TestProperty(final String value) {
      super("test-key", new StringValue());
      this.value.wrap(value.getBytes(StandardCharsets.UTF_8));
      isSet = true;
    }
  }
}
