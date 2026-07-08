/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.suspension;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Track (b), reframed — the always-on worst-case suspension-lookup tax added by the suspend/resume
 * POC (#56552).
 *
 * <p>{@code BpmnStreamProcessor.processRecord} now runs {@code
 * suspensionState.isSuspended(processInstanceKey)} for bufferable element commands. This benchmark
 * isolates that lookup's cost — a single point-lookup on the {@code SUSPENDED_PROCESS_INSTANCES}
 * column family — and sweeps it against the CF size, for both the hit (instance is suspended) and
 * miss (the common case: nothing is suspended) outcomes. The miss case is the one that taxes every
 * cluster that never uses the feature.
 *
 * <p>Rather than depend on the whole engine, this reproduces exactly what {@code
 * DbSuspensionState.isSuspended} does: {@code ColumnFamily<DbLong, DbNil>.exists(wrapLong(key))}
 * over {@code ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES}, on a real RocksDB-backed {@link
 * ZeebeDb} configured the way the broker configures it (not RocksDB defaults).
 *
 * <p><b>Fidelity to the engine hot path — the thing that makes or breaks this number:</b>
 *
 * <ul>
 *   <li><b>Transaction stays open.</b> {@code ColumnFamily.exists()} funnels through {@code
 *       ensureInOpenTransaction -> TransactionContext.runInTransaction}, which opens <em>and
 *       commits a fresh RocksDB transaction per call</em> when none is already open — an
 *       allocation-heavy path that does NOT reflect the engine, where the lookup runs inside the
 *       already-open per-processing-cycle transaction. We open one transaction in {@code @Setup}
 *       (via {@code getCurrentTransaction()}) and never commit it, so every measured {@code
 *       exists()} takes the cheap reuse path — the real per-command cost. (A prior Track-b pass
 *       reported ~566 ns for a miss, which is ~3–5x too high for a warm bloom-filtered lookup and
 *       is consistent with per-call transaction creation; this setup is designed to confirm or
 *       refute that.)
 *   <li><b>Allocation-free steady state.</b> The key view ({@link DbLong}) is reused; each op only
 *       {@code wrapLong}s the next key, exactly like the engine. No per-op object allocation.
 *   <li><b>Rotating keys.</b> Probe keys rotate through a precomputed array (power-of-two size,
 *       masked index) rather than hammering one hot key, so the block cache / bloom filter are
 *       exercised realistically rather than trivially.
 *   <li><b>Blackholed result</b>, {@code @Threads(1)} (the engine processes on a single thread per
 *       partition), consistency checks off (production-like).
 * </ul>
 *
 * <p>Interpret the output as a fraction of the per-command processing budget and as core-time at
 * peak throughput — not as a raw nanosecond scare number.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
// agrona's UnsafeApi (pulled in by zeebe-db / RocksDB access) needs jdk.internal.misc exported,
// the same way the broker JVM opens it at startup; JMH forks a clean JVM so append it here.
@Fork(
    value = 2,
    jvmArgsAppend = {"--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@Threads(1)
public class SuspensionLookupBenchmark {

  @Benchmark
  public boolean isSuspended(final DbState state) {
    return state.isSuspended(state.nextProbeKey());
  }

  @Benchmark
  public void isSuspendedBlackholed(final DbState state, final Blackhole bh) {
    bh.consume(state.isSuspended(state.nextProbeKey()));
  }

  @State(Scope.Benchmark)
  public static class DbState {

    /** Number of entries pre-loaded into SUSPENDED_PROCESS_INSTANCES before measuring. */
    @Param({"1000", "10000", "100000", "1000000"})
    public int cfSize;

    /** hit = probe keys that ARE suspended; miss = probe keys that are NOT (the always-on case). */
    @Param({"hit", "miss"})
    public String outcome;

    private static final int PROBE_COUNT = 4096;
    private static final int PROBE_MASK = PROBE_COUNT - 1;
    private static final int POPULATE_BATCH = 25_000;

    private Path dbDir;
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private TransactionContext txContext;
    private ColumnFamily<DbLong, DbNil> suspendedColumnFamily;
    private final DbLong keyView = new DbLong();

    private long[] probeKeys;
    private int probeIndex;

    @Setup(Level.Trial)
    public void setup() throws Exception {
      dbDir = Files.createTempDirectory("suspension-lookup-bench");

      // Broker-style RocksDB config, not the bare defaults (mirrors DefaultZeebeDbFactory but with
      // consistency checks off, since those are test-only overhead not present in the hot path).
      final var rocksDbConfiguration = new RocksDbConfiguration();
      rocksDbConfiguration.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
      rocksDbConfiguration.setMemoryLimit(512 * 1024 * 1024);
      final var factory =
          new ZeebeRocksDbFactory<ZbColumnFamilies>(
              rocksDbConfiguration,
              new ConsistencyChecksSettings(false, false),
              new AccessMetricsConfiguration(Kind.NONE),
              SimpleMeterRegistry::new);

      zeebeDb = factory.createDb(dbDir.toFile());
      txContext = zeebeDb.createContext();
      suspendedColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES, txContext, keyView, DbNil.INSTANCE);

      // Populate keys [0, cfSize) as suspended, committing in batches so the write batch never
      // holds the whole CF in memory (matters at 1M).
      for (int batchStart = 0; batchStart < cfSize; batchStart += POPULATE_BATCH) {
        final int start = batchStart;
        final int end = Math.min(batchStart + POPULATE_BATCH, cfSize);
        txContext.runInTransaction(
            () -> {
              for (int k = start; k < end; k++) {
                keyView.wrapLong(k);
                suspendedColumnFamily.upsert(keyView, DbNil.INSTANCE);
              }
            });
      }

      // Precompute rotating probe keys.
      //  - hit:  keys inside [0, cfSize) -> present
      //  - miss: keys inside [cfSize, 2*cfSize) -> absent (never inserted)
      probeKeys = new long[PROBE_COUNT];
      final boolean hit = "hit".equals(outcome);
      for (int i = 0; i < PROBE_COUNT; i++) {
        // spread probes across the whole populated range rather than clustering
        final long spread = (long) i * Math.max(1, cfSize / PROBE_COUNT);
        probeKeys[i] = hit ? (spread % cfSize) : (cfSize + (spread % cfSize));
      }

      // Open one long-lived transaction so every measured exists() reuses it (cheap path),
      // exactly as the engine's lookup runs inside the open per-cycle transaction. Populate above
      // already committed, so this read transaction sees all inserted keys.
      txContext.getCurrentTransaction();
    }

    boolean isSuspended(final long processInstanceKey) {
      keyView.wrapLong(processInstanceKey);
      return suspendedColumnFamily.exists(keyView);
    }

    long nextProbeKey() {
      final long key = probeKeys[probeIndex & PROBE_MASK];
      probeIndex++;
      return key;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
      if (zeebeDb != null) {
        zeebeDb.close();
      }
      if (dbDir != null) {
        Files.walk(dbDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }
}
