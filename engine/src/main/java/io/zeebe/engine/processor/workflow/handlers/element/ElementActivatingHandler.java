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
 * Applies input mappings in the scope.
 *
 * @param <T>
 */
public class ElementActivatingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  private final IOMappingHelper ioMappingHelper;

  public ElementActivatingHandler() {
    this(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
  }

  public ElementActivatingHandler(WorkflowInstanceIntent nextState) {
    this(nextState, new IOMappingHelper());
  }

  public ElementActivatingHandler(
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
      ioMappingHelper.applyInputMappings(context);
      return true;
    } catch (MappingException e) {
      context.raiseIncident(ErrorType.IO_MAPPING_ERROR, e.getMessage());
    }

    return false;
  }
}
