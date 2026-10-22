/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.state;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks the job queue in {@link DbJobState} on a real RocksDB, to measure the effect of {@code
 * min_tombstones_for_range_conversion}.
 *
 * <p>Jobs behave like queue entries: they are inserted, picked from the head of the activatable
 * column family, and deleted soon after, which leaves a growing run of point tombstones in front of
 * the live entries. One invocation is one cycle at a constant backlog: create a batch of jobs,
 * activate the oldest batch (a seek followed by an iteration over the activatable jobs), and
 * complete the jobs it activated. Each step runs in its own transaction, like a processed command.
 *
 * <p>Everything else the engine does around jobs (records, appliers, log) is left out, so the
 * result isolates the state access cost.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms2G", "-Xmx2G", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class JobQueueTombstoneBenchmark {

  private static final String JOB_TYPE = "benchmark-job";
  private static final long DEADLINE = 1_000_000L;

  /** 0 disables conversion, which is the behavior before range tombstone conversion was added. */
  @Param({"0", "20", "100", "1000"})
  private int minTombstonesForRangeConversion;

  @Param({"10000"})
  private int backlog;

  @Param({"100"})
  private int batchSize;

  private Path dbDirectory;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private DbJobState jobState;
  private long nextKey = 1;
  private long[] pickedKeys;
  private int pickedCount;

  public static void main(final String[] args) throws RunnerException {
    final var options =
        new OptionsBuilder().include(JobQueueTombstoneBenchmark.class.getSimpleName()).build();
    new Runner(options).run();
  }

  @Setup
  public void setup() throws Exception {
    dbDirectory = Files.createTempDirectory("job-queue-benchmark");
    final var columnFamilyOptions = new Properties();
    columnFamilyOptions.setProperty(
        "min_tombstones_for_range_conversion", String.valueOf(minTombstonesForRangeConversion));
    zeebeDb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration().setColumnFamilyOptions(columnFamilyOptions),
                new ConsistencyChecksSettings(),
                new AccessMetricsConfiguration(Kind.NONE),
                SimpleMeterRegistry::new)
            .createDb(dbDirectory.toFile());
    transactionContext = zeebeDb.createContext();
    jobState = new DbJobState(zeebeDb, transactionContext);
    pickedKeys = new long[batchSize];

    // fill the queue so the activated batch is always taken from behind a backlog
    for (int created = 0; created < backlog; created += batchSize) {
      createJobs();
    }
  }

  @TearDown
  public void tearDown() throws Exception {
    zeebeDb.close();
    FileUtil.deleteFolder(dbDirectory);
  }

  @Benchmark
  public long measureQueueCycle() {
    createJobs();
    activateOldestJobs();
    completeActivatedJobs();
    return nextKey;
  }

  private void createJobs() {
    inTransaction(
        () -> {
          for (int i = 0; i < batchSize; i++) {
            final long key = nextKey++;
            final var job = newJob(0);
            jobState.insertJobRecordActivatable(key, job);
            jobState.makeJobActivatableByPriority(
                job.getTypeBuffer(), key, job.getTenantId(), job.getPriority());
          }
        });
  }

  private void activateOldestJobs() {
    pickedCount = 0;
    inTransaction(
        () ->
            jobState.forEachActivatableJobs(
                BufferUtil.wrapString(JOB_TYPE),
                List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER),
                (key, job) -> {
                  pickedKeys[pickedCount++] = key;
                  return pickedCount < batchSize;
                }));
    inTransaction(
        () -> {
          for (int i = 0; i < pickedCount; i++) {
            jobState.activate(pickedKeys[i], newJob(DEADLINE));
          }
        });
  }

  private void completeActivatedJobs() {
    inTransaction(
        () -> {
          for (int i = 0; i < pickedCount; i++) {
            jobState.complete(pickedKeys[i], newJob(DEADLINE));
          }
        });
  }

  private void inTransaction(final Runnable work) {
    final var transaction = transactionContext.getCurrentTransaction();
    try {
      transaction.run(work::run);
      transaction.commit();
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JobRecord newJob(final long deadline) {
    return new JobRecord()
        .setType(JOB_TYPE)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .setRetries(3)
        .setDeadline(deadline);
  }
}
