/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.DEFAULT_MEMORY_LIMIT;

import io.camunda.debug.cli.concurrency.CurrentThreadConcurrencyControl;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotMetadata;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;

public class SnapshotUtil {

  private final ZeebeRocksDbFactory zeebeDbFactory;

  public SnapshotUtil() {
    zeebeDbFactory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setWalDisabled(false),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE),
            SimpleMeterRegistry::new,
            new RocksDbResources.Shared(DEFAULT_MEMORY_LIMIT, 3));
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

  @SuppressWarnings("unchecked")
  public ZeebeDb<ZbColumnFamilies> openReadOnly(final Path snapshotPath, final Path runtimePath) {
    return openSnapshot(snapshotPath, runtimePath);
  }

  /**
   * Builds a {@link DbProcessState} over an opened snapshot db. The transformer is only needed to
   * satisfy the constructor; reading persisted process state does not use it, so a fixed clock and
   * no-op metrics are fine.
   */
  public static DbProcessState openProcessState(
      final ZeebeDb<ZbColumnFamilies> db, final TransactionContext context) {
    final var stateTransformer =
        BpmnFactory.createTransformer(
            InstantSource.fixed(Instant.EPOCH),
            ExpressionLanguageMetrics.noop(),
            Integer.MAX_VALUE);
    return new DbProcessState(db, context, new EngineConfiguration(), stateTransformer);
  }

  public PersistedSnapshot takeSnapshot(
      final ZeebeDb runtime,
      final Path rootDirectory,
      final String stringSnapshotId,
      final long lastFollowupEventPosition) {
    final var snapshotId = FileBasedSnapshotId.ofFileName(stringSnapshotId).getOrThrow();

    // Preserve the source snapshot's broker id; hardcoding 0 here would mislabel snapshots on every
    // broker whose node id is not 0 (the broker id is part of the snapshot folder name).
    final var snapshotStore =
        new FileBasedSnapshotStoreImpl(
            snapshotId.getBrokerId(),
            rootDirectory,
            new RocksDBSnapshotFileInfoProvider(),
            new CurrentThreadConcurrencyControl(),
            new SnapshotMetrics(new SimpleMeterRegistry()));

    final var transientSnapshot =
        snapshotStore
            .newTransientSnapshot(
                snapshotId.getIndex(),
                snapshotId.getTerm(),
                snapshotId.getProcessedPosition(),
                snapshotId.getExportedPosition(),
                true)
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
