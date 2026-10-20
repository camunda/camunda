/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes the records of a journal entry: its application entry's data is the log stream's batch of
 * logged events, each with its metadata and value. The records keep views of the entry's bytes,
 * which must not change.
 */
public final class RecordDecoder {

  private RecordDecoder() {}

  /**
   * The entry's records, in position order, or none if it holds none. Records of value types that
   * this version of Zeebe's protocol doesn't know are left out.
   *
   * @param entry the journal entry
   * @param partition the partition the entry belongs to, which the records name
   * @throws InvalidRecordBatchException if the data isn't a batch of logged events
   */
  public static List<Record<?>> decode(final Entry entry, final int partition) {
    if (!entry.hasRecords()) {
      return List.of();
    }
    final var buffer = new UnsafeBuffer(entry.applicationData());
    final var records = new ArrayList<Record<?>>();
    final var event = new LoggedEventImpl();
    int offset = 0;
    while (offset < buffer.capacity()) {
      event.wrap(buffer, offset);
      final int length = event.getLength();
      if (length <= 0 || length > buffer.capacity() - offset) {
        throw new InvalidRecordBatchException(entry.index(), offset, length, buffer.capacity());
      }
      offset += length;
      final var metadata = new RecordMetadata();
      event.readMetadata(metadata);
      final var value = UnifiedRecordValue.fromValueType(metadata.getValueType());
      if (value == null) {
        continue;
      }
      event.readValue(value);
      records.add(
          new CopiedRecord<>(
              value,
              metadata,
              event.getKey(),
              partition,
              event.getPosition(),
              event.getSourceEventPosition(),
              event.getTimestamp()));
    }
    return records;
  }
}
