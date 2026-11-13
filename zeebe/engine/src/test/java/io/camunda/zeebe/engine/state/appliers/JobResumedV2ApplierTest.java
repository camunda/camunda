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
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class JobResumedV2ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private IncidentCreatedV2Applier incidentCreatedApplier;
  private JobSuspendedApplier suspendedApplier;
  private JobResumedV2Applier applier;

  @BeforeEach
  void setup() {
    jobState = processingState.getJobState();
    incidentCreatedApplier =
        new IncidentCreatedV2Applier(processingState.getIncidentState(), jobState);
    suspendedApplier = new JobSuspendedApplier(processingState);
    applier = new JobResumedV2Applier(processingState);
  }

  @Test
  void shouldKeepJobWithOpenSecretIncidentWaitingForTheIncident() {
    // given - the incident parks the job, and suspension then overrides the waiting state
    final long jobKey = 1L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    incidentCreatedApplier.applyState(10L, secretIncident(jobKey));
    suspendedApplier.applyState(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);
    assertThat(isServedAsActivatable(jobKey)).isFalse();
  }

  @Test
  void shouldMakeParkedJobActivatableWithItsPriority() {
    // given
    final long jobKey = 2L;
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
  void shouldMakeSecretWaitingJobWithoutIncidentActivatable() {
    // given - a job still waiting for its secrets has no incident, so the next activation re-parks
    // it for resolution if the secrets are still missing
    final long jobKey = 3L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    jobState.parkForSecretResolution(jobKey, record);
    suspendedApplier.applyState(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATABLE);
    assertThat(isServedAsActivatable(jobKey)).isTrue();
  }

  @Test
  void shouldMakeSuspendedJobWithOtherIncidentActivatable() {
    // given - resolving any other incident type does not hand out a waiting job, so resume must
    // not park it
    final long jobKey = 5L;
    final var record = jobRecord();
    createActivatableJob(jobKey, record);
    incidentCreatedApplier.applyState(
        11L, secretIncident(jobKey).setErrorType(ErrorType.JOB_NO_RETRIES));
    suspendedApplier.applyState(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATABLE);
    assertThat(isServedAsActivatable(jobKey)).isTrue();
  }

  @Test
  void shouldNotModifyJobNotInSuspendedState() {
    // given
    final long jobKey = 4L;
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

  private static IncidentRecord secretIncident(final long jobKey) {
    return new IncidentRecord()
        .setErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
        .setJobKey(jobKey)
        .setElementId(BufferUtil.wrapString("elementId"));
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
