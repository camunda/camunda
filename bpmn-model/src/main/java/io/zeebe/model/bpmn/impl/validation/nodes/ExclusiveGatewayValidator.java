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
package io.zeebe.model.bpmn.impl.validation.nodes;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.instance.ExclusiveGatewayImpl;
import io.zeebe.model.bpmn.impl.instance.SequenceFlowImpl;
import java.util.List;

public class ExclusiveGatewayValidator {
  public void validate(ErrorCollector validationResult, ExclusiveGatewayImpl exclusiveGateway) {
    final List<SequenceFlowImpl> outgoing = exclusiveGateway.getOutgoing();
    if (outgoing.size() > 1 || (outgoing.size() == 1 && outgoing.get(0).hasCondition())) {
      final SequenceFlowImpl defaultFlow = exclusiveGateway.getDefaultFlow();
      if (defaultFlow != null) {
        if (defaultFlow.hasCondition()) {
          validationResult.addError(
              defaultFlow, "A default sequence flow must not have a condition.");
        }

        if (!exclusiveGateway.getOutgoingSequenceFlows().contains(defaultFlow)) {
          validationResult.addError(
              exclusiveGateway,
              "The default sequence flow must be an outgoing sequence flow of the exclusive gateway.");
        }
      }

      for (SequenceFlowImpl sequenceFlow : exclusiveGateway.getOutgoing()) {
        if (!sequenceFlow.hasCondition() && !sequenceFlow.equals(defaultFlow)) {
          validationResult.addError(
              sequenceFlow,
              "A sequence flow on an exclusive gateway must have a condition, if it is not the default flow.");
        }
      }
    }
  }
}
