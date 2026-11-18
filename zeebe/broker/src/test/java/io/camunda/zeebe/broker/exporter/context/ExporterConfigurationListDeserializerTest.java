/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Unit tests for {@link ExporterConfigurationListDeserializer}.
 *
 * <p>The deserializer handles four distinct token cases: a proper JSON array ({@code START_ARRAY}),
 * a map with numeric keys ({@code START_OBJECT}), an empty string (Spring Boot flattens {@code []}
 * to {@code ""}), and a non-empty string (Spring Boot single-value shorthand). An unexpected token
 * type must throw immediately.
 *
 * <p>All scenarios are exercised through {@link ExporterConfiguration#fromArgs} because the
 * deserializer is only registered on the package-private {@link ExporterConfiguration#MAPPER}.
 */
@Execution(ExecutionMode.CONCURRENT)
final class ExporterConfigurationListDeserializerTest {

  // ---------------------------------------------------------------------------
  // START_ARRAY — standard JSON / YAML array syntax
  // ---------------------------------------------------------------------------

  @Test
  void shouldDeserializeFromJsonArray() {
    // given — List.of(...) goes through normalizeValue's Iterable branch and becomes an ArrayList,
    // which Jackson serialises as a JSON array → START_ARRAY token hits deserializeArray()
    final var args = Map.<String, Object>of("items", List.of("a", "b", "c"));

    // when
    final var instance = ExporterConfiguration.fromArgs(StringListConfig.class, args);

    // then
    assertThat(instance.items()).containsExactly("a", "b", "c");
  }

  @Test
  void shouldDeserializeEmptyJsonArrayAsEmptyList() {
    // given
    final var args = Map.<String, Object>of("items", List.of());

    // when
    final var instance = ExporterConfiguration.fromArgs(StringListConfig.class, args);

    // then
    assertThat(instance.items()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // START_OBJECT — Spring Boot indexed properties (myList[0]=value)
  // ---------------------------------------------------------------------------

  @Test
  void shouldDeserializeMapWithNumericKeysAsList() {
    // given — Spring Boot flattens myList[0]=a, myList[1]=b to {"0": "a", "1": "b"}
    final var args = Map.<String, Object>of("items", Map.of("0", "a", "1", "b"));

    // when
    final var instance = ExporterConfiguration.fromArgs(StringListConfig.class, args);

    // then
    assertThat(instance.items()).containsExactly("a", "b");
  }

  // ---------------------------------------------------------------------------
  // VALUE_STRING — Spring Boot empty-list and single-value shorthands
  // ---------------------------------------------------------------------------

  @Test
  void shouldDeserializeEmptyStringAsEmptyList() {
    // given — Spring Boot flattens an empty YAML list (`items: []`) to an empty string
    final var args = Map.<String, Object>of("items", "");

    // when
    final var instance = ExporterConfiguration.fromArgs(StringListConfig.class, args);

    // then
    assertThat(instance.items()).isEmpty();
  }

  @Test
  void shouldDeserializeNonEmptyStringAsSingletonList() {
    // given — Spring Boot may write a single value without array syntax (items: hello)
    final var args = Map.<String, Object>of("items", "hello");

    // when
    final var instance = ExporterConfiguration.fromArgs(StringListConfig.class, args);

    // then
    assertThat(instance.items()).containsExactly("hello");
  }

  // ---------------------------------------------------------------------------
  // Unexpected token
  // ---------------------------------------------------------------------------

  @Test
  void shouldThrowOnUnexpectedTokenType() {
    // given — an integer is not a valid token type for List deserialization
    final var args = Map.<String, Object>of("items", 42);

    // when - then
    assertThatCode(() -> ExporterConfiguration.fromArgs(StringListConfig.class, args))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected START_ARRAY, START_OBJECT, or VALUE_STRING");
  }

  // ---------------------------------------------------------------------------
  // Sparse / non-integer numeric key validation (map → list conversion)
  // ---------------------------------------------------------------------------

  @Test
  void shouldThrowOnSparseNumericKeys() {
    // given — keys start at 1 (gap at 0) making list insertion fail with IndexOutOfBoundsException
    final var args = Map.<String, Object>of("items", Map.of("1", "a", "2", "b"));

    // when - then
    assertThatCode(() -> ExporterConfiguration.fromArgs(StringListConfig.class, args))
        .isInstanceOf(IllegalArgumentException.class)
        .hasRootCauseInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void shouldThrowOnNonNumericMapKeys() {
    // given — non-integer keys cannot be interpreted as list indices
    final var args = Map.<String, Object>of("items", Map.of("foo", "a", "bar", "b"));

    // when - then
    assertThatCode(() -> ExporterConfiguration.fromArgs(StringListConfig.class, args))
        .isInstanceOf(IllegalArgumentException.class)
        .hasRootCauseInstanceOf(NumberFormatException.class);
  }

  // ---------------------------------------------------------------------------
  // Test fixtures
  // ---------------------------------------------------------------------------

  private record StringListConfig(List<String> items) {}
}
