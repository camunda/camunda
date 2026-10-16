/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;

/**
 * A deliberately partial {@link ZeebeDb} on a snapshot of a {@link SqliteDb}, for the recovery path
 * that copies a snapshot into the runtime directory. Like RocksDB's snapshot only database it
 * supports nothing but {@link #createSnapshot} and {@link #close}.
 *
 * <p>A snapshot is the single file that {@code VACUUM INTO} produced, so copying the snapshot is
 * copying the file. The snapshot is opened read-only once to check that it is a database.
 */
final class SqliteSnapshotOnlyDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final Path snapshotFile;

  private SqliteSnapshotOnlyDb(final Path snapshotFile) {
    this.snapshotFile = snapshotFile;
  }

  static <T extends Enum<? extends EnumValue> & EnumValue> ZeebeDb<T> open(final Path directory) {
    final var file = directory.resolve(SqliteDb.DB_FILE_NAME);
    if (!Files.isRegularFile(file)) {
      throw new IllegalStateException(
          "Expected the snapshot at '%s' to contain the database file '%s', but it does not"
              .formatted(directory, SqliteDb.DB_FILE_NAME));
    }
    try (final var connection = SqliteConnection.openReader(file);
        final var statement = connection.jdbc().createStatement()) {
      statement.execute("SELECT count(*) FROM sqlite_master");
    } catch (final SQLException e) {
      throw new IllegalStateException(
          "Expected the snapshot at '%s' to be a readable SQLite database, but it is not"
              .formatted(directory),
          e);
    }
    return new SqliteSnapshotOnlyDb<>(file);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    throw unsupported("createColumnFamily");
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    try {
      Files.createDirectories(snapshotDir.toPath());
      Files.copy(
          snapshotFile,
          snapshotDir.toPath().resolve(SqliteDb.DB_FILE_NAME),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (final IOException e) {
      throw new ZeebeDbException(
          "Failed to copy the SQLite snapshot to '%s'".formatted(snapshotDir), e);
    }
  }

  @Override
  public TransactionContext createContext() {
    throw unsupported("createContext");
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    throw unsupported("isEmpty");
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    throw new UnsupportedOperationException(
        "No meter registry is available for a snapshot only DB, as no metrics are collected.");
  }

  @Override
  public void close() {
    // holds no resources, the snapshot was only opened to validate it
  }

  private UnsupportedOperationException unsupported(final String operation) {
    return new UnsupportedOperationException(
        "Failed to execute 'ZeebeDb#%s'; this operation is not supported on a snapshot-only DB"
            .formatted(operation));
  }
}
