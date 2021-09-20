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
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class MessageThrowEventValidator implements ModelElementValidator<ThrowEvent> {

  private final ExtensionElementsValidator<ThrowEvent, ZeebeTaskDefinition>
      extensionElementsValidator =
          ExtensionElementsValidator.verifyThat(ThrowEvent.class)
              .hasSingleExtensionElement(
                  ZeebeTaskDefinition.class, ZeebeConstants.ELEMENT_TASK_DEFINITION);

  @Override
  public Class<ThrowEvent> getElementType() {
    return ThrowEvent.class;
  }

  @Override
  public void validate(
      final ThrowEvent element, final ValidationResultCollector validationResultCollector) {

    if (isMessageThrowEvent(element)) {
      // a message throw event must have a task definition
      extensionElementsValidator.validate(element, validationResultCollector);
    }
  }

  private boolean isMessageThrowEvent(final ThrowEvent element) {
    return element.getEventDefinitions().stream()
        .anyMatch(MessageEventDefinition.class::isInstance);
  }
}
