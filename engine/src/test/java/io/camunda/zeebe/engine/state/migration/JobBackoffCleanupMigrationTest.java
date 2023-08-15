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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
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

  @Test
  public void afterCleanupValidTimeoutIsStillPresent() {
    // given
    final int deadline = 123;
    jobsColumnFamily.upsert(jobKey, createJobRecordValue(deadline));
    backoffKey.wrapLong(deadline);
    backoffColumnFamily.upsert(backoffJobKey, DbNil.INSTANCE);

    // when
    jobBackoffCleanupMigration.runMigration(processingState);

    // then
    assertThat(backoffColumnFamily.exists(backoffJobKey)).isTrue();
  }

  @Test
  public void afterCleanupOrphanedBackoffIsDeleted() {
    // given
    jobsColumnFamily.upsert(jobKey, new JobRecordValue());
    backoffKey.wrapLong(123);
    backoffColumnFamily.upsert(backoffJobKey, DbNil.INSTANCE);
    jobsColumnFamily.deleteExisting(jobKey);

    // when
    jobBackoffCleanupMigration.runMigration(processingState);

    // then
    assertThat(backoffColumnFamily.exists(backoffJobKey)).isFalse();
  }

  @Test
  public void afterCleanupTimeoutWithNonMatchingRetryBackoffIsDeleted() {
    // given
    final int firstRetryBackoff = 123;
    final int secondRetryBackoff = 456;
    jobsColumnFamily.upsert(jobKey, createJobRecordValue(secondRetryBackoff));
    backoffKey.wrapLong(firstRetryBackoff);
    backoffColumnFamily.upsert(backoffJobKey, DbNil.INSTANCE);
    backoffKey.wrapLong(secondRetryBackoff);
    backoffColumnFamily.upsert(backoffJobKey, DbNil.INSTANCE);

    // when
    jobBackoffCleanupMigration.runMigration(processingState);

    // then
    backoffKey.wrapLong(firstRetryBackoff);
    assertThat(backoffColumnFamily.exists(backoffJobKey)).isFalse();
    backoffKey.wrapLong(secondRetryBackoff);
    assertThat(backoffColumnFamily.exists(backoffJobKey)).isTrue();
  }

  private static JobRecordValue createJobRecordValue(final long retryBackoff) {
    final JobRecordValue jobRecordValue = new JobRecordValue();
    jobRecordValue.setRecordWithoutVariables(new JobRecord().setRetryBackoff(retryBackoff));
    return jobRecordValue;
  }
}
