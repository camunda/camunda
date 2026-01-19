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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.zeebe.protocol.record.Record;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportRecordFilterChainTest {

  private final Record<?> record = mock(Record.class);

  @Test
  void shouldThrowNpeIfFilterListIsNull() {
    // when/then
    assertThatThrownBy(() -> new ExportRecordFilterChain(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("recordFilters must not be null");
  }

  @Test
  void shouldAcceptWhenNoFilters() {
    // given
    final var chain = new ExportRecordFilterChain(Collections.emptyList());

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldAcceptWhenAllFiltersAccept() {
    // given
    final var filter1 = mock(ExporterRecordFilter.class);
    final var filter2 = mock(ExporterRecordFilter.class);
    when(filter1.accept(record)).thenReturn(true);
    when(filter2.accept(record)).thenReturn(true);
    final var chain = new ExportRecordFilterChain(List.of(filter1, filter2));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
    verify(filter1).accept(record);
    verify(filter2).accept(record);
  }

  @Test
  void shouldRejectWhenFirstFilterRejects() {
    // given
    final var filter1 = mock(ExporterRecordFilter.class);
    final var filter2 = mock(ExporterRecordFilter.class);
    when(filter1.accept(record)).thenReturn(false);
    final var chain = new ExportRecordFilterChain(List.of(filter1, filter2));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
    verify(filter1).accept(record);
    verify(filter2, never()).accept(any());
  }

  @Test
  void shouldRejectWhenSecondFilterRejects() {
    // given
    final var filter1 = mock(ExporterRecordFilter.class);
    final var filter2 = mock(ExporterRecordFilter.class);
    when(filter1.accept(record)).thenReturn(true);
    when(filter2.accept(record)).thenReturn(false);
    final var chain = new ExportRecordFilterChain(List.of(filter1, filter2));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
    verify(filter1).accept(record);
    verify(filter2).accept(record);
  }
}
