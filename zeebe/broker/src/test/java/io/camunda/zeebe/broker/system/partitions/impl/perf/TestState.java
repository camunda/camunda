/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.perf;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

final class TestState {
  private static final int BATCH_INSERT_SIZE = 10_000;
  private static final int KEY_VALUE_SIZE = 8096;
  private static final long DEFAULT_TEST_CACHE_SIZE = 100 * 1024 * 1024;

  static {
    RocksDB.loadLibrary();
  }

  TestContext generateContext(final long sizeInBytes) throws Exception {
    final var meterRegistry = new SimpleMeterRegistry();
    final var tempDirectory = Files.createTempDirectory("statePerf");
    final var actorScheduler =
        ActorScheduler.newActorScheduler()
            .setIoBoundActorThreadCount(1)
            .setCpuBoundActorThreadCount(1)
            .build();
    actorScheduler.start();

    final var snapshotStore =
        new FileBasedSnapshotStore(0, 1, tempDirectory, snapshotPath -> Map.of(), meterRegistry);
    actorScheduler.submitActor(snapshotStore).join();

    generateSnapshot(snapshotStore, sizeInBytes);

    return new TestContext(actorScheduler, tempDirectory, snapshotStore, createDbFactory());
  }

  private void generateSnapshot(
      final ConstructableSnapshotStore snapshotStore, final long sizeInBytes) {
    final var snapshot = snapshotStore.newTransientSnapshot(1, 1, 1, 1, false).get();
    snapshot.take(path -> generateSnapshot(path, sizeInBytes)).join();
    snapshot.persist().join();
  }

  private void generateSnapshot(final Path path, final long sizeInBytes) {
    final var dbFactory = createDbFactory();

    //noinspection ResultOfMethodCallIgnored
    path.toFile().mkdirs();

    do {
      try (final var db = dbFactory.createDb(path.toFile())) {
        final var txn = db.createContext();
        final var columns =
            Arrays.stream(ZbColumnFamilies.values())
                .map(col -> db.createColumnFamily(col, txn, new DbString(), new DbString()))
                .toList();
        txn.runInTransaction(() -> insertData(columns));
      }
    } while (computeSnapshotSize(path) < sizeInBytes);
  }

  private ZeebeRocksDbFactory<ZbColumnFamilies> createDbFactory() {
    final LRUCache lruCache = new LRUCache(DEFAULT_TEST_CACHE_SIZE);
    final int defaultPartitionCount = 3;
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration(),
        new ConsistencyChecksSettings(false, false),
        new AccessMetricsConfiguration(Kind.NONE, 1),
        SimpleMeterRegistry::new,
        lruCache,
        new WriteBufferManager(DEFAULT_TEST_CACHE_SIZE / 4, lruCache),
        defaultPartitionCount);
  }

  private void insertData(final List<ColumnFamily<DbString, DbString>> columns) {
    final var random = ThreadLocalRandom.current();

    for (int i = 0; i < BATCH_INSERT_SIZE; i++) {
      final var column = columns.get(random.nextInt(columns.size()));
      column.insert(generateData(), generateData());
    }
  }

  private DbString generateData() {
    final var buffer = new byte[KEY_VALUE_SIZE];
    final var data = new DbString();
    ThreadLocalRandom.current().nextBytes(buffer);
    data.wrapBuffer(new UnsafeBuffer(buffer));

    return data;
  }

  private static long computeSnapshotSize(final Path root) {
    try (final var files = Files.walk(root)) {
      return files.mapToLong(TestState::uncheckedFileSize).sum();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static long uncheckedFileSize(final Path file) {
    try {
      return Files.size(file);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public record TestContext(
      ActorScheduler actorScheduler,
      Path temporaryFolder,
      FileBasedSnapshotStore snapshotStore,
      ZeebeDbFactory<ZbColumnFamilies> dbFactory)
      implements AutoCloseable {

    @Override
    public void close() throws Exception {
      CloseHelper.quietCloseAll(snapshotStore, actorScheduler);
      FileUtil.deleteFolder(temporaryFolder);
    }

    public long snapshotSize() {
      return computeSnapshotSize(temporaryFolder);
    }
  }
}
