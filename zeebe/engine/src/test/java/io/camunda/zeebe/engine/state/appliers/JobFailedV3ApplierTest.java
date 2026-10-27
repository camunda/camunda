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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class JobFailedV3ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableJobState jobState;
  private JobFailedV3Applier applier;

  @BeforeEach
  void setup() {
    jobState = processingState.getJobState();
    applier = new JobFailedV3Applier(processingState);
  }

  @Test
  void shouldMakeJobActivatableByPriorityWhenRetriesRemainWithoutBackoff() {
    // given
    final long key = 1L;
    final var jobRecord = createJob(key, 3, 0L);

    // when
    applier.applyState(key, jobRecord);

    // then
    assertThat(jobState.getState(key)).isEqualTo(State.ACTIVATABLE);
    assertThat(isServedAsActivatable(key)).isTrue();
  }

  @Test
  void shouldNotMakeJobActivatableWhenRetriesRemainWithBackoff() {
    // given
    final long key = 2L;
    final var jobRecord = createJob(key, 3, 100L);

    // when
    applier.applyState(key, jobRecord);

    // then
    assertThat(jobState.getState(key)).isEqualTo(State.FAILED);
    assertThat(isServedAsActivatable(key)).isFalse();
  }

  @Test
  void shouldNotMakeJobActivatableWhenNoRetriesRemain() {
    // given
    final long key = 3L;
    final var jobRecord = createJob(key, 0, 0L);

    // when
    applier.applyState(key, jobRecord);

    // then
    assertThat(jobState.getState(key)).isEqualTo(State.FAILED);
    assertThat(isServedAsActivatable(key)).isFalse();
  }

  private JobRecord createJob(final long key, final int retries, final long retryBackoff) {
    final var jobRecord =
        new JobRecord()
            .setRetries(retries)
            .setRetryBackoff(retryBackoff)
            .setRecurringTime(500L)
            .setDeadline(256L)
            .setType("test")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(key, jobRecord);
    return jobRecord;
  }

  private boolean isServedAsActivatable(final long key) {
    final var found = new boolean[] {false};
    jobState.forEachActivatableJobs(
        BufferUtil.wrapString("test"),
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
