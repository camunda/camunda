/*
 * Zeebe Broker Core
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
package io.zeebe.broker.job;

import io.zeebe.broker.logstreams.state.UnpackedObjectValue;
import io.zeebe.broker.logstreams.state.ZbColumnFamilies;
import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbByte;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.util.EnsureUtil;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.agrona.DirectBuffer;

public class JobState {

  // key => job record value
  private final UnpackedObjectValue jobRecord;
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
  private final ZeebeDb<ZbColumnFamilies> zeebeDb;

  public JobState(ZeebeDb<ZbColumnFamilies> zeebeDb) {
    jobRecord = new UnpackedObjectValue();
    jobRecord.wrapObject(new JobRecord());
    jobKey = new DbLong();
    jobsColumnFamily = zeebeDb.createColumnFamily(ZbColumnFamilies.JOBS, jobKey, jobRecord);

    jobState = new DbByte();
    statesJobColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.JOB_STATES, jobKey, jobState);

    jobTypeKey = new DbString();
    typeJobKey = new DbCompositeKey<>(jobTypeKey, jobKey);
    activatableColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.JOB_ACTIVATABLE, typeJobKey, DbNil.INSTANCE);

    deadlineKey = new DbLong();
    deadlineJobKey = new DbCompositeKey<>(deadlineKey, jobKey);
    deadlinesColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.JOB_DEADLINES, deadlineJobKey, DbNil.INSTANCE);

    this.zeebeDb = zeebeDb;
  }

  public void create(final long key, final JobRecord record) {
    final DirectBuffer type = record.getType();
    zeebeDb.batch(() -> createJob(key, record, type));
  }

  private void createJob(long key, JobRecord record, DirectBuffer type) {
    updateJobRecord(key, record);
    updateJobState(State.ACTIVATABLE);
    makeJobActivatable(type);
  }

  public void activate(final long key, final JobRecord record) {
    final DirectBuffer type = record.getType();
    final long deadline = record.getDeadline();

    validateParameters(type, deadline);

    zeebeDb.batch(
        () -> {
          updateJobRecord(key, record);

          updateJobState(State.ACTIVATED);

          makeJobNotActivatable(type);

          deadlineKey.wrapLong(deadline);
          deadlinesColumnFamily.put(deadlineJobKey, DbNil.INSTANCE);
        });
  }

  public void timeout(final long key, final JobRecord record) {
    final DirectBuffer type = record.getType();
    final long deadline = record.getDeadline();
    validateParameters(type, deadline);

    zeebeDb.batch(
        () -> {
          createJob(key, record, type);

          removeJobDeadline(deadline);
        });
  }

  public void delete(long key, JobRecord record) {
    final DirectBuffer type = record.getType();
    final long deadline = record.getDeadline();

    zeebeDb.batch(
        () -> {
          jobKey.wrapLong(key);
          jobsColumnFamily.delete(jobKey);

          statesJobColumnFamily.delete(jobKey);

          makeJobNotActivatable(type);

          removeJobDeadline(deadline);
        });
  }

  public void fail(long key, JobRecord updatedValue) {
    final DirectBuffer type = updatedValue.getType();
    final long deadline = updatedValue.getDeadline();

    validateParameters(type, deadline);

    zeebeDb.batch(
        () -> {
          updateJobRecord(key, updatedValue);

          final State newState = updatedValue.getRetries() > 0 ? State.ACTIVATABLE : State.FAILED;
          updateJobState(newState);

          if (newState == State.ACTIVATABLE) {
            makeJobActivatable(type);
          }

          removeJobDeadline(deadline);
        });
  }

  private void validateParameters(DirectBuffer type, long deadline) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureGreaterThan("deadline", deadline, 0);
  }

  public void resolve(long key, final JobRecord updatedValue) {
    final DirectBuffer type = updatedValue.getType();

    zeebeDb.batch(
        () -> {
          updateJobRecord(key, updatedValue);
          updateJobState(State.ACTIVATABLE);
          makeJobActivatable(type);
        });
  }

  public void forEachTimedOutEntry(
      final long upperBound, final BiConsumer<Long, JobRecord> callback) {

    deadlinesColumnFamily.whileTrue(
        (compositeKey, zbNil) -> {
          final long deadline = compositeKey.getFirst().getValue();
          final boolean isDue = deadline < upperBound;
          if (isDue) {
            final Long jobKey = compositeKey.getSecond().getValue();
            final JobRecord job = getJob(jobKey);

            if (job == null) {
              throw new IllegalStateException(
                  String.format("Expected to find job with key %d, but no job found", jobKey));
            }
            callback.accept(jobKey, job);
          }
          return isDue;
        });
  }

  public boolean exists(long jobKey) {
    this.jobKey.wrapLong(jobKey);
    return jobsColumnFamily.exists(this.jobKey);
  }

  public boolean isInState(long key, State state) {
    jobKey.wrapLong(key);

    final DbByte storedState = statesJobColumnFamily.get(jobKey);
    if (storedState != null) {
      return storedState.getValue() == state.value;
    }
    return false;
  }

  public void forEachActivatableJobs(
      final DirectBuffer type, final BiFunction<Long, JobRecord, Boolean> callback) {
    jobTypeKey.wrapBuffer(type);

    activatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        ((compositeKey, zbNil) -> {
          final long jobKey = compositeKey.getSecond().getValue();

          final JobRecord job = getJob(jobKey);
          if (job == null) {
            throw new IllegalStateException(
                String.format("Expected to find job with key %d, but no job found", jobKey));
          }
          return callback.apply(jobKey, job);
        }));
  }

  public JobRecord updateJobRetries(final long jobKey, final int retries) {
    final JobRecord job = getJob(jobKey);
    if (job != null) {
      job.setRetries(retries);
      updateJobRecord(jobKey, job);
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
    FAILED((byte) 2);

    byte value;

    State(byte value) {
      this.value = value;
    }
  }

  private void updateJobRecord(long key, JobRecord updatedValue) {
    jobKey.wrapLong(key);
    jobRecord.wrapObject(updatedValue);
    jobsColumnFamily.put(jobKey, jobRecord);
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
