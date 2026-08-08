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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.InclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.util.Optional;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class SequenceFlowValidator implements ModelElementValidator<SequenceFlow> {

  @Override
  public Class<SequenceFlow> getElementType() {
    return SequenceFlow.class;
  }

  @Override
  public void validate(
      final SequenceFlow element, final ValidationResultCollector validationResultCollector) {
    IdentifiableBpmnElementValidator.validate(element, validationResultCollector);

    final FlowNode source = element.getSource();
    final FlowNode target = element.getTarget();

    if (source == null) {
      validationResultCollector.addError(
          0, "Attribute 'sourceRef' must reference a valid flow node");
    }

    if (target == null) {
      validationResultCollector.addError(
          0, "Attribute 'targetRef' must reference a valid flow node");
    }

    if (source == null) {
      return;
    }

    if (source instanceof ExclusiveGateway) {
      final ExclusiveGateway gateway = (ExclusiveGateway) source;
      if (gateway.getOutgoing().size() > 1
          && gateway.getDefault() != element
          && element.getConditionExpression() == null) {
        validationResultCollector.addError(0, "Must have a condition or be default flow");
      }
    }

    if (source instanceof InclusiveGateway) {
      final InclusiveGateway gateway = (InclusiveGateway) source;
      if (gateway.getOutgoing().size() > 1) {
        final Optional<SequenceFlow> sequenceFlow =
            gateway.getOutgoing().stream()
                .filter(x -> x.getConditionExpression() == null && x == element)
                .findFirst();

        sequenceFlow.ifPresent(
            out -> {
              if (gateway.getDefault() != element) {
                validationResultCollector.addError(0, "Must have a condition");
              }
            });
      }
    }
  }
}
