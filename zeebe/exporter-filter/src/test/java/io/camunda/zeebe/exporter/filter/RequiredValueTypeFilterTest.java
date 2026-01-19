/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RequiredValueTypeFilterTest {

  private final Record<?> record = mock(Record.class);

  @Test
  void shouldThrowNpeForNullIncludeEnabledRecords() {
    assertThatThrownBy(() -> new RequiredValueTypeFilter(null, (vt) -> true, (vt) -> true))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("includeEnabledRecords must not be null");
  }

  @Test
  void shouldThrowNpeForNullShouldIndexValueType() {
    assertThatThrownBy(() -> new RequiredValueTypeFilter(() -> true, null, (vt) -> true))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("shouldIndexValueType must not be null");
  }

  @Test
  void shouldThrowNpeForNullShouldIndexRequiredValueType() {
    assertThatThrownBy(() -> new RequiredValueTypeFilter(() -> true, (vt) -> true, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("shouldIndexRequiredValueType must not be null");
  }

  @Test
  void shouldThrowForInvalidVersion() {
    // given
    when(record.getBrokerVersion()).thenReturn("invalid");
    final var filter = new RequiredValueTypeFilter(() -> false, (vt) -> true, (vt) -> true);

    // when/then
    assertThatThrownBy(() -> filter.accept(record))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported record broker version: [invalid] Must be a semantic version.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"8.7.0", "8.7.15", "8.6.0"})
  void shouldUseShouldIndexValueTypeForOldBrokerVersions(final String oldVersion) {
    // given
    when(record.getBrokerVersion()).thenReturn(oldVersion);
    when(record.getValueType()).thenReturn(ValueType.JOB);

    final BooleanSupplier includeEnabledRecords = () -> false; // should be ignored
    final Predicate<ValueType> shouldIndexValueType = (vt) -> vt == ValueType.JOB;
    final Predicate<ValueType> shouldIndexRequiredValueType = (vt) -> false;

    final var filter =
        new RequiredValueTypeFilter(
            includeEnabledRecords, shouldIndexValueType, shouldIndexRequiredValueType);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldUseShouldIndexValueTypeWhenIncludeEnabledRecordsIsTrue() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.8.0");
    when(record.getValueType()).thenReturn(ValueType.JOB);

    final BooleanSupplier includeEnabledRecords = () -> true;
    final Predicate<ValueType> shouldIndexValueType = (vt) -> vt == ValueType.JOB;
    final Predicate<ValueType> shouldIndexRequiredValueType = (vt) -> false;

    final var filter =
        new RequiredValueTypeFilter(
            includeEnabledRecords, shouldIndexValueType, shouldIndexRequiredValueType);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"8.8.0,true", "8.9.0,true", "9.0.0,true", "8.8.0,false", "8.9.0,false"})
  void shouldUseShouldIndexRequiredValueType(
      final String brokerVersion, final boolean shouldIndex) {
    // given
    when(record.getBrokerVersion()).thenReturn(brokerVersion);
    when(record.getValueType()).thenReturn(ValueType.JOB);

    final BooleanSupplier includeEnabledRecords = () -> false;
    final Predicate<ValueType> shouldIndexValueType = (vt) -> false;
    final Predicate<ValueType> shouldIndexRequiredValueType = (vt) -> shouldIndex;

    final var filter =
        new RequiredValueTypeFilter(
            includeEnabledRecords, shouldIndexValueType, shouldIndexRequiredValueType);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isEqualTo(shouldIndex);
  }
}
