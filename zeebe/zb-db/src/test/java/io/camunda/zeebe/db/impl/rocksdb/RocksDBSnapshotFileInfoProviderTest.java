/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RocksDBSnapshotFileInfoProviderTest {

  @TempDir Path dbPath;
  @TempDir Path snapshotParent;

  private Path snapshotPath;
  private ZeebeRocksDbFactory<ZbColumnFamilies> factory;

  @BeforeEach
  void setup() {
    factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE),
            SimpleMeterRegistry::new);
    snapshotPath = snapshotParent.resolve("snapshot");
  }

  @Test
  public void shouldReportCheckpointFileSizes() throws IOException {
    // given
    createSnapshotWithData();
    final var provider = new RocksDBSnapshotFileInfoProvider();

    // when
    final var filesInfo = provider.getSnapshotFilesInfo(snapshotPath);

    // then
    assertThat(filesInfo.sizes()).isNotEmpty();
    for (final var entry : filesInfo.sizes().entrySet()) {
      final var file = snapshotPath.resolve(entry.getKey());
      assertThat(file).as("reported file %s exists", entry.getKey()).exists();
      assertThat(Files.size(file))
          .as("reported size of %s matches the file on disk", entry.getKey())
          .isEqualTo(entry.getValue());
    }

    assertThat(filesInfo.sizes().keySet()).allMatch(name -> name.endsWith(".sst"));
    assertThat(filesInfo.checksums().keySet()).isSubsetOf(filesInfo.sizes().keySet());
  }

  private void createSnapshotWithData() {
    final var random = new Random(1212331);
    try (final var db = factory.createDb(dbPath.toFile())) {
      final var context = db.createContext();
      context.runInTransaction(
          () -> {
            final var transaction = (ZeebeTransaction) context.getCurrentTransaction();
            final var columnFamily = new RawTransactionalColumnFamily(db, ZbColumnFamilies.DEFAULT);
            for (int i = 0; i < 200; i++) {
              final var key = new byte[random.nextInt(1024) + 1];
              random.nextBytes(key);
              final var value = new byte[random.nextInt(64 * 1024) + 1];
              random.nextBytes(value);
              columnFamily.put(transaction, key, key.length, value, value.length);
            }
            transaction.commit();
          });
      db.createSnapshot(snapshotPath.toFile());
    }
  }
}
