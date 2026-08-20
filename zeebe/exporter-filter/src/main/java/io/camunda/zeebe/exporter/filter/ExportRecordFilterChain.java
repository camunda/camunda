/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a chain of {@link ExporterRecordFilter}s to determine whether a record should be
 * exported.
 *
 * <p>Filters are evaluated in order and combined with logical AND: the first filter that returns
 * {@code false} causes the whole chain to reject the record.
 */
public final class ExportRecordFilterChain {

  private static final Logger LOG = LoggerFactory.getLogger(ExportRecordFilterChain.class);

  private final List<ExporterRecordFilter> recordFilters;

  public ExportRecordFilterChain(final List<ExporterRecordFilter> recordFilters) {
    this.recordFilters =
        List.copyOf(Objects.requireNonNull(recordFilters, "recordFilters must not be null"));
  }

  /** Returns {@code true} if the record passes all configured filters. */
  public boolean acceptRecord(final Record<?> record) {
    return recordFilters.stream()
        .allMatch(
            filter -> {
              if (filter instanceof final RecordVersionFilter versionFilter) {
                if (!shouldApplyVersionedFilter(record, versionFilter)) {
                  // For records from older brokers, skip this filter => treat as "passed"
                  return true;
                }
              }

              // Apply the filter normally
              return filter.accept(record);
            });
  }

  private boolean shouldApplyVersionedFilter(
      final Record<?> record, final RecordVersionFilter versionFilter) {

    final SemanticVersion minVersion = versionFilter.minRecordBrokerVersion();
    final String rawBrokerVersion = record.getBrokerVersion();
    final Optional<SemanticVersion> brokerVersion = SemanticVersion.parse(rawBrokerVersion);

    if (brokerVersion.isEmpty()) {
      // Unparseable broker version -> conservatively NOT apply the filter
      if (LOG.isDebugEnabled()) {
        LOG.debug(
            "Unable to parse broker version '{}' for record {} when applying filter {} "
                + "(minRecordBrokerVersion={}). Not applying the filter conservatively.",
            rawBrokerVersion,
            summarizeRecord(record),
            versionFilter.getClass().getSimpleName(),
            minVersion);
      }
      return false;
    }

    return brokerVersion.get().compareTo(minVersion) >= 0;
  }

  private String summarizeRecord(final Record<?> record) {
    return "type="
        + record.getValueType()
        + ", intent="
        + record.getIntent()
        + ", key="
        + record.getKey()
        + ", partitionId="
        + record.getPartitionId()
        + ", position="
        + record.getPosition();
  }
}
