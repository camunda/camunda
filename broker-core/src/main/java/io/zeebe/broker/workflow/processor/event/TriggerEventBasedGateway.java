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
package io.zeebe.broker.workflow.processor.event;

import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.state.VariablesState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class TriggerEventBasedGateway implements BpmnStepHandler<ExecutableCatchEventElement> {

  @Override
  public void handle(BpmnStepContext<ExecutableCatchEventElement> context) {
    final long elementInstanceKey = context.getRecord().getKey();

    context.getCatchEventBehavior().unsubscribeFromEvents(elementInstanceKey, context);

    final long eventInstanceKey =
        context
            .getOutput()
            .appendNewEvent(WorkflowInstanceIntent.EVENT_TRIGGERING, context.getValue());

    // TODO (saig0) #1899: since the events have a different key, we need to copy the payload to the
    // new scope
    final VariablesState variablesState = context.getElementInstanceState().getVariablesState();
    final DirectBuffer payload = variablesState.getPayload(elementInstanceKey);
    if (payload != null) {
      variablesState.setPayload(eventInstanceKey, payload);
      variablesState.removePayload(elementInstanceKey);
    }
  }
}
