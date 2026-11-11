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

/** Keeps the exporter state in the exporter column family of RocksDB. */
public final class RocksDbExporterStateStore implements ExporterStateStore {

  private final DbString exporterId = new DbString();
  private final ColumnFamily<DbString, ExporterStateEntry> columnFamily;

  public RocksDbExporterStateStore(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EXPORTER, transactionContext, exporterId, new ExporterStateEntry());
  }

  @Override
  public Optional<ExporterStateEntry> find(final String exporterId) {
    this.exporterId.wrapString(exporterId);
    return Optional.ofNullable(columnFamily.get(this.exporterId));
  }

  @Override
  public void upsert(final String exporterId, final ExporterStateEntry entry) {
    this.exporterId.wrapString(exporterId);
    columnFamily.upsert(this.exporterId, entry);
  }

  @Override
  public void delete(final String exporterId) {
    this.exporterId.wrapString(exporterId);
    columnFamily.deleteIfExists(this.exporterId);
  }

  @Override
  public void forEach(final BiConsumer<String, ExporterStateEntry> consumer) {
    columnFamily.forEach((id, entry) -> consumer.accept(id.toString(), entry));
  }

  @Override
  public boolean isEmpty() {
    return columnFamily.isEmpty();
  }
}
