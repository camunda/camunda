/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.PrimaryStorageType;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.sqlite.SqliteDb;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The state of a partition is specific to its storage backend. Opening a directory that holds the
 * state of the other backend must fail, not create an empty database beside it, which would look
 * like total data loss.
 */
final class PrimaryStorageBackendTest {

  private static final String ROCKSDB_MARKER = "CURRENT";

  @TempDir Path scratch;

  private final RocksDbResources rocksDbResources =
      RocksDbResources.of(new RocksDbConfiguration(), new RocksDbResources.RuntimeInfo(1));

  @AfterEach
  void closeResources() {
    if (rocksDbResources instanceof final RocksDbResources.Shared shared) {
      CloseHelper.closeAll(shared.getSharedWriteBufferManager(), shared.getSharedCache());
    }
  }

  @Test
  void shouldSelectRocksDbByDefault() {
    // given
    final var cfg = new BrokerCfg();

    // when
    final var backend = backend(cfg);

    // then
    assertThat(cfg.getData().getPrimaryStorageType()).isEqualTo(PrimaryStorageType.ROCKSDB);
    assertThat(backend.type()).isEqualTo(PrimaryStorageType.ROCKSDB);
    assertThat(backend.snapshotFileInfoProvider())
        .isInstanceOf(RocksDBSnapshotFileInfoProvider.class);
  }

  @Test
  void shouldSelectSqlite() throws Exception {
    // given
    final var cfg = configured(PrimaryStorageType.SQLITE);

    // when
    final var backend = backend(cfg);

    // then it opens SQLite databases, and its capabilities are what the broker requires
    assertThat(backend.type()).isEqualTo(PrimaryStorageType.SQLITE);
    assertThat(backend.dbFactory().capabilities().scopedCopy()).isTrue();
    final var runtime = scratch.resolve("runtime");
    backend.dbFactory().createDb(runtime.toFile()).close();
    assertThat(runtime.resolve(SqliteDb.DB_FILE_NAME)).isRegularFile();
  }

  @Test
  void shouldChecksumSqliteSnapshotAsPlainFiles() throws Exception {
    // given
    final var snapshot = Files.createDirectory(scratch.resolve("snapshot"));
    Files.writeString(snapshot.resolve(SqliteDb.DB_FILE_NAME), "content");

    // when
    final var info =
        PrimaryStorageBackend.snapshotFileInfoProvider(PrimaryStorageType.SQLITE)
            .getSnapshotFilesInfo(snapshot);

    // then no per-file checksum is reused, so every file is checksummed as a plain file
    assertThat(info).isEqualTo(SnapshotFilesInfo.none());
  }

  @Test
  void shouldRecoverSqliteRuntimeFromSnapshotThroughSelectedBackend() throws Exception {
    // given
    final var backend = backend(configured(PrimaryStorageType.SQLITE));
    final var runtime = scratch.resolve("runtime");
    final var snapshot = scratch.resolve("snapshot");
    try (final var db = backend.dbFactory().createDb(runtime.toFile())) {
      db.createSnapshot(snapshot.toFile());
    }

    // when
    final var recovered = scratch.resolve("recovered");
    try (final var snapshotOnly = backend.dbFactory().openSnapshotOnlyDb(snapshot.toFile())) {
      snapshotOnly.createSnapshot(recovered.toFile());
    }

    // then
    backend.dbFactory().createDb(recovered.toFile()).close();
  }

  @Test
  void shouldFailToOpenRuntimeOfRocksDbWithSqlite() throws Exception {
    // given a runtime directory that RocksDB wrote
    final var runtime = Files.createDirectory(scratch.resolve("runtime"));
    Files.createFile(runtime.resolve(ROCKSDB_MARKER));
    final var backend = backend(configured(PrimaryStorageType.SQLITE));

    // then
    assertThatThrownBy(() -> backend.dbFactory().createDb(runtime.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sqlite")
        .hasMessageContaining("rocksdb")
        .hasMessageContaining(runtime.toString());
    assertThat(listing(runtime)).containsExactly(ROCKSDB_MARKER);
  }

  @Test
  void shouldFailToOpenRuntimeOfSqliteWithRocksDb() throws Exception {
    // given a runtime directory that SQLite wrote
    final var runtime = Files.createDirectory(scratch.resolve("runtime"));
    Files.createFile(runtime.resolve(SqliteDb.DB_FILE_NAME));
    final var backend = backend(configured(PrimaryStorageType.ROCKSDB));

    // then
    assertThatThrownBy(() -> backend.dbFactory().createDb(runtime.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rocksdb")
        .hasMessageContaining("sqlite");
    assertThat(listing(runtime)).containsExactly(SqliteDb.DB_FILE_NAME);
  }

  @Test
  void shouldFailToRecoverFromSnapshotOfOtherBackend() throws Exception {
    // given a snapshot that RocksDB wrote
    final var snapshot = Files.createDirectory(scratch.resolve("snapshot"));
    Files.createFile(snapshot.resolve(ROCKSDB_MARKER));
    final var backend = backend(configured(PrimaryStorageType.SQLITE));

    // then
    assertThatThrownBy(() -> backend.dbFactory().openSnapshotOnlyDb(snapshot.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sqlite")
        .hasMessageContaining("rocksdb");
  }

  @Test
  void shouldFailToCopySnapshotOfOtherBackend() throws Exception {
    // given a snapshot that SQLite wrote
    final var snapshot = Files.createDirectory(scratch.resolve("snapshot"));
    Files.createFile(snapshot.resolve(SqliteDb.DB_FILE_NAME));
    final var backend = backend(configured(PrimaryStorageType.ROCKSDB));

    // then
    assertThatThrownBy(
            () ->
                backend
                    .snapshotCopy()
                    .copySnapshot(
                        snapshot, scratch.resolve("copy"), Set.of(ColumnFamilyScope.GLOBAL)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rocksdb")
        .hasMessageContaining("sqlite");
  }

  @Test
  void shouldFailSanityCheckOfStateOfOtherBackend() throws Exception {
    // given
    final var snapshot = Files.createDirectory(scratch.resolve("snapshot"));
    Files.createFile(snapshot.resolve(ROCKSDB_MARKER));
    final var backend = PrimaryStorageBackend.forSanityCheck(PrimaryStorageType.SQLITE);

    // then
    assertThatThrownBy(() -> backend.dbFactory().openSnapshotOnlyDb(snapshot.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sqlite")
        .hasMessageContaining("rocksdb");
  }

  @Test
  void shouldAcceptEmptyAndMissingDirectories() throws Exception {
    // given
    final var backend = backend(configured(PrimaryStorageType.SQLITE));
    final var empty = Files.createDirectory(scratch.resolve("empty"));

    // then
    backend.dbFactory().createDb(empty.toFile()).close();
    backend.dbFactory().createDb(scratch.resolve("missing").toFile()).close();
  }

  private PrimaryStorageBackend backend(final BrokerCfg cfg) {
    return PrimaryStorageBackend.forPartition(cfg, SimpleMeterRegistry::new, rocksDbResources);
  }

  private static BrokerCfg configured(final PrimaryStorageType type) {
    final var cfg = new BrokerCfg();
    cfg.getData().setPrimaryStorageType(type);
    return cfg;
  }

  private static Set<String> listing(final Path dir) throws IOException {
    try (final Stream<Path> files = Files.list(dir)) {
      return files
          .map(file -> file.getFileName().toString())
          .collect(java.util.stream.Collectors.toSet());
    }
  }
}
