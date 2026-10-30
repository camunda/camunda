/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class JobPriorityUpdatedApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private JobPriorityUpdatedApplier applier;

  @BeforeEach
  public void setup() {
    jobState = processingState.getJobState();
    applier = new JobPriorityUpdatedApplier(processingState);
  }

  @Test
  void shouldUpdateJobPriorityInState() {
    // given
    final long jobKey = 1L;
    final int newPriority = 77;
    final var jobRecord =
        new JobRecord()
            .setRetries(2)
            .setDeadline(256L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.create(jobKey, jobRecord);

    final var updatedRecord = new JobRecord();
    updatedRecord.setPriority(newPriority);

    // when
    applier.applyState(jobKey, updatedRecord);

    // then
    assertThat(jobState.getJob(jobKey).getPriority()).isEqualTo(newPriority);
  }

  @Test
  void shouldIgnoreApplyStateIfJobNotFound() {
    // given
    final long unknownJobKey = 999L;
    final var jobRecord = new JobRecord().setPriority(42);

    // when / then - no exception thrown
    applier.applyState(unknownJobKey, jobRecord);
  }
}
