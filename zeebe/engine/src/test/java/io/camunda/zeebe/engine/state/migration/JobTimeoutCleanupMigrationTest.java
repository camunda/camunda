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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobTimeoutCleanupMigrationTest {

  final JobTimeoutCleanupMigration jobTimeoutCleanupMigration = new JobTimeoutCleanupMigration();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;

  private final JobRecordValue jobRecordToRead = new JobRecordValue();
  private DbLong jobKey;
  private DbForeignKey<DbLong> fkJob;
  private ColumnFamily<DbLong, JobRecordValue> jobsColumnFamily;

  private DbLong deadlineKey;
  private DbCompositeKey<DbLong, DbForeignKey<DbLong>> deadlineJobKey;
  private ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil> deadlinesColumnFamily;

  @BeforeEach
  public void setup() {
    jobKey = new DbLong();
    fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    jobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS, transactionContext, jobKey, jobRecordToRead);

    deadlineKey = new DbLong();
    deadlineJobKey = new DbCompositeKey<>(deadlineKey, fkJob);
    deadlinesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_DEADLINES, transactionContext, deadlineJobKey, DbNil.INSTANCE);

    jobKey.wrapLong(1);
  }

  @Test
  public void afterCleanupValidTimeoutIsStillPresent() {
    // given
    final int deadline = 123;
    jobsColumnFamily.upsert(jobKey, createJobRecordValue(deadline));
    deadlineKey.wrapLong(deadline);
    deadlinesColumnFamily.upsert(deadlineJobKey, DbNil.INSTANCE);

    // when
    jobTimeoutCleanupMigration.runMigration(
        new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

    // then
    assertThat(deadlinesColumnFamily.exists(deadlineJobKey)).isTrue();
  }

  @Test
  public void afterCleanupOrphanedTimeoutIsDeleted() {
    // given
    jobsColumnFamily.upsert(jobKey, new JobRecordValue());
    deadlineKey.wrapLong(123);
    deadlinesColumnFamily.upsert(deadlineJobKey, DbNil.INSTANCE);
    jobsColumnFamily.deleteExisting(jobKey);

    // when
    jobTimeoutCleanupMigration.runMigration(
        new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

    // then
    assertThat(deadlinesColumnFamily.exists(deadlineJobKey)).isFalse();
  }

  @Test
  public void afterCleanupTimeoutWithNonMatchingDeadlineIsDeleted() {
    // given
    final int firstDeadline = 123;
    final int secondDeadline = 456;
    jobsColumnFamily.upsert(jobKey, createJobRecordValue(secondDeadline));
    deadlineKey.wrapLong(firstDeadline);
    deadlinesColumnFamily.upsert(deadlineJobKey, DbNil.INSTANCE);
    deadlineKey.wrapLong(secondDeadline);
    deadlinesColumnFamily.upsert(deadlineJobKey, DbNil.INSTANCE);

    // when
    jobTimeoutCleanupMigration.runMigration(
        new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

    // then
    deadlineKey.wrapLong(firstDeadline);
    assertThat(deadlinesColumnFamily.exists(deadlineJobKey)).isFalse();
    deadlineKey.wrapLong(secondDeadline);
    assertThat(deadlinesColumnFamily.exists(deadlineJobKey)).isTrue();
  }

  private static JobRecordValue createJobRecordValue(final long deadline) {
    final JobRecordValue jobRecordValue = new JobRecordValue();
    jobRecordValue.setRecordWithoutVariables(new JobRecord().setDeadline(deadline));
    return jobRecordValue;
  }
}
