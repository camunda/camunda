/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.model.bpmn.util.time.TimeDateTimer;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.record.intent.TimerIntent;
import java.util.Random;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CatchEventBehaviorTest {
  private static final String TIME_DATE = "1990-01-01T00:00:00Z";
  private final Random random = new Random();
  @Mock private ZeebeState zeebeState;
  @Mock private SubscriptionCommandSender commandSender;
  @Mock private TypedStreamWriter streamWriter;
  @Captor private ArgumentCaptor<UnpackedObject> captor;
  private CatchEventBehavior catchEventBehavior;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    catchEventBehavior = new CatchEventBehavior(zeebeState, commandSender, 1);
  }

  @Test
  public void shouldWriteCorrectTimerRecord() {
    // given
    final TimeDateTimer timer = TimeDateTimer.parse(TIME_DATE);
    final long dueDate = timer.getDueDate(0);
    final long repetitions = timer.getRepetitions();
    final long elementInstanceKey = random.nextLong();
    final long workflowKey = random.nextLong();
    final long workflowInstanceKey = random.nextLong();

    final byte[] buffer = new byte[5];
    random.nextBytes(buffer);
    final UnsafeBuffer handlerNodeId = new UnsafeBuffer(buffer);

    // when
    catchEventBehavior.subscribeToTimerEvent(
        elementInstanceKey,
        workflowInstanceKey,
        workflowKey,
        new UnsafeBuffer(handlerNodeId),
        timer,
        streamWriter);

    // then
    Mockito.verify(streamWriter).appendNewCommand(eq(TimerIntent.CREATE), captor.capture());
    final TimerRecord record = (TimerRecord) captor.getValue();

    assertThat(record.getTargetElementIdBuffer()).isEqualTo(handlerNodeId);
    assertThat(record.getElementInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(record.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(record.getRepetitions()).isEqualTo(repetitions);
    assertThat(record.getDueDate()).isEqualTo(dueDate);
  }

  @Test
  public void shouldWriteCorrectCancelTimerRecord() {
    // given
    final TimerInstance timer = new TimerInstance();
    timer.setKey(random.nextLong());
    timer.setDueDate(random.nextLong());
    timer.setRepetitions(random.nextInt());
    timer.setWorkflowKey(random.nextLong());
    timer.setElementInstanceKey(random.nextLong());
    timer.setWorkflowInstanceKey(random.nextLong());

    final byte[] buffer = new byte[5];
    random.nextBytes(buffer);
    timer.setHandlerNodeId(new UnsafeBuffer(buffer));

    // when
    catchEventBehavior.unsubscribeFromTimerEvent(timer, streamWriter);

    // then
    Mockito.verify(streamWriter)
        .appendFollowUpCommand(
            ArgumentMatchers.eq(timer.getKey()), eq(TimerIntent.CANCEL), captor.capture());
    final TimerRecord record = (TimerRecord) captor.getValue();

    assertThat(record.getTargetElementIdBuffer()).isEqualTo(timer.getHandlerNodeId());
    assertThat(record.getElementInstanceKey()).isEqualTo(timer.getElementInstanceKey());
    assertThat(record.getWorkflowKey()).isEqualTo(timer.getWorkflowKey());
    assertThat(record.getWorkflowInstanceKey()).isEqualTo(timer.getWorkflowInstanceKey());
    assertThat(record.getRepetitions()).isEqualTo(timer.getRepetitions());
    assertThat(record.getDueDate()).isEqualTo(timer.getDueDate());
  }
}
