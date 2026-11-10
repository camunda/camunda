/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BufferAssert;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link MutableJobState} implementation must share. Each backend runs these tests
 * by extending this class.
 *
 * <p>The tests drive the state the way the current event appliers do. The transitions that only the
 * appliers released before 8.10 use (like {@link MutableJobState#create}) enter jobs in the legacy
 * activation lookup, which only the RocksDB state has; they are tested there.
 */
public abstract class AbstractJobStateContractTest {

  protected MutableJobState jobState;

  /** Creates the state under test. Called before each test. */
  protected abstract MutableJobState createJobState();

  @BeforeEach
  void setUpJobState() {
    jobState = createJobState();
  }

  @Test
  void shouldCreateJobEntry() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1);
  }

  @Test
  void shouldActivateJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
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
  void shouldGetJobForDefaultTenant() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();
    createJob(key, jobRecord);

    // when
    final JobRecord jobRecord1 = jobState.getJob(key);

    // then
    assertThat(jobRecord1).isEqualTo(jobRecord);
  }

  @Test
  void shouldGetJobForCustomTenant() {
    // given
    final long key = 1L;
    final String testTenant = "test-tenant";
    final JobRecord jobRecord = newJobRecord(testTenant);
    createJob(key, jobRecord);

    // when
    final JobRecord jobRecord1 = jobState.getJob(key);

    // then
    assertThat(jobRecord1).isEqualTo(jobRecord);
  }

  @Test
  void shouldDisableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();
    createJob(key, jobRecord);

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
  void shouldTimeoutJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    timeOutJob(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  void shouldDeleteJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
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
  void shouldNeverPersistJobVariables() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    final List<BiConsumer<Long, JobRecord>> stateUpdates =
        Arrays.asList(
            this::createJob,
            jobState::activate,
            this::timeOutJob,
            jobState::activate,
            this::failJob,
            jobState::updateJobRecord,
            jobState::migrate);

    // when job state is updated then the variables is not persisted
    for (final BiConsumer<Long, JobRecord> stateUpdate : stateUpdates) {
      jobRecord.setVariables(MsgPackUtil.asMsgPack("foo", "bar"));
      stateUpdate.accept(key, jobRecord);
      final DirectBuffer variables = jobState.getJob(key).getVariablesBuffer();
      BufferAssert.assertThatBuffer(variables).isEqualTo(DocumentValue.EMPTY_DOCUMENT);
    }
  }

  @Test
  void shouldCompleteActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
    jobState.complete(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldCancelActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
    jobState.cancel(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldThrowErrorActivatableJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
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
  void shouldCompleteActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.complete(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldCancelActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    jobState.cancel(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldThrowErrorActivatedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
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
  void shouldCompleteFailedJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    failJob(key, jobRecord.setRetries(0));
    jobState.complete(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldCancelBackedOffJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetryBackoff(100).setRecurringTime(100);
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    failJob(key, jobRecord);

    // when
    jobState.cancel(key, jobRecord);

    // then
    assertJobRemoved(key, jobRecord);
  }

  @Test
  void shouldUpdateJobPriority() {
    // given
    final long jobKey = 1L;
    final int newPriority = 99;
    final JobRecord jobRecord = newJobRecord();
    createJob(jobKey, jobRecord);

    // when
    jobState.updateJobPriority(jobKey, newPriority);

    // then
    assertThat(jobState.exists(jobKey)).isTrue();
    assertThat(jobState.getJob(jobKey).getPriority()).isEqualTo(newPriority);
    assertThat(getActivatableKeys(jobRecord.getTypeBuffer(), jobRecord.getTenantId()))
        .containsExactly(jobKey);
  }

  @Test
  void shouldServeJobAtItsNewPriority() {
    // given
    final DirectBuffer type = wrapString("test");
    createJob(1L, newJobRecord().setType(type).setPriority(10));
    createJob(2L, newJobRecord().setType(type).setPriority(5));

    // when
    jobState.updateJobPriority(2L, 20);

    // then
    assertThat(getActivatableKeys(type, TenantOwned.DEFAULT_TENANT_IDENTIFIER))
        .containsExactly(2L, 1L);
  }

  @Test
  void shouldUpdatePriorityOfActivatedJobWithoutMakingItActivatable() {
    // given
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord();
    createJob(jobKey, jobRecord);
    jobState.activate(jobKey, jobRecord);

    // when
    jobState.updateJobPriority(jobKey, 42);

    // then
    assertThat(jobState.getJob(jobKey).getPriority()).isEqualTo(42);
    assertJobState(jobKey, State.ACTIVATED);
    refuteListedAsActivatable(jobKey, jobRecord.getTypeBuffer());
  }

  @Test
  void shouldIgnoreUpdateJobPriorityIfJobNotFound() {
    // given
    final long jobKey = 999L;

    // when / then - no exception, no state change
    jobState.updateJobPriority(jobKey, 42);
    assertThat(jobState.exists(jobKey)).isFalse();
  }

  @Test
  void shouldUpdateJobRetries() {
    // given
    final long jobKey = 1L;
    createJob(jobKey, newJobRecord());

    // when
    final JobRecord updated = jobState.updateJobRetries(jobKey, 7);

    // then
    assertThat(updated.getRetries()).isEqualTo(7);
    assertThat(jobState.getJob(jobKey).getRetries()).isEqualTo(7);
  }

  @Test
  void shouldIgnoreUpdateJobRetriesIfJobNotFound() {
    // when
    final JobRecord updated = jobState.updateJobRetries(999L, 7);

    // then
    assertThat(updated).isNull();
    assertThat(jobState.exists(999L)).isFalse();
  }

  @Test
  void shouldNotParkJobForSecretResolutionWhenItIsNotUpForActivation() {
    // given
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord();
    createAndActivateJobRecord(jobKey, jobRecord);

    // when
    jobState.parkForSecretResolution(jobKey, jobRecord);

    // then
    assertJobState(jobKey, State.ACTIVATED);
  }

  @Test
  void shouldMakeWaitingJobActivatableAfterSecretResolution() {
    // given
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord();
    parkJobForSecretResolution(jobKey, jobRecord);
    refuteListedAsActivatable(jobKey, jobRecord.getTypeBuffer());

    // when
    jobState.makeActivatableAfterSecretResolution(jobKey);

    // then
    assertJobState(jobKey, State.ACTIVATABLE);
    assertListedAsActivatable(jobKey, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
  }

  @Test
  void shouldNotMakeJobActivatableAfterSecretResolutionWhenItNoLongerWaits() {
    // given
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord();
    parkJobForSecretResolution(jobKey, jobRecord);
    jobState.makeActivatableAfterSecretResolution(jobKey);
    jobState.activate(jobKey, jobRecord);

    // when
    jobState.makeActivatableAfterSecretResolution(jobKey);

    // then
    assertJobState(jobKey, State.ACTIVATED);
    refuteListedAsActivatable(jobKey, jobRecord.getTypeBuffer());
  }

  @Test
  void shouldNotMakeSuspendedJobActivatableAfterSecretResolution() {
    // given
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord();
    parkJobForSecretResolution(jobKey, jobRecord);
    jobState.updateJobState(jobKey, State.SUSPENDED);
    refuteListedAsActivatable(jobKey, jobRecord.getTypeBuffer());

    // when
    jobState.makeActivatableAfterSecretResolution(jobKey);

    // then
    assertJobState(jobKey, State.SUSPENDED);
    refuteListedAsActivatable(jobKey, jobRecord.getTypeBuffer());
  }

  @Test
  void shouldIgnoreMakeActivatableAfterSecretResolutionIfJobNotFound() {
    // given
    final long jobKey = 999L;

    // when / then
    jobState.makeActivatableAfterSecretResolution(jobKey);
    assertThat(jobState.exists(jobKey)).isFalse();
  }

  @Test
  void shouldKeepJobParkedWhenUpdatingPriorityOfJobWaitingForSecretResolution() {
    // given
    final long jobKey = 1L;
    final JobRecord jobRecord = newJobRecord();
    parkJobForSecretResolution(jobKey, jobRecord);

    // when
    jobState.updateJobPriority(jobKey, 99);

    // then
    assertThat(jobState.getJob(jobKey).getPriority()).isEqualTo(99);
    assertJobState(jobKey, State.WAITING_FOR_SECRET_RESOLUTION);
    refuteListedAsActivatable(jobKey, jobRecord.getTypeBuffer());
  }

  @Test
  void shouldUpdateDeadline() {
    // given
    final long jobKey = 1L;
    final long deadline = 300000;
    final long newDeadline = 600000;
    final JobRecord jobRecord = newJobRecord().setDeadline(deadline);
    createJob(jobKey, jobRecord);
    jobState.activate(jobKey, jobRecord);

    // when
    jobState.updateJobDeadline(jobKey, newDeadline);

    // then
    assertThat(jobState.exists(jobKey)).isTrue();
    assertThat(jobState.getJob(jobKey).getDeadline()).isEqualTo(newDeadline);
    assertThat(jobState.jobDeadlineExists(jobKey, deadline)).isFalse();
    assertThat(jobState.jobDeadlineExists(jobKey, newDeadline)).isTrue();
    refuteListedAsTimedOut(jobKey, deadline + 1);
    assertListedAsTimedOut(jobKey, newDeadline + 1);
  }

  @Test
  void shouldFailJobWithRetriesLeft() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(1);

    // when
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    failJob(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.ACTIVATABLE);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    assertListedAsActivatable(key, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  @Test
  void shouldFailJobWithRetriesAndBackOff() {
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
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    failJob(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    assertListedAsBackOff(key, jobRecord.getRecurringTime() + 1);
  }

  @Test
  void shouldRetryJobAfterRecurredAndHasRetries() {
    // given
    final long jobKey = 1L;
    final long retryBackoff = 100;
    final JobRecord jobRecord =
        newJobRecord().setRetries(1).setRetryBackoff(retryBackoff).setRecurringTime(retryBackoff);
    createJob(jobKey, jobRecord);
    jobState.activate(jobKey, jobRecord);
    failJob(jobKey, jobRecord);

    // when
    recurJob(jobKey, jobRecord);

    // then
    assertJobState(jobKey, State.ACTIVATABLE);
    assertListedAsActivatable(jobKey, jobRecord.getTypeBuffer(), jobRecord.getTenantId());
    refuteListedAsBackOff(jobKey, jobRecord.getRecurringTime() + 1);
  }

  @Test
  void shouldFailJobWithNoRetriesLeft() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setRetries(0);

    // when
    createJob(key, jobRecord);
    jobState.activate(key, jobRecord);
    failJob(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertJobState(key, State.FAILED);
    assertJobRecordIsEqualTo(jobState.getJob(key), jobRecord);
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
  }

  @Test
  void shouldListBackedOffJobsInOrderAndReturnNextDueDate() {
    // given
    backOffJob(1L, 300L);
    backOffJob(2L, 100L);
    backOffJob(3L, 200L);
    backOffJob(4L, 1000L);

    // when
    final List<Long> backedOffKeys = new ArrayList<>();
    final long nextDueDate = jobState.findBackedOffJobs(300L, (k, r) -> backedOffKeys.add(k));

    // then
    assertThat(backedOffKeys).containsExactly(2L, 3L, 1L);
    assertThat(nextDueDate).isEqualTo(1000L);
  }

  @Test
  void shouldStopListingBackedOffJobsWhenCallbackReturnsFalse() {
    // given
    backOffJob(1L, 100L);
    backOffJob(2L, 200L);

    // when
    final List<Long> backedOffKeys = new ArrayList<>();
    final long nextDueDate =
        jobState.findBackedOffJobs(
            300L,
            (k, r) -> {
              backedOffKeys.add(k);
              return false;
            });

    // then
    assertThat(backedOffKeys).containsExactly(1L);
    assertThat(nextDueDate).isEqualTo(100L);
  }

  @Test
  void shouldReturnNoDueDateIfNoJobIsBackedOff() {
    // given
    createJob(1L, newJobRecord());

    // when
    final long nextDueDate = jobState.findBackedOffJobs(300L, (k, r) -> true);

    // then
    assertThat(nextDueDate).isEqualTo(-1L);
  }

  @Test
  void shouldListTimedOutEntriesInOrder() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    createJob(5, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(65536L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> jobKeys = getTimedOutKeys(32768L);

    // then
    assertThat(jobKeys).containsExactly(1L, 2L);
  }

  @Test
  void shouldOnlyIterateOverTimedoutWhileTrue() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(65536L));
    createAndActivateJobRecord(5, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> timedOutKeys = new ArrayList<>();
    final long since = 65536L;
    final DeadlineIndex nextIndex =
        jobState.forEachTimedOutEntry(
            since,
            null,
            (k, e) -> {
              timedOutKeys.add(k);
              return k.longValue() < 3;
            });

    // then
    assertThat(timedOutKeys).containsExactly(1L, 2L, 3L);
    assertThat(nextIndex).isEqualTo(new DeadlineIndex(512L, 3L));
  }

  @Test
  void shouldContinueTimedOutIterationAtIndex() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    createAndActivateJobRecord(2, newJobRecord().setDeadline(256L));
    createAndActivateJobRecord(3, newJobRecord().setDeadline(512L));

    // when
    final List<Long> timedOutKeys = new ArrayList<>();
    final DeadlineIndex nextIndex =
        jobState.forEachTimedOutEntry(
            65536L,
            new DeadlineIndex(256L, 2L),
            (k, e) -> {
              timedOutKeys.add(k);
              return true;
            });

    // then
    assertThat(timedOutKeys).containsExactly(2L, 3L);
    assertThat(nextIndex).isNull();
  }

  @Test
  void shouldNotListCanceledJobAsTimedOut() {
    // given
    createAndActivateJobRecord(1, newJobRecord().setDeadline(1L));
    jobState.cancel(1, newJobRecord().setDeadline(1L));
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
  void shouldDoNothingIfNotTimedOutJobs() {
    // given
    createJob(5, newJobRecord().setDeadline(512L));
    createAndActivateJobRecord(4, newJobRecord().setDeadline(4294967296L));

    // when
    final List<Long> jobKeys = getTimedOutKeys(32768L);

    // then
    assertThat(jobKeys).isEmpty();
  }

  @Test
  void shouldCheckExistenceCorrectly() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord();

    // when
    createJob(key, jobRecord);

    // then
    assertThat(jobState.exists(key)).isTrue();
    assertThat(jobState.exists(key + 1)).isFalse();
  }

  @Test
  void shouldListActivatableJobsForTypeInOrder() {
    // given
    final DirectBuffer type = wrapString("test");
    createJob(1, newJobRecord().setType("tes"));
    createJob(256L, newJobRecord().setType(type));
    createAndActivateJobRecord(512, newJobRecord().setType(type));
    createJob(65536L, newJobRecord().setType(type));
    createJob(4294967296L, newJobRecord().setType("test-other"));

    // when
    final List<Long> jobKeys = getActivatableKeys(type, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    assertThat(jobKeys).containsExactly(256L, 65536L);
  }

  @Test
  void shouldListActivatableJobsByDescendingPriorityThenKey() {
    // given
    final DirectBuffer type = wrapString("test");
    createJob(1L, newJobRecord().setType(type).setPriority(0));
    createJob(2L, newJobRecord().setType(type).setPriority(-5));
    createJob(3L, newJobRecord().setType(type).setPriority(10));
    createJob(4L, newJobRecord().setType(type).setPriority(0));
    createJob(5L, newJobRecord().setType(type).setPriority(Integer.MAX_VALUE));
    createJob(6L, newJobRecord().setType(type).setPriority(Integer.MIN_VALUE));
    createJob(7L, newJobRecord().setType(type).setPriority(10));

    // when
    final List<Long> jobKeys = getActivatableKeys(type, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    assertThat(jobKeys).containsExactly(5L, 3L, 7L, 1L, 4L, 2L, 6L);
  }

  @Test
  void shouldListActivatableJobsOfTheGivenTenantsOnly() {
    // given
    final DirectBuffer type = wrapString("test");
    createJob(1L, newJobRecord("tenant-a").setType(type));
    createJob(2L, newJobRecord("tenant-b").setType(type));
    createJob(3L, newJobRecord("tenant-c").setType(type).setPriority(5));

    // when
    final List<Long> jobKeys = getActivatableKeys(type, "tenant-a", "tenant-c");

    // then
    assertThat(jobKeys).containsExactly(3L, 1L);
  }

  @Test
  void shouldStopListingActivatableJobsWhenCallbackReturnsFalse() {
    // given
    final DirectBuffer type = wrapString("test");
    createJob(1L, newJobRecord().setType(type).setPriority(5));
    createJob(2L, newJobRecord().setType(type));
    createJob(3L, newJobRecord().setType(type).setPriority(-5));

    // when
    final List<Long> jobKeys = new ArrayList<>();
    jobState.forEachActivatableJobs(
        type,
        List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER),
        1,
        (k, e) -> {
          jobKeys.add(k);
          return jobKeys.size() < 2;
        });

    // then
    assertThat(jobKeys).containsExactly(1L, 2L);
  }

  @Test
  void shouldNotDoAnythingIfNoActivatableJobs() {
    // given
    final DirectBuffer type = wrapString("test");
    final String tenantId = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    createAndActivateJobRecord(1, newJobRecord().setType(type));
    createJob(256L, newJobRecord().setType("other"));

    // when
    final List<Long> jobKeys = getActivatableKeys(type, tenantId);

    // then
    assertThat(jobKeys).isEmpty();
  }

  @Test
  void shouldReturnNullIfJobDoesNotExist() {
    // given
    final long key = 1L;

    // then
    assertThat(jobState.getJob(key)).isNull();
  }

  @Test
  void shouldReturnCorrectJob() {
    // given
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setType("test");

    // when
    createJob(key, jobRecord);
    createJob(key + 1, newJobRecord().setType("other"));

    // then
    final JobRecord savedJob = jobState.getJob(key);
    assertJobRecordIsEqualTo(savedJob, jobRecord);
    assertThat(BufferUtil.bufferAsString(savedJob.getTypeBuffer())).isEqualTo("test");
  }

  @Test
  void shouldEnforceInvariants() {
    final JobRecord jobWithoutType = newJobRecord().setType(new UnsafeBuffer(0, 0));
    final JobRecord jobWithoutDeadline = newJobRecord().setDeadline(0L);

    // make activatable
    assertThatThrownBy(
            () -> jobState.makeJobActivatableByPriority(new UnsafeBuffer(0, 0), 1L, "t", 0))
        .hasMessage("type must not be empty");
    assertThatThrownBy(() -> jobState.makeJobActivatableByPriority(wrapString("test"), 1L, "", 0))
        .hasMessage("tenantId must not be empty");

    // activate
    assertThatThrownBy(() -> jobState.activate(1L, jobWithoutType))
        .hasMessage("type must not be empty");
    assertThatThrownBy(() -> jobState.activate(1L, jobWithoutDeadline))
        .hasMessage("deadline must be greater than 0");

    // make not activatable
    assertThatThrownBy(() -> jobState.makeJobNotActivatable(1L, jobWithoutType))
        .hasMessage("type must not be empty");

    // should not throw any exception
    createJob(1L, newJobRecord());
    jobState.activate(1L, newJobRecord());
    jobState.complete(1L, jobWithoutDeadline);

    createJob(1L, newJobRecord());
    jobState.cancel(1L, jobWithoutDeadline);

    createJob(1L, newJobRecord());
    jobState.throwError(1L, jobWithoutDeadline);
  }

  @Test
  void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final JobRecord writtenRecord = newJobRecord();

    // when
    createJob(key, writtenRecord);
    writtenRecord.setType("foo");

    // then
    final JobRecord readRecord = jobState.getJob(key);
    assertThat(readRecord.getTypeBuffer()).isNotEqualTo(writtenRecord.getTypeBuffer());
    assertThat(readRecord.getTypeBuffer()).isEqualTo(BufferUtil.wrapString("test"));
    assertThat(writtenRecord.getTypeBuffer()).isEqualTo(BufferUtil.wrapString("foo"));
  }

  @Test
  void shouldIndexJobByProcessInstanceOnCreation() {
    // given
    final long processInstanceKey = 10L;
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setProcessInstanceKey(processInstanceKey);

    // when - the 8.10+ creation path
    jobState.insertJobRecordActivatable(key, jobRecord);

    // then
    assertThat(visitAllJobsOfProcessInstance(processInstanceKey)).containsExactly(key);
  }

  @Test
  void shouldNotDuplicateIndexWhenAlreadyIndexedJobIsSuspended() {
    // given - a job already indexed at creation
    final long processInstanceKey = 10L;
    final long key = 1L;
    jobState.insertJobRecordActivatable(
        key, newJobRecord().setProcessInstanceKey(processInstanceKey));

    // when - suspension re-indexes it
    jobState.updateJobState(key, State.SUSPENDED);

    // then - the upsert leaves a single entry
    assertThat(visitAllJobsOfProcessInstance(processInstanceKey)).containsExactly(key);
  }

  @Test
  void shouldRemoveJobFromProcessInstanceIndexOnDeletion() {
    // given
    final long processInstanceKey = 10L;
    final long key = 1L;
    final JobRecord jobRecord = newJobRecord().setProcessInstanceKey(processInstanceKey);
    jobState.insertJobRecordActivatable(key, jobRecord);

    // when
    jobState.delete(key, jobRecord);

    // then
    assertThat(visitAllJobsOfProcessInstance(processInstanceKey)).isEmpty();
  }

  @Test
  void shouldOnlyIndexJobsOfTheGivenProcessInstance() {
    // given
    final JobRecord jobOfInstanceOne = newJobRecord().setProcessInstanceKey(10L);
    final JobRecord jobOfInstanceTwo = newJobRecord().setProcessInstanceKey(20L);
    jobState.insertJobRecordActivatable(1L, jobOfInstanceOne);
    jobState.insertJobRecordActivatable(2L, jobOfInstanceTwo);

    // when
    final var visited = visitAllJobsOfProcessInstance(10L);

    // then
    assertThat(visited).containsExactly(1L);
  }

  @Test
  void shouldStartAtFirstEntryWhenStartAtIsNegative() {
    // given
    final long processInstanceKey = 10L;
    jobState.insertJobRecordActivatable(
        1L, newJobRecord().setProcessInstanceKey(processInstanceKey));
    jobState.insertJobRecordActivatable(
        2L, newJobRecord().setProcessInstanceKey(processInstanceKey));

    // when - a non-sentinel negative key, to prove any negative (not only -1) starts from the first
    final var visited = new ArrayList<Long>();
    jobState.forEachJobsByProcessInstance(
        processInstanceKey,
        -2L,
        jobKey -> {
          visited.add(jobKey);
          return true;
        });

    // then
    assertThat(visited).containsExactly(1L, 2L);
  }

  @Test
  void shouldIncludeStartAtJobKeyInSearch() {
    // given
    final long processInstanceKey = 10L;
    jobState.insertJobRecordActivatable(
        1L, newJobRecord().setProcessInstanceKey(processInstanceKey));
    jobState.insertJobRecordActivatable(
        2L, newJobRecord().setProcessInstanceKey(processInstanceKey));

    // when
    final var visited = new ArrayList<Long>();
    jobState.forEachJobsByProcessInstance(
        processInstanceKey,
        2L,
        jobKey -> {
          visited.add(jobKey);
          return true;
        });

    // then - the given key itself is included, not skipped
    assertThat(visited).containsExactly(2L);
  }

  @Test
  void shouldStopVisitingJobsOfProcessInstanceWhenVisitorReturnsFalse() {
    // given
    final long processInstanceKey = 10L;
    jobState.insertJobRecordActivatable(
        1L, newJobRecord().setProcessInstanceKey(processInstanceKey));
    jobState.insertJobRecordActivatable(
        2L, newJobRecord().setProcessInstanceKey(processInstanceKey));

    // when
    final var visited = new ArrayList<Long>();
    jobState.forEachJobsByProcessInstance(
        processInstanceKey,
        -1L,
        jobKey -> {
          visited.add(jobKey);
          return false;
        });

    // then
    assertThat(visited).containsExactly(1L);
  }

  @Test
  void shouldContinuePastACursorKeyThatNoLongerExists() {
    // given - two indexed jobs, then the one the cursor points at is deleted (e.g. cancelled
    // between resume cycles) so the cursor now names a key with no index entry
    final long processInstanceKey = 10L;
    final JobRecord deleted = newJobRecord().setProcessInstanceKey(processInstanceKey);
    jobState.insertJobRecordActivatable(1L, deleted);
    jobState.insertJobRecordActivatable(
        2L, newJobRecord().setProcessInstanceKey(processInstanceKey));
    jobState.delete(1L, deleted);

    // when - seeking from the deleted key
    final var visited = new ArrayList<Long>();
    jobState.forEachJobsByProcessInstance(
        processInstanceKey,
        1L,
        jobKey -> {
          visited.add(jobKey);
          return true;
        });

    // then - the seek lands on the next key >= the cursor, so iteration continues from there
    assertThat(visited).containsExactly(2L);
  }

  /** Creates an activatable job the way the current job created applier does. */
  protected void createJob(final long key, final JobRecord record) {
    jobState.insertJobRecordActivatable(key, record);
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), key, record.getTenantId(), record.getPriority());
  }

  /** Fails a job the way the current job failed applier does. */
  protected void failJob(final long key, final JobRecord record) {
    if (record.getRetries() > 0) {
      if (record.getRetryBackoff() > 0) {
        jobState.addJobBackoff(key, record.getRecurringTime());
        jobState.updateJobRecord(key, record);
        jobState.updateJobState(key, State.FAILED);
        jobState.removeJobDeadline(key, record.getDeadline());
        jobState.makeJobNotActivatable(key, record);
      } else {
        jobState.updateJobRecord(key, record);
        jobState.updateJobState(key, State.ACTIVATABLE);
        jobState.removeJobDeadline(key, record.getDeadline());
        jobState.makeJobActivatableByPriority(
            record.getTypeBuffer(), key, record.getTenantId(), record.getPriority());
      }
    } else {
      jobState.updateJobRecord(key, record);
      jobState.updateJobState(key, State.FAILED);
      jobState.removeJobDeadline(key, record.getDeadline());
      jobState.makeJobNotActivatable(key, record);
    }
  }

  /** Times out a job the way the current job timed out applier does. */
  protected void timeOutJob(final long key, final JobRecord record) {
    jobState.updateJobRecord(key, record);
    jobState.updateJobState(key, State.ACTIVATABLE);
    jobState.removeJobDeadline(key, record.getDeadline());
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), key, record.getTenantId(), record.getPriority());
  }

  /** Lets a job recur after its backoff the way the current job recurred applier does. */
  protected void recurJob(final long key, final JobRecord record) {
    jobState.updateJobRecord(key, record);
    jobState.updateJobState(key, State.ACTIVATABLE);
    jobState.removeJobDeadline(key, record.getDeadline());
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), key, record.getTenantId(), record.getPriority());
    jobState.removeJobBackoff(key, record.getRecurringTime());
  }

  protected void createAndActivateJobRecord(final long key, final JobRecord record) {
    createJob(key, record);
    jobState.activate(key, record);
  }

  protected JobRecord newJobRecord() {
    return newJobRecord(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  protected JobRecord newJobRecord(final String tenantId) {
    final JobRecord jobRecord = new JobRecord();

    jobRecord.setRetries(2).setDeadline(256L).setType("test").setTenantId(tenantId);

    return jobRecord;
  }

  protected List<Long> visitAllJobsOfProcessInstance(final long processInstanceKey) {
    final List<Long> visited = new ArrayList<>();
    jobState.forEachJobsByProcessInstance(
        processInstanceKey,
        -1L,
        jobKey -> {
          visited.add(jobKey);
          return true;
        });
    return visited;
  }

  protected void assertJobState(final long key, final State state) {
    final List<State> others = Arrays.stream(State.values()).filter(s -> s != state).toList();

    assertThat(jobState.isInState(key, state)).isTrue();
    assertThat(others).noneMatch(other -> jobState.isInState(key, other));
  }

  protected void assertJobRecordIsEqualTo(final JobRecord jobRecord, final JobRecord expected) {
    assertThat(jobRecord.getDeadline()).isEqualTo(expected.getDeadline());
    assertThat(jobRecord.getWorkerBuffer()).isEqualTo(expected.getWorkerBuffer());
    assertThat(jobRecord.getRetries()).isEqualTo(expected.getRetries());
    assertThat(jobRecord.getTypeBuffer()).isEqualTo(expected.getTypeBuffer());
    assertThat(jobRecord.getCustomHeadersBuffer()).isEqualTo(expected.getCustomHeadersBuffer());
    assertThat(jobRecord.getVariablesBuffer()).isEqualTo(expected.getVariablesBuffer());
  }

  protected void assertJobRemoved(final long key, final JobRecord jobRecord) {
    assertThat(jobState.exists(key)).isFalse();
    assertThat(jobState.isInState(key, State.NOT_FOUND)).isTrue();
    assertThat(jobState.getJob(key)).isNull();
    refuteListedAsActivatable(key, jobRecord.getTypeBuffer());
    refuteListedAsTimedOut(key, jobRecord.getDeadline() + 1);
    refuteListedAsBackOff(key, jobRecord.getRecurringTime() + 1 + 1);
  }

  protected void assertListedAsActivatable(
      final long key, final DirectBuffer type, final String tenantId) {
    final List<Long> activatableKeys = getActivatableKeys(type, tenantId);
    assertThat(activatableKeys).contains(key);
  }

  protected void refuteListedAsActivatable(final long key, final DirectBuffer type) {
    final List<Long> activatableKeys =
        getActivatableKeys(type, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(activatableKeys).doesNotContain(key);
  }

  protected void assertListedAsTimedOut(final long key, final long since) {
    final List<Long> timedOutKeys = getTimedOutKeys(since);
    assertThat(timedOutKeys).contains(key);
  }

  protected void refuteListedAsTimedOut(final long key, final long since) {
    final List<Long> timedOutKeys = getTimedOutKeys(since);
    assertThat(timedOutKeys).doesNotContain(key);
  }

  protected void assertListedAsBackOff(final long key, final long since) {
    final List<Long> backedOffKeys = getBackedOffKeys(since);
    assertThat(backedOffKeys).contains(key);
  }

  protected void refuteListedAsBackOff(final long key, final long since) {
    final List<Long> backedOffKeys = getBackedOffKeys(since);
    assertThat(backedOffKeys).doesNotContain(key);
  }

  protected List<Long> getActivatableKeys(final DirectBuffer type, final String... tenantIds) {
    final List<Long> activatableKeys = new ArrayList<>();

    jobState.forEachActivatableJobs(type, List.of(tenantIds), (k, e) -> activatableKeys.add(k));
    return activatableKeys;
  }

  protected List<Long> getTimedOutKeys(final long since) {
    final List<Long> timedOutKeys = new ArrayList<>();

    jobState.forEachTimedOutEntry(since, null, (k, e) -> timedOutKeys.add(k));
    return timedOutKeys;
  }

  protected List<Long> getBackedOffKeys(final long since) {
    final List<Long> backedOffKeys = new ArrayList<>();
    jobState.findBackedOffJobs(since, (k, record) -> backedOffKeys.add(k));
    return backedOffKeys;
  }

  private void backOffJob(final long key, final long recurringTime) {
    final JobRecord jobRecord =
        newJobRecord().setRetries(1).setRetryBackoff(recurringTime).setRecurringTime(recurringTime);
    createAndActivateJobRecord(key, jobRecord);
    failJob(key, jobRecord);
  }

  private void parkJobForSecretResolution(final long key, final JobRecord record) {
    createJob(key, record);
    jobState.parkForSecretResolution(key, record);
  }
}
