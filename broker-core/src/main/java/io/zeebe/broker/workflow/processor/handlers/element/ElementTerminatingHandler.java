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
package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.incident.processor.IncidentState;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Resolves any incident that were associated with this element.
 *
 * @param <T>
 */
public class ElementTerminatingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  protected final IncidentState incidentState;

  public ElementTerminatingHandler(IncidentState incidentState) {
    this(WorkflowInstanceIntent.ELEMENT_TERMINATED, incidentState);
  }

  public ElementTerminatingHandler(WorkflowInstanceIntent nextState, IncidentState incidentState) {
    super(nextState);
    this.incidentState = incidentState;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isStateSameAsElementState(context);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    // todo: this is used to be executed after the child class' terminate logic; see if it matters
    // could perhaps be moved to the terminated phase
    // https://github.com/zeebe-io/zeebe/issues/1978
    incidentState.forExistingWorkflowIncident(
        context.getRecord().getKey(),
        (record, key) -> context.getOutput().appendResolvedIncidentEvent(key, record));

    return true;
  }
}
