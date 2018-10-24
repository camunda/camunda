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

import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingBuilder;
import java.util.Collection;

public class FlowNodeHandler implements ModelElementTransformer<FlowNode> {

  private final MappingBuilder mappingBuilder = new MappingBuilder();

  @Override
  public Class<FlowNode> getType() {
    return FlowNode.class;
  }

  @Override
  public void transform(FlowNode element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableFlowNode flowNode =
        workflow.getElementById(element.getId(), ExecutableFlowNode.class);

    transformIoMappings(element, flowNode, context);

    final BpmnStep outgoingBehavior = determineOutgoingBehavior(element, flowNode);
    context.setCurrentFlowNodeOutgoingStep(outgoingBehavior);
  }

  private BpmnStep determineOutgoingBehavior(FlowNode element, final ExecutableFlowNode flowNode) {
    final int outgoingFlows = element.getOutgoing().size();

    if (outgoingFlows == 0) {
      return BpmnStep.CONSUME_TOKEN;
    } else if (outgoingFlows == 1) {
      return BpmnStep.TAKE_SEQUENCE_FLOW;
    } else {
      return BpmnStep.PARALLEL_SPLIT;
    }
  }

  private void transformIoMappings(
      FlowNode element, final ExecutableFlowNode flowNode, TransformContext context) {
    final ZeebeIoMapping ioMapping = element.getSingleExtensionElement(ZeebeIoMapping.class);

    if (ioMapping != null) {
      final Collection<ZeebeInput> inputs = ioMapping.getInputs();
      inputs.forEach(i -> mappingBuilder.mapping(i.getSource(), i.getTarget()));

      final Mapping[] inputMappings = mappingBuilder.build();

      final Collection<ZeebeOutput> outputs = ioMapping.getOutputs();
      outputs.forEach(o -> mappingBuilder.mapping(o.getSource(), o.getTarget()));
      final Mapping[] outputMappings = mappingBuilder.build();

      flowNode.setInputMappings(inputMappings);
      flowNode.setOutputMappings(outputMappings);
      flowNode.setOutputBehavior(ioMapping.getOutputBehavior());
    }
  }
}
