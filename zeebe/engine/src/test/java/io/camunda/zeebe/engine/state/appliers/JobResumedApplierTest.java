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
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobResumedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private MutableSuspensionState suspensionState;
  private JobSuspendedApplier suspendedApplier;
  private JobResumedApplier applier;

  @BeforeEach
  public void setup() {
    jobState = processingState.getJobState();
    suspensionState = processingState.getSuspensionState();
    suspendedApplier = new JobSuspendedApplier(processingState);
    applier = new JobResumedApplier(processingState);
  }

  @Test
  void shouldMakeParkedJobActivatableWithItsPriority() {
    // given
    final long jobKey = 1L;
    final int priority = 42;
    final var record = jobRecord().setPriority(priority);
    createActivatableJob(jobKey, record);
    suspendedApplier.applyState(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATABLE);
    assertThat(jobState.getJob(jobKey).getPriority()).isEqualTo(priority);
    assertThat(isServedAsActivatable(jobKey)).isTrue();
  }

  @Test
  void shouldIndexWithStoredAttributesWhenEventRecordDiffers() {
    // given - index key is type/tenant/priority of the stored job; a mismatched event value must
    // not insert under the wrong attributes
    final long jobKey = 2L;
    final int priority = 42;
    final var stored = jobRecord().setPriority(priority);
    createActivatableJob(jobKey, stored);
    suspendedApplier.applyState(jobKey, stored);
    final var mismatchedEvent = jobRecord().setPriority(1);

    // when
    applier.applyState(jobKey, mismatchedEvent);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATABLE);
    assertThat(jobState.getJob(jobKey).getPriority()).isEqualTo(priority);
    assertThat(isServedAsActivatable(jobKey)).isTrue();
    // removing with the stored attributes must hit the entry that was written
    jobState.makeJobNotActivatable(jobKey, jobState.getJob(jobKey));
    assertThat(isServedAsActivatable(jobKey)).isFalse();
  }

  @Test
  void shouldLeaveJobThatWasNeverSuspendedAlone() {
    // given
    final long jobKey = 3L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    jobState.activate(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATED);
    assertThat(isServedAsActivatable(jobKey)).isFalse();
  }

  @Test
  void shouldLeaveUnknownJobAlone() {
    // given
    final long jobKey = 999L;
    final var record = jobRecord();

    // when / then - no exception
    applier.applyState(jobKey, record);
    assertThat(jobState.getState(jobKey)).isEqualTo(State.NOT_FOUND);
  }

  @Test
  void shouldMakeSecretWaitingJobActivatableAfterSuspendOverride() {
    // given - suspend overrides WAITING_FOR_SECRET_RESOLUTION; resume restores ACTIVATABLE. If
    // secrets are still needed, the next activation path re-parks for resolution.
    final long jobKey = 4L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    jobState.parkForSecretResolution(jobKey, record);
    suspendedApplier.applyState(jobKey, record);
    assertThat(jobState.getState(jobKey)).isEqualTo(State.SUSPENDED);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATABLE);
    assertThat(isServedAsActivatable(jobKey)).isTrue();
  }

  @Test
  void shouldAdvanceResumeCursorToTheResumedJobKey() {
    // given
    final long jobKey = 5L;
    final long processInstanceKey = 100L;
    final var record = jobRecord().setProcessInstanceKey(processInstanceKey);
    createActivatableJob(jobKey, record);
    suspendedApplier.applyState(jobKey, record);
    suspensionState.setSuspensionState(processInstanceKey, SuspensionState.State.RESUMING);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(suspensionState.getLastResumedJobKey(processInstanceKey)).isEqualTo(jobKey);
  }

  @Test
  void shouldAdvanceCursorUsingStoredProcessInstanceKeyWhenEventRecordDiffers() {
    // given - a mismatched event value must not advance the wrong process instance's cursor
    final long jobKey = 6L;
    final long storedProcessInstanceKey = 100L;
    final var stored = jobRecord().setProcessInstanceKey(storedProcessInstanceKey);
    createActivatableJob(jobKey, stored);
    suspendedApplier.applyState(jobKey, stored);
    suspensionState.setSuspensionState(storedProcessInstanceKey, SuspensionState.State.RESUMING);
    final var mismatchedEvent = jobRecord().setProcessInstanceKey(200L);

    // when
    applier.applyState(jobKey, mismatchedEvent);

    // then
    assertThat(suspensionState.getLastResumedJobKey(storedProcessInstanceKey)).isEqualTo(jobKey);
  }

  @Test
  void shouldNotAdvanceCursorForAJobThatWasNeverSuspended() {
    // given
    final long jobKey = 7L;
    final long processInstanceKey = 100L;
    final var record = jobRecord().setProcessInstanceKey(processInstanceKey);
    createActivatableJob(jobKey, record);
    jobState.activate(jobKey, record);
    suspensionState.setSuspensionState(processInstanceKey, SuspensionState.State.RESUMING);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(suspensionState.getLastResumedJobKey(processInstanceKey)).isLessThan(0);
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
