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
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.JobRecordValue;
import io.camunda.zeebe.engine.state.instance.JobStateValue;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

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

  // regression test of https://github.com/camunda/camunda/issues/27183
  @Test
  public void shouldNotAddBackoffOfFailedJobWithoutEntry() {
    // given a job that ran out of retries, keeping the recurring time of an earlier backoff, and
    // whose retries were updated without resolving its incident
    createJobInIncidentWithUpdatedRetries();

    // and a failed job that lost its backoff, which only the bug fixed by #15419 could cause
    final MutableJobState jobState = processingState.getJobState();
    jobKey.wrapLong(2);
    final JobRecord backoffRecord = createJobRecord(2000);
    jobState.create(jobKey.getValue(), backoffRecord);
    jobState.fail(jobKey.getValue(), backoffRecord);
    backoffKey.wrapLong(backoffRecord.getRecurringTime());
    backoffColumnFamily.deleteExisting(backoffJobKey);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(jobBackoffRestoreMigration.needsToRun(context)).isTrue();
    jobBackoffRestoreMigration.runMigration(context);

    // then
    assertThat(backoffColumnFamily.isEmpty()).isTrue();
  }

  // regression test of https://github.com/camunda/camunda/issues/14329
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
    assertThat(backoffColumnFamily.count()).isEqualTo(2);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(jobBackoffRestoreMigration.needsToRun(context)).isTrue();
    jobBackoffRestoreMigration.runMigration(context);

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
    assertThat(backoffColumnFamily.count()).isEqualTo(2);
  }

  @Test
  public void shouldNotRestoreJobWithoutRetries() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    record = jobState.updateJobRetries(jobKey.getValue(), 0);
    jobState.fail(jobKey.getValue(), record);
    backoffKey.wrapLong(record.getRecurringTime());

    jobKey.wrapLong(2);
    final JobRecord backoffRecord = createJobRecord(2000);
    jobState.create(jobKey.getValue(), backoffRecord);
    jobState.fail(jobKey.getValue(), backoffRecord);
    assertThat(backoffColumnFamily.count()).isEqualTo(1);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(jobBackoffRestoreMigration.needsToRun(context)).isTrue();
    jobBackoffRestoreMigration.runMigration(context);

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
    assertThat(backoffColumnFamily.count()).isEqualTo(1);
  }

  @Test
  public void shouldRemoveFailedJobFromBackoffColumn() {
    // given
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord record = createJobRecord(1000);
    jobState.create(jobKey.getValue(), record);
    jobState.fail(jobKey.getValue(), record);
    backoffKey.wrapLong(record.getRecurringTime());
    jobState.updateJobRetries(jobKey.getValue(), 0);

    jobKey.wrapLong(2);
    final JobRecord backoffRecord = createJobRecord(2000);
    jobState.create(jobKey.getValue(), backoffRecord);
    jobState.fail(jobKey.getValue(), backoffRecord);
    backoffKey.wrapLong(backoffRecord.getRecurringTime());

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(jobBackoffRestoreMigration.needsToRun(context)).isTrue();
    jobBackoffRestoreMigration.runMigration(context);

    // then
    assertThat(backoffColumnFamily.isEmpty()).isFalse();
    assertThat(backoffColumnFamily.count()).isEqualTo(1);
    backoffKey.wrapLong(record.getRecurringTime());
    assertThat(backoffColumnFamily.exists(backoffJobKey)).isFalse();
  }

  // regression test of https://github.com/camunda/camunda/issues/27183
  @ParameterizedTest
  @EnumSource(
      value = State.class,
      names = {"FAILED", "NOT_FOUND"},
      mode = Mode.EXCLUDE)
  public void shouldRemoveBackoffOfJobThatIsNotFailed(final State state) {
    // given a job in backoff that left the failed state without recurring, for example because an
    // older version of this migration added its entry and its incident was resolved later
    final JobRecord job = createFailedJobInBackoff(1, 1000);
    processingState.getJobState().updateJobState(1, state);
    final JobRecord otherJob = createFailedJobInBackoff(2, 2000);

    // when
    runMigration();

    // then
    assertThat(hasBackoffEntry(1, job.getRecurringTime())).isFalse();
    assertThat(hasBackoffEntry(2, otherJob.getRecurringTime())).isTrue();
    assertThat(backoffColumnFamily.count()).isEqualTo(1);
  }

  // regression test of https://github.com/camunda/camunda/issues/27183
  @ParameterizedTest
  @EnumSource(value = ErrorType.class, names = "SECRET_RESOLUTION_ERROR", mode = Mode.EXCLUDE)
  public void shouldRemoveBackoffOfFailedJobWithOpenIncident(final ErrorType errorType) {
    // given a job in backoff with an open incident, for example because an older version of this
    // migration added its entry while the job was waiting on the incident
    final JobRecord job = createFailedJobInBackoff(1, 1000);
    openIncident(1, errorType);
    final JobRecord otherJob = createFailedJobInBackoff(2, 2000);

    // when
    runMigration();

    // then
    assertThat(hasBackoffEntry(1, job.getRecurringTime())).isFalse();
    assertThat(hasBackoffEntry(2, otherJob.getRecurringTime())).isTrue();
    assertThat(backoffColumnFamily.count()).isEqualTo(1);
  }

  @Test
  public void shouldKeepBackoffOfFailedJobWithOpenSecretResolutionIncident() {
    // given a job in backoff with an open secret resolution incident, which resolving the incident
    // does not reactivate, so the job only leaves the failed state by recurring
    final JobRecord job = createFailedJobInBackoff(1, 1000);
    openIncident(1, ErrorType.SECRET_RESOLUTION_ERROR);

    // when
    runMigration();

    // then
    assertThat(hasBackoffEntry(1, job.getRecurringTime())).isTrue();
    assertThat(backoffColumnFamily.count()).isEqualTo(1);
  }

  @Test
  public void shouldRemoveBackoffThatDoesNotMatchRecurringTime() {
    // given a job in backoff with a second entry at another time
    final JobRecord job = createFailedJobInBackoff(1, 1000);
    processingState.getJobState().addJobBackoff(1, job.getRecurringTime() + 1);

    // when
    runMigration();

    // then
    assertThat(hasBackoffEntry(1, job.getRecurringTime())).isTrue();
    assertThat(hasBackoffEntry(1, job.getRecurringTime() + 1)).isFalse();
    assertThat(backoffColumnFamily.count()).isEqualTo(1);
  }

  private void runMigration() {
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    jobBackoffRestoreMigration.runMigration(context);
  }

  private boolean hasBackoffEntry(final long job, final long backoff) {
    jobKey.wrapLong(job);
    backoffKey.wrapLong(backoff);
    return backoffColumnFamily.exists(backoffJobKey);
  }

  private JobRecord createFailedJobInBackoff(final long key, final long retryBackoff) {
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord job = createJobRecord(retryBackoff);
    jobState.create(key, job);
    jobState.fail(key, job);
    return job;
  }

  private void openIncident(final long job, final ErrorType errorType) {
    processingState
        .getIncidentState()
        .createIncident(100, new IncidentRecord().setErrorType(errorType).setJobKey(job));
  }

  private void createJobInIncidentWithUpdatedRetries() {
    final MutableJobState jobState = processingState.getJobState();
    final JobRecord job = createJobRecord(1000);
    jobState.create(jobKey.getValue(), job);
    jobState.fail(jobKey.getValue(), job.setRetries(0));
    openIncident(jobKey.getValue(), ErrorType.JOB_NO_RETRIES);
    jobState.updateJobRetries(jobKey.getValue(), 1);
  }

  private static JobRecord createJobRecord(final long retryBackoff) {
    return new JobRecord()
        .setType("test")
        .setRetries(3)
        .setRetryBackoff(retryBackoff)
        .setRecurringTime(System.currentTimeMillis() + retryBackoff);
  }
}
