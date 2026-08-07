/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.test.util.MsgPackUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class YieldingJobStreamErrorHandlerTest {

  private final YieldingJobStreamErrorHandler errorHandler = new YieldingJobStreamErrorHandler();
  private final TaskResultBuilder mockTaskResultBuilder = mock(TaskResultBuilder.class);

  @BeforeEach
  public void setUp() {
    when(mockTaskResultBuilder.appendCommandRecord(anyLong(), any(), any())).thenReturn(true);
  }

  @Test
  public void shouldYieldJob() {
    // given
    final ActivatedJobImpl activatedJob =
        new ActivatedJobImpl()
            .setJobKey(1L)
            .setRecord(
                new JobRecord()
                    .setType("test-type")
                    .setRetries(3)
                    .setBpmnProcessId("process")
                    .setProcessInstanceKey(42L)
                    .setElementId("task")
                    .setElementInstanceKey(43L)
                    .setTenantId("tenant"));

    // when
    errorHandler.handleError(
        activatedJob, new RuntimeException("job push failed"), mockTaskResultBuilder);

    // then - the yielded job keeps the identity the exported command is read by
    final var yieldedJob = ArgumentCaptor.forClass(JobRecord.class);
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(1L), eq(JobIntent.YIELD), yieldedJob.capture());
    assertThat(yieldedJob.getValue())
        .extracting(
            JobRecord::getType,
            JobRecord::getRetries,
            JobRecord::getBpmnProcessId,
            JobRecord::getProcessInstanceKey,
            JobRecord::getElementId,
            JobRecord::getElementInstanceKey,
            JobRecord::getTenantId)
        .containsExactly("test-type", 3, "process", 42L, "task", 43L, "tenant");
  }

  /**
   * Every pushed job is yielded without its variables, whatever they hold. The one that motivates
   * it is a job whose variables carry the values of its secret references, which must not reach the
   * log, but the yield processor takes the job it yields from state either way, so the command only
   * ever needs to name the job.
   */
  @Test
  public void shouldYieldJobWithoutVariables() {
    // given a pushed job with variables
    final JobRecord pushedJob =
        new JobRecord()
            .setType("test-type")
            .setVariables(MsgPackUtil.asMsgPack("token", "resolved-secret"));
    final ActivatedJobImpl activatedJob = new ActivatedJobImpl().setJobKey(1L).setRecord(pushedJob);

    // when
    errorHandler.handleError(
        activatedJob, new RuntimeException("job push failed"), mockTaskResultBuilder);

    // then the yield command does not carry them into the log, and the pushed job keeps them
    final var yieldedJob = ArgumentCaptor.forClass(JobRecord.class);
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(1L), eq(JobIntent.YIELD), yieldedJob.capture());
    assertThat(yieldedJob.getValue().getVariables()).isEmpty();
    assertThat(activatedJob.jobRecord().getVariables()).containsEntry("token", "resolved-secret");
  }
}
