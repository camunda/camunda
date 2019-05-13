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
package io.zeebe.engine.processor.workflow.timer;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.intent.TimerIntent;

public class CancelTimerProcessor implements TypedRecordProcessor<TimerRecord> {
  public static final String NO_TIMER_FOUND_MESSAGE =
      "Expected to cancel timer with key '%d', but no such timer was found";
  private final WorkflowState workflowState;

  public CancelTimerProcessor(final WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      TypedRecord<TimerRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final TimerRecord timer = record.getValue();
    final TimerInstance timerInstance =
        workflowState.getTimerState().get(timer.getElementInstanceKey(), record.getKey());

    if (timerInstance == null) {
      streamWriter.appendRejection(
          record, RejectionType.NOT_FOUND, String.format(NO_TIMER_FOUND_MESSAGE, record.getKey()));
    } else {
      streamWriter.appendFollowUpEvent(record.getKey(), TimerIntent.CANCELED, timer);
      workflowState.getTimerState().remove(timerInstance);
    }
  }
}
