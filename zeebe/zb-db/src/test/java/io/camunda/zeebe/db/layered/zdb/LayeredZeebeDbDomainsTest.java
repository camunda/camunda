/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.typed.LayeredViewReader;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ownership-registry surface of {@link LayeredZeebeDb}: N isolated single-owner domains. */
final class LayeredZeebeDbDomainsTest {

  private final ZeebeDbFactory<ColumnFamilies> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @TempDir private File dbDirectory;

  private ZeebeDb<ColumnFamilies> inner;
  private LayeredZeebeDb<ColumnFamilies> layered;

  @BeforeEach
  void setUp() {
    inner = dbFactory.createDb(dbDirectory);
    layered = new LayeredZeebeDb<>(inner, LayeredZeebeDbConfig.defaults());
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
  // Domain isolation
  // ------------------------------------------------------------------

  @Test
  void shouldIsolateBufferedWritesBetweenDomains() {
    // given two domains with disjoint column families
    final LayeredDomain domainA = layered.registerDomain("a");
    final LayeredDomain domainB = layered.registerDomain("b");
    final ColumnFamily<DbLong, DbLong> oneInA = createColumnFamily(domainA, ColumnFamilies.ONE);
    final ColumnFamily<DbLong, DbLong> twoInB = createColumnFamily(domainB, ColumnFamilies.TWO);

    // when each domain commits into its own store
    domainA.context().runInTransaction(() -> put(oneInA, 1, 100));
    domainB.context().runInTransaction(() -> put(twoInB, 1, 200));

    // then each domain reads back only its own write
    assertThat(get(oneInA, 1)).isEqualTo(100);
    assertThat(get(twoInB, 1)).isEqualTo(200);

    // and both writes are buffered in memory only — RocksDB holds neither yet
    assertThat(passThroughGet(ColumnFamilies.ONE, 1)).isNull();
    assertThat(passThroughGet(ColumnFamilies.TWO, 1)).isNull();
  }

  @Test
  void shouldDrainOnlyOwnColumnFamiliesInPersistRound() throws Exception {
    // given both domains hold unpersisted buffered writes
    final LayeredDomain domainA = layered.registerDomain("a");
    final LayeredDomain domainB = layered.registerDomain("b");
    final ColumnFamily<DbLong, DbLong> oneInA = createColumnFamily(domainA, ColumnFamilies.ONE);
    final ColumnFamily<DbLong, DbLong> twoInB = createColumnFamily(domainB, ColumnFamilies.TWO);
    domainA.context().runInTransaction(() -> put(oneInA, 1, 100));
    domainB.context().runInTransaction(() -> put(twoInB, 1, 200));

    // when only domain A runs a persist round
    runPersistRound(domainA, 1);

    // then A's write reached RocksDB while B's stayed buffered
    assertThat(passThroughGet(ColumnFamilies.ONE, 1)).isEqualTo(100);
    assertThat(passThroughGet(ColumnFamilies.TWO, 1)).isNull();
    assertThat(get(twoInB, 1)).isEqualTo(200);

    // and B's own later round drains B's store
    runPersistRound(domainB, 1);
    assertThat(passThroughGet(ColumnFamilies.TWO, 1)).isEqualTo(200);
  }

  @Test
  void shouldRejectColumnFamilyOwnedByAnotherDomain() {
    // given a column family owned by domain A
    final LayeredDomain domainA = layered.registerDomain("a");
    final LayeredDomain domainB = layered.registerDomain("b");
    createColumnFamily(domainA, ColumnFamilies.ONE);

    // when / then creating the same column family in domain B throws
    assertThatThrownBy(() -> createColumnFamily(domainB, ColumnFamilies.ONE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("single owning domain")
        .hasMessageContaining("'a'")
        .hasMessageContaining("'b'");
  }

  @Test
  void shouldRejectDuplicateDomainName() {
    // given
    layered.registerDomain("a");

    // when / then
    assertThatThrownBy(() -> layered.registerDomain("a"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'a'");
  }

  @Test
  void shouldScopeCoordinatorCreationGuardToDomain() {
    // given domain A's coordinator is already built
    final LayeredDomain domainA = layered.registerDomain("a");
    final LayeredDomain domainB = layered.registerDomain("b");
    createColumnFamily(domainA, ColumnFamilies.ONE);
    domainA.coordinator();

    // when / then a new layered column family in A throws, while B is unaffected
    assertThatThrownBy(() -> createColumnFamily(domainA, ColumnFamilies.THREE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'a'");
    assertThat(createColumnFamily(domainB, ColumnFamilies.TWO)).isNotNull();
  }

  // ------------------------------------------------------------------
  // Per-domain capacity and views
  // ------------------------------------------------------------------

  @Test
  void shouldReportOverCapacityPerDomain() {
    // given a facade whose stores hold at most one byte; it shares the inner database with the
    // default facade, which owns closing it — so this one is deliberately never closed
    final var tiny = new LayeredZeebeDb<>(inner, new LayeredZeebeDbConfig(1, false, 4));
    final LayeredDomain domainA = tiny.registerDomain("a");
    final LayeredDomain domainB = tiny.registerDomain("b");
    final ColumnFamily<DbLong, DbLong> oneInA =
        tiny.createColumnFamily(ColumnFamilies.ONE, domainA.context(), new DbLong(), new DbLong());
    tiny.createColumnFamily(ColumnFamilies.TWO, domainB.context(), new DbLong(), new DbLong());

    // when only domain A pins more bytes than the budget
    domainA.context().runInTransaction(() -> put(oneInA, 1, -1));

    // then the signal is per domain, and the facade-wide signal aggregates over all domains
    assertThat(domainA.overCapacity()).isTrue();
    assertThat(domainB.overCapacity()).isFalse();
    assertThat(tiny.overCapacity()).isTrue();
  }

  @Test
  void shouldPublishOnlyOwnStoresThroughDomainViewPublisher() {
    // given both domains committed writes and built their coordinators
    final LayeredDomain domainA = layered.registerDomain("a");
    final LayeredDomain domainB = layered.registerDomain("b");
    final ColumnFamily<DbLong, DbLong> oneInA = createColumnFamily(domainA, ColumnFamilies.ONE);
    final ColumnFamily<DbLong, DbLong> twoInB = createColumnFamily(domainB, ColumnFamilies.TWO);
    domainA.context().runInTransaction(() -> put(oneInA, 1, 100));
    domainB.context().runInTransaction(() -> put(twoInB, 1, 200));

    // when each coordinator freezes its stores into a published view
    domainA.coordinator().freezeAll(1);
    domainB.coordinator().freezeAll(1);

    // then each domain's publisher delivers a view over exactly that domain's stores
    final ReadOnlyView viewA = domainA.viewPublisher().acquireLatest();
    try {
      final var readerA =
          new LayeredViewReader<>(viewA, ColumnFamilies.ONE.name(), new DbLong(), new DbLong());
      final DbLong key = new DbLong();
      key.wrapLong(1);
      assertThat(readerA.get(key).getValue()).isEqualTo(100);
      assertThatThrownBy(() -> viewA.get(ColumnFamilies.TWO.name(), new byte[8]))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown store");
    } finally {
      domainA.viewPublisher().release(viewA);
    }

    final ReadOnlyView viewB = domainB.viewPublisher().acquireLatest();
    try {
      final var readerB =
          new LayeredViewReader<>(viewB, ColumnFamilies.TWO.name(), new DbLong(), new DbLong());
      final DbLong key = new DbLong();
      key.wrapLong(1);
      assertThat(readerB.get(key).getValue()).isEqualTo(200);
      assertThatThrownBy(() -> viewB.get(ColumnFamilies.ONE.name(), new byte[8]))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown store");
    } finally {
      domainB.viewPublisher().release(viewB);
    }
  }

  // ------------------------------------------------------------------
  // Default-domain convenience and pass-through coexistence
  // ------------------------------------------------------------------

  @Test
  void shouldBackLayeredContextByEngineDomain() {
    // given the engine domain registered explicitly
    final LayeredDomain engine = layered.registerDomain(LayeredZeebeDb.DEFAULT_DOMAIN_NAME);
    createColumnFamily(engine, ColumnFamilies.ONE);

    // when / then the convenience surface resolves to the very same domain
    assertThat(layered.layeredContext()).isSameAs(engine.context());
    assertThat(layered.coordinator()).isSameAs(engine.coordinator());
  }

  @Test
  void shouldRegisterEngineDomainImplicitlyThroughLayeredContext() {
    // given the convenience surface was used first
    layered.layeredContext();

    // when / then the default name is taken like any explicitly registered one
    assertThatThrownBy(() -> layered.registerDomain(LayeredZeebeDb.DEFAULT_DOMAIN_NAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(LayeredZeebeDb.DEFAULT_DOMAIN_NAME);
  }

  @Test
  void shouldServePassThroughWritesAlongsideDomains() {
    // given two domains and a plain pass-through writer on one of their column families
    final LayeredDomain domainA = layered.registerDomain("a");
    final LayeredDomain domainB = layered.registerDomain("b");
    final ColumnFamily<DbLong, DbLong> oneInA = createColumnFamily(domainA, ColumnFamilies.ONE);
    final ColumnFamily<DbLong, DbLong> twoInB = createColumnFamily(domainB, ColumnFamilies.TWO);
    final TransactionContext passThroughContext = layered.createContext();
    final ColumnFamily<DbLong, DbLong> passThroughOne =
        layered.createColumnFamily(
            ColumnFamilies.ONE, passThroughContext, new DbLong(), new DbLong());

    // when the pass-through writer commits through its own transaction
    passThroughContext.runInTransaction(() -> put(passThroughOne, 7, 77));

    // then it wrote straight to RocksDB, domain A reads it through its delegate, and domain B's
    // store is untouched
    assertThat(passThroughGet(ColumnFamilies.ONE, 7)).isEqualTo(77);
    assertThat(get(oneInA, 7)).isEqualTo(77);
    assertThat(get(twoInB, 7)).isNull();
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private ColumnFamily<DbLong, DbLong> createColumnFamily(
      final LayeredDomain domain, final ColumnFamilies columnFamily) {
    return layered.createColumnFamily(columnFamily, domain.context(), new DbLong(), new DbLong());
  }

  private Long passThroughGet(final ColumnFamilies columnFamily, final long key) {
    final TransactionContext context = layered.createContext();
    final ColumnFamily<DbLong, DbLong> passThrough =
        layered.createColumnFamily(columnFamily, context, new DbLong(), new DbLong());
    return get(passThrough, key);
  }

  private static void runPersistRound(final LayeredDomain domain, final long watermark)
      throws Exception {
    final var coordinator = domain.coordinator();
    final var round = coordinator.prepareRound(watermark);
    round.persist();
    coordinator.completeRound(round, true);
  }

  private static void put(
      final ColumnFamily<DbLong, DbLong> columnFamily, final long key, final long value) {
    final DbLong dbKey = new DbLong();
    final DbLong dbValue = new DbLong();
    dbKey.wrapLong(key);
    dbValue.wrapLong(value);
    columnFamily.upsert(dbKey, dbValue);
  }

  private static Long get(final ColumnFamily<DbLong, DbLong> columnFamily, final long key) {
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    final DbLong value = columnFamily.get(dbKey);
    return value == null ? null : value.getValue();
  }

  private enum ColumnFamilies implements EnumValue, ScopedColumnFamily {
    DEFAULT,
    ONE,
    TWO,
    THREE;

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
