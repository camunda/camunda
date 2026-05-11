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
import java.util.List;

public final class LogAppendEntryMetadata {
  private final short[] recordTypes;
  private final short[] valueTypes;
  private final short[] intents;

  private LogAppendEntryMetadata(
      final short[] recordTypes, final short[] valueTypes, final short[] intents) {
    this.recordTypes = recordTypes;
    this.valueTypes = valueTypes;
    this.intents = intents;
  }

  public int size() {
    return recordTypes.length;
  }

  public RecordType recordType(final int index) {
    return RecordType.get(recordTypes[index]);
  }

  public ValueType valueType(final int index) {
    return ValueType.get(valueTypes[index]);
  }

  public Intent intent(final int index) {
    return Intent.fromProtocolValue(ValueType.get(valueTypes[index]), intents[index]);
  }

  public static LogAppendEntryMetadata copyMetadata(final List<LogAppendEntry> entries) {
    final int size = entries.size();
    final var recordTypes = new short[size];
    final var valueTypes = new short[size];
    final var intents = new short[size];

    for (int i = 0; i < size; i++) {
      final var entry = entries.get(i);
      final var metadata = entry.recordMetadata();
      recordTypes[i] = metadata.getRecordType().value();
      valueTypes[i] = metadata.getValueType().value();
      intents[i] = metadata.getIntent().value();
    }

    return new LogAppendEntryMetadata(recordTypes, valueTypes, intents);
  }
}
