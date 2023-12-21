/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import io.camunda.zeebe.engine.state.instance.JobStateValue;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobBackoffRestoreMigrationTest {

  final JobBackoffRestoreMigration jobBackoffRestoreMigration = new JobBackoffRestoreMigration();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;
  private final JobRecordValue jobRecordToRead = new JobRecordValue();
  private DbLong jobKey;
  private ColumnFamily<DbLong, JobRecordValue> jobsColumnFamily;
  private DbLong backoffKey;
  private DbCompositeKey<DbLong, DbForeignKey<DbLong>> backoffJobKey;
  private ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil> backoffColumnFamily;
  private final JobStateValue jobState = new JobStateValue();
  private ColumnFamily<DbForeignKey<DbLong>, JobStateValue> statesJobColumnFamily;

  @BeforeEach
  public void setup() {
    jobKey = new DbLong();
    final DbForeignKey<DbLong> fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    jobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS, transactionContext, jobKey, jobRecordToRead);

    statesJobColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_STATES, transactionContext, fkJob, jobState);

    backoffKey = new DbLong();
    backoffJobKey = new DbCompositeKey<>(backoffKey, fkJob);
    backoffColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_BACKOFF, transactionContext, backoffJobKey, DbNil.INSTANCE);

    jobKey.wrapLong(1);
  }

  // regression test of https://github.com/camunda/zeebe/issues/14329
  @Test
  public void shouldRestoreIfBackoffColumnIsEmpty() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    jobState.fail(jobKey.getValue(), record);
    backoffKey.wrapLong(record.getRecurringTime());
    backoffColumnFamily.deleteExisting(backoffJobKey);

    // when
    assertThat(jobBackoffRestoreMigration.needsToRun(processingState)).isTrue();
    jobBackoffRestoreMigration.runMigration(processingState);

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
  }

  // regression test of https://github.com/camunda/zeebe/issues/14329
  @Test
  public void shouldRestoreIfFailedJobsAreMoreThanBackoffJob() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    jobState.fail(jobKey.getValue(), record);
    backoffKey.wrapLong(record.getRecurringTime());
    backoffColumnFamily.deleteExisting(backoffJobKey);

    jobKey.wrapLong(2);
    final JobRecord backoffRecord = createJobRecord(2000);
    jobState.create(jobKey.getValue(), backoffRecord);
    jobState.fail(jobKey.getValue(), backoffRecord);
    backoffKey.wrapLong(backoffRecord.getRecurringTime());

    // when
    assertThat(jobBackoffRestoreMigration.needsToRun(processingState)).isTrue();
    jobBackoffRestoreMigration.runMigration(processingState);

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
  }

  // regression test of https://github.com/camunda/zeebe/issues/14329
  @Test
  public void shouldDoNothingIfFailedJobsAreTheSameAsBackoff() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    jobState.fail(jobKey.getValue(), record);

    jobKey.wrapLong(2);
    final JobRecord backoffRecord = createJobRecord(2000);
    jobState.create(jobKey.getValue(), backoffRecord);
    jobState.fail(jobKey.getValue(), backoffRecord);

    // when
    assertThat(jobBackoffRestoreMigration.needsToRun(processingState)).isTrue();
    jobBackoffRestoreMigration.runMigration(processingState);

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
  }

  private static JobRecord createJobRecord(final long retryBackoff) {
    return new JobRecord()
        .setType("test")
        .setRetries(3)
        .setRetryBackoff(retryBackoff)
        .setRecurringTime(System.currentTimeMillis() + retryBackoff);
  }
}
