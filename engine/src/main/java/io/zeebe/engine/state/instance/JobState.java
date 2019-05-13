/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbByte;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.util.EnsureUtil;
import java.util.function.BiFunction;
import org.agrona.DirectBuffer;

public class JobState {

  // key => job record value
  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1914
  private final UnpackedObjectValue jobRecordToRead;
  private final UnpackedObjectValue jobRecordToWrite;

  private final DbLong jobKey;
  private final ColumnFamily<DbLong, UnpackedObjectValue> jobsColumnFamily;

  // key => job state
  private final DbByte jobState;
  private final ColumnFamily<DbLong, DbByte> statesJobColumnFamily;

  // type => [key]
  private final DbString jobTypeKey;
  private final DbCompositeKey<DbString, DbLong> typeJobKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, DbNil> activatableColumnFamily;

  // timeout => key
  private final DbLong deadlineKey;
  private final DbCompositeKey<DbLong, DbLong> deadlineJobKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> deadlinesColumnFamily;

  public JobState(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {

    jobRecordToRead = new UnpackedObjectValue();
    jobRecordToRead.wrapObject(new JobRecord());

    jobRecordToWrite = new UnpackedObjectValue();
    jobKey = new DbLong();
    jobsColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.JOBS, dbContext, jobKey, jobRecordToRead);

    jobState = new DbByte();
    statesJobColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.JOB_STATES, dbContext, jobKey, jobState);

    jobTypeKey = new DbString();
    typeJobKey = new DbCompositeKey<>(jobTypeKey, jobKey);
    activatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE, dbContext, typeJobKey, DbNil.INSTANCE);

    deadlineKey = new DbLong();
    deadlineJobKey = new DbCompositeKey<>(deadlineKey, jobKey);
    deadlinesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_DEADLINES, dbContext, deadlineJobKey, DbNil.INSTANCE);
  }

  public void create(final long key, final JobRecord record) {
    final DirectBuffer type = record.getType();
    createJob(key, record, type);
  }

  private void createJob(long key, JobRecord record, DirectBuffer type) {
    resetVariablesAndUpdateJobRecord(key, record);
    updateJobState(State.ACTIVATABLE);
    makeJobActivatable(type);
  }

  /**
   * <b>Note:</b> calling this method will reset the variables of the job record. Make sure to write
   * the job record to the log before updating it in the state.
   *
   * <p>related to https://github.com/zeebe-io/zeebe/issues/2182
   */
  public void activate(final long key, final JobRecord record) {
    final DirectBuffer type = record.getType();
    final long deadline = record.getDeadline();

    validateParameters(type, deadline);

    resetVariablesAndUpdateJobRecord(key, record);

    updateJobState(State.ACTIVATED);

    makeJobNotActivatable(type);

    deadlineKey.wrapLong(deadline);
    deadlinesColumnFamily.put(deadlineJobKey, DbNil.INSTANCE);
  }

  public void timeout(final long key, final JobRecord record) {
    final DirectBuffer type = record.getType();
    final long deadline = record.getDeadline();
    validateParameters(type, deadline);

    createJob(key, record, type);
    removeJobDeadline(deadline);
  }

  public void delete(long key, JobRecord record) {
    final DirectBuffer type = record.getType();
    final long deadline = record.getDeadline();

    jobKey.wrapLong(key);
    jobsColumnFamily.delete(jobKey);

    statesJobColumnFamily.delete(jobKey);

    makeJobNotActivatable(type);

    removeJobDeadline(deadline);
  }

  public void fail(long key, JobRecord updatedValue) {
    final DirectBuffer type = updatedValue.getType();
    final long deadline = updatedValue.getDeadline();

    validateParameters(type, deadline);

    resetVariablesAndUpdateJobRecord(key, updatedValue);

    final State newState = updatedValue.getRetries() > 0 ? State.ACTIVATABLE : State.FAILED;
    updateJobState(newState);

    if (newState == State.ACTIVATABLE) {
      makeJobActivatable(type);
    }

    removeJobDeadline(deadline);
  }

  private void validateParameters(DirectBuffer type, long deadline) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureGreaterThan("deadline", deadline, 0);
  }

  public void resolve(long key, final JobRecord updatedValue) {
    final DirectBuffer type = updatedValue.getType();

    resetVariablesAndUpdateJobRecord(key, updatedValue);
    updateJobState(State.ACTIVATABLE);
    makeJobActivatable(type);
  }

  public void forEachTimedOutEntry(
      final long upperBound, final BiFunction<Long, JobRecord, Boolean> callback) {

    deadlinesColumnFamily.whileTrue(
        (compositeKey, zbNil) -> {
          final long deadline = compositeKey.getFirst().getValue();
          final boolean isDue = deadline < upperBound;
          if (isDue) {
            final long jobKey = compositeKey.getSecond().getValue();
            return visitJob(jobKey, callback);
          }
          return false;
        });
  }

  public boolean exists(long jobKey) {
    this.jobKey.wrapLong(jobKey);
    return jobsColumnFamily.exists(this.jobKey);
  }

  public State getState(long key) {
    jobKey.wrapLong(key);

    final DbByte storedState = statesJobColumnFamily.get(jobKey);

    if (storedState == null) {
      return State.NOT_FOUND;
    }

    return State.forValue(storedState.getValue());
  }

  public boolean isInState(long key, State state) {
    return getState(key) == state;
  }

  public void forEachActivatableJobs(
      final DirectBuffer type, final BiFunction<Long, JobRecord, Boolean> callback) {
    jobTypeKey.wrapBuffer(type);

    activatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        ((compositeKey, zbNil) -> {
          final long jobKey = compositeKey.getSecond().getValue();
          return visitJob(jobKey, callback);
        }));
  }

  boolean visitJob(long jobKey, BiFunction<Long, JobRecord, Boolean> callback) {
    final JobRecord job = getJob(jobKey);
    if (job == null) {
      throw new IllegalStateException(
          String.format("Expected to find job with key %d, but no job found", jobKey));
    }
    return callback.apply(jobKey, job);
  }

  public JobRecord updateJobRetries(final long jobKey, final int retries) {
    final JobRecord job = getJob(jobKey);
    if (job != null) {
      job.setRetries(retries);
      resetVariablesAndUpdateJobRecord(jobKey, job);
    }
    return job;
  }

  public JobRecord getJob(final long key) {
    jobKey.wrapLong(key);
    final UnpackedObjectValue unpackedObjectValue = jobsColumnFamily.get(jobKey);
    return unpackedObjectValue == null ? null : (JobRecord) unpackedObjectValue.getObject();
  }

  public enum State {
    ACTIVATABLE((byte) 0),
    ACTIVATED((byte) 1),
    FAILED((byte) 2),
    NOT_FOUND((byte) 3);

    byte value;

    State(byte value) {
      this.value = value;
    }

    static State forValue(byte value) {
      switch (value) {
        case 0:
          return ACTIVATABLE;
        case 1:
          return ACTIVATED;
        case 2:
          return FAILED;
        default:
          return NOT_FOUND;
      }
    }
  }

  private void resetVariablesAndUpdateJobRecord(long key, JobRecord updatedValue) {
    jobKey.wrapLong(key);
    // do not persist variables in job state
    updatedValue.resetVariables();
    jobRecordToWrite.wrapObject(updatedValue);
    jobsColumnFamily.put(jobKey, jobRecordToWrite);
  }

  private void updateJobState(State newState) {
    jobState.wrapByte(newState.value);
    statesJobColumnFamily.put(jobKey, jobState);
  }

  private void makeJobActivatable(DirectBuffer type) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);

    jobTypeKey.wrapBuffer(type);
    activatableColumnFamily.put(typeJobKey, DbNil.INSTANCE);
  }

  private void makeJobNotActivatable(DirectBuffer type) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);

    jobTypeKey.wrapBuffer(type);
    activatableColumnFamily.delete(typeJobKey);
  }

  private void removeJobDeadline(long deadline) {
    deadlineKey.wrapLong(deadline);
    deadlinesColumnFamily.delete(deadlineJobKey);
  }
}
