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

import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeCalledDecisionValidator implements ModelElementValidator<ZeebeCalledDecision> {

  private static final String errorMessage(final String attribute) {
    return String.format("Attribute '%s' must be present and not empty", attribute);
  }

  @Override
  public Class<ZeebeCalledDecision> getElementType() {
    return ZeebeCalledDecision.class;
  }

  @Override
  public void validate(
      final ZeebeCalledDecision element,
      final ValidationResultCollector validationResultCollector) {

    final String decisionId = element.getDecisionId();
    if (decisionId == null || decisionId.isEmpty()) {
      validationResultCollector.addError(0, errorMessage(ZeebeConstants.ATTRIBUTE_DECISION_ID));
    }

    final String resultVariable = element.getResultVariable();
    if (resultVariable == null || resultVariable.isEmpty()) {
      validationResultCollector.addError(0, errorMessage(ZeebeConstants.ATTRIBUTE_RESULT_VARIABLE));
    }
  }
}
