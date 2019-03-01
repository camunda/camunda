/*
 * Zeebe Broker Core
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

package io.zeebe.broker.workflow.processor.timer;

import static io.zeebe.broker.workflow.state.TimerInstance.NO_ELEMENT_INSTANCE;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.model.element.AbstractFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.processor.CatchEventBehavior;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.TimerInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public class TriggerTimerProcessor implements TypedRecordProcessor<TimerRecord> {
  private static final String NO_TIMER_FOUND_MESSAGE =
      "Expected to trigger timer with key '%d', but no such timer was found";
  private static final String NO_ACTIVE_TIMER_MESSAGE =
      "Expected to trigger a timer with key '%d', but the timer is not active anymore";

  private final CatchEventBehavior catchEventBehavior;
  private final WorkflowState workflowState;
  private final WorkflowInstanceRecord eventOccurredRecord = new WorkflowInstanceRecord();

  public TriggerTimerProcessor(
      final WorkflowState workflowState, CatchEventBehavior catchEventBehavior) {
    this.workflowState = workflowState;
    this.catchEventBehavior = catchEventBehavior;
  }

  @Override
  public void processRecord(
      TypedRecord<TimerRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final TimerRecord timer = record.getValue();
    final long elementInstanceKey = timer.getElementInstanceKey();

    final TimerInstance timerInstance =
        workflowState.getTimerState().get(elementInstanceKey, record.getKey());
    if (timerInstance == null) {
      streamWriter.appendRejection(
          record, RejectionType.NOT_FOUND, String.format(NO_TIMER_FOUND_MESSAGE, record.getKey()));
      return;
    }
    workflowState.getTimerState().remove(timerInstance);

    processTimerTrigger(record, streamWriter, timer, elementInstanceKey);
  }

  private void processTimerTrigger(
      TypedRecord<TimerRecord> record,
      TypedStreamWriter streamWriter,
      TimerRecord timer,
      long elementInstanceKey) {

    final boolean wasActiveTimer = tryTriggerTimer(streamWriter, timer, elementInstanceKey);

    if (wasActiveTimer) {
      final long eventOccuredKey =
          prepareEventOccuredEvent(streamWriter, timer, elementInstanceKey);

      streamWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);
      streamWriter.appendFollowUpEvent(
          eventOccuredKey, WorkflowInstanceIntent.EVENT_OCCURRED, eventOccurredRecord);

      if (shouldReschedule(timer)) {
        final ExecutableCatchEventElement timerEvent = getTimerEvent(elementInstanceKey, timer);

        if (timerEvent != null && timerEvent.isTimer()) {
          rescheduleTimer(timer, streamWriter, timerEvent);
        }
      }
    } else {
      streamWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(NO_ACTIVE_TIMER_MESSAGE, record.getKey()));
    }
  }

  private boolean tryTriggerTimer(
      TypedStreamWriter streamWriter, TimerRecord timer, long elementInstanceKey) {
    boolean wasActiveTimer = true;

    final boolean isNoTimerStartEvent = !isTimerStartEvent(elementInstanceKey);
    if (isNoTimerStartEvent) {
      final long eventKey = streamWriter.getKeyGenerator().nextKey();
      wasActiveTimer =
          workflowState
              .getEventScopeInstanceState()
              .triggerEvent(
                  timer.getElementInstanceKey(),
                  eventKey,
                  timer.getHandlerNodeId(),
                  WorkflowInstanceRecord.EMPTY_PAYLOAD);
    }
    return wasActiveTimer;
  }

  private long prepareEventOccuredEvent(
      TypedStreamWriter streamWriter, TimerRecord timer, long elementInstanceKey) {
    final long eventOccuredKey;

    eventOccurredRecord.reset();
    if (isTimerStartEvent(elementInstanceKey)) {
      eventOccuredKey = streamWriter.getKeyGenerator().nextKey();
      eventOccurredRecord
          .setBpmnElementType(BpmnElementType.START_EVENT)
          .setWorkflowKey(timer.getWorkflowKey())
          .setElementId(timer.getHandlerNodeId())
          .setPayload(WorkflowInstanceRecord.EMPTY_PAYLOAD);
    } else {
      eventOccuredKey = elementInstanceKey;
      eventOccurredRecord.wrap(
          workflowState.getElementInstanceState().getInstance(elementInstanceKey).getValue());
    }
    return eventOccuredKey;
  }

  private boolean isTimerStartEvent(long elementInstanceKey) {
    return elementInstanceKey == NO_ELEMENT_INSTANCE;
  }

  private boolean shouldReschedule(TimerRecord timer) {
    return timer.getRepetitions() == RepeatingInterval.INFINITE || timer.getRepetitions() > 1;
  }

  private ExecutableCatchEventElement getTimerEvent(long elementInstanceKey, TimerRecord timer) {
    if (isTimerStartEvent(elementInstanceKey)) {
      final List<ExecutableCatchEventElement> startEvents =
          workflowState.getWorkflowByKey(timer.getWorkflowKey()).getWorkflow().getStartEvents();

      for (ExecutableCatchEventElement startEvent : startEvents) {
        if (startEvent.getId().equals(timer.getHandlerNodeId())) {
          return startEvent;
        }
      }
    } else {
      final ElementInstance elementInstance =
          workflowState.getElementInstanceState().getInstance(elementInstanceKey);

      if (elementInstance != null) {
        return getCatchEventById(
            workflowState, elementInstance.getValue().getWorkflowKey(), timer.getHandlerNodeId());
      }
    }
    return null;
  }

  private void rescheduleTimer(
      TimerRecord record, TypedStreamWriter writer, ExecutableCatchEventElement event) {
    if (event.getTimer() == null) {
      final String message =
          String.format(
              "Expected to reschedule repeating timer for element with id '%s', but no timer definition was found",
              BufferUtil.bufferAsString(event.getId()));
      throw new IllegalStateException(message);
    }

    int repetitions = record.getRepetitions();
    if (repetitions != RepeatingInterval.INFINITE) {
      repetitions--;
    }

    final RepeatingInterval timer =
        new RepeatingInterval(repetitions, event.getTimer().getInterval());
    catchEventBehavior.subscribeToTimerEvent(
        record.getElementInstanceKey(),
        record.getWorkflowInstanceKey(),
        record.getWorkflowKey(),
        event.getId(),
        timer,
        writer);
  }

  private ExecutableCatchEventElement getCatchEventById(
      WorkflowState state, long workflowKey, DirectBuffer id) {
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);
    if (workflow == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to reschedule timer in workflow with key '%d', but no such workflow was found",
              workflowKey));
    }

    final AbstractFlowElement element = workflow.getWorkflow().getElementById(id);
    if (element == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to reschedule timer for element with id '%s', but no such element was found",
              bufferAsString(id)));
    }

    if (!(element instanceof ExecutableCatchEventElement)) {
      throw new IllegalStateException(
          String.format(
              "Expected to reschedule timer for element with id '%s', but the element is not a timer catch event",
              workflowKey));
    }

    return (ExecutableCatchEventElement) element;
  }
}
