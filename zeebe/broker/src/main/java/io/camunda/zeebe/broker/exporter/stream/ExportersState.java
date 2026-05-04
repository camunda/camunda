/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExportersState {

  public static final long VALUE_NOT_FOUND = -1;

  private static final UnsafeBuffer METADATA_NOT_FOUND = new UnsafeBuffer();

  private final TransactionContext transactionContext;
  private final DbString exporterId;
  private final ColumnFamily<DbString, ExporterStateEntry> exporterPositionColumnFamily;

  public ExportersState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this.transactionContext = transactionContext;
    exporterId = new DbString();
    exporterPositionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EXPORTER, transactionContext, exporterId, new ExporterStateEntry());
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
          exporterPositionColumnFamily.upsert(this.exporterId, exporterStateEntry);
        });
  }

  public void initializeExporterState(
      final String exporterId,
      final long position,
      final DirectBuffer metadata,
      final long metadataVersion) {
    this.exporterId.wrapString(exporterId);
    final var exporterStateEntry = new ExporterStateEntry();
    exporterStateEntry.setPosition(position).setMetadataVersion(metadataVersion);
    if (metadata != null) {
      exporterStateEntry.setMetadata(metadata);
    }
    exporterPositionColumnFamily.upsert(this.exporterId, exporterStateEntry);
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
    this.exporterId.wrapString(exporterId);
    return Optional.ofNullable(exporterPositionColumnFamily.get(this.exporterId));
  }

  public void visitExporterState(final BiConsumer<String, ExporterStateEntry> consumer) {
    exporterPositionColumnFamily.forEach(
        (exporterId, exporterStateEntry) ->
            consumer.accept(exporterId.toString(), exporterStateEntry));
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
   * the result of a historical bug where {@code undoSoftPauseExporter} flushed the long default
   * value of {@code lastAcknowledgedPosition}. Treat such entries as uninitialized so seek and
   * compaction behave like for a fresh exporter and the first real acknowledgment overwrites the
   * stale entry.
   */
  private static long normalizePosition(final long position) {
    return position == 0 ? VALUE_NOT_FOUND : position;
  }

  public void removeExporterState(final String exporterId) {
    this.exporterId.wrapString(exporterId);
    exporterPositionColumnFamily.deleteIfExists(this.exporterId);
  }

  public boolean hasExporters() {
    return !exporterPositionColumnFamily.isEmpty();
  }
}
