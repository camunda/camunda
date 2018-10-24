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

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.TimerInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class TriggerTimerProcessor implements TypedRecordProcessor<TimerRecord> {

  private final WorkflowState workflowState;

  public TriggerTimerProcessor(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      TypedRecord<TimerRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {

    final TimerRecord timer = record.getValue();
    final long activityInstanceKey = timer.getActivityInstanceKey();

    final TimerInstance timerInstance = workflowState.getTimerState().get(activityInstanceKey);
    if (timerInstance == null) {
      streamWriter.writeRejection(
          record, RejectionType.NOT_APPLICABLE, "timer is already triggered or canceled");
      return;
    }

    final TypedBatchWriter batchWriter = streamWriter.newBatch();
    batchWriter.addFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);

    final ElementInstance elementInstance =
        workflowState.getElementInstanceState().getInstance(activityInstanceKey);

    if (elementInstance != null
        && elementInstance.getState() == WorkflowInstanceIntent.ELEMENT_ACTIVATED) {

      batchWriter.addFollowUpEvent(
          activityInstanceKey,
          WorkflowInstanceIntent.ELEMENT_COMPLETING,
          elementInstance.getValue());

      elementInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
      workflowState.getElementInstanceState().flushDirtyState();
    }

    workflowState.getTimerState().remove(timerInstance);
  }
}
