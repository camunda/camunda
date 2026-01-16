/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.common.filter.VariableTypeFilter.VariableValueType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VariableTypeFilterTest {

  @Test
  void shouldAcceptNonVariableRecords() {
    // given
    final var filter = new VariableTypeFilter(Set.of(), Set.of());

    @SuppressWarnings("unchecked")
    final Record<ProcessInstanceRecordValue> record = mock(Record.class);
    final ProcessInstanceRecordValue value = mock(ProcessInstanceRecordValue.class);
    when(record.getValue()).thenReturn(value);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldInferBooleanType() {
    // given
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.BOOLEAN), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getValue()).thenReturn("true"); // JSON boolean

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldInferDoubleTypeForNumbers() {
    // given
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.DOUBLE), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getValue()).thenReturn("42"); // JSON number

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldInferStringTypeForJsonString() {
    // given
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.STRING), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getValue()).thenReturn("\"hello\""); // JSON string

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldInferStringTypeForNonJson() {
    // given
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.STRING), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getValue()).thenReturn("not-json"); // invalid JSON → fallback STRING

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldInferObjectTypeForJsonObject() {
    // given
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.OBJECT), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getValue()).thenReturn("{\"foo\":1}");

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldInferObjectTypeForJsonArray() {
    // given
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.OBJECT), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getValue()).thenReturn("[1,2,3]");

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldInferNullTypeWhenRawValueIsNull() {
    // given
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.NULL), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    final VariableRecordValue value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getValue()).thenReturn(null);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRespectInclusionOnly() {
    // given: include only STRING
    final var filter = new VariableTypeFilter(Set.of(VariableValueType.STRING), Set.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> stringRecord = mock(Record.class);
    final VariableRecordValue stringValue = mock(VariableRecordValue.class);
    when(stringRecord.getValue()).thenReturn(stringValue);
    when(stringValue.getValue()).thenReturn("\"str\"");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> booleanRecord = mock(Record.class);
    final VariableRecordValue booleanValue = mock(VariableRecordValue.class);
    when(booleanRecord.getValue()).thenReturn(booleanValue);
    when(booleanValue.getValue()).thenReturn("true");

    // when
    final boolean stringAccepted = filter.accept(stringRecord);
    final boolean booleanAccepted = filter.accept(booleanRecord);

    // then
    assertThat(stringAccepted).isTrue();
    assertThat(booleanAccepted).isFalse();
  }

  @Test
  void shouldRespectExclusionOnly() {
    // given: exclude BOOLEAN
    final var filter = new VariableTypeFilter(Set.of(), Set.of(VariableValueType.BOOLEAN));

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> booleanRecord = mock(Record.class);
    final VariableRecordValue booleanValue = mock(VariableRecordValue.class);
    when(booleanRecord.getValue()).thenReturn(booleanValue);
    when(booleanValue.getValue()).thenReturn("true");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> stringRecord = mock(Record.class);
    final VariableRecordValue stringValue = mock(VariableRecordValue.class);
    when(stringRecord.getValue()).thenReturn(stringValue);
    when(stringValue.getValue()).thenReturn("\"str\"");

    // when
    final boolean booleanAccepted = filter.accept(booleanRecord);
    final boolean stringAccepted = filter.accept(stringRecord);

    // then
    assertThat(booleanAccepted).isFalse();
    assertThat(stringAccepted).isTrue();
  }

  @Test
  void shouldRespectInclusionAndExclusionTogether() {
    // given: include DOUBLE and BOOLEAN, but exclude BOOLEAN
    final var filter =
        new VariableTypeFilter(
            Set.of(VariableValueType.DOUBLE, VariableValueType.BOOLEAN),
            Set.of(VariableValueType.BOOLEAN));

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> doubleRecord = mock(Record.class);
    final VariableRecordValue doubleValue = mock(VariableRecordValue.class);
    when(doubleRecord.getValue()).thenReturn(doubleValue);
    when(doubleValue.getValue()).thenReturn("1.5");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> booleanRecord = mock(Record.class);
    final VariableRecordValue booleanValue = mock(VariableRecordValue.class);
    when(booleanRecord.getValue()).thenReturn(booleanValue);
    when(booleanValue.getValue()).thenReturn("true");

    // when
    final boolean doubleAccepted = filter.accept(doubleRecord);
    final boolean booleanAccepted = filter.accept(booleanRecord);

    // then
    assertThat(doubleAccepted).isTrue(); // included and not excluded
    assertThat(booleanAccepted).isFalse(); // included, but explicitly excluded
  }

  @Test
  void parseTypesShouldHandleNullAndEmpty() {
    assertThat(VariableTypeFilter.parseTypes(null)).isEmpty();
    assertThat(VariableTypeFilter.parseTypes(List.of())).isEmpty();
  }

  @Test
  void parseTypesShouldBeCaseInsensitiveAndIgnoreUnknown() {
    // given
    final var raw = List.of("string", "BOOLEAN", "double", "unknown", "  object  ");

    // when
    final var types = VariableTypeFilter.parseTypes(raw);

    // then
    assertThat(types)
        .containsExactlyInAnyOrder(
            VariableValueType.STRING,
            VariableValueType.BOOLEAN,
            VariableValueType.DOUBLE,
            VariableValueType.OBJECT);
  }

  @Test
  void shouldExposeMinRecordVersion() {
    final var filter = new VariableTypeFilter(Set.of(), Set.of());
    assertThat(filter.minRecordVersion()).isEqualTo("8.9.0");
  }
}
