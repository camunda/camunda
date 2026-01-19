/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Record-level filter that applies the "required value types" logic based on broker version and
 * exporter configuration.
 *
 * <p>It mirrors:
 *
 * <pre>
 * if (configuration.getIsIncludeEnabledRecords()
 *     || (recordVersion.major() == 8 && recordVersion.minor() < 8)) {
 *   return configuration.shouldIndexValueType(record.getValueType());
 * }
 * return configuration.shouldIndexRequiredValueType(record.getValueType());
 * </pre>
 *
 * The configuration is provided via functional parameters so this class can be reused by multiple
 * exporters (e.g. Elasticsearch, Opensearch).
 */
public final class RequiredValueTypeFilter implements ExporterRecordFilter {

  private final BooleanSupplier includeEnabledRecords;
  private final Predicate<ValueType> shouldIndexValueType;
  private final Predicate<ValueType> shouldIndexRequiredValueType;

  public RequiredValueTypeFilter(
      final BooleanSupplier includeEnabledRecords,
      final Predicate<ValueType> shouldIndexValueType,
      final Predicate<ValueType> shouldIndexRequiredValueType) {

    this.includeEnabledRecords =
        Objects.requireNonNull(includeEnabledRecords, "includeEnabledRecords must not be null");
    this.shouldIndexValueType =
        Objects.requireNonNull(shouldIndexValueType, "shouldIndexValueType must not be null");
    this.shouldIndexRequiredValueType =
        Objects.requireNonNull(
            shouldIndexRequiredValueType, "shouldIndexRequiredValueType must not be null");
  }

  @Override
  public boolean accept(final Record<?> record) {
    final SemanticVersion recordVersion = parseVersion(record.getBrokerVersion());

    final boolean includeAllEnabledRecords =
        includeEnabledRecords.getAsBoolean()
            || (recordVersion.major() == 8 && recordVersion.minor() < 8);

    if (includeAllEnabledRecords) {
      // Old brokers or explicit include-all flag: use the normal value-type config
      return shouldIndexValueType.test(record.getValueType());
    }

    // 8.8+ without include-all flag: only index "required" value types
    return shouldIndexRequiredValueType.test(record.getValueType());
  }

  private static SemanticVersion parseVersion(final String brokerVersion) {
    return SemanticVersion.parse(brokerVersion)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported record broker version: ["
                        + brokerVersion
                        + "] Must be a semantic version."));
  }
}
