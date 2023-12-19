/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.platform.commons.util.ExceptionUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Warmup(iterations = 50, time = 1)
@Measurement(iterations = 50, time = 1)
@Fork(
    value = 1,
    jvmArgs = {"-Xmx4g", "-Xms4g"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class JobBackoffRestoreMigrationPerformanceTest {

  public static final Logger LOG =
      LoggerFactory.getLogger(JobBackoffRestoreMigrationPerformanceTest.class.getName());

  private MutableJobState jobState;
  private ZeebeDb zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;
  private long count;

  @Benchmark
  public long measureMigrationTime() {
    final var startTime = System.currentTimeMillis();
    jobState.restoreBackoff();
    final var stopTime = System.currentTimeMillis();
    return stopTime - startTime;
  }

  @JMHTest("measureMigrationTime")
  void shouldProcessWithinExpectedDeviation(final JMHTestCase testCase) {
    final var referenceScore = 4;

    // when
    final var assertResult = testCase.run();

    // then
    assertResult.isWithinDeviation(referenceScore, 0.15);
  }

  @Setup
  public void setup() {
    createProcessingState();
    jobState = processingState.getJobState();
    final DbLong jobKey = new DbLong();
    final DbForeignKey<DbLong> fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);

    final DbLong backoffKey = new DbLong();
    final DbCompositeKey<DbLong, DbForeignKey<DbLong>> backoffJobKey =
        new DbCompositeKey<>(backoffKey, fkJob);
    final ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil> backoffColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_BACKOFF, transactionContext, backoffJobKey, DbNil.INSTANCE);

    final int maxJobCount = 200_000;

    LOG.info("Creating {} jobs, please hold the line...", maxJobCount);
    for (int i = 0; i < maxJobCount; i++) {
      final JobRecord record = createJobRecord();
      jobKey.wrapLong(i + 1);
      jobState.create(jobKey.getValue(), record);
      jobState.fail(jobKey.getValue(), record);
      backoffKey.wrapLong(record.getRecurringTime());
      backoffColumnFamily.deleteExisting(backoffJobKey);
      count++;
    }
    LOG.info("Created {} jobs.", count);
  }

  private static JobRecord createJobRecord() {
    return new JobRecord()
        .setType("test")
        .setRetries(3)
        .setRetryBackoff(1000)
        .setRecurringTime(System.currentTimeMillis() + (long) 1000);
  }

  private void createProcessingState() {
    final var factory = DefaultZeebeDbFactory.defaultFactory();
    try {
      final Path tempFolder = Files.createTempDirectory(null);
      zeebeDb = factory.createDb(tempFolder.toFile());
      transactionContext = zeebeDb.createContext();
      final var keyGenerator =
          new DbKeyGenerator(Protocol.DEPLOYMENT_PARTITION, zeebeDb, transactionContext);
      processingState =
          new ProcessingDbState(
              Protocol.DEPLOYMENT_PARTITION,
              zeebeDb,
              transactionContext,
              keyGenerator,
              new TransientPendingSubscriptionState(),
              new TransientPendingSubscriptionState(),
              new EngineConfiguration());
    } catch (final Exception e) {
      ExceptionUtils.throwAsUncheckedException(e);
    }
  }
}
