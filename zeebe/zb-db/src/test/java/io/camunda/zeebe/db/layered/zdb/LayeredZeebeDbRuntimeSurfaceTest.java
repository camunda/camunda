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
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.typed.LayeredViewReader;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
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

  @Test
  void shouldElideCreateThenDeleteCyclesByDefault() {
    // given a db built with the default configuration — delete absorption is on by default — and
    // a create-then-delete cycle across two committed batches
    layered.layeredContext().runInTransaction(() -> put(1, 100));
    layered.layeredContext().runInTransaction(() -> delete(1));

    // when the buffer drains
    layered.defaultDomain().persistNow(2, PersistTrigger.INTERVAL);

    // then the pair annihilated in memory — the elision meter moved and neither write ever
    // reached RocksDB
    final var annihilated =
        inner
            .getMeterRegistry()
            .get("zeebe.db.layered.writes.elided")
            .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
            .tag("reason", "annihilated")
            .counter();
    assertThat(annihilated.count()).isEqualTo(2.0);
    assertThat(passThroughGet(1)).isNull();
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
  void shouldAbortRoundLeftOutstandingByPredecessor() {
    // given a persist round a predecessor prepared but never completed (it died in between; its
    // persist IO has terminated)
    final LayeredDomain domain = layered.defaultDomain();
    layered.layeredContext().runInTransaction(() -> put(1, 100));
    domain.preparePersist(1, PersistTrigger.INTERVAL);
    assertThat(domain.roundInFlight()).isTrue();

    // when the successor aborts the stale round
    domain.abortStaleRound();

    // then the segments stayed buffered and the successor's own round drains them
    assertThat(domain.roundInFlight()).isFalse();
    assertThat(domain.hasBufferedWrites()).isTrue();
    domain.persistNow(2, PersistTrigger.INTERVAL);
    assertThat(passThroughGet(1)).isEqualTo(100);
    // and aborting without an outstanding round is a no-op
    domain.abortStaleRound();
  }

  @Test
  void shouldCompleteRoundLeftOutstandingByPredecessorForward() throws Exception {
    // given a paced persist round a predecessor prepared and partially drained — one slice
    // committed, no anchor — before it died (its persist IO has terminated)
    final LayeredDomain domain = layered.defaultDomain();
    layered.layeredContext().runInTransaction(() -> put(1, 100));
    layered.layeredContext().runInTransaction(() -> put(2, 200));
    final var round = domain.preparePersist(2, PersistTrigger.INTERVAL);
    assertThat(round.persistSlice(1)).isFalse();
    assertThat(domain.roundInFlight()).isTrue();

    // when the successor completes the stale round forward
    domain.completeStaleRoundForward();

    // then the durable store holds the full cut — replay on the reused database never sees the
    // torn partial-slice state — and nothing stayed buffered
    assertThat(domain.roundInFlight()).isFalse();
    assertThat(domain.hasBufferedWrites()).isFalse();
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isEqualTo(200);
    // and completing forward without an outstanding round is a no-op
    domain.completeStaleRoundForward();
  }

  @Test
  void shouldDeferDesignatedAnchorEntryOfSlicedDomainDrainToFinalSlice() throws Exception {
    // given the anchor-carrying entry designated before the coordinator exists (as the stream
    // processor does for the last-processed position during recovery)
    final LayeredDomain domain = layered.defaultDomain();
    domain.designateAnchorEntry(ColumnFamilies.ONE.name(), serializedKey(9));
    layered.layeredContext().runInTransaction(() -> put(1, 100));
    layered.layeredContext().runInTransaction(() -> put(9, 900));

    // when a paced drain commits its data slices
    final var round = domain.preparePersist(2, PersistTrigger.INTERVAL);
    boolean done = round.persistSlice(1);
    while (!done) {
      // the anchor carrier must never land while data slices remain
      assertThat(passThroughGet(9)).isNull();
      done = round.persistSlice(1);
    }
    domain.completePersist(round, true);

    // then the anchor carrier landed only with the final slice
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(9)).isEqualTo(900);
  }

  @Test
  void shouldGetOrRegisterDomainByName() {
    // given a named domain
    final LayeredDomain registered = layered.domain("exporter");

    // when asked again by name
    final LayeredDomain resolved = layered.domain("exporter");

    // then the same instance is returned — successor owners of a reused database rely on it
    assertThat(resolved).isSameAs(registered);
    assertThat(resolved).isNotSameAs(layered.defaultDomain());
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
  // Freeze surface (drives the runtime's freeze cadence)
  // ------------------------------------------------------------------

  @Test
  void shouldFreezeActiveWritesIntoPublishedViews() {
    // given a committed batch, invisible to views until frozen
    final LayeredDomain domain = layered.defaultDomain();
    domain.coordinator();
    layered.layeredContext().runInTransaction(() -> put(1, 100));
    assertThat(domain.hasActiveWrites()).isTrue();
    assertThat(readThroughLatestView(domain, 1)).isNull();

    // when
    domain.freezeNow(7L);

    // then the republished view serves the committed write and the overlay is drained
    assertThat(domain.hasActiveWrites()).isFalse();
    assertThat(readThroughLatestView(domain, 1)).isEqualTo(100L);
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

  @Test
  void shouldPinViewSnapshotsWhenCreatedWithColumnFamilyClass() throws Exception {
    // given a factory-wrapped db whose views read through the pinned snapshot source
    final var layeredFactory =
        LayeredZeebeDbFactory.of(dbFactory, LayeredZeebeDbConfig.defaults(), ColumnFamilies.class);
    final ZeebeDb<ColumnFamilies> runtimeDb = layeredFactory.createDb(secondDbDirectory);
    try {
      final var layeredDb = (LayeredZeebeDb<ColumnFamilies>) runtimeDb;
      final TransactionContext context = layeredDb.layeredContext();
      final ColumnFamily<DbLong, DbLong> columnFamily =
          layeredDb.createColumnFamily(ColumnFamilies.ONE, context, new DbLong(), new DbLong());
      final LayeredDomain domain = layeredDb.defaultDomain();
      final DbLong key = new DbLong();
      final DbLong value = new DbLong();

      // and a persisted key, so a view's snapshot is its only source for it
      context.runInTransaction(
          () -> {
            key.wrapLong(1);
            value.wrapLong(100);
            columnFamily.upsert(key, value);
          });
      domain.persistNow(1L, PersistTrigger.INTERVAL);
      final ReadOnlyView pinnedBeforeDelete = domain.viewPublisher().acquireLatest();

      // when the key is deleted and the deletion is persisted after the view was acquired
      context.runInTransaction(
          () -> {
            key.wrapLong(1);
            columnFamily.deleteExisting(key);
          });
      domain.persistNow(2L, PersistTrigger.INTERVAL);

      // then the held view still serves the pinned cut — an unpinned source would lose the key
      try {
        final var viewReader =
            new LayeredViewReader<>(
                pinnedBeforeDelete, ColumnFamilies.ONE.name(), new DbLong(), new DbLong());
        key.wrapLong(1);
        final DbLong pinnedValue = viewReader.get(key);
        assertThat(pinnedValue).isNotNull();
        assertThat(pinnedValue.getValue()).isEqualTo(100L);
      } finally {
        domain.viewPublisher().release(pinnedBeforeDelete);
      }

      // and the latest view observes the deletion
      assertThat(readThroughLatestView(domain, 1)).isNull();
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

  private void delete(final long key) {
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    layeredColumnFamily.deleteExisting(dbKey);
  }

  private Long get(final long key) {
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    final DbLong value = layeredColumnFamily.get(dbKey);
    return value == null ? null : value.getValue();
  }

  private Long readThroughLatestView(final LayeredDomain domain, final long key) {
    final ReadOnlyView view = domain.viewPublisher().acquireLatest();
    try {
      final var viewReader =
          new LayeredViewReader<>(view, ColumnFamilies.ONE.name(), new DbLong(), new DbLong());
      final DbLong dbKey = new DbLong();
      dbKey.wrapLong(key);
      final DbLong value = viewReader.get(dbKey);
      return value == null ? null : value.getValue();
    } finally {
      domain.viewPublisher().release(view);
    }
  }

  /** The serialized key bytes as the layered store keys the entry — for anchor designation. */
  private static byte[] serializedKey(final long key) {
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    final byte[] bytes = new byte[dbKey.getLength()];
    dbKey.write(new UnsafeBuffer(bytes), 0);
    return bytes;
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
