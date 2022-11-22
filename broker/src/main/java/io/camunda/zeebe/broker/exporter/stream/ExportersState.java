/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;

public final class ExportersState {

  public static final long VALUE_NOT_FOUND = -1;

  private final DbString exporterId;
  private final ExporterStateEntry exporterStateEntry = new ExporterStateEntry();
  private final ColumnFamily<DbString, ExporterStateEntry> exporterPositionColumnFamily;

  public ExportersState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    exporterId = new DbString();
    exporterPositionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EXPORTER, transactionContext, exporterId, exporterStateEntry);
  }

  public void setPosition(final String exporterId, final long position) {
    this.exporterId.wrapString(exporterId);
    setPosition(position);
  }

  public long getPosition(final String exporterId) {
    this.exporterId.wrapString(exporterId);
    return getPosition();
  }

  public long getPosition(final DirectBuffer exporterId) {
    this.exporterId.wrapBuffer(exporterId);
    return getPosition();
  }

  private long getPosition() {
    final ExporterStateEntry pos = exporterPositionColumnFamily.get(exporterId);
    return pos == null ? VALUE_NOT_FOUND : pos.getPosition();
  }

  private void setPosition(final long position) {
    exporterStateEntry.setPosition(position);
    exporterPositionColumnFamily.upsert(exporterId, exporterStateEntry);
  }

  public void visitExporterState(final BiConsumer<String, ExporterStateEntry> consumer) {
    exporterPositionColumnFamily.forEach(
        (exporterId, exporterStateEntry) ->
            consumer.accept(exporterId.toString(), exporterStateEntry));
  }

  public long getLowestPosition() {
    final LongArrayList positions = new LongArrayList();

    visitExporterState(
        (exporterId, exporterStateEntry) -> positions.addLong(exporterStateEntry.getPosition()));
    return positions.longStream().min().orElse(-1L);
  }

  public void removeExporterState(final String exporterId) {
    this.exporterId.wrapString(exporterId);
    exporterPositionColumnFamily.deleteIfExists(this.exporterId);
  }

  public boolean hasExporters() {
    return !exporterPositionColumnFamily.isEmpty();
  }
}
