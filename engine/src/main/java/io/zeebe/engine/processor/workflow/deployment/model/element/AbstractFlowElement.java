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
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.EnumMap;
import java.util.Map;
import org.agrona.DirectBuffer;

public abstract class AbstractFlowElement implements ExecutableFlowElement {

  private final DirectBuffer id;
  private final Map<WorkflowInstanceIntent, BpmnStep> bpmnSteps =
      new EnumMap<>(WorkflowInstanceIntent.class);
  private BpmnElementType elementType;

  public AbstractFlowElement(String id) {
    this.id = BufferUtil.wrapString(id);
    this.elementType = BpmnElementType.UNSPECIFIED;
  }

  @Override
  public DirectBuffer getId() {
    return id;
  }

  public void bindLifecycleState(WorkflowInstanceIntent state, BpmnStep step) {
    this.bpmnSteps.put(state, step);
  }

  @Override
  public BpmnStep getStep(WorkflowInstanceIntent state) {
    return bpmnSteps.get(state);
  }

  public void setElementType(BpmnElementType elementType) {
    this.elementType = elementType;
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }
}
