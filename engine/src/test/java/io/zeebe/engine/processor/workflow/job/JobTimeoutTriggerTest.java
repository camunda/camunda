/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Optional;
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

    final JobState jobState = stateRule.getZeebeState().getJobState();
    jobTimeoutTrigger = new JobTimeoutTrigger(jobState);

    final ProcessingContext processingContext =
        new ProcessingContext().actor(someActor).logStreamWriter(typedStreamWriter);
    jobTimeoutTrigger.onRecovered(processingContext);

    jobState.activate(0, newJobRecord());
    jobState.activate(1, newJobRecord());
    jobState.activate(2, newJobRecord());
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
    final CompletableActorFuture<Long> future = new CompletableActorFuture<>();
    future.complete(1L);
    when(typedStreamWriter.flush()).thenReturn(Optional.of(future), Optional.empty());

    // when
    jobTimeoutTrigger.deactivateTimedOutJobs();

    // then
    final InOrder inOrder = Mockito.inOrder(typedStreamWriter);

    inOrder
        .verify(typedStreamWriter)
        .appendFollowUpCommand(eq(0L), eq(JobIntent.TIME_OUT), any(JobRecord.class), any());
    inOrder.verify(typedStreamWriter).flush();
    inOrder
        .verify(typedStreamWriter)
        .appendFollowUpCommand(eq(1L), eq(JobIntent.TIME_OUT), any(JobRecord.class), any());
    inOrder.verify(typedStreamWriter).flush();
    inOrder.verify(typedStreamWriter).reset();
    inOrder.verifyNoMoreInteractions();
  }
}
