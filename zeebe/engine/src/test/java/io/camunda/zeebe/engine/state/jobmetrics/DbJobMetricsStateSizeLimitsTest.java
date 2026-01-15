/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import static io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsState.META_SIZE_LIMITS_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DbJobMetricsStateSizeLimitsTest {

  private Path tempFolder;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;

  @BeforeEach
  void setUp() throws Exception {
    final var factory = DefaultZeebeDbFactory.defaultFactory();
    tempFolder = Files.createTempDirectory(null);
    zeebeDb = factory.createDb(tempFolder.toFile());
    transactionContext = zeebeDb.createContext();
  }

  private List<StatusMetrics[]> collectMetrics(final MutableJobMetricsState state) {
    final List<StatusMetrics[]> result = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> result.add(metrics));
    return result;
  }

  @Nested
  class JobTypeLengthLimits {

    @Test
    void shouldRejectJobTypeExceedingMaxLength() {
      // given
      final int maxJobTypeLength = 10;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(maxJobTypeLength)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(10000));

      final String tooLongJobType = "a".repeat(maxJobTypeLength + 1);

      // when
      state.incrementMetric(
          new JobRecord().setType(tooLongJobType).setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).isEmpty();
      assertThat(state.isIncompleteBatch()).isTrue();
      assertThat(state.getMetadata(META_SIZE_LIMITS_EXCEEDED)).isEqualTo(1L);
    }

    @Test
    void shouldAcceptJobTypeAtMaxLength() {
      // given
      final int maxJobTypeLength = 10;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(maxJobTypeLength)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(10000));
      final String exactLengthJobType = "a".repeat(maxJobTypeLength);

      // when
      state.incrementMetric(
          new JobRecord().setType(exactLengthJobType).setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).hasSize(1);
      assertThat(state.isIncompleteBatch()).isFalse();
    }

    @Test
    void shouldAcceptJobTypeBelowMaxLength() {
      // given
      final int maxJobTypeLength = 10;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(maxJobTypeLength)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(10000));

      final String shortJobType = "a".repeat(maxJobTypeLength - 1);

      // when
      state.incrementMetric(
          new JobRecord().setType(shortJobType).setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).hasSize(1);
      assertThat(state.isIncompleteBatch()).isFalse();
    }
  }

  @Nested
  class TenantIdLengthLimits {

    @Test
    void shouldRejectTenantIdExceedingMaxLength() {
      // given
      final int maxTenantIdLength = 10;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(maxTenantIdLength)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(10000));

      final String tooLongTenantId = "t".repeat(maxTenantIdLength + 1);

      // when
      state.incrementMetric(
          new JobRecord().setType("jobType").setTenantId(tooLongTenantId).setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).isEmpty();
      assertThat(state.isIncompleteBatch()).isTrue();
    }

    @Test
    void shouldAcceptTenantIdAtMaxLength() {
      // given
      final int maxTenantIdLength = 10;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(maxTenantIdLength)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(10000));

      final String exactLengthTenantId = "t".repeat(maxTenantIdLength);

      // when
      state.incrementMetric(
          new JobRecord().setType("jobType").setTenantId(exactLengthTenantId).setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).hasSize(1);
      assertThat(state.isIncompleteBatch()).isFalse();
    }
  }

  @Nested
  class WorkerNameLengthLimits {

    @Test
    void shouldRejectWorkerNameExceedingMaxLength() {
      // given
      final int maxWorkerNameLength = 10;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(maxWorkerNameLength)
                  .setMaxUniqueJobMetricsKeys(10000));

      final String tooLongWorkerName = "w".repeat(maxWorkerNameLength + 1);

      // when
      state.incrementMetric(
          new JobRecord().setType("jobType").setTenantId("tenant").setWorker(tooLongWorkerName),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).isEmpty();
      assertThat(state.isIncompleteBatch()).isTrue();
    }

    @Test
    void shouldAcceptWorkerNameAtMaxLength() {
      // given
      final int maxWorkerNameLength = 10;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(maxWorkerNameLength)
                  .setMaxUniqueJobMetricsKeys(10000));

      final String exactLengthWorkerName = "w".repeat(maxWorkerNameLength);

      // when
      state.incrementMetric(
          new JobRecord().setType("jobType").setTenantId("tenant").setWorker(exactLengthWorkerName),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).hasSize(1);
      assertThat(state.isIncompleteBatch()).isFalse();
    }
  }

  @Nested
  class UniqueKeysLimits {

    @Test
    void shouldRejectWhenMaxUniqueKeysExceeded() {
      // given
      final int maxUniqueKeys = 3;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(maxUniqueKeys));

      // when - add exactly maxUniqueKeys entries
      state.incrementMetric(
          new JobRecord().setType("jobType1").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType2").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType3").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then - should have 3 entries
      assertThat(collectMetrics(state)).hasSize(3);
      assertThat(state.isIncompleteBatch()).isFalse();

      // when - try to add one more unique key
      state.incrementMetric(
          new JobRecord().setType("jobType4").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then - should still have 3 entries and be marked as incomplete
      assertThat(collectMetrics(state)).hasSize(3);
      assertThat(state.isIncompleteBatch()).isTrue();
    }

    @Test
    void shouldAllowIncrementingExistingKeyAfterLimitReached() {
      // given
      final int maxUniqueKeys = 2;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(maxUniqueKeys));

      state.incrementMetric(
          new JobRecord().setType("jobType1").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType2").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      assertThat(collectMetrics(state)).hasSize(2);

      // when - try to add a new key (should be rejected)
      state.incrementMetric(
          new JobRecord().setType("jobType3").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then - limit exceeded, no more increments allowed
      assertThat(collectMetrics(state)).hasSize(2);
      assertThat(state.isIncompleteBatch()).isTrue();
    }

    @Test
    void shouldNotCountIncrementToExistingKeyAsNewKey() {
      // given
      final int maxUniqueKeys = 2;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(maxUniqueKeys));

      // when - add 2 unique keys, then increment existing one multiple times
      state.incrementMetric(
          new JobRecord().setType("jobType1").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType2").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType1").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.COMPLETED);
      state.incrementMetric(
          new JobRecord().setType("jobType2").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.FAILED);

      // then - still only 2 unique keys, not incomplete
      final List<StatusMetrics[]> metrics = collectMetrics(state);
      assertThat(metrics).hasSize(2);
      assertThat(state.isIncompleteBatch()).isFalse();
    }

    @Test
    void shouldAllowAddingKeysUpToLimit() {
      // given
      final int maxUniqueKeys = 5;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(100)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(maxUniqueKeys));

      // when - add exactly maxUniqueKeys entries with different combinations
      state.incrementMetric(
          new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType1").setTenantId("tenant2").setWorker("worker1"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker2"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType2").setTenantId("tenant1").setWorker("worker1"),
          JobMetricsExportState.CREATED);
      state.incrementMetric(
          new JobRecord().setType("jobType2").setTenantId("tenant2").setWorker("worker2"),
          JobMetricsExportState.CREATED);

      // then - should have exactly 5 entries
      assertThat(collectMetrics(state)).hasSize(5);
      assertThat(state.isIncompleteBatch()).isFalse();
    }
  }

  @Nested
  class IncompleteBatchBehavior {

    @Test
    void shouldSkipAllSubsequentIncrementsAfterSizeLimitExceeded() {
      // given
      final int maxJobTypeLength = 5;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(maxJobTypeLength)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(2));

      // when - first add a valid metric, then exceed limit, then try to add more valid metrics
      state.incrementMetric(
          new JobRecord().setType("valid").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      assertThat(collectMetrics(state)).hasSize(1);

      // exceed the limit
      state.incrementMetric(
          new JobRecord().setType("toolong").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      assertThat(state.isIncompleteBatch()).isTrue();

      // try to add another valid metric
      state.incrementMetric(
          new JobRecord().setType("ok").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);

      // then - no new metrics should be added after limit exceeded
      assertThat(collectMetrics(state)).hasSize(2);
    }

    @Test
    void shouldResetIncompleteBatchFlagAfterCleanUp() {
      // given
      final int maxJobTypeLength = 5;
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(maxJobTypeLength)
                  .setMaxTenantIdLength(100)
                  .setMaxWorkerNameLength(100)
                  .setMaxUniqueJobMetricsKeys(2));

      // exceed limit
      state.incrementMetric(
          new JobRecord().setType("toolong").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      assertThat(state.isIncompleteBatch()).isTrue();

      // when - clean up
      state.cleanUp();

      // then - should be able to add metrics again
      assertThat(state.isIncompleteBatch()).isFalse();
      state.incrementMetric(
          new JobRecord().setType("short").setTenantId("tenant").setWorker("worker"),
          JobMetricsExportState.CREATED);
      assertThat(collectMetrics(state)).hasSize(1);
    }

    @Test
    void shouldMarkIncompleteBatchOnFirstViolation() {
      // given
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(5)
                  .setMaxTenantIdLength(5)
                  .setMaxWorkerNameLength(5)
                  .setMaxUniqueJobMetricsKeys(100));

      // when - violate job type limit
      state.incrementMetric(
          new JobRecord().setType("toolong").setTenantId("ten").setWorker("wkr"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(state.isIncompleteBatch()).isTrue();
      assertThat(collectMetrics(state)).isEmpty();
    }
  }

  @Nested
  class MultipleLimitViolations {

    @Test
    void shouldRejectWhenMultipleLimitsExceeded() {
      // given
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(3)
                  .setMaxTenantIdLength(3)
                  .setMaxWorkerNameLength(3)
                  .setMaxUniqueJobMetricsKeys(100));

      // when - exceed all length limits
      state.incrementMetric(
          new JobRecord().setType("toolong").setTenantId("toolong").setWorker("toolong"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).isEmpty();
      assertThat(state.isIncompleteBatch()).isTrue();
    }

    @Test
    void shouldCheckJobTypeLengthFirst() {
      // given
      final MutableJobMetricsState state =
          new DbJobMetricsState(
              zeebeDb,
              transactionContext,
              InstantSource.system(),
              new EngineConfiguration()
                  .setMaxJobTypeLength(3)
                  .setMaxTenantIdLength(3)
                  .setMaxWorkerNameLength(3)
                  .setMaxUniqueJobMetricsKeys(100));

      // when - only job type exceeds
      state.incrementMetric(
          new JobRecord().setType("long").setTenantId("ok").setWorker("ok"),
          JobMetricsExportState.CREATED);

      // then
      assertThat(collectMetrics(state)).isEmpty();
      assertThat(state.isIncompleteBatch()).isTrue();
    }
  }
}
