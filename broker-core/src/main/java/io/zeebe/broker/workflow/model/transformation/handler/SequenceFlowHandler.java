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
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.MappingCompiler;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMappingType;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.Mapping.Type;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.EnumMap;

public class SequenceFlowHandler implements ModelElementTransformer<SequenceFlow> {

  private static final EnumMap<ZeebeMappingType, Mapping.Type> TYPE_MAP =
      new EnumMap<>(ZeebeMappingType.class);

  static {
    TYPE_MAP.put(ZeebeMappingType.PUT, Type.PUT);
    TYPE_MAP.put(ZeebeMappingType.COLLECT, Type.COLLECT);
  }

  @Override
  public Class<SequenceFlow> getType() {
    return SequenceFlow.class;
  }

  @Override
  public void transform(final SequenceFlow element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableSequenceFlow sequenceFlow =
        workflow.getElementById(element.getId(), ExecutableSequenceFlow.class);

    compileCondition(element, sequenceFlow);
    compilePayloadMappings(element, sequenceFlow, context);
    connectWithFlowNodes(element, workflow, sequenceFlow);
    bindLifecycle(element, sequenceFlow);
  }

  private void bindLifecycle(
      final SequenceFlow element, final ExecutableSequenceFlow sequenceFlow) {
    final FlowNode target = element.getTarget();

    final BpmnStep step;

    if (target instanceof Activity || target instanceof IntermediateCatchEvent) {
      step = BpmnStep.START_STATEFUL_ELEMENT;
    } else if (target instanceof ExclusiveGateway) {
      step = BpmnStep.ACTIVATE_GATEWAY;
    } else if (target instanceof ParallelGateway) {
      if (target.getIncoming().size() == 1) {
        step = BpmnStep.ACTIVATE_GATEWAY;
      } else {
        step = BpmnStep.PARALLEL_MERGE;
      }
    } else if (target instanceof EndEvent) {
      step = BpmnStep.TRIGGER_END_EVENT;
    } else {
      throw new RuntimeException("Unsupported element");
    }

    sequenceFlow.bindLifecycleState(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, step);
  }

  private void connectWithFlowNodes(
      final SequenceFlow element,
      final ExecutableWorkflow workflow,
      final ExecutableSequenceFlow sequenceFlow) {
    final ExecutableFlowNode source =
        workflow.getElementById(element.getSource().getId(), ExecutableFlowNode.class);
    final ExecutableFlowNode target =
        workflow.getElementById(element.getTarget().getId(), ExecutableFlowNode.class);

    source.addOutgoing(sequenceFlow);
    target.addIncoming(sequenceFlow);
    sequenceFlow.setTarget(target);
  }

  private void compileCondition(
      final SequenceFlow element, final ExecutableSequenceFlow sequenceFlow) {
    final ConditionExpression conditionExpression = element.getConditionExpression();
    if (conditionExpression != null) {
      final String rawExpression = conditionExpression.getTextContent();
      final CompiledJsonCondition compiledExpression =
          JsonConditionFactory.createCondition(rawExpression);
      sequenceFlow.setCondition(compiledExpression);
    }
  }

  private void compilePayloadMappings(
      SequenceFlow element, ExecutableSequenceFlow sequenceFlow, TransformContext context) {

    final MappingCompiler mappingCompiler = context.getMappingCompiler();
    final Mapping[] mappings = mappingCompiler.compilePayloadMappings(element);
    sequenceFlow.setPayloadMappings(mappings);
  }
}
