/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ExportRecordFilterTest {

  @Test
  void shouldAcceptRecordWhenNoRecordFiltersConfigured() {
    // given
    final var filter = new ExportRecordFilter(List.of() /* no filters */);

    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    // when
    final boolean accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldApplyNonVersionedFilter() {
    // given
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final ExporterRecordFilter nonVersionedFilter =
        r -> {
          calls.incrementAndGet();
          return false; // actively reject
        };

    final var filter = new ExportRecordFilter(List.of(nonVersionedFilter));

    // when
    final boolean accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void shouldSkipVersionedFilterForOlderBrokerVersion() {
    // given
    final Record<?> record = mock(Record.class);
    // Broker version lower than 8.9.0
    when(record.getBrokerVersion()).thenReturn("8.8.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter("8.9.0", /* acceptResult= */ false, calls);

    final var filter = new ExportRecordFilter(List.of(versionedFilter));

    // when
    final boolean accepted = filter.acceptRecord(record);

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
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0"); // equal to min version
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter("8.9.0", /* acceptResult= */ false, calls);

    final var filter = new ExportRecordFilter(List.of(versionedFilter));

    // when
    final boolean accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
    assertThat(calls.get()).as("Versioned filter must be applied for >= min version").isEqualTo(1);
  }

  @Test
  void shouldApplyVersionedFilterWhenBrokerVersionUnparseable() {
    // given
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("not-a-semver");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger calls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter("8.9.0", /* acceptResult= */ true, calls);

    final var filter = new ExportRecordFilter(List.of(versionedFilter));

    // when
    final boolean accepted = filter.acceptRecord(record);

    // then
    // Unparseable broker version -> conservatively apply the filter
    assertThat(accepted).isTrue();
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void shouldShortCircuitOnFirstRejectingFilter() {
    // given
    final Record<?> record = mock(Record.class);
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

    final var filter = new ExportRecordFilter(List.of(firstFilter, secondFilter));

    // when
    final boolean accepted = filter.acceptRecord(record);

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
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.8.0"); // < 8.9.0
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger versionedCalls = new AtomicInteger(0);
    final AtomicInteger nonVersionedCalls = new AtomicInteger(0);

    final TestVersionedFilter versionedFilter =
        new TestVersionedFilter("8.9.0", /* acceptResult= */ true, versionedCalls);

    final ExporterRecordFilter nonVersionedFilter =
        r -> {
          nonVersionedCalls.incrementAndGet();
          return false; // actively reject
        };

    final var filter = new ExportRecordFilter(List.of(versionedFilter, nonVersionedFilter));

    // when
    final boolean accepted = filter.acceptRecord(record);

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
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.8.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger firstCalls = new AtomicInteger(0);
    final AtomicInteger secondCalls = new AtomicInteger(0);

    final TestVersionedFilter firstVersioned =
        new TestVersionedFilter("8.9.0", /* acceptResult= */ true, firstCalls);
    final TestVersionedFilter secondVersioned =
        new TestVersionedFilter("8.8.0", /* acceptResult= */ false, secondCalls);

    final var filter = new ExportRecordFilter(List.of(firstVersioned, secondVersioned));

    // when
    final boolean accepted = filter.acceptRecord(record);

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
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger firstCalls = new AtomicInteger(0);
    final AtomicInteger secondCalls = new AtomicInteger(0);

    final TestVersionedFilter firstVersioned =
        new TestVersionedFilter("8.9.0", /* acceptResult= */ true, firstCalls);
    final TestVersionedFilter secondVersioned =
        new TestVersionedFilter("8.10.0", /* acceptResult= */ false, secondCalls);

    final var filter = new ExportRecordFilter(List.of(firstVersioned, secondVersioned));

    // when
    final boolean accepted = filter.acceptRecord(record);

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
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.7.0"); // < 8.8.0 and < 8.9.0
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);

    final AtomicInteger v1Calls = new AtomicInteger(0);
    final AtomicInteger v2Calls = new AtomicInteger(0);
    final AtomicInteger nonVersionedCalls = new AtomicInteger(0);

    final TestVersionedFilter v1 =
        new TestVersionedFilter("8.8.0", /* acceptResult= */ false, v1Calls);
    final TestVersionedFilter v2 =
        new TestVersionedFilter("8.9.0", /* acceptResult= */ false, v2Calls);

    final ExporterRecordFilter nonVersionedFilter =
        r -> {
          nonVersionedCalls.incrementAndGet();
          return true; // accept
        };

    final var filter = new ExportRecordFilter(List.of(v1, v2, nonVersionedFilter));

    // when
    final boolean accepted = filter.acceptRecord(record);

    // then
    assertThat(v1Calls.get()).isZero();
    assertThat(v2Calls.get()).isZero();
    assertThat(nonVersionedCalls.get()).isEqualTo(1);
    assertThat(accepted).isTrue();
  }

  /** Simple test implementation of a versioned filter. */
  private static final class TestVersionedFilter
      implements ExporterRecordFilter, RecordVersionFilter {

    private final String minVersion;
    private final boolean acceptResult;
    private final AtomicInteger calls;

    private TestVersionedFilter(
        final String minVersion, final boolean acceptResult, final AtomicInteger calls) {
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
    public String minRecordVersion() {
      return minVersion;
    }
  }
}
