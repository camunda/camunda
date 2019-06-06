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
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Applies output mappings to the scope.
 *
 * @param <T>
 */
public class ElementCompletingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  private final IOMappingHelper ioMappingHelper;

  public ElementCompletingHandler() {
    this(new IOMappingHelper());
  }

  public ElementCompletingHandler(IOMappingHelper ioMappingHelper) {
    this(WorkflowInstanceIntent.ELEMENT_COMPLETED, ioMappingHelper);
  }

  public ElementCompletingHandler(
      WorkflowInstanceIntent nextState, IOMappingHelper ioMappingHelper) {
    super(nextState);
    this.ioMappingHelper = ioMappingHelper;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()));
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    try {
      ioMappingHelper.applyOutputMappings(context);
      return true;
    } catch (MappingException e) {
      context.raiseIncident(ErrorType.IO_MAPPING_ERROR, e.getMessage());
    }

    return false;
  }
}
