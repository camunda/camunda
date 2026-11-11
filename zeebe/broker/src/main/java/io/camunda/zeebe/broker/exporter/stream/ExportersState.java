/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExportersState {

  public static final long VALUE_NOT_FOUND = -1;

  private static final UnsafeBuffer METADATA_NOT_FOUND = new UnsafeBuffer();

  private final TransactionContext transactionContext;
  private final ExporterStateStore store;

  public ExportersState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this(new RocksDbExporterStateStore(zeebeDb, transactionContext), transactionContext);
  }

  /**
   * @param store where the state is kept
   * @param transactionContext the context in which the state is read and changed
   */
  public ExportersState(
      final ExporterStateStore store, final TransactionContext transactionContext) {
    this.transactionContext = transactionContext;
    this.store = store;
  }

  public void setPosition(final String exporterId, final long position) {
    setExporterState(exporterId, position, null);
  }

  public void setExporterState(
      final String exporterId, final long position, final DirectBuffer metadata) {
    transactionContext.runInTransaction(
        () -> {
          final var exporterStateEntry =
              findExporterStateEntry(exporterId).orElseGet(ExporterStateEntry::new);
          exporterStateEntry.setPosition(position);
          if (metadata != null) {
            exporterStateEntry.setMetadata(metadata);
          }
          store.upsert(exporterId, exporterStateEntry);
        });
  }

  public void initializeExporterState(
      final String exporterId,
      final long position,
      final DirectBuffer metadata,
      final long metadataVersion) {
    final var exporterStateEntry = new ExporterStateEntry();
    exporterStateEntry.setPosition(position).setMetadataVersion(metadataVersion);
    if (metadata != null) {
      exporterStateEntry.setMetadata(metadata);
    }
    store.upsert(exporterId, exporterStateEntry);
  }

  public long getPosition(final String exporterId) {
    return findExporterStateEntry(exporterId)
        .map(ExporterStateEntry::getPosition)
        .map(ExportersState::normalizePosition)
        .orElse(VALUE_NOT_FOUND);
  }

  public DirectBuffer getExporterMetadata(final String exporterId) {
    return findExporterStateEntry(exporterId)
        .map(ExporterStateEntry::getMetadata)
        .orElse(METADATA_NOT_FOUND);
  }

  public long getMetadataVersion(final String exporterId) {
    return findExporterStateEntry(exporterId)
        .map(ExporterStateEntry::getMetadataVersion)
        .orElse(0L);
  }

  private Optional<ExporterStateEntry> findExporterStateEntry(final String exporterId) {
    return store.find(exporterId);
  }

  public void visitExporterState(final BiConsumer<String, ExporterStateEntry> consumer) {
    store.forEach(consumer);
  }

  public long getLowestPosition() {
    final LongArrayList positions = new LongArrayList();

    visitExporterState(
        (exporterId, exporterStateEntry) ->
            positions.addLong(normalizePosition(exporterStateEntry.getPosition())));
    return positions.longStream().min().orElse(VALUE_NOT_FOUND);
  }

  /**
   * Position 0 is not a valid log entry position; the first valid position is 1. A persisted 0 is
   * the result of a historical bug where resuming an exporter from a soft pause flushed the long
   * default value of {@code lastAcknowledgedPosition}. Treat such entries as uninitialized so seek
   * and compaction behave like for a fresh exporter and the first real acknowledgment overwrites
   * the stale entry.
   */
  private static long normalizePosition(final long position) {
    return position == 0 ? VALUE_NOT_FOUND : position;
  }

  /**
   * Return highest position of all exporters or {@link Long#MIN_VALUE} if no exporters are present.
   */
  public long getHighestPosition() {
    final var max = new MutableLong(Long.MIN_VALUE);
    visitExporterState(
        (exporterId, exporterStateEntry) -> {
          final var position = normalizePosition(exporterStateEntry.getPosition());
          if (max.longValue() < position) {
            max.set(position);
          }
        });
    return max.get();
  }

  public void removeExporterState(final String exporterId) {
    store.delete(exporterId);
  }

  /** Writes the changes that the store held back, see {@link ExporterStateStore#flush()}. */
  public void flush() {
    store.flush();
  }

  public boolean hasExporters() {
    return !store.isEmpty();
  }
}
