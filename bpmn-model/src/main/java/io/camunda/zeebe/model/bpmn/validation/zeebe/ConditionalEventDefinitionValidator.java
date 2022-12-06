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

import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Expression;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ConditionalEventDefinitionValidator
    implements ModelElementValidator<ConditionalEventDefinition> {

  @Override
  public Class<ConditionalEventDefinition> getElementType() {
    return ConditionalEventDefinition.class;
  }

  @Override
  public void validate(
      final ConditionalEventDefinition element,
      final ValidationResultCollector validationResultCollector) {

    final Expression condition = element.getCondition();
    if (condition == null || condition.getTextContent().isEmpty()) {
      validationResultCollector.addError(
          0, "Conditional event must contain an expression for evaluation");
    }
  }
}
