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
import io.camunda.zeebe.model.bpmn.instance.InclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
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

    if (element.getSource() instanceof ExclusiveGateway) {
      final ExclusiveGateway gateway = (ExclusiveGateway) element.getSource();
      if (gateway.getOutgoing().size() > 1
          && gateway.getDefault() != element
          && element.getConditionExpression() == null) {
        validationResultCollector.addError(0, "Must have a condition or be default flow");
      }
    }

    if (element.getSource() instanceof InclusiveGateway) {
      final InclusiveGateway gateway = (InclusiveGateway) element.getSource();
      if (gateway.getOutgoing().size() > 1
          && gateway.getDefault() != element
          && element.getConditionExpression() == null) {
        validationResultCollector.addError(0, "Must have a condition or be default flow");
      }
    }
  }
}
