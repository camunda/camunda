/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies a chain of {@link ExporterRecordFilter}s to determine whether a record should be
 * exported.
 *
 * <p>Filters are evaluated in order and combined with logical AND: the first filter that returns
 * {@code false} causes the whole chain to reject the record.
 *
 * <p>Filters that also implement {@link RecordVersionFilter} are only applied when the broker
 * version of the record is greater than or equal to their {@link
 * RecordVersionFilter#minRecordVersion()}. For older versions, those filters are skipped and the
 * record is accepted immediately (matching the previous behavior).
 */
public final class ExportRecordFilter {

  private final List<ExporterRecordFilter> recordFilters;

  public ExportRecordFilter(final List<ExporterRecordFilter> recordFilters) {
    this.recordFilters =
        List.copyOf(Objects.requireNonNull(recordFilters, "recordFilters must not be null"));
  }

  /** Returns {@code true} if the record passes all configured filters. */
  public boolean acceptRecord(final Record<?> record) {
    for (final ExporterRecordFilter filter : recordFilters) {
      if (filter instanceof final RecordVersionFilter versionFilter) {
        final String minVersion = versionFilter.minRecordVersion();
        if (!shouldApplyVersionedFilter(record, minVersion)) {
          // For records from older brokers, skip this filter
          continue;
        }
      }

      if (!filter.accept(record)) {
        // First rejecting filter short-circuits the chain.
        return false;
      }
    }

    // No filter rejected the record.
    return true;
  }

  private boolean shouldApplyVersionedFilter(final Record<?> record, final String minVersion) {
    final Optional<SemanticVersion> brokerVersion =
        SemanticVersion.parse(record.getBrokerVersion());
    if (brokerVersion.isEmpty()) {
      // Unparseable broker version -> conservatively apply the filter
      return true;
    }

    final SemanticVersion minVersionParsed = SemanticVersion.parse(minVersion).orElseThrow();
    return brokerVersion.get().compareTo(minVersionParsed) >= 0;
  }
}
