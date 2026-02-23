/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.filter.VariableTypeFilter.VariableValueType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class VariableTypeFilterTest {

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

  @Test
  void shouldLetExclusionOverrideInclusionWhenBothPresent() {
    // given
    final var filter =
        new VariableTypeFilter(
            /* inclusion */ Set.of(VariableValueType.STRING, VariableValueType.OBJECT),
            /* exclusion */ Set.of(VariableValueType.STRING));

    final var stringVar = variableRecord("\"foo\"");
    final var objectVar = variableRecord("{\"a\":1}");

    // when / then
    assertThat(filter.accept(stringVar))
        .as("STRING is included but also excluded -> should be rejected")
        .isFalse();
    assertThat(filter.accept(objectVar)).as("OBJECT is only included -> accepted").isTrue();
  }

  @Test
  void shouldSupportNullVariablesWhenExplicitlyIncluded() {
    // given
    final var filter =
        new VariableTypeFilter(
            /* inclusion */ Set.of(VariableValueType.NULL), /* exclusion */ Set.of());

    final var nullVar = variableRecord(null);
    final var nonNullVar = variableRecord("\"foo\"");

    // when / then
    assertThat(filter.accept(nullVar)).isTrue();
    assertThat(filter.accept(nonNullVar)).isFalse();
  }

  @Test
  void shouldInferJsonNullAsNullType() {
    // Only allow NULL so we can detect that branch via accept()
    final var filter =
        new VariableTypeFilter(
            EnumSet.of(VariableTypeFilter.VariableValueType.NULL), Collections.emptySet());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);

    when(record.getValue()).thenReturn(value);
    // Jackson will parse this into a NullNode with nodeType == JsonNodeType.NULL
    when(value.getValue()).thenReturn("null");

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted)
        .as("JSON literal 'null' should be inferred as VariableValueType.NULL")
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Type inference via accept(...)
  // ---------------------------------------------------------------------------

  @Test
  void shouldReturnEmptySetForNullOrEmptyRawTypes() {
    assertThat(VariableTypeFilter.parseTypes(null)).isEmpty();
    assertThat(VariableTypeFilter.parseTypes(List.of())).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // parseTypes(...)
  // ---------------------------------------------------------------------------

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
  // Versioning
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Record<?> variableRecord(final String value) {
    final var variableValue = mock(VariableRecordValue.class);
    when(variableValue.getValue()).thenReturn(value);

    final var record = (Record<VariableRecordValue>) mock(Record.class);
    when(record.getValue()).thenReturn(variableValue);

    return record;
  }

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  private static Record<?> nonVariableRecord() {
    @SuppressWarnings("unchecked")
    final var record = (Record<RecordValue>) mock(Record.class);

    final var someOtherValue = mock(RecordValue.class);
    when(record.getValue()).thenReturn(someOtherValue);

    return record;
  }

  @Nested
  final class TypeInference {

    private final VariableTypeFilter booleanOnlyFilter =
        new VariableTypeFilter(Set.of(VariableValueType.BOOLEAN), Set.of());
    private final VariableTypeFilter doubleOnlyFilter =
        new VariableTypeFilter(Set.of(VariableValueType.NUMBER), Set.of());
    private final VariableTypeFilter stringOnlyFilter =
        new VariableTypeFilter(Set.of(VariableValueType.STRING), Set.of());
    private final VariableTypeFilter objectOnlyFilter =
        new VariableTypeFilter(Set.of(VariableValueType.OBJECT), Set.of());

    @Test
    void shouldInferBooleanFromJsonBoolean() {
      assertThat(booleanOnlyFilter.accept(variableRecord("true"))).isTrue();
      assertThat(booleanOnlyFilter.accept(variableRecord("false"))).isTrue();
      assertThat(booleanOnlyFilter.accept(variableRecord("\"true\"")))
          .as("JSON string '\"true\"' is STRING, not BOOLEAN")
          .isFalse();
    }

    @Test
    void shouldInferDoubleFromJsonNumber() {
      assertThat(doubleOnlyFilter.accept(variableRecord("0"))).isTrue();
      assertThat(doubleOnlyFilter.accept(variableRecord("1.23"))).isTrue();
      assertThat(doubleOnlyFilter.accept(variableRecord("\"1.23\"")))
          .as("JSON string '\"1.23\"' is STRING, not DOUBLE")
          .isFalse();
    }

    @Test
    void shouldInferStringFromJsonString() {
      assertThat(stringOnlyFilter.accept(variableRecord("\"foo\""))).isTrue();
    }

    @Test
    void shouldInferUnknownFromInvalidJson() {
      assertThat(stringOnlyFilter.accept(variableRecord("foo")))
          .as("invalid JSON treated as UNKNOWN")
          .isFalse();
    }

    @Test
    void shouldInferObjectFromJsonObjectOrArray() {
      assertThat(objectOnlyFilter.accept(variableRecord("{\"a\":1}"))).isTrue();
      assertThat(objectOnlyFilter.accept(variableRecord("[1,2,3]"))).isTrue();
    }
  }
}
