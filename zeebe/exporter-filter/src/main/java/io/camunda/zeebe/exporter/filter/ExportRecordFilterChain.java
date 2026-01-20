/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.Record;
import java.util.List;
import java.util.Objects;

/**
 * Applies a chain of {@link ExporterRecordFilter}s to determine whether a record should be
 * exported.
 *
 * <p>Filters are evaluated in order and combined with logical AND: the first filter that returns
 * {@code false} causes the whole chain to reject the record.
 */
public final class ExportRecordFilterChain {

  private final List<ExporterRecordFilter> recordFilters;

  public ExportRecordFilterChain(final List<ExporterRecordFilter> recordFilters) {
    this.recordFilters =
        List.copyOf(Objects.requireNonNull(recordFilters, "recordFilters must not be null"));
  }

  /** Returns {@code true} if the record passes all configured filters. */
  public boolean acceptRecord(final Record<?> record) {
    for (final ExporterRecordFilter filter : recordFilters) {
      if (!filter.accept(record)) {
        // First rejecting filter short-circuits the chain.
        return false;
      }
    }

    // No filter rejected the record.
    return true;
  }
}
