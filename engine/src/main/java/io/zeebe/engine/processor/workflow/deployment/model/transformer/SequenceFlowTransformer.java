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
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class SequenceFlowTransformer implements ModelElementTransformer<SequenceFlow> {
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
    connectWithFlowNodes(element, workflow, sequenceFlow);
    bindLifecycle(sequenceFlow);
  }

  private void bindLifecycle(final ExecutableSequenceFlow sequenceFlow) {
    final ExecutableFlowNode target = sequenceFlow.getTarget();
    if (target.getElementType() == BpmnElementType.PARALLEL_GATEWAY) {
      sequenceFlow.bindLifecycleState(
          WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, BpmnStep.PARALLEL_MERGE_SEQUENCE_FLOW_TAKEN);
    } else {
      sequenceFlow.bindLifecycleState(
          WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, BpmnStep.SEQUENCE_FLOW_TAKEN);
    }
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
    sequenceFlow.setSource(source);
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
}
