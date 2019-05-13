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
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEventBasedGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;

// todo: this skips the sequence flow taken and just starts the next element
// https://github.com/zeebe-io/zeebe/issues/1979
public class EventBasedGatewayEventOccurredHandler<T extends ExecutableEventBasedGateway>
    extends EventOccurredHandler<T> {
  public EventBasedGatewayEventOccurredHandler() {
    super();
  }

  public EventBasedGatewayEventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      final EventTrigger event = getTriggeredEvent(context, context.getRecord().getKey());
      final ExecutableSequenceFlow flow = getSequenceFlow(context, event);

      if (flow == null) {
        Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
            "No outgoing flow has a target with ID {} for process {}",
            BufferUtil.bufferAsString(event.getElementId()),
            BufferUtil.bufferAsString(context.getValue().getBpmnProcessId()));
        return false;
      }

      final WorkflowInstanceRecord eventRecord =
          getEventRecord(context, event, flow.getTarget().getElementType());
      deferEvent(
          context, context.getRecord().getKey(), context.getRecord().getKey(), eventRecord, event);
      return true;
    }

    return false;
  }

  private ExecutableSequenceFlow getSequenceFlow(BpmnStepContext<T> context, EventTrigger event) {
    final List<ExecutableSequenceFlow> outgoing = context.getElement().getOutgoing();

    for (final ExecutableSequenceFlow flow : outgoing) {
      if (flow.getTarget().getId().equals(event.getElementId())) {
        return flow;
      }
    }

    return null;
  }
}
