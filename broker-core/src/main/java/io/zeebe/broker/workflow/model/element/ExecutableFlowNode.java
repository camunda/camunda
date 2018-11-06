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

import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.msgpack.mapping.Mapping;
import java.util.ArrayList;
import java.util.List;

public class ExecutableFlowNode extends AbstractFlowElement {

  private List<ExecutableSequenceFlow> incoming = new ArrayList<>();
  private List<ExecutableSequenceFlow> outgoing = new ArrayList<>();

  private Mapping[] inputMappings = new Mapping[0];
  private Mapping[] outputMappings = new Mapping[0];

  private ZeebeOutputBehavior outputBehavior;

  public ExecutableFlowNode(String id) {
    super(id);
  }

  public List<ExecutableSequenceFlow> getOutgoing() {
    return outgoing;
  }

  public void addOutgoing(ExecutableSequenceFlow flow) {
    this.outgoing.add(flow);
  }

  public List<ExecutableSequenceFlow> getIncoming() {
    return incoming;
  }

  public void addIncoming(ExecutableSequenceFlow flow) {
    this.incoming.add(flow);
  }

  public Mapping[] getInputMappings() {
    return inputMappings;
  }

  public void setInputMappings(Mapping[] inputMappings) {
    this.inputMappings = inputMappings;
  }

  public void setOutputMappings(Mapping[] outputMappings) {
    this.outputMappings = outputMappings;
  }

  public Mapping[] getOutputMappings() {
    return outputMappings;
  }

  public ZeebeOutputBehavior getOutputBehavior() {
    return outputBehavior;
  }

  public void setOutputBehavior(ZeebeOutputBehavior outputBehavior) {
    this.outputBehavior = outputBehavior;
  }
}
