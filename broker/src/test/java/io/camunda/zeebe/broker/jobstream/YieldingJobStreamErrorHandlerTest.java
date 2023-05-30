/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        new ActivatedJobImpl().setJobKey(1L).setRecord(new JobRecord());

    // when
    errorHandler.handleError(
        activatedJob, new RuntimeException("job push failed"), mockTaskResultBuilder);

    // then
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(1L), eq(JobIntent.YIELD), eq(activatedJob.jobRecord()));
  }
}
