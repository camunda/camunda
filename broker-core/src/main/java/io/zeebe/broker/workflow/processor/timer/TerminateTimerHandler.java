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
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.flownode.TerminateElementHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.TimerInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.intent.TimerIntent;

public class TerminateTimerHandler extends TerminateElementHandler {

  private final WorkflowState workflowState;

  private final TimerRecord timerRecord = new TimerRecord();

  public TerminateTimerHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  protected void addTerminatingRecords(
      BpmnStepContext<ExecutableFlowNode> context, TypedBatchWriter batch) {

    final ElementInstance activityInstance = context.getElementInstance();

    final TimerInstance timerInstance =
        workflowState.getTimerState().get(activityInstance.getKey());
    if (timerInstance != null) {
      timerRecord
          .setActivityInstanceKey(timerInstance.getActivityInstanceKey())
          .setDueDate(timerInstance.getDueDate());

      batch.addFollowUpCommand(timerInstance.getKey(), TimerIntent.CANCEL, timerRecord);
    }
  }
}
