/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources.RuntimeInfo;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.metrics.RocksDbTickerMetricsDoc;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the collapsed single-scan path in {@link DbJobStore#visitActivatableJobs} that is taken
 * once {@code isLegacyCfDrained} is set, using RocksDB ticker metrics to assert on the number of
 * seeks and skipped internal keys per poll.
 *
 * <p>Ticker metrics are whole-DB counters, so each test polls only within its own {@code
 * JobStateImpl}/{@code ZeebeDb} instance and takes before/after deltas; no concurrent DB activity
 * runs during a measured poll.
 */
final class DbJobStoreActivationScanTest {

  private static final String TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  @TempDir File dir;
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private ZeebeDb<ZbColumnFamilies> db;
  private JobStateImpl jobState;

  @BeforeEach
  void setUp() {
    final var config = new RocksDbConfiguration();
    config.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    config.setMemoryLimit(32 * 1024 * 1024);
    config.setStatisticsEnabled(true);
    final var factory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            config,
            new ConsistencyChecksSettings(false, false),
            new AccessMetricsConfiguration(Kind.NONE),
            () -> registry,
            RocksDbResources.of(config, new RuntimeInfo(1)));
    db = factory.createDb(dir);
    final TransactionContext ctx = db.createContext();
    jobState = new JobStateImpl(new DbJobStore(db, ctx));
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldUseOneSeekForDrainedIdlePoll() {
    // given: a type with no jobs at all; the first poll drains (and sets) the flag
    final DirectBuffer type = wrapString("idle");
    poll(type, 10);

    // when
    final double seeksBefore = ticker(RocksDbTickerMetricsDoc.NUMBER_DB_SEEK);
    poll(type, 10);
    final double seekDelta = ticker(RocksDbTickerMetricsDoc.NUMBER_DB_SEEK) - seeksBefore;

    // then: one scan instead of the pre-fix two (Phase 1 + Phase 3)
    assertThat(seekDelta).isEqualTo(1);
  }

  @Test
  void shouldUseOneSeekAndHalvedSkipsForDrainedTombstonePoll() {
    // given: the flag is drained, then 100 priority-0 jobs are created and 90 are consumed
    // (tombstoned) without compaction
    final DirectBuffer type = wrapString("busy");
    poll(type, 10); // drains the flag (no jobs yet, CF globally empty)
    for (long k = 1; k <= 100; k++) {
      createJob(k, type, 0);
    }

    for (long k = 1; k <= 90; k++) {
      final var record = jobState.getJob(k);
      jobState.makeJobNotActivatable(k, record);
    }
    final double seeksAfterConsume = ticker(RocksDbTickerMetricsDoc.NUMBER_DB_SEEK);
    final double skipsAfterConsume = ticker(RocksDbTickerMetricsDoc.NUMBER_ITER_SKIP);

    // when
    final List<Long> keys = poll(type, 10);
    final double seekDelta = ticker(RocksDbTickerMetricsDoc.NUMBER_DB_SEEK) - seeksAfterConsume;
    final double skipDelta = ticker(RocksDbTickerMetricsDoc.NUMBER_ITER_SKIP) - skipsAfterConsume;

    // then: single scan (1 seek), and bounded skipped tombstone keys of the single-scan path
    assertThat(keys).containsExactly(91L, 92L, 93L, 94L, 95L, 96L, 97L, 98L, 99L, 100L);
    assertThat(seekDelta).isEqualTo(1);
    assertThat(skipDelta).isLessThanOrEqualTo(200);
  }

  @Test
  void shouldPreserveOrderAcrossMixedPrioritiesWhenDrained() {
    // given: drain the flag first, then seed mixed positive/zero/negative priorities
    final DirectBuffer type = wrapString("mixed");
    poll(type, 10); // drains the flag
    createJob(11L, type, 10);
    createJob(10L, type, 5);
    createJob(20L, type, 0);
    createJob(21L, type, -5);

    // when
    final List<Long> keys = poll(type, Integer.MAX_VALUE);

    // then: (priority DESC, jobKey ASC), identical to the pre-fix phase 1 + phase 3 order
    assertThat(keys).containsExactly(11L, 10L, 20L, 21L);
  }

  @Test
  void shouldFilterByTenantWhenDrained() {
    // given
    final DirectBuffer type = wrapString("tenant-scoped");
    poll(type, 10); // drains the flag
    final String otherTenant = "other-tenant";
    createJob(1L, type, 0, TENANT);
    createJob(2L, type, 0, otherTenant);

    // when/then: only the queried tenant's job is returned
    assertThat(poll(type, Integer.MAX_VALUE, List.of(TENANT))).containsExactly(1L);
  }

  @Test
  void shouldStopMidScanWhenBatchIsFullWhileDrained() {
    // given
    final DirectBuffer type = wrapString("batch-limited");
    poll(type, 10); // drains the flag
    createJob(1L, type, 10);
    createJob(2L, type, 5);
    createJob(3L, type, 0);

    // when: the batch fills after the first job
    final List<Long> keys = poll(type, 1);

    // then
    assertThat(keys).containsExactly(1L);
  }

  private void createJob(final long key, final DirectBuffer type, final int priority) {
    createJob(key, type, priority, TENANT);
  }

  private void createJob(
      final long key, final DirectBuffer type, final int priority, final String tenantId) {
    final JobRecord record =
        new JobRecord().setType(type).setTenantId(tenantId).setPriority(priority).setRetries(1);
    jobState.insertJobRecordActivatable(key, record);
    jobState.makeJobActivatableByPriority(type, key, tenantId, priority);
  }

  private List<Long> poll(final DirectBuffer type, final int max) {
    return poll(type, max, List.of(TENANT, "other-tenant"));
  }

  private List<Long> poll(final DirectBuffer type, final int max, final List<String> tenantIds) {
    final List<Long> keys = new ArrayList<>();
    jobState.forEachActivatableJobs(
        type,
        tenantIds,
        (k, j) -> {
          keys.add(k);
          return keys.size() < max;
        });
    return keys;
  }

  private double ticker(final RocksDbTickerMetricsDoc doc) {
    db.exportMetrics();
    final var gauge = registry.find(doc.getName()).gauge();
    return gauge == null ? 0d : gauge.value();
  }
}
