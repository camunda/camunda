/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.metadata.store.api.KeyEvent.ChangeType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which values an event carries follows from its type, so a listener can rely on it. */
final class KeyEventTest {

  private static final StoreKey KEY = StoreKey.of(Namespace.cluster("config"), "a");
  private static final StoreValue VALUE = StoreValue.of(new byte[] {1});

  @ParameterizedTest(name = "{0} current={1} previous={2}")
  @CsvSource({"ADD, true, false", "REPLACE, true, true", "REMOVE, false, true"})
  void shouldAcceptValuesMatchingType(
      final ChangeType type, final boolean current, final boolean previous) {
    // when - then
    assertThatCode(() -> new KeyEvent(type, KEY, value(current), value(previous)))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "{0} current={1} previous={2}")
  @CsvSource({
    "ADD, false, false",
    "ADD, true, true",
    "ADD, false, true",
    "REPLACE, true, false",
    "REPLACE, false, true",
    "REMOVE, true, true",
    "REMOVE, false, false"
  })
  void shouldRejectValuesNotMatchingType(
      final ChangeType type, final boolean current, final boolean previous) {
    // when - then
    assertThatThrownBy(() -> new KeyEvent(type, KEY, value(current), value(previous)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static @Nullable StoreValue value(final boolean present) {
    return present ? VALUE : null;
  }
}
