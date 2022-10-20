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

import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class EscalationEventDefinitionValidator
    implements ModelElementValidator<EscalationEventDefinition> {

  @Override
  public Class<EscalationEventDefinition> getElementType() {
    return EscalationEventDefinition.class;
  }

  @Override
  public void validate(
      final EscalationEventDefinition element,
      final ValidationResultCollector validationResultCollector) {

    if (isEscalationThrowEvent(element) && element.getEscalation() == null) {
      validationResultCollector.addError(0, "Must reference an escalation");
    }
  }

  private boolean isEscalationThrowEvent(final EscalationEventDefinition element) {
    final ModelElementInstance parentElement = element.getParentElement();
    return parentElement instanceof IntermediateThrowEvent || parentElement instanceof EndEvent;
  }
}
