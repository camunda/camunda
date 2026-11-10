/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RawDbAccessTest {

  @TempDir Path path;
  @AutoClose ZeebeTransactionDb<ZbColumnFamilies> db;
  private RawDbAccess raw;

  @BeforeEach
  void setup() {
    final var factory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE),
            SimpleMeterRegistry::new);
    db = factory.createDb(path.resolve("db").toFile());
    raw = db.rawAccess();
  }

  @Test
  void shouldReturnNullForMissingKey() {
    // given
    final var key = new byte[] {1, 2, 3};

    // when
    final var value = raw.get(key, key.length);

    // then
    assertThat(value).isNull();
  }

  @Test
  void shouldReadValueWrittenByBatch() {
    // given
    final var key = new byte[] {1, 2, 3};
    final var value = new byte[] {9, 8};

    // when
    try (final var batch = raw.newBatch()) {
      batch.put(key, value);
      batch.write();
    }

    // then
    assertThat(raw.get(key, key.length)).isEqualTo(value);
  }

  @Test
  void shouldIgnoreBytesPastKeyLength() {
    // given -- the key is the first three bytes of a longer array
    final var longer = new byte[] {1, 2, 3, 4, 5};
    try (final var batch = raw.newBatch()) {
      batch.put(new byte[] {1, 2, 3}, new byte[] {7});
      batch.write();
    }

    // when
    final var value = raw.get(longer, 3);

    // then
    assertThat(value).isEqualTo(new byte[] {7});
  }

  @Test
  void shouldApplyPutsAndDeletesOfOneBatch() {
    // given
    final var kept = new byte[] {1};
    final var removed = new byte[] {2};
    try (final var batch = raw.newBatch()) {
      batch.put(removed, new byte[] {5});
      batch.write();
    }

    // when
    try (final var batch = raw.newBatch()) {
      batch.put(kept, new byte[] {6});
      batch.delete(removed);
      batch.write();
    }

    // then
    assertThat(raw.get(kept, kept.length)).isEqualTo(new byte[] {6});
    assertThat(raw.get(removed, removed.length)).isNull();
  }

  @Test
  void shouldReportWrittenKeyAsPossiblyExisting() {
    // given
    final var key = new byte[] {1, 2, 3};
    try (final var batch = raw.newBatch()) {
      batch.put(key, new byte[] {1});
      batch.write();
    }

    // when
    final var mayExist = raw.keyMayExist(key, key.length);

    // then
    assertThat(mayExist).isTrue();
  }

  @Test
  void shouldIterateCommittedKeysInOrder() {
    // given
    try (final var batch = raw.newBatch()) {
      batch.put(new byte[] {2}, new byte[] {20});
      batch.put(new byte[] {1}, new byte[] {10});
      batch.put(new byte[] {3}, new byte[] {30});
      batch.write();
    }

    // when
    final var keys = new ArrayList<Byte>();
    try (final var iterator = raw.newIterator()) {
      for (iterator.seek(new byte[] {1}); iterator.isValid(); iterator.next()) {
        keys.add(iterator.key()[0]);
      }
    }

    // then
    assertThat(keys).containsExactly((byte) 1, (byte) 2, (byte) 3);
  }

  @Test
  void shouldCreateCheckpoint() throws Exception {
    // given
    try (final var batch = raw.newBatch()) {
      batch.put(new byte[] {1}, new byte[] {1});
      batch.write();
    }
    final var checkpoint = path.resolve("checkpoint");

    // when
    raw.createCheckpoint(checkpoint.toFile());

    // then
    assertThat(checkpoint.resolve("CURRENT")).exists();
    try (final var files = Files.list(checkpoint)) {
      assertThat(files).isNotEmpty();
    }
  }
}
