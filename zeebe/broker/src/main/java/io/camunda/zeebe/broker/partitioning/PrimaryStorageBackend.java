/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.CloseMode;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.PrimaryStorageType;
import io.camunda.zeebe.db.SnapshotCopy;
import io.camunda.zeebe.db.StorageCapabilities;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotCopy;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.sqlite.SqliteDb;
import io.camunda.zeebe.db.impl.sqlite.SqliteDbFactory;
import io.camunda.zeebe.db.impl.sqlite.SqliteSnapshotCopy;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * The storage backend selected by {@code camunda.data.primary-storage.type}: the database factory
 * and the {@link SnapshotCopy} and {@link SnapshotFileInfoProvider} that belong to it. This is the
 * one place that maps the configured type to its implementations.
 *
 * <p>The factory is guarded against state of the other backend. A partition's on-disk state is
 * backend specific, and opening a directory that holds the other backend's state would not fail but
 * create an empty database beside it, which looks like total data loss. Opening, recovering from or
 * copying such a directory fails with a message that names both backends instead.
 *
 * @param type the selected backend
 * @param dbFactory opens the runtime database and snapshots, failing on state of another backend
 * @param snapshotCopy copies the column families of a scope from one snapshot to another
 * @param snapshotFileInfoProvider the per-file checksums of a snapshot of this backend
 */
public record PrimaryStorageBackend(
    PrimaryStorageType type,
    ZeebeDbFactory<ZbColumnFamilies> dbFactory,
    SnapshotCopy snapshotCopy,
    SnapshotFileInfoProvider snapshotFileInfoProvider) {

  /** RocksDB's file that identifies a directory as one of its databases. */
  private static final String ROCKSDB_MARKER_FILE = "CURRENT";

  /**
   * The backend of a running partition.
   *
   * @param rocksDbResources shared by all partitions of a broker, and only used by RocksDB
   */
  public static PrimaryStorageBackend forPartition(
      final BrokerCfg brokerCfg,
      final Supplier<MeterRegistry> meterRegistryFactory,
      final RocksDbResources rocksDbResources) {
    final var type = brokerCfg.getData().getPrimaryStorageType();
    final var consistencyChecks = brokerCfg.getExperimental().getConsistencyChecks().getSettings();
    return switch (type) {
      case ROCKSDB -> {
        final var rocksDb = brokerCfg.getExperimental().getRocksdb();
        final var factory =
            new ZeebeRocksDbFactory<ZbColumnFamilies>(
                rocksDb.createRocksDbConfiguration(),
                consistencyChecks,
                new AccessMetricsConfiguration(rocksDb.getAccessMetrics()),
                meterRegistryFactory,
                rocksDbResources);
        yield of(type, factory, new RocksDBSnapshotCopy(factory));
      }
      case SQLITE ->
          of(
              type,
              new SqliteDbFactory<>(consistencyChecks, meterRegistryFactory),
              new SqliteSnapshotCopy());
    };
  }

  /**
   * A backend for opening and closing a restored partition once, without a running partition around
   * it. It does not need the broker's shared resources and collects no metrics.
   */
  public static PrimaryStorageBackend forSanityCheck(final PrimaryStorageType type) {
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    return switch (type) {
      case ROCKSDB -> {
        final var factory = SanityCheckRocksDb.FACTORY;
        yield of(type, factory, new RocksDBSnapshotCopy(factory));
      }
      case SQLITE ->
          of(
              type,
              new SqliteDbFactory<>(consistencyChecks, SimpleMeterRegistry::new),
              new SqliteSnapshotCopy());
    };
  }

  /**
   * The file info provider of snapshots of the backend. RocksDB reuses the CRC32C checksums it
   * keeps per SST file. SQLite has none, so its snapshot, a single file, is checksummed as a plain
   * file.
   */
  public static SnapshotFileInfoProvider snapshotFileInfoProvider(final PrimaryStorageType type) {
    return switch (type) {
      case ROCKSDB -> new RocksDBSnapshotFileInfoProvider();
      case SQLITE -> snapshotPath -> SnapshotFilesInfo.none();
    };
  }

  private static PrimaryStorageBackend of(
      final PrimaryStorageType type,
      final ZeebeDbFactory<ZbColumnFamilies> factory,
      final SnapshotCopy snapshotCopy) {
    return new PrimaryStorageBackend(
        type,
        new MismatchGuardingFactory(type, factory),
        (from, to, scope) -> {
          requireNoStateOfOtherBackend(type, from);
          snapshotCopy.copySnapshot(from, to, scope);
        },
        snapshotFileInfoProvider(type));
  }

  /**
   * @throws IllegalStateException if the directory holds state written by a backend other than the
   *     configured one
   */
  static void requireNoStateOfOtherBackend(final PrimaryStorageType configured, final Path dir) {
    for (final var other : PrimaryStorageType.values()) {
      if (other != configured && holdsStateOf(other, dir)) {
        throw new IllegalStateException(
            ("Expected the directory '%s' to hold %s state, as configured by camunda.data.primary-storage.type=%s, "
                    + "but it holds %s state. The state of a partition is specific to its storage backend, and opening "
                    + "it with %s would start from an empty database next to it. Set camunda.data.primary-storage.type=%s "
                    + "to keep using this state, or start from an empty data directory to switch backends.")
                .formatted(
                    dir,
                    configured.configValue(),
                    configured.configValue(),
                    other.configValue(),
                    configured.configValue(),
                    other.configValue()));
      }
    }
  }

  private static boolean holdsStateOf(final PrimaryStorageType type, final Path dir) {
    return switch (type) {
      case ROCKSDB -> Files.exists(dir.resolve(ROCKSDB_MARKER_FILE));
      case SQLITE -> Files.exists(dir.resolve(SqliteDb.DB_FILE_NAME));
    };
  }

  /** Holds the RocksDB factory of the sanity check, which is only built if RocksDB is used. */
  private static final class SanityCheckRocksDb {
    private static final RocksDbConfiguration CONFIG = new RocksDbConfiguration();
    private static final ZeebeRocksDbFactory<ZbColumnFamilies> FACTORY =
        new ZeebeRocksDbFactory<>(
            CONFIG,
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(AccessMetricsConfiguration.Kind.NONE),
            SimpleMeterRegistry::new,
            RocksDbResources.of(CONFIG, new RocksDbResources.RuntimeInfo(1)));
  }

  private record MismatchGuardingFactory(
      PrimaryStorageType type, ZeebeDbFactory<ZbColumnFamilies> delegate)
      implements ZeebeDbFactory<ZbColumnFamilies> {

    @Override
    public ZeebeDb<ZbColumnFamilies> createDb(final File pathName, final CloseMode closeMode) {
      requireNoStateOfOtherBackend(type, pathName.toPath());
      return delegate.createDb(pathName, closeMode);
    }

    @Override
    public ZeebeDb<ZbColumnFamilies> createDb(final File pathName) {
      requireNoStateOfOtherBackend(type, pathName.toPath());
      return delegate.createDb(pathName);
    }

    @Override
    public ZeebeDb<ZbColumnFamilies> openSnapshotOnlyDb(final File path) {
      requireNoStateOfOtherBackend(type, path.toPath());
      return delegate.openSnapshotOnlyDb(path);
    }

    @Override
    public StorageCapabilities capabilities() {
      return delegate.capabilities();
    }
  }
}
