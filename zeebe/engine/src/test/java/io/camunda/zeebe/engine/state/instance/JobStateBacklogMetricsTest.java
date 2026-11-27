/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Verifies that the {@code zeebe.job.activatable.count} gauge always equals the number of jobs in
 * an activatable index, across the legacy {@code JOB_ACTIVATABLE} column family (reached through
 * the deprecated {@code create} path) and {@code JOB_ACTIVATABLE_BY_PRIORITY} (reached through
 * {@code insertJobRecordActivatable} + {@code makeJobActivatableByPriority}).
 */
@ExtendWith(ProcessingStateExtension.class)
final class JobStateBacklogMetricsTest {

  private static final String BACKLOG_METRIC = "zeebe.job.activatable.count";

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableProcessingState processingState;
  private MutableJobState jobState;
  private String type;

  @BeforeEach
  void setUp(final TestInfo testInfo) {
    jobState = processingState.getJobState();
    type = testInfo.getTestMethod().orElseThrow().getName();
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldCountEveryJobMadeActivatable(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();

    // when
    index.makeActivatable(jobState, 1L, record);
    index.makeActivatable(jobState, 2L, newJobRecord());

    // then
    assertThat(backlog()).isEqualTo(2.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldNotCountJobsOfOtherTypes(final ActivatableIndex index) {
    // given
    final var otherType = type + "-other";

    // when
    index.makeActivatable(jobState, 1L, newJobRecord());
    index.makeActivatable(jobState, 2L, newJobRecord().setType(otherType));

    // then
    assertThat(backlog()).isEqualTo(1.0);
    assertThat(backlog(otherType)).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldDecrementBacklogWhenJobIsActivated(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();
    index.makeActivatable(jobState, 1L, record);
    index.makeActivatable(jobState, 2L, newJobRecord());

    // when
    jobState.activate(1L, record);

    // then
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldNotCountJobFailedWithBackoffUntilItRecurs(final ActivatableIndex index) {
    // given
    final var record = newJobRecord().setRetries(1).setRetryBackoff(100).setRecurringTime(100);
    index.makeActivatable(jobState, 1L, record);
    jobState.activate(1L, record);

    // when
    jobState.fail(1L, record);

    // then
    assertThat(backlog()).isZero();

    // when
    jobState.recurAfterBackoff(1L, record);

    // then
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldCountJobAgainWhenItIsFailedWithoutBackoff(final ActivatableIndex index) {
    // given
    final var record = newJobRecord().setRetries(1).setRetryBackoff(0);
    index.makeActivatable(jobState, 1L, record);
    jobState.activate(1L, record);

    // when
    jobState.fail(1L, record);

    // then
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldNotCountJobFailedWithoutRetries(final ActivatableIndex index) {
    // given
    final var record = newJobRecord().setRetries(0);
    index.makeActivatable(jobState, 1L, record);
    jobState.activate(1L, record);

    // when
    jobState.fail(1L, record);

    // then
    assertThat(backlog()).isZero();
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldCountJobAgainWhenItIsYielded(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();
    index.makeActivatable(jobState, 1L, record);
    jobState.activate(1L, record);
    assertThat(backlog()).isZero();

    // when
    jobState.yield(1L, record);

    // then
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldCountJobAgainWhenItTimesOut(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();
    index.makeActivatable(jobState, 1L, record);
    jobState.activate(1L, record);
    assertThat(backlog()).isZero();

    // when
    jobState.timeout(1L, record);

    // then
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldDecrementBacklogWhenActivatableJobIsCanceled(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();
    index.makeActivatable(jobState, 1L, record);
    index.makeActivatable(jobState, 2L, newJobRecord());

    // when
    jobState.cancel(1L, record);

    // then
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldDecrementBacklogWhenActivatableJobIsDeleted(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();
    index.makeActivatable(jobState, 1L, record);

    // when
    jobState.delete(1L, record);

    // then
    assertThat(backlog()).isZero();
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldDecrementBacklogWhenJobIsMadeNotActivatable(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();
    index.makeActivatable(jobState, 1L, record);

    // when
    jobState.makeJobNotActivatable(1L, record);

    // then
    assertThat(backlog()).isZero();
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldNotChangeBacklogWhenActivatedJobIsCompleted(final ActivatableIndex index) {
    // given - one job still waiting, one activated by a worker
    final var activated = newJobRecord();
    index.makeActivatable(jobState, 1L, activated);
    index.makeActivatable(jobState, 2L, newJobRecord());
    jobState.activate(1L, activated);

    // when
    jobState.complete(1L, activated);

    // then - the completed job was not in an index, so the waiting job still counts
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldNotChangeBacklogWhenRemovingJobThatIsNotActivatable(final ActivatableIndex index) {
    // given - job 1 is activated (already out of the index), job 2 still waits
    final var activated = newJobRecord();
    index.makeActivatable(jobState, 1L, activated);
    index.makeActivatable(jobState, 2L, newJobRecord());
    jobState.activate(1L, activated);

    // when - removal paths run for a job that is no longer in an index
    jobState.cancel(1L, activated);
    jobState.makeJobNotActivatable(1L, activated);

    // then - no drift: the gauge must not go down for a removal that removed nothing
    assertThat(backlog()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldNotDriftWhenSameJobIsMadeActivatableTwice(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();
    index.makeActivatable(jobState, 1L, record);

    // when - re-making an already activatable job is an upsert, not a second job
    index.makeActivatableAgain(jobState, 1L, record);

    // then
    assertThat(backlog()).isEqualTo(1.0);

    // when - a single removal empties the index, so the gauge must not stay at 1
    jobState.activate(1L, record);

    // then
    assertThat(backlog()).isZero();
  }

  @ParameterizedTest
  @EnumSource(ActivatableIndex.class)
  void shouldReturnToZeroAfterFullJobLifecycle(final ActivatableIndex index) {
    // given
    final var record = newJobRecord();

    // when
    index.makeActivatable(jobState, 1L, record);
    jobState.activate(1L, record);
    jobState.yield(1L, record);
    jobState.activate(1L, record);
    jobState.timeout(1L, record);
    jobState.cancel(1L, record);

    // then
    assertThat(backlog()).isZero();
  }

  @Test
  void shouldNotCountJobParkedForSecretResolutionUntilItIsReleased() {
    // given
    final var record = newJobRecord();
    ActivatableIndex.BY_PRIORITY.makeActivatable(jobState, 1L, record);
    assertThat(backlog()).isEqualTo(1.0);

    // when
    jobState.parkForSecretResolution(1L, record);

    // then
    assertThat(backlog()).isZero();

    // when
    jobState.makeActivatableAfterSecretResolution(1L);

    // then
    assertThat(backlog()).isEqualTo(1.0);
  }

  @Test
  void shouldKeepBacklogUnchangedWhenReseededOnRecovery() {
    // given - jobs spread over both column families and one already activated
    final var activated = newJobRecord();
    ActivatableIndex.LEGACY.makeActivatable(jobState, 1L, activated);
    ActivatableIndex.LEGACY.makeActivatable(jobState, 2L, newJobRecord());
    ActivatableIndex.BY_PRIORITY.makeActivatable(jobState, 3L, newJobRecord());
    jobState.activate(1L, activated);
    assertThat(backlog()).isEqualTo(2.0);

    // when
    recover();

    // then
    assertThat(backlog()).isEqualTo(2.0);
  }

  @Test
  void shouldReportZeroAfterRecoveryWhenAllJobsLeftTheIndexes() {
    // given
    final var legacy = newJobRecord();
    final var byPriority = newJobRecord();
    ActivatableIndex.LEGACY.makeActivatable(jobState, 1L, legacy);
    ActivatableIndex.BY_PRIORITY.makeActivatable(jobState, 2L, byPriority);
    jobState.activate(1L, legacy);
    jobState.activate(2L, byPriority);

    // when
    recover();

    // then - a type that drained must read 0 (or have no series), not keep a stale level
    assertThat(backlogOrZero(type)).isZero();
  }

  @Test
  void shouldSeparateTypesWhenReseededOnRecovery() {
    // given
    final var otherType = type + "-other";
    ActivatableIndex.LEGACY.makeActivatable(jobState, 1L, newJobRecord());
    ActivatableIndex.BY_PRIORITY.makeActivatable(jobState, 2L, newJobRecord());
    ActivatableIndex.BY_PRIORITY.makeActivatable(jobState, 3L, newJobRecord().setType(otherType));

    // when
    recover();

    // then
    assertThat(backlog()).isEqualTo(2.0);
    assertThat(backlog(otherType)).isEqualTo(1.0);
  }

  @Test
  void shouldReseedBacklogFromPersistedStateWhenProcessingStateRecovers() {
    // given - the gauges of the processing state's job state are registered and read 0
    final var registered = newJobRecord();
    ActivatableIndex.BY_PRIORITY.makeActivatable(jobState, 1L, registered);
    jobState.makeJobNotActivatable(1L, registered);
    assertThat(backlogOrZero(type)).isZero();

    // and a second job state on the same database persists jobs behind the first one's back; the
    // first-registered gauge wins in the registry, so the processing state's gauge stays stale
    final var otherJobState = new DbJobState(zeebeDb, transactionContext);
    final int priorityJobs = 3;
    final int legacyJobs = 2;
    for (int i = 0; i < priorityJobs; i++) {
      ActivatableIndex.BY_PRIORITY.makeActivatable(otherJobState, 100L + i, newJobRecord());
    }
    for (int i = 0; i < legacyJobs; i++) {
      ActivatableIndex.LEGACY.makeActivatable(otherJobState, 200L + i, newJobRecord());
    }
    assertThat(backlogOrZero(type)).isZero();

    // when - the partition recovers through the processing state
    final var context = mock(ReadonlyStreamProcessorContext.class);
    final var clock = mock(StreamClock.class);
    when(context.getClock()).thenReturn(clock);
    ((ProcessingDbState) processingState).onRecovered(context);

    // then - the persisted jobs of both column families are counted
    assertThat(backlog()).isEqualTo(priorityJobs + legacyJobs);
  }

  private void recover() {
    ((DbJobState) jobState).onRecovered(null);
  }

  private double backlog() {
    return backlog(type);
  }

  /** An absent series means zero: a type without backlog may have no gauge after recovery. */
  private double backlogOrZero(final String jobType) {
    final var gauge = zeebeDb.getMeterRegistry().find(BACKLOG_METRIC).tag("type", jobType).gauge();
    return gauge == null ? 0.0 : gauge.value();
  }

  private double backlog(final String jobType) {
    return zeebeDb.getMeterRegistry().get(BACKLOG_METRIC).tag("type", jobType).gauge().value();
  }

  private JobRecord newJobRecord() {
    return new JobRecord()
        .setRetries(2)
        .setDeadline(256L)
        .setType(type)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  /** The column family through which a job becomes activatable. */
  private enum ActivatableIndex {
    /** Legacy {@code JOB_ACTIVATABLE}, populated by the deprecated create path. */
    LEGACY {
      @Override
      @SuppressWarnings("deprecation")
      void makeActivatable(final MutableJobState state, final long key, final JobRecord record) {
        state.create(key, record);
      }

      @Override
      @SuppressWarnings("deprecation")
      void makeActivatableAgain(
          final MutableJobState state, final long key, final JobRecord record) {
        // re-runs the deprecated makeJobActivatable on a job that is already activatable
        state.yield(key, record);
      }
    },

    /** {@code JOB_ACTIVATABLE_BY_PRIORITY}, populated by the current creation path. */
    BY_PRIORITY {
      @Override
      void makeActivatable(final MutableJobState state, final long key, final JobRecord record) {
        state.insertJobRecordActivatable(key, record);
        state.makeJobActivatableByPriority(
            record.getTypeBuffer(), key, record.getTenantId(), record.getPriority());
      }

      @Override
      void makeActivatableAgain(
          final MutableJobState state, final long key, final JobRecord record) {
        state.makeJobActivatableByPriority(
            record.getTypeBuffer(), key, record.getTenantId(), record.getPriority());
      }
    };

    abstract void makeActivatable(MutableJobState state, long key, JobRecord record);

    /** Makes an already activatable job activatable again without recreating the job. */
    abstract void makeActivatableAgain(MutableJobState state, long key, JobRecord record);
  }
}
