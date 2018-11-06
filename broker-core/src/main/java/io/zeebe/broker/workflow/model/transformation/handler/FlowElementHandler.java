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
package io.zeebe.broker.workflow.model.transformation.handler;

import io.zeebe.broker.workflow.model.element.AbstractFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableEndEvent;
import io.zeebe.broker.workflow.model.element.ExecutableExclusiveGateway;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElementContainer;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableIntermediateCatchElement;
import io.zeebe.broker.workflow.model.element.ExecutableReceiveTask;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.model.element.ExecutableServiceTask;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class FlowElementHandler implements ModelElementTransformer<FlowElement> {

  private static final Map<Class<?>, Function<String, AbstractFlowElement>> ELEMENT_FACTORIES;

  static {
    ELEMENT_FACTORIES = new HashMap<>();

    ELEMENT_FACTORIES.put(EndEvent.class, ExecutableEndEvent::new);
    ELEMENT_FACTORIES.put(ExclusiveGateway.class, ExecutableExclusiveGateway::new);
    ELEMENT_FACTORIES.put(IntermediateCatchEvent.class, ExecutableIntermediateCatchElement::new);
    ELEMENT_FACTORIES.put(ParallelGateway.class, ExecutableFlowNode::new);
    ELEMENT_FACTORIES.put(SequenceFlow.class, ExecutableSequenceFlow::new);
    ELEMENT_FACTORIES.put(ServiceTask.class, ExecutableServiceTask::new);
    ELEMENT_FACTORIES.put(ReceiveTask.class, ExecutableReceiveTask::new);
    ELEMENT_FACTORIES.put(StartEvent.class, ExecutableFlowNode::new);
    ELEMENT_FACTORIES.put(SubProcess.class, ExecutableFlowElementContainer::new);
  }

  @Override
  public Class<FlowElement> getType() {
    return FlowElement.class;
  }

  @Override
  public void transform(FlowElement element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final Class<?> elemenType = element.getElementType().getInstanceType();

    final Function<String, AbstractFlowElement> elementFactory = ELEMENT_FACTORIES.get(elemenType);
    final AbstractFlowElement executableElement = elementFactory.apply(element.getId());

    workflow.addFlowElement(executableElement);
  }
}
