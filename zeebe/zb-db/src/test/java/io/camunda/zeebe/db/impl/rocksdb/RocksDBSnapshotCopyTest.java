/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_CACHE_SIZE;
import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_WRITE_BUFFER_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

public class RocksDBSnapshotCopyTest {

  static {
    RocksDB.loadLibrary();
  }

  final long rowsPerCF = 100;
  @TempDir Path destinationPath;
  @TempDir Path sourceDBPath;
  @TempDir Path sourcePath;
  Path sourceSnapshotPath;
  private RocksDBSnapshotCopy copy;
  private Random random;
  private ZeebeRocksDbFactory<ZbColumnFamilies> factory;

  @BeforeEach
  void setup() {
    final LRUCache lruCache = new LRUCache(DEFAULT_CACHE_SIZE);
    final int defaultPartitionCount = 3;
    factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new,
            lruCache,
            new WriteBufferManager(DEFAULT_WRITE_BUFFER_SIZE, lruCache),
            defaultPartitionCount);
    copy = new RocksDBSnapshotCopy(factory);
    random = new Random(1212331);
    sourceSnapshotPath = sourcePath.resolve("snapshot");
  }

  @AfterEach
  public void tearDown() {
    sourceSnapshotPath.toFile().delete();
  }

  @Test
  public void shouldCopyOnlySomeColumns() throws Exception {
    // given
    final var expectedRowsPerCF = populateSnapshot(factory);
    // when
    copy.copySnapshot(sourceSnapshotPath, destinationPath, Set.of(ColumnFamilyScope.GLOBAL));

    // then
    final var sourceSize = computeDBSpace(sourcePath);
    final var copySize = computeDBSpace(destinationPath);
    assertThat(copySize).isLessThan(sourceSize);

    final var copiedRows = initCounterMap();
    try (final var fromDB = factory.createDb(sourceSnapshotPath.toFile());
        final var toDB = factory.createDb(destinationPath.toFile())) {
      final var fromCtx = fromDB.createContext();
      final var toCtx = toDB.createContext();
      toCtx.runInTransaction(
          () -> {
            final var toTx = (ZeebeTransaction) toCtx.getCurrentTransaction();
            for (final var cf : ZbColumnFamilies.values()) {
              if (cf.partitionScope() == ColumnFamilyScope.GLOBAL) {
                final var fromCf = new RawTransactionalColumnFamily(fromDB, cf);
                final var toCf = new RawTransactionalColumnFamily(toDB, cf);
                fromCf.forEach(
                    fromCtx,
                    (key, keyOffset, keyLen, value, valueOffset, valueLen) -> {
                      final byte[] toValue;
                      try {
                        toValue = toCf.get(toTx, key, keyOffset, keyLen);
                      } catch (final Exception e) {
                        throw new RuntimeException(e);
                      }
                      assertThat(toValue).isNotNull();
                      final var original = new byte[valueLen - valueOffset];
                      System.arraycopy(value, valueOffset, original, 0, valueLen - valueOffset);
                      assertThat(toValue).isEqualTo(original);
                      copiedRows.compute(cf, (k, v) -> v + 1);
                      return true;
                    });
              }
            }
          });
    }
    assertThat(copiedRows).containsExactlyInAnyOrderEntriesOf(expectedRowsPerCF);
  }

  @Test
  public void shouldNotChangeFilesOnDiskFromSourceSnapshot() throws IOException {
    // given
    final var expectedRowsPerCF = populateSnapshot(factory);
    final var initialChecksums = computeChecksums(sourceSnapshotPath);

    // when
    copy.copySnapshot(sourceSnapshotPath, destinationPath, Set.of(ColumnFamilyScope.GLOBAL));

    // then
    final var afterChecksums = computeChecksums(sourceSnapshotPath);
    assertThat(afterChecksums).containsExactlyInAnyOrderEntriesOf(initialChecksums);
  }

  private long computeDBSpace(final Path snapshotPath) throws IOException {
    final var totalSize = new AtomicLong();
    Files.walk(snapshotPath, 10).forEach(f -> totalSize.addAndGet(f.toFile().length()));
    return totalSize.get();
  }

  private Map<String, Long> computeChecksums(final Path snapshotPath) throws IOException {
    try (final var fileStream = Files.walk(snapshotPath)) {
      return fileStream
          .filter(p -> !p.toFile().isDirectory())
          .parallel()
          .map(
              path -> {
                try (final var channel = FileChannel.open(path)) {
                  final var bb = ByteBuffer.allocate(4 * 1024);
                  final var crc = new CRC32();
                  while (true) {
                    bb.clear();
                    if (channel.read(bb) < 0) {
                      break;
                    }
                    crc.update(bb);
                  }
                  return Map.entry(path.toString(), crc.getValue());
                } catch (final IOException e) {
                  throw new RuntimeException(e);
                }
              })
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
  }

  private Map<ZbColumnFamilies, Long> initCounterMap() {
    final var map = new java.util.EnumMap<ZbColumnFamilies, Long>(ZbColumnFamilies.class);
    Arrays.stream(ZbColumnFamilies.values()).forEach(cf -> map.put(cf, 0L));
    return map;
  }

  private Map<ZbColumnFamilies, Long> populateSnapshot(
      final ZeebeRocksDbFactory<ZbColumnFamilies> factory) {
    final Map<ZbColumnFamilies, Long> expectedRowsPerCF = initCounterMap();
    try (final var fromDB = factory.createDb(sourceDBPath.toFile())) {
      final var fromCtx = fromDB.createContext();
      fromCtx.runInTransaction(
          () -> {
            final var ctx = (ZeebeTransaction) fromCtx.getCurrentTransaction();
            for (final ZbColumnFamilies cf : ZbColumnFamilies.values()) {
              final var transactionalColumnFamily = new RawTransactionalColumnFamily(fromDB, cf);
              for (int i = 0; i < rowsPerCF; i++) {
                // the key must be big enough to avoid generating duplicates.
                // with 1024 and the seed in the setup no duplicates are created.
                // otherwise, the number of rows per cf can be reduced to reduce the amount of
                // collisions
                final var key = new byte[random.nextInt(1024)];
                random.nextBytes(key);

                final var value = new byte[random.nextInt(64 * 1024)];
                random.nextBytes(value);
                transactionalColumnFamily.put(ctx, key, 0, key.length, value, 0, value.length);
                if (cf.partitionScope() == ColumnFamilyScope.GLOBAL) {
                  expectedRowsPerCF.compute(cf, (k, v) -> v + 1);
                }
              }
              ctx.commit();
            }
          });

      fromDB.createSnapshot(sourceSnapshotPath.toFile());
    }
    return expectedRowsPerCF;
  }
}
