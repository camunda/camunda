/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.test.util.BufferAssert;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the job state contract against the RocksDB state, plus the tests of the transitions that
 * only the event appliers released before 8.10 use. These enter jobs in the legacy {@code
 * JOB_ACTIVATABLE} column family, which only the RocksDB state has.
 */
@ExtendWith(ProcessingStateExtension.class)
public final class JobStateTest extends AbstractJobStateContractTest {

  private MutableProcessingState processingState;

  @Override
  protected MutableJobState createJobState() {
    return processingState.getJobState();
  }

  @Test
  void shouldCreateJobEntryViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1);
  }

  @Test
  void shouldTimeoutJobViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.timeout(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  void shouldNeverPersistJobVariablesViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    final List<BiConsumer<Long, JobRecord>> stateUpdates =
        Arrays.asList(
            jobState::create,
            jobState::activate,
            jobState::timeout,
            jobState::activate,
            jobState::fail);

    // when job state is updated then the variables is not persisted
    for (final BiConsumer<Long, JobRecord> stateUpdate : stateUpdates) {
      jobRecord.setVariables(MsgPackUtil.asMsgPack("foo", "bar"));
      stateUpdate.accept(key, jobRecord);
      final DirectBuffer variables = jobState.getJob(key).getVariablesBuffer();
      BufferAssert.assertThatBuffer(variables).isEqualTo(DocumentValue.EMPTY_DOCUMENT);
    }
  }

  @Test
  void shouldCompleteJobCreatedViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();
    jobState.create(key, jobRecord);

    // when
    jobState.complete(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldCompleteFailedJobViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord.setRetries(0));
    jobState.complete(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldCancelFailedJobViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord.setRetries(0));
    jobState.cancel(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldUpdatePriorityOfJobCreatedViaDeprecatedPath() {
    // given - a pre-8.10 job lives in the legacy column family only
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord();
    jobState.create(jobKey, jobRecord);

    // when
    jobState.updateJobPriority(jobKey, 99);

    // then - it is moved to the priority column family, listed once
    assertThat(jobState.getJob(jobKey).getPriority()).isEqualTo(99);
    assertThat(getActivatableKeys(jobRecord.getTypeBuffer(), jobRecord.getTenantId()))
        .containsExactly(jobKey);
  }

  @Test
  void shouldFailJobWithRetriesLeftViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(1);

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  void shouldFailJobWithRetriesAndBackOffViaDeprecatedPath() {
    // given
    final long key = 1L;
    final long now = 0;
    final var retryBackoff = 100;
    final JobRecord jobRecord =
        newJobRecord()
            .setRetries(1)
            .setRetryBackoff(retryBackoff)
            .setRecurringTime(now + retryBackoff);

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    assertListedAsBackOff(key, jobRecord.getRecurringTime() + 1);
  }

  @Test
  void shouldRetryProperJobWithRetryBackoffViaDeprecatedPath() {
    // given
    final long firstKey = 1L;
    final long secondKey = 2L;
    final JobRecord firstJobRecord = newJobRecord().setRetries(1).setRetryBackoff(0);
    final JobRecord secondJobRecord = newJobRecord().setRetries(1).setRetryBackoff(0);

    // when
    jobState.create(firstKey, firstJobRecord);
    jobState.activate(firstKey, firstJobRecord);
    jobState.create(secondKey, secondJobRecord);
    jobState.activate(secondKey, secondJobRecord);
    jobState.fail(firstKey, firstJobRecord);
    jobState.fail(secondKey, secondJobRecord);

    // then
    assertThat(jobState.exists(firstKey)).isTrue();
    assertThat(jobState.exists(secondKey)).isTrue();
    assertJobState(firstKey, State.ACTIVATABLE);
    assertJobState(secondKey, State.ACTIVATABLE);
  }

  @Test
  void shouldImmediatelyRetryJobAfterFailedIfRetryBackoffIsZeroAndHasRetriesViaDeprecatedPath() {
    // given
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(1).setRetryBackoff(0);

    // when
    jobState.create(jobKey, jobRecord);
    jobState.activate(jobKey, jobRecord);
    jobState.fail(jobKey, jobRecord);

    // then
    assertThat(jobState.exists(jobKey)).isTrue();
    assertJobState(jobKey, State.ACTIVATABLE);
    refuteListedAsBackOff(jobKey, jobRecord.getRecurringTime());
  }

  @Test
  void shouldRetryJobAfterRecurredAndHasRetriesViaDeprecatedPath() {
    // given
    final long jobKey = 1L;
    final long now = 0;
    final long retryBackoff = Duration.ofDays(1).toMillis();
    final JobRecord jobRecord =
        newJobRecord()
            .setRetries(1)
            .setRetryBackoff(retryBackoff)
            .setRecurringTime(now + retryBackoff);

    // when
    jobState.create(jobKey, jobRecord);
    jobState.activate(jobKey, jobRecord);
    jobState.fail(jobKey, jobRecord);
    assertThat(jobState.exists(jobKey)).isTrue();
    assertJobState(jobKey, State.FAILED);
    assertListedAsBackOff(jobKey, jobRecord.getRecurringTime() + 1);
    jobState.recurAfterBackoff(jobKey, jobRecord);

    // then
    assertJobState(jobKey, State.ACTIVATABLE);
    refuteListedAsBackOff(jobKey, jobRecord.getRecurringTime() + 1);
  }

  @Test
  void shouldFailJobWithNoRetriesLeftViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(0);

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  void shouldResolveJobViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord.setRetries(0));
    jobState.resolve(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  void shouldMakeJobNotActivatableWhenFailedWithoutRetriesViaDeprecatedPath() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(0);

    // when
    jobState.create(key, jobRecord);
    jobState.fail(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  void shouldEnforceInvariantsOfDeprecatedTransitions() {
    final JobRecord jobWithoutType = newJobRecord().setType(new UnsafeBuffer(0, 0));
    final JobRecord jobWithoutDeadline = newJobRecord().setDeadline(0L);

    // create
    assertThatThrownBy(() -> jobState.create(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // fail
    assertThatThrownBy(() -> jobState.fail(1L, jobWithoutType))
        .hasMessage("type must not be empty");

    // resolve
    assertThatThrownBy(() -> jobState.resolve(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // timeout
    assertThatThrownBy(() -> jobState.timeout(1L, jobWithoutType))
        .hasMessage("type must not be empty");
    assertThatThrownBy(() -> jobState.timeout(1L, jobWithoutDeadline))
        .hasMessage("deadline must be greater than 0");

    // complete
    assertThatThrownBy(() -> jobState.complete(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // cancel
    jobState.create(1L, newJobRecord());
    assertThatThrownBy(() -> jobState.cancel(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // throw error
    assertThatThrownBy(() -> jobState.throwError(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // should not throw any exception
    jobState.create(1L, newJobRecord());
    jobState.activate(1L, newJobRecord());
    jobState.complete(1L, jobWithoutDeadline);

    jobState.create(1L, newJobRecord());
    jobState.cancel(1L, jobWithoutDeadline);

    jobState.create(1L, newJobRecord());
    jobState.throwError(1L, jobWithoutDeadline);
  }

  /**
   * Deleting a job with another deadline than it was activated with leaves its deadline entry
   * behind in the column family; the scan skips it.
   */
  @Test
  void shouldSkipDeadlineEntryOfDeletedJob() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    jobState.cancel(1, newJobRecord());
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));

    // when
    final List<Long> timedOutKeys = new ArrayList<>();
    final long since = 65536L;
    final DeadlineIndex nextIndex =
        jobState.forEachTimedOutEntry(
            since,
            null,
            (k, e) -> {
              timedOutKeys.add(k);
              return true;
            });

    // then
    assertThat(timedOutKeys).containsExactly(2L);
    assertThat(nextIndex).isNull();
  }

  @Test
  void shouldNotIndexJobCreatedViaDeprecatedPath() {
    // given / when - the deprecated create() path does not populate the index
    final long processInstanceKey = 10L;
    jobState.create(1L, newJobRecord().setProcessInstanceKey(processInstanceKey));

    // then - a pre-8.10 job that has never been suspended has no entry
    assertThat(visitAllJobsOfProcessInstance(processInstanceKey)).isEmpty();
  }

  @Test
  void shouldBackfillIndexWhenDeprecatedJobIsSuspended() {
    // given - a job created via the deprecated path, so it is not yet indexed
    final long processInstanceKey = 10L;
    final long key = 1L;
    jobState.create(key, newJobRecord().setProcessInstanceKey(processInstanceKey));
    assertThat(visitAllJobsOfProcessInstance(processInstanceKey)).isEmpty();

    // when - it is suspended, the point every pre-8.10 job passes through
    jobState.updateJobState(key, State.SUSPENDED);

    // then - the index is backfilled
    assertThat(visitAllJobsOfProcessInstance(processInstanceKey)).containsExactly(key);
  }
}
