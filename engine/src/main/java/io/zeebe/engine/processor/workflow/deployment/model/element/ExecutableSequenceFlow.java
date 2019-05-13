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

import io.zeebe.msgpack.el.CompiledJsonCondition;

public class ExecutableSequenceFlow extends AbstractFlowElement {

  private ExecutableFlowNode source;
  private ExecutableFlowNode target;
  private CompiledJsonCondition condition;

  public ExecutableSequenceFlow(String id) {
    super(id);
  }

  public void setTarget(ExecutableFlowNode target) {
    this.target = target;
  }

  public ExecutableFlowNode getTarget() {
    return target;
  }

  public ExecutableFlowNode getSource() {
    return source;
  }

  public void setSource(ExecutableFlowNode source) {
    this.source = source;
  }

  public void setCondition(CompiledJsonCondition condition) {
    this.condition = condition;
  }

  public CompiledJsonCondition getCondition() {
    return condition;
  }
}
