/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.record;

import io.camunda.exporter.kafka.config.RecordsConfiguration;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class KafkaRecordFilter implements RecordFilter {

  private final Set<RecordType> allowedTypes;
  private final RecordsConfiguration recordsConfiguration;

  public KafkaRecordFilter(final RecordsConfiguration recordsConfiguration) {
    this.recordsConfiguration = recordsConfiguration;
    // Pre-compute the union of all allowed record types across default and per-type configurations
    // so that acceptType() — called on every record before it reaches the exporter — is an O(1)
    // set lookup rather than a per-call stream scan.
    final EnumSet<RecordType> types = EnumSet.noneOf(RecordType.class);
    types.addAll(recordsConfiguration.defaults().allowedTypes());
    recordsConfiguration.byType().values().forEach(c -> types.addAll(c.allowedTypes()));
    this.allowedTypes = Collections.unmodifiableSet(types);
  }

  @Override
  public boolean acceptType(final RecordType recordType) {
    return allowedTypes.contains(recordType);
  }

  @Override
  public boolean acceptValue(final ValueType valueType) {
    return !recordsConfiguration.forType(valueType).allowedTypes().isEmpty();
  }
}
