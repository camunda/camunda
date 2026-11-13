/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.db.writebehind.TestValues.COLUMN_FAMILY;
import static io.camunda.zeebe.db.writebehind.TestValues.commit;
import static io.camunda.zeebe.db.writebehind.TestValues.held;
import static io.camunda.zeebe.db.writebehind.TestValues.key;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class MetricsExportTest {

  private static final String STORE = "test";
  private static final String PREFIX = "zeebe.writebehind.";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private CountingRawDbAccess storage;
  private WriteBehindStore<RawKey, HeldValue, DbValue> store;

  @BeforeEach
  void setUp() {
    storage = new CountingRawDbAccess(new InMemoryRawDbAccess());
    // the delay keeps the commits unflushed until the test asks for the flush
    store =
        new WriteBehindStore<>(
            new RawBackend(storage),
            WriteBehindConfiguration.defaults().withFlushDelay(Duration.ofHours(1)));
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldExportWhatIsCommittedButNotFlushed() {
    // given
    put(1);
    put(2);

    // when
    store.exportMetrics(registry, STORE);

    // then
    assertThat(gauge("pending.transactions")).isEqualTo(2);
    assertThat(gauge("pending.bytes")).isPositive();
    assertThat(gauge("pending.age")).isPositive();
  }

  @Test
  void shouldExportNothingPendingAndTheFlushAfterTheFlush() {
    // given
    put(1);
    store.exportMetrics(registry, STORE);

    // when
    store.awaitFlushed();

    // then
    assertThat(gauge("pending.transactions")).isZero();
    assertThat(gauge("pending.bytes")).isZero();
    assertThat(gauge("pending.age")).isZero();
    final var flush = registry.get(PREFIX + "flush.duration").tag("store", STORE).functionTimer();
    assertThat(flush.count()).isEqualTo(1);
    assertThat(flush.totalTime(TimeUnit.NANOSECONDS)).isPositive();
  }

  @Test
  void shouldExportCountersPerColumnFamilyAsCounters() {
    // given
    put(1);
    store.awaitFlushed();

    // when
    store.exportMetrics(registry, STORE);

    // then
    assertThat(columnFamilyCounter("committed.puts")).isEqualTo(1);
    assertThat(columnFamilyCounter("flushed.puts")).isEqualTo(1);
  }

  @Test
  void shouldKeepExportedCountersLiveWhenExportedAgain() {
    // given
    store.exportMetrics(registry, STORE);
    put(1);

    // when
    store.exportMetrics(registry, STORE);

    // then
    assertThat(columnFamilyCounter("committed.puts")).isEqualTo(1);
    put(2);
    assertThat(columnFamilyCounter("committed.puts")).isEqualTo(2);
  }

  @Test
  void shouldCountWherePointReadsWereAnswered() {
    // given
    put(1);
    store.get(key(1));
    store.awaitFlushed();
    store.get(key(1));
    store.get(key(2));

    // when
    store.exportMetrics(registry, STORE);

    // then
    assertThat(columnFamilyCounter("lookup.dirty.hits")).isEqualTo(1);
    assertThat(columnFamilyCounter("lookup.cache.hits")).isEqualTo(1);
    assertThat(columnFamilyCounter("lookup.storage.gets")).isEqualTo(1);
    assertThat(columnFamilyCounter("lookup.storage.found")).isZero();
    assertThat(columnFamilyCounter("lookup.known.absent")).isZero();
    assertThat(storage.gets()).isEqualTo(1);
  }

  private double gauge(final String name) {
    return registry.get(PREFIX + name).tag("store", STORE).gauge().value();
  }

  private double columnFamilyCounter(final String name) {
    return registry
        .get(PREFIX + name)
        .tag("store", STORE)
        .tag("columnFamily", Long.toString(COLUMN_FAMILY))
        .functionCounter()
        .count();
  }

  private void put(final long id) {
    commit(store, key(id), held(id));
  }
}
