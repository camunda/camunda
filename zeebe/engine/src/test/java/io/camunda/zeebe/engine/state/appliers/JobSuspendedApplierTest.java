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
    jobState.create(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.SUSPENDED);
  }

  @Test
  void shouldLeaveActivatedJobAlone() {
    // given
    final long jobKey = 2L;
    final var record = jobRecord();
    jobState.create(jobKey, record);
    jobState.activate(jobKey, record);

    // when
    applier.applyState(jobKey, record);

    // then
    assertThat(jobState.getState(jobKey)).isEqualTo(State.ACTIVATED);
  }

  private static JobRecord jobRecord() {
    return new JobRecord()
        .setType("type")
        .setRetries(3)
        .setPriority(50)
        .setDeadline(256L)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }
}
