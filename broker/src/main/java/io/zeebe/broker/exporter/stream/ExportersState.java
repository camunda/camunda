/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.exporter.stream;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;

public final class ExportersState {

  public static final long VALUE_NOT_FOUND = -1;

  private final DbString exporterId;
  private final ExporterPosition position = new ExporterPosition();
  private final ColumnFamily<DbString, ExporterPosition> exporterPositionColumnFamily;

  public ExportersState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    exporterId = new DbString();
    exporterPositionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EXPORTER, transactionContext, exporterId, position);
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
    final ExporterPosition pos = exporterPositionColumnFamily.get(exporterId);
    return pos == null ? VALUE_NOT_FOUND : pos.get();
  }

  private void setPosition(final long position) {
    this.position.set(position);
    exporterPositionColumnFamily.put(exporterId, this.position);
  }

  public void visitPositions(final BiConsumer<String, Long> consumer) {
    exporterPositionColumnFamily.forEach(
        (exporterId, position) -> consumer.accept(exporterId.toString(), position.get()));
  }

  public long getLowestPosition() {
    final LongArrayList positions = new LongArrayList();

    visitPositions((id, pos) -> positions.addLong(pos));
    return positions.longStream().min().orElse(-1L);
  }

  public void removePosition(final String exporter) {
    exporterId.wrapString(exporter);
    exporterPositionColumnFamily.delete(exporterId);
  }

  public boolean hasExporters() {
    return !exporterPositionColumnFamily.isEmpty();
  }
}
