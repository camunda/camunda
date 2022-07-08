/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.util.ZeebeStateRule;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.streamprocessor.StreamProcessorContext;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

public final class JobTimeoutTriggerTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  @Mock private ActorControl someActor;

  @Mock private TypedStreamWriter typedStreamWriter;
  private JobTimeoutTrigger jobTimeoutTrigger;

  @Before
  public void setUp() {
    initMocks(this);

    final MutableJobState jobState = stateRule.getZeebeState().getJobState();
    jobTimeoutTrigger = new JobTimeoutTrigger(jobState);

    final StreamProcessorContext streamProcessorContext =
        new StreamProcessorContext().actor(someActor).logStreamWriter(typedStreamWriter);
    streamProcessorContext.enableLogStreamWriter();
    jobTimeoutTrigger.onRecovered(streamProcessorContext);

    IntStream.range(0, 3)
        .forEach(
            (i) -> {
              final var job = newJobRecord();
              jobState.create(i, job);
              jobState.activate(i, job);
            });
  }

  private JobRecord newJobRecord() {
    final JobRecord jobRecord = new JobRecord();

    jobRecord.setRetries(2);
    jobRecord.setDeadline(256L);
    jobRecord.setType("test");

    return jobRecord;
  }

  @Test
  public void shouldNotWriteAgainAfterFlushFailed() {
    // given
    when(typedStreamWriter.flush()).thenReturn(1L, -1L);

    // when
    jobTimeoutTrigger.deactivateTimedOutJobs();

    // then
    final InOrder inOrder = Mockito.inOrder(typedStreamWriter);

    inOrder.verify(typedStreamWriter).reset();
    inOrder
        .verify(typedStreamWriter)
        .appendFollowUpCommand(eq(0L), eq(JobIntent.TIME_OUT), any(JobRecord.class));
    inOrder.verify(typedStreamWriter).flush();
    inOrder.verify(typedStreamWriter).reset();
    inOrder
        .verify(typedStreamWriter)
        .appendFollowUpCommand(eq(1L), eq(JobIntent.TIME_OUT), any(JobRecord.class));
    inOrder.verify(typedStreamWriter).flush();
    inOrder.verifyNoMoreInteractions();
  }
}
