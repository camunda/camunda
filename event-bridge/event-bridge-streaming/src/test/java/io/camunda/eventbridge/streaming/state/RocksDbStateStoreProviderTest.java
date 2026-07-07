/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RocksDbStateStoreProviderTest {

  /**
   * The RocksDB property gauges that prove or disprove tombstone accumulation: tombstones in the
   * memtables, live-data vs. SST-file sizes, key estimates and memtable sizes.
   */
  private static final String[] EXPECTED_PROPERTY_GAUGES = {
    "zeebe.rocksdb.live.num.deletes.active.mem.table",
    "zeebe.rocksdb.live.num.deletes.imm.mem.tables",
    "zeebe.rocksdb.live.estimate.num.keys",
    "zeebe.rocksdb.live.estimate.live.data.size",
    "zeebe.rocksdb.sst.total.sst.files.size",
    "zeebe.rocksdb.sst.live.sst.files.size",
    "zeebe.rocksdb.memory.cur.size.all.mem.tables",
    "zeebe.rocksdb.memory.cur.size.active.mem.table",
    "zeebe.rocksdb.writes.estimate.pending.compaction.bytes",
  };

  @TempDir private Path dataDir;

  @Test
  void shouldRegisterRocksDbPropertyMetricsOnOpen() throws Exception {
    // given
    final var registry = new SimpleMeterRegistry();

    // when
    try (final var provider =
        RocksDbStateStoreProvider.<TestColumnFamilies>open(dataDir.toFile(), registry)) {
      final KeyValueStore<DbLong, DbString> store =
          provider.keyValueStore(TestColumnFamilies.KV, new DbLong(), new DbString());
      final var key = new DbLong();
      final var value = new DbString();
      key.wrapLong(1);
      value.wrapString("value");
      store.put(key, value);

      // then - the gauges exist in the caller's registry, tagged by store, and read a real value
      for (final String gaugeName : EXPECTED_PROPERTY_GAUGES) {
        final Gauge gauge =
            registry.find(gaugeName).tag("store", dataDir.toFile().getName()).gauge();
        assertThat(gauge).as("gauge %s", gaugeName).isNotNull();
        assertThat(gauge.value()).as("value of gauge %s", gaugeName).isNotNaN().isNotNegative();
      }
    }
  }

  @Test
  void shouldRemoveRocksDbMetricsFromRegistryOnClose() throws Exception {
    // given
    final var registry = new SimpleMeterRegistry();
    final var provider =
        RocksDbStateStoreProvider.<TestColumnFamilies>open(dataDir.toFile(), registry);

    // when
    provider.close();

    // then - the DB's meters are gone, but the caller's registry itself is still usable
    for (final String gaugeName : EXPECTED_PROPERTY_GAUGES) {
      assertThat(registry.find(gaugeName).gauge()).as("gauge %s", gaugeName).isNull();
    }
    assertThat(registry.isClosed()).isFalse();
    registry.counter("still.usable").increment();
    assertThat(registry.find("still.usable").counter()).isNotNull();
  }
}
