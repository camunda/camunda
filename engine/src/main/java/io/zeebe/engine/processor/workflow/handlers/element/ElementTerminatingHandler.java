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
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Resolves any incident that were associated with this element.
 *
 * @param <T>
 */
public class ElementTerminatingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  public ElementTerminatingHandler() {
    this(WorkflowInstanceIntent.ELEMENT_TERMINATED);
  }

  public ElementTerminatingHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isStateSameAsElementState(context);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    return true;
  }
}
