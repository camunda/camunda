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
import io.camunda.zeebe.protocol.record.RecordType;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RecordTypeFilterTest {

  @Test
  void shouldThrowNpeIfPredicateIsNull() {
    // when/then
    assertThatThrownBy(() -> new RecordTypeFilter(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("predicate must not be null");
  }

  @ParameterizedTest
  @EnumSource(RecordType.class)
  void shouldAcceptWhenPredicateIsTrue(final RecordType recordType) {
    // given
    final Predicate<RecordType> predicate = (t) -> true;
    final var filter = new RecordTypeFilter(predicate);
    final var record = mock(Record.class);
    when(record.getRecordType()).thenReturn(recordType);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @ParameterizedTest
  @EnumSource(RecordType.class)
  void shouldRejectWhenPredicateIsFalse(final RecordType recordType) {
    // given
    final Predicate<RecordType> predicate = (t) -> false;
    final var filter = new RecordTypeFilter(predicate);
    final var record = mock(Record.class);
    when(record.getRecordType()).thenReturn(recordType);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isFalse();
  }
}
