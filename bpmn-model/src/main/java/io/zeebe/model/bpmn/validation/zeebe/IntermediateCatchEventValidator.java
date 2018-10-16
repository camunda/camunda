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
package io.zeebe.model.bpmn.validation.zeebe;

import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import java.util.Collection;
import java.util.Optional;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class IntermediateCatchEventValidator
    implements ModelElementValidator<IntermediateCatchEvent> {

  @Override
  public Class<IntermediateCatchEvent> getElementType() {
    return IntermediateCatchEvent.class;
  }

  @Override
  public void validate(
      IntermediateCatchEvent element, ValidationResultCollector validationResultCollector) {

    final Collection<EventDefinition> eventDefinitions = element.getEventDefinitions();
    final Optional<EventDefinition> messageEventDefinition =
        eventDefinitions.stream().filter(e -> e instanceof MessageEventDefinition).findFirst();

    if (!messageEventDefinition.isPresent()) {
      validationResultCollector.addError(0, "Must have a message event definition");

    } else {
      final Message message = ((MessageEventDefinition) messageEventDefinition.get()).getMessage();
      if (message == null) {
        validationResultCollector.addError(0, "Must reference a message");
      }
    }
  }
}
