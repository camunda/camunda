/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3.legacy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.JobRecordValue;
import io.camunda.zeebe.engine.state.instance.JobStateValue;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.EnsureUtil;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class LegacyJobState {
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  // key => job record value
  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1914
  private final JobRecordValue jobRecordToRead = new JobRecordValue();
  private final JobRecordValue jobRecordToWrite = new JobRecordValue();

  private final DbLong jobKey;
  private final DbForeignKey<DbLong> fkJob;
  private final ColumnFamily<DbLong, JobRecordValue> jobsColumnFamily;

  // key => job state
  private final JobStateValue jobState = new JobStateValue();
  private final ColumnFamily<DbForeignKey<DbLong>, JobStateValue> statesJobColumnFamily;

  // type => [key]
  private final DbString jobTypeKey;
  private final DbCompositeKey<DbString, DbForeignKey<DbLong>> typeJobKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbForeignKey<DbLong>>, DbNil>
      activatableColumnFamily;

  public LegacyJobState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    jobKey = new DbLong();
    fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    jobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS, transactionContext, jobKey, jobRecordToRead);

    statesJobColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_STATES, transactionContext, fkJob, jobState);

    jobTypeKey = new DbString();
    typeJobKey = new DbCompositeKey<>(jobTypeKey, fkJob);
    activatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_JOB_ACTIVATABLE,
            transactionContext,
            typeJobKey,
            DbNil.INSTANCE);
  }

  public void create(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    createJob(key, record, type);
  }

  private void createJob(final long key, final JobRecord record, final DirectBuffer type) {
    createJobRecord(key, record);
    initializeJobState();
    makeJobActivatable(type, key);
  }

  private void createJobRecord(final long key, final JobRecord record) {
    jobKey.wrapLong(key);
    // do not persist variables in job state
    jobRecordToWrite.setRecordWithoutVariables(record);
    jobsColumnFamily.insert(jobKey, jobRecordToWrite);
  }

  private void makeJobActivatable(final DirectBuffer type, final long key) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);

    jobTypeKey.wrapBuffer(type);

    jobKey.wrapLong(key);
    // Need to upsert here because jobs can be marked as failed (and thus made activatable)
    // without activating them first
    activatableColumnFamily.upsert(typeJobKey, DbNil.INSTANCE);
  }

  private void initializeJobState() {
    jobState.setState(State.ACTIVATABLE);
    statesJobColumnFamily.insert(fkJob, jobState);
  }

  public ColumnFamily<DbCompositeKey<DbString, DbForeignKey<DbLong>>, DbNil>
      getActivatableColumnFamily() {
    return activatableColumnFamily;
  }
}
