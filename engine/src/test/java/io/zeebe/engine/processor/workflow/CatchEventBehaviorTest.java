/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import io.zeebe.protocol.intent.TimerIntent;
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

  @Mock private ZeebeState zeebeState;
  @Mock private SubscriptionCommandSender commandSender;
  @Mock private TypedStreamWriter streamWriter;
  @Captor private ArgumentCaptor<UnpackedObject> captor;

  private CatchEventBehavior catchEventBehavior;
  private final Random random = new Random();

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

    assertThat(record.getHandlerNodeId()).isEqualTo(handlerNodeId);
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

    assertThat(record.getHandlerNodeId()).isEqualTo(timer.getHandlerNodeId());
    assertThat(record.getElementInstanceKey()).isEqualTo(timer.getElementInstanceKey());
    assertThat(record.getWorkflowKey()).isEqualTo(timer.getWorkflowKey());
    assertThat(record.getWorkflowInstanceKey()).isEqualTo(timer.getWorkflowInstanceKey());
    assertThat(record.getRepetitions()).isEqualTo(timer.getRepetitions());
    assertThat(record.getDueDate()).isEqualTo(timer.getDueDate());
  }
}
