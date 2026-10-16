/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The broker recovers by opening the snapshot only and taking a snapshot of it into the runtime
 * directory, which it then opens as the database.
 */
final class SqliteSnapshotOnlyDbTest extends ConformanceDbTestBase {

  @TempDir Path scratch;

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>();
  }

  @Test
  void shouldRecoverRuntimeDirectoryFromSnapshot() throws Exception {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(1), dbLong(10));
    cf.insert(dbLong(2), dbLong(20));
    final var snapshot = scratch.resolve("snapshot");
    final var runtime = scratch.resolve("runtime");
    db.createSnapshot(snapshot.toFile());
    cf.insert(dbLong(3), dbLong(30));

    // when
    try (final var snapshotOnly = this.<Cf>factory().openSnapshotOnlyDb(snapshot.toFile())) {
      snapshotOnly.createSnapshot(runtime.toFile());
    }

    // then
    try (final var restored = this.<Cf>factory().createDb(runtime.toFile())) {
      final var restoredCf =
          restored.createColumnFamily(Cf.ONE, restored.createContext(), new DbLong(), new DbLong());
      assertThat(restoredCf.get(dbLong(1)).getValue()).isEqualTo(10);
      assertThat(restoredCf.get(dbLong(2)).getValue()).isEqualTo(20);
      assertThat(restoredCf.exists(dbLong(3))).isFalse();
      assertThat(restoredCf.count()).isEqualTo(2);
    }
  }

  @Test
  void shouldLeaveSnapshotUntouched() throws Exception {
    // given
    longColumnFamily(Cf.ONE).insert(dbLong(1), dbLong(10));
    final var snapshot = scratch.resolve("snapshot");
    final var runtime = scratch.resolve("runtime");
    db.createSnapshot(snapshot.toFile());
    final var before = Files.readAllBytes(snapshot.resolve(SqliteDb.DB_FILE_NAME));

    // when
    try (final var snapshotOnly = this.<Cf>factory().openSnapshotOnlyDb(snapshot.toFile())) {
      snapshotOnly.createSnapshot(runtime.toFile());
    }

    // then
    assertThat(Files.readAllBytes(snapshot.resolve(SqliteDb.DB_FILE_NAME))).isEqualTo(before);
    assertThat(Files.readAllBytes(runtime.resolve(SqliteDb.DB_FILE_NAME))).isEqualTo(before);
    try (final var files = Files.list(snapshot)) {
      assertThat(files.map(Path::getFileName).map(Path::toString))
          .containsExactly(SqliteDb.DB_FILE_NAME);
    }
  }

  @Test
  void shouldRejectOperationsOtherThanSnapshot() throws Exception {
    // given
    final var snapshot = scratch.resolve("snapshot");
    db.createSnapshot(snapshot.toFile());

    try (final var snapshotOnly = this.<Cf>factory().openSnapshotOnlyDb(snapshot.toFile())) {
      // then
      assertThatThrownBy(snapshotOnly::createContext)
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> snapshotOnly.isEmpty(Cf.ONE, context))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(
              () -> snapshotOnly.createColumnFamily(Cf.ONE, context, new DbLong(), new DbLong()))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(snapshotOnly::getMeterRegistry)
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void shouldFailToOpenDirectoryWithoutDatabase() {
    // given
    final var empty = scratch.resolve("empty");

    // then
    assertThatThrownBy(
            () -> {
              Files.createDirectories(empty);
              this.<Cf>factory().openSnapshotOnlyDb(empty.toFile());
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SqliteDb.DB_FILE_NAME);
  }

  @Test
  void shouldFailToOpenFileThatIsNoDatabase() throws Exception {
    // given
    final var corrupt = scratch.resolve("corrupt");
    Files.createDirectories(corrupt);
    Files.writeString(corrupt.resolve(SqliteDb.DB_FILE_NAME), "this is not a database".repeat(20));

    // then
    assertThatThrownBy(() -> this.<Cf>factory().openSnapshotOnlyDb(corrupt.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("readable SQLite database");
  }
}
