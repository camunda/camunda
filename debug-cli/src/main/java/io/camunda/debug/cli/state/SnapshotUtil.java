/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.debug.cli.concurrency.CurrentThreadConcurrencyControl;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotMetadata;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SnapshotUtil {

  private final ZeebeRocksDbFactory zeebeDbFactory;

  public SnapshotUtil() {
    zeebeDbFactory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setWalDisabled(false),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
  }

  public ZeebeDb openSnapshot(final Path snapshotPath, final Path runtimePath) {
    try (final var db = zeebeDbFactory.openSnapshotOnlyDb(snapshotPath.toFile())) {
      db.createSnapshot(runtimePath.toFile());
    } catch (final Exception e) {
      throw new RuntimeException("Failed to open and copy snapshot", e);
    }
    final var runtimeDb = zeebeDbFactory.createDb(runtimePath.toFile());
    return runtimeDb;
  }

  public PersistedSnapshot takeSnapshot(
      final ZeebeDb runtime,
      final Path rootDirectory,
      final String stringSnapshotId,
      final long lastFollowupEventPosition) {
    final var snapshotStore =
        new FileBasedSnapshotStoreImpl(
            0,
            rootDirectory,
            new ChecksumProviderRocksDBImpl(),
            new CurrentThreadConcurrencyControl(),
            new SnapshotMetrics(new SimpleMeterRegistry()));

    final var snapshotId = FileBasedSnapshotId.ofFileName(stringSnapshotId).get();

    final var transientSnapshot =
        snapshotStore
            .newTransientSnapshot(
                snapshotId.getIndex(),
                snapshotId.getTerm(),
                snapshotId.getProcessedPosition(),
                snapshotId.getExportedPosition())
            .get();

    transientSnapshot
        .withLastFollowupEventPosition(lastFollowupEventPosition)
        .take(
            path -> {
              runtime.createSnapshot(path.toFile());
            })
        .join();

    return transientSnapshot.persist().join();
  }

  public static long getLastFollowupEventPosition(final Path snapshotPath) throws IOException {
    final var snapshotMetadata =
        Files.readAllBytes(snapshotPath.resolve(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME));
    final var metadata = FileBasedSnapshotMetadata.decode(snapshotMetadata);

    final var lastFollowupEventPosition = metadata.lastFollowupEventPosition();
    return lastFollowupEventPosition;
  }
}
