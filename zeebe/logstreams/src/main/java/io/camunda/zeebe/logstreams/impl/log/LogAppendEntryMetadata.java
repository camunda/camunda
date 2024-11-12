/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.ArrayList;
import java.util.List;

public record LogAppendEntryMetadata(RecordType recordType, ValueType valueType, Intent intent) {
  LogAppendEntryMetadata(final LogAppendEntry entry) {
    this(
        entry.recordMetadata().getRecordType(),
        entry.recordMetadata().getValueType(),
        entry.recordMetadata().getIntent());
  }

  public static List<LogAppendEntryMetadata> copyMetadata(final List<LogAppendEntry> entries) {
    final var metricsMetadata = new ArrayList<LogAppendEntryMetadata>(entries.size());
    for (final LogAppendEntry entry : entries) {
      metricsMetadata.add(new LogAppendEntryMetadata(entry));
    }

    return metricsMetadata;
  }
}
