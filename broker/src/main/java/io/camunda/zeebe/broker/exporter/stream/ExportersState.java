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
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.Optional;
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
    final var exporter = getExporter(exporterId).orElseGet(ExporterPosition::new);
    exporter.setPosition(position);
    updateExporter(exporterId, exporter);
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
    return pos == null ? VALUE_NOT_FOUND : pos.getPosition();
  }

  public Optional<ExporterPosition> getExporter(final String exporterId) {
    this.exporterId.wrapString(exporterId);
    return Optional.ofNullable(exporterPositionColumnFamily.get(this.exporterId));
  }

  public void visitPositions(final BiConsumer<String, Long> consumer) {
    exporterPositionColumnFamily.forEach(
        (exporterId, position) -> consumer.accept(exporterId.toString(), position.getPosition()));
  }

  public void visitSequences(final String exporterId, final BiConsumer<ValueType, Long> consumer) {
    final var exporter = getExporter(exporterId);
    if (exporter.isPresent()) {
      final var valueTypes = ValueType.values();

      final var exclude = new ArrayList<ValueType>();
      exclude.add(ValueType.TIMER);
      exclude.add(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
      exclude.add(ValueType.PROCESS_INSTANCE_RESULT);
      exclude.add(ValueType.DEPLOYMENT_DISTRIBUTION);
      exclude.add(ValueType.PROCESS_EVENT);
      exclude.add(ValueType.SBE_UNKNOWN);
      exclude.add(ValueType.NULL_VAL);

      for (var i = 0; i < valueTypes.length; i++) {
        final var valueType = valueTypes[i];
        if (!exclude.contains(valueType)) {
          final var sequence = exporter.get().getSequence(valueType);
          consumer.accept(valueType, sequence);
        }
      }
    }
  }

  public void updateExporter(final String exporterId, final ExporterPosition exporterPosition) {
    this.exporterId.wrapString(exporterId);
    this.position.wrap(exporterPosition);
    exporterPositionColumnFamily.upsert(this.exporterId, this.position);
  }

  public long getLowestPosition() {
    final LongArrayList positions = new LongArrayList();

    visitPositions((id, pos) -> positions.addLong(pos));
    return positions.longStream().min().orElse(-1L);
  }

  public void removePosition(final String exporter) {
    exporterId.wrapString(exporter);
    exporterPositionColumnFamily.deleteIfExists(exporterId);
  }

  public boolean hasExporters() {
    return !exporterPositionColumnFamily.isEmpty();
  }
}
