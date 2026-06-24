/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VariableTypeFilterTest {

  // ---------------------------------------------------------------------------
  // Constructor null checks
  // ---------------------------------------------------------------------------

  @Test
  void shouldRejectNullObjectMapper() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VariableTypeFilter(null, Set.of(), Set.of()))
        .withMessage("objectMapper must not be null");
  }

  @Test
  void shouldRejectNullInclusion() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VariableTypeFilter(null, Set.of()))
        .withMessage("inclusion must not be null");
  }

  @Test
  void shouldRejectNullExclusion() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VariableTypeFilter(Set.of(), null))
        .withMessage("exclusion must not be null");
  }

  // ---------------------------------------------------------------------------
  // accept(...) behavior
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptAllRecordsWhenInclusionAndExclusionEmpty() {
    // given
    final var filter = new VariableTypeFilter(/* inclusion */ Set.of(), /* exclusion */ Set.of());

    final var variableRecord = variableRecord("true");
    final var nonVariableRecord = nonVariableRecord();

    // when / then
    assertThat(filter.accept(variableRecord)).as("variable record should be accepted").isTrue();
    assertThat(filter.accept(nonVariableRecord))
        .as("non-variable record should be accepted")
        .isTrue();
  }

  @Test
  void shouldFilterByInclusionOnly() {
    // given
    final var filter =
        new VariableTypeFilter(
            /* inclusion */ Set.of(VariableValueType.BOOLEAN, VariableValueType.STRING),
            /* exclusion */ Set.of());

    final var booleanVar = variableRecord("true");
    final var stringVar = variableRecord("\"foo\"");
    final var numberVar = variableRecord("42");

    // when / then
    assertThat(filter.accept(booleanVar)).isTrue();
    assertThat(filter.accept(stringVar)).isTrue();
    assertThat(filter.accept(numberVar)).isFalse();
  }

  @Test
  void shouldFilterByExclusionOnly() {
    // given
    final var filter =
        new VariableTypeFilter(
            /* inclusion */ Set.of(),
            /* exclusion */ Set.of(VariableValueType.BOOLEAN, VariableValueType.STRING));

    final var booleanVar = variableRecord("false");
    final var stringVar = variableRecord("\"bar\"");
    final var numberVar = variableRecord("123.4");

    // when / then
    assertThat(filter.accept(booleanVar)).isFalse();
    assertThat(filter.accept(stringVar)).isFalse();
    assertThat(filter.accept(numberVar)).isTrue();
  }

  // ---------------------------------------------------------------------------
  // parseTypes(...)
  // ---------------------------------------------------------------------------

  @Test
  void shouldReturnEmptySetForNullOrEmptyRawTypes() {
    assertThat(VariableTypeFilter.parseTypes(null)).isEmpty();
    assertThat(VariableTypeFilter.parseTypes(List.of())).isEmpty();
  }

  @Test
  void shouldParseValidTypeTokensCaseInsensitively() {
    // given
    final var rawValues = List.of("boolean", "NUMBER", "String", "ObJeCt");

    // when
    final var types = VariableTypeFilter.parseTypes(rawValues);

    // then
    assertThat(types)
        .containsExactlyInAnyOrder(
            VariableValueType.BOOLEAN,
            VariableValueType.NUMBER,
            VariableValueType.STRING,
            VariableValueType.OBJECT);
  }

  @Test
  void shouldIgnoreUnknownEmptyAndNullTokens() {
    // given
    final var rawValues =
        Arrays.asList(
            "boolean",
            "  ", // empty after trim
            null, // allowed here
            "unknown",
            "does-not-exist",
            "STRING");

    // when
    final var types = VariableTypeFilter.parseTypes(rawValues);

    // then
    assertThat(types)
        .containsExactlyInAnyOrder(VariableValueType.BOOLEAN, VariableValueType.STRING);
  }

  @Test
  void shouldExposeMinimumBrokerVersion() {
    final var filter = new VariableTypeFilter(Set.of(), Set.of());

    assertThat(filter.minRecordBrokerVersion()).isEqualTo(new SemanticVersion(8, 9, 0, null, null));
  }

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Record<?> variableRecord(final String value) {
    final var variableValue = mock(VariableRecordValue.class);
    when(variableValue.getValue()).thenReturn(value);

    final var record = (Record<VariableRecordValue>) mock(Record.class);
    when(record.getValue()).thenReturn(variableValue);

    return record;
  }

  private static Record<?> nonVariableRecord() {
    @SuppressWarnings("unchecked")
    final var record = (Record<RecordValue>) mock(Record.class);

    final var someOtherValue = mock(RecordValue.class);
    when(record.getValue()).thenReturn(someOtherValue);

    return record;
  }
}
