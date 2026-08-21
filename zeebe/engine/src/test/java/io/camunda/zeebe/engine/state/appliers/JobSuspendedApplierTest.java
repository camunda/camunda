/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobSuspendedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private JobSuspendedApplier applier;

  @BeforeEach
  public void setup() {
    jobState = processingState.getJobState();
    applier = new JobSuspendedApplier(processingState);
  }

  @Test
  void shouldParkActivatableJob() {
    // given
    final long jobKey = 1L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.SUSPENDED);
    assertThat(isServedAsActivatable(jobKey)).isFalse();
  }

  @Test
  void shouldLeaveActivatedJobAlone() {
    // given
    final long jobKey = 2L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    jobState.activate(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATED);
  }

  @Test
  void shouldLeaveAlreadyParkedJobAlone() {
    // given
    final long jobKey = 3L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    applier.applyState(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.SUSPENDED);
    assertThat(isServedAsActivatable(jobKey)).isFalse();
  }

  @Test
  void shouldOverrideJobWaitingForSecretResolution() {
    // given - suspension must win over secret-waiting so a later secret reactivation cannot
    // re-insert the job while the process instance is still suspended
    final long jobKey = 4L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    jobState.parkForSecretResolution(jobKey, record);
    assertThat(jobState.getState(jobKey)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.SUSPENDED);
    assertThat(isServedAsActivatable(jobKey)).isFalse();
  }

  @Test
  void shouldLeaveFailedJobAlone() {
    // given
    final long jobKey = 5L;
    final var record = jobRecord().setRetries(0);
    createActivatableJob(jobKey, record);
    jobState.activate(jobKey, record);
    jobState.updateJobState(jobKey, State.FAILED);
    jobState.makeJobNotActivatable(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.FAILED);
  }

  @Test
  void shouldLeaveErrorThrownJobAlone() {
    // given
    final long jobKey = 6L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    jobState.activate(jobKey, record);
    jobState.throwError(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ERROR_THROWN);
  }

  @Test
  void shouldRemoveIndexEntryUsingStoredJobWhenEventRecordDiffers() {
    // given - index key is type/tenant/priority of the stored job; a mismatched event value must
    // not leave a ghost activatable entry behind
    final long jobKey = 7L;
    final var stored = jobRecord().setPriority(50);
    createActivatableJob(jobKey, stored);
    final var mismatchedEvent = jobRecord().setPriority(1);

    // when
    applier.applyState(jobKey, mismatchedEvent);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.SUSPENDED);
    assertThat(isServedAsActivatable(jobKey)).isFalse();
  }

  @Test
  void shouldIndexJobByProcessInstance() {
    // given
    final long jobKey = 8L;
    final long processInstanceKey = 100L;
    final var record = jobRecord().setProcessInstanceKey(processInstanceKey);
    createActivatableJob(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(visitedJobsOfProcessInstance(processInstanceKey)).containsExactly(jobKey);
  }

  @Test
  void shouldIndexUsingStoredJobsProcessInstanceKeyWhenEventRecordDiffers() {
    // given - a job created via the deprecated path, so it is unindexed and the only index entry
    // can come from the suspension backfill; the backfill must key off the stored job, so a
    // mismatched event value cannot desync the index from what is actually persisted
    final long jobKey = 9L;
    final long storedProcessInstanceKey = 100L;
    final var stored = jobRecord().setProcessInstanceKey(storedProcessInstanceKey);
    jobState.create(jobKey, stored);
    final var mismatchedEvent = jobRecord().setProcessInstanceKey(200L);

    // when
    applier.applyState(jobKey, mismatchedEvent);

    // then
    assertThat(visitedJobsOfProcessInstance(storedProcessInstanceKey)).containsExactly(jobKey);
    assertThat(visitedJobsOfProcessInstance(200L)).isEmpty();
  }

  private List<Long> visitedJobsOfProcessInstance(final long processInstanceKey) {
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

  private void createActivatableJob(final long jobKey, final JobRecord record) {
    jobState.insertJobRecordActivatable(jobKey, record);
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), jobKey, record.getTenantId(), record.getPriority());
  }

  private static JobRecord jobRecord() {
    return new JobRecord()
        .setType("type")
        .setRetries(3)
        .setPriority(50)
        .setDeadline(256L)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private boolean isServedAsActivatable(final long key) {
    final var found = new boolean[] {false};
    jobState.forEachActivatableJobs(
        BufferUtil.wrapString("type"),
        List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER),
        (jobKey, job) -> {
          if (jobKey == key) {
            found[0] = true;
          }
          return true;
        });
    return found[0];
  }
}
