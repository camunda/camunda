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
package io.zeebe.model.bpmn.impl.transformation.nodes;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.instance.ConditionExpressionImpl;
import io.zeebe.model.bpmn.impl.instance.FlowElementImpl;
import io.zeebe.model.bpmn.impl.instance.FlowNodeImpl;
import io.zeebe.model.bpmn.impl.instance.SequenceFlowImpl;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public class SequenceFlowTransformer {
  public void transform(
      ErrorCollector errorCollector,
      List<SequenceFlowImpl> sequenceFlows,
      Map<DirectBuffer, FlowElementImpl> flowElementsById) {
    for (int s = 0; s < sequenceFlows.size(); s++) {
      final SequenceFlowImpl sequenceFlow = sequenceFlows.get(s);

      final FlowElementImpl sourceElement =
          flowElementsById.get(sequenceFlow.getSourceRefAsBuffer());
      if (sourceElement != null) {
        sequenceFlow.setSourceNode((FlowNodeImpl) sourceElement);
      }

      final FlowElementImpl targetElement =
          flowElementsById.get(sequenceFlow.getTargetRefAsBuffer());
      if (targetElement != null) {
        sequenceFlow.setTargetNode((FlowNodeImpl) targetElement);
      }

      if (sequenceFlow.hasCondition()) {
        createCondition(errorCollector, sequenceFlow);
      }
    }
  }

  private void createCondition(ErrorCollector errorCollector, SequenceFlowImpl sequenceFlow) {
    final ConditionExpressionImpl conditionExpression = sequenceFlow.getConditionExpression();
    final CompiledJsonCondition condition =
        JsonConditionFactory.createCondition(conditionExpression.getText());

    if (!condition.isValid()) {
      errorCollector.addError(
          sequenceFlow,
          String.format(
              "The condition '%s' is not valid: %s",
              condition.getExpression(), condition.getErrorMessage()));
    }

    conditionExpression.setCondition(condition);
  }
}
