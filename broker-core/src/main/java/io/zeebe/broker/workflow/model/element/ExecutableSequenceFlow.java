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
package io.zeebe.broker.workflow.model.element;

import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.mapping.Mapping;

public class ExecutableSequenceFlow extends AbstractFlowElement {

  private ExecutableFlowNode target;
  private CompiledJsonCondition condition;

  private Mapping[] payloadMappings = new Mapping[0];

  public ExecutableSequenceFlow(String id) {
    super(id);
  }

  public void setTarget(ExecutableFlowNode target) {
    this.target = target;
  }

  public ExecutableFlowNode getTarget() {
    return target;
  }

  public void setCondition(CompiledJsonCondition condition) {
    this.condition = condition;
  }

  public CompiledJsonCondition getCondition() {
    return condition;
  }

  public void setPayloadMappings(Mapping[] payloadMappings) {
    this.payloadMappings = payloadMappings;
  }

  public Mapping[] getPayloadMappings() {
    return payloadMappings;
  }
}
