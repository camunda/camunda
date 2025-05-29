/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RocksDBSnapshotCopyTest {
  @TempDir Path destinationPath;
  @TempDir Path destinationSnapshotPath;
  private final Path sourcePath =
      Path.of("/home/carlosana/incidents/game-day/2/snapshots/6734951-1-72656096-72655064-2");

  @Test
  public void shouldCopyOnlySomeColumns() throws Exception {
    // given
    final var factory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
    final var copy = new RocksDBSnapshotCopy(factory);
    final var snapshotPath = destinationSnapshotPath.resolve("snapshot");
    // when
    copy.copySnapshot(sourcePath, destinationPath, snapshotPath, Set.of(ColumnFamilyScope.GLOBAL));

    // then
    assertThat(snapshotPath.toFile()).exists();
    final var sourceSize = printSnapshotFiles(sourcePath);
    final var copySize = printSnapshotFiles(destinationPath);
    assertThat(copySize).isLessThan(sourceSize);

    final var numOfRows = new AtomicLong();
    final var numOfColumnFamilies = new AtomicLong();

    try (final var toDB = factory.createDb(snapshotPath.toFile())) {
      try (final var fromDB = factory.createDb(sourcePath.toFile())) {
        final var fromCtx = fromDB.createContext();
        final var toCtx = toDB.createContext();

        toCtx.runInTransaction(
            () -> {
              final var toTx = (ZeebeTransaction) toCtx.getCurrentTransaction();
              for (final var cf : ZbColumnFamilies.values()) {
                if (cf.partitionScope() == ColumnFamilyScope.GLOBAL) {
                  numOfColumnFamilies.incrementAndGet();
                  final var fromCf = new RawTransactionalColumnFamily(fromDB, cf, fromCtx);
                  final var toCf = new RawTransactionalColumnFamily(toDB, cf, toCtx);
                  fromCf.forEach(
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
                        numOfRows.incrementAndGet();
                        return true;
                      });
                }
              }
            });
      }
    }
    System.out.println(
        numOfRows.get() + " rows copied, " + numOfColumnFamilies.get() + " column families");
    assertThat(numOfRows.get()).isGreaterThan(0L);
    assertThat(numOfColumnFamilies.get())
        .isEqualTo(
            Arrays.stream(ZbColumnFamilies.values())
                .filter(cf -> cf.partitionScope() == ColumnFamilyScope.GLOBAL)
                .count());
  }

  private long printSnapshotFiles(final Path snapshotPath) throws IOException {
    final var totalSize = new AtomicLong();
    Files.walk(snapshotPath, 10)
        .forEach(
            f -> {
              try {
                totalSize.addAndGet(f.toFile().length());
                System.out.printf("%s -> %s\n", f, Files.size(f));
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            });
    System.out.println("Total space used: " + totalSize.get());
    return totalSize.get();
  }
}
