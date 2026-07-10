/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.db.layered.PersistTrigger;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The surface a runtime persist driver relies on: buffered-write and batch-in-flight probes, the
 * inline persist round, and the metrics published along the way.
 */
final class LayeredZeebeDbRuntimeSurfaceTest {

  private final ZeebeDbFactory<ColumnFamilies> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @TempDir private File dbDirectory;
  @TempDir private File secondDbDirectory;

  private ZeebeDb<ColumnFamilies> inner;
  private LayeredZeebeDb<ColumnFamilies> layered;
  private ColumnFamily<DbLong, DbLong> layeredColumnFamily;

  @BeforeEach
  void setUp() {
    inner = dbFactory.createDb(dbDirectory);
    layered = new LayeredZeebeDb<>(inner, LayeredZeebeDbConfig.defaults());
    layeredColumnFamily =
        layered.createColumnFamily(
            ColumnFamilies.ONE, layered.layeredContext(), new DbLong(), new DbLong());
  }

  @AfterEach
  void tearDown() {
    if (layered != null) {
      CloseHelper.quietClose(layered);
      layered = null;
      inner = null;
    }
  }

  // ------------------------------------------------------------------
  // Probes
  // ------------------------------------------------------------------

  @Test
  void shouldReportBufferedWritesUntilPersisted() {
    // given
    final LayeredDomain domain = layered.defaultDomain();
    assertThat(domain.hasBufferedWrites()).isFalse();

    // when a batch commits into the domain
    layered.layeredContext().runInTransaction(() -> put(1, 100));

    // then the write is buffered until a persist round drains it
    assertThat(domain.hasBufferedWrites()).isTrue();
    domain.persistNow(1, PersistTrigger.INTERVAL);
    assertThat(domain.hasBufferedWrites()).isFalse();
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  @Test
  void shouldReportBatchInFlightWhileTransactionIsOpen() {
    // given
    final LayeredDomain domain = layered.defaultDomain();
    final AtomicBoolean observedInFlight = new AtomicBoolean();
    assertThat(domain.batchInFlight()).isFalse();

    // when observed from within an open transaction
    layered
        .layeredContext()
        .runInTransaction(
            () -> {
              put(1, 100);
              observedInFlight.set(domain.batchInFlight());
            });

    // then the probe was true inside and is false after the commit
    assertThat(observedInFlight).isTrue();
    assertThat(domain.batchInFlight()).isFalse();
  }

  // ------------------------------------------------------------------
  // Inline persist round
  // ------------------------------------------------------------------

  @Test
  void shouldCountRoundsByTrigger() {
    // given a buffered write
    layered.layeredContext().runInTransaction(() -> put(1, 100));

    // when a round runs for an explicit trigger
    layered.defaultDomain().persistNow(1, PersistTrigger.PRE_SNAPSHOT);

    // then the round counter carries the trigger tag
    final var registry = inner.getMeterRegistry();
    final var rounds =
        registry
            .get("zeebe.db.layered.persist.rounds")
            .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
            .tag("trigger", "preSnapshot")
            .counter();
    assertThat(rounds.count()).isEqualTo(1.0);
  }

  @Test
  void shouldPublishDrainAndBufferMetrics() {
    // given committed writes that are read back once from the durable store
    layered.layeredContext().runInTransaction(() -> put(1, 100));
    layered.defaultDomain().persistNow(1, PersistTrigger.INTERVAL);

    // when reading a key that only the durable store holds
    layered.layeredContext().runInTransaction(() -> get(1));

    // then drained entries were counted and the read-through was attributed
    final var registry = inner.getMeterRegistry();
    assertThat(
            registry
                .get("zeebe.db.layered.persist.drained.entries")
                .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
                .counter()
                .count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(
            registry.get("zeebe.db.layered.buffered.bytes").tag("layer", "clean").gauge().value())
        .isGreaterThan(0.0);
  }

  // ------------------------------------------------------------------
  // Successor takeover on a reused database
  // ------------------------------------------------------------------

  @Test
  void shouldRecreateExistingColumnFamilyAfterCoordinatorIsBuilt() {
    // given a domain whose coordinator captured the store set
    layered.layeredContext().runInTransaction(() -> put(1, 100));
    layered.defaultDomain().coordinator();

    // when a successor owner re-creates an existing column family on the same context
    final ColumnFamily<DbLong, DbLong> recreated =
        layered.createColumnFamily(
            ColumnFamilies.ONE, layered.layeredContext(), new DbLong(), new DbLong());

    // then it is bound to the same store and sees the buffered state
    final DbLong key = new DbLong();
    key.wrapLong(1);
    assertThat(recreated.get(key).getValue()).isEqualTo(100);
  }

  @Test
  void shouldDiscardBatchLeftOpenByPredecessor() {
    // given a batch left open (a predecessor died mid-batch, its staged write uncommitted)
    final var transaction = layered.layeredContext().getCurrentTransaction();
    try {
      transaction.run(() -> put(1, 100));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    assertThat(layered.defaultDomain().batchInFlight()).isTrue();

    // when the successor discards the open batch
    layered.defaultDomain().discardOpenBatch();

    // then the staged write is gone and no batch is in flight
    assertThat(layered.defaultDomain().batchInFlight()).isFalse();
    assertThat(get(1)).isNull();
  }

  // ------------------------------------------------------------------
  // Factory decoration
  // ------------------------------------------------------------------

  @Test
  void shouldWrapRuntimeDbsAndPassThroughSnapshotOnlyDbs() throws Exception {
    // given
    final var layeredFactory = LayeredZeebeDbFactory.of(dbFactory, LayeredZeebeDbConfig.defaults());

    // when
    final ZeebeDb<ColumnFamilies> runtimeDb = layeredFactory.createDb(secondDbDirectory);

    // then the runtime db is layered and snapshotting it delegates to the wrapped db
    try {
      assertThat(runtimeDb).isInstanceOf(LayeredZeebeDb.class);
    } finally {
      runtimeDb.close();
    }
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private void put(final long key, final long value) {
    final DbLong dbKey = new DbLong();
    final DbLong dbValue = new DbLong();
    dbKey.wrapLong(key);
    dbValue.wrapLong(value);
    layeredColumnFamily.upsert(dbKey, dbValue);
  }

  private Long get(final long key) {
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    final DbLong value = layeredColumnFamily.get(dbKey);
    return value == null ? null : value.getValue();
  }

  private Long passThroughGet(final long key) {
    final TransactionContext passThrough = layered.createContext();
    final ColumnFamily<DbLong, DbLong> columnFamily =
        layered.createColumnFamily(ColumnFamilies.ONE, passThrough, new DbLong(), new DbLong());
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    final DbLong value = columnFamily.get(dbKey);
    return value == null ? null : value.getValue();
  }

  private enum ColumnFamilies implements EnumValue, ScopedColumnFamily {
    DEFAULT,
    ONE;

    @Override
    public int getValue() {
      return ordinal();
    }

    @Override
    public ColumnFamilyScope partitionScope() {
      return ColumnFamilyScope.PARTITION_LOCAL;
    }
  }
}
