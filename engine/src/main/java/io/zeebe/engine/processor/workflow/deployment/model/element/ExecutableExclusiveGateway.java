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

import java.util.ArrayList;
import java.util.List;

public class ExecutableExclusiveGateway extends ExecutableFlowNode {

  private ExecutableSequenceFlow defaultFlow;

  private final List<ExecutableSequenceFlow> outgoingWithCondition = new ArrayList<>();

  public ExecutableExclusiveGateway(String id) {
    super(id);
  }

  public ExecutableSequenceFlow getDefaultFlow() {
    return defaultFlow;
  }

  public void setDefaultFlow(ExecutableSequenceFlow defaultFlow) {
    this.defaultFlow = defaultFlow;
  }

  @Override
  public void addOutgoing(ExecutableSequenceFlow flow) {
    super.addOutgoing(flow);
    if (flow.getCondition() != null) {
      outgoingWithCondition.add(flow);
    }
  }

  public List<ExecutableSequenceFlow> getOutgoingWithCondition() {
    return outgoingWithCondition;
  }
}
