/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens a snapshot in a copied runtime database for read-only inspection.
 *
 * <p>The database API does not expose transactional reads on a snapshot-only database. Copying the
 * snapshot before opening the runtime database also avoids taking RocksDB locks on the source
 * snapshot.
 */
public final class SnapshotReader {

  private SnapshotReader() {}

  public static <T> T read(
      final Path root,
      final String snapshotId,
      final Path runtimePath,
      final SnapshotOperation<T> operation)
      throws Exception {
    final var snapshotPath =
        root.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY).resolve(snapshotId);
    if (!Files.isDirectory(snapshotPath)) {
      throw new IOException("Snapshot directory does not exist: " + snapshotPath);
    }

    final Path runtime;
    final Path temporaryParent;
    if (runtimePath == null) {
      temporaryParent = Files.createTempDirectory("cdbg-read-");
      runtime = temporaryParent.resolve("runtime");
    } else {
      temporaryParent = null;
      runtime = runtimePath;
    }

    try (final ZeebeTransactionDb<ZbColumnFamilies> db = openSnapshot(snapshotPath, runtime)) {
      return operation.read(db);
    } finally {
      if (temporaryParent != null) {
        FileUtil.deleteFolderIfExists(temporaryParent);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static ZeebeTransactionDb<ZbColumnFamilies> openSnapshot(
      final Path snapshotPath, final Path runtimePath) {
    return (ZeebeTransactionDb<ZbColumnFamilies>)
        new SnapshotUtil().openSnapshot(snapshotPath, runtimePath);
  }

  @FunctionalInterface
  public interface SnapshotOperation<T> {
    T read(ZeebeTransactionDb<ZbColumnFamilies> db) throws Exception;
  }
}
