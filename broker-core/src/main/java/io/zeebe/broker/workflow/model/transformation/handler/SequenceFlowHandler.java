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

import io.zeebe.broker.workflow.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;

public class SequenceFlowHandler implements ModelElementTransformer<SequenceFlow> {

  @Override
  public Class<SequenceFlow> getType() {
    return SequenceFlow.class;
  }

  @Override
  public void transform(SequenceFlow element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableSequenceFlow sequenceFlow =
        workflow.getElementById(element.getId(), ExecutableSequenceFlow.class);

    compileCondition(element, sequenceFlow);
    connectWithFlowNodes(element, workflow, sequenceFlow);
  }

  private void connectWithFlowNodes(
      SequenceFlow element,
      final ExecutableWorkflow workflow,
      final ExecutableSequenceFlow sequenceFlow) {
    final ExecutableFlowNode source =
        workflow.getElementById(element.getSource().getId(), ExecutableFlowNode.class);
    final ExecutableFlowNode target =
        workflow.getElementById(element.getTarget().getId(), ExecutableFlowNode.class);

    source.addOutgoing(sequenceFlow);
    sequenceFlow.setTarget(target);
  }

  private void compileCondition(SequenceFlow element, final ExecutableSequenceFlow sequenceFlow) {
    final ConditionExpression conditionExpression = element.getConditionExpression();
    if (conditionExpression != null) {
      final String rawExpression = conditionExpression.getTextContent();
      final CompiledJsonCondition compiledExpression =
          JsonConditionFactory.createCondition(rawExpression);
      sequenceFlow.setCondition(compiledExpression);
    }
  }
}
