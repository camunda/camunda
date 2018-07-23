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

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.workflow.model.BpmnAspect;
import io.zeebe.broker.workflow.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.Mapping;
import java.util.Collection;

public class FlowNodeHandler implements ModelElementTransformer<FlowNode> {

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

    final int outgoingFlows = element.getOutgoing().size();
    if (outgoingFlows == 0) {
      flowNode.setBpmnAspect(BpmnAspect.CONSUME_TOKEN);
    } else {
      flowNode.setBpmnAspect(BpmnAspect.TAKE_SEQUENCE_FLOW);
    }
  }

  private void transformIoMappings(
      FlowNode element, final ExecutableFlowNode flowNode, TransformContext context) {
    final ZeebeIoMapping ioMapping = element.getSingleExtensionElement(ZeebeIoMapping.class);

    if (ioMapping != null) {
      final Collection<ZeebeInput> inputs = ioMapping.getInputs();
      final Mapping[] inputMappings =
          inputs
              .stream()
              .map(
                  i ->
                      createMapping(
                          context.getJsonPathQueryCompiler(), i.getSource(), i.getTarget()))
              .toArray(e -> new Mapping[e]);

      final Collection<ZeebeOutput> outputs = ioMapping.getOutputs();
      final Mapping[] outputMappings =
          outputs
              .stream()
              .map(
                  i ->
                      createMapping(
                          context.getJsonPathQueryCompiler(), i.getSource(), i.getTarget()))
              .toArray(e -> new Mapping[e]);

      flowNode.setInputMappings(inputMappings);
      flowNode.setOutputMappings(outputMappings);
      flowNode.setOutputBehavior(ioMapping.getOutputBehavior());
    }
  }

  private Mapping createMapping(JsonPathQueryCompiler queryCompiler, String source, String target) {
    final JsonPathQuery query = queryCompiler.compile(source);

    return new Mapping(query, wrapString(target));
  }
}
