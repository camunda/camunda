/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.message.TransientPendingMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.util.FileUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Benchmarks the job state alone, without the stream processor, exporter or any command round trip,
 * so a cost of a few microseconds per job is not lost in the noise of an engine-level benchmark.
 * One invocation runs a whole job lifecycle: a job is created and made activatable, activated and
 * then deleted, each in its own transaction like the engine does. The lifecycle leaves the state as
 * it found it, so the backlog stays at {@code backlogSize} activatable jobs of the same type for
 * the whole trial and the result reads as jobs per second.
 */
@Warmup(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 25, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class JobStateLifecycleBenchmark {

  private static final Logger LOG =
      LoggerFactory.getLogger(JobStateLifecycleBenchmark.class.getName());

  private static final String TYPE = "task";
  private static final int PRELOAD_BATCH_SIZE = 1_000;

  /** Activatable jobs of the benchmarked type that are in state before the lifecycle runs. */
  @Param({"0", "10000", "100000"})
  private int backlogSize;

  private Path directory;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableJobState jobState;
  private JobRecord job;
  private long nextKey;

  @Setup
  public void setup() throws Exception {
    directory = Files.createTempDirectory("job-state-lifecycle");
    zeebeDb = DefaultZeebeDbFactory.defaultFactory().createDb(directory.toFile());
    transactionContext = zeebeDb.createContext();
    final var keyGenerator =
        new DbKeyGenerator(Protocol.DEPLOYMENT_PARTITION, zeebeDb, transactionContext);
    jobState =
        new ProcessingDbState(
                Protocol.DEPLOYMENT_PARTITION,
                zeebeDb,
                transactionContext,
                keyGenerator,
                new TransientPendingSubscriptionState(),
                new TransientPendingSubscriptionState(),
                new TransientPendingMessageStartProcessInstanceAskState(),
                new EngineConfiguration(),
                InstantSource.system(),
                ExpressionLanguageMetrics.noop())
            .getJobState();

    job =
        new JobRecord()
            .setRetries(3)
            .setDeadline(256L)
            .setType(TYPE)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    LOG.info("Creating {} activatable jobs...", backlogSize);
    for (long first = 1; first <= backlogSize; first += PRELOAD_BATCH_SIZE) {
      final long start = first;
      final long end = Math.min(first + PRELOAD_BATCH_SIZE, backlogSize + 1L);
      transactionContext.runInTransaction(
          () -> {
            for (long key = start; key < end; key++) {
              createActivatableJob(key);
            }
          });
    }
    nextKey = backlogSize + 1L;
    LOG.info("Setup complete with {} activatable jobs.", backlogSize);
  }

  @TearDown
  public void tearDown() throws Exception {
    zeebeDb.close();
    FileUtil.deleteFolderIfExists(directory);
  }

  @Benchmark
  public long measureJobLifecycleThroughput() {
    final long key = nextKey++;

    transactionContext.runInTransaction(() -> createActivatableJob(key));
    transactionContext.runInTransaction(() -> jobState.activate(key, job));
    transactionContext.runInTransaction(() -> jobState.delete(key, job));

    return key;
  }

  private void createActivatableJob(final long key) {
    jobState.insertJobRecordActivatable(key, job);
    jobState.makeJobActivatableByPriority(
        job.getTypeBuffer(), key, job.getTenantId(), job.getPriority());
  }

  @JMHTest("measureJobLifecycleThroughput")
  void shouldMeasureJobLifecycle(final JMHTestCase testCase) {
    testCase.run();
  }
}
