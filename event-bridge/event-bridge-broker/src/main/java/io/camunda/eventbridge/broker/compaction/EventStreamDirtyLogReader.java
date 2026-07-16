/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.messaging.stream.EventStreamReader;
import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeBatchIterator;
import io.camunda.eventbridge.protocol.EventBridgeEntry;
import java.util.function.Supplier;

/**
 * Production {@link DirtyLogReader} that decodes the raw journal through the journal's {@link
 * EventStreamReader}. It seeks to the record just after the previous cleaner point, walks batches,
 * and decodes each entry whose position falls in the requested range into a {@link
 * CompactionRecord}.
 *
 * <p>A fresh {@link EventStreamReader} is obtained per {@link #read} call from the injected
 * supplier and closed afterwards, so the cleaner never pins a reader across passes. Wiring the
 * supplier onto a live partition's log storage is partition-startup concern (step 5); this class is
 * the decode logic that sits on top of it.
 *
 * <p>Threading: driven by the single cleaner actor; each call is self-contained.
 */
public final class EventStreamDirtyLogReader implements DirtyLogReader {

  private final Supplier<EventStreamReader> readerSupplier;
  private final EventBridgeBatchIterator batchIterator = new EventBridgeBatchIterator();

  /**
   * @param readerSupplier supplies a freshly-opened {@link EventStreamReader} over the partition's
   *     log storage; the reader is closed by this class after each read
   */
  public EventStreamDirtyLogReader(final Supplier<EventStreamReader> readerSupplier) {
    this.readerSupplier = readerSupplier;
  }

  @Override
  public void read(
      final long fromExclusive, final long toInclusive, final DirtyRecordVisitor visitor) {
    if (toInclusive <= fromExclusive) {
      return;
    }
    try (final EventStreamReader reader = readerSupplier.get()) {
      // Seek to the first batch containing or following the first position we care about.
      reader.seek(fromExclusive + 1);
      while (reader.hasNext()) {
        if (reader.position() > toInclusive) {
          break;
        }
        if (!visitBatch(reader, fromExclusive, toInclusive, visitor)) {
          return;
        }
        reader.next();
      }
    }
  }

  private boolean visitBatch(
      final EventStreamReader reader,
      final long fromExclusive,
      final long toInclusive,
      final DirtyRecordVisitor visitor) {
    batchIterator.wrap(reader.batchBuffer(), reader.batchOffset(), reader.batchTotalSize());
    // Carry the source batch's full attributes int so the sweep preserves it verbatim on rewrap.
    final int attributes =
        EventBridgeBatch.getAttributes(reader.batchBuffer(), reader.batchOffset());
    while (batchIterator.hasNext()) {
      final EventBridgeEntry entry = batchIterator.next();
      final long position = entry.getPosition();
      if (position <= fromExclusive) {
        continue;
      }
      if (position > toInclusive) {
        return true; // remaining entries in this batch are also beyond the range
      }
      final var record =
          new CompactionRecord(
              position, entry.getTimestamp(), entry.getKeyCopy(), entry.getValueCopy(), attributes);
      if (!visitor.visit(record)) {
        return false;
      }
    }
    return true;
  }
}
