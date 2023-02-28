/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.test.util.BufferAssert;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class JobStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableJobState jobState;
  private MutableProcessingState processingState;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    jobState = processingState.getJobState();
  }

  @Test
  public void shouldCreateJobEntry() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1);
  }

  @Test
  public void shouldActivateJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    assertListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shoulDisableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();
    jobState.create(key, jobRecord);

    // when
    jobState.disable(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldTimeoutJob() {
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
    assertListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldDeleteJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.delete(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldNeverPersistJobVariables() {
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
  public void shouldCompleteActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.complete(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldCancelActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.cancel(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldThrowErrorActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.throwError(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ERROR_THROWN);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldCompleteActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.complete(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldCancelActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.cancel(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldThrowErrorActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.throwError(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ERROR_THROWN);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldCompleteFailedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord.setRetries(0));
    jobState.complete(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldCancelFailedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.fail(key, jobRecord.setRetries(0));
    jobState.cancel(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldFailJobWithRetriesLeft() {
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
    assertListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldFailJobWithRetriesAndBackOff() {
    // given
    final long key = 1L;
    final var retryBackoff = 100;
    final JobRecord jobRecord = newJobRecord().setRetries(1).setRetryBackoff(retryBackoff);

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
    assertListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + retryBackoff);
  }

  @Test
  public void shouldRetryProperJobWithRetryBackoff() {
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
  public void shouldImmediatelyRetryJobAfterFailedIfRetryBackoffIsZeroAndHasRetries() {
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
  public void shouldRetryJobAfterRecurredAndHasRetries() {
    // given
    final long jobKey = 1L;
    final long retryBackoff = Duration.ofDays(1).toMillis();
    final JobRecord jobRecord = newJobRecord().setRetries(1).setRetryBackoff(retryBackoff);

    // when
    jobState.create(jobKey, jobRecord);
    jobState.activate(jobKey, jobRecord);
    jobState.fail(jobKey, jobRecord);
    assertThat(jobState.exists(jobKey)).isTrue();
    assertJobState(jobKey, State.FAILED);
    assertListedAsBackOff(jobKey, jobRecord.getRecurringTime() + 1 + retryBackoff);
    jobState.recurAfterBackoff(jobKey, jobRecord);

    // then
    assertJobState(jobKey, State.ACTIVATABLE);
    refuteListedAsBackOff(jobKey, jobRecord.getRecurringTime() + 1 + retryBackoff);
  }

  @Test
  public void shouldFailJobWithNoRetriesLeft() {
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
  public void shouldResolveJob() {
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
    assertListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  public void shouldListTimedOutEntriesInOrder() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    jobState.create(5, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(65536L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> jobKeys = getTimedOutKeys(32768L);

    // then
    assertThat(jobKeys).hasSize(2);
    assertThat(jobKeys).containsExactly(1L, 2L);
  }

  @Test
  public void shouldOnlyIterateOverTimedoutWhileTrue() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(65536L));
    createAndActivateJobRecord(5, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> timedOutKeys = new ArrayList<>();
    final long since = 65536L;
    jobState.forEachTimedOutEntry(
        since,
        (k, e) -> {
          timedOutKeys.add(k);
          return k.longValue() < 3;
        });

    // then
    assertThat(timedOutKeys).hasSize(3);
    assertThat(timedOutKeys).containsExactly(1L, 2L, 3L);
  }

  @Test
  public void shouldCleanUpOnForEachTimedOutAndVisitNext() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    jobState.cancel(1, newJobRecord());
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));

    // when
    final List<Long> timedOutKeys = new ArrayList<>();
    final long since = 65536L;
    jobState.forEachTimedOutEntry(
        since,
        (k, e) -> {
          timedOutKeys.add(k);
          return true;
        });

    // then
    assertThat(timedOutKeys).hasSize(1);
    assertThat(timedOutKeys).containsExactly(2L);
  }

  @Test
  public void shouldDoNothingIfNotTimedOutJobs() {
    // given
    jobState.create(5, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> jobKeys = getTimedOutKeys(32768L);

    // then
    assertThat(jobKeys).isEmpty();
  }

  @Test
  public void shouldCheckExistenceCorrectly() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    jobState.create(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertThat(jobState.exists(key + 1)).isFalse();
  }

  @Test
  public void shouldListActivatableJobsForTypeInOrder() {
    // given
    final DirectBuffer type = wrapString("test");
    jobState.create(1, newJobRecord().setType("tes"));
    jobState.create(256L, newJobRecord().setType(type));
    createAndActivateJobRecord(512, newJobRecord().setType(type));
    jobState.create(65536L, newJobRecord().setType(type));
    jobState.create(4294967296L, newJobRecord().setType("test-other"));

    // when
    final List<Long> jobKeys = getActivatableKeys(type);

    // then
    assertThat(jobKeys).hasSize(2);
    assertThat(jobKeys).containsExactly(256L, 65536L);
  }

  @Test
  public void shouldNotDoAnythingIfNoActivatableJobs() {
    // given
    final DirectBuffer type = wrapString("test");
    createAndActivateJobRecord(1, newJobRecord().setType(type));
    jobState.create(256L, newJobRecord().setType("other"));

    // when
    final List<Long> jobKeys = getActivatableKeys(type);

    // then
    assertThat(jobKeys).isEmpty();
  }

  @Test
  public void shouldReturnNullIfJobDoesNotExist() {
    // given
    final long key = 1L;

    // then
    assertThat(jobState.getJob(key)).isNull();
  }

  @Test
  public void shouldReturnCorrectJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setType("test");

    // when
    jobState.create(key, jobRecord);
    jobState.create(key + 1, newJobRecord().setType("other"));

    // then
    final JobRecord savedJob = jobState.getJob(key);
    assertJobRecordIsEqualTo(savedJob, jobRecord);
    assertThat(BufferUtil.bufferAsString(savedJob.getTypeBuffer())).isEqualTo("test");
  }

  @Test
  public void testInvariants() {
    final JobRecord jobWithoutType = newJobRecord().setType(new UnsafeBuffer(0, 0));
    final JobRecord jobWithoutDeadline = newJobRecord().setDeadline(0L);

    // create
    assertThatThrownBy(() -> jobState.create(1L, jobWithoutType))
        .hasStackTraceContaining("type must not be empty");

    // activate
    assertThatThrownBy(() -> jobState.activate(1L, jobWithoutType))
        .hasMessage("type must not be empty");
    assertThatThrownBy(() -> jobState.activate(1L, jobWithoutDeadline))
        .hasMessage("deadline must be greater than 0");

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

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final JobRecord writtenRecord = newJobRecord();

    // when
    jobState.create(key, writtenRecord);
    writtenRecord.setType("foo");

    // then
    final JobRecord readRecord = jobState.getJob(key);
    assertThat(readRecord.getTypeBuffer()).isNotEqualTo(writtenRecord.getTypeBuffer());
    assertThat(readRecord.getTypeBuffer()).isEqualTo(BufferUtil.wrapString("test"));
    assertThat(writtenRecord.getTypeBuffer()).isEqualTo(BufferUtil.wrapString("foo"));
  }

  @Test
  public void shouldMakeJobNotActivatableWhenFailedWithoutRetries() {
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

  private void createAndActivateJobRecord(final long key, final JobRecord record) {
    jobState.create(key, record);
    jobState.activate(key, record);
  }

  private JobRecord newJobRecord() {
    final JobRecord jobRecord = new JobRecord();

    jobRecord.setRetries(2);
    jobRecord.setDeadline(256L);
    jobRecord.setType("test");

    return jobRecord;
  }

  private void assertJobState(final long key, final JobState.State state) {
    final List<State> others =
        Arrays.stream(State.values()).filter(s -> s != state).collect(Collectors.toList());

    assertThat(jobState.isInState(key, state)).isTrue();
    assertThat(others).noneMatch(other -> jobState.isInState(key, other));
  }

  private void assertJobRecordIsEqualTo(final JobRecord jobRecord, final JobRecord expected) {
    assertThat(jobRecord.getDeadline()).isEqualTo(expected.getDeadline());
    assertThat(jobRecord.getWorkerBuffer()).isEqualTo(expected.getWorkerBuffer());
    assertThat(jobRecord.getRetries()).isEqualTo(expected.getRetries());
    assertThat(jobRecord.getTypeBuffer()).isEqualTo(expected.getTypeBuffer());
    assertThat(jobRecord.getCustomHeadersBuffer()).isEqualTo(expected.getCustomHeadersBuffer());
    assertThat(jobRecord.getVariablesBuffer()).isEqualTo(expected.getVariablesBuffer());
  }

  private void assertListedAsActivatable(final long key, final DirectBuffer type) {
    final List<Long> activatableKeys = getActivatableKeys(type);
    assertThat(activatableKeys).contains(key);
  }

  private void refuteListedAsActivatable(final long key, final DirectBuffer type) {
    final List<Long> activatableKeys = getActivatableKeys(type);
    assertThat(activatableKeys).doesNotContain(key);
  }

  private void assertListedAsTimedOut(final long key, final long since) {
    final List<Long> timedOutKeys = getTimedOutKeys(since);
    assertThat(timedOutKeys).contains(key);
  }

  private void refuteListedAsTimedOut(final long key, final long since) {
    final List<Long> timedOutKeys = getTimedOutKeys(since);
    assertThat(timedOutKeys).doesNotContain(key);
  }

  private void assertListedAsBackOff(final long key, final long since) {
    final List<Long> backedOffKeys = getBackedOffKeys(since);
    assertThat(backedOffKeys).contains(key);
  }

  private void refuteListedAsBackOff(final long key, final long since) {
    final List<Long> backedOffKeys = getBackedOffKeys(since);
    assertThat(backedOffKeys).doesNotContain(key);
  }

  private List<Long> getActivatableKeys(final DirectBuffer type) {
    final List<Long> activatableKeys = new ArrayList<>();

    jobState.forEachActivatableJobs(type, (k, e) -> activatableKeys.add(k));
    return activatableKeys;
  }

  private List<Long> getTimedOutKeys(final long since) {
    final List<Long> timedOutKeys = new ArrayList<>();

    jobState.forEachTimedOutEntry(since, (k, e) -> timedOutKeys.add(k));
    return timedOutKeys;
  }

  private List<Long> getBackedOffKeys(final long since) {
    final List<Long> backedOffKeys = new ArrayList<>();
    jobState.findBackedOffJobs(since, (k, record) -> backedOffKeys.add(k));
    return backedOffKeys;
  }
}
