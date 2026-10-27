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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExportRecordFilterChainTest {

  public static final SemanticVersion VERSION_8_8_0 = SemanticVersion.parse("8.8.0").orElseThrow();
  public static final SemanticVersion VERSION_8_9_0 = SemanticVersion.parse("8.9.0").orElseThrow();
  public static final SemanticVersion VERSION_8_10_0 =
      SemanticVersion.parse("8.10.0").orElseThrow();

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

  @Test
  void shouldAcceptRecordWhenNoRecordFiltersConfigured() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final var chain = new ExportRecordFilterChain(List.of() /* no filters */);

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldApplyNonVersionedFilter() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final ExporterRecordFilter nonVersionedFilter =
        r -> {
          calls.incrementAndGet();
          return false; // actively reject
        };

    final var chain = new ExportRecordFilterChain(List.of(nonVersionedFilter));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void shouldSkipVersionedFilterForOlderBrokerVersion() {
    // given
    // Broker version lower than 8.9.0
    when(record.getBrokerVersion()).thenReturn("8.8.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter(VERSION_8_9_0, /* acceptResult= */ false, calls);

    final var chain = new ExportRecordFilterChain(List.of(versionedFilter));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    // For older versions, versioned filter should be skipped; record should be accepted.
    assertThat(accepted).isTrue();
    assertThat(calls.get())
        .as("Versioned filter should not be called for records below min version")
        .isZero();
  }

  @Test
  void shouldApplyVersionedFilterForNewerOrEqualBrokerVersion() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.9.0"); // equal to min version
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter(VERSION_8_9_0, /* acceptResult= */ false, calls);

    final var chain = new ExportRecordFilterChain(List.of(versionedFilter));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
    assertThat(calls.get()).as("Versioned filter must be applied for >= min version").isEqualTo(1);
  }

  @Test
  void shouldNotApplyVersionedFilterWhenBrokerVersionUnparseable() {
    // given
    when(record.getBrokerVersion()).thenReturn("not-a-semver");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter(VERSION_8_9_0, /* acceptResult= */ false, calls);

    final var chain = new ExportRecordFilterChain(List.of(versionedFilter));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    // Unparseable broker version -> conservatively NOT apply the filter
    assertThat(accepted).isTrue();
    assertThat(calls.get()).isEqualTo(0);
  }

  @Test
  void shouldShortCircuitOnFirstRejectingFilter() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger firstCalls = new AtomicInteger(0);
    final AtomicInteger secondCalls = new AtomicInteger(0);

    final ExporterRecordFilter firstFilter =
        r -> {
          firstCalls.incrementAndGet();
          return false;
        };
    final ExporterRecordFilter secondFilter =
        r -> {
          secondCalls.incrementAndGet();
          return true;
        };

    final var chain = new ExportRecordFilterChain(List.of(firstFilter, secondFilter));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
    assertThat(firstCalls.get()).isEqualTo(1);
    assertThat(secondCalls.get())
        .as("Subsequent filters should not be evaluated after first reject")
        .isZero();
  }

  @Test
  void shouldSkipVersionedFilterButApplyNonVersionedFilterForOlderBrokerVersion() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.8.0"); // < 8.9.0
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger versionedCalls = new AtomicInteger(0);
    final AtomicInteger nonVersionedCalls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter(VERSION_8_9_0, /* acceptResult= */ true, versionedCalls);

    final ExporterRecordFilter nonVersionedFilter =
        r -> {
          nonVersionedCalls.incrementAndGet();
          return false; // actively reject
        };

    final var chain = new ExportRecordFilterChain(List.of(versionedFilter, nonVersionedFilter));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    // Versioned filter skipped due to older broker version; non-versioned filter still applied
    assertThat(accepted).isFalse();
    assertThat(versionedCalls.get())
        .as("Versioned filter should not be called for records below min version")
        .isZero();
    assertThat(nonVersionedCalls.get())
        .as("Non-versioned filter should still be evaluated")
        .isEqualTo(1);
  }

  @Test
  void shouldApplyOnlyQualifiedVersionedFiltersInChain() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.8.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger firstCalls = new AtomicInteger(0);
    final AtomicInteger secondCalls = new AtomicInteger(0);

    final TestVersionedFilter firstVersioned =
        new TestVersionedFilter(VERSION_8_9_0, /* acceptResult= */ true, firstCalls);
    final TestVersionedFilter secondVersioned =
        new TestVersionedFilter(VERSION_8_8_0, /* acceptResult= */ false, secondCalls);

    final var chain = new ExportRecordFilterChain(List.of(firstVersioned, secondVersioned));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    // First versioned filter skipped (min=8.9.0, record=8.8.0)
    assertThat(firstCalls.get()).isZero();
    // Second versioned filter applied (min=8.8.0, record=8.8.0) and rejects
    assertThat(secondCalls.get()).isEqualTo(1);
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldApplyAllQualifiedVersionedFilters() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger firstCalls = new AtomicInteger(0);
    final AtomicInteger secondCalls = new AtomicInteger(0);

    final TestVersionedFilter firstVersioned =
        new TestVersionedFilter(VERSION_8_9_0, /* acceptResult= */ true, firstCalls);
    final TestVersionedFilter secondVersioned =
        new TestVersionedFilter(VERSION_8_10_0, /* acceptResult= */ false, secondCalls);

    final var chain = new ExportRecordFilterChain(List.of(firstVersioned, secondVersioned));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(firstCalls.get())
        .as("First versioned filter should be applied for >= 8.9.0")
        .isEqualTo(1);
    assertThat(secondCalls.get())
        .as("Second versioned filter should be applied for >= 8.10.0")
        .isEqualTo(1);
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldAcceptWhenAllVersionedFiltersAreSkippedAndNonVersionedFilterAccepts() {
    // given
    when(record.getBrokerVersion()).thenReturn("8.7.0"); // < 8.8.0 and < 8.9.0
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger v1Calls = new AtomicInteger(0);
    final AtomicInteger v2Calls = new AtomicInteger(0);
    final AtomicInteger nonVersionedCalls = new AtomicInteger(0);

    final TestVersionedFilter v1 =
        new TestVersionedFilter(VERSION_8_8_0, /* acceptResult= */ false, v1Calls);
    final TestVersionedFilter v2 =
        new TestVersionedFilter(VERSION_8_9_0, /* acceptResult= */ false, v2Calls);

    final ExporterRecordFilter nonVersionedFilter =
        r -> {
          nonVersionedCalls.incrementAndGet();
          return true; // accept
        };

    final var chain = new ExportRecordFilterChain(List.of(v1, v2, nonVersionedFilter));

    // when
    final boolean accepted = chain.acceptRecord(record);

    // then
    assertThat(v1Calls.get()).isZero();
    assertThat(v2Calls.get()).isZero();
    assertThat(nonVersionedCalls.get()).isEqualTo(1);
    assertThat(accepted).isTrue();
  }

  /** Simple test implementation of a versioned filter. */
  private static final class TestVersionedFilter
      implements ExporterRecordFilter, RecordVersionFilter {

    private final SemanticVersion minVersion;
    private final boolean acceptResult;
    private final AtomicInteger calls;

    private TestVersionedFilter(
        final SemanticVersion minVersion, final boolean acceptResult, final AtomicInteger calls) {
      this.minVersion = minVersion;
      this.acceptResult = acceptResult;
      this.calls = calls;
    }

    @Override
    public boolean accept(final Record<?> record) {
      calls.incrementAndGet();
      return acceptResult;
    }

    @Override
    public SemanticVersion minRecordBrokerVersion() {
      return minVersion;
    }
  }
}
