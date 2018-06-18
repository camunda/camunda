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
package io.zeebe.model.bpmn.impl.transformation;

import io.zeebe.model.bpmn.BpmnAspect;
import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.instance.FlowElementImpl;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.impl.instance.StartEventImpl;
import io.zeebe.model.bpmn.impl.transformation.nodes.ExclusiveGatewayTransformer;
import io.zeebe.model.bpmn.impl.transformation.nodes.SequenceFlowTransformer;
import io.zeebe.model.bpmn.impl.transformation.nodes.task.ServiceTaskTransformer;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public class ProcessTransformer {
  private final SequenceFlowTransformer sequenceFlowTransformer = new SequenceFlowTransformer();
  private final ServiceTaskTransformer serviceTaskTransformer = new ServiceTaskTransformer();
  private final ExclusiveGatewayTransformer exclusiveGatewayTransformer =
      new ExclusiveGatewayTransformer();

  public void transform(ErrorCollector errorCollector, ProcessImpl process) {
    final List<FlowElementImpl> flowElements = collectFlowElements(process);
    process.getFlowElements().addAll(flowElements);

    final Map<DirectBuffer, FlowElementImpl> flowElementsById = getFlowElementsById(flowElements);
    process.getFlowElementMap().putAll(flowElementsById);

    setInitialStartEvent(process);

    sequenceFlowTransformer.transform(errorCollector, process.getSequenceFlows(), flowElementsById);
    serviceTaskTransformer.transform(errorCollector, process.getServiceTasks());
    exclusiveGatewayTransformer.transform(process.getExclusiveGateways());

    transformBpmnAspects(process);
  }

  private List<FlowElementImpl> collectFlowElements(final ProcessImpl process) {
    final List<FlowElementImpl> flowElements = new ArrayList<>();
    flowElements.addAll(process.getStartEvents());
    flowElements.addAll(process.getEndEvents());
    flowElements.addAll(process.getSequenceFlows());
    flowElements.addAll(process.getServiceTasks());
    flowElements.addAll(process.getExclusiveGateways());
    return flowElements;
  }

  private Map<DirectBuffer, FlowElementImpl> getFlowElementsById(
      List<FlowElementImpl> flowElements) {
    final Map<DirectBuffer, FlowElementImpl> map = new HashMap<>();
    for (FlowElementImpl flowElement : flowElements) {
      map.put(flowElement.getIdAsBuffer(), flowElement);
    }
    return map;
  }

  private void setInitialStartEvent(final ProcessImpl process) {
    final List<StartEventImpl> startEvents = process.getStartEvents();
    if (startEvents.size() >= 1) {
      final StartEventImpl startEvent = startEvents.get(0);
      process.setInitialStartEvent(startEvent);
    }
  }

  private void transformBpmnAspects(ProcessImpl process) {
    final List<FlowElement> flowElements = process.getFlowElements();
    for (int f = 0; f < flowElements.size(); f++) {
      final FlowElementImpl flowElement = (FlowElementImpl) flowElements.get(f);

      if (flowElement instanceof FlowNode) {
        final FlowNode flowNode = (FlowNode) flowElement;

        final List<SequenceFlow> outgoingSequenceFlows = flowNode.getOutgoingSequenceFlows();
        if (outgoingSequenceFlows.isEmpty()) {
          flowElement.setBpmnAspect(BpmnAspect.CONSUME_TOKEN);
        } else if (outgoingSequenceFlows.size() == 1
            && !outgoingSequenceFlows.get(0).hasCondition()) {
          flowElement.setBpmnAspect(BpmnAspect.TAKE_SEQUENCE_FLOW);
        } else if (flowElement instanceof ExclusiveGateway) {
          flowElement.setBpmnAspect(BpmnAspect.EXCLUSIVE_SPLIT);
        }
      }
    }
  }
}
