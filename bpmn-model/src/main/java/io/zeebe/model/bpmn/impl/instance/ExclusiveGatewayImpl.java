/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.impl.instance;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.*;

public class ExclusiveGatewayImpl extends FlowNodeImpl implements ExclusiveGateway {
  private SequenceFlowImpl defaultFlow;

  private List<SequenceFlow> sequenceFlowsWithConditions = new ArrayList<>();

  @XmlIDREF
  @XmlAttribute(name = BpmnConstants.BPMN_ATTRIBUTE_DEFAULT)
  public void setDefaultFlow(SequenceFlowImpl defaultFlow) {
    this.defaultFlow = defaultFlow;
  }

  @Override
  public SequenceFlowImpl getDefaultFlow() {
    return defaultFlow;
  }

  @XmlTransient
  public void setOutgoingSequenceFlowsWithConditions(
      List<SequenceFlow> sequenceFlowsWithConditions) {
    this.sequenceFlowsWithConditions = sequenceFlowsWithConditions;
  }

  @Override
  public List<SequenceFlow> getOutgoingSequenceFlowsWithConditions() {
    return sequenceFlowsWithConditions;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("ExclusiveGateway [id=");
    builder.append(getId());
    builder.append(", name=");
    builder.append(getName());
    builder.append(", incoming=");
    builder.append(getIncoming());
    builder.append(", outgoing=");
    builder.append(getOutgoing());
    builder.append(", default=");
    builder.append(defaultFlow != null ? defaultFlow.getId() : "null");
    builder.append("]");
    return builder.toString();
  }
}
