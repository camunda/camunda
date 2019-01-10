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
import static io.zeebe.msgpack.value.DocumentValue.EMPTY_DOCUMENT;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.model.element.AbstractFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.processor.CatchEventBehavior;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.TimerInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public class TriggerTimerProcessor implements TypedRecordProcessor<TimerRecord> {

  private final CatchEventBehavior catchEventBehavior;
  private final WorkflowState workflowState;
  private final WorkflowInstanceRecord startEventRecord = new WorkflowInstanceRecord();

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
          record, RejectionType.NOT_APPLICABLE, "timer is already triggered or canceled");
      return;
    }

    workflowState.getTimerState().remove(timerInstance);

    boolean isOccurred = true;

    if (elementInstanceKey == NO_ELEMENT_INSTANCE) {
      // timer start event
      startEventRecord
          .setWorkflowKey(timer.getWorkflowKey())
          .setElementId(timer.getHandlerNodeId())
          .setPayload(WorkflowInstanceRecord.EMPTY_PAYLOAD);
      streamWriter.appendNewEvent(WorkflowInstanceIntent.EVENT_OCCURRED, startEventRecord);

    } else {
      isOccurred =
          catchEventBehavior.occurEventForElement(
              elementInstanceKey, timer.getHandlerNodeId(), EMPTY_DOCUMENT, streamWriter);
    }

    if (isOccurred) {
      streamWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);

      if (shouldReschedule(timer)) {
        final ExecutableCatchEventElement timerEvent = getTimerEvent(elementInstanceKey, timer);

        if (timerEvent != null && timerEvent.isTimer()) {
          rescheduleTimer(timer, streamWriter, timerEvent);
        }
      }
    } else {
      streamWriter.appendRejection(
          record, RejectionType.NOT_APPLICABLE, "activity is not active anymore");
    }
  }

  private boolean shouldReschedule(TimerRecord timer) {
    return timer.getRepetitions() == RepeatingInterval.INFINITE || timer.getRepetitions() > 1;
  }

  private ExecutableCatchEventElement getTimerEvent(long elementInstanceKey, TimerRecord timer) {
    final ElementInstance elementInstance =
        workflowState.getElementInstanceState().getInstance(elementInstanceKey);

    if (elementInstance != null) {
      return getCatchEventById(
          workflowState, elementInstance.getValue().getWorkflowKey(), timer.getHandlerNodeId());
    } else if (elementInstanceKey == NO_ELEMENT_INSTANCE) {
      final List<ExecutableCatchEventElement> startEvents =
          workflowState.getWorkflowByKey(timer.getWorkflowKey()).getWorkflow().getStartEvents();

      for (ExecutableCatchEventElement startEvent : startEvents) {
        if (startEvent.getId().equals(timer.getHandlerNodeId())) {
          return startEvent;
        }
      }
    }

    return null;
  }

  private void rescheduleTimer(
      TimerRecord record, TypedStreamWriter writer, ExecutableCatchEventElement event) {
    if (event.getTimer() == null) {
      final String message =
          String.format(
              "Missing time cycle from repeating timer's associated boundary event %s",
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
        record.getElementInstanceKey(), record.getWorkflowKey(), event.getId(), timer, writer);
  }

  private ExecutableCatchEventElement getCatchEventById(
      WorkflowState state, long workflowKey, DirectBuffer id) {
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);
    if (workflow == null) {
      throw new IllegalStateException("No workflow found with key: " + workflowKey);
    }

    final AbstractFlowElement element = workflow.getWorkflow().getElementById(id);
    if (element == null) {
      throw new IllegalStateException("No element found with id: " + bufferAsString(id));
    }

    if (!ExecutableCatchEventElement.class.isInstance(element)) {
      throw new IllegalStateException(
          "Element with id " + bufferAsString(id) + " is not a message catch event");
    }

    return (ExecutableCatchEventElement) element;
  }
}
