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

import static io.zeebe.msgpack.value.DocumentValue.EMPTY_DOCUMENT;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.model.element.ExecutableBoundaryEvent;
import io.zeebe.broker.workflow.processor.boundary.BoundaryEventActivator;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.ElementInstanceState;
import io.zeebe.broker.workflow.state.StoredRecord;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.broker.workflow.state.TimerInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;

public class TriggerTimerProcessor implements TypedRecordProcessor<TimerRecord> {
  private final BoundaryEventActivator boundaryEventActivator;
  private final WorkflowState workflowState;

  public TriggerTimerProcessor(
      final WorkflowState workflowState, BoundaryEventActivator boundaryEventActivator) {
    this.workflowState = workflowState;
    this.boundaryEventActivator = boundaryEventActivator;
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

    final ElementInstanceState elementInstanceState = workflowState.getElementInstanceState();
    final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

    workflowState.getTimerState().remove(timerInstance);
    streamWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);

    // TODO handler trigger events in a uniform way - #1699

    if (elementInstance != null
        && elementInstance.getState() == WorkflowInstanceIntent.ELEMENT_ACTIVATED) {
      if (boundaryEventActivator.shouldActivateBoundaryEvent(
          elementInstance, timer.getHandlerNodeId())) {
        final ExecutableBoundaryEvent event =
            boundaryEventActivator.getBoundaryEventById(
                workflowState,
                elementInstance.getValue().getWorkflowKey(),
                timer.getHandlerNodeId());

        boundaryEventActivator.activateBoundaryEvent(
            workflowState,
            elementInstance,
            timer.getHandlerNodeId(),
            EMPTY_DOCUMENT,
            streamWriter,
            event);

        if (shouldReschedule(timer)) {
          rescheduleTimer(timer, streamWriter, event);
        }
      } else {
        completeActivatedNode(elementInstanceKey, streamWriter, elementInstance);
      }

      elementInstanceState.flushDirtyState();

    } else {
      final StoredRecord tokenEvent = elementInstanceState.getTokenEvent(elementInstanceKey);

      if (tokenEvent != null && tokenEvent.getPurpose() == Purpose.DEFERRED_TOKEN) {
        // continue at an event-based gateway
        final WorkflowInstanceRecord deferredRecord = tokenEvent.getRecord().getValue();
        deferredRecord.setPayload(EMPTY_DOCUMENT).setElementId(timer.getHandlerNodeId());

        streamWriter.appendFollowUpEvent(
            tokenEvent.getKey(), WorkflowInstanceIntent.CATCH_EVENT_TRIGGERING, deferredRecord);
      }
    }
  }

  private boolean shouldReschedule(TimerRecord timer) {
    return timer.getRepetitions() == RepeatingInterval.INFINITE || timer.getRepetitions() > 1;
  }

  private void completeActivatedNode(
      long activityInstanceKey, TypedStreamWriter writer, ElementInstance elementInstance) {
    writer.appendFollowUpEvent(
        activityInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, elementInstance.getValue());
    elementInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
  }

  private void rescheduleTimer(
      TimerRecord record, TypedStreamWriter writer, ExecutableBoundaryEvent event) {
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
    boundaryEventActivator
        .getCatchEventOutput()
        .subscribeToTimerEvent(record.getElementInstanceKey(), event.getId(), timer, writer);
  }
}
