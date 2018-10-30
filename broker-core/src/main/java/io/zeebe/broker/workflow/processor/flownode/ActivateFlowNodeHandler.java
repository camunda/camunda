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
package io.zeebe.broker.workflow.processor.flownode;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ActivateFlowNodeHandler<T extends ExecutableFlowNode> implements BpmnStepHandler<T> {
  private final IOMappingHelper ioMappingHelper = new IOMappingHelper();

  @Override
  public void handle(BpmnStepContext<T> context) {
    ioMappingHelper.applyInputMappings(context);
    activate(context);

    context
        .getOutput()
        .writeFollowUpEvent(
            context.getRecord().getKey(),
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            context.getValue());
  }

  /**
   * To be overridden by subclasses
   *
   * @param context current processor context
   */
  protected void activate(BpmnStepContext<T> context) {}
}
