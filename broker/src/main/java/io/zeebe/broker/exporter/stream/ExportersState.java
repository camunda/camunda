/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.stream;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;

public class ExportersState {
  public static final long VALUE_NOT_FOUND = -1;

  private final DbString exporterId;
  private final DbLong position;
  private final ColumnFamily<DbString, DbLong> exporterPositionColumnFamily;

  public ExportersState(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    exporterId = new DbString();
    position = new DbLong();
    exporterPositionColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.EXPORTER, dbContext, exporterId, position);
  }

  public void setPosition(final String exporterId, final long position) {
    this.exporterId.wrapString(exporterId);
    setPosition(position);
  }

  public void setPositionIfGreater(final String exporterId, final long position) {
    this.exporterId.wrapString(exporterId);

    setPositionIfGreater(position);
  }

  private void setPositionIfGreater(long position) {
    // not that performant then rocksdb merge but
    // was currently simpler and easier to implement
    // if necessary change it again to merge

    final long oldPosition = getPosition();
    if (oldPosition < position) {
      setPosition(position);
    }
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
    final DbLong zbLong = exporterPositionColumnFamily.get(exporterId);
    return zbLong == null ? VALUE_NOT_FOUND : zbLong.getValue();
  }

  private void setPosition(long position) {
    this.position.wrapLong(position);
    exporterPositionColumnFamily.put(this.exporterId, this.position);
  }

  public void visitPositions(BiConsumer<String, Long> consumer) {
    exporterPositionColumnFamily.forEach(
        (exporterId, position) -> consumer.accept(exporterId.toString(), position.getValue()));
  }

  public long getLowestPosition() {
    final LongArrayList positions = new LongArrayList();

    visitPositions((id, pos) -> positions.addLong(pos));
    return positions.longStream().min().orElse(-1L);
  }

  public void removePosition(String exporter) {
    exporterId.wrapString(exporter);
    exporterPositionColumnFamily.delete(exporterId);
  }

  public boolean hasExporters() {
    return !exporterPositionColumnFamily.isEmpty();
  }
}
