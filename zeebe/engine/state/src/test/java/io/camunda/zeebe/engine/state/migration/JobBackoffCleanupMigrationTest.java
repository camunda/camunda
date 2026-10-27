/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.instance.JobRecordValue;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobBackoffCleanupMigrationTest {

  final JobBackoffCleanupMigration jobBackoffCleanupMigration = new JobBackoffCleanupMigration();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;

  private final JobRecordValue jobRecordToRead = new JobRecordValue();
  private DbLong jobKey;
  private ColumnFamily<DbLong, JobRecordValue> jobsColumnFamily;

  private DbLong backoffKey;
  private DbCompositeKey<DbLong, DbForeignKey<DbLong>> backoffJobKey;
  private ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil> backoffColumnFamily;

  @BeforeEach
  public void setup() {
    jobKey = new DbLong();
    final DbForeignKey<DbLong> fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    jobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS, transactionContext, jobKey, jobRecordToRead);

    backoffKey = new DbLong();
    backoffJobKey = new DbCompositeKey<>(backoffKey, fkJob);
    backoffColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_BACKOFF, transactionContext, backoffJobKey, DbNil.INSTANCE);

    jobKey.wrapLong(1);
  }

  // regression test of https://github.com/camunda/camunda/issues/14329
  @Test
  public void shouldCleanOrphanBackoffEntries() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    jobState.fail(jobKey.getValue(), record);
    jobsColumnFamily.deleteExisting(jobKey);

    // when
    jobBackoffCleanupMigration.runMigration(
        new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

    // then
    assertThat(backoffColumnFamily.isEmpty()).isTrue();
  }

  // regression test of https://github.com/camunda/camunda/issues/14329
  @Test
  public void shouldNotCleanUpFailedJobs() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    jobState.fail(jobKey.getValue(), record);

    // when
    jobBackoffCleanupMigration.runMigration(
        new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
  }

  // regression test of https://github.com/camunda/camunda/issues/14329
  @Test
  public void shoulCleanDuplicatedBackoffEntries() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    jobState.fail(jobKey.getValue(), record);

    // second fail will cause duplicate entry and orphan the first backoff
    record.setRecurringTime(System.currentTimeMillis() + 1001);
    jobState.fail(jobKey.getValue(), record);

    // when
    jobBackoffCleanupMigration.runMigration(
        new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
    final var keys = new ArrayList<DbCompositeKey<DbLong, DbForeignKey<DbLong>>>();
    backoffColumnFamily.forEach((k, v) -> keys.add(k));
    assertThat(keys).hasSize(1);
    assertThat(keys)
        .extracting(DbCompositeKey::second)
        .extracting(DbForeignKey::inner)
        .contains(jobKey);
  }

  private static JobRecord createJobRecord(final long retryBackoff) {
    return new JobRecord()
        .setType("test")
        .setRetries(3)
        .setRetryBackoff(retryBackoff)
        .setRecurringTime(System.currentTimeMillis() + retryBackoff);
  }
}
