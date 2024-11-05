/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.inmemory;

import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.stream.impl.TypedEventRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RecordStreamView {

  private final LogStreamReader logStreamReader;
  private final int partitionId;
  private final List<Record<?>> records = new ArrayList<>();
  private volatile long lastPosition = -1L;

  public RecordStreamView(final LogStreamReader logStreamReader, final int partitionId) {
    this.logStreamReader = logStreamReader;
    this.partitionId = partitionId;
  }

  public Collection<Record<?>> getRecords() {
    updateWithNewRecords();
    return Collections.unmodifiableList(records);
  }

  private void updateWithNewRecords() {
    synchronized (logStreamReader) {
      if (lastPosition < 0) {
        logStreamReader.seekToFirstEvent();
      } else {
        logStreamReader.seekToNextEvent(lastPosition);
      }

      while (logStreamReader.hasNext()) {
        final LoggedEvent event = logStreamReader.next();
        final CopiedRecord<UnifiedRecordValue> record = mapToRecord(event);
        records.add(record);
        lastPosition = event.getPosition();
      }
    }
  }

  private CopiedRecord<UnifiedRecordValue> mapToRecord(final LoggedEvent event) {
    final RecordMetadata metadata = new RecordMetadata();
    event.readMetadata(metadata);

    final UnifiedRecordValue value;
    try {
      value =
          TypedEventRegistry.EVENT_REGISTRY
              .get(metadata.getValueType())
              .getDeclaredConstructor()
              .newInstance();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    event.readValue(value);

    return new CopiedRecord<>(
        value,
        metadata,
        event.getKey(),
        partitionId,
        event.getPosition(),
        event.getSourceEventPosition(),
        event.getTimestamp());
  }
}
